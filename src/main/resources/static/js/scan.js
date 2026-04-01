/* ============================================
   Expense Tracker - Swipe & Scan Panel
   ============================================ */

let touchStartX = 0;
let touchStartY = 0;
let touchDeltaX = 0;
let isSwiping = false;
let swipeDisabled = false; // disable swipe when interacting with sliders/selects
let cameraStream = null;

// ============================================
// SWIPE LOGIC (mobile only)
// Panels: 0=New Expense, 1=Home, 2=Chat
// ============================================
function isInteractiveElement(el) {
    if (!el) return false;
    let node = el;
    while (node && node !== document.body) {
        if (node.classList && node.classList.contains('interactive-element')) return true;
        if (node.tagName === 'INPUT' && node.type === 'range') return true;
        if (node.tagName === 'SELECT') return true;
        if (node.tagName === 'CANVAS') return true;
        if (node.classList && node.classList.contains('dual-range-wrapper')) return true;
        if (node.classList && node.classList.contains('leaflet-container')) return true;
        node = node.parentElement;
    }
    return false;
}

function initSwipe() {
    const container = document.getElementById('swipeWrapper');
    if (!container) return;

    container.addEventListener('touchstart', (e) => {
        swipeDisabled = isInteractiveElement(e.target);
        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;
        touchDeltaX = 0;
        isSwiping = false;
        if (!swipeDisabled) {
            document.getElementById('swipeContainer').style.transition = 'none';
        }
    }, { passive: true });

    container.addEventListener('touchmove', (e) => {
        if (swipeDisabled) return;
        const dx = e.touches[0].clientX - touchStartX;
        const dy = e.touches[0].clientY - touchStartY;
        if (!isSwiping && Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy)) {
            isSwiping = true;
        }
        if (isSwiping) {
            touchDeltaX = dx;
            const offset = -currentPanel * window.innerWidth + dx;
            document.getElementById('swipeContainer').style.transform = `translateX(${offset}px)`;
        }
    }, { passive: true });

    container.addEventListener('touchend', () => {
        if (swipeDisabled) {
            swipeDisabled = false;
            return;
        }
        document.getElementById('swipeContainer').style.transition = 'transform 0.35s ease';
        if (isSwiping) {
            if (touchDeltaX > 60 && currentPanel > 0) {
                goToPanel(currentPanel - 1);
            } else if (touchDeltaX < -60 && currentPanel < 2) {
                goToPanel(currentPanel + 1);
            } else {
                goToPanel(currentPanel);
            }
        }
        isSwiping = false;
        touchDeltaX = 0;
    });
}
initSwipe();

function goToPanel(index, animate = true) {
    currentPanel = index;
    const container = document.getElementById('swipeContainer');
    if (container) {
        if (animate) container.style.transition = 'transform 0.35s ease';
        else container.style.transition = 'none';
        container.style.transform = `translateX(-${index * window.innerWidth}px)`;
    }
    // Update tab bar: match by data-panel attribute
    document.querySelectorAll('.tab-btn').forEach(btn => {
        const panelIdx = btn.getAttribute('data-panel');
        btn.classList.toggle('active', panelIdx !== null && parseInt(panelIdx) === index);
    });
    // Refresh home panel data when switching to it
    if (index === 1 && mobilePanelsRendered) {
        loadHomeFeed();
    }
    // Auto-open camera when swiping to New Expense panel
    if (index === 0 && mobilePanelsRendered) {
        activateMobileScanTab();
    }
}

// ============================================
// MOBILE PANEL RENDERING (3 panels: New Expense, Home, Chat)
// ============================================
function renderMobilePanels() {
    renderMobileNewExpensePanel();
    renderHomePanel();
    renderChatPanel();
}

/**
 * Render a lightweight New Expense form inside the swipe panel
 * with Scan Receipt tab activated by default.
 */
function renderMobileNewExpensePanel() {
    const panel = document.getElementById('panel-newexpense');
    if (!panel) return;
    panel.innerHTML = `<div style="padding:0.75rem;" id="mobileNewExpenseContent"></div>`;
    renderNewExpense(document.getElementById('mobileNewExpenseContent'), true);
}

/**
 * Activate the Scan tab and camera when swiping to the New Expense panel
 */
function activateMobileScanTab() {
    const scanTab = document.querySelector('#panel-newexpense .tab[data-tab="scan"]');
    if (scanTab) scanTab.click();
}


// ============================================
// CAMERA CLEANUP (still needed for desktop camera)
// ============================================

function stopCamera() {
    if (cameraStream) {
        cameraStream.getTracks().forEach(t => t.stop());
        cameraStream = null;
    }
}

