/* ============================================
   Expense Tracker - Expense Detail & Receipt View
   ============================================ */

async function renderExpenseDetail(app, id) {
    const data = await api(`/api/expenses/${id}`, { noAuthRedirect: true });
    if (!data || !data.expense) {
        app.innerHTML = `<div class="container"><div class="card" style="text-align:center; padding:2rem;">
            <i class="fa-solid fa-receipt" style="font-size:2rem; color:var(--text-light)"></i>
            <p style="margin-top:1rem">Expense not found.</p>
            ${currentUser
                ? `<a href="#/expenses" class="btn btn-outline" style="margin-top:1rem;"><i class="fa-solid fa-arrow-left"></i> Back to Expenses</a>`
                : `<a href="#/login" class="btn btn-primary" style="margin-top:1rem;"><i class="fa-solid fa-right-to-bracket"></i> Login</a>`}
        </div></div>`;
        return;
    }
    const e = data.expense;
    const items = data.items || [];
    const store = data.store;
    const isOwner = !!data.isOwner;
    window._expenseIsOwner = isOwner;

    const isReceiptScan = e.type === 'RECEIPT_SCAN';
    const isCompleted = e.status === 'COMPLETED';
    const isProcessing = e.status === 'PROCESSING';
    const isFailed = e.status === 'FAILED';

    if (isOwner && (!window._allExpenseCategories || window._allExpenseCategories.length === 0)) {
        window._allExpenseCategories = (await api('/api/expenses/categories')) || [];
    }

    let html = `<div class="container">
        <div class="action-bar">
            <div class="action-bar-left">
                <h2 style="color:var(--aegean-dark)">Expense Detail</h2>
                <span class="badge badge-${(e.status||'').toLowerCase()}">${statusIcon(e.status)} ${e.status}</span>
            </div>
            <div class="action-bar-right">
                ${isOwner && isFailed ? `<button class="btn btn-secondary btn-sm" onclick="retryExpense('${e.urlId}'); setTimeout(()=>location.reload(),500)"><i class="fa-solid fa-rotate"></i> Retry</button>` : ''}
                ${!isProcessing && !isFailed ? `<button class="btn btn-outline btn-sm" onclick="openShareMenu('${e.urlId}', this)"><i class="fa-solid fa-share-nodes"></i> Share</button>` : ''}
                ${isOwner ? `<button class="btn btn-secondary btn-sm" onclick="duplicateExpense('${e.urlId}')"><i class="fa-solid fa-copy"></i> Duplicate</button>` : ''}
                ${isOwner ? `<button class="btn btn-danger btn-sm" onclick="deleteExpense('${e.urlId}'); navigate('#/expenses')"><i class="fa-solid fa-trash"></i> Delete</button>` : ''}
            </div>
        </div>`;

    if (isProcessing) {
        html += `<div class="card" style="text-align:center; padding:3rem">
            <i class="fa-solid fa-spinner fa-spin" style="font-size:3rem; color:var(--aegean-mid)"></i>
            <p style="margin-top:1rem; color:var(--text-light)">Processing receipt... This may take 2-3 minutes.</p>
            <button class="btn btn-primary" style="margin-top:1rem" onclick="renderExpenseDetail(document.getElementById('app'),'${e.urlId}')">
                <i class="fa-solid fa-rotate"></i> Refresh
            </button>
        </div></div>`;
        app.innerHTML = html;
        return;
    }

    if (isReceiptScan && isCompleted && e.imagePath) {
        const imgFilename = e.imagePath.replace(/\\/g, '/').split('/').pop();
        const ext = (imgFilename.split('.').pop() || '').toLowerCase();
        const isPdf = ext === 'pdf';
        html += `<div class="side-by-side">
            <div class="card">
                <h3 class="card-title"><i class="fa-solid ${isPdf ? 'fa-file-pdf' : 'fa-image'}"></i> Scanned Receipt</h3>
                ${isPdf ? `
                    <iframe src="/pdfjs-5.6.205-dist/web/viewer.html?file=/api/attachments/receipts/${imgFilename}" style="width:100%; height:600px; border:0;"></iframe>
                ` : `
                    <div class="receipt-zoom-container" id="receiptZoomContainer">
                        <img src="/api/attachments/receipts/${imgFilename}" class="receipt-image" id="receiptImg" alt="Receipt">
                    </div>
                `}
            </div>
            <div class="card">
                <h3 class="card-title"><i class="fa-solid fa-receipt"></i> Receipt Details</h3>
                ${renderReceiptView(e, items, store, id, isOwner)}
            </div>
        </div>`;
    } else {
        html += `<div class="card">
            <h3 class="card-title"><i class="fa-solid fa-receipt"></i> Receipt Details</h3>
            ${renderReceiptView(e, items, store, id, isOwner)}
        </div>`;
    }

    const attachments = e.attachments || [];
    html += `<div class="card">
        <h3 class="card-title"><i class="fa-solid fa-paperclip"></i> Attachments</h3>
        <ul class="attachment-list" id="attachmentList">
            ${attachments.map(a => {
                const fname = a.replace(/\\/g, '/').split('/').pop();
                return `<li>
                    <a href="/api/attachments/${e.id}/${fname}" target="_blank"><i class="fa-solid fa-file"></i> ${fname}</a>
                    ${isOwner ? `<button class="btn btn-danger btn-sm btn-icon" onclick="removeAttachment('${e.id}','${fname}')"><i class="fa-solid fa-xmark"></i></button>` : ''}
                </li>`;
            }).join('')}
        </ul>
        ${isOwner ? `<div style="margin-top:0.75rem">
            <input type="file" id="newAttachment" multiple>
            <button class="btn btn-outline btn-sm" onclick="uploadAttachments('${e.id}')"><i class="fa-solid fa-upload"></i> Upload</button>
        </div>` : ''}
    </div>`;

    // Other Details collapsed section
    const storeHasMap = (store?.latitude != null && store?.longitude != null) || store?.name;
    html += `<div class="card">
        <div class="expand-toggle" onclick="toggleOtherDetails()">
            <i class="fa-solid fa-chevron-down" id="otherDetailsIcon"></i>
            <span id="otherDetailsLabel">Other details</span>
        </div>
        <div id="otherDetailsSection" style="display:none;">
            <div class="other-details-grid">
                <div class="other-detail-row">
                    <span class="other-detail-label">Type</span>
                    <span class="badge badge-${isReceiptScan ? 'scan' : 'manual'}">
                        <i class="fa-solid fa-${isReceiptScan ? 'camera' : 'pen'}"></i> ${isReceiptScan ? 'Receipt Scan' : 'Manual'}
                    </span>
                </div>
                ${e.scannedAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">Scanned</span>
                    <span>${new Date(e.scannedAt).toLocaleString()}</span>
                </div>` : ''}
                ${e.createdAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">Created</span>
                    <span>${new Date(e.createdAt).toLocaleString()}</span>
                </div>` : ''}
                ${e.updatedAt ? `<div class="other-detail-row">
                    <span class="other-detail-label">Updated</span>
                    <span>${new Date(e.updatedAt).toLocaleString()}</span>
                </div>` : ''}
                ${storeHasMap ? `<div class="other-detail-row">
                    <span class="other-detail-label"><i class="fa-solid fa-map-location-dot" style="color:var(--aegean-mid); margin-right:0.25rem;"></i> Store location</span>
                </div>
                <div id="storeOtherDetailsMap" style="height:200px; border-radius:var(--radius); margin-top:0.4rem; border:1px solid var(--border-color);"></div>` : ''}
            </div>
        </div>
    </div>`;

    html += '</div>';
    app.innerHTML = html;

    const editForm = document.getElementById('expenseEditForm');
    if (editForm) {
        editForm.onsubmit = async (ev) => {
            ev.preventDefault();
            const updates = {
                transactionDatetime: document.getElementById('eDate').value + ':00',
                amount: parseFloat(document.getElementById('eAmount').value),
                currency: document.getElementById('eCurrency').value,
                category: document.getElementById('eCategory').value,
                receiptNumber: document.getElementById('eReceipt').value,
                notes: document.getElementById('eNotes').value,
                tags: window._editTags || []
            };
            const exRate = document.getElementById('eExRate').value;
            if (exRate) updates.exchangeRate = parseFloat(exRate);
            const data = await api(`/api/expenses/${id}`, { method: 'PUT', body: updates });
            if (data && data.error) toast(data.error, 'error');
            else { toast('Expense updated!', 'success'); return; }
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
        marker.bindPopup(`<b>${name}</b>`);
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
        label.textContent = 'Hide details';
        setTimeout(() => {
            if (window._storeOtherDetailsMap) window._storeOtherDetailsMap.invalidateSize();
        }, 100);
    } else {
        section.style.display = 'none';
        icon.className = 'fa-solid fa-chevron-down';
        label.textContent = 'Other details';
    }
}

// --- Receipt view rendering ---
function renderReceiptView(e, items, store, id, isOwner) {
    const storeName = store?.name || '';
    const addressParts = [store?.address, store?.city, getCountryName(store?.country), store?.postalCode].filter(Boolean);
    const addressStr = addressParts.join(', ');
    const phone = store?.phoneNumber || '';
    const website = store?.website || '';

    const receiptDate = e.transactionDatetime ? new Date(e.transactionDatetime) : null;
    const dateStr = receiptDate ? receiptDate.toLocaleDateString() : '\u2014';
    const timeStr = receiptDate ? receiptDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
    const receiptNum = e.receiptNumber || '';

    const activeItems = (items || []).filter(i => !i.deleted);
    const currency = e.currency || '';
    const total = e.amount != null ? Number(e.amount).toFixed(2) : '\u2014';

    const baseCurr = currentUser?.baseCurrency || '';
    let amountInBase = e.amountInBase != null ? Number(e.amountInBase) : null;
    if (amountInBase === null && e.amount != null && e.exchangeRate != null && baseCurr && baseCurr !== currency) {
        amountInBase = Number(e.amount) * Number(e.exchangeRate);
    }
    const showBase = amountInBase != null && baseCurr && baseCurr !== currency;

    let html = `<div class="receipt-paper">`;

    // Store header
    const storeClick = isOwner ? `onclick="openChangeStoreDialog('${id}')" title="Click to edit store details"` : '';
    html += `<div class="receipt-store-section${isOwner ? ' receipt-clickable' : ''}" ${storeClick}>`;
    if (storeName) {
        html += `<div class="receipt-store-name">${esc(storeName)}</div>`;
        if (addressStr) html += `<div class="receipt-store-address">${esc(addressStr)}</div>`;
        if (phone || website) {
            html += `<div class="receipt-store-contact">`;
            if (phone) html += `<span>${esc(phone)}</span>`;
            if (phone && website) html += ' \u00b7 ';
            if (website) html += `<span>${esc(website)}</span>`;
            html += `</div>`;
        }
    } else {
        html += `<div class="receipt-store-placeholder">${isOwner ? 'No store info \u2014 tap to add' : 'No store info'}</div>`;
    }
    html += `</div><div class="receipt-divider"></div>`;

    // Receipt number + date/time
    const metaClick = isOwner ? `onclick="openExpenseDetailsDialog('${id}')" title="Click to edit expense details"` : '';
    html += `<div class="receipt-meta-section${isOwner ? ' receipt-clickable' : ''}" ${metaClick}>
        <div class="receipt-meta-line">
            <span>${receiptNum ? '#' + esc(receiptNum) : '<span style="color:var(--text-light);font-style:italic">No receipt #</span>'}</span>
            <span>${dateStr}${timeStr ? ' ' + timeStr : ''}</span>
        </div>
    </div><div class="receipt-divider"></div>`;

    // Items
    html += `<div class="receipt-items-section">`;
    html += `<div class="receipt-items-count">${activeItems.length} item${activeItems.length !== 1 ? 's' : ''}</div>`;
    if (activeItems.length > 0) {
        activeItems.forEach(i => {
            const qty = Number(i.quantity);
            const qtyStr = qty % 1 === 0 ? qty.toFixed(0) : qty.toFixed(2);
            const unitPrice = Number(i.unitPrice).toFixed(2);
            const itemTotal = i.totalPrice != null ? Number(i.totalPrice).toFixed(2) : '\u2014';
            const itemClick = isOwner
                ? `onclick="openItemDialog('${id}','${i.id}','${esc(i.itemName).replace(/'/g, "\\'")}',${i.quantity},${i.unitPrice},${i.totalPrice != null ? i.totalPrice : 0})" title="Click to edit item"`
                : '';
            html += `<div class="receipt-item-row${isOwner ? ' receipt-clickable' : ''}" ${itemClick}>
                <div class="receipt-item-name">${esc(i.itemName)}</div>
                <div class="receipt-item-detail">
                    <span class="receipt-item-qty">${qtyStr} \u00d7 ${unitPrice}</span>
                    <span class="receipt-item-total">${itemTotal}</span>
                </div>
            </div>`;
        });
    } else if (e.notes) {
        html += `<div class="receipt-notes-in-items">${esc(e.notes)}</div>`;
    } else {
        html += `<div class="receipt-no-items">No items</div>`;
    }
    html += `</div>`;

    if (isOwner) {
        html += `<div class="receipt-add-item">
            <button class="btn btn-outline btn-sm" onclick="openItemDialog('${id}')"><i class="fa-solid fa-plus"></i> Add Item</button>
        </div>`;
    }

    html += `<div class="receipt-divider receipt-divider-thick"></div>`;

    // Total
    const totalClick = isOwner ? `onclick="openExpenseDetailsDialog('${id}')" title="Click to edit expense details"` : '';
    html += `<div class="receipt-total-section${isOwner ? ' receipt-clickable' : ''}" ${totalClick}>
        <div class="receipt-total-line">
            <span class="receipt-total-label">TOTAL</span>
            <span class="receipt-total-amount">${total} ${esc(currency)}</span>
        </div>
        ${showBase ? `<div class="receipt-base-amount"><i class="fa-solid fa-exchange-alt" style="font-size:0.7rem"></i> ${amountInBase.toFixed(2)} ${esc(baseCurr)}</div>` : ''}
    </div>`;

    // Footer
    const notesShownInItems = activeItems.length === 0 && !!e.notes;
    if (e.category || (e.tags && e.tags.length) || (e.notes && !notesShownInItems)) {
        html += `<div class="receipt-divider"></div>`;
        const footerClick = isOwner ? `onclick="openExpenseDetailsDialog('${id}')" title="Click to edit expense details"` : '';
        html += `<div class="receipt-footer-section${isOwner ? ' receipt-clickable' : ''}" ${footerClick}>`;
        if (e.category) html += `<div class="receipt-footer-line"><span class="receipt-footer-label">Category:</span> ${esc(e.category)}</div>`;
        if (e.tags && e.tags.length) html += `<div class="receipt-footer-line"><span class="receipt-footer-label">Tags:</span> ${e.tags.map(t => esc(t)).join(', ')}</div>`;
        if (e.notes && !notesShownInItems) html += `<div class="receipt-footer-line"><span class="receipt-footer-label">Notes:</span> ${esc(e.notes)}</div>`;
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
            <div class="form-group"><label>Date & Time</label>
                <input type="datetime-local" class="form-control detail-datetime" id="eDate" value="${e.transactionDatetime ? e.transactionDatetime.substring(0,16) : ''}" ${ro}>
            </div>
            <div class="form-group"><label>Category</label>
                <input type="text" class="form-control" id="eCategory" value="${esc(e.category)}" list="eCategoryList" autocomplete="off" ${ro}>
                <datalist id="eCategoryList">
                    ${allCats.map(c => `<option value="${esc(c)}">`).join('')}
                </datalist>
            </div>
        </div>
        <div class="form-row detail-amount-row">
            <div class="form-group"><label>Amount</label>
                <input type="number" step="0.01" class="form-control" id="eAmount" value="${e.amount||''}" ${ro}>
            </div>
            <div class="form-group"><label>Currency</label>
                <input type="text" class="form-control" id="eCurrency" value="${esc(e.currency)}" list="eCurrencyList" autocomplete="off" ${ro}>
                <datalist id="eCurrencyList">
                    <option value="USD"></option><option value="EUR"></option>
                    <option value="GBP"></option><option value="SGD"></option>
                    <option value="JPY"></option><option value="AUD"></option>
                    <option value="CAD"></option><option value="CHF"></option>
                </datalist>
            </div>
            <div class="form-group"><label>Exchange Rate</label>
                <input type="number" step="0.000001" class="form-control" id="eExRate" value="${e.exchangeRate||''}" placeholder="Auto-fetched" ${ro}>
            </div>
        </div>
        ${e.amountInBase ? `<p class="amount-secondary" style="margin-bottom:1rem"><i class="fa-solid fa-exchange-alt"></i> ${Number(e.amountInBase).toFixed(2)} ${currentUser?.baseCurrency||''}</p>` : ''}
        <div class="form-group"><label>Receipt Number</label>
            <input type="text" class="form-control" id="eReceipt" value="${esc(e.receiptNumber)}" ${ro}>
        </div>
        <div class="form-group"><label>Tags</label>
            <div class="tags-container" id="eTagsContainer">
                <input type="text" class="tag-input" id="eTagInput" placeholder="Add tag..." ${ro}>
            </div>
        </div>
        <div class="form-group"><label>Notes</label>
            <textarea class="form-control" id="eNotes" rows="2" ${ro}>${esc(e.notes)}</textarea>
        </div>
        ${isOwner ? `<button type="submit" class="btn btn-primary"><i class="fa-solid fa-save"></i> Save Changes</button>` : ''}
    </form>`;
}

// --- Attachments ---
async function uploadAttachments(expenseId) {
    const files = document.getElementById('newAttachment').files;
    for (let f of files) {
        const fd = new FormData(); fd.append('file', f);
        await api(`/api/expenses/${expenseId}/attachments`, { method: 'POST', body: fd });
    }
    toast('Attachments uploaded', 'success');
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

async function removeAttachment(expenseId, filename) {
    await api(`/api/expenses/${expenseId}/attachments/${filename}`, { method: 'DELETE' });
    toast('Attachment removed', 'success');
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

