/* ============================================
   Expense Tracker - Expense CRUD
   ============================================ */

// ============================================
// EXPENSE LIST
// ============================================
let _expensePage = 0;
const _expensePageSize = 20;
let _expenseSortField = 'transactionDatetime';
let _expenseSortDir = 'desc';
let _allExpenses = [];

async function renderExpenseList(app) {
    // Fetch categories + countries for filters
    const dashData = await api('/api/dashboard');
    const categories = dashData?.categories || [];
    const countries = (dashData?.geoByCountry || []).map(c => ({ code: c.country, name: c.countryName || c.country })).filter(c => c.code);
    const seenCodes = new Set();
    const uniqueCountries = countries.filter(c => { if (seenCodes.has(c.code)) return false; seenCodes.add(c.code); return true; });

    // Cache country names and categories for later use (e.g. expense detail)
    cacheCountryNames(dashData?.geoByCountry);
    window._allExpenseCategories = dashData?.categories || [];

    app.innerHTML = `
    <div class="container">
        <div class="action-bar">
            <div class="action-bar-left">
                <h2 style="color:var(--aegean-dark)"><i class="fa-solid fa-receipt"></i> Expenses</h2>
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
    await loadExpenses();
}

let searchTimeout;
function debounceSearch() { clearTimeout(searchTimeout); searchTimeout = setTimeout(() => { _expensePage = 0; loadExpenses(); }, 400); }

let _expMoreFiltersShown = false;
function toggleExpenseMoreFilters() {
    if (_expMoreFiltersShown) return; // once shown, stay shown
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
        // Handle nulls
        if (va == null && vb == null) return 0;
        if (va == null) return 1;
        if (vb == null) return -1;
        // Numeric
        if (typeof va === 'number' || (_expenseSortField === 'amount' || _expenseSortField === 'amountInBase')) {
            va = Number(va) || 0;
            vb = Number(vb) || 0;
            return _expenseSortDir === 'asc' ? va - vb : vb - va;
        }
        // String
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
        // Show matching items as child rows
        if (e.matchingItems && e.matchingItems.length > 0) {
            for (const item of e.matchingItems) {
                rows += `
                <tr class="item-child-row" onclick="navigate('#/expenses/${e.urlId}')">
                    <td></td>
                    <td></td>
                    <td class="td-description item-child-name"><i class="fa-solid fa-arrow-turn-up fa-rotate-90" style="font-size:0.65rem; opacity:0.4; margin-right:0.3rem;"></i> ${esc(item.itemName)}</td>
                    <td class="amount-primary item-child-amount">${item.unitPrice != null ? Number(item.unitPrice).toFixed(2) : '-'} ${e.currency || ''}</td>
                    <td></td>
                    <td></td>
                </tr>`;
            }
        }
        return rows;
    }).join('');

    // Update sort icons
    document.querySelectorAll('.expense-table th .fa-sort, .expense-table th .fa-sort-up, .expense-table th .fa-sort-down').forEach(icon => {
        const field = icon.id.replace('sort-', '');
        if (field === _expenseSortField) {
            icon.className = _expenseSortDir === 'asc' ? 'fa-solid fa-sort-up' : 'fa-solid fa-sort-down';
        } else {
            icon.className = 'fa-solid fa-sort';
        }
    });

    // Pagination
    renderPagination(totalPages);
}

function statusBadge(status) {
    switch(status) {
        case 'PROCESSING': return '<span class="status-dot status-processing" title="Processing"><i class="fa-solid fa-spinner fa-spin"></i></span>';
        case 'COMPLETED': return '<span class="status-dot status-completed" title="Completed"><i class="fa-solid fa-check"></i></span>';
        case 'FAILED': return '<span class="status-dot status-failed" title="Failed"><i class="fa-solid fa-xmark"></i></span>';
        case 'DRAFT': return '<span class="status-dot status-draft" title="Draft"><i class="fa-solid fa-pencil"></i></span>';
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
    // Scroll to top of table
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

// ============================================
// NEW EXPENSE
// ============================================
function renderNewExpense(app, embedded = false) {
    const container = embedded ? app : app;
    if (!embedded) {
        // Full page mode: wrap in container div
    }
    container.innerHTML = `
    <div class="${embedded ? '' : 'container'}">
        <h2 style="color:var(--aegean-dark); margin-bottom:1rem"><i class="fa-solid fa-plus-circle"></i> New Expense</h2>
        <div class="tabs">
            <div class="tab active" data-tab="manual" onclick="switchTab('manual', this)"><i class="fa-solid fa-pen"></i> Manual Entry</div>
            <div class="tab" data-tab="scan" onclick="switchTab('scan', this)"><i class="fa-solid fa-camera"></i> Scan Receipt</div>
        </div>
        <div id="tab-manual" class="tab-content active">
            <div class="card">
                <form id="manualForm">
                    <div class="form-group">
                        <label>Amount <span style="color:var(--danger)">*</span></label>
                        <input type="number" step="0.01" class="form-control" id="mAmount" required placeholder="0.00">
                    </div>
                    <div class="form-group">
                        <label>Notes</label>
                        <textarea class="form-control" id="mNotes" placeholder="What was this expense for?"></textarea>
                    </div>
                    <div class="expand-toggle" onclick="toggleNewExpenseDetails()">
                        <i class="fa-solid fa-chevron-down" id="expandIcon"></i> <span id="expandLabel">More details</span>
                    </div>
                    <div class="expandable-section" id="newExpenseDetails" style="display:none;">
                        <div class="form-row">
                            <div class="form-group">
                                <label>Date & Time</label>
                                <input type="datetime-local" class="form-control" id="mDate">
                            </div>
                            <div class="form-group">
                                <label>Category</label>
                                <input type="text" class="form-control" id="mCategory" placeholder="e.g. Food, Transport" list="mCategoryList" autocomplete="off">
                                <datalist id="mCategoryList"></datalist>
                            </div>
                        </div>
                        <div class="form-row-3">
                            <div class="form-group">
                                <label>Currency</label>
                                <input type="text" class="form-control" id="mCurrency" list="mCurrencyList" placeholder="e.g. USD">
                                <datalist id="mCurrencyList">
                                    <option value="USD"></option><option value="EUR"></option>
                                    <option value="GBP"></option><option value="SGD"></option>
                                    <option value="JPY"></option><option value="AUD"></option>
                                    <option value="CAD"></option><option value="CHF"></option>
                                </datalist>
                            </div>
                            <div class="form-group">
                                <label>Exchange Rate</label>
                                <input type="number" step="0.000001" class="form-control" id="mExRate" placeholder="Auto-fetched">
                            </div>
                            <div class="form-group">
                                <label>Receipt Number</label>
                                <input type="text" class="form-control" id="mReceipt">
                            </div>
                        </div>
                        <div class="form-group">
                            <label>Tags (press Enter to add)</label>
                            <div class="tags-container" id="mTagsContainer">
                                <input type="text" class="tag-input" id="mTagInput" placeholder="Add tag...">
                            </div>
                        </div>
                        <div class="form-group">
                            <label>Attachments</label>
                            <input type="file" class="form-control" id="mAttachments" multiple>
                        </div>
                    </div>
                    <button type="submit" class="btn btn-primary" style="margin-top:1rem;"><i class="fa-solid fa-save"></i> Save Expense</button>
                </form>
            </div>
        </div>
        <div id="tab-scan" class="tab-content">
            <div class="card">
                <div class="upload-zone" id="uploadZone" onclick="document.getElementById('receiptFile').click()">
                    <i class="fa-solid fa-cloud-arrow-up"></i>
                    <p>Click or drag & drop receipt image here</p>
                    <p style="font-size:0.8rem; margin-top:0.5rem">Supported: JPG, PNG, PDF</p>
                    <input type="file" id="receiptFile" style="display:none" accept="image/*,.pdf" onchange="uploadReceipt()">
                </div>
                <div style="text-align:center; margin-top:1rem">
                    <span style="color:var(--text-light)">\u2014 or \u2014</span>
                </div>
                <div style="text-align:center; margin-top:1rem">
                    <button class="btn btn-secondary" id="desktopCameraBtn" onclick="openDesktopCamera()">
                        <i class="fa-solid fa-camera"></i> Use Camera
                    </button>
                </div>
                <div id="desktopCameraContainer" style="display:none; margin-top:1rem;">
                    <video id="desktopCameraPreview" autoplay playsinline muted style="width:100%; border-radius:var(--radius); background:#000;"></video>
                    <canvas id="desktopCameraCanvas" style="display:none;"></canvas>
                    <div style="display:flex; gap:0.5rem; margin-top:0.75rem; justify-content:center;">
                        <button class="btn btn-primary" onclick="desktopCapturePhoto()"><i class="fa-solid fa-circle-dot"></i> Capture</button>
                        <button class="btn btn-outline" onclick="closeDesktopCamera()"><i class="fa-solid fa-xmark"></i> Cancel</button>
                    </div>
                </div>
                <div id="uploadStatus" style="margin-top:1rem; display:none"></div>
            </div>
        </div>
    </div>`;

    // Set date default
    document.getElementById('mDate').value = new Date().toISOString().slice(0, 16);
    // Set user's base currency as default
    if (currentUser?.baseCurrency) {
        const currSel = document.getElementById('mCurrency');
        if (currSel) currSel.value = currentUser.baseCurrency;
    }

    // Populate category datalist
    (async () => {
        const cats = (await api('/api/expenses/categories')) || [];
        const dl = document.getElementById('mCategoryList');
        if (dl) dl.innerHTML = cats.map(c => `<option value="${esc(c)}">`).join('');
    })();

    // Populate currency datalists from server
    (async () => {
        try {
            const map = await api('/api/currencies');
            if (map) {
                const codes = Object.keys(map).sort();
                const mdl = document.getElementById('mCurrencyList');
                if (mdl) mdl.innerHTML = codes.map(c => `<option value="${c}"></option>`).join('');
                const edl = document.getElementById('eCurrencyList');
                if (edl) edl.innerHTML = codes.map(c => `<option value="${c}"></option>`).join('');
                // If currentUser has baseCurrency, ensure mCurrency default set earlier still works
                const currSel = document.getElementById('mCurrency');
                if (currSel && currentUser?.baseCurrency && !currSel.value) currSel.value = currentUser.baseCurrency;
            }
        } catch (err) {
            // ignore
        }
    })();

    let tags = [];
    document.getElementById('mTagInput').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const val = e.target.value.trim();
            if (val && !tags.includes(val)) { tags.push(val); renderTags('mTagsContainer', 'mTagInput', tags); }
            e.target.value = '';
        }
    });

    document.getElementById('manualForm').onsubmit = async (ev) => {
        ev.preventDefault();
        const expense = {
            transactionDatetime: (document.getElementById('mDate').value || new Date().toISOString().slice(0, 16)) + ':00',
            amount: parseFloat(document.getElementById('mAmount').value),
            currency: document.getElementById('mCurrency').value,
            category: document.getElementById('mCategory').value,
            receiptNumber: document.getElementById('mReceipt').value,
            tags: tags,
            notes: document.getElementById('mNotes').value
        };
        const exRate = document.getElementById('mExRate').value;
        if (exRate) expense.exchangeRate = parseFloat(exRate);

        const saved = await api('/api/expenses/manual', { method: 'POST', body: expense });
        if (saved && saved.id) {
            const fileInput = document.getElementById('mAttachments');
            if (fileInput) {
                const files = fileInput.files;
                for (let f of files) {
                    const fd = new FormData(); fd.append('file', f);
                    await api(`/api/expenses/${saved.urlId}/attachments`, { method: 'POST', body: fd });
                }
            }
            toast('Expense created!', 'success');
            navigate('#/expenses/' + saved.urlId);
        } else {
            toast('Failed to create expense', 'error');
        }
    };

    const zone = document.getElementById('uploadZone');
    zone.addEventListener('dragover', (e) => { e.preventDefault(); zone.style.borderColor = 'var(--aegean-mid)'; });
    zone.addEventListener('dragleave', () => { zone.style.borderColor = 'var(--aegean-pale)'; });
    zone.addEventListener('drop', (e) => {
        e.preventDefault(); zone.style.borderColor = 'var(--aegean-pale)';
        const file = e.dataTransfer.files[0];
        if (file) { document.getElementById('receiptFile').files = e.dataTransfer.files; uploadReceipt(); }
    });

    // Auto-switch to Scan tab if ?tab=scan in the URL hash
    if (window.location.hash.includes('tab=scan')) {
        const scanTabEl = container.querySelector('.tab[data-tab="scan"]');
        switchTab('scan', scanTabEl);
        // Auto-open camera if camera=1
        if (window.location.hash.includes('camera=1')) {
            const uploadZone = document.getElementById('uploadZone');
            if (uploadZone) uploadZone.style.display = 'none';
            const orDivider = uploadZone?.nextElementSibling;
            if (orDivider) orDivider.style.display = 'none';
            setTimeout(() => openDesktopCamera(), 300);
        }
    }
}

function toggleNewExpenseDetails() {
    const section = document.getElementById('newExpenseDetails');
    const icon = document.getElementById('expandIcon');
    const label = document.getElementById('expandLabel');
    if (section.style.display === 'none') {
        section.style.display = 'block';
        icon.className = 'fa-solid fa-chevron-up';
        label.textContent = 'Less details';
    } else {
        section.style.display = 'none';
        icon.className = 'fa-solid fa-chevron-down';
        label.textContent = 'More details';
    }
}

function switchTab(tab, el) {
    // Find the closest parent that contains the tabs (support embedded panels)
    const root = el ? el.closest('.tabs')?.parentElement : document;
    const tabs = root ? root.querySelectorAll('.tab') : document.querySelectorAll('.tab');
    const contents = root ? root.querySelectorAll('.tab-content') : document.querySelectorAll('.tab-content');
    tabs.forEach(t => t.classList.toggle('active', t.getAttribute('data-tab') === tab));
    contents.forEach(tc => tc.classList.toggle('active', tc.id === 'tab-' + tab));
}

async function uploadReceipt() {
    const file = document.getElementById('receiptFile').files[0];
    if (!file) return;
    const statusEl = document.getElementById('uploadStatus');
    statusEl.style.display = 'block';
    statusEl.innerHTML = '<div class="badge badge-processing"><i class="fa-solid fa-spinner fa-spin"></i> Uploading and processing...</div>';
    const fd = new FormData(); fd.append('file', file);
    const result = await api('/api/expenses/scan', { method: 'POST', body: fd });
    if (result && result.id) {
        toast('Receipt uploaded! Processing...', 'info');
        navigate('#/expenses/' + result.urlId);
    } else {
        statusEl.innerHTML = '<div class="badge badge-failed"><i class="fa-solid fa-xmark"></i> Upload failed</div>';
        toast('Upload failed', 'error');
    }
}

// Desktop camera for new expense page
let desktopCameraStream = null;

async function openDesktopCamera() {
    try {
        const video = document.getElementById('desktopCameraPreview');
        const container = document.getElementById('desktopCameraContainer');

        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            toast('Camera requires HTTPS or localhost', 'error');
            return;
        }

        try {
            desktopCameraStream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1080 } }
            });
        } catch (envErr) {
            // Fallback to any camera
            desktopCameraStream = await navigator.mediaDevices.getUserMedia({ video: true });
        }

        video.srcObject = desktopCameraStream;
        await video.play();
        container.style.display = 'block';
        document.getElementById('desktopCameraBtn').style.display = 'none';
    } catch (err) {
        let msg = 'Camera access denied or not available';
        if (err.name === 'NotAllowedError') msg = 'Camera permission denied. Allow access in browser settings.';
        else if (err.name === 'NotFoundError') msg = 'No camera found on this device.';
        toast(msg, 'error');
    }
}

function closeDesktopCamera() {
    if (desktopCameraStream) {
        desktopCameraStream.getTracks().forEach(t => t.stop());
        desktopCameraStream = null;
    }
    const container = document.getElementById('desktopCameraContainer');
    if (container) container.style.display = 'none';
    const btn = document.getElementById('desktopCameraBtn');
    if (btn) btn.style.display = '';
    // Restore drag & drop zone and or-divider (initial state)
    const uploadZone = document.getElementById('uploadZone');
    if (uploadZone) uploadZone.style.display = '';
    const orDivider = uploadZone?.nextElementSibling;
    if (orDivider && orDivider.tagName === 'DIV') orDivider.style.display = '';
}

async function desktopCapturePhoto() {
    const video = document.getElementById('desktopCameraPreview');
    const canvas = document.getElementById('desktopCameraCanvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);

    // Don't stop the camera yet — user may want to Retry
    const dataUrl = canvas.toDataURL('image/jpeg', 0.9);

    // Show capture confirmation overlay
    const overlay = document.createElement('div');
    overlay.className = 'capture-overlay';
    overlay.id = 'captureOverlay';
    overlay.innerHTML = `
        <div class="capture-preview-container">
            <img src="${dataUrl}" class="capture-preview-img" alt="Captured photo">
            <p class="capture-prompt">Use this photo?</p>
            <div class="capture-actions">
                <button class="btn btn-secondary" onclick="captureRetry()"><i class="fa-solid fa-rotate"></i> Retry</button>
                <button class="btn btn-primary" onclick="captureSubmit()"><i class="fa-solid fa-check"></i> Submit</button>
                <button class="btn btn-outline" onclick="captureCancel()"><i class="fa-solid fa-xmark"></i> Cancel</button>
            </div>
        </div>`;
    document.body.appendChild(overlay);
}

function captureRetry() {
    // Remove overlay, keep camera running
    const overlay = document.getElementById('captureOverlay');
    if (overlay) overlay.remove();
}

function captureSubmit() {
    const overlay = document.getElementById('captureOverlay');
    if (overlay) overlay.remove();
    const canvas = document.getElementById('desktopCameraCanvas');
    closeDesktopCamera();

    canvas.toBlob(async (blob) => {
        if (!blob) { toast('Failed to capture photo', 'error'); return; }
        const statusEl = document.getElementById('uploadStatus');
        statusEl.style.display = 'block';
        statusEl.innerHTML = '<div class="badge badge-processing"><i class="fa-solid fa-spinner fa-spin"></i> Uploading captured image...</div>';
        const fd = new FormData();
        fd.append('file', blob, 'receipt_' + Date.now() + '.jpg');
        const result = await api('/api/expenses/scan', { method: 'POST', body: fd });
        if (result && result.id) {
            toast('Photo captured & uploaded! Processing...', 'info');
            navigate('#/expenses/' + result.urlId);
        } else {
            statusEl.innerHTML = '<div class="badge badge-failed"><i class="fa-solid fa-xmark"></i> Upload failed</div>';
            toast('Upload failed', 'error');
        }
    }, 'image/jpeg', 0.9);
}

function captureCancel() {
    const overlay = document.getElementById('captureOverlay');
    if (overlay) overlay.remove();
    closeDesktopCamera();
}

// ============================================
// EXPENSE DETAIL
// ============================================
async function renderExpenseDetail(app, id) {
    const data = await api(`/api/expenses/${id}`);
    if (!data || !data.expense) { toast('Expense not found', 'error'); navigate('#/expenses'); return; }
    const e = data.expense;
    const items = data.items || [];
    const store = data.store;
    const isReceiptScan = e.type === 'RECEIPT_SCAN';
    const isCompleted = e.status === 'COMPLETED';
    const isProcessing = e.status === 'PROCESSING';
    const isFailed = e.status === 'FAILED';

    // Load categories for dropdown (always fresh from dedicated endpoint)
    if (!window._allExpenseCategories || window._allExpenseCategories.length === 0) {
        window._allExpenseCategories = (await api('/api/expenses/categories')) || [];
    }

    let html = `<div class="container">
        <div class="action-bar">
            <div class="action-bar-left">
                <h2 style="color:var(--aegean-dark)">Expense Detail</h2>
                <span class="badge badge-${(e.status||'').toLowerCase()}">${statusIcon(e.status)} ${e.status}</span>
            </div>
            <div class="action-bar-right">
                ${isFailed ? `<button class="btn btn-secondary btn-sm" onclick="retryExpense('${e.urlId}'); setTimeout(()=>location.reload(),500)"><i class="fa-solid fa-rotate"></i> Retry</button>` : ''}
                <button class="btn btn-secondary btn-sm" onclick="duplicateExpense('${e.urlId}')"><i class="fa-solid fa-copy"></i> Duplicate</button>
                <button class="btn btn-danger btn-sm" onclick="deleteExpense('${e.urlId}'); navigate('#/expenses')"><i class="fa-solid fa-trash"></i> Delete</button>
            </div>
        </div>`;

    if (isProcessing) {
        html += `<div class="card" style="text-align:center; padding:3rem">
            <i class="fa-solid fa-spinner fa-spin" style="font-size:3rem; color:var(--aegean-mid)"></i>
            <p style="margin-top:1rem; color:var(--text-light)">Processing receipt... This may take 2-3 minutes.</p>
            <button class="btn btn-primary" style="margin-top:1rem" onclick="renderExpenseDetail(document.getElementById('app'),'${e.urlId}')">
                <i class="fa-solid fa-rotate"></i> Refresh
            </button>
        </div></div>`;
        app.innerHTML = html;
        return;
    }

    if (isReceiptScan && isCompleted && e.imagePath) {
        const imgFilename = e.imagePath.replace(/\\/g, '/').split('/').pop();
        const ext = (imgFilename.split('.').pop() || '').toLowerCase();
        const isPdf = ext === 'pdf';
        html += `<div class="side-by-side">
            <div class="card">
                <h3 class="card-title"><i class="fa-solid ${isPdf ? 'fa-file-pdf' : 'fa-image'}"></i> Scanned Receipt</h3>
                ${isPdf ? `
                    <div class="receipt-pdf-container" id="receiptZoomContainer">
                        <canvas class="receipt-pdf" id="receiptPdf" style="width:100%; height:600px; border:0;"></canvas>
                        <div style="text-align:center; margin-top:0.5rem; color:var(--text-light); font-size:0.9rem;">If the PDF does not display, <a href="/api/attachments/receipts/${imgFilename}" target="_blank">open in new tab</a>.</div>
                    </div>

                <script>
                  pdfjsLib.GlobalWorkerOptions.workerSrc =
                    'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.16.105/pdf.worker.min.js';

                  async function renderPDF(url) {
                    const pdf = await pdfjsLib.getDocument(url).promise;
                    const page = await pdf.getPage(1); // page 1

                    const viewport = page.getViewport({ scale: 1.5 });
                    const canvas = document.getElementById('receiptPdf');
                    canvas.width = viewport.width;
                    canvas.height = viewport.height;

                    await page.render({
                      canvasContext: canvas.getContext('2d'),
                      viewport
                    }).promise;
                  }

                  renderPDF('/api/attachments/receipts/${imgFilename}');
                </script>
                ` : `
                    <div class="receipt-zoom-container" id="receiptZoomContainer">
                        <img src="/api/attachments/receipts/${imgFilename}" class="receipt-image" id="receiptImg" alt="Receipt">
                    </div>
                `}
            </div>
            <div class="card">
                <h3 class="card-title"><i class="fa-solid fa-edit"></i> Parsed Data</h3>
                ${expenseForm(e, id)}
            </div>
        </div>`;
    } else {
        html += `<div class="card">
            <h3 class="card-title"><i class="fa-solid fa-edit"></i> Expense Details</h3>
            ${expenseForm(e, id)}
        </div>`;
    }

    // Items section: read-only clickable rows
    const activeItems = items.filter(i => !i.deleted);
    html += `<div class="card">
        <h3 class="card-title"><i class="fa-solid fa-list"></i> Items</h3>
        <div class="items-list" id="itemsList">
            ${activeItems.length ? activeItems.map(i => `
            <div class="item-row" onclick="openItemDialog('${id}','${i.id}','${esc(i.itemName).replace(/'/g,"\\'")}',${i.quantity},${i.unitPrice},${i.totalPrice != null ? i.totalPrice : 0})">
                <div class="item-row-name">${esc(i.itemName)}</div>
                <div class="item-row-detail">
                    <span class="item-row-qty">\u00d7${Number(i.quantity).toFixed(i.quantity % 1 === 0 ? 0 : 2)}</span>
                    <span class="item-row-unit">@ ${Number(i.unitPrice).toFixed(2)}</span>
                    <span class="item-row-total amount-primary">${i.totalPrice != null ? Number(i.totalPrice).toFixed(2) : '-'}</span>
                </div>
            </div>`).join('') : '<p style="color:var(--text-light); text-align:center; padding:0.75rem 0;">No items</p>'}
        </div>
        <div style="margin-top:0.75rem">
            <button class="btn btn-outline btn-sm" onclick="openItemDialog('${id}')"><i class="fa-solid fa-plus"></i> Add Item</button>
        </div>
    </div>`;

    html += renderStoreReadOnly(store, id);

    const attachments = e.attachments || [];
    html += `<div class="card">
        <h3 class="card-title"><i class="fa-solid fa-paperclip"></i> Attachments</h3>
        <ul class="attachment-list" id="attachmentList">
            ${attachments.map(a => {
                const fname = a.replace(/\\/g, '/').split('/').pop();
                return `<li>
                    <a href="/api/attachments/${e.id}/${fname}" target="_blank"><i class="fa-solid fa-file"></i> ${fname}</a>
                    <button class="btn btn-danger btn-sm btn-icon" onclick="removeAttachment('${e.id}','${fname}')"><i class="fa-solid fa-xmark"></i></button>
                </li>`;
            }).join('')}
        </ul>
        <div style="margin-top:0.75rem">
            <input type="file" id="newAttachment" multiple>
            <button class="btn btn-outline btn-sm" onclick="uploadAttachments('${e.id}')"><i class="fa-solid fa-upload"></i> Upload</button>
        </div>
    </div>`;

    // Other Details collapsed section
    html += `<div class="card">
        <div class="expand-toggle" onclick="toggleOtherDetails()">
            <i class="fa-solid fa-chevron-down" id="otherDetailsIcon"></i>
            <span id="otherDetailsLabel">Other details</span>
        </div>
        <div id="otherDetailsSection" style="display:none;">
            <div class="other-details-grid">
                <div class="other-detail-row">
                    <span class="other-detail-label">Type</span>
                    <span class="badge badge-${isReceiptScan ? 'scan' : 'manual'}">
                        <i class="fa-solid fa-${isReceiptScan ? 'camera' : 'pen'}"></i> ${isReceiptScan ? 'Receipt Scan' : 'Manual'}
                    </span>
                </div>
                ${e.scannedAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">Scanned</span>
                    <span>${new Date(e.scannedAt).toLocaleString()}</span>
                </div>` : ''}
                ${e.createdAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">Created</span>
                    <span>${new Date(e.createdAt).toLocaleString()}</span>
                </div>` : ''}
                ${e.updatedAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">Updated</span>
                    <span>${new Date(e.updatedAt).toLocaleString()}</span>
                </div>` : ''}
            </div>
        </div>
    </div>`;

    html += '</div>';
    app.innerHTML = html;

    document.getElementById('expenseEditForm').onsubmit = async (ev) => {
        ev.preventDefault();
        const updates = {
            transactionDatetime: document.getElementById('eDate').value + ':00',
            amount: parseFloat(document.getElementById('eAmount').value),
            currency: document.getElementById('eCurrency').value,
            category: document.getElementById('eCategory').value,
            receiptNumber: document.getElementById('eReceipt').value,
            notes: document.getElementById('eNotes').value,
            tags: window._editTags || []
        };
        const exRate = document.getElementById('eExRate').value;
        if (exRate) updates.exchangeRate = parseFloat(exRate);
        const data = await api(`/api/expenses/${id}`, { method: 'PUT', body: updates });
        if (data && data.error) toast(data.error, 'error');
        else { toast('Expense updated!', 'success'); return; }
        window._allExpenseCategories = null; // refresh category cache
        renderExpenseDetail(app, id);
    };

    window._editTags = [...(e.tags || [])];
    renderTags('eTagsContainer', 'eTagInput', window._editTags);

    // Initialize store location map (read-only with tooltip)
    initStoreReadOnlyMap(store);

    // Initialize receipt image pinch-zoom
    initReceiptZoom();
}

function renderStoreReadOnly(store, expenseId) {
    const name = store?.name || '';
    const countryDisplay = store?.country ? (getCountryName(store.country)) : null;
    const addressParts = [store?.address, store?.city, countryDisplay, store?.postalCode].filter(Boolean);
    const addressStr = addressParts.join(', ') || '—';
    const phone = store?.phoneNumber || '';
    const website = store?.website || '';
    const hasCoords = store?.latitude != null && store?.longitude != null;
    const showMap = hasCoords || name;

    return `<div class="card">
        <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:0.75rem;">
            <h3 class="card-title" style="margin-bottom:0"><i class="fa-solid fa-store"></i> Store</h3>
            <button class="btn btn-outline btn-sm" onclick="openChangeStoreDialog('${expenseId}')">
                <i class="fa-solid fa-pen-to-square"></i> Change Store
            </button>
        </div>
        <div class="store-readonly-layout">
            <div class="store-readonly-details">
                <div class="store-readonly-row">
                    <span class="store-readonly-label"><i class="fa-solid fa-shop"></i> Name</span>
                    <span class="store-readonly-value">${esc(name) || '<span style="color:var(--text-light)">—</span>'}</span>
                </div>
                <div class="store-readonly-row">
                    <span class="store-readonly-label"><i class="fa-solid fa-location-dot"></i> Address</span>
                    <span class="store-readonly-value">${esc(addressStr)}</span>
                </div>
                <div class="store-readonly-row">
                    <span class="store-readonly-label"><i class="fa-solid fa-phone"></i> Phone</span>
                    <span class="store-readonly-value">${phone ? `<a href="tel:${esc(phone)}">${esc(phone)}</a>` : '<span style="color:var(--text-light)">—</span>'}</span>
                </div>
                <div class="store-readonly-row">
                    <span class="store-readonly-label"><i class="fa-solid fa-globe"></i> Website</span>
                    <span class="store-readonly-value">${website ? `<a href="${esc(website)}" target="_blank" rel="noopener">${esc(website)}</a>` : '<span style="color:var(--text-light)">—</span>'}</span>
                </div>
            </div>
            ${showMap ? `<div class="store-readonly-map-wrap"><div id="storeReadOnlyMap"></div></div>` : ''}
        </div>
    </div>`;
}

function initStoreReadOnlyMap(store) {
    const mapEl = document.getElementById('storeReadOnlyMap');
    if (!mapEl) return;

    const lat = store?.latitude || 0;
    const lng = store?.longitude || 0;
    const hasCoords = store?.latitude != null && store?.longitude != null;
    const zoom = hasCoords ? 15 : 2;
    const name = store?.name || 'Store';

    const map = L.map('storeReadOnlyMap', { scrollWheelZoom: false, dragging: true, zoomControl: true }).setView([lat, lng], zoom);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '\u00a9 OpenStreetMap'
    }).addTo(map);

    if (hasCoords) {
        const marker = L.marker([lat, lng]).addTo(map);
        marker.bindTooltip(name, { permanent: true, direction: 'top', offset: [0, -10] });
        marker.bindPopup(`<b>${name}</b>`);
    }
    setTimeout(() => map.invalidateSize(), 300);
}

// ============================================
// CHANGE STORE DIALOG
// ============================================
let _changeStoreMap = null;
let _changeStoreMarker = null;
let _nominatimTimer = null;

async function openChangeStoreDialog(expenseId) {
    // Fetch current store
    const data = await api(`/api/expenses/${expenseId}`);
    const store = data?.store || {};

    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'changeStoreOverlay';
    overlay.onclick = (ev) => { if (ev.target === overlay) closeChangeStoreDialog(); };

    overlay.innerHTML = `
        <div class="change-store-dialog" id="changeStoreDialog">
            <div class="change-store-header">
                <h3 class="item-dialog-title" style="margin-bottom:0"><i class="fa-solid fa-store"></i> Change Store</h3>
                <button class="btn btn-outline btn-sm" onclick="closeChangeStoreDialog()"><i class="fa-solid fa-xmark"></i></button>
            </div>

            <div class="form-group" style="position:relative; margin-top:0.75rem;">
                <label>Search address or place</label>
                <input type="text" class="form-control" id="nominatimSearch" placeholder="e.g. Rivergate Vienna" oninput="debounceNominatim()" autocomplete="off">
                <div class="nominatim-results" id="nominatimResults" style="display:none;"></div>
            </div>
            <p style="font-size:0.78rem; color:var(--text-light); margin-bottom:0.75rem;"><i class="fa-solid fa-circle-info"></i> Search to auto-fill, or edit fields directly.</p>

            <div class="form-group">
                <label>Name</label>
                <input type="text" class="form-control" id="csName" value="${esc(store.name || '')}">
            </div>
            <div class="form-group">
                <label>Address</label>
                <input type="text" class="form-control" id="csAddress" value="${esc(store.address || '')}">
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label>City</label>
                    <input type="text" class="form-control" id="csCity" value="${esc(store.city || '')}">
                </div>
                <div class="form-group">
                    <label>Country Code</label>
                    <input type="text" class="form-control" id="csCountry" value="${esc(store.country || '')}" placeholder="e.g. AT">
                </div>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label>Postal Code</label>
                    <input type="text" class="form-control" id="csPostal" value="${esc(store.postalCode || '')}">
                </div>
                <div class="form-group">
                    <label>Phone</label>
                    <input type="text" class="form-control" id="csPhone" value="${esc(store.phoneNumber || '')}">
                </div>
            </div>
            <div class="form-group">
                <label>Website</label>
                <input type="text" class="form-control" id="csWebsite" value="${esc(store.website || '')}">
            </div>
            <div class="form-group">
                <label>Location <span style="font-weight:normal; color:var(--text-light); font-size:0.8rem;">(drag pin to adjust)</span></label>
                <div id="changeStoreMap" style="height:220px; border-radius:var(--radius); border:1px solid var(--border-color);"></div>
            </div>
            <input type="hidden" id="csLat" value="${store.latitude || ''}">
            <input type="hidden" id="csLng" value="${store.longitude || ''}">
            <div class="item-dialog-actions" style="margin-top:1rem;">
                <button class="btn btn-primary" onclick="saveChangeStore('${expenseId}', '${store.id || ''}')">
                    <i class="fa-solid fa-save"></i> Save
                </button>
                <button class="btn btn-outline" onclick="closeChangeStoreDialog()">
                    <i class="fa-solid fa-xmark"></i> Cancel
                </button>
            </div>
        </div>`;

    document.body.appendChild(overlay);
    document.getElementById('nominatimSearch').focus();

    // Reset nominatim tracking
    window._nominatimPlaceId = null;
    window._nominatimSnapshot = null;

    // Init map inside dialog
    setTimeout(() => initChangeStoreMap(store), 200);
}

function initChangeStoreMap(store) {
    const mapEl = document.getElementById('changeStoreMap');
    if (!mapEl) return;

    if (_changeStoreMap) {
        try { _changeStoreMap.remove(); } catch(e) {}
        _changeStoreMap = null; _changeStoreMarker = null;
    }

    const lat = store?.latitude || 0;
    const lng = store?.longitude || 0;
    const hasCoords = store?.latitude != null && store?.longitude != null;
    const zoom = hasCoords ? 15 : 2;

    _changeStoreMap = L.map('changeStoreMap', { scrollWheelZoom: true }).setView([lat, lng], zoom);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '\u00a9 OpenStreetMap'
    }).addTo(_changeStoreMap);

    _changeStoreMarker = L.marker([lat, lng], { draggable: true }).addTo(_changeStoreMap);
    _changeStoreMarker.on('dragend', function() {
        const pos = _changeStoreMarker.getLatLng();
        const latEl = document.getElementById('csLat');
        const lngEl = document.getElementById('csLng');
        if (latEl) latEl.value = pos.lat.toFixed(6);
        if (lngEl) lngEl.value = pos.lng.toFixed(6);
    });

    setTimeout(() => _changeStoreMap && _changeStoreMap.invalidateSize(), 300);
}

function debounceNominatim() {
    clearTimeout(_nominatimTimer);
    const query = document.getElementById('nominatimSearch')?.value?.trim();
    if (!query || query.length < 3) { document.getElementById('nominatimResults').style.display = 'none'; return; }
    _nominatimTimer = setTimeout(() => searchNominatim(query), 1000);
}

async function searchNominatim(query) {
    const resultsEl = document.getElementById('nominatimResults');
    if (!resultsEl) return;
    resultsEl.style.display = 'block';
    resultsEl.innerHTML = '<div class="nominatim-loading"><i class="fa-solid fa-spinner fa-spin"></i> Searching...</div>';

    try {
        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=5`;
        const res = await fetch(url, { headers: { 'Accept-Language': 'en', 'User-Agent': 'ExpenseTracker/1.0' } });
        const results = await res.json();

        if (!results || results.length === 0) {
            resultsEl.innerHTML = '<div class="nominatim-loading">No results found</div>';
            return;
        }

        resultsEl.innerHTML = results.map((r, i) => `
            <div class="nominatim-result-item" onclick="selectNominatimResult(${i})">
                <div class="nominatim-result-name">${esc(r.display_name || '')}</div>
            </div>`).join('');

        // Store results for selection
        window._nominatimResults = results;
    } catch (err) {
        resultsEl.innerHTML = '<div class="nominatim-loading">Search failed. Check connection.</div>';
    }
}

function selectNominatimResult(index) {
    const results = window._nominatimResults;
    if (!results || !results[index]) return;
    const r = results[index];
    const addr = r.address || {};

    // Populate fields
    const setVal = (id, val) => { const el = document.getElementById(id); if (el && val) el.value = val; };

    // Name: use r.name if present, else named place, else building
    const placeName = r.name || addr.building || addr.place || addr.amenity || addr.shop || '';
    setVal('csName', placeName);

    // Address = road + house_number
    const road = [addr.road, addr.house_number].filter(Boolean).join(' ') || addr.pedestrian || addr.path || '';
    setVal('csAddress', road);

    // City
    const city = addr.city || addr.town || addr.village || addr.municipality || '';
    setVal('csCity', city);

    // Country code (uppercase 2-letter)
    const cc = (addr.country_code || '').toUpperCase();
    setVal('csCountry', cc);

    // Postal code
    setVal('csPostal', addr.postcode || '');

    // Coordinates
    if (r.lat && r.lon) {
        setVal('csLat', parseFloat(r.lat).toFixed(6));
        setVal('csLng', parseFloat(r.lon).toFixed(6));
        // Update map
        const lat = parseFloat(r.lat);
        const lng = parseFloat(r.lon);
        if (_changeStoreMap && _changeStoreMarker) {
            _changeStoreMarker.setLatLng([lat, lng]);
            _changeStoreMap.setView([lat, lng], 16);
        }
    }

    // Hide results
    document.getElementById('nominatimResults').style.display = 'none';
    document.getElementById('nominatimSearch').value = r.display_name || '';

    // Track nominatim selection for sourceId
    window._nominatimPlaceId = r.place_id ? String(r.place_id) : null;
    // Snapshot the address fields as populated by nominatim
    window._nominatimSnapshot = {
        address: document.getElementById('csAddress')?.value || '',
        city: document.getElementById('csCity')?.value || '',
        country: document.getElementById('csCountry')?.value || '',
        postalCode: document.getElementById('csPostal')?.value || ''
    };
    toast('Address populated from search result', 'success');
}

async function saveChangeStore(expenseId, storeId) {
    // Determine if nominatim sourceId should be saved:
    // Only if a nominatim result was selected AND the user didn't modify the address fields
    let sourceId = null;
    if (window._nominatimPlaceId && window._nominatimSnapshot) {
        const snap = window._nominatimSnapshot;
        const currentAddr = document.getElementById('csAddress')?.value || '';
        const currentCity = document.getElementById('csCity')?.value || '';
        const currentCountry = document.getElementById('csCountry')?.value || '';
        const currentPostal = document.getElementById('csPostal')?.value || '';
        if (currentAddr === snap.address && currentCity === snap.city &&
            currentCountry === snap.country && currentPostal === snap.postalCode) {
            sourceId = 'nominatim-' + window._nominatimPlaceId;
        }
    }

    const storeData = {
        id: storeId || null,
        name: document.getElementById('csName')?.value || '',
        address: document.getElementById('csAddress')?.value || '',
        city: document.getElementById('csCity')?.value || '',
        country: document.getElementById('csCountry')?.value || '',
        postalCode: document.getElementById('csPostal')?.value || '',
        phoneNumber: document.getElementById('csPhone')?.value || '',
        website: document.getElementById('csWebsite')?.value || '',
        latitude: parseFloat(document.getElementById('csLat')?.value) || null,
        longitude: parseFloat(document.getElementById('csLng')?.value) || null,
        sourceId: sourceId
    };
    await api(`/api/expenses/${expenseId}/store`, { method: 'PUT', body: storeData });
    toast('Store updated!', 'success');
    closeChangeStoreDialog();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

function closeChangeStoreDialog() {
    if (_changeStoreMap) {
        try { _changeStoreMap.remove(); } catch(e) {}
        _changeStoreMap = null; _changeStoreMarker = null;
    }
    const overlay = document.getElementById('changeStoreOverlay');
    if (overlay) overlay.remove();
}

function initReceiptZoom() {
    const container = document.getElementById('receiptZoomContainer');
    const img = document.getElementById('receiptImg');
    if (!container || !img) return;

    let scale = 1;
    let lastDist = 0;
    let translateX = 0, translateY = 0;
    let lastTouchX = 0, lastTouchY = 0;
    let isPanning = false;

    function applyTransform() {
        img.style.transform = `translate(${translateX}px, ${translateY}px) scale(${scale})`;
    }

    container.addEventListener('touchstart', (e) => {
        if (e.touches.length === 2) {
            lastDist = Math.hypot(
                e.touches[0].clientX - e.touches[1].clientX,
                e.touches[0].clientY - e.touches[1].clientY
            );
            e.preventDefault();
        } else if (e.touches.length === 1 && scale > 1) {
            isPanning = true;
            lastTouchX = e.touches[0].clientX;
            lastTouchY = e.touches[0].clientY;
            e.preventDefault();
        }
    }, { passive: false });

    container.addEventListener('touchmove', (e) => {
        if (e.touches.length === 2) {
            e.preventDefault();
            const dist = Math.hypot(
                e.touches[0].clientX - e.touches[1].clientX,
                e.touches[0].clientY - e.touches[1].clientY
            );
            if (lastDist > 0) {
                scale = Math.max(1, Math.min(5, scale * (dist / lastDist)));
                applyTransform();
            }
            lastDist = dist;
        } else if (e.touches.length === 1 && isPanning && scale > 1) {
            e.preventDefault();
            translateX += e.touches[0].clientX - lastTouchX;
            translateY += e.touches[0].clientY - lastTouchY;
            lastTouchX = e.touches[0].clientX;
            lastTouchY = e.touches[0].clientY;
            applyTransform();
        }
    }, { passive: false });

    container.addEventListener('touchend', (e) => {
        if (e.touches.length < 2) lastDist = 0;
        if (e.touches.length === 0) isPanning = false;
        if (scale <= 1) { scale = 1; translateX = 0; translateY = 0; applyTransform(); }
    });

    // Double-tap to reset
    let lastTap = 0;
    container.addEventListener('touchend', (e) => {
        if (e.touches.length > 0) return;
        const now = Date.now();
        if (now - lastTap < 300) {
            scale = 1; translateX = 0; translateY = 0;
            applyTransform();
        }
        lastTap = now;
    });
}

function toggleOtherDetails() {
    const section = document.getElementById('otherDetailsSection');
    const icon = document.getElementById('otherDetailsIcon');
    const label = document.getElementById('otherDetailsLabel');
    if (section.style.display === 'none') {
        section.style.display = 'block';
        icon.className = 'fa-solid fa-chevron-up';
        label.textContent = 'Hide details';
    } else {
        section.style.display = 'none';
        icon.className = 'fa-solid fa-chevron-down';
        label.textContent = 'Other details';
    }
}

// Item dialog (add / edit+delete)
function openItemDialog(expenseId, itemId, itemName, quantity, unitPrice, totalPrice) {
    const isEdit = !!itemId;
    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'itemDialogOverlay';
    overlay.onclick = (ev) => { if (ev.target === overlay) overlay.remove(); };
    overlay.innerHTML = `
        <div class="item-dialog">
            <h3 class="item-dialog-title">${isEdit ? 'Edit Item' : 'Add Item'}</h3>
            <div class="form-group">
                <label>Item Name</label>
                <input type="text" class="form-control" id="dlgItemName" value="${isEdit ? itemName : ''}" placeholder="Item name">
            </div>
            <div class="form-row" style="display:grid; grid-template-columns:1fr 1fr; gap:0.75rem;">
                <div class="form-group">
                    <label>Quantity</label>
                    <input type="number" step="0.01" class="form-control" id="dlgItemQty" value="${isEdit ? quantity : 1}">
                </div>
                <div class="form-group">
                    <label>Unit Price</label>
                    <input type="number" step="0.01" class="form-control" id="dlgItemPrice" value="${isEdit ? unitPrice : ''}">
                </div>
            </div>
            <div class="item-dialog-actions">
                <button class="btn btn-primary btn-sm" onclick="saveItemDialog('${expenseId}','${itemId || ''}')">
                    <i class="fa-solid fa-save"></i> ${isEdit ? 'Save' : 'Add'}
                </button>
                ${isEdit ? `<button class="btn btn-danger btn-sm" onclick="deleteItemDialog('${expenseId}','${itemId}')">
                    <i class="fa-solid fa-trash"></i> Delete
                </button>` : ''}
                <button class="btn btn-outline btn-sm" onclick="document.getElementById('itemDialogOverlay').remove()">
                    <i class="fa-solid fa-xmark"></i> Cancel
                </button>
            </div>
        </div>`;
    document.body.appendChild(overlay);
    document.getElementById('dlgItemName').focus();
}

async function saveItemDialog(expenseId, itemId) {
    const name = document.getElementById('dlgItemName').value;
    const qty = parseFloat(document.getElementById('dlgItemQty').value);
    const price = parseFloat(document.getElementById('dlgItemPrice').value);
    if (!name) { toast('Item name is required', 'error'); return; }

    const item = { itemName: name, quantity: qty, unitPrice: price };
    if (itemId) {
        await api(`/api/expenses/${expenseId}/items/${itemId}`, { method: 'PUT', body: item });
        toast('Item updated', 'success');
    } else {
        await api(`/api/expenses/${expenseId}/items`, { method: 'POST', body: item });
        toast('Item added', 'success');
    }
    document.getElementById('itemDialogOverlay')?.remove();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

async function deleteItemDialog(expenseId, itemId) {
    if (!confirm('Delete this item?')) return;
    await api(`/api/expenses/${expenseId}/items/${itemId}`, { method: 'DELETE' });
    toast('Item deleted', 'success');
    document.getElementById('itemDialogOverlay')?.remove();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

function expenseForm(e, id) {
    const allCats = window._allExpenseCategories || [];
    return `<form id="expenseEditForm">
        <div class="form-row">
            <div class="form-group"><label>Date & Time</label>
                <input type="datetime-local" class="form-control detail-datetime" id="eDate" value="${e.transactionDatetime ? e.transactionDatetime.substring(0,16) : ''}">
            </div>
            <div class="form-group"><label>Category</label>
                <input type="text" class="form-control" id="eCategory" value="${esc(e.category)}" list="eCategoryList" autocomplete="off">
                <datalist id="eCategoryList">
                    ${allCats.map(c => `<option value="${esc(c)}">`).join('')}
                </datalist>
            </div>
        </div>
        <div class="form-row detail-amount-row">
            <div class="form-group"><label>Amount</label>
                <input type="number" step="0.01" class="form-control" id="eAmount" value="${e.amount||''}">
            </div>
            <div class="form-group"><label>Currency</label>
                <input type="text" class="form-control" id="eCurrency" value="${esc(e.currency)}" list="eCurrencyList" autocomplete="off">
                <datalist id="eCurrencyList">
                    <option value="USD"></option><option value="EUR"></option>
                    <option value="GBP"></option><option value="SGD"></option>
                    <option value="JPY"></option><option value="AUD"></option>
                    <option value="CAD"></option><option value="CHF"></option>
                </datalist>
            </div>
            <div class="form-group"><label>Exchange Rate</label>
                <input type="number" step="0.000001" class="form-control" id="eExRate" value="${e.exchangeRate||''}" placeholder="Auto-fetched">
            </div>
        </div>
        ${e.amountInBase ? `<p class="amount-secondary" style="margin-bottom:1rem"><i class="fa-solid fa-exchange-alt"></i> ${Number(e.amountInBase).toFixed(2)} ${currentUser?.baseCurrency||''}</p>` : ''}
        <div class="form-group"><label>Receipt Number</label>
            <input type="text" class="form-control" id="eReceipt" value="${esc(e.receiptNumber)}">
        </div>
        <div class="form-group"><label>Tags</label>
            <div class="tags-container" id="eTagsContainer">
                <input type="text" class="tag-input" id="eTagInput" placeholder="Add tag...">
            </div>
        </div>
        <div class="form-group"><label>Notes</label>
            <textarea class="form-control" id="eNotes" rows="2">${esc(e.notes)}</textarea>
        </div>
        <button type="submit" class="btn btn-primary"><i class="fa-solid fa-save"></i> Save Changes</button>
    </form>`;
}


async function uploadAttachments(expenseId) {
    const files = document.getElementById('newAttachment').files;
    for (let f of files) {
        const fd = new FormData(); fd.append('file', f);
        await api(`/api/expenses/${expenseId}/attachments`, { method: 'POST', body: fd });
    }
    toast('Attachments uploaded', 'success');
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

async function removeAttachment(expenseId, filename) {
    await api(`/api/expenses/${expenseId}/attachments/${filename}`, { method: 'DELETE' });
    toast('Attachment removed', 'success');
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

