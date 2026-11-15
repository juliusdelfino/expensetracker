/* ============================================
   Expense Tracker - Expense Detail & Receipt View
   ============================================ */

// Auto-refresh polling handle
let _expenseDetailPollTimer = null;
const _expenseDetailUiState = new Map();

function _getExpenseDetailUiStateKey(id, options = {}) {
    const isShared = !!options.shared;
    const resolvedId = isShared ? (options.shareToken || id) : id;
    return `${isShared ? 'shared' : 'owner'}:${resolvedId}`;
}

function _getExpenseDetailUiState(id, options = {}) {
    const key = _getExpenseDetailUiStateKey(id, options);
    if (!_expenseDetailUiState.has(key)) {
        _expenseDetailUiState.set(key, {
            sharePanelVisible: false,
            sharedBannerDismissed: false
        });
    }
    return _expenseDetailUiState.get(key);
}

function resetExpenseDetailTransientUiState() {
    _expenseDetailUiState.clear();
}

function showExpenseShareStatusCard(expenseUrlId) {
    if (!expenseUrlId) return;
    const state = _getExpenseDetailUiState(expenseUrlId);
    state.sharePanelVisible = true;
}

function dismissExpenseShareStatusCard(expenseUrlId) {
    if (!expenseUrlId) return;
    const state = _getExpenseDetailUiState(expenseUrlId);
    state.sharePanelVisible = false;
    document.querySelector('.share-status-card')?.remove();
}

function dismissSharedExpenseBanner(shareToken) {
    if (!shareToken) return;
    const state = _getExpenseDetailUiState(shareToken, { shared: true, shareToken });
    state.sharedBannerDismissed = true;
    document.querySelector('.shared-expense-banner')?.remove();
}

function _stopExpenseDetailPolling() {
    if (_expenseDetailPollTimer) {
        clearInterval(_expenseDetailPollTimer);
        _expenseDetailPollTimer = null;
    }
}

function refreshExpenseDetail(id, shared = false, shareToken = null) {
    const options = shared ? { shared: true, shareToken: shareToken || id } : {};
    return renderExpenseDetail(document.getElementById('app'), id, options);
}

function _startExpenseDetailPolling(app, id, options = {}) {
    _stopExpenseDetailPolling();
    _expenseDetailPollTimer = setInterval(async () => {
        try {
            const data = await loadExpenseDetail(id, options);
            const status = data && data.expense && data.expense.status;
            if (status && status !== 'PROCESSING') {
                _stopExpenseDetailPolling();
                renderExpenseDetail(app, id, options);
            }
        } catch (e) {
            // ignore polling errors
        }
    }, 5000);
}

function getReceiptUrl(imagePath, options = {}) {
    if (!imagePath) return '';
    if (options.shared && options.shareToken) {
        const filename = imagePath.replace(/\\/g, '/').split('/').pop();
        return `/api/share/${options.shareToken}/receipts/${filename}`;
    }
    const normalized = imagePath.replace(/\\/g, '/');
    const match = normalized.match(/(?:^|.*\/)?receipts\/(\d+)\/(\d{4}_\d{2})\/([^/]+)$/);
    if (!match) return '';
    const [, userId, yearMonth, filename] = match;
    return `/api/attachments/receipts/${userId}/${yearMonth}/${filename}`;
}

async function loadExpenseDetail(id, options = {}) {
    const url = options.shared ? `/api/share/${id}/expense` : `/api/expenses/${id}`;
    return api(url, { noAuthRedirect: true });
}

async function loadExpenseShareStatus(expenseUrlId) {
    const status = await api(`/api/expenses/${expenseUrlId}/share`, { noAuthRedirect: true });
    return status && typeof status === 'object' && Object.prototype.hasOwnProperty.call(status, 'active')
        ? status
        : null;
}

async function loadExpenseReports(expenseUrlId) {
    return api(`/api/expenses/${expenseUrlId}/reports`, { noAuthRedirect: true });
}

async function loadSimilarExpenses(expenseUrlId, limit = 12) {
    return api(`/api/expenses/${expenseUrlId}/similar?limit=${limit}`, { noAuthRedirect: true });
}

function getAbsoluteShareUrl(shareUrl) {
    if (!shareUrl) return '';
    if (/^https?:\/\//i.test(shareUrl)) return shareUrl;
    return `${window.location.origin}${shareUrl.startsWith('/') ? '' : '/'}${shareUrl}`;
}

function formatShareDateTime(dateValue) {
    if (!dateValue) return '';
    const date = new Date(dateValue);
    if (Number.isNaN(date.getTime())) return '';
    return formatDateTime(date);
}

function formatShortHumanDateTime(dateValue) {
    if (!dateValue) return '\u2014';
    const date = new Date(dateValue);
    if (Number.isNaN(date.getTime())) return '\u2014';
    return formatDateTime(date);
}

function toDateTimeLocalValue(dateValue) {
    if (!dateValue) return '';
    const date = new Date(dateValue);
    if (Number.isNaN(date.getTime())) return '';
    const pad = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function normalizeWebsiteUrl(url) {
    if (!url) return '';
    return /^https?:\/\//i.test(url) ? url : `https://${url}`;
}

function formatAmountCurrency(value, currency) {
    if (value == null || value === '') return '\u2014';
    return formatCurrency(value, currency);
}

function formatRelativeTimeFromNow(dateValue) {
    if (!dateValue) return '';
    const target = new Date(dateValue);
    if (Number.isNaN(target.getTime())) return '';

    let diffMs = target.getTime() - Date.now();
    const isFuture = diffMs >= 0;
    diffMs = Math.abs(diffMs);

    const rtf = new Intl.RelativeTimeFormat(getActiveLocale(), { numeric: 'auto' });
    if (diffMs >= 24 * 60 * 60 * 1000) {
        const days = Math.max(1, Math.round(diffMs / (24 * 60 * 60 * 1000)));
        return rtf.format(isFuture ? days : -days, 'day');
    }
    if (diffMs >= 60 * 60 * 1000) {
        const hours = Math.max(1, Math.round(diffMs / (60 * 60 * 1000)));
        return rtf.format(isFuture ? hours : -hours, 'hour');
    }
    const minutes = Math.max(1, Math.round(diffMs / (60 * 1000)));
    return rtf.format(isFuture ? minutes : -minutes, 'minute');
}

function renderSharedExpenseBanner(options = {}) {
    if (!options.shared) return '';

    return `<div class="card shared-expense-banner">
        <button type="button" class="share-card-dismiss-btn" onclick="dismissSharedExpenseBanner('${options.shareToken || options.expenseId || ''}')" aria-label="${t('expenseDetail.hideSharedBanner', { defaultValue: 'Hide shared expense banner' })}">
            <i class="fa-solid fa-xmark"></i>
        </button>
        <div class="shared-expense-banner-main">
            <div class="shared-expense-banner-icon"><i class="fa-solid fa-share-nodes"></i></div>
            <div>
                <div class="shared-expense-banner-title">${t('expenseDetail.sharedExpenseLink', { defaultValue: 'Shared expense link' })}</div>
                <div class="shared-expense-banner-text">
                    ${t('expenseDetail.sharedExpenseInfo', { defaultValue: 'You are viewing a shared expense. Anyone with this link can view the expense details and receipt files until the owner revokes access or the link expires.' })}
                </div>
            </div>
        </div>
        ${!currentUser ? `<div class="shared-expense-banner-actions">
            <a href="#/login" class="btn btn-primary btn-sm"><i class="fa-solid fa-right-to-bracket"></i> ${t('expenseDetail.signInTrack', { defaultValue: 'Sign in to track your own expenses' })}</a>
        </div>` : ''}
    </div>`;
}

function getShareTtlSelectId(expenseUrlId) {
    return `shareTtlDays_${expenseUrlId}`;
}

function getShareTtlOptions(defaultTtlDays) {
    const values = [1, 7, defaultTtlDays]
        .map(v => Number(v))
        .filter(v => Number.isFinite(v) && v > 0);
    return [...new Set(values)].sort((a, b) => a - b);
}

function getSelectedShareTtlDays(expenseUrlId) {
    const select = document.getElementById(getShareTtlSelectId(expenseUrlId));
    const value = Number(select?.value);
    return Number.isFinite(value) && value > 0 ? value : null;
}

function renderOwnerSharePanel(expenseUrlId, shareStatus) {
    const defaultTtlDays = Number(appConfig.sharingDefaultTtlDays) || 30;
    const hasNativeShare = typeof navigator.share === 'function';

    if (shareStatus && shareStatus.active && shareStatus.shareUrl) {
        const absoluteUrl = getAbsoluteShareUrl(shareStatus.shareUrl);
        const expiryRelative = formatRelativeTimeFromNow(shareStatus.expiresAt);
        const expiryAbsolute = formatShareDateTime(shareStatus.expiresAt);
        const expiryLabel = t('expenseDetail.expiresLabel', {
            defaultValue: 'Expires {{value}}',
            value: expiryRelative || t('expenseDetail.inDays', { defaultValue: 'in {{count}} days', count: defaultTtlDays })
        });

        return `<div class="card share-status-card share-status-card-active">
            <button type="button" class="share-card-dismiss-btn" onclick="dismissExpenseShareStatusCard('${expenseUrlId}')" aria-label="${t('expenseDetail.hideShareCard', { defaultValue: 'Hide share access card' })}">
                <i class="fa-solid fa-xmark"></i>
            </button>
            <div class="share-status-header-row">
                <div>
                    <h3 class="card-title"><i class="fa-solid fa-share-nodes"></i> ${t('expenseDetail.shareAccess', { defaultValue: 'Share access' })} <span class="share-status-pill share-status-pill-active"><i class="fa-solid fa-check"></i> ${t('expenseDetail.active', { defaultValue: 'Active' })}</span></h3>
                    <p class="share-status-subtitle">${t('expenseDetail.shareAccessSubtitle', { defaultValue: 'Anyone with the link below can open this expense and its receipts without signing in.' })}</p>
                </div>
                
            </div>

            <div class="share-status-link-row">
                <input class="share-status-link-input" type="text" readonly value="${esc(absoluteUrl)}" onclick="this.select()" aria-label="${t('expenseDetail.shareLink', { defaultValue: 'Share link' })}">
                <button class="btn btn-outline btn-sm" onclick="copyExpenseLink('${expenseUrlId}', true)"><i class="fa-solid fa-copy"></i> ${t('expenseDetail.copy', { defaultValue: 'Copy' })}</button>
                ${hasNativeShare ? `<button class="btn btn-primary btn-sm" onclick="nativeShareExpense('${expenseUrlId}', true)"><i class="fa-solid fa-paper-plane"></i> ${t('expenseDetail.share', { defaultValue: 'Share' })}</button>` : ''}
            </div>
            <span class="share-status-meta-subvalue">${esc(expiryLabel)}</span> -- 
            ${expiryAbsolute ? `<span class="share-status-meta-subvalue">${esc(expiryAbsolute)}</span> --` : ''}
            <span class="share-status-meta-subvalue"><a class="share-status-preview-link" href="${esc(absoluteUrl)}" target="_blank" rel="noopener noreferrer">${t('expenseDetail.openSharedView', { defaultValue: 'Open shared view' })}</a></span>


            <div class="share-status-actions">
                <button class="btn btn-danger btn-sm" onclick="revokeExpenseShare('${expenseUrlId}', true)"><i class="fa-solid fa-ban"></i> ${t('expenseDetail.revokeLink', { defaultValue: 'Revoke link' })}</button>
            </div>
        </div>`;
    }

    let statusHint = t('expenseDetail.newShareExpiryHint', {
        defaultValue: 'New share links expire automatically after {{count}} day(s).',
        count: defaultTtlDays
    });
    if (shareStatus && shareStatus.revokedAt) {
        statusHint = t('expenseDetail.lastShareRevoked', {
            defaultValue: 'Your last share link was revoked on {{date}}.',
            date: formatShareDateTime(shareStatus.revokedAt) || t('expenseDetail.previousDate', { defaultValue: 'a previous date' })
        });
    } else if (shareStatus && shareStatus.expiresAt) {
        statusHint = t('expenseDetail.lastShareExpired', {
            defaultValue: 'Your last share link expired on {{date}}.',
            date: formatShareDateTime(shareStatus.expiresAt) || t('expenseDetail.previousDate', { defaultValue: 'a previous date' })
        });
    }

    const ttlSelectId = getShareTtlSelectId(expenseUrlId);
    const ttlOptions = getShareTtlOptions(defaultTtlDays);

    return `<div class="card share-status-card share-status-card-inactive">
        <button type="button" class="share-card-dismiss-btn" onclick="dismissExpenseShareStatusCard('${expenseUrlId}')" aria-label="${t('expenseDetail.hideShareCard', { defaultValue: 'Hide share access card' })}">
            <i class="fa-solid fa-xmark"></i>
        </button>
        <div class="share-status-header-row">
            <div>
                <h3 class="card-title"><i class="fa-solid fa-share-nodes"></i> ${t('expenseDetail.shareAccess', { defaultValue: 'Share access' })} <span class="share-status-pill share-status-pill-inactive"><i class="fa-solid fa-lock"></i> ${t('expenseDetail.notShared', { defaultValue: 'Not shared' })}</span></h3>
                <p class="share-status-subtitle">${t('expenseDetail.createTemporaryLink', { defaultValue: 'Create a temporary link so someone can view this expense and its receipt files.' })}</p>
            </div>
        </div>

        <div class="share-status-empty-state">
            <div class="share-status-empty-copy">
                <p>${esc(statusHint)}</p>
                <div class="share-status-ttl-row">
                    <label class="share-status-ttl-label" for="${ttlSelectId}">${t('expenseDetail.linkExpiry', { defaultValue: 'Link expiry' })}</label>
                    <select id="${ttlSelectId}" class="share-status-ttl-select">
                        ${ttlOptions.map(days => `<option value="${days}" ${days === defaultTtlDays ? 'selected' : ''}>${days === 1 ? t('expenseDetail.hours24', { defaultValue: '24 hours' }) : t('expenseDetail.daysCount', { defaultValue: '{{count}} days', count: days })}</option>`).join('')}
                    </select>
                </div>
            </div>
            <button class="btn btn-primary" onclick="createExpenseShare('${expenseUrlId}', true, getSelectedShareTtlDays('${expenseUrlId}'))"><i class="fa-solid fa-link"></i> ${t('expenseDetail.createShareLink', { defaultValue: 'Create share link' })}</button>
        </div>
    </div>`;
}

function renderReceiptDetailsHeader(expenseId, isOwner, showScanToggle = false, isPdf = false) {
    return `<div class="card-title-row">
        <h3 class="card-title"><i class="fa-solid fa-receipt"></i> ${t('expenseDetail.receiptDetails', { defaultValue: 'Receipt Details' })}</h3>
        <div class="receipt-header-actions">
            ${showScanToggle ? `<button class="btn btn-outline btn-sm receipt-scan-toggle-btn" onclick="toggleScannedReceipt()">
                <i class="fa-solid fa-${isPdf ? 'file-pdf' : 'image'}"></i> ${t('expenseDetail.viewScan', { defaultValue: 'View Scan' })}
            </button>` : ''}
            ${isOwner ? `<div class="action-menu-wrap" id="receiptSectionMenuWrap">
                <button class="btn btn-outline btn-sm btn-icon" onclick="toggleReceiptSectionMenu()" title="${t('expenseDetail.receiptActions', { defaultValue: 'Receipt actions' })}"><i class="fa-solid fa-ellipsis-vertical"></i></button>
                <div class="action-dropdown" id="receiptSectionMenuDropdown" style="display:none;">
                    <button class="action-dropdown-item" onclick="closeReceiptSectionMenu(); openChangeStoreDialog('${expenseId}')"><i class="fa-solid fa-store"></i> ${t('expenseDetail.updateStore', { defaultValue: 'Update Store' })}</button>
                    <button class="action-dropdown-item" onclick="closeReceiptSectionMenu(); openExpenseDetailsDialog('${expenseId}')"><i class="fa-solid fa-pen-to-square"></i> ${t('expenseDetail.updateDetails', { defaultValue: 'Update Details' })}</button>
                    <button class="action-dropdown-item" onclick="closeReceiptSectionMenu(); openItemDialog('${expenseId}')"><i class="fa-solid fa-plus"></i> ${t('expenseDetail.addItem', { defaultValue: 'Add Item' })}</button>
                </div>
            </div>` : ''}
        </div>
    </div>`;
}

function renderExpenseReportsSection(reports) {
    const safe = Array.isArray(reports) ? reports : [];
    return `<div class="card">
        <h3 class="card-title"><i class="fa-solid fa-chart-line"></i> ${t('nav.reports', { defaultValue: 'Reports' })}</h3>
        ${safe.length === 0 ? `<p class="receipt-section-empty">${t('expenseDetail.notInAnyReport', { defaultValue: 'This expense is not included in any report yet.' })}</p>` : `<div class="expense-reports-list">${safe.map(r => `
            <a href="#/reports/${r.id}" class="expense-report-link">
                <span class="expense-report-link-title">${esc(r.title || `${t('chat.reportFallback', { defaultValue: 'Report' })} #${r.id}`)}</span>
                <span class="expense-report-link-meta">${esc(typeof formatGroupBy === 'function' ? formatGroupBy(r.groupBy) : (r.groupBy || t('chat.reportFallback', { defaultValue: 'Report' })))} \u00b7 ${t('chat.expenseCount', { count: Number(r.expenseCount || 0), defaultValue: Number(r.expenseCount || 0) === 1 ? '{{count}} expense' : '{{count}} expenses' })}</span>
            </a>`).join('')}</div>`}
    </div>`;
}

function renderSimilarExpensesSection(similar, options = {}) {
    const safe = Array.isArray(similar) ? similar : [];
    return `<div class="card">
        <h3 class="card-title"><i class="fa-solid fa-clone"></i> ${t('expenseDetail.similarExpenses', { defaultValue: 'Similar Expenses' })}</h3>
        ${safe.length === 0 ? `<p class="receipt-section-empty">${t('expenseDetail.noSimilarExpenses', { defaultValue: 'No similar expenses found.' })}</p>` : `<div class="similar-expenses-row">${safe.map(exp => {
            const href = options.shared ? `#/shared/${exp.urlId || ''}` : `#/expenses/${exp.urlId || ''}`;
            const hasImage = !!exp.imagePath;
            const thumb = hasImage
                ? `<img src="${getReceiptUrl(exp.imagePath, options)}" alt="${t('expenseDetail.receiptPreview', { defaultValue: 'Receipt preview' })}" class="similar-expense-thumb">`
                : `<div class="similar-expense-thumb similar-expense-thumb-placeholder"><i class="fa-solid fa-receipt"></i></div>`;
            return `<a class="similar-expense-card" href="${href}">
                ${thumb}
                <div class="similar-expense-store" title="${esc(exp.storeName || t('reports.storeFallback', { defaultValue: 'Store' }))}">${esc(exp.storeName || t('reports.storeFallback', { defaultValue: 'Store' }))}</div>
                <div class="similar-expense-total">${formatAmountCurrency(exp.amount, exp.currency)}</div>
                <div class="similar-expense-date">${formatShortHumanDateTime(exp.transactionDatetime)}</div>
            </a>`;
        }).join('')}</div>`}
    </div>`;
}

function openDateTimePicker(displayId, pickerId) {
    const picker = document.getElementById(pickerId);
    if (!picker) return;
    if (typeof picker.showPicker === 'function') picker.showPicker();
    else picker.click();
    picker.focus();
}

function syncDisplayDateTimeFromPicker(displayId, pickerId) {
    const display = document.getElementById(displayId);
    const picker = document.getElementById(pickerId);
    if (!display || !picker) return;
    display.value = picker.value ? formatShortHumanDateTime(picker.value) : '';
}

function syncPickerDateTimeFromDisplay(displayId, pickerId) {
    const display = document.getElementById(displayId);
    const picker = document.getElementById(pickerId);
    if (!display || !picker) return;
    const parsed = new Date(display.value);
    if (!Number.isNaN(parsed.getTime())) {
        picker.value = toDateTimeLocalValue(parsed.toISOString());
        display.value = formatShortHumanDateTime(parsed.toISOString());
    }
}

function getDateTimeForApi(displayId, pickerId) {
    const picker = document.getElementById(pickerId);
    if (picker?.value) return `${picker.value}:00`;
    const display = document.getElementById(displayId);
    const parsed = new Date(display?.value || '');
    if (Number.isNaN(parsed.getTime())) return null;
    const pad = n => String(n).padStart(2, '0');
    return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())}T${pad(parsed.getHours())}:${pad(parsed.getMinutes())}:00`;
}

function bindExchangeRateRefreshOnCurrencyChange(currencyInputId, exchangeRateInputId) {
    const currencyInput = document.getElementById(currencyInputId);
    const exchangeRateInput = document.getElementById(exchangeRateInputId);
    if (!currencyInput || !exchangeRateInput) return;

    const initialCurrency = (currencyInput.value || '').trim().toUpperCase();
    const initialRate = (exchangeRateInput.value || '').trim();

    const onCurrencyChanged = () => {
        const currentCurrency = (currencyInput.value || '').trim().toUpperCase();
        if (currentCurrency && currentCurrency !== initialCurrency) {
            exchangeRateInput.value = '';
            exchangeRateInput.placeholder = t('expenseDetail.autoFetchedOnSave', { defaultValue: 'Auto-fetched on save' });
        } else {
            exchangeRateInput.placeholder = t('expenseDetail.autoFetched', { defaultValue: 'Auto-fetched' });
            if (!exchangeRateInput.value && initialRate) exchangeRateInput.value = initialRate;
        }
    };

    currencyInput.removeEventListener('change', onCurrencyChanged);
    currencyInput.addEventListener('change', onCurrencyChanged);
}

function scrollToStoreMapFromReceiptAddress() {
    const section = document.getElementById('otherDetailsSection');
    if (section && section.style.display === 'none') toggleOtherDetails();
    const map = document.getElementById('storeOtherDetailsMap');
    if (!map) return;
    setTimeout(() => {
        map.scrollIntoView({ behavior: 'smooth', block: 'center' });
        map.classList.add('store-map-highlight');
        setTimeout(() => map.classList.remove('store-map-highlight'), 1400);
    }, 120);
}

async function renderExpenseDetail(app, id, options = {}) {
    const resolvedOptions = options.shared && !options.shareToken
        ? { ...options, shareToken: id }
        : options;
    _stopExpenseDetailPolling();
    const data = await loadExpenseDetail(id, resolvedOptions);
    if (!data || !data.expense) {
        const expiredMessage = resolvedOptions.shared
            ? t('expenseDetail.sharedLinkExpired', { defaultValue: 'This link has expired or was revoked.' })
            : t('expenseDetail.notFound', { defaultValue: 'Expense not found.' });
        app.innerHTML = `${!currentUser ? `<div class="shared-header"><a href="/" class="brand"><img src="/images/logo192.png" alt="logo"> ${t('app.name', { defaultValue: 'Expense Tracker' })}</a></div>` : ''}
            <div class="container"><div class="card shared-expense-empty-state">
                <div class="shared-expense-empty-icon"><i class="fa-solid fa-link-slash"></i></div>
                <h2>${resolvedOptions.shared ? t('expenseDetail.sharedLinkUnavailable', { defaultValue: 'Shared link unavailable' }) : t('expenseDetail.notFound', { defaultValue: 'Expense not found' })}</h2>
                <p>${expiredMessage}</p>
                <div class="shared-expense-empty-actions">
                    ${currentUser
                        ? `<a href="#/expenses" class="btn btn-outline"><i class="fa-solid fa-arrow-left"></i> ${t('expenseDetail.backToExpenses', { defaultValue: 'Back to Expenses' })}</a>`
                        : `<a href="#/login" class="btn btn-primary"><i class="fa-solid fa-right-to-bracket"></i> ${t('expenseDetail.signIn', { defaultValue: 'Sign in' })}</a>`}
                    ${!currentUser ? `<a href="/" class="btn btn-outline"><i class="fa-solid fa-house"></i> ${t('expenseDetail.goHome', { defaultValue: 'Go to home page' })}</a>` : ''}
                </div>
            </div></div>`;
        return;
    }
    const e = data.expense;
    const items = data.items || [];
    const store = data.store;
    const isOwner = !!data.isOwner;
    const shareStatus = isOwner && e.urlId ? await loadExpenseShareStatus(e.urlId) : null;
    const expenseDetailUiState = _getExpenseDetailUiState(id, resolvedOptions);
    window._expenseIsOwner = isOwner;

    const expenseType = String(e.type || 'MANUAL').toUpperCase();
    const isReceiptScan = expenseType === 'RECEIPT_SCAN';
    const isChatbot = expenseType === 'CHATBOT';
    const isProcessing = e.status === 'PROCESSING';
    const linkedReports = isOwner && e.urlId ? await loadExpenseReports(e.urlId) : [];
    const similarExpenses = isOwner && e.urlId ? await loadSimilarExpenses(e.urlId, 12) : [];

    if (isOwner && (!window._allExpenseCategories || window._allExpenseCategories.length === 0)) {
        window._allExpenseCategories = (await api('/api/expenses/categories')) || [];
    }

    let html = '';
    // Show branded header for shared (non-owner/non-logged-in) views
    if (!currentUser) {
        html += `<div class="shared-header"><a href="/" class="brand"><img src="/images/logo192.png" alt="logo"> ${t('app.name', { defaultValue: 'Expense Tracker' })}</a></div>`;
    }
    html += `<div class="container">
        <div class="action-bar">
            <div class="action-bar-left">
                <h2 style="color:var(--primary-dark)">${t('expenseDetail.title', { defaultValue: 'Expense Detail' })}</h2>
                <span class="badge badge-${(e.status||'').toLowerCase()}">${statusIcon(e.status)} ${e.status}</span>
            </div>
            <div class="action-bar-right">
                <div class="action-menu-wrap" id="actionMenuWrap">
                    <button class="btn btn-outline btn-sm btn-icon" onclick="toggleActionMenu()" title="${t('profile.trash.actions', { defaultValue: 'Actions' })}"><i class="fa-solid fa-ellipsis-vertical"></i></button>
                    <div class="action-dropdown" id="actionDropdown" style="display:none;">
                        ${isOwner && isReceiptScan && !isProcessing ? `<button class="action-dropdown-item" onclick="toggleActionMenu(); retryExpenseWithConfirm('${e.urlId}', '${e.status}')"><i class="fa-solid fa-rotate"></i> ${t('expenseDetail.retryScan', { defaultValue: 'Retry Scan' })}</button>` : ''}
                        ${isOwner && !isProcessing ? `<button class="action-dropdown-item" onclick="toggleActionMenu(); openShareMenu('${e.urlId}', document.getElementById('actionMenuWrap'))"><i class="fa-solid fa-share-nodes"></i> ${t('expenseDetail.share', { defaultValue: 'Share' })}</button>` : ''}
                        ${isOwner && !isProcessing ? `<button class="action-dropdown-item" onclick="toggleActionMenu(); duplicateExpense('${e.urlId}')"><i class="fa-solid fa-copy"></i> ${t('expenseDetail.duplicate', { defaultValue: 'Duplicate' })}</button>` : ''}
                        ${isOwner ? `<button class="action-dropdown-item action-dropdown-danger" onclick="toggleActionMenu(); deleteExpense('${e.urlId}'); navigate('#/expenses')"><i class="fa-solid fa-trash"></i> ${t('expenseDetail.delete', { defaultValue: 'Delete' })}</button>` : ''}
                    </div>
                </div>
            </div>
        </div>`;

    if (!expenseDetailUiState.sharedBannerDismissed) {
        html += renderSharedExpenseBanner({ ...resolvedOptions, expenseId: id });
    }
    if (isOwner && !isProcessing && expenseDetailUiState.sharePanelVisible) {
        html += renderOwnerSharePanel(e.urlId, shareStatus);
    }

    if (isProcessing && isReceiptScan && e.imagePath) {
        const receiptUrl = getReceiptUrl(e.imagePath, resolvedOptions);
        const imgFilename = e.imagePath.replace(/\\/g, '/').split('/').pop();
        const ext = (imgFilename.split('.').pop() || '').toLowerCase();
        const isPdf = ext === 'pdf';
        html += `<div class="side-by-side">
            <div class="card receipt-details-card">
                ${renderReceiptDetailsHeader(id, isOwner, true, isPdf)}
                <div style="text-align:center; padding:2rem 1rem;">
                    <i class="fa-solid fa-spinner fa-spin" style="font-size:2.5rem; color:var(--primary)"></i>
                    <p style="margin-top:1rem; color:var(--text-light)">${t('expenseDetail.processingReceiptBr', { defaultValue: 'Processing receipt...<br>This may take less than a minute.' })}</p>
                    <div style="display:flex; gap:0.75rem; justify-content:center; flex-wrap:wrap; margin-top:1rem;">
                        <button class="btn btn-primary btn-sm" onclick="refreshExpenseDetail('${id}', ${resolvedOptions.shared ? 'true' : 'false'}, ${resolvedOptions.shareToken ? `'${resolvedOptions.shareToken}'` : 'null'})">
                            <i class="fa-solid fa-rotate"></i> ${t('expenseDetail.refresh', { defaultValue: 'Refresh' })}
                        </button>
                        ${isOwner ? `<button class="btn btn-outline btn-sm" onclick="navigate('#/expenses/new?tab=scan')">
                            <i class="fa-solid fa-camera"></i> ${t('expenseDetail.scanAnother', { defaultValue: 'Scan another receipt' })}
                        </button>` : ''}
                    </div>
                </div>
            </div>
            <div class="card scanned-receipt-card">
                <div class="card-title-row">
                    <h3 class="card-title"><i class="fa-solid ${isPdf ? 'fa-file-pdf' : 'fa-image'}"></i> ${t('expenseDetail.scannedReceipt', { defaultValue: 'Scanned Receipt' })}</h3>
                    <button class="btn btn-outline btn-sm receipt-scan-toggle-btn" onclick="toggleScannedReceipt()">
                        <i class="fa-solid fa-eye-slash"></i> ${t('expenseDetail.hideScan', { defaultValue: 'Hide Scan' })}
                    </button>
                </div>
                ${isPdf ? `
                    <iframe src="/pdfjs-5.6.205-dist/web/viewer.html?file=${encodeURIComponent(receiptUrl)}" style="width:100%; height:600px; border:0;"></iframe>
                ` : `
                    <div class="receipt-zoom-container" id="receiptZoomContainer">
                        <img src="${receiptUrl}" class="receipt-image" id="receiptImg" alt="${t('expenseDetail.receipt', { defaultValue: 'Receipt' })}">
                    </div>
                `}
            </div>
        </div>`;
        html += '</div>';
        app.innerHTML = html;
        initReceiptZoom();
        _startExpenseDetailPolling(app, id, resolvedOptions);
        return;
    }

    if (isProcessing) {
        html += `<div class="card" style="text-align:center; padding:3rem">
            <i class="fa-solid fa-spinner fa-spin" style="font-size:3rem; color:var(--primary)"></i>
            <p style="margin-top:1rem; color:var(--text-light)">${t('expenseDetail.processingReceipt', { defaultValue: 'Processing receipt... This may take less than a minute.' })}</p>
            <div style="display:flex; gap:0.75rem; justify-content:center; flex-wrap:wrap; margin-top:1rem;">
                <button class="btn btn-primary" onclick="refreshExpenseDetail('${id}', ${resolvedOptions.shared ? 'true' : 'false'}, ${resolvedOptions.shareToken ? `'${resolvedOptions.shareToken}'` : 'null'})">
                    <i class="fa-solid fa-rotate"></i> ${t('expenseDetail.refresh', { defaultValue: 'Refresh' })}
                </button>
                ${isOwner ? `<button class="btn btn-outline" onclick="navigate('#/expenses/new?tab=scan')">
                    <i class="fa-solid fa-camera"></i> ${t('expenseDetail.scanAnother', { defaultValue: 'Scan another receipt' })}
                </button>` : ''}
            </div>
        </div></div>`;
        app.innerHTML = html;
        _startExpenseDetailPolling(app, id, resolvedOptions);
        return;
    }

    if (isReceiptScan && !isProcessing && e.imagePath) {
        const receiptUrl = getReceiptUrl(e.imagePath, resolvedOptions);
        const imgFilename = e.imagePath.replace(/\\/g, '/').split('/').pop();
        const ext = (imgFilename.split('.').pop() || '').toLowerCase();
        const isPdf = ext === 'pdf';
        html += `<div class="side-by-side">
            <div class="card receipt-details-card">
                ${renderReceiptDetailsHeader(id, isOwner, true, isPdf)}
                ${renderReceiptView(e, items, store, id, isOwner)}
            </div>
            <div class="card scanned-receipt-card">
                <div class="card-title-row">
                    <h3 class="card-title"><i class="fa-solid ${isPdf ? 'fa-file-pdf' : 'fa-image'}"></i> ${t('expenseDetail.scannedReceipt', { defaultValue: 'Scanned Receipt' })}</h3>
                    <button class="btn btn-outline btn-sm receipt-scan-toggle-btn" onclick="toggleScannedReceipt()">
                        <i class="fa-solid fa-eye-slash"></i> ${t('expenseDetail.hideScan', { defaultValue: 'Hide Scan' })}
                    </button>
                </div>
                ${isPdf ? `
                    <iframe src="/pdfjs-5.6.205-dist/web/viewer.html?file=${encodeURIComponent(receiptUrl)}" style="width:100%; height:600px; border:0;"></iframe>
                ` : `
                    <div class="receipt-zoom-container" id="receiptZoomContainer">
                        <img src="${receiptUrl}" class="receipt-image" id="receiptImg" alt="${t('expenseDetail.receipt', { defaultValue: 'Receipt' })}">
                    </div>
                `}
            </div>
        </div>`;
    } else {
        html += `<div class="card">
            ${renderReceiptDetailsHeader(id, isOwner, false, false)}
            ${renderReceiptView(e, items, store, id, isOwner)}
        </div>`;
    }

    const attachments = e.attachments || [];
    html += `<div class="card">
        <h3 class="card-title"><i class="fa-solid fa-paperclip"></i> ${t('expenseDetail.attachments', { defaultValue: 'Attachments' })}</h3>
        <ul class="attachment-list" id="attachmentList">
            ${attachments.map(a => {
                const fname = a.replace(/\\/g, '/').split('/').pop();
                const attachmentUrl = resolvedOptions.shared && resolvedOptions.shareToken
                    ? `/api/share/${resolvedOptions.shareToken}/receipts/${fname}`
                    : `/api/attachments/${e.urlId}/${fname}`;
                return `<li>
                    <a href="${attachmentUrl}" target="_blank"><i class="fa-solid fa-file"></i> ${fname}</a>
                    ${isOwner ? `<button class="btn btn-danger btn-sm btn-icon" onclick="removeAttachment('${e.id}','${fname}')"><i class="fa-solid fa-xmark"></i></button>` : ''}
                </li>`;
            }).join('')}
        </ul>
        ${isOwner ? `<div style="margin-top:0.75rem">
            <input type="file" id="newAttachment" multiple>
            <button class="btn btn-outline btn-sm" onclick="uploadAttachments('${e.id}')"><i class="fa-solid fa-upload"></i> ${t('expenseDetail.upload', { defaultValue: 'Upload' })}</button>
        </div>` : ''}
    </div>`;

    // Other Details collapsed section
    const storeHasMap = (store?.latitude != null && store?.longitude != null) || store?.name;
    html += `<div class="card">
        <div class="expand-toggle" onclick="toggleOtherDetails()" style="font-size:1.1rem; font-weight:600; color:var(--primary-dark); border-top:none; margin-top:0; padding-bottom:0;">
            <i class="fa-solid fa-chevron-down" id="otherDetailsIcon" style="font-size:0.85rem;"></i>
            <span id="otherDetailsLabel">${t('expenseDetail.otherDetails', { defaultValue: 'Other details' })}</span>
        </div>
        <div id="otherDetailsSection" style="display:none;">
            <div class="other-details-grid">
                <div class="other-detail-row">
                    <span class="other-detail-label">${t('expenseDetail.type', { defaultValue: 'Type' })}</span>
                    <span class="badge badge-${isReceiptScan ? 'scan' : 'manual'}">
                        <i class="fa-solid fa-${isReceiptScan ? 'camera' : (isChatbot ? 'robot' : 'pen')}"></i> ${isReceiptScan ? t('expenseDetail.receiptScan', { defaultValue: 'Receipt Scan' }) : (isChatbot ? t('expenseDetail.chatbot', { defaultValue: 'Chatbot' }) : t('expenseDetail.manual', { defaultValue: 'Manual' }))}
                    </span>
                </div>
                ${e.scannedAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">${t('expenseDetail.scanned', { defaultValue: 'Scanned' })}</span>
                    <span>${formatDateTime(e.scannedAt)}</span>
                </div>` : ''}
                ${e.createdAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">${t('expenseDetail.created', { defaultValue: 'Created' })}</span>
                    <span>${formatDateTime(e.createdAt)}</span>
                </div>` : ''}
                ${e.updatedAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">${t('expenseDetail.updatedLabel', { defaultValue: 'Updated' })}</span>
                    <span>${formatDateTime(e.updatedAt)}</span>
                </div>` : ''}
                ${storeHasMap ? `<div class="other-detail-row">
                    <span class="other-detail-label"><i class="fa-solid fa-map-location-dot" style="color:var(--primary); margin-right:0.25rem;"></i> ${t('expenseDetail.storeLocation', { defaultValue: 'Store location' })}</span>
                </div>
                <div id="storeOtherDetailsMap" style="height:200px; border-radius:var(--radius); margin-top:0.4rem; border:1px solid var(--border-color);"></div>` : ''}
            </div>
        </div>
    </div>`;

    if (isOwner) {
        html += renderExpenseReportsSection(linkedReports);
        html += renderSimilarExpensesSection(similarExpenses, resolvedOptions);
    }

    html += '</div>';
    app.innerHTML = html;

    const editForm = document.getElementById('expenseEditForm');
    if (editForm) {
        bindExchangeRateRefreshOnCurrencyChange('eCurrency', 'eExRate');
        editForm.onsubmit = async (ev) => {
            ev.preventDefault();
            commitPendingTag('eTagInput', window._editTags || []);
            const transactionDatetime = getDateTimeForApi('eDateDisplay', 'eDate');
            const updates = {
                transactionDatetime,
                amount: parseFloat(document.getElementById('eAmount').value),
                currency: document.getElementById('eCurrency').value,
                category: document.getElementById('eCategory').value,
                receiptNumber: document.getElementById('eReceipt').value,
                notes: document.getElementById('eNotes').value,
                tags: window._editTags || []
            };
            const exRate = (document.getElementById('eExRate').value || '').trim();
            if (exRate) updates.exchangeRate = parseFloat(exRate);
            const data = await api(`/api/expenses/${id}`, { method: 'PUT', body: updates });
            if (data && data.error) toast(data.error, 'error');
            else { toast(t('expenseDetail.updated', { defaultValue: 'Expense updated!' }), 'success'); return; }
            window._allExpenseCategories = null;
            renderExpenseDetail(app, id);
        };

        window._editTags = [...(e.tags || [])];
        renderTags('eTagsContainer', 'eTagInput', window._editTags);
    }

    initStoreOtherDetailsMap(store);
    initReceiptZoom();
}

function initStoreOtherDetailsMap(store) {
    const mapEl = document.getElementById('storeOtherDetailsMap');
    if (!mapEl) return;

    if (window._storeOtherDetailsMap) {
        try { window._storeOtherDetailsMap.remove(); } catch(e) {}
        window._storeOtherDetailsMap = null;
    }

    const lat = store?.latitude || 0;
    const lng = store?.longitude || 0;
    const hasCoords = store?.latitude != null && store?.longitude != null;
    const zoom = hasCoords ? 15 : 2;
    const name = store?.name || 'Store';

    window._storeOtherDetailsMap = L.map('storeOtherDetailsMap', { scrollWheelZoom: false, dragging: true, zoomControl: true }).setView([lat, lng], zoom);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '\u00a9 OpenStreetMap'
    }).addTo(window._storeOtherDetailsMap);

    if (hasCoords) {
        const marker = L.marker([lat, lng]).addTo(window._storeOtherDetailsMap);
        marker.bindTooltip(name, { permanent: true, direction: 'top', offset: [0, -10] });
        marker.on('click', function() {
            window.open(`https://www.google.com/maps?q=${lat},${lng}`, '_blank');
        });
    }
    setTimeout(() => window._storeOtherDetailsMap && window._storeOtherDetailsMap.invalidateSize(), 300);
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
        label.textContent = t('expenseDetail.hideDetails', { defaultValue: 'Hide details' });
        setTimeout(() => {
            if (window._storeOtherDetailsMap) window._storeOtherDetailsMap.invalidateSize();
        }, 100);
    } else {
        section.style.display = 'none';
        icon.className = 'fa-solid fa-chevron-down';
        label.textContent = t('expenseDetail.otherDetails', { defaultValue: 'Other details' });
    }
}

// --- Receipt view rendering ---
function renderReceiptView(e, items, store, id, isOwner) {
    const storeName = store?.name || '';
    const addressParts = [store?.address, store?.city, getCountryName(store?.country), store?.postalCode].filter(Boolean);
    const addressStr = addressParts.join(', ');
    const phone = store?.phoneNumber || '';
    const website = store?.website || '';

    const dateTimeStr = formatShortHumanDateTime(e.transactionDatetime);
    const receiptNum = e.receiptNumber || '';

    const activeItems = (items || []).filter(i => !i.deleted);
    const currency = e.currency || '';
    const total = e.amount != null ? formatCurrency(e.amount, currency) : '\u2014';

    const baseCurr = currentUser?.baseCurrency || '';
    let amountInBase = e.amountInBase != null ? Number(e.amountInBase) : null;
    if (amountInBase === null && e.amount != null && e.exchangeRate != null && baseCurr && baseCurr !== currency) {
        amountInBase = Number(e.amount) * Number(e.exchangeRate);
    }
    const showBase = amountInBase != null && baseCurr && baseCurr !== currency;

    let html = `<div class="receipt-paper">`;

    // Store header
    html += `<div class="receipt-store-section">`;
    if (storeName) {
        // Logo: show only if the store has a website and a logo.dev token is configured
        if (website && appConfig.logoDevToken) {
            const domain = getRootDomain(website);
            if (domain) {
                html += `<img id="storeFavicon" src="https://img.logo.dev/${encodeURIComponent(domain)}?token=${encodeURIComponent(appConfig.logoDevToken)}"
                    alt="" class="receipt-store-favicon" style="display:none;"
                    onload="checkFaviconDefault(this)" onerror="this.style.display='none'">`;
            }
        }
        html += `<div class="receipt-store-name">${esc(storeName)}</div>`;
        if (addressStr) html += `<button type="button" class="receipt-store-address receipt-map-link" onclick="scrollToStoreMapFromReceiptAddress()" title="${t('expenseDetail.showStoreMap', { defaultValue: 'Show store location map' })}">${esc(addressStr)}</button>`;
        if (phone || website) {
            html += `<div class="receipt-store-contact">`;
            if (phone) html += `<a href="tel:${esc(phone)}" class="receipt-store-link">${esc(phone)}</a>`;
            if (phone && website) html += ' \u00b7 ';
            if (website) html += `<a href="${esc(normalizeWebsiteUrl(website))}" target="_blank" rel="noopener noreferrer" class="receipt-store-link">${esc(website)}</a>`;
            html += `</div>`;
        }
    } else {
        html += `<div class="receipt-store-placeholder">${t('expenseDetail.noStoreInfo', { defaultValue: 'No store info' })}</div>`;
    }
    html += `</div><div class="receipt-divider"></div>`;

    // Receipt number + date/time (stacked to avoid wrapping conflicts on long values)
    html += `<div class="receipt-meta-section">
        <div class="receipt-meta-line">
            <span>${receiptNum ? '#' + esc(receiptNum) : `<span style="color:var(--text-light);font-style:italic">${t('expenseDetail.noReceiptNumber', { defaultValue: 'No receipt #' })}</span>`}</span>
        </div>
        <div class="receipt-meta-line receipt-meta-line-secondary">
            <span>${dateTimeStr}</span>
        </div>
    </div><div class="receipt-divider"></div>`;

    // Items
    html += `<div class="receipt-items-section">`;
    html += `<div class="receipt-items-count">${t('expenseDetail.itemCount', { count: activeItems.length, defaultValue: activeItems.length === 1 ? '{{count}} item' : '{{count}} items' })}</div>`;
    if (activeItems.length > 0) {
        activeItems.forEach(i => {
            const qty = Number(i.quantity);
            const qtyStr = formatQty(qty);
            const unitPrice = Number(i.unitPrice).toFixed(2);
            const itemTotal = i.totalPrice != null ? Number(i.totalPrice).toFixed(2) : '\u2014';
            const adj = i.adjustment != null ? Number(i.adjustment) : 0;
            const hasAdj = adj !== 0;
            const adjStr = adj > 0 ? `+${adj.toFixed(2)}` : adj.toFixed(2);
            const itemClick = isOwner
                ? `onclick="openItemDialog('${id}','${i.id}','${esc(i.itemName).replace(/'/g, "\\'")}',${i.quantity},${i.unitPrice},${i.adjustment != null ? i.adjustment : 0})" title="${t('expenseDetail.clickToEditItem', { defaultValue: 'Click to edit item' })}"`
                : '';
            html += `<div class="receipt-item-row${isOwner ? ' receipt-clickable' : ''}" ${itemClick}>
                <div class="receipt-item-name">${esc(i.itemName)}</div>
                <div class="receipt-item-detail">
                    <span class="receipt-item-qty">${qtyStr} \u00d7 ${unitPrice}</span>
                    ${hasAdj ? `<span class="receipt-item-adjustment">${adjStr}</span>` : ''}
                    <span class="receipt-item-total">${itemTotal}</span>
                </div>
            </div>`;
        });
    } else if (e.notes) {
        html += `<div class="receipt-notes-in-items">${esc(e.notes)}</div>`;
    } else {
        html += `<div class="receipt-no-items">${t('expenseDetail.noItems', { defaultValue: 'No items' })}</div>`;
    }
    html += `</div>`;

    html += `<div class="receipt-divider receipt-divider-thick"></div>`;

    // Total
    html += `<div class="receipt-total-section">
        <div class="receipt-total-line">
            <span class="receipt-total-label">${t('expenseDetail.total', { defaultValue: 'TOTAL' })}</span>
            <span class="receipt-total-amount">${total}</span>
        </div>
        ${showBase ? `<div class="receipt-base-amount"><i class="fa-solid fa-exchange-alt" style="font-size:0.7rem"></i> ${formatCurrency(amountInBase, baseCurr)}</div>` : ''}
    </div>`;

    // Footer
    const notesShownInItems = activeItems.length === 0 && !!e.notes;
    if (e.category || (e.tags && e.tags.length) || (e.notes && !notesShownInItems)) {
        html += `<div class="receipt-divider"></div>`;
        html += `<div class="receipt-footer-section">`;
        if (e.category) html += `<div class="receipt-footer-line"><span class="receipt-footer-label">${t('profile.trash.category', { defaultValue: 'Category' })}:</span> ${esc(e.category)}</div>`;
        if (e.tags && e.tags.length) html += `<div class="receipt-footer-line"><span class="receipt-footer-label">${t('expenseDetail.tags', { defaultValue: 'Tags' })}:</span> ${e.tags.map(t => esc(t)).join(', ')}</div>`;
        if (e.notes && !notesShownInItems) html += `<div class="receipt-footer-line"><span class="receipt-footer-label">${t('expenseDetail.notes', { defaultValue: 'Notes' })}:</span> ${esc(e.notes)}</div>`;
        html += `</div>`;
    }

    html += `</div>`;
    return html;
}

// --- Expense edit form helper ---
function expenseForm(e, id, isOwner = true) {
    const allCats = window._allExpenseCategories || [];
    const ro = isOwner ? '' : 'readonly disabled';
    return `<form ${isOwner ? 'id="expenseEditForm"' : ''}>
        <div class="form-row">
            <div class="form-group"><label>${t('expenseDetail.dateTime', { defaultValue: 'Date & Time' })}</label>
                <div class="detail-datetime-wrap">
                    <input type="text" class="form-control detail-datetime" id="eDateDisplay" value="${e.transactionDatetime ? esc(formatShortHumanDateTime(e.transactionDatetime)) : ''}" placeholder="${t('expenseDetail.dateTimePlaceholder', { defaultValue: 'e.g. Oct 13, 2026, 3:45 PM' })}" ${ro} onblur="syncPickerDateTimeFromDisplay('eDateDisplay','eDate')">
                    <input type="datetime-local" id="eDate" value="${toDateTimeLocalValue(e.transactionDatetime)}" style="position:absolute; opacity:0; pointer-events:none; width:1px; height:1px;" onchange="syncDisplayDateTimeFromPicker('eDateDisplay','eDate')" ${isOwner ? '' : 'disabled'}>
                    ${isOwner ? `<button type="button" class="btn btn-outline btn-sm" onclick="openDateTimePicker('eDateDisplay','eDate')"><i class="fa-solid fa-calendar-days"></i></button>` : ''}
                </div>
            </div>
            <div class="form-group"><label>${t('profile.trash.category', { defaultValue: 'Category' })}</label>
                <input type="text" class="form-control" id="eCategory" maxlength="50" value="${esc(e.category)}" list="eCategoryList" autocomplete="off" ${ro}>
                <datalist id="eCategoryList">
                    ${allCats.map(c => `<option value="${esc(c)}">`).join('')}
                </datalist>
            </div>
        </div>
        <div class="form-row detail-amount-row">
            <div class="form-group"><label>${t('profile.trash.amount', { defaultValue: 'Amount' })}</label>
                <input type="number" step="0.01" min="0" max="9999999999" class="form-control" id="eAmount" value="${e.amount||''}" ${ro}>
            </div>
            <div class="form-group"><label>${t('auth.baseCurrency', { defaultValue: 'Currency' })}</label>
                <input type="text" class="form-control" id="eCurrency" maxlength="3" value="${esc(e.currency)}" list="eCurrencyList" autocomplete="off" ${ro}>
                <datalist id="eCurrencyList">
                    <option value="USD"></option><option value="EUR"></option>
                    <option value="GBP"></option><option value="SGD"></option>
                    <option value="JPY"></option><option value="AUD"></option>
                    <option value="CAD"></option><option value="CHF"></option>
                </datalist>
            </div>
            <div class="form-group"><label>${t('expenseDetail.exchangeRate', { defaultValue: 'Exchange Rate' })}</label>
                <input type="number" step="0.000001" min="0" max="999999" class="form-control" id="eExRate" value="${e.exchangeRate||''}" placeholder="${t('expenseDetail.autoFetched', { defaultValue: 'Auto-fetched' })}" ${ro}>
            </div>
        </div>
        ${e.amountInBase ? `<p class="amount-secondary" style="margin-bottom:1rem"><i class="fa-solid fa-exchange-alt"></i> ${formatCurrency(e.amountInBase, currentUser?.baseCurrency||'')}</p>` : ''}
        <div class="form-group"><label>${t('expenseDetail.receiptNumber', { defaultValue: 'Receipt Number' })}</label>
            <input type="text" class="form-control" id="eReceipt" maxlength="50" value="${esc(e.receiptNumber)}" ${ro}>
        </div>
        <div class="form-group"><label>${t('expenseDetail.tags', { defaultValue: 'Tags' })}</label>
            <div class="tags-container" id="eTagsContainer">
                <input type="text" class="tag-input" id="eTagInput" placeholder="${t('expenseDetail.addTag', { defaultValue: 'Add tag...' })}" ${ro}>
            </div>
        </div>
        <div class="form-group"><label>${t('expenseDetail.notes', { defaultValue: 'Notes' })}</label>
            <textarea class="form-control" id="eNotes" rows="2" maxlength="500" ${ro}>${esc(e.notes)}</textarea>
        </div>
        ${isOwner ? `<button type="submit" class="btn btn-primary"><i class="fa-solid fa-save"></i> ${t('expenseDetail.saveChanges', { defaultValue: 'Save Changes' })}</button>` : ''}
    </form>`;
}

// --- Attachments ---
async function uploadAttachments(expenseId) {
    const files = document.getElementById('newAttachment').files;
    for (let f of files) {
        const fd = new FormData(); fd.append('file', f);
        await api(`/api/expenses/${expenseId}/attachments`, { method: 'POST', body: fd });
    }
    toast(t('expenseDetail.attachmentsUploaded', { defaultValue: 'Attachments uploaded' }), 'success');
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

async function removeAttachment(expenseId, filename) {
    await api(`/api/expenses/${expenseId}/attachments/${filename}`, { method: 'DELETE' });
    toast(t('expenseDetail.attachmentRemoved', { defaultValue: 'Attachment removed' }), 'success');
    renderExpenseDetail(document.getElementById('app'), expenseId);
}
function toggleScannedReceipt() {
    const scanCard = document.querySelector('.scanned-receipt-card');
    const detailsCard = document.querySelector('.receipt-details-card');
    if (!scanCard || !detailsCard) return;
    const showingScan = scanCard.classList.contains('show');
    if (showingScan) {
        scanCard.classList.remove('show');
        detailsCard.classList.remove('hidden-mobile');
    } else {
        scanCard.classList.add('show');
        detailsCard.classList.add('hidden-mobile');
    }
}

function toggleActionMenu() {
    const dd = document.getElementById('actionDropdown');
    if (!dd) return;
    const visible = dd.style.display !== 'none';
    dd.style.display = visible ? 'none' : 'block';
    if (!visible) {
        setTimeout(() => {
            document.addEventListener('click', function closeMenu(e) {
                if (!document.getElementById('actionMenuWrap')?.contains(e.target)) {
                    const d = document.getElementById('actionDropdown');
                    if (d) d.style.display = 'none';
                    document.removeEventListener('click', closeMenu);
                }
            });
        }, 10);
    }
}

function toggleReceiptSectionMenu() {
    const dd = document.getElementById('receiptSectionMenuDropdown');
    if (!dd) return;
    const visible = dd.style.display !== 'none';
    dd.style.display = visible ? 'none' : 'block';
    if (!visible) {
        setTimeout(() => {
            document.addEventListener('click', function closeMenu(e) {
                if (!document.getElementById('receiptSectionMenuWrap')?.contains(e.target)) {
                    closeReceiptSectionMenu();
                    document.removeEventListener('click', closeMenu);
                }
            });
        }, 10);
    }
}

function closeReceiptSectionMenu() {
    const dd = document.getElementById('receiptSectionMenuDropdown');
    if (dd) dd.style.display = 'none';
}

async function retryExpenseWithConfirm(id, status) {
    if (status !== 'FAILED') {
        const ok = confirm(t('expenseDetail.retryConfirm', { defaultValue: 'This expense has already been processed (status: {{status}}). Retrying will re-scan the receipt and replace the current data. Continue?', status }));
        if (!ok) return;
    }
    await api(`/api/expenses/${id}/retry`, { method: 'POST' });
    toast(t('expenseDetail.retryStarted', { defaultValue: 'Retry initiated. Refreshing in a moment...' }), 'info');
    setTimeout(() => renderExpenseDetail(document.getElementById('app'), id), 1500);
}
