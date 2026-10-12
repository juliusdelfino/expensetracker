/* ============================================
   Expense Tracker - Router & Navigation
   ============================================ */

let currentPanel = 1; // 0=new expense, 1=home, 2=chat
let mobilePanelsRendered = false;
let _aiWarmupTriggered = false;

// --- Router ---
function navigate(hash) { window.location.hash = hash; }

async function ensureScriptsLoaded(sources) {
    const list = Array.isArray(sources) ? sources : [sources];
    await Promise.all(list.map(src => loadExternalScriptOnce(src)));
}

async function ensureDashboardRouteScripts() {
    await ensureScriptsLoaded(['/js/dashboard-widgets.js', '/js/home.js', '/js/expense-new.js', '/js/expense-dialogs.js']);
}

async function ensureExpensesRouteScripts() {
    await ensureScriptsLoaded(['/js/expenses.js']);
}

async function ensureExpenseDetailRouteScripts() {
    await ensureScriptsLoaded(['/js/expense-dialogs.js', '/js/expense-detail.js']);
}

async function ensureNewExpenseRouteScripts() {
    await ensureScriptsLoaded(['/js/expense-new.js', '/js/expense-dialogs.js']);
}

async function ensureReportsRouteScripts() {
    await ensureScriptsLoaded(['/js/reports.js']);
}

async function ensureAdminRouteScripts() {
    await ensureScriptsLoaded(['/js/admin.js']);
}

function renderRouteLoading(app, message) {
    if (!app) return;
    app.innerHTML = `<div class="container"><div class="card" style="text-align:center; padding:1.25rem;"><i class="fa-solid fa-spinner fa-spin"></i> ${esc(message || 'Loading...')}</div></div>`;
}

function applyAuthenticatedUser(data) {
    currentUser = data;
    document.getElementById('navbar').style.display = 'flex';
    renderNavigationState();
    if (!_aiWarmupTriggered) {
        _aiWarmupTriggered = true;
        Promise.allSettled([loadAiStatus(), loadAiModels()]).finally(() => {
            _aiWarmupTriggered = false;
        });
    }
}

async function checkAuth() {
    const data = await api('/api/auth/me');
    if (data && data.id) {
        applyAuthenticatedUser(data);
        return true;
    }
    currentUser = null;
    document.getElementById('navbar').style.display = 'none';
    clearAiClientState();
    return false;
}

/** Try to authenticate silently — never redirects to login on failure. */
async function tryCheckAuth() {
    try {
        const res = await fetch('/api/auth/me', { credentials: 'include' });
        if (res.ok) {
            const data = await res.json();
            if (data && data.id) {
                applyAuthenticatedUser(data);
                return true;
            }
        }
    } catch (e) { /* ignore */ }
    currentUser = null;
    document.getElementById('navbar').style.display = 'none';
    clearAiClientState();
    return false;
}

async function router() {
    const hash = window.location.hash || '#/dashboard';
    const routeOnly = hash.split('?')[0];
    const app = document.getElementById('app');
    // Stop any active expense-detail polling when navigating away
    if (typeof _stopExpenseDetailPolling === 'function') _stopExpenseDetailPolling();
    if (typeof resetExpenseDetailTransientUiState === 'function') resetExpenseDetailTransientUiState();

    if (routeOnly === '#/login' || routeOnly === '#/register') {
        hideMobileUI();
        const authed = await tryCheckAuth();
        if (authed) {
            navigate('#/dashboard');
            return;
        }
        if (routeOnly === '#/login') renderLogin(app);
        else renderRegister(app);
        return;
    }
    if (routeOnly === '#/terms') { hideMobileUI(); document.getElementById('navbar').style.display = 'none'; renderTerms(app); return; }
    if (routeOnly === '#/privacy') { hideMobileUI(); document.getElementById('navbar').style.display = 'none'; renderPrivacy(app); return; }

    // Owner expense detail pages require authentication
    if (hash.match(/^#\/expenses\/[a-f0-9-]+$/)) {
        hideMobileUI();
        const authed = await checkAuth();
        if (!authed) { navigate('#/login'); return; }
        await ensureExpenseDetailRouteScripts();
        renderExpenseDetail(app, hash.split('/')[2]);
        return;
    }

    // Shared expense detail pages are publicly accessible — try auth but never force redirect
    if (hash.match(/^#\/share\/[A-Za-z0-9-]+$/)) {
        hideMobileUI();
        await tryCheckAuth();
        await ensureExpenseDetailRouteScripts();
        renderExpenseDetail(app, hash.split('/')[2], { shared: true });
        return;
    }

    const authed = await checkAuth();
    if (!authed) { navigate('#/login'); return; }

    if (isMobile()) {
        // On mobile, #/dashboard shows the swipe panels always starting at Home (panel 1)
        if (routeOnly === '#/dashboard' || routeOnly === '' || routeOnly === '#/') {
            renderRouteLoading(app, 'Loading dashboard...');
            await ensureDashboardRouteScripts();
            app.innerHTML = '';
            currentPanel = 1; // Always snap to Home when navigating to dashboard
            showMobileUI();
            if (!mobilePanelsRendered) {
                renderMobilePanels();
                mobilePanelsRendered = true;
            }
            return;
        }
        // Other routes on mobile: hide swipe, render in #app
        hideMobileUI();
    } else {
        hideMobileUI();
    }

    if (routeOnly === '#/dashboard') {
        renderRouteLoading(app, 'Loading dashboard...');
        await ensureDashboardRouteScripts();
        await renderDashboard(app);
    }
    else if (routeOnly === '#/expenses') {
        await ensureExpensesRouteScripts();
        renderExpenseList(app);
    }
    else if (routeOnly === '#/expenses/new') {
        await ensureNewExpenseRouteScripts();
        renderNewExpense(app);
    }
    else if (routeOnly === '#/reports') {
        renderRouteLoading(app, 'Loading reports...');
        await ensureReportsRouteScripts();
        await renderReportsPage(app);
    }
    else if (routeOnly.match(/^#\/reports\/\d+$/)) {
        renderRouteLoading(app, 'Loading report details...');
        await ensureReportsRouteScripts();
        await renderReportDetail(app, routeOnly.split('/')[2]);
    }
    else if (routeOnly === '#/profile') renderProfile(app);
    else if (routeOnly === '#/admin') {
        if (!isAdminUser()) {
            toast('Admin access is required to open this page.', 'error');
            navigate('#/dashboard');
            return;
        }
        await ensureAdminRouteScripts();
        renderAdminPage(app);
    }
    else if (routeOnly === '#/chat') { toggleDesktopChat(); navigate('#/dashboard'); }
    else {
        await ensureDashboardRouteScripts();
        renderDashboard(app);
    }
}

window.addEventListener('hashchange', router);
window.addEventListener('load', () => { loadAppConfig(); router(); });
window.addEventListener('resize', () => {
    // If switching between mobile/desktop, re-route
    if (isMobile() && !document.getElementById('swipeWrapper').style.display !== 'none' && currentUser) {
        if (window.location.hash === '#/dashboard' || window.location.hash === '' || window.location.hash === '#/') {
            router();
        }
    } else if (!isMobile()) {
        hideMobileUI();
        if (window.location.hash === '#/dashboard' || window.location.hash === '' || window.location.hash === '#/') {
            router();
        }
    }
});

function showMobileUI() {
    document.getElementById('swipeWrapper').style.display = 'block';
    document.getElementById('bottomTabBar').style.display = 'flex';
    document.getElementById('app').style.display = 'none';
    goToPanel(currentPanel, false);
}

function hideMobileUI() {
    document.getElementById('swipeWrapper').style.display = 'none';
    document.getElementById('bottomTabBar').style.display = 'none';
    document.getElementById('app').style.display = 'block';
    // Stop camera if leaving scan panel
    stopCamera();
}

async function logout() {
    await api('/api/auth/logout', { method: 'POST' });
    currentUser = null;
    mobilePanelsRendered = false;
    document.getElementById('navbar').style.display = 'none';
    clearAiClientState();
    hideMobileUI();
    navigate('#/login');
}

function openChat(e) {
    if (e) e.preventDefault();
    if (isMobile()) {
        // On mobile, show swipe UI and go to chat panel
        const hash = window.location.hash;
        if (hash === '#/dashboard' || hash === '' || hash === '#/') {
            goToPanel(2);
        } else {
            navigate('#/dashboard');
            setTimeout(() => goToPanel(2), 100);
        }
    } else {
        // On desktop, toggle the chatbox
        toggleDesktopChat();
    }
}

function goToDashboard() {
    if (isMobile()) {
        const hash = window.location.hash;
        if (hash === '#/dashboard' || hash === '' || hash === '#/') {
            // Already on dashboard route — just snap to Home panel
            showMobileUI();
            goToPanel(1);
        } else {
            navigate('#/dashboard');
            // After route is handled, snap to panel 1
            setTimeout(() => goToPanel(1), 50);
        }
    } else {
        navigate('#/dashboard');
    }
}

