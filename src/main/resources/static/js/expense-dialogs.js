/* ============================================
   Expense Tracker - Expense Dialogs
   (Store, Item, Expense Details, Share Menu)
   ============================================ */

// ============================================
// CHANGE STORE DIALOG
// ============================================
let _changeStoreMap = null;
let _changeStoreMarker = null;
let _nominatimTimer = null;

async function openChangeStoreDialog(expenseId) {
    if (!window._expenseIsOwner) return;
    const data = await api(`/api/expenses/${expenseId}`);
    const store = data?.store || {};

    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'changeStoreOverlay';

    overlay.innerHTML = `
        <div class="change-store-dialog" id="changeStoreDialog">
            <div class="change-store-header">
                <h3 class="item-dialog-title" style="margin-bottom:0"><i class="fa-solid fa-store"></i> Change Store</h3>
                <button class="btn btn-outline btn-sm" onclick="closeChangeStoreDialog()"><i class="fa-solid fa-xmark"></i></button>
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
                <button class="btn btn-primary" onclick="saveChangeStore('${expenseId}', '${store.id || ''}')">
                    <i class="fa-solid fa-save"></i> Save
                </button>
                <button class="btn btn-outline" onclick="closeChangeStoreDialog()">
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

function initChangeStoreMap(store) {
    const mapEl = document.getElementById('changeStoreMap');
    if (!mapEl) return;

    if (_changeStoreMap) {
        try { _changeStoreMap.remove(); } catch(e) {}
        _changeStoreMap = null; _changeStoreMarker = null;
    }

    const lat = store?.latitude || 0;
    const lng = store?.longitude || 0;
    const hasCoords = store?.latitude != null && store?.longitude != null;
    const zoom = hasCoords ? 15 : 2;

    _changeStoreMap = L.map('changeStoreMap', { scrollWheelZoom: true }).setView([lat, lng], zoom);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '\u00a9 OpenStreetMap'
    }).addTo(_changeStoreMap);

    _changeStoreMarker = L.marker([lat, lng], { draggable: true }).addTo(_changeStoreMap);
    _changeStoreMarker.on('dragend', function() {
        const pos = _changeStoreMarker.getLatLng();
        const latEl = document.getElementById('csLat');
        const lngEl = document.getElementById('csLng');
        if (latEl) latEl.value = pos.lat.toFixed(6);
        if (lngEl) lngEl.value = pos.lng.toFixed(6);
    });

    setTimeout(() => _changeStoreMap && _changeStoreMap.invalidateSize(), 300);
}

function debounceNominatim() {
    clearTimeout(_nominatimTimer);
    const query = document.getElementById('nominatimSearch')?.value?.trim();
    if (!query || query.length < 3) { document.getElementById('nominatimResults').style.display = 'none'; return; }
    _nominatimTimer = setTimeout(() => searchNominatim(query), 1000);
}

async function searchNominatim(query) {
    const resultsEl = document.getElementById('nominatimResults');
    if (!resultsEl) return;
    resultsEl.style.display = 'block';
    resultsEl.innerHTML = '<div class="nominatim-loading"><i class="fa-solid fa-spinner fa-spin"></i> Searching...</div>';

    try {
        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=5`;
        const res = await fetch(url, { headers: { 'Accept-Language': 'en', 'User-Agent': 'ExpenseTracker/1.0' } });
        const results = await res.json();

        if (!results || results.length === 0) {
            resultsEl.innerHTML = '<div class="nominatim-loading">No results found</div>';
            return;
        }

        resultsEl.innerHTML = results.map((r, i) => `
            <div class="nominatim-result-item" onclick="selectNominatimResult(${i})">
                <div class="nominatim-result-name">${esc(r.display_name || '')}</div>
            </div>`).join('');

        window._nominatimResults = results;
    } catch (err) {
        resultsEl.innerHTML = '<div class="nominatim-loading">Search failed. Check connection.</div>';
    }
}

function selectNominatimResult(index) {
    const results = window._nominatimResults;
    if (!results || !results[index]) return;
    const r = results[index];
    const addr = r.address || {};

    const setVal = (id, val) => { const el = document.getElementById(id); if (el && val) el.value = val; };
    const placeName = r.name || addr.building || addr.place || addr.amenity || addr.shop || '';
    setVal('csName', placeName);
    const road = [addr.road, addr.house_number].filter(Boolean).join(' ') || addr.pedestrian || addr.path || '';
    setVal('csAddress', road);
    const city = addr.city || addr.town || addr.village || addr.municipality || '';
    setVal('csCity', city);
    const cc = (addr.country_code || '').toUpperCase();
    setVal('csCountry', cc);
    setVal('csPostal', addr.postcode || '');

    if (r.lat && r.lon) {
        setVal('csLat', parseFloat(r.lat).toFixed(6));
        setVal('csLng', parseFloat(r.lon).toFixed(6));
        const lat = parseFloat(r.lat);
        const lng = parseFloat(r.lon);
        if (_changeStoreMap && _changeStoreMarker) {
            _changeStoreMarker.setLatLng([lat, lng]);
            _changeStoreMap.setView([lat, lng], 16);
        }
    }

    document.getElementById('nominatimResults').style.display = 'none';
    document.getElementById('nominatimSearch').value = r.display_name || '';
    window._nominatimPlaceId = r.place_id ? String(r.place_id) : null;
    window._nominatimSnapshot = {
        address: document.getElementById('csAddress')?.value || '',
        city: document.getElementById('csCity')?.value || '',
        country: document.getElementById('csCountry')?.value || '',
        postalCode: document.getElementById('csPostal')?.value || ''
    };
    toast('Address populated from search result', 'success');
}

async function saveChangeStore(expenseId, storeId) {
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

    const storeData = {
        id: storeId || null,
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
    await api(`/api/expenses/${expenseId}/store`, { method: 'PUT', body: storeData });
    toast('Store updated!', 'success');
    closeChangeStoreDialog();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

function closeChangeStoreDialog() {
    if (_changeStoreMap) {
        try { _changeStoreMap.remove(); } catch(e) {}
        _changeStoreMap = null; _changeStoreMarker = null;
    }
    const overlay = document.getElementById('changeStoreOverlay');
    if (overlay) overlay.remove();
}

// ============================================
// ITEM DIALOG
// ============================================
function openItemDialog(expenseId, itemId, itemName, quantity, unitPrice) {
    if (!window._expenseIsOwner) return;
    const isEdit = !!itemId;
    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'itemDialogOverlay';
    overlay.onclick = (ev) => { if (ev.target === overlay) overlay.remove(); };
    overlay.innerHTML = `
        <div class="item-dialog">
            <h3 class="item-dialog-title">${isEdit ? 'Edit Item' : 'Add Item'}</h3>
            <div class="form-group">
                <label>Item Name</label>
                <input type="text" class="form-control" id="dlgItemName" value="${isEdit ? itemName : ''}" placeholder="Item name">
            </div>
            <div class="form-row" style="display:grid; grid-template-columns:1fr 1fr; gap:0.75rem;">
                <div class="form-group">
                    <label>Quantity</label>
                    <input type="number" step="0.01" class="form-control" id="dlgItemQty" value="${isEdit ? quantity : 1}">
                </div>
                <div class="form-group">
                    <label>Unit Price</label>
                    <input type="number" step="0.01" class="form-control" id="dlgItemPrice" value="${isEdit ? unitPrice : ''}">
                </div>
            </div>
            <div class="item-dialog-actions">
                <button class="btn btn-primary btn-sm" onclick="saveItemDialog('${expenseId}','${itemId || ''}')">
                    <i class="fa-solid fa-save"></i> ${isEdit ? 'Save' : 'Add'}
                </button>
                ${isEdit ? `<button class="btn btn-danger btn-sm" onclick="deleteItemDialog('${expenseId}','${itemId}')">
                    <i class="fa-solid fa-trash"></i> Delete
                </button>` : ''}
                <button class="btn btn-outline btn-sm" onclick="document.getElementById('itemDialogOverlay').remove()">
                    <i class="fa-solid fa-xmark"></i> Cancel
                </button>
            </div>
        </div>`;
    document.body.appendChild(overlay);
    document.getElementById('dlgItemName').focus();
}

async function saveItemDialog(expenseId, itemId) {
    const name = document.getElementById('dlgItemName').value;
    const qty = parseFloat(document.getElementById('dlgItemQty').value);
    const price = parseFloat(document.getElementById('dlgItemPrice').value);
    if (!name) { toast('Item name is required', 'error'); return; }

    const item = { itemName: name, quantity: qty, unitPrice: price };
    if (itemId) {
        await api(`/api/expenses/${expenseId}/items/${itemId}`, { method: 'PUT', body: item });
        toast('Item updated', 'success');
    } else {
        await api(`/api/expenses/${expenseId}/items`, { method: 'POST', body: item });
        toast('Item added', 'success');
    }
    document.getElementById('itemDialogOverlay')?.remove();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

async function deleteItemDialog(expenseId, itemId) {
    if (!confirm('Delete this item?')) return;
    await api(`/api/expenses/${expenseId}/items/${itemId}`, { method: 'DELETE' });
    toast('Item deleted', 'success');
    document.getElementById('itemDialogOverlay')?.remove();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

// ============================================
// EXPENSE DETAILS DIALOG (for receipt view)
// ============================================
async function openExpenseDetailsDialog(expenseId) {
    if (!window._expenseIsOwner) return;
    const data = await api(`/api/expenses/${expenseId}`);
    const e = data?.expense || {};

    const allCats = window._allExpenseCategories || [];
    window._dlgExpTags = [...(e.tags || [])];

    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'expenseDetailsOverlay';
    overlay.onclick = (ev) => { if (ev.target === overlay) closeExpenseDetailsDialog(); };

    overlay.innerHTML = `
        <div class="change-store-dialog" id="expenseDetailsDialogInner">
            <div class="change-store-header">
                <h3 class="item-dialog-title" style="margin-bottom:0"><i class="fa-solid fa-pen-to-square"></i> Edit Expense Details</h3>
                <button class="btn btn-outline btn-sm" onclick="closeExpenseDetailsDialog()"><i class="fa-solid fa-xmark"></i></button>
            </div>
            <div class="exp-dlg-fields-grid">
                <div class="form-group exp-dlg-f-date"><label>Date &amp; Time</label>
                    <input type="datetime-local" class="form-control" id="dlgExpDate" value="${e.transactionDatetime ? e.transactionDatetime.substring(0, 16) : ''}">
                </div>
                <div class="form-group exp-dlg-f-cat"><label>Category</label>
                    <input type="text" class="form-control" id="dlgExpCategory" value="${esc(e.category)}" list="dlgExpCategoryList" autocomplete="off">
                    <datalist id="dlgExpCategoryList">
                        ${allCats.map(c => `<option value="${esc(c)}">`).join('')}
                    </datalist>
                </div>
                <div class="form-group exp-dlg-f-amt"><label>Amount</label>
                    <input type="number" step="0.01" class="form-control" id="dlgExpAmount" value="${e.amount || ''}">
                </div>
                <div class="form-group exp-dlg-f-curr"><label>Currency</label>
                    <input type="text" class="form-control" id="dlgExpCurrency" list="dlgExpCurrencyList" value="${esc(e.currency)}" placeholder="e.g. USD">
                    <datalist id="dlgExpCurrencyList">
                        ${['USD','EUR','GBP','SGD','JPY','AUD','CAD','CHF'].map(c => `<option value="${c}"></option>`).join('')}
                    </datalist>
                </div>
                <div class="form-group exp-dlg-f-exr"><label>Exchange Rate</label>
                    <input type="number" step="0.000001" class="form-control" id="dlgExpExRate" value="${e.exchangeRate || ''}" placeholder="Auto">
                </div>
            </div>
            <div class="form-group" style="margin-top:0.75rem;">
                <label>Receipt Number</label>
                <input type="text" class="form-control" id="dlgExpReceipt" value="${esc(e.receiptNumber)}">
            </div>
            <div class="form-group"><label>Tags</label>
                <div class="tags-container" id="dlgExpTagsContainer">
                    <input type="text" class="tag-input" id="dlgExpTagInput" placeholder="Add tag...">
                </div>
            </div>
            <div class="form-group"><label>Notes</label>
                <textarea class="form-control" id="dlgExpNotes" rows="2">${esc(e.notes)}</textarea>
            </div>
            <div class="item-dialog-actions" style="margin-top:1rem;">
                <button class="btn btn-primary" onclick="saveExpenseDetailsDialog('${expenseId}')">
                    <i class="fa-solid fa-save"></i> Save
                </button>
                <button class="btn btn-outline" onclick="closeExpenseDetailsDialog()">
                    <i class="fa-solid fa-xmark"></i> Cancel
                </button>
            </div>
        </div>`;

    document.body.appendChild(overlay);
    renderTags('dlgExpTagsContainer', 'dlgExpTagInput', window._dlgExpTags);
}

async function saveExpenseDetailsDialog(expenseId) {
    commitPendingTag('dlgExpTagInput', window._dlgExpTags || []);
    const updates = {
        transactionDatetime: document.getElementById('dlgExpDate').value + ':00',
        amount: parseFloat(document.getElementById('dlgExpAmount').value),
        currency: document.getElementById('dlgExpCurrency').value,
        category: document.getElementById('dlgExpCategory').value,
        receiptNumber: document.getElementById('dlgExpReceipt').value,
        notes: document.getElementById('dlgExpNotes').value,
        tags: window._dlgExpTags || []
    };
    const exRate = document.getElementById('dlgExpExRate').value;
    if (exRate) updates.exchangeRate = parseFloat(exRate);

    await api(`/api/expenses/${expenseId}`, { method: 'PUT', body: updates });
    toast('Expense updated!', 'success');
    window._allExpenseCategories = null;
    closeExpenseDetailsDialog();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

function closeExpenseDetailsDialog() {
    const overlay = document.getElementById('expenseDetailsOverlay');
    if (overlay) overlay.remove();
}

// ============================================
// SHARE MENU
// ============================================
function openShareMenu(expenseId, btn) {
    closeShareMenu();
    const menu = document.createElement('div');
    menu.id = 'shareMenu';
    menu.className = 'share-menu';

    const url = window.location.origin + '/view/expenses/' + expenseId;
    const hasNativeShare = typeof navigator.share === 'function';

    menu.innerHTML = `
        <div class="share-menu-item" onclick="copyExpenseLink('${url}')">
            <i class="fa-solid fa-link"></i> Copy link
        </div>
        ${hasNativeShare ? `<div class="share-menu-item" onclick="nativeShareExpense('${url}')">
            <i class="fa-solid fa-share-nodes"></i> Share\u2026
        </div>` : ''}
    `;

    document.body.appendChild(menu);
    const rect = btn.getBoundingClientRect();
    menu.style.top = (rect.bottom + window.scrollY + 6) + 'px';
    menu.style.right = (document.documentElement.clientWidth - rect.right) + 'px';
    setTimeout(() => document.addEventListener('click', _shareMenuOutsideClick), 0);
}

function _shareMenuOutsideClick(e) {
    const menu = document.getElementById('shareMenu');
    if (menu && !menu.contains(e.target)) closeShareMenu();
}

function closeShareMenu() {
    document.removeEventListener('click', _shareMenuOutsideClick);
    const menu = document.getElementById('shareMenu');
    if (menu) menu.remove();
}

async function copyExpenseLink(url) {
    closeShareMenu();
    try {
        await navigator.clipboard.writeText(url);
        toast('Link copied!', 'success');
    } catch {
        toast('Could not copy link', 'error');
    }
}

async function nativeShareExpense(url) {
    closeShareMenu();
    try {
        await navigator.share({ title: 'Expense', url });
    } catch (err) {
        if (err.name !== 'AbortError') toast('Share failed', 'error');
    }
}

