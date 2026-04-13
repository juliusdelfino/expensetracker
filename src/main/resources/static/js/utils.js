/* ============================================
   Expense Tracker - Utility Functions
   ============================================ */

let currentUser = null;
let chartInstances = {};

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

function renderTags(containerId, inputId, tags) {
    const container = document.getElementById(containerId);
    container.innerHTML = tags.map((t, i) => `<span class="tag">${t} <span class="remove-tag" onclick="this.parentElement.remove(); window._tags_${containerId}?.splice(${i},1)">&times;</span></span>`).join('') +
        `<input type="text" class="tag-input" id="${inputId}" placeholder="Add tag...">`;
    document.getElementById(inputId).addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const val = e.target.value.trim();
            if (val && !tags.includes(val)) { tags.push(val); renderTags(containerId, inputId, tags); }
            e.target.value = '';
        }
    });
    document.getElementById(inputId).focus();
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

