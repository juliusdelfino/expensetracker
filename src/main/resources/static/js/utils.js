/* ============================================
   Expense Tracker - Utility Functions
   ============================================ */

let currentUser = null;
let chartInstances = {};
let appConfig = {}; // populated by loadAppConfig() on startup
let currentAiStatus = null;
let availableAiModels = null;
let _aiStatusRequest = null;
let _aiModelsRequest = null;

// ---- ESC key handler registry for dialogs ----
const _escHandlers = {}; // overlayId -> handler function
document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    // Fire the most recently registered handler
    const ids = Object.keys(_escHandlers);
    if (ids.length === 0) return;
    const lastId = ids[ids.length - 1];
    e.stopPropagation();
    _escHandlers[lastId]();
});
function _registerEscHandler(overlayId, fn) {
    _escHandlers[overlayId] = fn;
}
function _unregisterEscHandler(overlayId) {
    delete _escHandlers[overlayId];
}

async function loadAppConfig() {
    try {
        const cfg = await api('/api/config', { noAuthRedirect: true });
        if (cfg) Object.assign(appConfig, cfg);
    } catch { /* keep defaults */ }
}

function clearAiClientState() {
    currentAiStatus = null;
    availableAiModels = null;
    _aiStatusRequest = null;
    _aiModelsRequest = null;
    renderNavigationState();
    syncAiQuotaState();
}

function isAdminUser(user = currentUser) {
    return user?.role === 'ADMIN';
}

function renderNavigationState() {
    const navAdminLink = document.getElementById('navAdminLink');
    const drawerAdminLink = document.getElementById('drawerAdminLink');
    const usernameEl = document.getElementById('nav-username');

    if (usernameEl) usernameEl.textContent = currentUser?.username || '';
    if (navAdminLink) navAdminLink.style.display = isAdminUser() ? 'flex' : 'none';
    if (drawerAdminLink) drawerAdminLink.style.display = isAdminUser() ? 'flex' : 'none';
}

function getAiModelOption(modelId) {
    if (!availableAiModels?.models || !modelId) return null;
    return availableAiModels.models.find(model => model.id === modelId) || null;
}

function getAiModelLabel(modelId) {
    const model = getAiModelOption(modelId);
    return model?.label || modelId || 'Default';
}

function getAiDefaultOptionLabel(modelsResponse = availableAiModels) {
    if (!modelsResponse) return 'Default (configured by admin)';
    const chatLabel = getAiModelLabel(modelsResponse.defaultChatModel);
    const ocrLabel = getAiModelLabel(modelsResponse.defaultOcrModel);
    if (modelsResponse.defaultChatModel && modelsResponse.defaultChatModel === modelsResponse.defaultOcrModel) {
        return `Default (${chatLabel})`;
    }
    return `Default (Chat: ${chatLabel} • OCR: ${ocrLabel})`;
}

function getEffectiveAiModelSummary(status = currentAiStatus) {
    if (!status) return 'AI status unavailable';
    if (status.effectiveChatModel && status.effectiveChatModel === status.effectiveOcrModel) {
        return getAiModelLabel(status.effectiveChatModel);
    }
    return `Chat: ${getAiModelLabel(status.effectiveChatModel)} • OCR: ${getAiModelLabel(status.effectiveOcrModel)}`;
}

function syncAiQuotaState() {
    if (typeof applyChatQuotaState === 'function') applyChatQuotaState();
    if (typeof applyOcrQuotaState === 'function') applyOcrQuotaState();
}

async function loadAiStatus(force = false) {
    if (!currentUser) {
        clearAiClientState();
        return null;
    }
    if (!force && currentAiStatus) {
        syncAiQuotaState();
        return currentAiStatus;
    }
    if (!force && _aiStatusRequest) return _aiStatusRequest;

    _aiStatusRequest = (async () => {
        const result = await apiResult('/api/user/ai/status', { noAuthRedirect: true });
        currentAiStatus = result.ok ? result.data : null;
        syncAiQuotaState();
        return currentAiStatus;
    })();

    try {
        return await _aiStatusRequest;
    } finally {
        _aiStatusRequest = null;
    }
}

async function loadAiModels(force = false) {
    if (!currentUser) {
        availableAiModels = null;
        _aiModelsRequest = null;
        return null;
    }
    if (!force && availableAiModels) return availableAiModels;
    if (!force && _aiModelsRequest) return _aiModelsRequest;

    _aiModelsRequest = (async () => {
        const result = await apiResult('/api/user/ai/models', { noAuthRedirect: true });
        availableAiModels = result.ok ? result.data : null;
        syncAiQuotaState();
        return availableAiModels;
    })();

    try {
        return await _aiModelsRequest;
    } finally {
        _aiModelsRequest = null;
    }
}

/**
 * Format a quantity value: whole numbers show no decimals,
 * fractional values show up to 4 significant decimal places (trailing zeros stripped).
 */
function formatQty(value) {
    const n = Number(value);
    if (n % 1 === 0) return n.toFixed(0);
    // Up to 4 decimal places, strip trailing zeros
    return parseFloat(n.toFixed(4)).toString();
}

// --- Theme management ---
function applyTheme(theme) {
    // theme: 'light' | 'dark' | 'system'
    const root = document.documentElement;
    if (theme === 'dark') {
        root.setAttribute('data-theme', 'dark');
    } else if (theme === 'light') {
        root.setAttribute('data-theme', 'light');
    } else {
        // system: remove attribute, let @media handle it
        root.removeAttribute('data-theme');
    }
}

function setTheme(theme) {
    localStorage.setItem('theme', theme);
    applyTheme(theme);
}

function getTheme() {
    return localStorage.getItem('theme') || 'system';
}

// Apply saved theme on page load
applyTheme(getTheme());

// --- Responsive detection ---
function isMobile() { return window.innerWidth < 900; }

// --- API Helper ---
async function api(url, options = {}) {
    const result = await apiResult(url, options);
    return result.data;
}

async function apiResult(url, options = {}) {
    const { noAuthRedirect, ...fetchOptions } = options;
    const defaults = { headers: { 'Content-Type': 'application/json' }, credentials: 'include' };
    if (fetchOptions.body && !(fetchOptions.body instanceof FormData)) {
        fetchOptions.body = JSON.stringify(fetchOptions.body);
    } else if (fetchOptions.body instanceof FormData) {
        delete defaults.headers['Content-Type'];
    }
    const res = await fetch(url, { ...defaults, ...fetchOptions });
    if (res.status === 401 && !noAuthRedirect) {
        currentUser = null;
        clearAiClientState();
        navigate('#/login');
        return { ok: false, status: 401, data: null };
    }
    const text = await res.text();
    let data;
    try { data = JSON.parse(text); } catch { data = text; }
    return { ok: res.ok, status: res.status, data };
}

// --- Toast ---
function toast(msg, type = 'info') {
    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.innerHTML = `<i class="fa-solid fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'triangle-exclamation' : 'info-circle'}"></i> ${msg}`;
    document.getElementById('toasts').appendChild(el);
    setTimeout(() => el.remove(), 3500);
}

// --- Nav Drawer ---
function toggleNavDrawer() {
    const drawer = document.getElementById('navDrawer');
    drawer.classList.toggle('open');
}
function closeNavDrawer() {
    document.getElementById('navDrawer').classList.remove('open');
}
// Close drawer on outside click
document.addEventListener('click', (e) => {
    const drawer = document.getElementById('navDrawer');
    const burger = document.getElementById('burgerBtn');
    if (drawer && drawer.classList.contains('open') && !drawer.contains(e.target) && !burger.contains(e.target)) {
        closeNavDrawer();
    }
});

// --- Helper functions ---
function categoryIcon(cat) {
    if (!cat) return 'receipt';
    const c = cat.toLowerCase();
    if (c.includes('food') || c.includes('meal') || c.includes('lunch') || c.includes('dinner') || c.includes('breakfast') || c.includes('restaurant')) return 'utensils';
    if (c.includes('transport') || c.includes('taxi') || c.includes('uber') || c.includes('grab') || c.includes('bus') || c.includes('train')) return 'car';
    if (c.includes('grocery') || c.includes('supermarket')) return 'cart-shopping';
    if (c.includes('coffee') || c.includes('cafe')) return 'mug-hot';
    if (c.includes('entertainment') || c.includes('movie') || c.includes('game')) return 'film';
    if (c.includes('health') || c.includes('medical') || c.includes('pharmacy')) return 'heart-pulse';
    if (c.includes('shopping') || c.includes('clothing') || c.includes('clothes')) return 'bag-shopping';
    if (c.includes('bill') || c.includes('utility') || c.includes('electric') || c.includes('water')) return 'file-invoice-dollar';
    if (c.includes('travel') || c.includes('hotel') || c.includes('flight')) return 'plane';
    return 'receipt';
}

function statusIcon(status) {
    switch(status) {
        case 'PROCESSING': return '<i class="fa-solid fa-spinner fa-spin"></i>';
        case 'COMPLETED': return '<i class="fa-solid fa-check"></i>';
        case 'FAILED': return '<i class="fa-solid fa-xmark"></i>';
        default: return '';
    }
}

function truncate(str, len) { return str ? (str.length > len ? str.substring(0, len) + '...' : str) : '-'; }

function esc(val) { return val ? String(val).replace(/"/g, '&quot;').replace(/</g, '&lt;') : ''; }

// Country code -> name cache (populated from dashboard geoByCountry on first load)
window._countryCodeToName = window._countryCodeToName || {};

function cacheCountryNames(geoByCountry) {
    (geoByCountry || []).forEach(c => {
        if (c.country && c.countryName) window._countryCodeToName[c.country.toUpperCase()] = c.countryName;
    });
}

function getCountryName(code) {
    if (!code) return '';
    return window._countryCodeToName[code.toUpperCase()] || code;
}

/**
 * Convert a 2-letter ISO country code to a flag emoji.
 * e.g. "US" → "🇺🇸", "JP" → "🇯🇵"
 */
function countryCodeToFlag(code) {
    if (!code || code.length !== 2) return '';
    const upper = code.toUpperCase();
    const offset = 0x1F1E6 - 65; // Regional Indicator Symbol Letter A - 'A' char code
    return String.fromCodePoint(upper.charCodeAt(0) + offset, upper.charCodeAt(1) + offset);
}

/**
 * Commit any text currently typed in a tag input (without requiring Enter).
 * Call this before reading the tags array on form submit.
 * Returns the (possibly updated) tags array.
 */
function commitPendingTag(inputId, tags) {
    const input = document.getElementById(inputId);
    if (!input) return tags;
    const val = input.value.trim();
    if (val && !tags.includes(val)) {
        tags.push(val);
        input.value = '';
    }
    return tags;
}

function renderTags(containerId, inputId, tags) {
    // Store the array reference so the remove-onclick can access it
    window['_tagsArr_' + containerId] = tags;
    const container = document.getElementById(containerId);
    container.innerHTML = tags.map((t, i) =>
        `<span class="tag">${esc(t)} <span class="remove-tag" onclick="window['_tagsArr_${containerId}'].splice(${i},1); renderTags('${containerId}','${inputId}',window['_tagsArr_${containerId}'])">&times;</span></span>`
    ).join('') + `<input type="text" class="tag-input" id="${inputId}" placeholder="Add tag...">`;
    document.getElementById(inputId).addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const val = e.target.value.trim();
            if (val && !tags.includes(val)) { tags.push(val); renderTags(containerId, inputId, tags); }
            e.target.value = '';
        }
    });
}

/**
 * Populate a currency <datalist> from the server.
 * @param {string} datalistId  - the id of the <datalist> element
 * @param {string} [inputId]   - optional: input id whose value should be preserved
 * @param {string} [fallback]  - optional: fallback value to set if input is empty
 */
async function populateCurrencyDatalist(datalistId, inputId, fallback) {
    try {
        const map = await api('/api/currencies');
        if (map) {
            const codes = Object.keys(map).sort();
            const dl = document.getElementById(datalistId);
            if (dl) dl.innerHTML = codes.map(c => `<option value="${c}"></option>`).join('');
            if (inputId && fallback) {
                const inp = document.getElementById(inputId);
                if (inp && !inp.value) inp.value = fallback;
            }
        }
    } catch (err) { /* ignore */ }
}

/**
 * Extract the root domain from a URL string, stripping subdomains and query strings.
 * e.g. "https://shop.apple.com/path?foo=1" → "apple.com"
 */
function getRootDomain(websiteStr) {
    try {
        const url = new URL(websiteStr.startsWith('http') ? websiteStr : 'https://' + websiteStr);
        const parts = url.hostname.split('.');
        // Keep last two parts for most domains (e.g. apple.com), three for co.uk-style
        if (parts.length > 2) {
            const twoLD = parts.slice(-2).join('.');
            const knownSecondLevel = ['co.uk','com.au','co.nz','co.jp','co.in','com.br','com.sg'];
            if (knownSecondLevel.includes(twoLD)) {
                return parts.slice(-3).join('.');
            }
        }
        return parts.slice(-2).join('.');
    } catch {
        return null;
    }
}

/**
 * Called after a logo.dev image loads.
 * Hides the image on error (onerror handles network failures;
 * this handles the case where logo.dev returns a fallback placeholder).
 * logo.dev returns a proper 404 / transparent image for unknown domains,
 * so we simply show on load and hide on error.
 */
function checkFaviconDefault(img) {
    // logo.dev returns a styled placeholder for unknown domains — just show it.
    // If the image truly failed to load, onerror already hides it.
    img.style.display = 'block';
}

const _legalPageStyle = `
    <style>
        .legal-page h2 { margin-bottom: 0.25rem; }
        .legal-page hr { margin: 0.75rem 0 1.5rem; border: none; border-top: 2px solid var(--border-color); }
        .legal-page .legal-meta { color: var(--text-light); font-size: 0.9rem; margin-bottom: 2rem; }
        .legal-page h3 { margin-top: 2.25rem; margin-bottom: 0.6rem; font-size: 1.05rem; color: var(--primary-dark); }
        .legal-page p { margin: 0 0 0.9rem; line-height: 1.75; color: var(--text); }
        .legal-page ul { margin: 0.4rem 0 1rem 0; padding-left: 1.5rem; }
        .legal-page ul li { margin-bottom: 0.6rem; line-height: 1.7; color: var(--text); }
        .legal-page a { color: var(--primary); }
    </style>`;

function renderTerms(app) {
    app.innerHTML = _legalPageStyle + `
    <div class="shared-header"><a href="/" class="brand"><img src="/images/logo192.png" alt="logo"> Expense Tracker</a></div>
    <div class="container legal-page" style="max-width:760px; margin:2rem auto; padding:1rem 1.5rem 4rem;">
        <h2>Terms of Service</h2>
        <hr/>
        <p class="legal-meta"><strong>Last updated: May 2026</strong></p>

        <h3>1. Acceptance of Terms</h3>
        <p>By creating an account or using Expense Tracker ("the Service"), you agree to be bound by these Terms of Service. If you do not agree, do not use the Service.</p>

        <h3>2. Description of Service</h3>
        <p>Expense Tracker is a personal expense management application that allows you to record, categorise, and analyse expenses, scan receipts using AI-powered OCR, and export your data. The Service is provided as-is for personal use.</p>

        <h3>3. Account Responsibilities</h3>
        <p>You are responsible for maintaining the confidentiality of your account credentials. You must not share your account with others or attempt to access another user's data. You agree to notify us immediately of any unauthorised access at <a href="mailto:admin@rizibo.com">admin@rizibo.com</a>.</p>

        <h3>4. Receipt Scanning &amp; OCR Accuracy</h3>
        <p>Receipt scanning uses AI-based optical character recognition (OCR) technology. <strong>The accuracy of extracted data depends entirely on the quality of the uploaded image.</strong> Factors such as poor lighting, blurriness, skewed angles, low resolution, or partial visibility of the receipt will reduce extraction accuracy. You are responsible for reviewing and correcting all scanned data before relying on it. Expense Tracker makes no warranty regarding OCR accuracy and is not liable for errors in extracted data.</p>

        <h3>5. Third-Party AI &amp; OCR Processors</h3>
        <p>To provide receipt scanning, your uploaded images may be sent to third-party AI API providers (such as OpenAI or similar large-language-model services). By using the receipt scanning feature, you consent to this processing. We enter into Data Processing Agreements (DPAs) with our AI providers where required. Receipt images may be processed on servers located outside your country of residence, including transfers to the United States or the European Union. These transfers occur under appropriate safeguards (e.g. Standard Contractual Clauses). Third-party providers do not use your data for model training.</p>

        <h3>6. Data Retention &amp; Deletion</h3>
        <p>Your expense records and receipt images are retained for as long as your account is active. You may delete individual receipts or expenses at any time within the application. Upon account deletion, all your personal data including expense records, receipt images, and attachments will be permanently deleted within <strong>30 days</strong>. To request full account deletion, contact us at <a href="mailto:admin@rizibo.com">admin@rizibo.com</a>. Backups may retain deleted data for up to <strong>30 additional days</strong> before being purged.</p>

        <h3>7. Data Export</h3>
        <p>You may export your expense data at any time in CSV or JSON format using the built-in export feature. To request a full data export including attachments and account information, contact <a href="mailto:admin@rizibo.com">admin@rizibo.com</a>.</p>

        <h3>8. Prohibited Use</h3>
        <p>You may not use the Service to upload illegal content, attempt to access other users' data, reverse-engineer the application, or conduct automated scraping or abuse of the API.</p>

        <h3>9. Limitation of Liability</h3>
        <p>Expense Tracker is provided "as is" without any warranty of any kind. We are not liable for any inaccuracies in expense data, OCR results, currency conversions, or any loss of data. Our total liability to you for any claim arising from use of the Service is limited to the amount you paid for the Service in the 12 months preceding the claim (if applicable).</p>

        <h3>10. Changes to Terms</h3>
        <p>We may update these Terms at any time. We will notify users of material changes via email or an in-app notice. Continued use of the Service after changes constitutes acceptance of the updated Terms.</p>

        <h3>11. Contact</h3>
        <p>Questions about these Terms? Email us at <a href="mailto:admin@rizibo.com">admin@rizibo.com</a>.</p>
    </div>`;
}

function renderPrivacy(app) {
    app.innerHTML = _legalPageStyle + `
    <div class="shared-header"><a href="/" class="brand"><img src="/images/logo192.png" alt="logo"> Expense Tracker</a></div>
    <div class="container legal-page" style="max-width:760px; margin:2rem auto; padding:1rem 1.5rem 4rem;">
        <h2>Privacy Policy</h2>
        <hr/>
        <p class="legal-meta"><strong>Last updated: May 2026</strong></p>
        <p>This Privacy Policy explains how Expense Tracker ("we", "us", "our") collects, uses, stores, and protects your personal data when you use our service.</p>

        <h3>1. Data We Collect</h3>
        <ul>
            <li><strong>Account data:</strong> username, email address, phone number (optional), base currency.</li>
            <li><strong>Expense data:</strong> amounts, dates, categories, notes, tags, currency, and exchange rates you enter.</li>
            <li><strong>Receipt images &amp; attachments:</strong> images or PDFs you upload for OCR scanning or manual attachment.</li>
            <li><strong>Usage data:</strong> session information and application logs for security and debugging purposes.</li>
        </ul>

        <h3>2. How We Use Your Data</h3>
        <p>Your data is used solely to provide and improve the expense tracking functionality: storing and displaying your expenses, running OCR on receipt images, calculating currency conversions, and generating analytics dashboards. We do not use your data for advertising or sell it to third parties.</p>

        <h3>3. Data Retention</h3>
        <ul>
            <li>Expense records and receipt images are kept for the lifetime of your account.</li>
            <li>You can delete individual receipts, attachments, and expenses at any time within the app.</li>
            <li>On <strong>account deletion</strong>, all personal data (expense records, receipt images, attachments, account information) is permanently and immediately deleted. Encrypted backups may hold the data for up to <strong>30 additional days</strong> before purge.</li>
            <li>Application logs are retained for up to <strong>90 days</strong> for security and debugging, then automatically deleted.</li>
        </ul>

        <h3>4. Your Rights &amp; Controls</h3>
        <ul>
            <li><strong>Delete receipts/attachments:</strong> Available within the app on any expense detail page.</li>
            <li><strong>Export your data:</strong> Use the <strong>Data &amp; Export</strong> tab on the Account page to download a full archive (ZIP) that includes your profile, all expenses, receipt images, and attachments. Individual JSON/CSV exports are also available from the Expenses page.</li>
            <li><strong>Delete your account:</strong> You can permanently delete your account and all associated data directly in the app via the <strong>Danger Zone</strong> tab on the Account page. Deletion is immediate and irreversible. If you need assistance, email <a href="mailto:admin@rizibo.com">admin@rizibo.com</a>.</li>
            <li><strong>Correction:</strong> You can edit or delete any expense record, note, or tag within the app at any time.</li>
        </ul>

        <h3>5. Access Control &amp; Security</h3>
        <ul>
            <li>Each user account is strictly isolated. Users can only access their own expenses and receipts.</li>
            <li>Receipt file names are generated as non-guessable unique identifiers — they are not derived from your username, date, or any predictable pattern.</li>
            <li>All data is transmitted over HTTPS. Passwords are stored as salted hashes (never in plaintext).</li>
            <li>Session-based authentication with server-side session invalidation on logout.</li>
        </ul>

        <h3>6. Third-Party OCR &amp; AI Processing</h3>
        <p>When you scan a receipt, the uploaded image is sent to a third-party AI/OCR provider (such as OpenAI or a similar LLM API service) to extract structured data. By using the scan feature, you consent to this. Key details:</p>
        <ul>
            <li><strong>Data processor agreements (DPAs):</strong> We maintain DPAs with our AI providers as required under applicable privacy law.</li>
            <li><strong>Data location:</strong> Processing may occur in the United States or European Union, depending on the provider.</li>
            <li><strong>International transfers:</strong> Covered by appropriate safeguards such as Standard Contractual Clauses (SCCs).</li>
            <li><strong>Retention by third parties:</strong> Receipt images are used solely for the single OCR request and are not retained by the AI provider for training or any other purpose, per our agreements.</li>
            <li><strong>No training use:</strong> Your data is not used to train AI models by any of our third-party providers.</li>
        </ul>

        <h3>7. Camera Access</h3>
        <p>The app requests camera permission solely to capture receipt photos for scanning. Camera access is used only when you actively choose to use the camera feature. No video is recorded or stored; only the captured image is processed.</p>

        <h3>8. Cookies &amp; Session Data</h3>
        <p>We use a single session cookie for authentication purposes. No third-party tracking cookies, advertising pixels, or analytics cookies are used.</p>

        <h3>9. Data Sharing</h3>
        <p>We do not sell, rent, or share your personal data with third parties except: (a) third-party OCR/AI processors as described above under DPAs, (b) when required by law or a valid legal process, or (c) to protect the rights and safety of our users.</p>

        <h3>10. Incident Response</h3>
        <p>In the event of a data breach or security incident affecting your personal data, we will:</p>
        <ul>
            <li>Investigate and contain the incident within <strong>24 hours</strong> of discovery.</li>
            <li>Notify affected users by email within <strong>72 hours</strong> of confirming a breach, where required by applicable law.</li>
            <li>Provide details of what data was affected, the likely consequences, and the measures taken to address the breach.</li>
            <li>Report to relevant supervisory authorities where legally required (e.g. under GDPR).</li>
        </ul>
        <p>To report a suspected security vulnerability, email <a href="mailto:admin@rizibo.com">admin@rizibo.com</a> with the subject "Security Incident".</p>

        <h3>11. Children's Privacy</h3>
        <p>The Service is not directed at children under 16. We do not knowingly collect data from children. If you believe a child has provided us with personal data, please contact us to have it removed.</p>

        <h3>12. Changes to This Policy</h3>
        <p>We may update this Privacy Policy from time to time. We will notify you of material changes via email or an in-app notice. The "last updated" date at the top of this page reflects the most recent revision.</p>

        <h3>13. Contact &amp; Data Controller</h3>
        <p>For any privacy questions, data access requests, or to exercise your rights, contact us at:<br>
        <a href="mailto:admin@rizibo.com">admin@rizibo.com</a></p>
    </div>`;
}


