/* ============================================
   Expense Tracker - Dashboard Widgets
   (Shared: hero card, charts, top lists, geo map, discovery cards)
   ============================================ */

// ============================================
// HERO CARD
// ============================================
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
    const data = heroCard._dashData;
    const baseCur = currentUser?.baseCurrency || 'USD';

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

    const params = buildParamsFromPeriod(val);
    if (mode === 'home') reloadHomeWithFilter(params);
    else reloadDesktopWithFilter(params);
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
                const navUrl = label ? '#/expenses?startDate=' + label + '&endDate=' + label : null;
                updateChartStatusBar(canvasId + 'Status', label, value, navUrl);
            }
        }
    });
}

function createCategoryChart(canvasId, chartKey, data) {
    const cc = document.getElementById(canvasId);
    if (!cc) return;
    if (chartInstances[chartKey]) { chartInstances[chartKey].destroy(); delete chartInstances[chartKey]; }
    const { cardBg } = getChartThemeColors();
    const catLabels = Object.keys(data.categoryTotals || {});
    const catValues = Object.values(data.categoryTotals || {});
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
                const navUrl = label ? '#/expenses?category=' + encodeURIComponent(label) : null;
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
                <div class="mini-card-icon"><i class="fa-solid fa-${categoryIcon(e.category)}"></i></div>
                <div class="mini-card-info">
                    <div class="mini-card-category">${e.displayName || e.category || 'Uncategorized'}</div>
                    <div class="mini-card-date">${e.transactionDatetime ? new Date(e.transactionDatetime).toLocaleString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'}</div>
                </div>
                <div class="mini-card-amount">${e.amount != null ? Number(e.amount).toFixed(2) : '-'} ${e.currency || ''}</div>
            </a>`).join('') : '<p style="color:var(--text-light); text-align:center; padding:1rem;">No expenses yet</p>';
    }
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

