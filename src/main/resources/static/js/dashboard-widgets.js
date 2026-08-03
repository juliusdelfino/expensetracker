/* ============================================
   Expense Tracker - Dashboard Widgets
   (Shared: hero card, charts, top lists, geo map, discovery cards)
   ============================================ */

// ============================================
// HERO CARD
// ============================================

// Global additional filters state
let _dashFilters = {};

function renderHeroCard(elementId, data, selectId, mode) {
    const baseCur = currentUser?.baseCurrency || 'USD';
    const heroCard = document.getElementById(elementId);
    if (!heroCard) return;

    const monthly = data.monthlyTotals || {};
    const annual = data.annualTotals || {};
    const monthKeys = Object.keys(monthly).sort().reverse();
    const yearKeys = Object.keys(annual).sort().reverse();
    const defaultMonth = getCurrentOrLatestMonth(data);
    const defaultAmt = Number(monthly[defaultMonth] || 0);
    const periodInfo = computePeriodStats(data, 'month:' + defaultMonth);

    heroCard.innerHTML = `
        <div class="hero-header-row">
            <div class="hero-title"><i class="fa-solid fa-chart-simple"></i> Summary</div>
            <div class="hero-controls">
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
                <button class="btn btn-outline btn-sm" onclick="openDashFilterDialog('${selectId}','${mode}')" title="More filters">
                    <i class="fa-solid fa-filter"></i>
                </button>
            </div>
        </div>
        <div class="hero-filter-badges" id="${elementId}Badges">${renderFilterBadges(mode)}</div>
        <div class="hero-amount" id="${elementId}Amount">${defaultAmt.toFixed(2)} <span class="hero-currency">${baseCur}</span></div>
        <div class="hero-stats" id="${elementId}Stats">
            <div class="hero-stat"><span class="hero-stat-value" id="${elementId}TxCount">${periodInfo.txCount}</span><span class="hero-stat-label">transactions</span></div>
            <div class="hero-stat"><span class="hero-stat-value" id="${elementId}TopCat">${periodInfo.topCategory}</span><span class="hero-stat-label">top category</span></div>
        </div>`;
    heroCard._dashData = data;
}

function computePeriodStats(data, periodVal) {
    let txCount = data.totalExpenses || 0;
    let topCategory = getTopCategory(data.categoryTotals);

    if (periodVal === 'all') {
        // use full data
    } else if (periodVal.startsWith('month:')) {
        const m = periodVal.split(':')[1];
        txCount = data.perMonthTxCount?.[m] || '\u2014';
        topCategory = data.perMonthTopCategory?.[m] || getTopCategory(data.categoryTotals);
    } else if (periodVal.startsWith('year:')) {
        const y = periodVal.split(':')[1];
        txCount = data.perYearTxCount?.[y] || '\u2014';
        topCategory = data.perYearTopCategory?.[y] || getTopCategory(data.categoryTotals);
    }
    return { txCount, topCategory };
}

function onHeroPeriodChange(elementId, selectId, mode) {
    const heroCard = document.getElementById(elementId);
    if (!heroCard || !heroCard._dashData) return;
    const sel = document.getElementById(selectId);
    if (!sel) return;
    const val = sel.value;
    const baseCur = currentUser?.baseCurrency || 'USD';

    // Update amount from base (unfiltered) data optimistically while the filtered call loads
    const data = heroCard._dashData;
    let amount = 0;
    if (val === 'all') {
        amount = Object.values(data.monthlyTotals || {}).reduce((a, b) => a + Number(b), 0);
    } else if (val.startsWith('month:')) {
        amount = Number(data.monthlyTotals?.[val.split(':')[1]] || 0);
    } else if (val.startsWith('year:')) {
        amount = Number(data.annualTotals?.[val.split(':')[1]] || 0);
    }
    const amtEl = document.getElementById(elementId + 'Amount');
    if (amtEl) amtEl.innerHTML = `${amount.toFixed(2)} <span class="hero-currency">${baseCur}</span>`;

    const periodInfo = computePeriodStats(data, val);
    const txEl = document.getElementById(elementId + 'TxCount');
    if (txEl) txEl.textContent = periodInfo.txCount;
    const catEl = document.getElementById(elementId + 'TopCat');
    if (catEl) catEl.textContent = periodInfo.topCategory;

    const params = buildFullFilterParams(val);
    if (mode === 'home') reloadHomeWithFilter(params);
    else reloadDesktopWithFilter(params);
}

/**
 * After a filtered dashboard API call, update the hero card amount and stats
 * using the filtered data so additional filters (category, country, etc.) are reflected.
 */
function updateHeroFromFilteredData(elementId, selectId, filteredData) {
    const sel = document.getElementById(selectId);
    if (!sel) return;
    const val = sel.value;
    const baseCur = currentUser?.baseCurrency || 'USD';

    // Derive amount from the filtered totals
    let amount = 0;
    if (val === 'all') {
        amount = Object.values(filteredData.monthlyTotals || {}).reduce((a, b) => a + Number(b), 0);
    } else if (val.startsWith('month:')) {
        amount = Number(filteredData.monthlyTotals?.[val.split(':')[1]] || 0);
    } else if (val.startsWith('year:')) {
        amount = Number(filteredData.annualTotals?.[val.split(':')[1]] || 0);
    }
    const amtEl = document.getElementById(elementId + 'Amount');
    if (amtEl) amtEl.innerHTML = `${amount.toFixed(2)} <span class="hero-currency">${baseCur}</span>`;

    // Stats from filtered data
    const txCount = filteredData.totalExpenses ?? '\u2014';
    const topCategory = getTopCategory(filteredData.categoryTotals);
    const txEl = document.getElementById(elementId + 'TxCount');
    if (txEl) txEl.textContent = txCount;
    const catEl = document.getElementById(elementId + 'TopCat');
    if (catEl) catEl.textContent = topCategory;
}

function buildParamsFromPeriod(val) {
    const params = new URLSearchParams();
    if (val === 'all') {
        // no date filter
    } else if (val.startsWith('month:')) {
        const ym = val.split(':')[1];
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

function getCurrentOrLatestMonth(data) {
    const now = new Date();
    const currentMonth = now.toISOString().slice(0, 7);
    const monthly = data.monthlyTotals || {};
    const monthKeys = Object.keys(monthly).sort().reverse();
    if (monthly[currentMonth] !== undefined) return currentMonth;
    return monthKeys.length > 0 ? monthKeys[0] : currentMonth;
}

// ============================================
// CHART HELPERS (deduplicated)
// ============================================
const CHART_COLORS = ['#1565C0','#1976D2','#42A5F5','#7C4DFF','#FF7043','#66BB6A','#388E3C','#D32F2F','#AB47BC','#F9A825'];

function getChartThemeColors() {
    const style = getComputedStyle(document.documentElement);
    const textColor = style.getPropertyValue('--chart-text-color').trim() || '#666';
    const gridColor = style.getPropertyValue('--chart-grid-color').trim() || 'rgba(0,0,0,0.1)';
    const cardBg = style.getPropertyValue('--bg-card').trim() || '#fff';
    return { textColor, gridColor, cardBg };
}

function chartScaleOptions() {
    const { textColor, gridColor } = getChartThemeColors();
    return {
        x: { ticks: { color: textColor }, grid: { color: gridColor } },
        y: { ticks: { color: textColor }, grid: { color: gridColor } }
    };
}

function chartPluginOptions() {
    const { textColor } = getChartThemeColors();
    return {
        legend: { labels: { color: textColor } },
        tooltip: {}
    };
}

/**
 * Update a chart status bar element with clicked datapoint details and a "View expenses" link.
 */
function updateChartStatusBar(statusBarId, label, value, navUrl) {
    const el = document.getElementById(statusBarId);
    if (!el) return;
    if (!label) { el.innerHTML = ''; return; }
    const linkHtml = navUrl ? `&nbsp;<a class="chart-status-link" href="${navUrl}">View expenses →</a>` : '';
    el.innerHTML = `<span class="chart-status-info"><strong>${label}</strong>: ${value}</span>${linkHtml}`;
}

function buildExpensesNavUrl(extraParams = {}) {
    const params = new URLSearchParams();
    const currentPeriod = document.getElementById('homeHeroPeriod')?.value || document.getElementById('deskHeroPeriod')?.value || 'all';
    const currentParams = new URLSearchParams(buildFullFilterParams(currentPeriod));
    for (const [key, value] of currentParams.entries()) {
        params.append(key, value);
    }
    Object.entries(extraParams).forEach(([key, value]) => {
        if (value != null && value !== '') params.set(key, value);
    });
    return '#/expenses' + (params.toString() ? '?' + params.toString() : '');
}

function createTimelineChart(canvasId, chartKey, data) {
    const tc = document.getElementById(canvasId);
    if (!tc) return;
    if (chartInstances[chartKey]) { chartInstances[chartKey].destroy(); delete chartInstances[chartKey]; }
    const timelineLabels = Object.keys(data.timeline || {});
    const timelineValues = Object.values(data.timeline || {});
    chartInstances[chartKey] = new Chart(tc, {
        type: 'line',
        data: { labels: timelineLabels,
            datasets: [{ label: 'Daily Spending',
                data: timelineValues,
                borderColor: '#42A5F5', backgroundColor: 'rgba(66,165,245,0.1)',
                fill: true, tension: 0.3, pointRadius: 3 }] },
        options: { responsive: true, maintainAspectRatio: false,
            plugins: { ...chartPluginOptions(), legend: { display: false } },
            scales: chartScaleOptions(),
            onClick: (evt, elements) => {
                if (!elements.length) return;
                const idx = elements[0].index;
                const label = timelineLabels[idx];
                const value = timelineValues[idx];
                const navUrl = label ? buildExpensesNavUrl({ startDate: label, endDate: label }) : null;
                updateChartStatusBar(canvasId + 'Status', label, value, navUrl);
            }
        }
    });
}

// ============================================
// TOP LISTS
// ============================================
async function renderRecentExpenses(elementId, count, filterParams) {
    const el = document.getElementById(elementId);
    if (!el) return;
    const url = filterParams ? '/api/expenses?' + filterParams : '/api/expenses';
    const allExpenses = await api(url);
    if (allExpenses && Array.isArray(allExpenses)) {
        const recent = allExpenses.slice(0, count);
        el.innerHTML = recent.length ? recent.map(e => `
            <a href="#/expenses/${e.urlId}" class="expense-mini-card">
                <div class="mini-card-icon">${recentExpenseIconHtml(e)}</div>
                <div class="mini-card-info">
                    <div class="mini-card-category">${e.displayName || e.category || 'Uncategorized'}</div>
                    <div class="mini-card-date">${e.transactionDatetime ? new Date(e.transactionDatetime).toLocaleString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'}</div>
                </div>
                <div class="mini-card-amount">${e.amount != null ? Number(e.amount).toFixed(2) : '-'} ${e.currency || ''}</div>
            </a>`).join('') : '<p style="color:var(--text-light); text-align:center; padding:1rem;">No expenses yet</p>';
    }
}

function recentExpenseIconHtml(expense) {
    if (expense?.status === 'COMPLETED' && expense.storeWebsite && appConfig?.logoDevToken) {
        const domain = getRootDomain(expense.storeWebsite);
        if (domain) {
            const label = esc(expense.storeName || 'Store');
            return `<img class="mini-card-logo" src="https://img.logo.dev/${encodeURIComponent(domain)}?token=${encodeURIComponent(appConfig.logoDevToken)}" alt="${label}" title="${label}" onerror="this.style.display='none'; this.nextElementSibling.style.display='inline-block'">
                    <i class="fa-solid fa-store mini-card-logo-fallback" style="display:none"></i>`;
        }
    }
    return `<i class="fa-solid fa-${categoryIcon(expense?.category)}"></i>`;
}

function renderTopShops(elementId, topShops) {
    const el = document.getElementById(elementId);
    if (!el) return;
    if (!topShops || topShops.length === 0) {
        el.innerHTML = '<p style="color:var(--text-light); text-align:center; padding:1rem;">No shop data yet</p>';
        return;
    }
    el.innerHTML = topShops.map((s, i) => {
        const recentHtml = (s.recentTransactions || []).map(t =>
            `<a href="#/expenses/${t.urlId}" class="rank-recent-tx">
                <span class="rank-recent-cat">${t.category || 'Uncategorized'}</span>
                <span class="rank-recent-detail">${t.amount != null ? Number(t.amount).toFixed(2) : '-'} ${t.currency || ''} · ${t.date || ''}</span>
            </a>`).join('');
        return `<div class="rank-row-wrap">
            <div class="rank-row">
                <span class="rank-badge">${i + 1}</span>
                <a href="#/expenses?search=${encodeURIComponent(s.name)}" class="rank-name rank-name-link">${s.name}</a>
                <span class="rank-value">${s.visits} visit${s.visits > 1 ? 's' : ''}</span>
            </div>
            ${recentHtml ? `<div class="rank-recent-list">${recentHtml}</div>` : ''}
        </div>`;
    }).join('');
}

function renderTopItems(elementId, topItems) {
    const el = document.getElementById(elementId);
    if (!el) return;
    if (!topItems || topItems.length === 0) {
        el.innerHTML = '<p style="color:var(--text-light); text-align:center; padding:1rem;">No item data yet</p>';
        return;
    }
    el.innerHTML = topItems.map((item, i) => {
        const recentHtml = (item.recentTransactions || []).map(t => {
            const storeLabel = t.storeName ? ` · ${t.storeName}` : '';
            return `<a href="#/expenses/${t.urlId}" class="rank-recent-tx">
                <span class="rank-recent-cat">${t.unitPrice != null ? Number(t.unitPrice).toFixed(2) : '-'} ${t.currency || ''}</span>
                <span class="rank-recent-detail">${t.date || ''}${storeLabel}</span>
            </a>`;
        }).join('');
        return `<div class="rank-row-wrap">
            <div class="rank-row">
                <span class="rank-badge">${i + 1}</span>
                <a href="#/expenses?search=${encodeURIComponent(item.name)}" class="rank-name rank-name-link">${item.name}</a>
                <span class="rank-value">\u00d7${Number(item.count).toFixed(0)}</span>
            </div>
            ${recentHtml ? `<div class="rank-recent-list">${recentHtml}</div>` : ''}
        </div>`;
    }).join('');
}

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
        return `<a href="#/expenses/${e.urlId}" class="top-expense-card">
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

// ============================================
// GEO MAP
// ============================================
let _geoMaps = {};
function renderGeoMap(elementId, data) {
    const mapEl = document.getElementById(elementId);
    if (!mapEl) return;

    if (_geoMaps[elementId]) {
        try { _geoMaps[elementId].remove(); } catch (e) { /* ignore */ }
        delete _geoMaps[elementId];
    }

    if (mapEl.offsetWidth === 0 && mapEl.offsetHeight === 0) {
        setTimeout(() => renderGeoMap(elementId, data), 300);
        return;
    }

    const map = L.map(elementId).setView([20, 0], 2);
    _geoMaps[elementId] = map;
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '\u00a9 OpenStreetMap'
    }).addTo(map);

    const baseCur = currentUser?.baseCurrency || 'USD';
    const geoByCountry = data.geoByCountry || [];
    geoByCountry.forEach(c => {
        if (c.lat == null || c.lng == null || (c.lat === 0 && c.lng === 0)) return;
        const total = Number(c.total).toFixed(2);
        const name = c.countryName || c.country || 'Unknown';
        const countryIcon = L.divIcon({
            className: 'geo-country-marker',
            html: `<div class="geo-country-dot">${c.count}</div>`,
            iconSize: [32, 32], iconAnchor: [16, 16]
        });
        L.marker([c.lat, c.lng], { icon: countryIcon }).addTo(map)
            .bindPopup(`<div class="geo-popup">
                <b>${name}</b><br>
                <span class="geo-popup-row"><i class="fa-solid fa-calendar"></i> ${formatDateShort(c.minDate)} \u2014 ${formatDateShort(c.maxDate)}</span><br>
                <span class="geo-popup-row"><i class="fa-solid fa-money-bill"></i> ${total} ${baseCur}</span><br>
                <span class="geo-popup-row"><i class="fa-solid fa-receipt"></i> ${c.count} transaction${c.count > 1 ? 's' : ''}</span>
            </div>`);
    });

    (data.geoData || []).forEach(p => {
        if (p.lat == null || p.lng == null || (p.lat === 0 && p.lng === 0)) return;
        const storeName = p.name || 'Store';
        const storeLink = `<a href="#/expenses?search=${encodeURIComponent(storeName)}" style="color:var(--primary); text-decoration:none; font-weight:600;">${storeName}</a>`;
        L.marker([p.lat, p.lng]).addTo(map)
            .bindPopup(`${storeLink}<br>${p.amount} ${p.currency || ''}<br>${p.date || ''}`);
    });

    const allPoints = [...(data.geoData || []), ...geoByCountry].filter(p => p.lat && p.lng && !(p.lat === 0 && p.lng === 0));
    if (allPoints.length > 0) {
        map.fitBounds(L.latLngBounds(allPoints.map(p => [p.lat, p.lng])), { padding: [50, 50] });
    }

    setTimeout(() => {
        if (_geoMaps[elementId] && mapEl.offsetWidth > 0 && mapEl.offsetHeight > 0) {
            try { _geoMaps[elementId].invalidateSize(); } catch (e) { /* ignore */ }
        }
    }, 500);
}

// ============================================
// DISCOVERY CARDS (infinite scroll)
// ============================================
let _discoveryIndex = {};
let _discoveryObserver = null;
window._discoveryReportCardsByContainer = window._discoveryReportCardsByContainer || {};

function renderDiscoveryCards(containerId, cards) {
    const el = document.getElementById(containerId);
    if (!el) return;
    _discoveryIndex[containerId] = 0;
    window._discoveryReportCardsByContainer[containerId] = [];
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
    const colorsPool = ['#1565C0','#1976D2','#42A5F5','#388E3C','#AB47BC','#D32F2F','#FF7043','#00897B'];

    for (let i = 0; i < count && startIdx + i < cards.length; i++) {
        const card = cards[startIdx + i];
        const icon = iconsPool[(startIdx + i) % iconsPool.length];
        const accentColor = colorsPool[(startIdx + i) % colorsPool.length];
        const total = Number(card.total).toFixed(2);
        const shopList = (card.shops || []).join(', ');
        const daysStayed = card.daysStayed || 1;

        let originalLine = '';
        if (card.originalCurrency && card.originalCurrency !== baseCur && card.originalTotal != null) {
            originalLine = `<span style="font-size:0.75rem; opacity:0.75;">(${Number(card.originalTotal).toFixed(2)} ${card.originalCurrency})</span>`;
        }

        const div = document.createElement('div');
        div.className = 'feed-card discovery-card';

        const flag = card.country ? countryCodeToFlag(card.country) : '';
        const topExp = card.topExpenses || [];
        const reportCards = window._discoveryReportCardsByContainer[containerId] || (window._discoveryReportCardsByContainer[containerId] = []);
        const reportCardIndex = reportCards.push(card) - 1;
        let topExpHtml = '';
        if (topExp.length > 0) {
            topExpHtml = `<div class="discovery-top-expenses">
                <div class="discovery-top-exp-label"><i class="fa-solid fa-arrow-trend-up"></i> Top expenses</div>
                ${topExp.map(e => {
                    const base = e.amountInBase != null ? Number(e.amountInBase).toFixed(2) : (e.amount != null ? Number(e.amount).toFixed(2) : '-');
                    const orig = (e.currency && e.currency !== baseCur && e.amount != null)
                        ? ` <span class="discovery-exp-orig">(${Number(e.amount).toFixed(2)} ${e.currency})</span>` : '';
                    return `<a href="#/expenses/${e.urlId}" class="discovery-exp-row">
                        <span class="discovery-exp-name">${e.displayName || 'Expense'}</span>
                        <span class="discovery-exp-amount">${base} ${baseCur}${orig}</span>
                    </a>`;
                }).join('')}
            </div>`;
        }

        const reportActionHtml = `<div class="discovery-card-actions">
                <button class="btn btn-primary btn-sm" onclick="createReportFromDiscoveryCard(window._discoveryReportCardsByContainer['${containerId}'][${reportCardIndex}])">
                    <i class="fa-solid fa-chart-line"></i> View Report
                </button>
            </div>`;

        div.innerHTML = `
            <div class="discovery-header" style="border-left-color:${accentColor}">
                ${flag ? `<div class="discovery-flag-icon">${flag}</div>` : `<div class="discovery-icon" style="background:${accentColor}15; color:${accentColor}"><i class="fa-solid ${icon}"></i></div>`}
                <div class="discovery-title-area">
                    <div class="discovery-trip-label">Trip to</div>
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
            ${shopList ? `<div class="discovery-shops"><i class="fa-solid fa-store"></i> ${shopList}</div>` : ''}
            ${reportActionHtml}`;
        el.appendChild(div);
    }
    _discoveryIndex[containerId] = startIdx + count;
}

async function createReportFromDiscoveryCard(card) {
    if (!card || !card.yearMonth || !card.country) {
        toast('This discovery card cannot generate a report yet.', 'error');
        return;
    }

    const [year, month] = card.yearMonth.split('-');
    if (!year || !month) {
        toast('This discovery card has an invalid date range.', 'error');
        return;
    }

    const lastDay = new Date(parseInt(year, 10), parseInt(month, 10), 0).getDate();
    const body = {
        title: card.title || `Trip Report ${card.yearMonth}`,
        description: `Generated from dashboard discovery card for ${card.locationLabel || card.countryName || card.country}.`,
        groupBy: 'STORE_LOCATION',
        startDate: `${card.yearMonth}-01`,
        endDate: `${card.yearMonth}-${String(lastDay).padStart(2, '0')}`,
        country: card.country
    };

    if (card.city) body.city = card.city;

    const result = await api('/api/reports', { method: 'POST', body });
    if (!result || result.error) {
        toast(result?.error || 'Failed to create report', 'error');
        return;
    }

    toast('Report generated', 'success');
    navigate(`#/reports/${result.id}`);
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

    let rootEl = null;
    if (scrollContainerId) {
        const container = document.getElementById(scrollContainerId);
        if (container) {
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
    }, { root: rootEl, rootMargin: '400px', threshold: 0 });
    _discoveryObserver.observe(loadEl);
}

// ============================================
// UTILITY HELPERS
// ============================================
function formatYearMonth(ym) {
    if (!ym) return '';
    const [y, m] = ym.split('-');
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return months[parseInt(m) - 1] + ' ' + y;
}

function formatDateShort(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function getTopCategory(categoryTotals) {
    const top = Object.entries(categoryTotals || {}).sort((a, b) => Number(b[1]) - Number(a[1]))[0];
    return top ? top[0] : '-';
}

// Track preferred chart view per canvas
const _categoryChartView = {};

function switchCategoryView(canvasId, chartKey, viewType, btn) {
    _categoryChartView[canvasId] = viewType;
    const toggle = btn.parentElement;
    toggle.querySelectorAll('button').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    // Use the filtered data (not unfiltered) so date range is respected
    const data = canvasId.startsWith('home') ? (_filteredHomeData || _homeData) : (_filteredDesktopData || _desktopData);
    if (data) createCategoryChart(canvasId, chartKey, data);
}

function createCategoryChart(canvasId, chartKey, data) {
    const cc = document.getElementById(canvasId);
    if (!cc) return;
    if (chartInstances[chartKey]) { chartInstances[chartKey].destroy(); delete chartInstances[chartKey]; }
    const { cardBg } = getChartThemeColors();
    const catLabels = Object.keys(data.categoryTotals || {});
    const catValues = Object.values(data.categoryTotals || {});
    const viewType = _categoryChartView[canvasId] || 'pie';

    // Sync toggle button active state to match the actual viewType being rendered
    const toggleId = canvasId.startsWith('home') ? 'homeCategoryToggle' : 'deskCategoryToggle';
    const toggleEl = document.getElementById(toggleId);
    if (toggleEl) {
        toggleEl.querySelectorAll('button').forEach(b => {
            const bView = b.getAttribute('onclick')?.includes("'pie'") ? 'pie' : 'bar';
            b.classList.toggle('active', bView === viewType);
        });
    }

    if (viewType === 'bar') {
        const { textColor, gridColor } = getChartThemeColors();
        chartInstances[chartKey] = new Chart(cc, {
            type: 'bar',
            data: { labels: catLabels,
                datasets: [{ data: catValues, backgroundColor: CHART_COLORS, borderRadius: 4 }] },
            options: { indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {}
                },
                scales: {
                    x: { beginAtZero: true, ticks: { color: textColor }, grid: { color: gridColor } },
                    y: { ticks: { color: textColor }, grid: { color: gridColor } }
                },
                onClick: (evt, elements) => {
                    if (!elements.length) return;
                    const idx = elements[0].index;
                    const label = catLabels[idx];
                    const value = catValues[idx];
                    const navUrl = label ? buildExpensesNavUrl({ category: label }) : null;
                    updateChartStatusBar(canvasId + 'Status', label, value, navUrl);
                }
            }
        });
    } else {
        chartInstances[chartKey] = new Chart(cc, {
            type: 'doughnut',
            data: { labels: catLabels,
                datasets: [{ data: catValues, backgroundColor: CHART_COLORS, borderColor: cardBg, borderWidth: 1 }] },
            options: { responsive: true, maintainAspectRatio: false,
                plugins: { ...chartPluginOptions() },
                onClick: (evt, elements) => {
                    if (!elements.length) return;
                    const idx = elements[0].index;
                    const label = catLabels[idx];
                    const value = catValues[idx];
                    const navUrl = label ? buildExpensesNavUrl({ category: label }) : null;
                    updateChartStatusBar(canvasId + 'Status', label, value, navUrl);
                }
            }
        });
    }
}

// ============================================
// DASHBOARD FILTER DIALOG
// ============================================
function renderFilterBadges(mode) {
    const f = _dashFilters;
    const badges = [];
    const addBadges = (key, label, val) => {
        const arr = Array.isArray(val) ? val : (val ? [val] : []);
        arr.forEach(v => badges.push({ key, label, value: v }));
    };
    addBadges('category', 'Category', f.category);
    addBadges('country', 'Country', f.country);
    addBadges('storeName', 'Store', f.storeName);
    if (f.search) badges.push({ key: 'search', label: 'Keyword', value: f.search });
    if (badges.length === 0) return '';
    return badges.map(b =>
        `<span class="filter-badge">${b.label}: <strong>${esc(b.value)}</strong>
            <button class="filter-badge-x" onclick="removeDashFilterValue('${b.key}','${esc(b.value)}','${mode}')">&times;</button>
        </span>`
    ).join('');
}

function removeDashFilterValue(key, value, mode) {
    if (key === 'search') {
        delete _dashFilters[key];
    } else {
        const arr = Array.isArray(_dashFilters[key]) ? _dashFilters[key] : (_dashFilters[key] ? [_dashFilters[key]] : []);
        const updated = arr.filter(v => v !== value);
        if (updated.length === 0) delete _dashFilters[key];
        else _dashFilters[key] = updated;
    }
    applyDashFilters(mode);
}

function _checkboxList(id, items, selected) {
    if (!items || items.length === 0)
        return `<div class="filter-checkbox-list"><div class="filter-checkbox-empty">No options available</div></div>`;
    const rows = items.map(v =>
        `<label class="filter-checkbox-item">
            <input type="checkbox" name="${id}" value="${esc(v)}" ${selected.includes(v) ? 'checked' : ''}>
            ${esc(v)}
        </label>`
    ).join('');
    return `<div class="filter-checkbox-list" id="${id}List">${rows}</div>`;
}

function _checkboxListCountries(id, codes, selected) {
    if (!codes || codes.length === 0)
        return `<div class="filter-checkbox-list"><div class="filter-checkbox-empty">No options available</div></div>`;
    const rows = codes.map(code => {
        const label = getCountryName(code) || code;
        return `<label class="filter-checkbox-item">
            <input type="checkbox" name="${id}" value="${esc(code)}" ${selected.includes(code) ? 'checked' : ''}>
            ${countryCodeToFlag(code)} ${esc(label)}
        </label>`;
    }).join('');
    return `<div class="filter-checkbox-list" id="${id}List">${rows}</div>`;
}

function openDashFilterDialog(selectId, mode) {
    const existing = document.getElementById('dashFilterDialog');
    if (existing) existing.remove();

    const cats = Array.isArray(window._allExpenseCategories) ? window._allExpenseCategories : Array.from(window._allExpenseCategories || []);
    const countries = window._allDashCountries || [];
    const storeNames = window._allDashStoreNames || [];
    const f = _dashFilters;

    const selCat = Array.isArray(f.category) ? f.category : (f.category ? [f.category] : []);
    const selCountry = Array.isArray(f.country) ? f.country : (f.country ? [f.country] : []);
    const selStore = Array.isArray(f.storeName) ? f.storeName : (f.storeName ? [f.storeName] : []);

    const dialog = document.createElement('div');
    dialog.id = 'dashFilterDialog';
    dialog.className = 'modal-overlay';
    dialog.innerHTML = `
        <div class="modal-content" style="max-width:420px; max-height:80vh; display:flex; flex-direction:column; padding:0; overflow:hidden;">
            <div class="modal-header" style="padding:1.25rem 1.5rem 1rem; flex-shrink:0;">
                <h3><i class="fa-solid fa-filter"></i> More Filters</h3>
                <button class="modal-close" onclick="closeDashFilterDialog()">&times;</button>
            </div>
            <div style="overflow-y:auto; flex:1; min-height:0; padding:0 1.5rem;">
                <div class="form-group">
                    <label>Category</label>
                    ${_checkboxList('dfCategory', cats, selCat)}
                </div>
                <div class="form-group">
                    <label>Country</label>
                    ${_checkboxListCountries('dfCountry', countries, selCountry)}
                </div>
                <div class="form-group">
                    <label>Store</label>
                    ${_checkboxList('dfStoreName', storeNames, selStore)}
                </div>
                <div class="form-group">
                    <label>Keyword (tags / notes)</label>
                    <input type="text" class="form-control" id="dfSearch" maxlength="100" value="${esc(f.search || '')}" placeholder="e.g. lunch, travel">
                </div>
            </div>
            <div style="display:flex; gap:0.5rem; padding:1rem 1.5rem; flex-shrink:0; border-top:1px solid var(--border-color); background:var(--bg-card);">
                <button class="btn btn-primary" onclick="saveDashFilters('${mode}')"><i class="fa-solid fa-check"></i> Apply</button>
                <button class="btn btn-outline" onclick="clearDashFilters('${mode}')"><i class="fa-solid fa-eraser"></i> Clear</button>
                <button class="btn btn-outline" onclick="closeDashFilterDialog()">Cancel</button>
            </div>
        </div>`;
    dialog.addEventListener('click', e => { if (e.target === dialog) closeDashFilterDialog(); });
    document.body.appendChild(dialog);
}

function closeDashFilterDialog() {
    const d = document.getElementById('dashFilterDialog');
    if (d) d.remove();
}

function _readCheckboxes(name) {
    return Array.from(document.querySelectorAll(`input[name="${name}"]:checked`)).map(cb => cb.value);
}

function saveDashFilters(mode) {
    const cats = _readCheckboxes('dfCategory');
    const countries = _readCheckboxes('dfCountry');
    const stores = _readCheckboxes('dfStoreName');
    const search = document.getElementById('dfSearch')?.value?.trim() || '';

    if (cats.length) _dashFilters.category = cats; else delete _dashFilters.category;
    if (countries.length) _dashFilters.country = countries; else delete _dashFilters.country;
    if (stores.length) _dashFilters.storeName = stores; else delete _dashFilters.storeName;
    if (search) _dashFilters.search = search; else delete _dashFilters.search;

    closeDashFilterDialog();
    applyDashFilters(mode);
}

function clearDashFilters(mode) {
    _dashFilters = {};
    closeDashFilterDialog();
    applyDashFilters(mode);
}

function applyDashFilters(mode) {
    const selectId = mode === 'home' ? 'homeHeroPeriod' : 'deskHeroPeriod';
    const sel = document.getElementById(selectId);
    const periodVal = sel ? sel.value : 'all';
    const params = buildFullFilterParams(periodVal);
    if (mode === 'home') {
        reloadHomeWithFilter(params);
        const badgeEl = document.getElementById('heroCardBadges');
        if (badgeEl) badgeEl.innerHTML = renderFilterBadges(mode);
    } else {
        reloadDesktopWithFilter(params);
        const badgeEl = document.getElementById('desktopHeroCardBadges');
        if (badgeEl) badgeEl.innerHTML = renderFilterBadges(mode);
    }
}

function buildFullFilterParams(periodVal) {
    const params = new URLSearchParams(buildParamsFromPeriod(periodVal));
    const appendArr = (key, val) => {
        const arr = Array.isArray(val) ? val : (val ? [val] : []);
        arr.forEach(v => params.append(key, v));
    };
    appendArr('category', _dashFilters.category);
    appendArr('country', _dashFilters.country);
    appendArr('storeName', _dashFilters.storeName);
    if (_dashFilters.search) params.set('search', _dashFilters.search);
    return params.toString();
}


