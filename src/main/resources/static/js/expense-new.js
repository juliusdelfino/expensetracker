/* ============================================
   Expense Tracker - New Expense (Manual + Scan)
   ============================================ */

function renderNewExpense(app, embedded = false) {
    const container = embedded ? app : app;
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
                    <div style="display:grid; grid-template-columns:3fr 1fr; gap:0.75rem;">
                        <div class="form-group">
                            <label>Amount <span style="color:var(--danger)">*</span></label>
                            <input type="number" step="0.01" class="form-control" id="mAmount" required placeholder="0.00">
                        </div>
                        <div class="form-group">
                            <label>Currency <span style="color:var(--danger)">*</span></label>
                            <input type="text" class="form-control" id="mCurrency" list="mCurrencyList" placeholder="USD" required>
                            <datalist id="mCurrencyList">
                                <option value="USD"></option><option value="EUR"></option>
                                <option value="GBP"></option><option value="SGD"></option>
                                <option value="JPY"></option><option value="AUD"></option>
                                <option value="CAD"></option><option value="CHF"></option>
                            </datalist>
                        </div>
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
                        <div style="display:grid; grid-template-columns:2fr 1fr; gap:0.75rem;">
                            <div class="form-group">
                                <label>Receipt Number</label>
                                <input type="text" class="form-control" id="mReceipt">
                            </div>
                            <div class="form-group">
                                <label>Exchange Rate</label>
                                <input type="number" step="0.000001" class="form-control" id="mExRate" placeholder="Auto-fetched">
                            </div>
                        </div>
                        <div class="form-group">
                            <label>Tags (press Enter to add)</label>
                            <div class="tags-container" id="mTagsContainer">
                                <input type="text" class="tag-input" id="mTagInput" placeholder="Add tag...">
                            </div>
                        </div>
                        <div class="form-group">
                            <label>Items</label>
                            <div id="mItemsList" class="new-expense-items-list"></div>
                            <button type="button" class="btn btn-outline btn-sm" onclick="addNewExpenseItem()" style="margin-top:0.5rem;">
                                <i class="fa-solid fa-plus"></i> Add Item
                            </button>
                        </div>
                        <div class="form-group">
                            <label>Store</label>
                            <div id="mStorePreview" class="new-expense-store-preview">
                                <span class="text-light">No store set</span>
                            </div>
                            <button type="button" class="btn btn-outline btn-sm" onclick="openNewExpenseStoreDialog()" style="margin-top:0.5rem;">
                                <i class="fa-solid fa-store"></i> Set Store
                            </button>
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
                <div id="desktopCameraContainer" style="display:none; margin-top:1rem; position:relative;">
                    <video id="desktopCameraPreview" autoplay playsinline muted style="width:100%; border-radius:var(--radius); background:#000; display:block;"></video>
                    <canvas id="desktopCameraCanvas" style="display:none;"></canvas>
                    <div style="position:absolute; bottom:1rem; left:0; right:0; display:flex; gap:0.75rem; justify-content:center; z-index:10;">
                        <button class="btn btn-primary camera-overlay-btn" onclick="desktopCapturePhoto()"><i class="fa-solid fa-circle-dot"></i> Capture</button>
                        <button class="btn camera-overlay-btn camera-cancel-btn" onclick="closeDesktopCamera()"><i class="fa-solid fa-xmark"></i> Cancel</button>
                    </div>
                </div>
                <div id="uploadStatus" style="margin-top:1rem; display:none"></div>
            </div>
        </div>
    </div>`;

    // Set date default to local time (not UTC)
    const _now = new Date();
    document.getElementById('mDate').value = new Date(_now - _now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
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

    // Populate currency datalist from server
    populateCurrencyDatalist('mCurrencyList', 'mCurrency', currentUser?.baseCurrency);

    let tags = [];
    window._newExpenseItems = [];
    window._newExpenseStore = null;

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
        const _n = new Date();
        const _localNow = new Date(_n - _n.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
        const expense = {
            transactionDatetime: (document.getElementById('mDate').value || _localNow) + ':00',
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
            // Save items
            for (const item of window._newExpenseItems) {
                await api(`/api/expenses/${saved.urlId}/items`, { method: 'POST', body: item });
            }
            // Save store
            if (window._newExpenseStore) {
                await api(`/api/expenses/${saved.urlId}/store`, { method: 'PUT', body: window._newExpenseStore });
            }
            // Upload attachments
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
            desktopCameraStream = await navigator.mediaDevices.getUserMedia({ video: true });
        }

        video.srcObject = desktopCameraStream;
        await video.play();
        container.style.display = 'block';
        document.getElementById('desktopCameraBtn').style.display = 'none';

        // Hide drag & drop area and "or" divider, reduce padding for mobile
        const uploadZone = document.getElementById('uploadZone');
        if (uploadZone) uploadZone.style.display = 'none';
        const orDivider = uploadZone?.nextElementSibling;
        if (orDivider) orDivider.style.display = 'none';
        const scanCard = container.closest('.card');
        if (scanCard && isMobile()) {
            scanCard.style.padding = '0';
            scanCard.style.boxShadow = 'none';
            scanCard.style.background = 'transparent';
            // Also reduce padding on parent wrappers
            const tabContent = scanCard.closest('.tab-content');
            if (tabContent) tabContent.style.padding = '0';
            const heading = scanCard.closest('.tab-content')?.previousElementSibling;
            // Hide the heading and tabs bar
            const parentContainer = scanCard.closest('#mobileNewExpenseContent') || scanCard.closest('.container');
            if (parentContainer) {
                parentContainer.style.padding = '0';
                const h2 = parentContainer.querySelector('h2');
                if (h2) h2.style.display = 'none';
                const tabs = parentContainer.querySelector('.tabs');
                if (tabs) tabs.style.display = 'none';
            }
        }
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
    if (container) {
        // Restore card padding and hidden elements
        const scanCard = container.closest('.card');
        if (scanCard) {
            scanCard.style.padding = '';
            scanCard.style.boxShadow = '';
            scanCard.style.background = '';
        }
        const parentContainer = container.closest('#mobileNewExpenseContent') || container.closest('.container');
        if (parentContainer) {
            parentContainer.style.padding = '';
            const h2 = parentContainer.querySelector('h2');
            if (h2) h2.style.display = '';
            const tabs = parentContainer.querySelector('.tabs');
            if (tabs) tabs.style.display = '';
        }
        container.style.display = 'none';
    }
    const btn = document.getElementById('desktopCameraBtn');
    if (btn) btn.style.display = '';
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

    const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
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
// NEW EXPENSE - INLINE ITEMS
// ============================================
function addNewExpenseItem() {
    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'newItemDialogOverlay';
    overlay.onclick = (ev) => { if (ev.target === overlay) overlay.remove(); };
    overlay.innerHTML = `
        <div class="item-dialog">
            <h3 class="item-dialog-title">Add Item</h3>
            <div class="form-group">
                <label>Item Name</label>
                <input type="text" class="form-control" id="newDlgItemName" placeholder="Item name">
            </div>
            <div class="form-row" style="display:grid; grid-template-columns:1fr 1fr; gap:0.75rem;">
                <div class="form-group">
                    <label>Quantity</label>
                    <input type="number" step="0.01" class="form-control" id="newDlgItemQty" value="1">
                </div>
                <div class="form-group">
                    <label>Unit Price</label>
                    <input type="number" step="0.01" class="form-control" id="newDlgItemPrice" placeholder="0.00">
                </div>
            </div>
            <div class="item-dialog-actions">
                <button class="btn btn-primary btn-sm" onclick="saveNewExpenseItem()">
                    <i class="fa-solid fa-plus"></i> Add
                </button>
                <button class="btn btn-outline btn-sm" onclick="document.getElementById('newItemDialogOverlay').remove()">
                    <i class="fa-solid fa-xmark"></i> Cancel
                </button>
            </div>
        </div>`;
    document.body.appendChild(overlay);
    document.getElementById('newDlgItemName').focus();
}

function editNewExpenseItem(index) {
    const item = window._newExpenseItems[index];
    if (!item) return;
    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'newItemDialogOverlay';
    overlay.onclick = (ev) => { if (ev.target === overlay) overlay.remove(); };
    overlay.innerHTML = `
        <div class="item-dialog">
            <h3 class="item-dialog-title">Edit Item</h3>
            <div class="form-group">
                <label>Item Name</label>
                <input type="text" class="form-control" id="newDlgItemName" value="${esc(item.itemName)}">
            </div>
            <div class="form-row" style="display:grid; grid-template-columns:1fr 1fr; gap:0.75rem;">
                <div class="form-group">
                    <label>Quantity</label>
                    <input type="number" step="0.01" class="form-control" id="newDlgItemQty" value="${item.quantity}">
                </div>
                <div class="form-group">
                    <label>Unit Price</label>
                    <input type="number" step="0.01" class="form-control" id="newDlgItemPrice" value="${item.unitPrice}">
                </div>
            </div>
            <div class="item-dialog-actions">
                <button class="btn btn-primary btn-sm" onclick="updateNewExpenseItem(${index})">
                    <i class="fa-solid fa-save"></i> Save
                </button>
                <button class="btn btn-danger btn-sm" onclick="removeNewExpenseItem(${index})">
                    <i class="fa-solid fa-trash"></i> Delete
                </button>
                <button class="btn btn-outline btn-sm" onclick="document.getElementById('newItemDialogOverlay').remove()">
                    <i class="fa-solid fa-xmark"></i> Cancel
                </button>
            </div>
        </div>`;
    document.body.appendChild(overlay);
    document.getElementById('newDlgItemName').focus();
}

function saveNewExpenseItem() {
    const name = document.getElementById('newDlgItemName').value.trim();
    const qty = parseFloat(document.getElementById('newDlgItemQty').value) || 1;
    const price = parseFloat(document.getElementById('newDlgItemPrice').value) || 0;
    if (!name) { toast('Item name is required', 'error'); return; }
    window._newExpenseItems.push({ itemName: name, quantity: qty, unitPrice: price });
    document.getElementById('newItemDialogOverlay')?.remove();
    renderNewExpenseItems();
}

function updateNewExpenseItem(index) {
    const name = document.getElementById('newDlgItemName').value.trim();
    const qty = parseFloat(document.getElementById('newDlgItemQty').value) || 1;
    const price = parseFloat(document.getElementById('newDlgItemPrice').value) || 0;
    if (!name) { toast('Item name is required', 'error'); return; }
    window._newExpenseItems[index] = { itemName: name, quantity: qty, unitPrice: price };
    document.getElementById('newItemDialogOverlay')?.remove();
    renderNewExpenseItems();
}

function removeNewExpenseItem(index) {
    window._newExpenseItems.splice(index, 1);
    document.getElementById('newItemDialogOverlay')?.remove();
    renderNewExpenseItems();
}

function renderNewExpenseItems() {
    const container = document.getElementById('mItemsList');
    if (!container) return;
    if (window._newExpenseItems.length === 0) {
        container.innerHTML = '';
        return;
    }
    container.innerHTML = window._newExpenseItems.map((item, i) => {
        const total = (item.quantity * item.unitPrice).toFixed(2);
        const qtyStr = item.quantity % 1 === 0 ? item.quantity.toFixed(0) : item.quantity.toFixed(2);
        return `<div class="new-expense-item-row" onclick="editNewExpenseItem(${i})">
            <span class="new-expense-item-name">${esc(item.itemName)}</span>
            <span class="new-expense-item-detail">${qtyStr} × ${item.unitPrice.toFixed(2)} = ${total}</span>
        </div>`;
    }).join('');

    // Auto-compute Amount from item totals
    const itemsTotal = window._newExpenseItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
    const amountField = document.getElementById('mAmount');
    if (amountField) amountField.value = itemsTotal.toFixed(2);
}

// ============================================
// NEW EXPENSE - STORE DIALOG (reuses Change Store pattern)
// ============================================
function openNewExpenseStoreDialog() {
    window._expenseIsOwner = true; // allow editing
    const store = window._newExpenseStore || {};

    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'changeStoreOverlay';
    overlay.onclick = (ev) => { if (ev.target === overlay) closeNewExpenseStoreDialog(); };

    overlay.innerHTML = `
        <div class="change-store-dialog" id="changeStoreDialog">
            <div class="change-store-header">
                <h3 class="item-dialog-title" style="margin-bottom:0"><i class="fa-solid fa-store"></i> Set Store</h3>
                <button class="btn btn-outline btn-sm" onclick="closeNewExpenseStoreDialog()"><i class="fa-solid fa-xmark"></i></button>
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
            <div class="form-row-inline">
                <div class="form-group">
                    <label>City</label>
                    <input type="text" class="form-control" id="csCity" value="${esc(store.city || '')}">
                </div>
                <div class="form-group">
                    <label>Country Code</label>
                    <input type="text" class="form-control" id="csCountry" value="${esc(store.country || '')}" placeholder="e.g. AT">
                </div>
            </div>
            <div class="form-row-inline">
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
                <button class="btn btn-primary" onclick="saveNewExpenseStore()">
                    <i class="fa-solid fa-save"></i> Save
                </button>
                ${window._newExpenseStore ? `<button class="btn btn-danger" onclick="clearNewExpenseStore()">
                    <i class="fa-solid fa-trash"></i> Remove
                </button>` : ''}
                <button class="btn btn-outline" onclick="closeNewExpenseStoreDialog()">
                    <i class="fa-solid fa-xmark"></i> Cancel
                </button>
            </div>
        </div>`;

    document.body.appendChild(overlay);
    document.getElementById('nominatimSearch').focus();
    window._nominatimPlaceId = null;
    window._nominatimSnapshot = null;
    setTimeout(() => initChangeStoreMap(store), 200);
}

function saveNewExpenseStore() {
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

    window._newExpenseStore = {
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
    toast('Store set!', 'success');
    closeNewExpenseStoreDialog();
    renderNewExpenseStorePreview();
}

function clearNewExpenseStore() {
    window._newExpenseStore = null;
    closeNewExpenseStoreDialog();
    renderNewExpenseStorePreview();
    toast('Store removed', 'info');
}

function closeNewExpenseStoreDialog() {
    if (_changeStoreMap) {
        try { _changeStoreMap.remove(); } catch(e) {}
        _changeStoreMap = null; _changeStoreMarker = null;
    }
    const overlay = document.getElementById('changeStoreOverlay');
    if (overlay) overlay.remove();
}

function renderNewExpenseStorePreview() {
    const preview = document.getElementById('mStorePreview');
    if (!preview) return;
    const store = window._newExpenseStore;
    if (!store || !store.name) {
        preview.innerHTML = '<span class="text-light">No store set</span>';
        return;
    }
    const parts = [store.name, store.city, store.country].filter(Boolean);
    preview.innerHTML = `<span><i class="fa-solid fa-store" style="color:var(--aegean-mid); margin-right:0.35rem;"></i>${esc(parts.join(', '))}</span>`;
}

