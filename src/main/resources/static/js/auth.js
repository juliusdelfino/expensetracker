/* ============================================
   Expense Tracker - Auth (Login/Register)
   ============================================ */

function renderLogin(app) {
    document.getElementById('navbar').style.display = 'none';
    app.innerHTML = `
    <div class="auth-container">
        <div class="card">
            <div style="text-align:center; margin-bottom:1rem;">
                <img src="/images/logo-large.png" alt="Expense Tracker" style="width:80px;height:80px;border-radius:16px;">
            </div>
            <h2 class="card-title" style="text-align:center;">Expense Tracker</h2>
            <form id="loginForm">
                <div class="form-group">
                    <label><i class="fa-solid fa-user"></i> Username</label>
                    <input type="text" class="form-control" id="loginUsername" maxlength="100" required>
                </div>
                <div class="form-group">
                    <label><i class="fa-solid fa-lock"></i> Password</label>
                    <input type="password" class="form-control" id="loginPassword" required>
                </div>
                <button type="submit" class="btn btn-primary" style="width:100%">
                    <i class="fa-solid fa-right-to-bracket"></i> Login
                </button>
            </form>
            <p class="auth-switch">Don't have an account? <a href="#/register">Register</a></p>
        </div>
    </div>`;
    document.getElementById('loginForm').onsubmit = async (e) => {
        e.preventDefault();
        const data = await api('/api/auth/login', { method: 'POST', body: {
            username: document.getElementById('loginUsername').value,
            password: document.getElementById('loginPassword').value
        }});
        if (data && data.userId) { toast('Welcome back!', 'success'); navigate('#/dashboard'); }
        else toast(data?.error || 'Login failed', 'error');
    };
}

function renderRegister(app) {
    document.getElementById('navbar').style.display = 'none';
    app.innerHTML = `
    <div class="auth-container">
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-user-plus"></i> Register</h2>
            <form id="registerForm">
                <div class="form-group">
                    <label>Username</label>
                    <input type="text" class="form-control" id="regUsername" maxlength="100" required>
                </div>
                <div class="form-group">
                    <label>Password</label>
                    <input type="password" class="form-control" id="regPassword" required>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Email</label>
                        <input type="email" class="form-control" id="regEmail" maxlength="100">
                    </div>
                    <div class="form-group">
                        <label>Phone</label>
                        <input type="text" class="form-control" id="regPhone" maxlength="30" placeholder="+65...">
                    </div>
                </div>
                <div class="form-group">
                    <label>Base Currency</label>
                    <input type="text" class="form-control" id="regCurrency" maxlength="3" list="regCurrencyList" placeholder="e.g. USD">
                    <datalist id="regCurrencyList">
                        <option value="USD"></option><option value="EUR"></option>
                        <option value="GBP"></option><option value="SGD"></option>
                        <option value="JPY"></option><option value="AUD"></option>
                        <option value="CAD"></option><option value="CHF"></option>
                    </datalist>
                </div>
                <div class="form-group" style="margin-top:0.75rem;">
                    <label style="display:flex; align-items:flex-start; gap:0.5rem; font-weight:normal; cursor:pointer;">
                        <input type="checkbox" id="regAgree" required style="margin-top:3px; flex-shrink:0;">
                        <span>I have read and agree to the <a href="#/terms" target="_blank">Terms of Service</a> and <a href="#/privacy" target="_blank">Privacy Policy</a></span>
                    </label>
                </div>
                <button type="submit" class="btn btn-primary" style="width:100%">
                    <i class="fa-solid fa-user-plus"></i> Register
                </button>
            </form>
            <p class="auth-switch">Already have an account? <a href="#/login">Login</a></p>
        </div>
    </div>`;
    document.getElementById('registerForm').onsubmit = async (e) => {
        e.preventDefault();
        if (!document.getElementById('regAgree').checked) {
            toast('You must agree to the Terms of Service and Privacy Policy to register.', 'error');
            return;
        }
        const data = await api('/api/auth/register', { method: 'POST', body: {
            username: document.getElementById('regUsername').value,
            password: document.getElementById('regPassword').value,
            email: document.getElementById('regEmail').value,
            phoneNumber: document.getElementById('regPhone').value,
            baseCurrency: document.getElementById('regCurrency').value
        }});
        if (data && data.userId) { toast('Registration successful! Please login.', 'success'); navigate('#/login'); }
        else toast(data?.error || 'Registration failed', 'error');
    };

    // Populate currency datalist for register form
    populateCurrencyDatalist('regCurrencyList');
}

async function renderProfile(app) {
    const hashParams = new URLSearchParams(window.location.hash.split('?')[1] || '');
    const rawTab = hashParams.get('tab') || 'account';
    // Map old tab IDs to new ones for backward compat
    const tabMap = { profile: 'account', trash: 'data', danger: 'account' };
    const activeTab = tabMap[rawTab] || rawTab;

    const [user, aiModels, aiStatus, summary] = await Promise.all([
        api('/api/auth/me'),
        loadAiModels(),
        loadAiStatus(),
        api('/api/user/account/summary')
    ]);
    if (!user) return;
    const currentTheme = getTheme();
    const selectableModels = (aiModels?.models || []).filter(model => model.supportsChat || model.supportsOcr);
    const selectedAiModel = user.aiModel || '';
    const trashedCount = summary?.trashedExpenseCount ?? 0;

    const tabs = [
        { id: 'account',    label: 'Account',    icon: 'fa-user' },
        { id: 'appearance', label: 'Appearance', icon: 'fa-palette' },
        { id: 'ai-usage',   label: 'AI Usage',   icon: 'fa-robot' },
        { id: 'data',       label: 'Data',        icon: 'fa-download' },
    ];

    app.innerHTML = `
    <div class="account-page container">
        <div class="account-tabs-header">
            ${tabs.map(t => `
                <button class="account-tab-btn${activeTab === t.id ? ' active' : ''}" data-tab="${t.id}">
                    <i class="fa-solid ${t.icon}"></i>
                    <span>${t.label}</span>
                </button>
            `).join('')}
        </div>
        <div class="account-tab-content">
            ${renderAccountTabContent(activeTab, user, selectableModels, selectedAiModel, aiModels, aiStatus, currentTheme, trashedCount)}
        </div>
    </div>`;

    // Tab switching
    document.querySelectorAll('.account-tab-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const tab = btn.dataset.tab;
            history.replaceState(null, '', `#/profile?tab=${tab}`);
            document.querySelectorAll('.account-tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
            const contentEl = document.querySelector('.account-tab-content');
            contentEl.innerHTML = renderAccountTabContent(tab, user, selectableModels, selectedAiModel, aiModels, aiStatus, currentTheme, trashedCount);
            bindAccountTabHandlers(tab, app, user);
            if (tab === 'data') {
                const trashEl = document.getElementById('trashSection');
                if (trashEl) await renderTrashTab(trashEl, app);
            }
        });
    });

    bindAccountTabHandlers(activeTab, app, user);

    if (activeTab === 'data') {
        const trashEl = document.getElementById('trashSection');
        if (trashEl) await renderTrashTab(trashEl, app);
    }
}

function renderAccountTabContent(tab, user, selectableModels, selectedAiModel, aiModels, aiStatus, currentTheme, trashedCount) {
    if (tab === 'account') {
        return `
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-user"></i> Account</h2>
            <form id="profileForm">
                <div class="form-group">
                    <label>Username</label>
                    <input type="text" class="form-control" value="${esc(user.username)}" disabled>
                </div>
                <div class="form-group">
                    <label>Email</label>
                    <input type="email" class="form-control" id="pEmail" value="${esc(user.email)}">
                </div>
                <div class="form-group">
                    <label>Phone Number</label>
                    <input type="text" class="form-control" id="pPhone" value="${esc(user.phoneNumber)}">
                </div>
                <div class="form-row-3">
                    <div class="form-group">
                        <label>Base Currency</label>
                        <input type="text" class="form-control" id="pCurrency" list="pCurrencyList" value="${user.baseCurrency || ''}">
                        <datalist id="pCurrencyList">
                            ${['USD','EUR','GBP','SGD','JPY','AUD','CAD','CHF'].map(c => `<option value="${c}"></option>`).join('')}
                        </datalist>
                    </div>
                    <div class="form-group">
                        <label>Base City</label>
                        <input type="text" class="form-control" id="pBaseCity" value="${esc(user.baseCity)}" placeholder="e.g. Singapore">
                    </div>
                    <div class="form-group">
                        <label>Base Country</label>
                        <input type="text" class="form-control" id="pBaseCountry" value="${esc(user.baseCountry)}" placeholder="e.g. SG">
                    </div>
                </div>
                <div class="form-group">
                    <label>New Password <span style="font-weight:normal;color:var(--text-secondary)">(leave blank to keep current)</span></label>
                    <input type="password" class="form-control" id="pPassword">
                </div>
                <button type="submit" class="btn btn-primary" style="width:100%">
                    <i class="fa-solid fa-save"></i> Save Changes
                </button>
            </form>
        </div>
        <div class="card account-danger-zone" style="margin-top:0;">
            <h2 class="card-title" style="color:var(--danger)"><i class="fa-solid fa-triangle-exclamation"></i> Danger Zone</h2>
            <p style="color:var(--text-secondary); margin-bottom:1.25rem;">
                Destructive account actions. These operations <strong>cannot be undone</strong>.
            </p>
            <div class="account-danger-item">
                <div style="flex:1; min-width:0;">
                    <strong>Delete Account</strong>
                    <p style="margin:0.25rem 0 0.75rem; font-size:0.9rem; color:var(--text-secondary);">
                        Permanently deletes your account, all expenses, receipt images, attachments, and associated data.
                        This action is immediate and irreversible.
                    </p>
                    <div class="form-group" style="margin-bottom:0.5rem;">
                        <label style="font-size:0.85rem;">Current Password</label>
                        <input type="password" id="dangerPassword" class="form-control form-control-sm" placeholder="Enter your password" autocomplete="current-password">
                    </div>
                    <div class="form-group" style="margin-bottom:0.75rem;">
                        <label style="font-size:0.85rem;">Type <code style="color:var(--danger);font-weight:700;">DELETE</code> to confirm</label>
                        <input type="text" id="dangerConfirmation" class="form-control form-control-sm" placeholder="DELETE" autocomplete="off" spellcheck="false">
                    </div>
                    <button id="dangerDeleteBtn" class="btn btn-danger" disabled>
                        <i class="fa-solid fa-user-slash"></i> Permanently Delete My Account
                    </button>
                </div>
            </div>
        </div>`;
    }

    if (tab === 'appearance') {
        return `
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-palette"></i> Appearance</h2>
            <div class="form-group">
                <label>Theme</label>
                <div class="theme-selector">
                    <button class="theme-btn ${currentTheme === 'light' ? 'active' : ''}" onclick="setTheme('light'); updateThemeBtns()">
                        <i class="fa-solid fa-sun"></i> Light
                    </button>
                    <button class="theme-btn ${currentTheme === 'system' ? 'active' : ''}" onclick="setTheme('system'); updateThemeBtns()">
                        <i class="fa-solid fa-circle-half-stroke"></i> System
                    </button>
                    <button class="theme-btn ${currentTheme === 'dark' ? 'active' : ''}" onclick="setTheme('dark'); updateThemeBtns()">
                        <i class="fa-solid fa-moon"></i> Dark
                    </button>
                </div>
            </div>
        </div>`;
    }

    if (tab === 'ai-usage') {
        const hasStatus = !!aiStatus?.chat && !!aiStatus?.ocr;
        const chatExceeded = hasStatus && !aiStatus.chatAllowed;
        const ocrExceeded  = hasStatus && !aiStatus.ocrAllowed;
        return `
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-chart-bar"></i> AI Usage</h2>
            ${hasStatus ? `
            <div class="ai-usage-grid">
                <div class="ai-usage-stat${chatExceeded ? ' ai-usage-exceeded' : ''}">
                    <span class="ai-usage-stat-label"><i class="fa-solid fa-comment"></i> Chat</span>
                    <span class="ai-usage-stat-value">${aiStatus.chat.usageCount} <span class="ai-usage-stat-of">/ ${aiStatus.chat.quota}</span></span>
                    ${chatExceeded ? `<span class="ai-usage-quota-badge">Quota reached</span>` : ''}
                </div>
                <div class="ai-usage-stat${ocrExceeded ? ' ai-usage-exceeded' : ''}">
                    <span class="ai-usage-stat-label"><i class="fa-solid fa-camera"></i> OCR</span>
                    <span class="ai-usage-stat-value">${aiStatus.ocr.usageCount} <span class="ai-usage-stat-of">/ ${aiStatus.ocr.quota}</span></span>
                    ${ocrExceeded ? `<span class="ai-usage-quota-badge">Quota reached</span>` : ''}
                </div>
            </div>
            <div class="form-help" style="margin-top:0.5rem; margin-bottom:1.25rem;">
                Effective model: <strong>${esc(getEffectiveAiModelSummary(aiStatus))}</strong>
                &nbsp;·&nbsp; Usage resets monthly.
            </div>` : `
            <p style="color:var(--text-secondary); margin-bottom:1.25rem;">AI usage information is not available.</p>`}
        </div>
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-robot"></i> AI Model</h2>
            <form id="aiModelForm">
                <div class="form-group">
                    <label>Model Override</label>
                    <select class="form-control" id="pAiModel">
                        <option value="">${esc(getAiDefaultOptionLabel(aiModels))}</option>
                        ${selectableModels.map(model => `
                            <option value="${esc(model.id)}" ${selectedAiModel === model.id ? 'selected' : ''}>${esc(model.label)} (${esc(model.provider)})</option>
                        `).join('')}
                    </select>
                    <div class="form-help">Choose a model for your account, or leave on <strong>Default</strong> to follow the app-wide configuration.</div>
                </div>
                <button type="submit" class="btn btn-primary">
                    <i class="fa-solid fa-save"></i> Save Model
                </button>
            </form>
        </div>`;
    }

    if (tab === 'data') {
        return `
        <div class="card">
            <h2 class="card-title"><i class="fa-solid fa-download"></i> Data &amp; Export</h2>
            <p style="color:var(--text-secondary); margin-bottom:1.5rem;">
                Download your account data. The full archive includes your profile, all active expenses with items, receipt images, and attachments.
            </p>
            <div style="display:flex; flex-direction:column; gap:0.75rem;">
                <a href="/api/user/export" class="btn btn-primary" style="text-align:left;">
                    <i class="fa-solid fa-file-zipper"></i> Download Full Account Archive (ZIP)
                </a>
                <a href="/api/expenses/export?format=json" class="btn btn-secondary" style="text-align:left;">
                    <i class="fa-solid fa-file-code"></i> Download Expenses (JSON)
                </a>
                <a href="/api/expenses/export?format=csv" class="btn btn-secondary" style="text-align:left;">
                    <i class="fa-solid fa-file-csv"></i> Download Expenses (CSV)
                </a>
            </div>
            <p style="margin-top:1rem; font-size:0.85rem; color:var(--text-secondary);">
                ZIP contains: <code>account.json</code>, <code>expenses.json</code> (with items), <code>expenses.csv</code> (with Items column), <code>metadata.json</code>, receipts, and attachments.
                Only active expenses are included.
            </p>
        </div>
        <div id="trashSection">${renderTrashLoading()}</div>`;
    }

    return '';
}

function bindAccountTabHandlers(tab, app, user) {
    if (tab === 'account') {
        const form = document.getElementById('profileForm');
        if (form) {
            form.onsubmit = async (ev) => {
                ev.preventDefault();
                const body = {
                    email: document.getElementById('pEmail').value,
                    phoneNumber: document.getElementById('pPhone').value,
                    baseCurrency: document.getElementById('pCurrency').value,
                    baseCity: document.getElementById('pBaseCity').value,
                    baseCountry: document.getElementById('pBaseCountry').value,
                    password: document.getElementById('pPassword').value
                };
                const result = await api('/api/user/profile', { method: 'PUT', body });
                if (result?.error) { toast(result.error, 'error'); return; }
                toast('Profile updated!', 'success');
                await checkAuth();
                await renderProfile(app);
            };
            populateCurrencyDatalist('pCurrencyList', 'pCurrency', user.baseCurrency);
        }

        // Danger Zone
        const passwordInput = document.getElementById('dangerPassword');
        const confirmInput  = document.getElementById('dangerConfirmation');
        const deleteBtn     = document.getElementById('dangerDeleteBtn');
        if (deleteBtn) {
            function checkReady() {
                deleteBtn.disabled = !(passwordInput.value.length > 0 && confirmInput.value === 'DELETE');
            }
            passwordInput.addEventListener('input', checkReady);
            confirmInput.addEventListener('input', checkReady);
            deleteBtn.addEventListener('click', async () => {
                if (!confirm('This will PERMANENTLY delete your account and ALL associated data. This cannot be undone. Proceed?')) return;
                deleteBtn.disabled = true;
                deleteBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Deleting…';
                const result = await api('/api/user/delete-account', {
                    method: 'POST',
                    body: { password: passwordInput.value, confirmation: confirmInput.value }
                });
                if (result?.error) {
                    toast(result.error, 'error');
                    deleteBtn.disabled = false;
                    deleteBtn.innerHTML = '<i class="fa-solid fa-user-slash"></i> Permanently Delete My Account';
                    return;
                }
                toast('Your account has been permanently deleted.', 'success');
                setTimeout(() => { currentUser = null; navigate('#/login'); }, 1500);
            });
        }
    }

    if (tab === 'ai-usage') {
        const form = document.getElementById('aiModelForm');
        if (form) {
            form.onsubmit = async (ev) => {
                ev.preventDefault();
                const result = await api('/api/user/profile', {
                    method: 'PUT',
                    body: { aiModel: document.getElementById('pAiModel').value }
                });
                if (result?.error) { toast(result.error, 'error'); return; }
                toast('AI model saved!', 'success');
                await Promise.all([loadAiModels(true), loadAiStatus(true)]);
                await renderProfile(app);
            };
        }
    }
}

// =====================================================
// Expense Trash helpers
// =====================================================

function renderTrashLoading() {
    return `<div class="card"><div class="account-empty-state"><i class="fa-solid fa-spinner fa-spin"></i><span>Loading trash…</span></div></div>`;
}

async function renderTrashTab(containerEl, app) {
    // Replace with a fresh clone to prevent event listener accumulation on re-renders
    const fresh = containerEl.cloneNode(false);
    containerEl.replaceWith(fresh);
    containerEl = fresh;

    const items = await api('/api/user/trash/expenses');
    if (!items) { containerEl.innerHTML = renderTrashLoading(); return; }

    const selectedIds = new Set();

    function buildHtml(list) {
        if (list.length === 0) {
            return `<div class="card"><h2 class="card-title"><i class="fa-solid fa-trash-can"></i> Expense Trash</h2>
                <div class="account-empty-state"><i class="fa-solid fa-circle-check"></i><span>Trash is empty</span></div></div>`;
        }
        const rows = list.map(e => {
            const date = e.transactionDatetime ? new Date(e.transactionDatetime).toLocaleDateString() : '—';
            const amt = e.amount != null ? `${e.amount} ${e.currency || ''}` : '—';
            const cat = esc(e.category || 'Uncategorized');
            const icons = [
                e.hasReceipt ? `<i class="fa-solid fa-image" title="Has receipt" style="color:var(--primary)"></i>` : '',
                e.attachmentCount > 0 ? `<i class="fa-solid fa-paperclip" title="${e.attachmentCount} attachment(s)" style="color:var(--primary)"></i>` : ''
            ].filter(Boolean).join(' ');
            return `<tr data-id="${esc(e.expenseId)}">
                <td style="width:36px;text-align:center;"><input type="checkbox" class="trash-select" data-id="${esc(e.expenseId)}"></td>
                <td>${date}</td>
                <td>${cat}</td>
                <td>${amt}</td>
                <td style="text-align:center;">${icons || '—'}</td>
                <td style="white-space:nowrap;">
                    <button class="btn btn-sm btn-outline trash-restore-btn" data-id="${esc(e.expenseId)}" title="Restore">
                        <i class="fa-solid fa-rotate-left"></i> Restore
                    </button>
                    <button class="btn btn-sm btn-danger trash-delete-btn" data-id="${esc(e.expenseId)}" title="Permanently delete" style="margin-left:0.35rem;">
                        <i class="fa-solid fa-trash"></i>
                    </button>
                </td>
            </tr>`;
        }).join('');

        return `<div class="card">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem; flex-wrap:wrap; gap:0.5rem;">
                <h2 class="card-title" style="margin-bottom:0;"><i class="fa-solid fa-trash-can"></i> Expense Trash <span style="font-size:0.85rem;font-weight:normal;color:var(--text-light);">(${list.length})</span></h2>
                <div style="display:flex; gap:0.5rem; flex-wrap:wrap;">
                    <button id="trashSelectAll" class="btn btn-sm btn-outline"><i class="fa-solid fa-check-double"></i> Select All</button>
                    <button id="trashBulkDelete" class="btn btn-sm btn-danger" disabled><i class="fa-solid fa-trash"></i> Delete Selected</button>
                    <button id="trashPurgeAll" class="btn btn-sm btn-danger"><i class="fa-solid fa-fire"></i> Empty Trash</button>
                </div>
            </div>
            <p style="font-size:0.85rem; color:var(--text-secondary); margin-bottom:1rem;">
                Permanently deleting an expense also removes its scanned receipt image and all attachments.
            </p>
            <div class="table-responsive">
                <table class="expense-table">
                    <thead><tr>
                        <th></th><th>Date</th><th>Category</th><th>Amount</th><th>Files</th><th>Actions</th>
                    </tr></thead>
                    <tbody id="trashTableBody">${rows}</tbody>
                </table>
            </div>
        </div>`;
    }

    containerEl.innerHTML = buildHtml(items);

    function refreshBulkBtn() {
        const btn = document.getElementById('trashBulkDelete');
        if (btn) btn.disabled = selectedIds.size === 0;
    }

    async function reloadTrash() {
        await renderTrashTab(containerEl, app);
    }

    // Checkbox selection
    containerEl.addEventListener('change', e => {
        if (e.target.classList.contains('trash-select')) {
            if (e.target.checked) selectedIds.add(e.target.dataset.id);
            else selectedIds.delete(e.target.dataset.id);
            refreshBulkBtn();
        }
    });

    // Select all
    const selectAllBtn = document.getElementById('trashSelectAll');
    if (selectAllBtn) {
        selectAllBtn.addEventListener('click', () => {
            const boxes = containerEl.querySelectorAll('.trash-select');
            const allChecked = [...boxes].every(b => b.checked);
            boxes.forEach(b => {
                b.checked = !allChecked;
                if (b.checked) selectedIds.add(b.dataset.id);
                else selectedIds.delete(b.dataset.id);
            });
            refreshBulkBtn();
        });
    }

    // Restore single
    containerEl.addEventListener('click', async e => {
        const restoreBtn = e.target.closest('.trash-restore-btn');
        if (restoreBtn) {
            const id = restoreBtn.dataset.id;
            restoreBtn.disabled = true;
            const result = await api(`/api/user/trash/expenses/${id}/restore`, { method: 'POST' });
            if (result?.error) { toast(result.error, 'error'); restoreBtn.disabled = false; return; }
            toast('Expense restored', 'success');
            await reloadTrash();
        }
    });

    // Delete single
    containerEl.addEventListener('click', async e => {
        const deleteBtn = e.target.closest('.trash-delete-btn');
        if (deleteBtn) {
            if (!confirm('Permanently delete this expense? This cannot be undone.')) return;
            const id = deleteBtn.dataset.id;
            deleteBtn.disabled = true;
            const result = await api(`/api/user/trash/expenses/${id}`, { method: 'DELETE' });
            if (result?.error) { toast(result.error, 'error'); deleteBtn.disabled = false; return; }
            toast('Expense permanently deleted', 'success');
            await reloadTrash();
        }
    });

    // Bulk delete selected
    const bulkBtn = document.getElementById('trashBulkDelete');
    if (bulkBtn) {
        bulkBtn.addEventListener('click', async () => {
            if (selectedIds.size === 0) return;
            if (!confirm(`Permanently delete ${selectedIds.size} expense(s)? This cannot be undone.`)) return;
            bulkBtn.disabled = true;
            const result = await api('/api/user/trash/expenses/purge', {
                method: 'POST', body: { expenseIds: [...selectedIds] }
            });
            if (result?.error) { toast(result.error, 'error'); return; }
            toast(result.message || 'Expenses deleted', 'success');
            selectedIds.clear();
            await reloadTrash();
        });
    }

    // Empty trash
    const purgeAllBtn = document.getElementById('trashPurgeAll');
    if (purgeAllBtn) {
        purgeAllBtn.addEventListener('click', async () => {
            if (!confirm('Permanently delete ALL trashed expenses? This cannot be undone.')) return;
            purgeAllBtn.disabled = true;
            const result = await api('/api/user/trash/expenses/purge', { method: 'POST', body: {} });
            if (result?.error) { toast(result.error, 'error'); purgeAllBtn.disabled = false; return; }
            toast(result.message || 'Trash emptied', 'success');
            selectedIds.clear();
            await reloadTrash();
        });
    }
}

function updateThemeBtns() {
    const current = getTheme();
    document.querySelectorAll('.theme-btn').forEach(btn => {
        const icon = btn.querySelector('i');
        const isLight = icon.classList.contains('fa-sun');
        const isSystem = icon.classList.contains('fa-circle-half-stroke');
        const isDark = icon.classList.contains('fa-moon');
        btn.classList.toggle('active',
            (current === 'light' && isLight) ||
            (current === 'system' && isSystem) ||
            (current === 'dark' && isDark)
        );
    });
}
