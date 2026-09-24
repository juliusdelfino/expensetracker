/* ============================================
   Expense Tracker - Reports Page & Detail
   ============================================ */

let _reportOptionsCache = null;
let _reportChartKeys = [];
let _reportsAutoOpenHandled = false;
// Report detail state — kept across per-section saves
let _reportEditKeywords = [];
let _reportCurrentId = null;
let _reportCurrentGroupBy = null;
let _reportCurrentTitle = '';
let _reportCurrentDescription = '';
let _reportCurrentFilters = {};
let _reportCurrentChartDefs = [];

function getReportsHashQuery() {
    return new URLSearchParams((window.location.hash.split('?')[1] || ''));
}

function getReportPrefillFromHash() {
    const params = getReportsHashQuery();
    return {
        title: params.get('title') || '',
        description: params.get('description') || '',
        startDate: params.get('startDate') || '',
        endDate: params.get('endDate') || '',
        category: params.get('category') || '',
        country: params.get('country') || '',
        storeName: params.get('storeName') || '',
        search: params.get('search') || '',
        groupBy: params.get('groupBy') || 'KEYWORD',
        autoOpen: params.get('openGenerate') === '1'
    };
}

async function loadReportFilterOptions() {
    if (_reportOptionsCache) return _reportOptionsCache;
    const dashData = await api('/api/dashboard');
    const categories = dashData?.categories || [];
    const countries = (dashData?.geoByCountry || [])
        .map(c => ({ code: c.country, name: c.countryName || c.country }))
        .filter(c => c.code);
    const seenCodes = new Set();
    const uniqueCountries = countries.filter(c => {
        if (seenCodes.has(c.code)) return false;
        seenCodes.add(c.code);
        return true;
    });
    _reportOptionsCache = {
        categories,
        countries: uniqueCountries,
        storeNames: Array.isArray(dashData?.storeNames) ? dashData.storeNames : Array.from(dashData?.storeNames || [])
    };
    return _reportOptionsCache;
}

async function renderReportsPage(app) {
    destroyReportCharts();
    const reports = await api('/api/reports');
    if (!reports || !Array.isArray(reports)) return;

    const prefill = getReportPrefillFromHash();
    if (!prefill.autoOpen) _reportsAutoOpenHandled = false;
    app.innerHTML = `
    <div class="container">
        <div class="action-bar reports-action-bar">
            <div class="action-bar-left">
                <h2 class="reports-page-title" style="color:var(--primary-dark)"><i class="fa-solid fa-chart-line"></i> Reports</h2>
                <p class="reports-subtitle">Generate saved reports from your expense filters and revisit them anytime.</p>
            </div>
            <div class="action-bar-right">
                <button class="btn btn-primary btn-sm" onclick="openGenerateReportModal()">
                    <i class="fa-solid fa-wand-magic-sparkles"></i> Generate Report
                </button>
            </div>
        </div>

        <div class="card">
            <div class="reports-list-header">
                <h3 class="card-title"><i class="fa-solid fa-folder-open"></i> Saved Reports</h3>
                <span class="reports-count">${reports.length} report${reports.length !== 1 ? 's' : ''}</span>
            </div>
            <div id="reportsList">${renderReportsListCards(reports)}</div>
        </div>
    </div>`;

    if (prefill.autoOpen && !_reportsAutoOpenHandled) {
        _reportsAutoOpenHandled = true;
        setTimeout(() => openGenerateReportModal(prefill), 0);
    }
}

function renderReportsListCards(reports) {
    if (!reports.length) {
        return `<div class="reports-empty-state">
            <i class="fa-solid fa-chart-line"></i>
            <h3>No reports yet</h3>
            <p>Create your first report from filters, categories, or store locations.</p>
            <button class="btn btn-primary btn-sm" onclick="openGenerateReportModal()"><i class="fa-solid fa-plus"></i> Generate Report</button>
        </div>`;
    }

    return `<div class="reports-list-grid">${reports.map(report => `
        <div class="report-card report-card-clickable" role="link" tabindex="0"
             onclick="navigate('#/reports/${report.id}')"
             onkeydown="if(event.key==='Enter' || event.key===' '){ event.preventDefault(); navigate('#/reports/${report.id}'); }">
            <div class="report-card-top">
                <div>
                    <div class="report-card-title">${esc(report.title)}</div>
                    <div class="report-card-meta">
                        <span><i class="fa-solid fa-layer-group"></i> ${formatGroupBy(report.groupBy)}</span>
                        <span><i class="fa-solid fa-wallet"></i> ${formatMoney(report.totalAmount, currentUser?.baseCurrency || 'USD')}</span>
                        <span><i class="fa-solid fa-receipt"></i> ${report.expenseCount || 0} expense${report.expenseCount === 1 ? '' : 's'}</span>
                    </div>
                </div>
                <span class="report-card-date">${formatReportDate(report.createdAt)}</span>
            </div>
            <div class="report-card-actions">
                <button class="btn btn-outline btn-sm btn-icon report-list-action-desktop" title="Export PDF"
                        onclick="event.stopPropagation(); exportReportPdf(${report.id})"><i class="fa-solid fa-file-pdf"></i></button>
                <button class="btn btn-danger btn-sm btn-icon report-list-action-desktop" title="Delete"
                        onclick="event.stopPropagation(); deleteReportFromList(${report.id})"><i class="fa-solid fa-trash"></i></button>
            </div>
        </div>`).join('')}</div>`;
}

async function renderReportDetail(app, reportId) {
    destroyReportCharts();
    const report = await api(`/api/reports/${reportId}`);
    if (!report || report.error) {
        app.innerHTML = `
        <div class="container">
            <div class="card reports-empty-state">
                <i class="fa-solid fa-triangle-exclamation"></i>
                <h3>${esc(report?.error || 'Report not found')}</h3>
                <p>The report may have been deleted or you may not have access to it.</p>
                <a class="btn btn-primary" href="#/reports"><i class="fa-solid fa-arrow-left"></i> Back to Reports</a>
            </div>
        </div>`;
        return;
    }

    const canViewMatchingExpenses = report.filterSnapshot?.mode === 'FILTERS';
    const filters = extractReportFilters(report.filterSnapshot);
    const chartDefinitions = normalizeReportChartDefinitions(report.chartDefinitions, report.groupBy);

    // Store module-level state for cross-section saves
    _reportCurrentId = report.id;
    _reportCurrentGroupBy = report.groupBy;
    _reportCurrentTitle = report.title || '';
    _reportCurrentDescription = report.description || '';
    _reportCurrentFilters = filters;
    _reportCurrentChartDefs = chartDefinitions;
    _reportEditKeywords = [...filters.searchKeywords];

    app.innerHTML = `
    <div class="container">
        <!-- ── Header: read-only title + description ── -->
        <div class="action-bar reports-action-bar reports-detail-header">
            <div class="action-bar-left" style="flex:1; min-width:0;">
                <a href="#/reports" class="reports-back-link"><i class="fa-solid fa-arrow-left"></i> Reports</a>

                <!-- Read-only view -->
                <div id="reportTitleView" class="report-title-view-row">
                    <h2 style="color:var(--primary-dark); margin:0;">${esc(report.title || 'Report')}</h2>
                    <button class="btn btn-outline btn-sm btn-icon report-section-edit-btn" title="Edit title & description" onclick="enableReportTitleEdit()"><i class="fa-solid fa-pen"></i></button>
                </div>
                ${report.description ? `<p id="reportDescView" class="reports-subtitle" style="margin:0;">${esc(report.description)}</p>` : `<p id="reportDescView" class="reports-subtitle report-placeholder-text" style="margin:0;">No description</p>`}

                <!-- Edit mode (hidden) -->
                <div id="reportTitleEditBlock" style="display:none; width:100%;">
                    <input id="reportTitleInput" class="form-control form-control-sm report-inline-input" maxlength="255" value="${esc(report.title || '')}" placeholder="Report title">
                    <textarea id="reportDescriptionInput" class="form-control form-control-sm report-inline-textarea" maxlength="5000" placeholder="Description (optional)">${esc(report.description || '')}</textarea>
                    <div class="report-section-actions">
                        <button class="btn btn-primary btn-sm" onclick="saveReportTitleEdit()"><i class="fa-solid fa-save"></i> Save</button>
                        <button class="btn btn-outline btn-sm" onclick="cancelReportTitleEdit()">Cancel</button>
                    </div>
                </div>

                <div class="report-meta-row">
                    <span><i class="fa-solid fa-layer-group"></i> ${formatGroupBy(report.groupBy)}</span>
                    <span><i class="fa-solid fa-calendar"></i> ${formatReportDate(report.createdAt)}</span>
                    <span><i class="fa-solid fa-receipt"></i> ${report.summary?.expenseCount || 0} expense${report.summary?.expenseCount === 1 ? '' : 's'}</span>
                </div>
            </div>
            <div class="action-bar-right report-detail-actions">
                <div class="action-menu-wrap" id="reportActionMenuWrap">
                    <button class="btn btn-outline btn-sm btn-icon" onclick="toggleReportDetailMenu()" title="Actions"><i class="fa-solid fa-ellipsis-vertical"></i></button>
                    <div class="action-dropdown" id="reportActionDropdown" style="display:none;">
                        <button class="action-dropdown-item" onclick="closeReportDetailMenu(); exportReportPdf(${report.id})"><i class="fa-solid fa-file-pdf"></i> Export PDF</button>
                        ${canViewMatchingExpenses ? `<button class="action-dropdown-item" onclick="closeReportDetailMenu(); openReportMatchingExpenses(${report.id})"><i class="fa-solid fa-table-list"></i> View Matching Expenses</button>` : ''}
                        <button class="action-dropdown-item action-dropdown-danger" onclick="closeReportDetailMenu(); deleteReportAndReturn(${report.id})"><i class="fa-solid fa-trash"></i> Delete</button>
                    </div>
                </div>
            </div>
        </div>

        <!-- ── Filters card ── -->
        <div class="card report-filter-compact-card">
            <div class="reports-list-header" style="margin-bottom:0.5rem;">
                <h3 class="card-title" style="margin:0;"><i class="fa-solid fa-filter"></i> Filters</h3>
                <button id="reportFilterEditBtn" class="btn btn-outline btn-sm btn-icon report-section-edit-btn" title="Edit filters" onclick="enableReportFilterEdit()"><i class="fa-solid fa-pen"></i></button>
            </div>

            <!-- Read-only badges -->
            <div id="reportFilterView">
                ${renderFilterBadgesHtml(filters)}
            </div>

            <!-- Edit mode (hidden) -->
            <div id="reportFilterEditBlock" style="display:none;">
                <div class="report-filter-compact-grid">
                    <input id="reportFilterStartDate" type="date" class="form-control form-control-sm" value="${esc(filters.startDate)}" title="Start date">
                    <input id="reportFilterEndDate" type="date" class="form-control form-control-sm" value="${esc(filters.endDate)}" title="End date">
                    <input id="reportFilterCategory" type="text" class="form-control form-control-sm" value="${esc(filters.category)}" placeholder="Category">
                    <input id="reportFilterCountry" type="text" class="form-control form-control-sm" value="${esc(filters.country)}" placeholder="Country">
                    <input id="reportFilterStoreName" type="text" class="form-control form-control-sm" value="${esc(filters.storeName)}" placeholder="Store">
                    <input id="reportFilterCity" type="text" class="form-control form-control-sm" value="${esc(filters.city)}" placeholder="City">
                </div>
                <div class="report-keyword-editor" id="reportKeywordEditor"></div>
                <div class="report-keyword-entry-row">
                    <input id="reportKeywordInput" type="text" class="form-control form-control-sm" placeholder="Add keyword and press Enter">
                    <button class="btn btn-outline btn-sm" onclick="addReportKeywordFromInput()"><i class="fa-solid fa-plus"></i> Add</button>
                </div>
                <div class="report-section-actions">
                    <button class="btn btn-primary btn-sm" onclick="saveReportFilterEdit()"><i class="fa-solid fa-save"></i> Save</button>
                    <button class="btn btn-outline btn-sm" onclick="cancelReportFilterEdit()">Cancel</button>
                </div>
            </div>
        </div>

        <!-- ── Summary cards ── -->
        <div class="reports-summary-grid">
            ${renderSummaryCard('Total Spend', formatMoney(report.summary?.totalAmount, currentUser?.baseCurrency || 'USD'), 'fa-wallet')}
            ${renderSummaryCard('Average', formatMoney(report.summary?.averageAmount, currentUser?.baseCurrency || 'USD'), 'fa-chart-simple')}
            ${renderSummaryCard('Largest Expense', formatMoney(report.summary?.maxAmount, currentUser?.baseCurrency || 'USD'), 'fa-arrow-trend-up')}
            ${renderSummaryCard('Top Category', report.summary?.topCategory || '—', 'fa-tags')}
            ${renderSummaryCard('Top Location', report.summary?.topLocation || '—', 'fa-location-dot')}
            ${renderSummaryCard('Active Days', String(report.summary?.activeDaysCount ?? 0), 'fa-calendar-days')}
            ${renderSummaryCard('Covered Dates', formatCoveredDateRange(report.summary?.coveredStartDate, report.summary?.coveredEndDate), 'fa-calendar-range')}
        </div>

        <!-- ── Charts: read-only grid + edit-charts panel ── -->
        <div class="reports-charts-grid" id="reportChartsGrid">
            ${renderChartShells(report.charts || [])}
        </div>

        <!-- Chart editor (hidden) -->
        <div id="reportChartEditorCard" class="card" style="display:none; margin-bottom:1.25rem;">
            <div class="reports-list-header" style="margin-bottom:0.75rem;">
                <h3 class="card-title" style="margin:0;"><i class="fa-solid fa-chart-column"></i> Edit Charts</h3>
                <button class="btn btn-outline btn-sm" onclick="addReportChartDefinition()"><i class="fa-solid fa-plus"></i> Add Chart</button>
            </div>
            <div id="reportChartEditorList"></div>
            <div class="report-section-actions" style="margin-top:0.75rem;">
                <button class="btn btn-primary btn-sm" onclick="saveReportChartEdit()"><i class="fa-solid fa-save"></i> Save Charts</button>
                <button class="btn btn-outline btn-sm" onclick="cancelReportChartEdit()">Cancel</button>
            </div>
        </div>

        <!-- Charts card header edit button (injected after render) -->

        <div class="card">
            <div class="reports-list-header">
                <h3 class="card-title"><i class="fa-solid fa-table"></i> Expenses in Report</h3>
                <span class="reports-count">${report.expenses?.length || 0} rows</span>
            </div>
            ${renderReportExpensesTable(report.expenses || [])}
        </div>
    </div>`;

    // Inject edit-charts button into charts-grid header by prepending a row above it
    _injectChartsEditButton(report.id);
    initReportKeywordEditor();
    renderReportCharts(report.charts || []);
}

function renderSummaryCard(label, value, icon) {
    return `<div class="report-summary-card">
        <div class="report-summary-icon"><i class="fa-solid ${icon}"></i></div>
        <div class="report-summary-content">
            <span class="report-summary-label">${label}</span>
            <span class="report-summary-value">${esc(value)}</span>
        </div>
    </div>`;
}

function renderInsights(insights) {
    if (!insights || !insights.length) {
        return '<div class="report-empty-inline">No insights available yet.</div>';
    }
    return insights.map(text => `<div class="report-insight-item"><i class="fa-solid fa-sparkles"></i><span>${esc(text)}</span></div>`).join('');
}

function renderReportFilterSnapshot(filterSnapshot) {
    if (!filterSnapshot) return '';
    const badges = [];
    if (filterSnapshot.groupBy) badges.push({ label: 'Group', value: formatGroupBy(filterSnapshot.groupBy) });
    if (filterSnapshot.startDate) badges.push({ label: 'Start', value: filterSnapshot.startDate });
    if (filterSnapshot.endDate) badges.push({ label: 'End', value: filterSnapshot.endDate });
    if (filterSnapshot.category) badges.push({ label: 'Category', value: filterSnapshot.category });
    if (filterSnapshot.country) badges.push({ label: 'Country', value: filterSnapshot.country });
    if (filterSnapshot.city) badges.push({ label: 'City', value: filterSnapshot.city });
    if (filterSnapshot.storeName) badges.push({ label: 'Store', value: filterSnapshot.storeName });
    if (filterSnapshot.search) badges.push({ label: 'Keyword', value: filterSnapshot.search });
    if (filterSnapshot.mode === 'EXPLICIT_EXPENSE_IDS') badges.push({ label: 'Source', value: 'Manual selection' });
    if (!badges.length) return '';
    return `<div class="card report-filters-card"><div class="card-title"><i class="fa-solid fa-filter"></i> Filters</div><div class="hero-filter-badges">${badges.map(b => `<span class="filter-badge">${esc(b.label)}: <strong>${esc(b.value)}</strong></span>`).join('')}</div></div>`;
}

function extractReportFilters(filterSnapshot) {
    const rawKeywords = Array.isArray(filterSnapshot?.searchKeywords)
        ? filterSnapshot.searchKeywords
        : [];
    const fallback = filterSnapshot?.search || '';
    const normalized = normalizeKeywordValues([...rawKeywords, ...fallback.split(/[,;\n]/)]);
    return {
        startDate: filterSnapshot?.startDate || '',
        endDate: filterSnapshot?.endDate || '',
        category: filterSnapshot?.category || '',
        country: filterSnapshot?.country || '',
        city: filterSnapshot?.city || '',
        storeName: filterSnapshot?.storeName || '',
        searchKeywords: normalized
    };
}

function normalizeKeywordValues(values) {
    const unique = new Set();
    (values || []).forEach(value => {
        const trimmed = String(value || '').trim();
        if (!trimmed) return;
        const key = trimmed.toLowerCase();
        if (!unique.has(key)) unique.add(key);
    });
    return Array.from(unique.values());
}

// ── Read-only filter badges ────────────────────────────────────────────
function renderFilterBadgesHtml(filters) {
    const badges = [];
    if (filters.startDate) badges.push({ label: 'Start', value: filters.startDate });
    if (filters.endDate)   badges.push({ label: 'End',   value: filters.endDate });
    if (filters.category)  badges.push({ label: 'Category', value: filters.category });
    if (filters.country)   badges.push({ label: 'Country',  value: filters.country });
    if (filters.city)      badges.push({ label: 'City',     value: filters.city });
    if (filters.storeName) badges.push({ label: 'Store',    value: filters.storeName });
    (filters.searchKeywords || []).forEach(k => badges.push({ label: 'Keyword', value: k }));
    if (!badges.length) return '<span class="reports-count" style="font-style:italic;">No filters applied</span>';
    return '<div class="hero-filter-badges">' +
        badges.map(b => `<span class="filter-badge">${esc(b.label)}: <strong>${esc(b.value)}</strong></span>`).join('') +
        '</div>';
}

// ── Charts grid edit button injection ─────────────────────────────────
function _injectChartsEditButton(reportId) {
    const grid = document.getElementById('reportChartsGrid');
    if (!grid) return;
    const wrap = document.createElement('div');
    wrap.className = 'reports-list-header';
    wrap.style.marginBottom = '0.75rem';
    wrap.innerHTML = `<h3 class="card-title" style="margin:0;"><i class="fa-solid fa-chart-column"></i> Charts</h3>
        <button class="btn btn-outline btn-sm btn-icon report-section-edit-btn" title="Edit charts" onclick="enableReportChartEdit()"><i class="fa-solid fa-pen"></i></button>`;
    grid.parentNode.insertBefore(wrap, grid);
}

// ── Title / Description section ────────────────────────────────────────
function enableReportTitleEdit() {
    document.getElementById('reportTitleView').style.display = 'none';
    document.getElementById('reportDescView').style.display = 'none';
    document.getElementById('reportTitleEditBlock').style.display = 'block';
    document.getElementById('reportTitleInput')?.focus();
}
function cancelReportTitleEdit() {
    document.getElementById('reportTitleEditBlock').style.display = 'none';
    document.getElementById('reportTitleView').style.display = '';
    document.getElementById('reportDescView').style.display = '';
}
async function saveReportTitleEdit() {
    const title = document.getElementById('reportTitleInput')?.value?.trim() || _reportCurrentTitle;
    const description = document.getElementById('reportDescriptionInput')?.value?.trim() || '';
    const body = _buildReportUpdateBody({ title, description });
    const result = await api(`/api/reports/${_reportCurrentId}`, { method: 'PUT', body });
    if (!result || result.error) { toast(result?.error || 'Failed to update', 'error'); return; }
    toast('Title updated', 'success');
    _reportCurrentTitle = result.title || title;
    _reportCurrentDescription = result.description || description;
    document.getElementById('reportTitleView').querySelector('h2').textContent = _reportCurrentTitle;
    const descEl = document.getElementById('reportDescView');
    if (descEl) {
        descEl.textContent = _reportCurrentDescription || 'No description';
        descEl.classList.toggle('report-placeholder-text', !_reportCurrentDescription);
    }
    cancelReportTitleEdit();
}

// ── Filters section ────────────────────────────────────────────────────
function enableReportFilterEdit() {
    document.getElementById('reportFilterView').style.display = 'none';
    document.getElementById('reportFilterEditBtn').style.display = 'none';
    document.getElementById('reportFilterEditBlock').style.display = 'block';
    initReportKeywordEditor();
}
function cancelReportFilterEdit() {
    document.getElementById('reportFilterEditBlock').style.display = 'none';
    document.getElementById('reportFilterView').style.display = '';
    document.getElementById('reportFilterEditBtn').style.display = '';
}
async function saveReportFilterEdit() {
    const startDate  = document.getElementById('reportFilterStartDate')?.value || '';
    const endDate    = document.getElementById('reportFilterEndDate')?.value || '';
    const category   = document.getElementById('reportFilterCategory')?.value?.trim() || '';
    const country    = document.getElementById('reportFilterCountry')?.value?.trim() || '';
    const city       = document.getElementById('reportFilterCity')?.value?.trim() || '';
    const storeName  = document.getElementById('reportFilterStoreName')?.value?.trim() || '';
    const body = _buildReportUpdateBody({ startDate, endDate, category, country, city, storeName,
        searchKeywords: _reportEditKeywords, search: _reportEditKeywords.join(', ') });
    const result = await api(`/api/reports/${_reportCurrentId}`, { method: 'PUT', body });
    if (!result || result.error) { toast(result?.error || 'Failed to update filters', 'error'); return; }
    toast('Filters updated', 'success');
    // Refresh the whole detail so charts + expense list reflect new expense set
    const app = document.getElementById('app');
    if (app) renderReportDetail(app, _reportCurrentId);
}

// ── Chart editor section ───────────────────────────────────────────────
function enableReportChartEdit() {
    const card = document.getElementById('reportChartEditorCard');
    if (!card) return;
    document.getElementById('reportChartEditorList').innerHTML =
        renderReportChartEditorRows(_reportCurrentChartDefs);
    card.style.display = 'block';
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
function cancelReportChartEdit() {
    const card = document.getElementById('reportChartEditorCard');
    if (card) card.style.display = 'none';
}
async function saveReportChartEdit() {
    const chartDefinitions = collectReportChartDefinitions();
    if (!chartDefinitions.length) { toast('Please keep at least one chart.', 'info'); return; }
    const body = _buildReportUpdateBody({ chartDefinitions });
    const result = await api(`/api/reports/${_reportCurrentId}`, { method: 'PUT', body });
    if (!result || result.error) { toast(result?.error || 'Failed to update charts', 'error'); return; }
    toast('Charts updated', 'success');
    const app = document.getElementById('app');
    if (app) renderReportDetail(app, _reportCurrentId);
}

// ── Shared helper: build a full PUT body merging current state with overrides ─
function _buildReportUpdateBody(overrides) {
    const f = _reportCurrentFilters;
    return Object.assign({
        title: _reportCurrentTitle,
        description: _reportCurrentDescription,
        groupBy: _reportCurrentGroupBy,
        startDate:  f.startDate  || '',
        endDate:    f.endDate    || '',
        category:   f.category   || '',
        country:    f.country    || '',
        city:       f.city       || '',
        storeName:  f.storeName  || '',
        searchKeywords: _reportEditKeywords,
        search: _reportEditKeywords.join(', '),
        chartDefinitions: _reportCurrentChartDefs
    }, overrides);
}

// ── Legacy combined save (kept for backward-compat if called elsewhere) ──
async function saveReportDetailEdits(reportId, groupBy) {
    const chartDefinitions = collectReportChartDefinitions();
    if (!chartDefinitions.length) { toast('Please keep at least one chart.', 'info'); return; }
    const body = _buildReportUpdateBody({ chartDefinitions });
    const result = await api(`/api/reports/${reportId}`, { method: 'PUT', body });
    if (!result || result.error) { toast(result?.error || 'Failed to update report', 'error'); return; }
    toast('Report updated', 'success');
    const app = document.getElementById('app');
    if (app) renderReportDetail(app, reportId);
}

function initReportKeywordEditor() {
    renderReportKeywordEditor();
    const input = document.getElementById('reportKeywordInput');
    if (!input) return;
    input.addEventListener('keydown', ev => {
        if (ev.key === 'Enter' || ev.key === ',') {
            ev.preventDefault();
            addReportKeywordFromInput();
        }
    });
}

function renderReportKeywordEditor() {
    const host = document.getElementById('reportKeywordEditor');
    if (!host) return;
    host.innerHTML = (_reportEditKeywords || []).map(keyword =>
        `<span class="filter-badge">${esc(keyword)}
            <button class="filter-badge-x" onclick="removeReportKeyword('${esc(keyword)}')">&times;</button>
        </span>`
    ).join('') || '<span class="reports-count">No keywords</span>';
}

function addReportKeywordFromInput() {
    const input = document.getElementById('reportKeywordInput');
    if (!input) return;
    const values = normalizeKeywordValues(input.value.split(/[,;\n]/));
    if (!values.length) return;
    _reportEditKeywords = normalizeKeywordValues([...( _reportEditKeywords || []), ...values]);
    input.value = '';
    renderReportKeywordEditor();
}

function removeReportKeyword(keyword) {
    const key = String(keyword || '').toLowerCase();
    _reportEditKeywords = (_reportEditKeywords || []).filter(k => k.toLowerCase() !== key);
    renderReportKeywordEditor();
}

function normalizeReportChartDefinitions(chartDefinitions, fallbackGroupBy) {
    if (!Array.isArray(chartDefinitions) || !chartDefinitions.length) {
        return [defaultReportChartDefinition(fallbackGroupBy, 'chart-1')];
    }
    return chartDefinitions.map((chart, idx) => ({
        id: chart?.id || `chart-${idx + 1}`,
        title: chart?.title || `Chart ${idx + 1}`,
        type: (chart?.type || 'BAR').toUpperCase(),
        groupBy: (chart?.groupBy || 'CATEGORY').toUpperCase(),
        metric: (chart?.metric || 'TOTAL_AMOUNT').toUpperCase(),
        limit: Number(chart?.limit || 10),
        sort: (chart?.sort || 'DESC').toUpperCase()
    }));
}

function defaultReportChartDefinition(groupBy, idSeed) {
    return {
        id: idSeed,
        title: 'New Chart',
        type: 'BAR',
        groupBy: groupBy === 'STORE_LOCATION' ? 'LOCATION' : 'CATEGORY',
        metric: 'TOTAL_AMOUNT',
        limit: 10,
        sort: 'DESC'
    };
}

function renderReportChartEditorRows(chartDefinitions) {
    return (chartDefinitions || []).map((chart, index) => renderReportChartEditorRow(chart, index)).join('');
}

function renderReportChartEditorRow(chart, index) {
    const chartId = esc(chart.id || `chart-${index + 1}`);
    return `<div class="report-chart-edit-row" data-chart-row="${index}">
        <input type="hidden" data-chart-field="id" value="${chartId}">
        <input class="form-control form-control-sm" data-chart-field="title" value="${esc(chart.title || '')}" placeholder="Chart title">
        <select class="form-control form-control-sm" data-chart-field="type">${renderReportOptionSet(['BAR','LINE','DOUGHNUT','TABLE'], chart.type || 'BAR')}</select>
        <select class="form-control form-control-sm" data-chart-field="groupBy">${renderReportOptionSet(['CATEGORY','LOCATION','DAY'], chart.groupBy || 'CATEGORY')}</select>
        <select class="form-control form-control-sm" data-chart-field="metric">${renderReportOptionSet(['TOTAL_AMOUNT','EXPENSE_COUNT','AVERAGE_AMOUNT'], chart.metric || 'TOTAL_AMOUNT')}</select>
        <input class="form-control form-control-sm" data-chart-field="limit" type="number" min="1" max="50" value="${Number(chart.limit || 10)}" title="Limit">
        <select class="form-control form-control-sm" data-chart-field="sort">${renderReportOptionSet(['DESC','ASC'], chart.sort || 'DESC')}</select>
        <button class="btn btn-danger btn-sm btn-icon" title="Remove chart" onclick="removeReportChartDefinition(this)"><i class="fa-solid fa-trash"></i></button>
    </div>`;
}

function renderReportOptionSet(values, selected) {
    const target = String(selected || '').toUpperCase();
    return values.map(v => `<option value="${v}" ${target === v ? 'selected' : ''}>${v}</option>`).join('');
}

function addReportChartDefinition() {
    const list = document.getElementById('reportChartEditorList');
    if (!list) return;
    const index = list.querySelectorAll('.report-chart-edit-row').length;
    const row = document.createElement('div');
    row.innerHTML = renderReportChartEditorRow(defaultReportChartDefinition('CATEGORY', `chart-${Date.now()}`), index);
    list.appendChild(row.firstElementChild);
}

function removeReportChartDefinition(btn) {
    const row = btn?.closest('.report-chart-edit-row');
    if (!row) return;
    row.remove();
}

function collectReportChartDefinitions() {
    const rows = Array.from(document.querySelectorAll('#reportChartEditorList .report-chart-edit-row'));
    return rows.map((row, idx) => ({
        id: row.querySelector('[data-chart-field="id"]')?.value || `chart-${idx + 1}`,
        title: row.querySelector('[data-chart-field="title"]')?.value?.trim() || `Chart ${idx + 1}`,
        type: row.querySelector('[data-chart-field="type"]')?.value || 'BAR',
        groupBy: row.querySelector('[data-chart-field="groupBy"]')?.value || 'CATEGORY',
        metric: row.querySelector('[data-chart-field="metric"]')?.value || 'TOTAL_AMOUNT',
        limit: Number(row.querySelector('[data-chart-field="limit"]')?.value || 10),
        sort: row.querySelector('[data-chart-field="sort"]')?.value || 'DESC'
    }));
}

async function saveReportDetailEdits(reportId, groupBy) {
    const chartDefinitions = collectReportChartDefinitions();
    if (!chartDefinitions.length) {
        toast('Please keep at least one chart.', 'info');
        return;
    }

    const body = {
        title: document.getElementById('reportTitleInput')?.value?.trim() || '',
        description: document.getElementById('reportDescriptionInput')?.value?.trim() || '',
        groupBy: groupBy,
        startDate: document.getElementById('reportFilterStartDate')?.value || '',
        endDate: document.getElementById('reportFilterEndDate')?.value || '',
        category: document.getElementById('reportFilterCategory')?.value?.trim() || '',
        country: document.getElementById('reportFilterCountry')?.value?.trim() || '',
        city: document.getElementById('reportFilterCity')?.value?.trim() || '',
        storeName: document.getElementById('reportFilterStoreName')?.value?.trim() || '',
        searchKeywords: _reportEditKeywords,
        search: (_reportEditKeywords || []).join(', '),
        chartDefinitions
    };

    const result = await api(`/api/reports/${reportId}`, { method: 'PUT', body });
    if (!result || result.error) {
        toast(result?.error || 'Failed to update report', 'error');
        return;
    }
    toast('Report updated', 'success');
    const app = document.getElementById('app');
    if (app) renderReportDetail(app, reportId);
}

function formatCoveredDateRange(startDate, endDate) {
    if (!startDate && !endDate) return '—';
    return `${startDate || '—'} → ${endDate || '—'}`;
}

function renderChartShells(charts) {
    if (!charts.length) {
        return `<div class="card report-empty-inline">No charts available for this report.</div>`;
    }
    return charts.map((chart, index) => {
        if ((chart.type || '').toUpperCase() === 'TABLE') {
            return `<div class="card"><h3 class="card-title"><i class="fa-solid fa-table"></i> ${esc(chart.title || 'Table')}</h3>${renderSimpleChartTable(chart)}</div>`;
        }
        return `<div class="card report-chart-card">
            <h3 class="card-title"><i class="fa-solid fa-chart-column"></i> ${esc(chart.title || 'Chart')}</h3>
            <div class="chart-container"><canvas id="reportChartCanvas${index}"></canvas></div>
        </div>`;
    }).join('');
}

function renderSimpleChartTable(chart) {
    const rows = (chart.labels || []).map((label, idx) => `
        <tr>
            <td>${esc(label)}</td>
            <td class="amount-primary">${Number(chart.values?.[idx] || 0).toFixed(2)}</td>
        </tr>`).join('');
    return `<div class="table-responsive"><table><thead><tr><th>Label</th><th>Value</th></tr></thead><tbody>${rows || '<tr><td colspan="2">No data</td></tr>'}</tbody></table></div>`;
}

function renderReportExpensesTable(expenses) {
    if (!expenses.length) {
        return '<div class="report-empty-inline">No expenses are included in this report.</div>';
    }
    return `<div class="table-responsive">
        <table>
            <thead>
                <tr>
                    <th>Date</th>
                    <th>Description</th>
                    <th>Location</th>
                    <th>Amount</th>
                    <th>Base</th>
                </tr>
            </thead>
            <tbody>
                ${expenses.map(expense => `
                    <tr onclick="navigate('#/expenses/${expense.urlId}')" style="cursor:pointer;">
                        <td>${expense.transactionDatetime ? new Date(expense.transactionDatetime).toLocaleDateString() : '—'}</td>
                        <td>${esc(expense.category || expense.notes || 'Expense')}</td>
                        <td>${esc(expense.locationLabel || expense.storeName || '—')}</td>
                        <td class="amount-primary">${expense.amount != null ? Number(expense.amount).toFixed(2) : '—'} ${esc(expense.currency || '')}</td>
                        <td class="amount-secondary">${expense.amountInBase != null ? Number(expense.amountInBase).toFixed(2) + ' ' + esc(currentUser?.baseCurrency || '') : '—'}</td>
                    </tr>`).join('')}
            </tbody>
        </table>
    </div>`;
}

function renderReportCharts(charts) {
    destroyReportCharts();
    charts.forEach((chart, index) => {
        const type = (chart.type || 'BAR').toUpperCase();
        if (type === 'TABLE') return;
        const canvas = document.getElementById(`reportChartCanvas${index}`);
        if (!canvas) return;

        const chartKey = `report-${index}-${Date.now()}`;
        _reportChartKeys.push(chartKey);
        const config = buildReportChartConfig(type, chart);
        chartInstances[chartKey] = new Chart(canvas, config);
    });
}

function buildReportChartConfig(type, chart) {
    const labels = chart.labels || [];
    const values = chart.values || [];
    const plugins = typeof chartPluginOptions === 'function' ? chartPluginOptions() : { legend: { labels: { color: '#666' } } };
    const scales = typeof chartScaleOptions === 'function' ? chartScaleOptions() : {};

    if (type === 'DOUGHNUT') {
        return {
            type: 'doughnut',
            data: {
                labels,
                datasets: [{
                    data: values,
                    backgroundColor: typeof CHART_COLORS !== 'undefined' ? CHART_COLORS : ['#1565C0','#42A5F5','#66BB6A','#FF7043','#7C4DFF','#D32F2F']
                }]
            },
            options: { responsive: true, maintainAspectRatio: false, plugins }
        };
    }

    if (type === 'LINE') {
        return {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: chart.title || 'Series',
                    data: values,
                    borderColor: '#42A5F5',
                    backgroundColor: 'rgba(66,165,245,0.15)',
                    fill: true,
                    tension: 0.25
                }]
            },
            options: { responsive: true, maintainAspectRatio: false, plugins, scales }
        };
    }

    return {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                label: chart.title || 'Series',
                data: values,
                backgroundColor: 'rgba(25, 118, 210, 0.75)',
                borderRadius: 6
            }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { ...plugins, legend: { display: false } }, scales }
    };
}

function destroyReportCharts() {
    _reportChartKeys.forEach(key => {
        if (chartInstances[key]) {
            chartInstances[key].destroy();
            delete chartInstances[key];
        }
    });
    _reportChartKeys = [];
}

async function openGenerateReportModal(prefill = {}) {
    const options = await loadReportFilterOptions();
    const existing = document.getElementById('generateReportModal');
    if (existing) existing.remove();

    const merged = {
        title: '',
        description: '',
        startDate: '',
        endDate: '',
        category: '',
        country: '',
        storeName: '',
        search: '',
        groupBy: 'KEYWORD',
        ...prefill
    };

    const dialog = document.createElement('div');
    dialog.id = 'generateReportModal';
    dialog.className = 'modal-overlay';
    dialog.innerHTML = `
        <div class="modal-content report-modal-content" style="max-width:720px; max-height:90vh; display:flex; flex-direction:column; padding:0; overflow:hidden;">
            <div class="modal-header" style="padding:1.25rem 1.5rem 1rem; flex-shrink:0;">
                <h3><i class="fa-solid fa-wand-magic-sparkles"></i> Generate Report</h3>
                <button class="modal-close" onclick="closeGenerateReportModal()">&times;</button>
            </div>
            <div class="report-modal-body">
                <div class="form-group">
                    <label>Title</label>
                    <input type="text" class="form-control" id="reportTitle" maxlength="255" value="${esc(merged.title)}" placeholder="Optional — leave blank to auto-generate">
                </div>
                <div class="form-group">
                    <label>Description</label>
                    <textarea class="form-control" id="reportDescription" maxlength="5000" placeholder="Optional notes about this report">${esc(merged.description)}</textarea>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Start date</label>
                        <input type="date" class="form-control" id="reportStartDate" value="${esc(merged.startDate)}">
                    </div>
                    <div class="form-group">
                        <label>End date</label>
                        <input type="date" class="form-control" id="reportEndDate" value="${esc(merged.endDate)}">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Category</label>
                        <select class="form-control" id="reportCategory">
                            <option value="">All Categories</option>
                            ${options.categories.map(c => `<option value="${esc(c)}" ${merged.category === c ? 'selected' : ''}>${esc(c)}</option>`).join('')}
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Country</label>
                        <select class="form-control" id="reportCountry">
                            <option value="">All Countries</option>
                            ${options.countries.map(c => `<option value="${esc(c.code)}" ${merged.country === c.code || merged.country === c.name ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Store</label>
                        <select class="form-control" id="reportStoreName">
                            <option value="">All Stores</option>
                            ${options.storeNames.map(name => `<option value="${esc(name)}" ${merged.storeName === name ? 'selected' : ''}>${esc(name)}</option>`).join('')}
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Keyword</label>
                        <input type="text" class="form-control" id="reportSearch" maxlength="100" value="${esc(merged.search)}" placeholder="Search notes, tags, stores...">
                    </div>
                </div>
                <div class="form-group">
                    <label>Group by</label>
                    <select class="form-control" id="reportGroupBy">
                        <option value="CATEGORY" ${merged.groupBy === 'CATEGORY' ? 'selected' : ''}>Category</option>
                        <option value="STORE_LOCATION" ${merged.groupBy === 'STORE_LOCATION' ? 'selected' : ''}>Store location</option>
                        <option value="KEYWORD" ${merged.groupBy === 'KEYWORD' ? 'selected' : ''}>Keyword</option>
                    </select>
                </div>
                <p class="reports-copy" style="margin-top:0.25rem;">Reports are saved snapshots. Future changes to your expenses will not automatically change the report membership.</p>
            </div>
            <div class="report-modal-actions">
                <button class="btn btn-primary" onclick="submitGenerateReportModal()"><i class="fa-solid fa-check"></i> Generate</button>
                <button class="btn btn-outline" onclick="closeGenerateReportModal()">Cancel</button>
            </div>
        </div>`;
    dialog.addEventListener('click', e => { if (e.target === dialog) closeGenerateReportModal(); });
    document.body.appendChild(dialog);
    _registerEscHandler('generateReportModal', closeGenerateReportModal);
}

function closeGenerateReportModal() {
    const dialog = document.getElementById('generateReportModal');
    if (dialog) dialog.remove();
    _unregisterEscHandler('generateReportModal');
}

async function submitGenerateReportModal() {
    const body = {
        groupBy: document.getElementById('reportGroupBy')?.value || 'KEYWORD'
    };

    const title = document.getElementById('reportTitle')?.value?.trim();
    const description = document.getElementById('reportDescription')?.value?.trim();
    const startDate = document.getElementById('reportStartDate')?.value || '';
    const endDate = document.getElementById('reportEndDate')?.value || '';
    const category = document.getElementById('reportCategory')?.value || '';
    const country = document.getElementById('reportCountry')?.value || '';
    const storeName = document.getElementById('reportStoreName')?.value || '';
    const search = document.getElementById('reportSearch')?.value?.trim() || '';

    if (title) body.title = title;
    if (description) body.description = description;
    if (startDate) body.startDate = startDate;
    if (endDate) body.endDate = endDate;
    if (category) body.category = category;
    if (country) body.country = country;
    if (storeName) body.storeName = storeName;
    if (search) body.search = search;

    const result = await api('/api/reports', { method: 'POST', body });
    if (!result || result.error) {
        toast(result?.error || 'Failed to generate report', 'error');
        return;
    }

    closeGenerateReportModal();
    toast('Report generated', 'success');
    navigate(`#/reports/${result.id}`);
}

async function deleteReportFromList(reportId) {
    if (!confirm('Delete this report?')) return;
    const result = await api(`/api/reports/${reportId}`, { method: 'DELETE' });
    if (result?.error) {
        toast(result.error, 'error');
        return;
    }
    toast('Report deleted', 'success');
    const app = document.getElementById('app');
    if (app) renderReportsPage(app);
}

async function exportReportPdf(reportId) {
    try {
        const response = await fetch(`/api/reports/${reportId}/pdf`, { credentials: 'include' });
        if (!response.ok) {
            let message = 'Failed to export report PDF';
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                const data = await response.json();
                if (data?.error) message = data.error;
            }
            toast(message, 'error');
            return;
        }

        const blob = await response.blob();
        const disposition = response.headers.get('content-disposition') || '';
        const filenameMatch = disposition.match(/filename\*?=(?:UTF-8''|\")?([^\";]+)/i);
        const filename = filenameMatch?.[1] ? decodeURIComponent(filenameMatch[1].replace(/\"/g, '')) : `report-${reportId}.pdf`;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
        toast('Report PDF exported', 'success');
    } catch (error) {
        toast('Failed to export report PDF', 'error');
    }
}

async function deleteReportAndReturn(reportId) {
    if (!confirm('Delete this report?')) return;
    const result = await api(`/api/reports/${reportId}`, { method: 'DELETE' });
    if (result?.error) {
        toast(result.error, 'error');
        return;
    }
    toast('Report deleted', 'success');
    navigate('#/reports');
}

async function openReportMatchingExpenses(reportId) {
    const report = await api(`/api/reports/${reportId}`);
    if (!report || report.error || !report.filterSnapshot) {
        toast('This report does not have reusable filters.', 'info');
        return;
    }
    const params = new URLSearchParams();
    if (report.filterSnapshot.startDate) params.set('startDate', report.filterSnapshot.startDate);
    if (report.filterSnapshot.endDate) params.set('endDate', report.filterSnapshot.endDate);
    if (report.filterSnapshot.category) params.set('category', report.filterSnapshot.category);
    if (report.filterSnapshot.country) params.set('country', report.filterSnapshot.country);
    const search = report.filterSnapshot.search || report.filterSnapshot.city || report.filterSnapshot.storeName;
    if (search) params.set('search', search);
    navigate('#/expenses' + (params.toString() ? '?' + params.toString() : ''));
}

function formatGroupBy(groupBy) {
    switch ((groupBy || '').toUpperCase()) {
        case 'CATEGORY': return 'Category';
        case 'STORE_LOCATION': return 'Store Location';
        case 'KEYWORD': return 'Keyword';
        default: return groupBy || '—';
    }
}

function formatMoney(value, currency) {
    if (value == null || value === '') return '—';
    return `${Number(value).toFixed(2)} ${currency || ''}`.trim();
}

function formatReportDate(value) {
    if (!value) return '—';
    return new Date(value).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function toggleReportDetailMenu() {
    const dd = document.getElementById('reportActionDropdown');
    if (!dd) return;
    const visible = dd.style.display !== 'none';
    dd.style.display = visible ? 'none' : 'block';
    if (!visible) {
        setTimeout(() => {
            document.addEventListener('click', function closeMenu(e) {
                if (!document.getElementById('reportActionMenuWrap')?.contains(e.target)) {
                    closeReportDetailMenu();
                    document.removeEventListener('click', closeMenu);
                }
            });
        }, 10);
    }
}

function closeReportDetailMenu() {
    const dd = document.getElementById('reportActionDropdown');
    if (dd) dd.style.display = 'none';
}




