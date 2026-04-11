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

    // Populate currency datalist from server
    populateCurrencyDatalist('mCurrencyList', 'mCurrency', currentUser?.baseCurrency);

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

