/* ============================================
   Expense Tracker - Utility Functions
   ============================================ */

let currentUser = null;
let chartInstances = {};
let appConfig = {}; // populated by loadAppConfig() on startup

async function loadAppConfig() {
    try {
        const cfg = await api('/api/config', { noAuthRedirect: true });
        if (cfg) Object.assign(appConfig, cfg);
    } catch { /* keep defaults */ }
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
    const { noAuthRedirect, ...fetchOptions } = options;
    const defaults = { headers: { 'Content-Type': 'application/json' }, credentials: 'include' };
    if (fetchOptions.body && !(fetchOptions.body instanceof FormData)) {
        fetchOptions.body = JSON.stringify(fetchOptions.body);
    } else if (fetchOptions.body instanceof FormData) {
        delete defaults.headers['Content-Type'];
    }
    const res = await fetch(url, { ...defaults, ...fetchOptions });
    if (res.status === 401 && !noAuthRedirect) { currentUser = null; navigate('#/login'); return null; }
    const text = await res.text();
    try { return JSON.parse(text); } catch { return text; }
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

function renderTerms(app) {
    app.innerHTML = `
    <div class="shared-header"><a href="/" class="brand"><img src="/images/logo192.png" alt="logo"> Expense Tracker</a></div>
    <div class="container" style="max-width:700px; margin:2rem auto; padding:1rem;">
        <h2>Terms of Service</h2>
        <hr/>
        <p>Last updated: May 2026</p>
        <h3>1. Acceptance</h3>
        <p>By using Expense Tracker, you agree to these terms. If you do not agree, please do not use the application.</p>
        <h3>2. Use of Service</h3>
        <p>Expense Tracker is provided as-is for personal expense tracking. You are responsible for maintaining the confidentiality of your account.</p>
        <h3>3. Data</h3>
        <p>Your expense data is stored securely. We do not sell or share your personal data with third parties.</p>
        <h3>4. Receipt Scanning</h3>
        <p>Receipt images are processed by AI models to extract structured data. Images may be stored for processing but are not used for training purposes.</p>
        <h3>5. Limitation of Liability</h3>
        <p>Expense Tracker is not liable for any inaccuracies in expense data, OCR results, or currency conversions.</p>
        <h3>6. Changes</h3>
        <p>We may update these terms at any time. Continued use constitutes acceptance of updated terms.</p>
    </div>`;
}

function renderPrivacy(app) {
    app.innerHTML = `
    <div class="shared-header"><a href="/" class="brand"><img src="/images/logo192.png" alt="logo"> Expense Tracker</a></div>
    <div class="container" style="max-width:700px; margin:2rem auto; padding:1rem;">
        <h2>Privacy Policy</h2>
        <hr/>
        <p>Last updated: May 2026</p>
        <h3>1. Information We Collect</h3>
        <p>We collect account information (username, email) and expense data you enter including receipt images.</p>
        <h3>2. How We Use Your Information</h3>
        <p>Your data is used solely to provide expense tracking functionality including receipt OCR, currency conversion, and analytics.</p>
        <h3>3. Camera Access</h3>
        <p>The app requests camera permission to scan receipts. Camera access is used only for this purpose and images are processed on our servers.</p>
        <h3>4. Data Storage</h3>
        <p>Data is stored on secured servers. Receipt images are stored for processing and retrieval.</p>
        <h3>5. Data Sharing</h3>
        <p>We do not sell, trade, or share your personal information with third parties except as required by law.</p>
        <h3>6. Cookies</h3>
        <p>We use session cookies for authentication. No third-party tracking cookies are used.</p>
        <h3>7. Contact</h3>
        <p>For privacy concerns, please refer to the project wiki.</p>
    </div>`;
}
