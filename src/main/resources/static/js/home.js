/* ============================================
   Expense Tracker - Home Panel & Dashboard
   ============================================ */

// --- Debounce utility ---
let _filterDebounceTimer = null;
function debounceFilters(fn, delay = 400) {
    clearTimeout(_filterDebounceTimer);
    _filterDebounceTimer = setTimeout(fn, delay);
}

// Store last loaded data for chart period switching
let _homeData = null;
let _desktopData = null;

// --- HOME PANEL (social-scroll feed) ---
function renderHomePanel() {
    const panel = document.getElementById('panel-home');
    panel.innerHTML = `
    <div class="home-feed" id="homeFeed">
        <div class="feed-card hero-card" id="heroCard">
            <div class="hero-loading"><i class="fa-solid fa-spinner fa-spin"></i> Loading...</div>
        </div>
        <div class="feed-card">
            <div class="card-title-row">
                <h3 class="card-title"><i class="fa-solid fa-chart-column"></i> Totals</h3>
                <select class="chart-period-select interactive-element" id="homeTotalsPeriod" onchange="updateHomeCharts()">
                    <option value="monthly" selected>Monthly</option>
                    <option value="weekly">Weekly</option>
                    <option value="annual">Annual</option>
                </select>
            </div>
            <div class="chart-container-sm"><canvas id="homeMonthlyChart"></canvas></div>
        </div>
        <div class="feed-card">
            <h3 class="card-title"><i class="fa-solid fa-chart-line"></i> Daily Spending</h3>
            <div class="chart-container-sm"><canvas id="homeTimelineChart"></canvas></div>
        </div>
        <div class="feed-card">
            <h3 class="card-title"><i class="fa-solid fa-chart-pie"></i> By Category</h3>
            <div class="chart-container-sm"><canvas id="homeCategoryChart"></canvas></div>
        </div>
        <div class="feed-card">
            <h3 class="card-title"><i class="fa-solid fa-store"></i> Most Visited Shops</h3>
            <div id="homeTopShops"></div>
        </div>
        <div class="feed-card">
            <h3 class="card-title"><i class="fa-solid fa-basket-shopping"></i> Most Bought Items</h3>
            <div id="homeTopItems"></div>
        </div>
        <div class="feed-card">
            <h3 class="card-title"><i class="fa-solid fa-clock-rotate-left"></i> Recent Expenses</h3>
            <div id="homeRecentExpenses"></div>
        </div>
        <div class="feed-card">
            <h3 class="card-title"><i class="fa-solid fa-map-location-dot"></i> Where You Spend</h3>
            <div id="homeGeoMap" style="height:280px; border-radius:var(--radius);"></div>
        </div>
        <div id="homeDiscoveryCards"></div>
        <div id="homeDiscoveryLoading" class="discovery-load-trigger" style="height:60px;"></div>
        <div style="height:80px;"></div>
    </div>`;
    loadHomeFeed();
}

function formatDateShort(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

async function loadHomeFeed() {
    // First load: fetch all data to build hero dropdown, then filter to current month
    const data = await api('/api/dashboard');
    if (!data) return;
    _homeData = data;

    // Cache country names + categories for expense detail use
    cacheCountryNames(data.geoByCountry);
    if (data.categories) window._allExpenseCategories = data.categories;

    // Hero card (acts as global filter)
    renderHeroCard('heroCard', data, 'homeHeroPeriod', 'home');

    // Destroy old charts
    Object.values(chartInstances).forEach(c => c.destroy && c.destroy());
    chartInstances = {};

    // Now load filtered data for current/latest month
    const currentMonth = getCurrentOrLatestMonth(data);
    const params = buildParamsFromPeriod('month:' + currentMonth);
    const filtered = await api('/api/dashboard' + (params ? '?' + params : ''));
    if (!filtered) return;

    renderFilteredHomeData(filtered);

    // Discovery cards (infinite scroll) — from unfiltered data
    renderDiscoveryCards('homeDiscoveryCards', data.discoveryCards);
    initDiscoveryScroll('homeFeed', 'homeDiscoveryCards', 'homeDiscoveryLoading', data.discoveryCards);
}

function getCurrentOrLatestMonth(data) {
    const now = new Date();
    const currentMonth = now.toISOString().slice(0, 7);
    const monthly = data.monthlyTotals || {};
    const monthKeys = Object.keys(monthly).sort().reverse();
    // If current month has data, use it; otherwise use latest available
    if (monthly[currentMonth] !== undefined) return currentMonth;
    return monthKeys.length > 0 ? monthKeys[0] : currentMonth;
}

function renderFilteredHomeData(data) {

    updateHomeCharts();

    // Timeline chart
    const tc = document.getElementById('homeTimelineChart');
    if (tc) {
        chartInstances.homeTimeline = new Chart(tc, {
            type: 'line',
            data: { labels: Object.keys(data.timeline || {}),
                datasets: [{ label: 'Daily Spending',
                    data: Object.values(data.timeline || {}),
                    borderColor: '#D4A853', backgroundColor: 'rgba(212,168,83,0.1)',
                    fill: true, tension: 0.3, pointRadius: 3 }] },
            options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }
        });
    }

    // Category chart
    const cc = document.getElementById('homeCategoryChart');
    if (cc) {
        const colors = ['#1B3A5C','#2E5A88','#4A7FB5','#89B4D4','#D4A853','#E8C97A','#27AE60','#C0392B','#8E44AD','#F39C12'];
        chartInstances.homeCategory = new Chart(cc, {
            type: 'doughnut',
            data: { labels: Object.keys(data.categoryTotals || {}),
                datasets: [{ data: Object.values(data.categoryTotals || {}), backgroundColor: colors }] },
            options: { responsive: true, maintainAspectRatio: false }
        });
    }

    // Top Shops
    renderTopShops('homeTopShops', data.topShops);

    // Top Items
    renderTopItems('homeTopItems', data.topItems);

    // Recent expenses
    renderRecentExpenses('homeRecentExpenses', 5);

    // Geo map
    renderGeoMap('homeGeoMap', data);
}

function updateHomeCharts() {
    const data = _homeData;
    if (!data) return;
    const period = document.getElementById('homeTotalsPeriod')?.value || 'monthly';
    const baseCur = currentUser?.baseCurrency || 'USD';
    const mc = document.getElementById('homeMonthlyChart');
    if (!mc) return;
    if (chartInstances.homeMonthly) { chartInstances.homeMonthly.destroy(); delete chartInstances.homeMonthly; }
    const totalsData = period === 'weekly' ? data.weeklyTotals : period === 'annual' ? data.annualTotals : data.monthlyTotals;
    chartInstances.homeMonthly = new Chart(mc, {
        type: 'bar',
        data: { labels: Object.keys(totalsData || {}),
            datasets: [{ label: 'Total (' + baseCur + ')',
                data: Object.values(totalsData || {}),
                backgroundColor: 'rgba(46, 90, 136, 0.7)', borderRadius: 6 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }
    });
}

// ============================================
// DASHBOARD (Desktop full version)
// ============================================
async function renderDashboard(app) {
    app.innerHTML = `
    <div class="container">
        <div class="feed-card hero-card" id="desktopHeroCard" style="margin-bottom:1.5rem;">
            <div class="hero-loading"><i class="fa-solid fa-spinner fa-spin"></i> Loading...</div>
        </div>
        <div class="dashboard-grid">
            <div class="card">
                <div class="card-title-row">
                    <h3 class="card-title"><i class="fa-solid fa-chart-column"></i> Totals</h3>
                    <select class="chart-period-select" id="deskTotalsPeriod" onchange="updateDesktopCharts()">
                        <option value="monthly" selected>Monthly</option>
                        <option value="weekly">Weekly</option>
                        <option value="annual">Annual</option>
                    </select>
                </div>
                <div class="chart-container"><canvas id="monthlyChart"></canvas></div>
            </div>
            <div class="card">
                <h3 class="card-title"><i class="fa-solid fa-chart-pie"></i> By Category</h3>
                <div class="chart-container"><canvas id="categoryChart"></canvas></div>
            </div>
            <div class="card">
                <h3 class="card-title"><i class="fa-solid fa-chart-line"></i> Daily Spending</h3>
                <div class="chart-container"><canvas id="timelineChart"></canvas></div>
            </div>
            <div class="card">
                <h3 class="card-title"><i class="fa-solid fa-map-location-dot"></i> Geographical Map</h3>
                <div id="geoMap"></div>
            </div>
            <div class="card">
                <h3 class="card-title"><i class="fa-solid fa-store"></i> Most Visited Shops</h3>
                <div id="deskTopShops"></div>
            </div>
            <div class="card">
                <h3 class="card-title"><i class="fa-solid fa-basket-shopping"></i> Most Bought Items</h3>
                <div id="deskTopItems"></div>
            </div>
            <div class="card" style="grid-column: 1 / -1;">
                <h3 class="card-title"><i class="fa-solid fa-clock-rotate-left"></i> Recent Expenses</h3>
                <div id="deskRecentExpenses"></div>
            </div>
        </div>
        <div id="deskDiscoveryCards" class="discovery-section"></div>
        <div id="deskDiscoveryLoading" class="discovery-load-trigger" style="height:60px;"></div>
    </div>`;
    await loadDashboardDesktop();
}

async function loadDashboardDesktop() {
    const data = await api('/api/dashboard');
    if (!data) return;
    _desktopData = data;

    // Desktop hero card (global filter)
    renderHeroCard('desktopHeroCard', data, 'deskHeroPeriod', 'desktop');

    Object.values(chartInstances).forEach(c => c.destroy && c.destroy());
    chartInstances = {};

    // Load filtered data for current/latest month
    const currentMonth = getCurrentOrLatestMonth(data);
    const params = buildParamsFromPeriod('month:' + currentMonth);
    const filtered = await api('/api/dashboard' + (params ? '?' + params : ''));
    if (!filtered) return;

    renderFilteredDesktopData(filtered);

    // Discovery cards from unfiltered data
    renderDiscoveryCards('deskDiscoveryCards', data.discoveryCards);
    initDiscoveryScroll(null, 'deskDiscoveryCards', 'deskDiscoveryLoading', data.discoveryCards);
}

function renderFilteredDesktopData(data) {
    updateDesktopCharts();

    const colors = ['#1B3A5C','#2E5A88','#4A7FB5','#89B4D4','#D4A853','#E8C97A','#27AE60','#C0392B','#8E44AD','#F39C12'];

    const cc = document.getElementById('categoryChart');
    if (cc) {
        chartInstances.category = new Chart(cc, {
            type: 'doughnut',
            data: { labels: Object.keys(data.categoryTotals || {}),
                datasets: [{ data: Object.values(data.categoryTotals || {}), backgroundColor: colors }] },
            options: { responsive: true, maintainAspectRatio: false }
        });
    }

    const tc = document.getElementById('timelineChart');
    if (tc) {
        chartInstances.timeline = new Chart(tc, {
            type: 'line',
            data: { labels: Object.keys(data.timeline || {}),
                datasets: [{ label: 'Daily Spending',
                    data: Object.values(data.timeline || {}),
                    borderColor: '#D4A853', backgroundColor: 'rgba(212,168,83,0.1)',
                    fill: true, tension: 0.3, pointRadius: 4 }] },
            options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }
        });
    }

    // Geo map with country aggregation
    renderGeoMap('geoMap', data);

    // Top shops & items
    renderTopShops('deskTopShops', data.topShops);
    renderTopItems('deskTopItems', data.topItems);

    // Recent expenses (desktop)
    renderRecentExpenses('deskRecentExpenses', 8);
}

function updateDesktopCharts() {
    const data = _desktopData;
    if (!data) return;
    const period = document.getElementById('deskTotalsPeriod')?.value || 'monthly';
    const baseCur = currentUser?.baseCurrency || 'USD';
    const mc = document.getElementById('monthlyChart');
    if (!mc) return;
    if (chartInstances.monthly) { chartInstances.monthly.destroy(); delete chartInstances.monthly; }
    const totalsData = period === 'weekly' ? data.weeklyTotals : period === 'annual' ? data.annualTotals : data.monthlyTotals;
    chartInstances.monthly = new Chart(mc, {
        type: 'bar',
        data: { labels: Object.keys(totalsData || {}),
            datasets: [{ label: 'Total (' + baseCur + ')',
                data: Object.values(totalsData || {}),
                backgroundColor: 'rgba(46, 90, 136, 0.7)', borderRadius: 6 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }
    });
}

// ============================================
// SHARED COMPONENTS
// ============================================

/**
 * Render hero card with period dropdown that acts as global filter.
 * mode = 'home' | 'desktop'
 */
function renderHeroCard(elementId, data, selectId, mode) {
    const baseCur = currentUser?.baseCurrency || 'USD';
    const heroCard = document.getElementById(elementId);
    if (!heroCard) return;

    const monthly = data.monthlyTotals || {};
    const annual = data.annualTotals || {};

    // Build month/year options from data
    const monthKeys = Object.keys(monthly).sort().reverse();
    const yearKeys = Object.keys(annual).sort().reverse();

    // Default to current or latest available month
    const defaultMonth = getCurrentOrLatestMonth(data);

    // Compute initial stats for default month
    const defaultAmt = Number(monthly[defaultMonth] || 0);
    const periodInfo = computePeriodStats(data, 'month:' + defaultMonth);

    heroCard.innerHTML = `
        <div class="hero-header-row">
            <div class="hero-title"><i class="fa-solid fa-chart-simple"></i> Summary</div>
            <select class="hero-period-select interactive-element" id="${selectId}"
                    onchange="onHeroPeriodChange('${elementId}', '${selectId}', '${mode}')">
                <optgroup label="Monthly">
                    ${monthKeys.map(m => `<option value="month:${m}" ${m === defaultMonth ? 'selected' : ''}>${formatYearMonth(m)}</option>`).join('')}
                </optgroup>
                <optgroup label="Annual">
                    ${yearKeys.map(y => `<option value="year:${y}">${y}</option>`).join('')}
                </optgroup>
                <option value="all">All Time</option>
            </select>
        </div>
        <div class="hero-amount" id="${elementId}Amount">${defaultAmt.toFixed(2)} <span class="hero-currency">${baseCur}</span></div>
        <div class="hero-stats" id="${elementId}Stats">
            <div class="hero-stat"><span class="hero-stat-value" id="${elementId}TxCount">${periodInfo.txCount}</span><span class="hero-stat-label">transactions</span></div>
            <div class="hero-stat"><span class="hero-stat-value" id="${elementId}TopCat">${periodInfo.topCategory}</span><span class="hero-stat-label">top category</span></div>
        </div>`;

    heroCard._dashData = data;
}

/**
 * Compute stats for a specific hero period value
 */
function computePeriodStats(data, periodVal) {
    let txCount = data.totalExpenses || 0;
    let topCategory = getTopCategory(data.categoryTotals);

    // For period-specific stats, we use the per-period breakdowns from the backend
    if (periodVal === 'all') {
        // all-time: use the full data as-is
    } else if (periodVal.startsWith('month:')) {
        const m = periodVal.split(':')[1];
        // Count from perMonthDetails if available, else fallback
        txCount = data.perMonthTxCount?.[m] || '—';
        topCategory = data.perMonthTopCategory?.[m] || getTopCategory(data.categoryTotals);
    } else if (periodVal.startsWith('year:')) {
        const y = periodVal.split(':')[1];
        txCount = data.perYearTxCount?.[y] || '—';
        topCategory = data.perYearTopCategory?.[y] || getTopCategory(data.categoryTotals);
    }

    return { txCount, topCategory };
}

/**
 * Called when hero period dropdown changes. Updates hero stats + re-fetches data for all cards.
 */
function onHeroPeriodChange(elementId, selectId, mode) {
    const heroCard = document.getElementById(elementId);
    if (!heroCard || !heroCard._dashData) return;
    const sel = document.getElementById(selectId);
    if (!sel) return;
    const val = sel.value;
    const data = heroCard._dashData;
    const baseCur = currentUser?.baseCurrency || 'USD';

    // Update hero amount
    let amount = 0;
    if (val === 'all') {
        amount = Object.values(data.monthlyTotals || {}).reduce((a, b) => a + Number(b), 0);
    } else if (val.startsWith('month:')) {
        const m = val.split(':')[1];
        amount = Number(data.monthlyTotals?.[m] || 0);
    } else if (val.startsWith('year:')) {
        const y = val.split(':')[1];
        amount = Number(data.annualTotals?.[y] || 0);
    }
    const amtEl = document.getElementById(elementId + 'Amount');
    if (amtEl) amtEl.innerHTML = `${amount.toFixed(2)} <span class="hero-currency">${baseCur}</span>`;

    // Update hero stats
    const periodInfo = computePeriodStats(data, val);
    const txEl = document.getElementById(elementId + 'TxCount');
    if (txEl) txEl.textContent = periodInfo.txCount;
    const catEl = document.getElementById(elementId + 'TopCat');
    if (catEl) catEl.textContent = periodInfo.topCategory;

    // Re-fetch dashboard data with the selected period as date filter
    const params = buildParamsFromPeriod(val);
    if (mode === 'home') {
        reloadHomeWithFilter(params);
    } else {
        reloadDesktopWithFilter(params);
    }
}

/**
 * Build URL params from a hero period value
 */
function buildParamsFromPeriod(val) {
    const params = new URLSearchParams();
    if (val === 'all') {
        // no date filter
    } else if (val.startsWith('month:')) {
        const ym = val.split(':')[1]; // e.g. "2026-03"
        const [y, m] = ym.split('-');
        const lastDay = new Date(parseInt(y), parseInt(m), 0).getDate();
        params.set('startDate', `${ym}-01`);
        params.set('endDate', `${ym}-${String(lastDay).padStart(2, '0')}`);
    } else if (val.startsWith('year:')) {
        const y = val.split(':')[1];
        params.set('startDate', `${y}-01-01`);
        params.set('endDate', `${y}-12-31`);
    }
    return params.toString();
}

/**
 * Reload home (mobile) dashboard with a filter string, keeping the hero dropdown intact
 */
async function reloadHomeWithFilter(params) {
    const data = await api('/api/dashboard' + (params ? '?' + params : ''));
    if (!data) return;
    _homeData = data;

    // Re-render charts
    if (chartInstances.homeMonthly) { chartInstances.homeMonthly.destroy(); delete chartInstances.homeMonthly; }
    if (chartInstances.homeTimeline) { chartInstances.homeTimeline.destroy(); delete chartInstances.homeTimeline; }
    if (chartInstances.homeCategory) { chartInstances.homeCategory.destroy(); delete chartInstances.homeCategory; }

    updateHomeCharts();

    const tc = document.getElementById('homeTimelineChart');
    if (tc) {
        chartInstances.homeTimeline = new Chart(tc, {
            type: 'line',
            data: { labels: Object.keys(data.timeline || {}),
                datasets: [{ label: 'Daily Spending',
                    data: Object.values(data.timeline || {}),
                    borderColor: '#D4A853', backgroundColor: 'rgba(212,168,83,0.1)',
                    fill: true, tension: 0.3, pointRadius: 3 }] },
            options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }
        });
    }

    const cc = document.getElementById('homeCategoryChart');
    if (cc) {
        const colors = ['#1B3A5C','#2E5A88','#4A7FB5','#89B4D4','#D4A853','#E8C97A','#27AE60','#C0392B','#8E44AD','#F39C12'];
        chartInstances.homeCategory = new Chart(cc, {
            type: 'doughnut',
            data: { labels: Object.keys(data.categoryTotals || {}),
                datasets: [{ data: Object.values(data.categoryTotals || {}), backgroundColor: colors }] },
            options: { responsive: true, maintainAspectRatio: false }
        });
    }

    renderTopShops('homeTopShops', data.topShops);
    renderTopItems('homeTopItems', data.topItems);
    renderRecentExpenses('homeRecentExpenses', 5);
    renderGeoMap('homeGeoMap', data);
}

/**
 * Reload desktop dashboard with a filter string, keeping the hero dropdown intact
 */
async function reloadDesktopWithFilter(params) {
    const data = await api('/api/dashboard' + (params ? '?' + params : ''));
    if (!data) return;
    _desktopData = data;

    // Re-render charts
    if (chartInstances.monthly) { chartInstances.monthly.destroy(); delete chartInstances.monthly; }
    if (chartInstances.timeline) { chartInstances.timeline.destroy(); delete chartInstances.timeline; }
    if (chartInstances.category) { chartInstances.category.destroy(); delete chartInstances.category; }

    updateDesktopCharts();

    const colors = ['#1B3A5C','#2E5A88','#4A7FB5','#89B4D4','#D4A853','#E8C97A','#27AE60','#C0392B','#8E44AD','#F39C12'];

    const cc = document.getElementById('categoryChart');
    if (cc) {
        chartInstances.category = new Chart(cc, {
            type: 'doughnut',
            data: { labels: Object.keys(data.categoryTotals || {}),
                datasets: [{ data: Object.values(data.categoryTotals || {}), backgroundColor: colors }] },
            options: { responsive: true, maintainAspectRatio: false }
        });
    }

    const tc = document.getElementById('timelineChart');
    if (tc) {
        chartInstances.timeline = new Chart(tc, {
            type: 'line',
            data: { labels: Object.keys(data.timeline || {}),
                datasets: [{ label: 'Daily Spending',
                    data: Object.values(data.timeline || {}),
                    borderColor: '#D4A853', backgroundColor: 'rgba(212,168,83,0.1)',
                    fill: true, tension: 0.3, pointRadius: 4 }] },
            options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }
        });
    }

    renderGeoMap('geoMap', data);
    renderTopShops('deskTopShops', data.topShops);
    renderTopItems('deskTopItems', data.topItems);
    renderRecentExpenses('deskRecentExpenses', 8);
}

/**
 * Render recent expenses using displayName
 */
async function renderRecentExpenses(elementId, count) {
    const el = document.getElementById(elementId);
    if (!el) return;
    const allExpenses = await api('/api/expenses');
    if (allExpenses && Array.isArray(allExpenses)) {
        const recent = allExpenses.slice(0, count);
        el.innerHTML = recent.length ? recent.map(e => `
            <a href="#/expenses/${e.id}" class="expense-mini-card">
                <div class="mini-card-icon"><i class="fa-solid fa-${categoryIcon(e.category)}"></i></div>
                <div class="mini-card-info">
                    <div class="mini-card-category">${e.displayName || e.category || 'Uncategorized'}</div>
                    <div class="mini-card-date">${e.transactionDatetime ? new Date(e.transactionDatetime).toLocaleDateString() : '-'}</div>
                </div>
                <div class="mini-card-amount">${e.amount != null ? Number(e.amount).toFixed(2) : '-'} ${e.currency || ''}</div>
            </a>`).join('') : '<p style="color:var(--text-light); text-align:center; padding:1rem;">No expenses yet</p>';
    }
}

function formatYearMonth(ym) {
    if (!ym) return '';
    const [y, m] = ym.split('-');
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return months[parseInt(m) - 1] + ' ' + y;
}

function getTopCategory(categoryTotals) {
    const top = Object.entries(categoryTotals || {}).sort((a, b) => Number(b[1]) - Number(a[1]))[0];
    return top ? top[0] : '-';
}

/**
 * Render top shops card
 */
function renderTopShops(elementId, topShops) {
    const el = document.getElementById(elementId);
    if (!el) return;
    if (!topShops || topShops.length === 0) {
        el.innerHTML = '<p style="color:var(--text-light); text-align:center; padding:1rem;">No shop data yet</p>';
        return;
    }
    el.innerHTML = topShops.map((s, i) => `
        <div class="rank-row">
            <span class="rank-badge">${i + 1}</span>
            <span class="rank-name">${s.name}</span>
            <span class="rank-value">${s.visits} visit${s.visits > 1 ? 's' : ''}</span>
        </div>`).join('');
}

/**
 * Render top items card
 */
function renderTopItems(elementId, topItems) {
    const el = document.getElementById(elementId);
    if (!el) return;
    if (!topItems || topItems.length === 0) {
        el.innerHTML = '<p style="color:var(--text-light); text-align:center; padding:1rem;">No item data yet</p>';
        return;
    }
    el.innerHTML = topItems.map((item, i) => `
        <div class="rank-row">
            <span class="rank-badge">${i + 1}</span>
            <span class="rank-name">${item.name}</span>
            <span class="rank-value">\u00d7${Number(item.count).toFixed(0)}</span>
        </div>`).join('');
}

/**
 * Render most expensive expenses as clickable cards
 */
function renderTopExpenses(elementId, topExpenses) {
    const el = document.getElementById(elementId);
    if (!el) return;
    if (!topExpenses || topExpenses.length === 0) {
        el.innerHTML = '<p style="color:var(--text-light); text-align:center; padding:1rem;">No expense data yet</p>';
        return;
    }
    const baseCur = currentUser?.baseCurrency || 'USD';
    el.innerHTML = topExpenses.map((e, i) => {
        const date = e.transactionDatetime ? new Date(e.transactionDatetime).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '';
        const baseAmt = e.amountInBase != null ? Number(e.amountInBase).toFixed(2) : (e.amount != null ? Number(e.amount).toFixed(2) : '-');
        const origAmt = e.amount != null ? Number(e.amount).toFixed(2) : '-';
        const sameAsCur = e.currency === baseCur;
        return `<a href="#/expenses/${e.id}" class="top-expense-card">
            <span class="rank-badge">${i + 1}</span>
            <div class="top-expense-info">
                <div class="top-expense-name">${e.displayName || e.category || 'Uncategorized'}</div>
                <div class="top-expense-date">${date}</div>
            </div>
            <div class="top-expense-amount">
                <span class="top-expense-base">${baseAmt} ${baseCur}</span>
                ${!sameAsCur ? `<span class="top-expense-orig">${origAmt} ${e.currency}</span>` : ''}
            </div>
        </a>`;
    }).join('');
}

/**
 * Render geo map with country details.
 * Fixed: check map container is visible before invalidateSize.
 */
let _geoMaps = {};
function renderGeoMap(elementId, data) {
    const mapEl = document.getElementById(elementId);
    if (!mapEl) return;

    // Destroy existing map if any
    if (_geoMaps[elementId]) {
        try { _geoMaps[elementId].remove(); } catch (e) { /* ignore */ }
        delete _geoMaps[elementId];
    }

    // Ensure the container has dimensions before initializing
    if (mapEl.offsetWidth === 0 && mapEl.offsetHeight === 0) {
        // Container not visible yet, wait and retry
        setTimeout(() => renderGeoMap(elementId, data), 300);
        return;
    }

    const map = L.map(elementId).setView([20, 0], 2);
    _geoMaps[elementId] = map;
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '\u00a9 OpenStreetMap'
    }).addTo(map);

    const baseCur = currentUser?.baseCurrency || 'USD';

    // Show country-level aggregated markers
    const geoByCountry = data.geoByCountry || [];
    geoByCountry.forEach(c => {
        if (c.lat == null || c.lng == null || (c.lat === 0 && c.lng === 0)) return;
        const total = Number(c.total).toFixed(2);
        const name = c.countryName || c.country || 'Unknown';
        const countryIcon = L.divIcon({
            className: 'geo-country-marker',
            html: `<div class="geo-country-dot">${c.count}</div>`,
            iconSize: [32, 32],
            iconAnchor: [16, 16]
        });
        L.marker([c.lat, c.lng], { icon: countryIcon }).addTo(map)
            .bindPopup(`
                <div class="geo-popup">
                    <b>${name}</b><br>
                    <span class="geo-popup-row"><i class="fa-solid fa-calendar"></i> ${formatDateShort(c.minDate)} \u2014 ${formatDateShort(c.maxDate)}</span><br>
                    <span class="geo-popup-row"><i class="fa-solid fa-money-bill"></i> ${total} ${baseCur}</span><br>
                    <span class="geo-popup-row"><i class="fa-solid fa-receipt"></i> ${c.count} transaction${c.count > 1 ? 's' : ''}</span>
                </div>`);
    });

    // Also show individual markers (skip if no valid coords)
    (data.geoData || []).forEach(p => {
        if (p.lat == null || p.lng == null || (p.lat === 0 && p.lng === 0)) return;
        L.marker([p.lat, p.lng]).addTo(map)
            .bindPopup(`<b>${p.name || 'Store'}</b><br>${p.amount} ${p.currency || ''}<br>${p.date || ''}`);
    });

    const allPoints = [...(data.geoData || []), ...geoByCountry].filter(p => p.lat && p.lng && !(p.lat === 0 && p.lng === 0));
    if (allPoints.length > 0) {
        const bounds = L.latLngBounds(allPoints.map(p => [p.lat, p.lng]));
        map.fitBounds(bounds, { padding: [50, 50] });
    }

    // Safe invalidateSize: only if container is still in DOM and visible
    setTimeout(() => {
        if (_geoMaps[elementId] && mapEl.offsetWidth > 0 && mapEl.offsetHeight > 0) {
            try { _geoMaps[elementId].invalidateSize(); } catch (e) { /* ignore */ }
        }
    }, 500);
}


/**
 * Discovery cards (infinite scroll)
 */
let _discoveryIndex = {};
let _discoveryObserver = null;

function renderDiscoveryCards(containerId, cards) {
    const el = document.getElementById(containerId);
    if (!el) return;
    _discoveryIndex[containerId] = 0;
    el.innerHTML = '';
    if (!cards || cards.length === 0) return;
    appendDiscoveryBatch(containerId, cards, 3);
}

function appendDiscoveryBatch(containerId, cards, count) {
    const el = document.getElementById(containerId);
    if (!el || !cards) return;
    const startIdx = _discoveryIndex[containerId] || 0;
    const baseCur = currentUser?.baseCurrency || 'USD';
    const iconsPool = ['fa-plane','fa-globe','fa-map-pin','fa-compass','fa-earth-americas','fa-earth-europe','fa-earth-asia','fa-suitcase'];
    const colorsPool = ['#1B3A5C','#2E5A88','#4A7FB5','#27AE60','#8E44AD','#C0392B','#E67E22','#16A085'];

    for (let i = 0; i < count && startIdx + i < cards.length; i++) {
        const card = cards[startIdx + i];
        const icon = iconsPool[(startIdx + i) % iconsPool.length];
        const accentColor = colorsPool[(startIdx + i) % colorsPool.length];
        const total = Number(card.total).toFixed(2);
        const shopList = (card.shops || []).join(', ');
        const daysStayed = card.daysStayed || 1;

        // Show original currency total if different from base currency
        let originalLine = '';
        if (card.originalCurrency && card.originalCurrency !== baseCur && card.originalTotal != null) {
            originalLine = `<span style="font-size:0.75rem; opacity:0.75;">(${Number(card.originalTotal).toFixed(2)} ${card.originalCurrency})</span>`;
        }

        const div = document.createElement('div');
        div.className = 'feed-card discovery-card';

        // Top expenses for this card
        const topExp = card.topExpenses || [];
        let topExpHtml = '';
        if (topExp.length > 0) {
            topExpHtml = `<div class="discovery-top-expenses">
                <div class="discovery-top-exp-label"><i class="fa-solid fa-arrow-trend-up"></i> Top expenses</div>
                ${topExp.map(e => {
                    const base = e.amountInBase != null ? Number(e.amountInBase).toFixed(2) : (e.amount != null ? Number(e.amount).toFixed(2) : '-');
                    const orig = (e.currency && e.currency !== baseCur && e.amount != null)
                        ? ` <span class="discovery-exp-orig">(${Number(e.amount).toFixed(2)} ${e.currency})</span>` : '';
                    return `<a href="#/expenses/${e.id}" class="discovery-exp-row">
                        <span class="discovery-exp-name">${e.displayName || 'Expense'}</span>
                        <span class="discovery-exp-amount">${base} ${baseCur}${orig}</span>
                    </a>`;
                }).join('')}
            </div>`;
        }

        div.innerHTML = `
            <div class="discovery-header" style="border-left-color:${accentColor}">
                <div class="discovery-icon" style="background:${accentColor}15; color:${accentColor}"><i class="fa-solid ${icon}"></i></div>
                <div class="discovery-title-area">
                    <div class="discovery-title">${card.locationLabel || card.countryName || card.country || 'Unknown'}</div>
                    <div class="discovery-subtitle">${card.month} ${card.year}</div>
                </div>
            </div>
            <div class="discovery-metrics">
                <div class="discovery-metric">
                    <span class="discovery-metric-value">${total}</span>
                    <span class="discovery-metric-label">${baseCur} spent</span>
                    ${originalLine ? `<span class="discovery-metric-orig">${originalLine}</span>` : ''}
                </div>
                <div class="discovery-metric">
                    <span class="discovery-metric-value">${card.count}</span>
                    <span class="discovery-metric-label">expense${card.count > 1 ? 's' : ''}</span>
                </div>
                <div class="discovery-metric">
                    <span class="discovery-metric-value">${daysStayed}</span>
                    <span class="discovery-metric-label">day${daysStayed > 1 ? 's' : ''} visited</span>
                </div>
            </div>
            ${topExpHtml}
            ${shopList ? `<div class="discovery-shops"><i class="fa-solid fa-store"></i> ${shopList}</div>` : ''}`;
        el.appendChild(div);
    }
    _discoveryIndex[containerId] = startIdx + count;
}

function initDiscoveryScroll(scrollContainerId, cardsContainerId, loadingId, cards) {
    if (!cards || cards.length === 0) {
        const loadEl = document.getElementById(loadingId);
        if (loadEl) loadEl.style.display = 'none';
        return;
    }

    const loadEl = document.getElementById(loadingId);
    if (!loadEl) return;

    if (_discoveryObserver) _discoveryObserver.disconnect();

    // Determine the scroll root: for mobile home feed, use the parent swipe-panel;
    // for desktop or null container, use null (viewport).
    let rootEl = null;
    if (scrollContainerId) {
        const container = document.getElementById(scrollContainerId);
        if (container) {
            // Walk up to find the actual scrolling parent (.swipe-panel or the container itself)
            let scrollParent = container.closest('.swipe-panel');
            rootEl = scrollParent || null;
        }
    }

    _discoveryObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const idx = _discoveryIndex[cardsContainerId] || 0;
                if (idx < cards.length) {
                    appendDiscoveryBatch(cardsContainerId, cards, 2);
                } else {
                    loadEl.innerHTML = '<p style="color:var(--text-light); text-align:center; font-size:0.8rem;">No more discoveries</p>';
                    _discoveryObserver.disconnect();
                }
            }
        });
    }, {
        root: rootEl,
        rootMargin: '400px',
        threshold: 0
    });
    _discoveryObserver.observe(loadEl);
}
