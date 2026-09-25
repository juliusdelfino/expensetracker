/* ============================================
   Expense Tracker - Admin Page
   ============================================ */

let _adminUsersResponse = null;
let _adminUsageOverview = null;
let _adminFilters = {
    search: '',
    role: '',
    quota: '',
    provider: ''
};

async function renderAdminPage(app) {
    if (!isAdminUser()) {
        app.innerHTML = `
        <div class="container">
            <div class="card admin-empty-state">
                <h2 class="card-title"><i class="fa-solid fa-user-shield"></i> Admin</h2>
                <p>You do not have access to this page.</p>
            </div>
        </div>`;
        return;
    }

    app.innerHTML = `
    <div class="container">
        <div class="card admin-empty-state">
            <div class="badge badge-processing"><i class="fa-solid fa-spinner fa-spin"></i> Loading admin data...</div>
        </div>
    </div>`;

    await loadAiModels();
    const [usersResult, overviewResult] = await Promise.all([
        apiResult('/api/admin/users'),
        apiResult('/api/admin/ai/usage')
    ]);

    if (!usersResult.ok) {
        app.innerHTML = `
        <div class="container">
            <div class="card admin-empty-state">
                <h2 class="card-title"><i class="fa-solid fa-user-shield"></i> Admin</h2>
                <p>${esc(usersResult.data?.error || 'Failed to load admin data.')}</p>
            </div>
        </div>`;
        return;
    }

    _adminUsersResponse = usersResult.data;
    _adminUsageOverview = overviewResult.ok ? overviewResult.data : null;

    app.innerHTML = `
    <div class="container">
        <div class="action-bar">
            <div class="action-bar-left">
                <h2 style="color:var(--primary-dark)"><i class="fa-solid fa-user-shield"></i> Admin</h2>
                <div class="form-help" style="margin-top:0.25rem;">Review users, roles, AI model overrides, and monthly AI usage.</div>
            </div>
            <div class="action-bar-right">
                <button class="btn btn-outline btn-sm" onclick="refreshAdminPage()">
                    <i class="fa-solid fa-rotate"></i> Refresh
                </button>
            </div>
        </div>

        ${renderAdminOverviewSection()}

        <div class="card">
            <div class="admin-filter-bar">
                <input type="text" class="form-control form-control-sm" id="adminSearch" placeholder="Search username, email, model..." value="${esc(_adminFilters.search)}" oninput="updateAdminFilters()">
                <select class="form-control form-control-sm" id="adminRoleFilter" onchange="updateAdminFilters()">
                    <option value="">All roles</option>
                    <option value="ADMIN" ${_adminFilters.role === 'ADMIN' ? 'selected' : ''}>Admin</option>
                    <option value="USER" ${_adminFilters.role === 'USER' ? 'selected' : ''}>User</option>
                </select>
                <select class="form-control form-control-sm" id="adminQuotaFilter" onchange="updateAdminFilters()">
                    <option value="">All quota states</option>
                    <option value="ANY_EXCEEDED" ${_adminFilters.quota === 'ANY_EXCEEDED' ? 'selected' : ''}>Any exceeded</option>
                    <option value="CHAT_EXCEEDED" ${_adminFilters.quota === 'CHAT_EXCEEDED' ? 'selected' : ''}>Chat exceeded</option>
                    <option value="OCR_EXCEEDED" ${_adminFilters.quota === 'OCR_EXCEEDED' ? 'selected' : ''}>OCR exceeded</option>
                </select>
                <select class="form-control form-control-sm" id="adminProviderFilter" onchange="updateAdminFilters()">
                    <option value="">All providers</option>
                    <option value="OLLAMA" ${_adminFilters.provider === 'OLLAMA' ? 'selected' : ''}>Ollama</option>
                    <option value="OPENAI" ${_adminFilters.provider === 'OPENAI' ? 'selected' : ''}>OpenAI</option>
                </select>
            </div>
            <div class="table-responsive">
                <table class="admin-users-table">
                    <thead>
                        <tr>
                            <th>User</th>
                            <th>Role</th>
                            <th>Selected Model</th>
                            <th>Effective Models</th>
                            <th>Chat</th>
                            <th>OCR</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody id="adminUsersTableBody"></tbody>
                </table>
            </div>
            <div class="form-help" id="adminUsersSummary" style="margin-top:0.75rem;"></div>
        </div>
    </div>`;

    renderAdminUsersTable();
}

function renderAdminOverviewSection() {
    const overview = _adminUsageOverview;
    if (!overview) {
        return `
        <div class="card admin-empty-state">
            <p>Usage overview is temporarily unavailable.</p>
        </div>`;
    }

    return `
    <div class="admin-summary-grid admin-summary-grid-wide">
        <div class="card admin-summary-card">
            <span class="admin-summary-label">Month</span>
            <strong>${esc(overview.monthYear)}</strong>
            <span class="form-help">Current monthly AI usage window</span>
        </div>
        <div class="card admin-summary-card">
            <span class="admin-summary-label">Users</span>
            <strong>${overview.totalUsers}</strong>
            <span class="form-help">${overview.adminUsers} admin${overview.adminUsers === 1 ? '' : 's'} with access</span>
        </div>
        <div class="card admin-summary-card">
            <span class="admin-summary-label">Chat Usage</span>
            <strong>${overview.totalChatUsage} / ${overview.totalChatQuota}</strong>
            <span class="form-help">${overview.chatNearQuotaUsers || 0} near quota · ${overview.chatExceededUsers} exceeded</span>
        </div>
        <div class="card admin-summary-card">
            <span class="admin-summary-label">OCR Usage</span>
            <strong>${overview.totalOcrUsage} / ${overview.totalOcrQuota}</strong>
            <span class="form-help">${overview.ocrNearQuotaUsers || 0} near quota · ${overview.ocrExceededUsers} exceeded</span>
        </div>
    </div>
    <div class="admin-breakdown-grid">
        ${renderBreakdownCard('Chat routing breakdown', overview.chatByModel, 'chat')}
        ${renderBreakdownCard('OCR routing breakdown', overview.ocrByModel, 'ocr')}
    </div>`;
}

function renderBreakdownCard(title, rows, type) {
    const data = Array.isArray(rows) ? rows : [];
    return `
    <div class="card">
        <div class="card-title"><i class="fa-solid fa-chart-column"></i> ${esc(title)}</div>
        ${data.length === 0 ? '<div class="form-help">No usage recorded for this period yet.</div>' : `
        <div class="table-responsive">
            <table class="admin-breakdown-table">
                <thead>
                    <tr>
                        <th>Model</th>
                        <th>Provider</th>
                        <th>Users</th>
                        <th>${type === 'chat' ? 'Chat' : 'OCR'} Usage</th>
                        <th>Near quota</th>
                        <th>Exceeded</th>
                    </tr>
                </thead>
                <tbody>
                    ${data.map(row => `
                        <tr>
                            <td>
                                <div class="admin-model-cell">
                                    <strong>${esc(row.modelLabel || row.modelId)}</strong>
                                    <span>${esc(row.modelId)}</span>
                                </div>
                            </td>
                            <td>${esc(row.provider)}</td>
                            <td>${row.userCount}</td>
                            <td>${row.usageCount} / ${row.quota}</td>
                            <td>${row.nearQuotaUsers}</td>
                            <td>${row.exceededUsers}</td>
                        </tr>`).join('')}
                </tbody>
            </table>
        </div>`}
    </div>`;
}

function getFilteredAdminUsers() {
    const users = _adminUsersResponse?.users || [];
    return users.filter(user => {
        const search = _adminFilters.search.trim().toLowerCase();
        if (search) {
            const haystack = [
                user.username,
                user.email,
                user.selectedAiModel,
                user.effectiveChatModel,
                user.effectiveOcrModel,
                user.effectiveChatProvider,
                user.effectiveOcrProvider
            ].filter(Boolean).join(' ').toLowerCase();
            if (!haystack.includes(search)) return false;
        }

        if (_adminFilters.role && user.role !== _adminFilters.role) return false;
        if (_adminFilters.provider && !adminUserMatchesProvider(user, _adminFilters.provider)) return false;
        if (_adminFilters.quota === 'ANY_EXCEEDED' && user.chatAllowed && user.ocrAllowed) return false;
        if (_adminFilters.quota === 'CHAT_EXCEEDED' && user.chatAllowed) return false;
        if (_adminFilters.quota === 'OCR_EXCEEDED' && user.ocrAllowed) return false;
        return true;
    });
}

function adminUserMatchesProvider(user, provider) {
    return user.effectiveChatProvider === provider || user.effectiveOcrProvider === provider;
}

function renderAdminUsersTable() {
    const tbody = document.getElementById('adminUsersTableBody');
    const summary = document.getElementById('adminUsersSummary');
    if (!tbody) return;

    const users = getFilteredAdminUsers();
    if (summary) {
        const total = _adminUsersResponse?.users?.length || 0;
        summary.textContent = `Showing ${users.length} of ${total} user${total === 1 ? '' : 's'}.`;
    }

    if (users.length === 0) {
        tbody.innerHTML = `
        <tr>
            <td colspan="7">
                <div class="admin-empty-state admin-inline-empty">
                    <p>No users match the current filters.</p>
                </div>
            </td>
        </tr>`;
        return;
    }

    tbody.innerHTML = users.map(user => `
        <tr ${user.id === currentUser?.id ? 'class="admin-current-user-row"' : ''}>
            <td>
                <div class="admin-user-meta">
                    <strong>${esc(user.username)}</strong>
                    <span>${esc(user.email || 'No email')}</span>
                </div>
            </td>
            <td>
                <span class="pill-badge ${user.role === 'ADMIN' ? 'pill-badge-admin' : ''}">${esc(user.role)}</span>
            </td>
            <td>
                <div class="admin-model-cell">
                    <strong>${esc(user.selectedAiModel ? getAiModelLabel(user.selectedAiModel) : getAiDefaultOptionLabel())}</strong>
                    <span>${user.selectedAiModel ? esc(user.selectedAiModel) : 'Default configuration'}</span>
                </div>
            </td>
            <td>
                <div class="admin-model-cell">
                    <strong>Chat:</strong> <span>${esc(adminModelText(user.effectiveChatModel, user.effectiveChatProvider))}</span>
                    <strong>OCR:</strong> <span>${esc(adminModelText(user.effectiveOcrModel, user.effectiveOcrProvider))}</span>
                </div>
            </td>
            <td><span class="pill-badge ${user.chatAllowed ? '' : 'pill-badge-danger'}">${user.chat.usageCount} / ${user.chat.quota}</span></td>
            <td><span class="pill-badge ${user.ocrAllowed ? '' : 'pill-badge-danger'}">${user.ocr.usageCount} / ${user.ocr.quota}</span></td>
            <td>
                <div class="admin-actions-cell">
                    <div class="admin-inline-form">
                        <label for="adminRole-${user.id}">Role</label>
                        <div class="admin-inline-form-controls">
                            <select class="form-control form-control-sm" id="adminRole-${user.id}">
                                <option value="USER" ${user.role === 'USER' ? 'selected' : ''}>USER</option>
                                <option value="ADMIN" ${user.role === 'ADMIN' ? 'selected' : ''}>ADMIN</option>
                            </select>
                            <button class="btn btn-outline btn-sm" onclick="adminSaveRole(${user.id})">Save</button>
                        </div>
                    </div>
                    <div class="admin-inline-form">
                        <label for="adminAiModel-${user.id}">AI model</label>
                        <div class="admin-inline-form-controls">
                            <select class="form-control form-control-sm" id="adminAiModel-${user.id}">
                                ${buildAdminAiModelOptions(user.selectedAiModel)}
                            </select>
                            <button class="btn btn-outline btn-sm" onclick="adminSaveAiModel(${user.id})">Save</button>
                        </div>
                    </div>
                    <button class="btn btn-secondary btn-sm" onclick="showAdminUserDetails(${user.id})">
                        <i class="fa-solid fa-eye"></i> View
                    </button>
                </div>
            </td>
        </tr>`).join('');
}

function adminModelText(modelId, provider) {
    const label = getAiModelLabel(modelId);
    return `${label} (${provider})`;
}

function buildAdminAiModelOptions(selectedAiModel) {
    const models = availableAiModels?.models || [];
    return [
        `<option value="">${esc(getAiDefaultOptionLabel())}</option>`,
        ...models.map(model => `<option value="${esc(model.id)}" ${selectedAiModel === model.id ? 'selected' : ''}>${esc(model.label)} (${esc(model.provider)})</option>`)
    ].join('');
}

function updateAdminFilters() {
    _adminFilters.search = document.getElementById('adminSearch')?.value || '';
    _adminFilters.role = document.getElementById('adminRoleFilter')?.value || '';
    _adminFilters.quota = document.getElementById('adminQuotaFilter')?.value || '';
    _adminFilters.provider = document.getElementById('adminProviderFilter')?.value || '';
    renderAdminUsersTable();
}

async function refreshAdminPage() {
    await renderAdminPage(document.getElementById('app'));
}

async function adminSaveRole(userId) {
    const value = document.getElementById(`adminRole-${userId}`)?.value;
    const result = await apiResult(`/api/admin/users/${userId}/role`, {
        method: 'PATCH',
        body: { role: value }
    });

    if (!result.ok) {
        toast(result.data?.error || 'Failed to update role.', 'error');
        return;
    }

    toast('User role updated.', 'success');
    await checkAuth();
    if (!isAdminUser()) {
        navigate('#/dashboard');
        return;
    }
    await refreshAdminPage();
}

async function adminSaveAiModel(userId) {
    const value = document.getElementById(`adminAiModel-${userId}`)?.value || '';
    const result = await apiResult(`/api/admin/users/${userId}/ai-model`, {
        method: 'PATCH',
        body: { aiModel: value }
    });

    if (!result.ok) {
        toast(result.data?.error || 'Failed to update AI model.', 'error');
        return;
    }

    toast('AI model override updated.', 'success');
    await checkAuth();
    if (currentUser?.id === userId) {
        await loadAiStatus(true);
    }
    if (!isAdminUser()) {
        navigate('#/dashboard');
        return;
    }
    await refreshAdminPage();
}

async function showAdminUserDetails(userId) {
    const result = await apiResult(`/api/admin/users/${userId}`);
    if (!result.ok) {
        toast(result.data?.error || 'Failed to load user details.', 'error');
        return;
    }

    const user = result.data;
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.id = 'adminUserDetailsModal';
    overlay.innerHTML = `
        <div class="modal-content admin-user-modal" style="max-width:720px;">
            <div class="modal-header">
                <h3><i class="fa-solid fa-user-shield"></i> ${esc(user.username)}</h3>
                <button class="modal-close" onclick="closeAdminUserDetailsModal()">&times;</button>
            </div>
            <div class="admin-detail-grid">
                <div class="admin-detail-card">
                    <span class="admin-summary-label">Role</span>
                    <strong>${esc(user.role)}</strong>
                    <span class="form-help">${esc(user.email || 'No email configured')}</span>
                </div>
                <div class="admin-detail-card">
                    <span class="admin-summary-label">Selected model</span>
                    <strong>${esc(user.selectedAiModel ? getAiModelLabel(user.selectedAiModel) : getAiDefaultOptionLabel())}</strong>
                    <span class="form-help">${user.selectedAiModel ? esc(user.selectedAiModel) : 'Following default configuration'}</span>
                </div>
                <div class="admin-detail-card">
                    <span class="admin-summary-label">Chat usage</span>
                    <strong>${user.chat.usageCount} / ${user.chat.quota}</strong>
                    <span class="form-help">${user.chatAllowed ? `${user.chat.remaining} remaining this month` : 'Quota exceeded'}</span>
                </div>
                <div class="admin-detail-card">
                    <span class="admin-summary-label">OCR usage</span>
                    <strong>${user.ocr.usageCount} / ${user.ocr.quota}</strong>
                    <span class="form-help">${user.ocrAllowed ? `${user.ocr.remaining} remaining this month` : 'Quota exceeded'}</span>
                </div>
            </div>
            <div class="profile-ai-meta" style="margin-top:1rem;">
                <span class="profile-ai-meta-title">Effective AI routing</span>
                <div class="form-help" style="margin-top:0;">
                    Chat: <strong>${esc(adminModelText(user.effectiveChatModel, user.effectiveChatProvider))}</strong><br>
                    OCR: <strong>${esc(adminModelText(user.effectiveOcrModel, user.effectiveOcrProvider))}</strong>
                </div>
            </div>
        </div>`;
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) closeAdminUserDetailsModal();
    });
    document.body.appendChild(overlay);
    _registerEscHandler('adminUserDetailsModal', closeAdminUserDetailsModal);
}

function closeAdminUserDetailsModal() {
    _unregisterEscHandler('adminUserDetailsModal');
    document.getElementById('adminUserDetailsModal')?.remove();
}


