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

    renderHomeData(filtered);

    // Discovery cards (infinite scroll) — from unfiltered data
    renderDiscoveryCards('homeDiscoveryCards', data.discoveryCards);
    initDiscoveryScroll('homeFeed', 'homeDiscoveryCards', 'homeDiscoveryLoading', data.discoveryCards);
}

/**
 * Render all home panel charts and widgets from dashboard data.
 * Used by both initial load and hero-period filter changes.
 */
function renderHomeData(data) {

    updateHomeCharts();
    createTimelineChart('homeTimelineChart', 'homeTimeline', data);
    createCategoryChart('homeCategoryChart', 'homeCategory', data);
    renderTopShops('homeTopShops', data.topShops);
    renderTopItems('homeTopItems', data.topItems);
    renderRecentExpenses('homeRecentExpenses', 5);
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

async function reloadHomeWithFilter(params) {
    const data = await api('/api/dashboard' + (params ? '?' + params : ''));
    if (!data) return;
    _homeData = data;

    if (chartInstances.homeMonthly) { chartInstances.homeMonthly.destroy(); delete chartInstances.homeMonthly; }
    if (chartInstances.homeTimeline) { chartInstances.homeTimeline.destroy(); delete chartInstances.homeTimeline; }
    if (chartInstances.homeCategory) { chartInstances.homeCategory.destroy(); delete chartInstances.homeCategory; }

    renderHomeData(data);
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

    renderDesktopData(filtered);

    // Discovery cards from unfiltered data
    renderDiscoveryCards('deskDiscoveryCards', data.discoveryCards);
    initDiscoveryScroll(null, 'deskDiscoveryCards', 'deskDiscoveryLoading', data.discoveryCards);
}

/**
 * Render all desktop dashboard charts and widgets from data.
 * Used by both initial load and hero-period filter changes.
 */
function renderDesktopData(data) {
    updateDesktopCharts();
    createCategoryChart('categoryChart', 'category', data);
    createTimelineChart('timelineChart', 'timeline', data);
    renderGeoMap('geoMap', data);
    renderTopShops('deskTopShops', data.topShops);
    renderTopItems('deskTopItems', data.topItems);
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

async function reloadDesktopWithFilter(params) {
    const data = await api('/api/dashboard' + (params ? '?' + params : ''));
    if (!data) return;
    _desktopData = data;

    if (chartInstances.monthly) { chartInstances.monthly.destroy(); delete chartInstances.monthly; }
    if (chartInstances.timeline) { chartInstances.timeline.destroy(); delete chartInstances.timeline; }
    if (chartInstances.category) { chartInstances.category.destroy(); delete chartInstances.category; }

    renderDesktopData(data);
}
