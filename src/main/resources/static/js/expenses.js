/* ============================================
   Expense Tracker - Expense List & Table
   ============================================ */

let _expensePage = 0;
const _expensePageSize = 20;
let _expenseSortField = 'transactionDatetime';
let _expenseSortDir = 'desc';
let _allExpenses = [];

async function renderExpenseList(app) {
    const dashData = await api('/api/dashboard');
    const categories = dashData?.categories || [];
    const countries = (dashData?.geoByCountry || []).map(c => ({ code: c.country, name: c.countryName || c.country })).filter(c => c.code);
    const seenCodes = new Set();
    const uniqueCountries = countries.filter(c => { if (seenCodes.has(c.code)) return false; seenCodes.add(c.code); return true; });

    cacheCountryNames(dashData?.geoByCountry);
    window._allExpenseCategories = dashData?.categories || [];

    app.innerHTML = `
    <div class="container">
        <div class="action-bar">
            <div class="action-bar-left">
                <h2 style="color:var(--primary-dark)"><i class="fa-solid fa-receipt"></i> Expenses</h2>
            </div>
            <div class="action-bar-right">
                <button class="btn btn-outline btn-sm expense-desktop-only" onclick="exportExpenses('csv')">
                    <i class="fa-solid fa-file-csv"></i> CSV
                </button>
                <button class="btn btn-outline btn-sm expense-desktop-only" onclick="exportExpenses('json')">
                    <i class="fa-solid fa-file-code"></i> JSON
                </button>
                <label class="btn btn-outline btn-sm expense-desktop-only">
                    <input type="checkbox" id="showDeleted" onchange="loadExpenses()"> <i class="fa-solid fa-trash-can"></i> Deleted
                </label>
            </div>
        </div>
        <div class="expense-filters-bar">
            <div class="exp-search-row">
                <input type="text" class="form-control form-control-sm" id="expenseSearch" placeholder="Search expenses, items, stores..." onkeyup="debounceSearch()">
                <button class="btn btn-outline btn-sm exp-more-filters-btn" id="expMoreFiltersBtn" onclick="toggleExpenseMoreFilters()">
                    <i class="fa-solid fa-sliders"></i> <span id="expMoreFiltersLabel">More filters</span>
                </button>
            </div>
            <div class="exp-extra-filters" id="expExtraFilters">
                <input type="date" class="form-control form-control-sm" id="expFilterStartDate" onchange="loadExpenses()" title="Start date">
                <input type="date" class="form-control form-control-sm" id="expFilterEndDate" onchange="loadExpenses()" title="End date">
                <select class="form-control form-control-sm" id="expFilterCategory" onchange="loadExpenses()">
                    <option value="">All Categories</option>
                    ${categories.map(c => `<option value="${c}">${c}</option>`).join('')}
                </select>
                <select class="form-control form-control-sm" id="expFilterCountry" onchange="loadExpenses()">
                    <option value="">All Countries</option>
                    ${uniqueCountries.map(c => `<option value="${c.code}">${c.name}</option>`).join('')}
                </select>
            </div>
        </div>
        <div class="card">
            <div class="table-responsive">
                <table class="expense-table">
                    <thead><tr>
                        <th class="th-status" style="width:36px;"></th>
                        <th class="sortable" data-field="transactionDatetime" onclick="sortExpenses('transactionDatetime')">Date <i class="fa-solid fa-sort" id="sort-transactionDatetime"></i></th>
                        <th class="sortable" data-field="displayName" onclick="sortExpenses('displayName')">Description <i class="fa-solid fa-sort" id="sort-displayName"></i></th>
                        <th class="sortable" data-field="amount" onclick="sortExpenses('amount')">Amount <i class="fa-solid fa-sort" id="sort-amount"></i></th>
                        <th class="sortable" data-field="amountInBase" onclick="sortExpenses('amountInBase')">Base <i class="fa-solid fa-sort" id="sort-amountInBase"></i></th>
                        <th>Actions</th>
                    </tr></thead>
                    <tbody id="expenseTableBody"></tbody>
                </table>
            </div>
            <div class="pagination-bar" id="expensePagination"></div>
        </div>
    </div>`;
    _expensePage = 0;
    _expMoreFiltersShown = false;

    // Pre-fill filters from URL hash params (e.g. #/expenses?startDate=...&category=...)
    const hashQuery = window.location.hash.split('?')[1];
    if (hashQuery) {
        const hp = new URLSearchParams(hashQuery);
        if (hp.get('search')) {
            const searchEl = document.getElementById('expenseSearch');
            if (searchEl) searchEl.value = hp.get('search');
        }
        if (hp.get('startDate') || hp.get('endDate') || hp.get('category') || hp.get('country')) {
            toggleExpenseMoreFilters();
            if (hp.get('startDate')) {
                const el = document.getElementById('expFilterStartDate');
                if (el) el.value = hp.get('startDate');
            }
            if (hp.get('endDate')) {
                const el = document.getElementById('expFilterEndDate');
                if (el) el.value = hp.get('endDate');
            }
            if (hp.get('category')) {
                const el = document.getElementById('expFilterCategory');
                if (el) el.value = hp.get('category');
            }
            if (hp.get('country')) {
                const el = document.getElementById('expFilterCountry');
                if (el) el.value = hp.get('country');
            }
        }
    }

    await loadExpenses();
}

let searchTimeout;
function debounceSearch() { clearTimeout(searchTimeout); searchTimeout = setTimeout(() => { _expensePage = 0; loadExpenses(); }, 400); }

let _expMoreFiltersShown = false;
function toggleExpenseMoreFilters() {
    if (_expMoreFiltersShown) return;
    _expMoreFiltersShown = true;
    const extra = document.getElementById('expExtraFilters');
    const btn = document.getElementById('expMoreFiltersBtn');
    if (extra) extra.classList.add('visible');
    if (btn) btn.style.display = 'none';
}

function sortExpenses(field) {
    if (_expenseSortField === field) {
        _expenseSortDir = _expenseSortDir === 'asc' ? 'desc' : 'asc';
    } else {
        _expenseSortField = field;
        _expenseSortDir = field === 'transactionDatetime' ? 'desc' : 'asc';
    }
    _expensePage = 0;
    renderExpenseTable();
}

function getSortedExpenses() {
    const sorted = [..._allExpenses];
    sorted.sort((a, b) => {
        let va = a[_expenseSortField];
        let vb = b[_expenseSortField];
        if (va == null && vb == null) return 0;
        if (va == null) return 1;
        if (vb == null) return -1;
        if (typeof va === 'number' || (_expenseSortField === 'amount' || _expenseSortField === 'amountInBase')) {
            va = Number(va) || 0;
            vb = Number(vb) || 0;
            return _expenseSortDir === 'asc' ? va - vb : vb - va;
        }
        va = String(va).toLowerCase();
        vb = String(vb).toLowerCase();
        if (va < vb) return _expenseSortDir === 'asc' ? -1 : 1;
        if (va > vb) return _expenseSortDir === 'asc' ? 1 : -1;
        return 0;
    });
    return sorted;
}

async function loadExpenses() {
    const search = document.getElementById('expenseSearch')?.value || '';
    const incDel = document.getElementById('showDeleted')?.checked || false;
    const startDate = document.getElementById('expFilterStartDate')?.value || '';
    const endDate = document.getElementById('expFilterEndDate')?.value || '';
    const category = document.getElementById('expFilterCategory')?.value || '';
    const country = document.getElementById('expFilterCountry')?.value || '';
    const params = new URLSearchParams();
    if (search) params.set('search', search);
    if (incDel) params.set('includeDeleted', 'true');
    if (startDate) params.set('startDate', startDate);
    if (endDate) params.set('endDate', endDate);
    if (category) params.set('category', category);
    if (country) params.set('country', country);
    const expenses = await api('/api/expenses?' + params.toString());
    if (!expenses || !Array.isArray(expenses)) return;
    _allExpenses = expenses;
    renderExpenseTable();
}

function renderExpenseTable() {
    const sorted = getSortedExpenses();
    const total = sorted.length;
    const totalPages = Math.max(1, Math.ceil(total / _expensePageSize));
    if (_expensePage >= totalPages) _expensePage = totalPages - 1;
    const start = _expensePage * _expensePageSize;
    const page = sorted.slice(start, start + _expensePageSize);

    const tbody = document.getElementById('expenseTableBody');
    if (!tbody) return;
    tbody.innerHTML = page.map(e => {
        const isFailed = e.status === 'FAILED';
        const failTitle = isFailed && e.notes ? esc(e.notes).replace(/"/g, '&quot;') : '';
        let rows = `
        <tr class="${e.deleted ? 'deleted' : ''} expense-row ${isFailed ? 'row-failed' : ''}"
            onclick="navigate('#/expenses/${e.urlId}')"
            ${isFailed ? `title="${failTitle}"` : ''}>
            <td class="td-status">${statusBadge(e.status)}</td>
            <td>${e.transactionDatetime ? new Date(e.transactionDatetime).toLocaleDateString() : '-'}</td>
            <td class="td-description">${e.displayName || e.category || '-'}</td>
            <td class="amount-primary">${e.amount != null ? Number(e.amount).toFixed(2) : '-'} ${e.currency || ''}</td>
            <td class="amount-secondary">${e.amountInBase != null ? Number(e.amountInBase).toFixed(2) + ' ' + (currentUser?.baseCurrency||'') : '-'}</td>
            <td class="td-actions" onclick="event.stopPropagation()">
                ${e.deleted ? `<button class="btn btn-success btn-sm btn-icon" onclick="restoreExpense('${e.urlId}')"><i class="fa-solid fa-rotate-left"></i></button>` : `
                    <button class="btn btn-secondary btn-sm btn-icon" onclick="duplicateExpense('${e.urlId}')"><i class="fa-solid fa-copy"></i></button>
                    ${isFailed ? `<button class="btn btn-secondary btn-sm btn-icon" onclick="retryExpense('${e.urlId}')"><i class="fa-solid fa-rotate"></i></button>` : ''}
                    <button class="btn btn-danger btn-sm btn-icon" onclick="deleteExpense('${e.urlId}')"><i class="fa-solid fa-trash"></i></button>
                `}
            </td>
        </tr>`;
        if (e.matchingItems && e.matchingItems.length > 0) {
            for (const item of e.matchingItems) {
                rows += `
                <tr class="item-child-row" onclick="navigate('#/expenses/${e.urlId}')">
                    <td></td><td></td>
                    <td class="td-description item-child-name"><i class="fa-solid fa-arrow-turn-up fa-rotate-90" style="font-size:0.65rem; opacity:0.4; margin-right:0.3rem;"></i> ${esc(item.itemName)}</td>
                    <td class="amount-primary item-child-amount">${item.unitPrice != null ? Number(item.unitPrice).toFixed(2) : '-'} ${e.currency || ''}</td>
                    <td></td><td></td>
                </tr>`;
            }
        }
        return rows;
    }).join('');

    document.querySelectorAll('.expense-table th .fa-sort, .expense-table th .fa-sort-up, .expense-table th .fa-sort-down').forEach(icon => {
        const field = icon.id.replace('sort-', '');
        icon.className = field === _expenseSortField
            ? (_expenseSortDir === 'asc' ? 'fa-solid fa-sort-up' : 'fa-solid fa-sort-down')
            : 'fa-solid fa-sort';
    });

    renderPagination(totalPages);
}

function statusBadge(status) {
    switch(status) {
        case 'PROCESSING': return '<span class="status-dot status-processing" title="Processing"><i class="fa-solid fa-spinner fa-spin"></i></span>';
        case 'COMPLETED': return '<span class="status-dot status-completed" title="Completed"><i class="fa-solid fa-check"></i></span>';
        case 'FAILED': return '<span class="status-dot status-failed" title="Failed"><i class="fa-solid fa-xmark"></i></span>';
        default: return '<span class="status-dot"></span>';
    }
}

function renderPagination(totalPages) {
    const container = document.getElementById('expensePagination');
    if (!container) return;
    if (totalPages <= 1) { container.innerHTML = `<span class="pagination-info">${_allExpenses.length} expense${_allExpenses.length !== 1 ? 's' : ''}</span>`; return; }

    const mobile = isMobile();
    const maxPageBtns = mobile ? 3 : 5;

    let html = `<span class="pagination-info">${_allExpenses.length} expense${_allExpenses.length !== 1 ? 's' : ''} · Page ${_expensePage + 1}/${totalPages}</span><div class="pagination-btns">`;
    html += `<button class="btn btn-outline btn-sm" ${_expensePage === 0 ? 'disabled' : ''} onclick="goExpensePage(0)"><i class="fa-solid fa-angles-left"></i></button>`;
    html += `<button class="btn btn-outline btn-sm" ${_expensePage === 0 ? 'disabled' : ''} onclick="goExpensePage(${_expensePage - 1})"><i class="fa-solid fa-chevron-left"></i></button>`;

    const half = Math.floor(maxPageBtns / 2);
    const startP = Math.max(0, Math.min(_expensePage - half, totalPages - maxPageBtns));
    const endP = Math.min(totalPages, startP + maxPageBtns);
    for (let i = startP; i < endP; i++) {
        html += `<button class="btn btn-sm ${i === _expensePage ? 'btn-primary' : 'btn-outline'}" onclick="goExpensePage(${i})">${i + 1}</button>`;
    }

    html += `<button class="btn btn-outline btn-sm" ${_expensePage >= totalPages - 1 ? 'disabled' : ''} onclick="goExpensePage(${_expensePage + 1})"><i class="fa-solid fa-chevron-right"></i></button>`;
    html += `<button class="btn btn-outline btn-sm" ${_expensePage >= totalPages - 1 ? 'disabled' : ''} onclick="goExpensePage(${totalPages - 1})"><i class="fa-solid fa-angles-right"></i></button>`;
    html += '</div>';
    container.innerHTML = html;
}

function goExpensePage(p) {
    _expensePage = p;
    renderExpenseTable();
    document.querySelector('.expense-table')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function deleteExpense(id) {
    if (!confirm('Delete this expense?')) return;
    await api(`/api/expenses/${id}`, { method: 'DELETE' });
    toast('Expense deleted', 'success');
    loadExpenses();
}
async function restoreExpense(id) {
    await api(`/api/expenses/${id}/restore`, { method: 'PATCH' });
    toast('Expense restored', 'success');
    loadExpenses();
}
async function duplicateExpense(id) {
    const copy = await api(`/api/expenses/${id}/duplicate`, { method: 'POST' });
    if (copy && copy.id) { toast('Expense duplicated', 'success'); navigate('#/expenses/' + copy.urlId); }
}
async function retryExpense(id) {
    await api(`/api/expenses/${id}/retry`, { method: 'POST' });
    toast('Retry initiated', 'info');
    loadExpenses();
}
async function exportExpenses(format) {
    const search = document.getElementById('expenseSearch')?.value || '';
    const params = new URLSearchParams({ format });
    if (search) params.set('search', search);
    if (format === 'csv') {
        const res = await fetch('/api/expenses/export?' + params.toString(), { credentials: 'include' });
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a'); a.href = url; a.download = 'expenses.csv'; a.click();
        URL.revokeObjectURL(url);
    } else {
        const data = await api('/api/expenses/export?' + params.toString());
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a'); a.href = url; a.download = 'expenses.json'; a.click();
        URL.revokeObjectURL(url);
    }
    toast(`Exported as ${format.toUpperCase()}`, 'success');
}
