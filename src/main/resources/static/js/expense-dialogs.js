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
let _selectedDbStoreId = null; // tracks a DB store selected from the search dropdown
let _userLocation = null; // cached user geolocation {lat, lon}

// Try to get user location once; cache the result
function _getUserLocation() {
    if (_userLocation !== null) return Promise.resolve(_userLocation);
    return new Promise(resolve => {
        if (!navigator.geolocation) { resolve(false); return; }
        navigator.geolocation.getCurrentPosition(
            pos => { _userLocation = { lat: pos.coords.latitude, lon: pos.coords.longitude }; resolve(_userLocation); },
            ()  => { _userLocation = false; resolve(false); },
            { timeout: 5000, maximumAge: 300000 }
        );
    });
}

function _setStoreFieldValue(id, value) {
    const el = document.getElementById(id);
    if (!el) return;
    el.value = value || '';
}

function _applyReverseAddress(address) {
    if (!address) return false;
    const road = [address.road, address.house_number].filter(Boolean).join(' ') || address.pedestrian || address.path || '';
    const city = address.city || address.town || address.village || address.municipality || '';
    const countryCode = (address.country_code || '').toUpperCase();
    const postalCode = address.postcode || '';

    const hasAny = !!(road || city || countryCode || postalCode);
    if (!hasAny) return false;

    _setStoreFieldValue('csAddress', road);
    _setStoreFieldValue('csCity', city);
    _setStoreFieldValue('csCountry', countryCode);
    _setStoreFieldValue('csPostal', postalCode);
    return true;
}

async function reverseGeocodeStoreLocation(lat, lng) {
    try {
        const url = `https://nominatim.openstreetmap.org/reverse?lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lng)}&format=json&addressdetails=1`;
        const result = await fetch(url, {
            headers: { 'Accept-Language': getActiveLocale(), 'User-Agent': 'ExpenseTracker/1.0' }
        }).then(r => r.json());

        const updated = _applyReverseAddress(result?.address);
        if (updated) {
            window._nominatimPlaceId = result?.place_id ? String(result.place_id) : null;
            window._nominatimSnapshot = {
                address: document.getElementById('csAddress')?.value || '',
                city: document.getElementById('csCity')?.value || '',
                country: document.getElementById('csCountry')?.value || '',
                postalCode: document.getElementById('csPostal')?.value || ''
            };
            _selectedDbStoreId = null;
        }
    } catch (e) {
        // Reverse geocoding is best-effort; keep coordinate update even if this fails.
    }
}

async function openChangeStoreDialog(expenseId) {
    if (!window._expenseIsOwner) return;
    const data = await api(`/api/expenses/${expenseId}`);
    const store = data?.store || {};

    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'changeStoreOverlay';

    overlay.innerHTML = `
        <div class="change-store-dialog" id="changeStoreDialog">
            <div class="change-store-header" style="padding-top:1.5rem; padding-left:1.5rem; padding-right:1.5rem; flex-shrink:0;">
                <h3 class="item-dialog-title" style="margin-bottom:0"><i class="fa-solid fa-store"></i> ${t('dialogs.changeStore.title', { defaultValue: 'Change Store' })}</h3>
                <button class="btn btn-outline btn-sm" onclick="closeChangeStoreDialog()"><i class="fa-solid fa-xmark"></i></button>
            </div>
            <div class="change-store-dialog-body">
                <div class="form-group" style="position:relative; margin-top:0.75rem;">
                    <label>${t('dialogs.changeStore.searchLabel', { defaultValue: 'Search your stores or an address' })}</label>
                    <input type="text" class="form-control" id="nominatimSearch" placeholder="${t('dialogs.changeStore.searchPlaceholder', { defaultValue: 'e.g. Starbucks or Rivergate Vienna' })}" oninput="debounceNominatim()" autocomplete="off">
                    <div class="nominatim-results" id="nominatimResults" style="display:none;"></div>
                </div>
                <p style="font-size:0.78rem; color:var(--text-light); margin-bottom:0.75rem;"><i class="fa-solid fa-circle-info"></i> ${t('dialogs.changeStore.searchHint', { defaultValue: 'Pick an existing store or search to auto-fill fields.' })}</p>
                <div class="form-group">
                    <label>${t('dialogs.common.name', { defaultValue: 'Name' })}</label>
                    <input type="text" class="form-control" id="csName" value="${esc(store.name || '')}">
                </div>
                <div class="form-group">
                    <label>${t('dialogs.common.address', { defaultValue: 'Address' })}</label>
                    <input type="text" class="form-control" id="csAddress" value="${esc(store.address || '')}">
                </div>
                <div class="form-row-inline">
                    <div class="form-group">
                        <label>${t('dialogs.common.city', { defaultValue: 'City' })}</label>
                        <input type="text" class="form-control" id="csCity" value="${esc(store.city || '')}">
                    </div>
                    <div class="form-group">
                        <label>${t('dialogs.common.countryCode', { defaultValue: 'Country Code' })}</label>
                        <input type="text" class="form-control" id="csCountry" value="${esc(store.country || '')}" placeholder="${t('dialogs.common.countryCodePlaceholder', { defaultValue: 'e.g. AT' })}">
                    </div>
                </div>
                <div class="form-row-inline">
                    <div class="form-group">
                        <label>${t('dialogs.common.postalCode', { defaultValue: 'Postal Code' })}</label>
                        <input type="text" class="form-control" id="csPostal" value="${esc(store.postalCode || '')}">
                    </div>
                    <div class="form-group">
                        <label>${t('auth.phone', { defaultValue: 'Phone' })}</label>
                        <input type="text" class="form-control" id="csPhone" value="${esc(store.phoneNumber || '')}">
                    </div>
                </div>
                <div class="form-group">
                    <label>${t('dialogs.common.website', { defaultValue: 'Website' })}</label>
                    <input type="text" class="form-control" id="csWebsite" value="${esc(store.website || '')}">
                </div>
                <div class="form-group">
                    <label>${t('dialogs.changeStore.location', { defaultValue: 'Location' })} <span style="font-weight:normal; color:var(--text-light); font-size:0.8rem;">${t('dialogs.changeStore.dragPinHint', { defaultValue: '(drag pin to adjust)' })}</span></label>
                    <div id="changeStoreMap" style="height:220px; border-radius:var(--radius); border:1px solid var(--border-color);"></div>
                </div>
                <input type="hidden" id="csLat" value="${store.latitude || ''}">
                <input type="hidden" id="csLng" value="${store.longitude || ''}">
            </div>
            <div class="item-dialog-actions">
                <button class="btn btn-primary" onclick="saveChangeStore('${expenseId}', '${store.id || ''}')">
                    <i class="fa-solid fa-save"></i> ${t('common.save', { defaultValue: 'Save' })}
                </button>
                <button class="btn btn-outline" onclick="closeChangeStoreDialog()">
                    <i class="fa-solid fa-xmark"></i> ${t('common.cancel', { defaultValue: 'Cancel' })}
                </button>
            </div>
        </div>`;

    document.body.appendChild(overlay);
    _selectedDbStoreId = null;
    document.getElementById('nominatimSearch').focus();
    window._nominatimPlaceId = null;
    window._nominatimSnapshot = null;
    _getUserLocation(); // warm up location cache
    _registerEscHandler('changeStoreOverlay', closeChangeStoreDialog);
    setTimeout(() => initChangeStoreMap(store), 200);
}

async function initChangeStoreMap(store) {
    try {
        await ensureLeafletLoaded();
    } catch (e) {
        toast(t('dialogs.changeStore.mapLoadFailed', { defaultValue: 'Map failed to load. Please try again.' }), 'error');
        return;
    }
    if (typeof L === 'undefined') {
        toast(t('dialogs.changeStore.mapUnavailable', { defaultValue: 'Map library is unavailable.' }), 'error');
        return;
    }

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
    _changeStoreMarker.on('dragend', async function() {
        const pos = _changeStoreMarker.getLatLng();
        const latEl = document.getElementById('csLat');
        const lngEl = document.getElementById('csLng');
        if (latEl) latEl.value = pos.lat.toFixed(6);
        if (lngEl) lngEl.value = pos.lng.toFixed(6);
        await reverseGeocodeStoreLocation(pos.lat, pos.lng);
    });

    // Custom "locate me" control — placed below the zoom buttons
    const LocateControl = L.Control.extend({
        options: { position: 'topleft' },
        onAdd() {
            const btn = L.DomUtil.create('button', 'leaflet-bar leaflet-control leaflet-control-locate');
            btn.type = 'button';
            btn.title = t('dialogs.changeStore.goToMyLocation', { defaultValue: 'Go to my location' });
            btn.innerHTML = '<i class="fa-solid fa-location-crosshairs"></i>';
            btn.style.cssText = 'width:34px;height:34px;line-height:30px;font-size:14px;cursor:pointer;background:#fff;display:flex;align-items:center;justify-content:center;';
            L.DomEvent.on(btn, 'click', L.DomEvent.stopPropagation)
                      .on(btn, 'click', L.DomEvent.preventDefault)
                      .on(btn, 'click', () => {
                          btn.disabled = true;
                          btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
                          navigator.geolocation.getCurrentPosition(pos => {
                              const { latitude, longitude } = pos.coords;
                              _userLocation = { lat: latitude, lon: longitude };
                              _changeStoreMarker.setLatLng([latitude, longitude]);
                              _changeStoreMap.setView([latitude, longitude], 16);
                              const latEl = document.getElementById('csLat');
                              const lngEl = document.getElementById('csLng');
                              if (latEl) latEl.value = latitude.toFixed(6);
                              if (lngEl) lngEl.value = longitude.toFixed(6);
                              reverseGeocodeStoreLocation(latitude, longitude);
                              btn.disabled = false;
                              btn.innerHTML = '<i class="fa-solid fa-location-crosshairs"></i>';
                          }, () => {
                              toast(t('dialogs.changeStore.locationUnavailable', { defaultValue: 'Location unavailable' }), 'error');
                              btn.disabled = false;
                              btn.innerHTML = '<i class="fa-solid fa-location-crosshairs"></i>';
                          }, { timeout: 8000 });
                      });
            return btn;
        }
    });
    new LocateControl().addTo(_changeStoreMap);

    setTimeout(() => _changeStoreMap && _changeStoreMap.invalidateSize(), 300);
}

function debounceNominatim() {
    clearTimeout(_nominatimTimer);
    const query = document.getElementById('nominatimSearch')?.value?.trim();
    const resultsEl = document.getElementById('nominatimResults');
    if (!query || query.length < 2) { if (resultsEl) resultsEl.style.display = 'none'; return; }
    _nominatimTimer = setTimeout(() => searchCombined(query), 400);
}

async function searchCombined(query) {
    const resultsEl = document.getElementById('nominatimResults');
    if (!resultsEl) return;
    resultsEl.style.display = 'block';
    resultsEl.innerHTML = `<div class="nominatim-loading"><i class="fa-solid fa-spinner fa-spin"></i> ${t('dialogs.changeStore.searching', { defaultValue: 'Searching...' })}</div>`;

    // Resolve user location (non-blocking; falls back gracefully)
    const loc = await _getUserLocation();

    // Build Nominatim URL — add viewbox around user location to prioritize nearby results
    let nominatimUrl = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=5`;
    if (loc) {
        // ~0.05° (~5 km) bounding box centred on user — prioritises nearby results without hard-restricting
        const d = 0.05;
        nominatimUrl += `&viewbox=${loc.lon - d},${loc.lat + d},${loc.lon + d},${loc.lat - d}&bounded=0`;
    }

    // Fire both searches in parallel
    const [dbResults, nominatimResults] = await Promise.all([
        api(`/api/stores/search?q=${encodeURIComponent(query)}`).catch(() => []),
        fetch(nominatimUrl,
            { headers: { 'Accept-Language': getActiveLocale(), 'User-Agent': 'ExpenseTracker/1.0' } })
            .then(r => r.json()).catch(() => [])
    ]);

    const safeDb = Array.isArray(dbResults) ? dbResults : [];
    const safeNom = Array.isArray(nominatimResults) ? nominatimResults : [];

    if (safeDb.length === 0 && safeNom.length === 0) {
        resultsEl.innerHTML = `<div class="nominatim-loading">${t('dialogs.changeStore.noResults', { defaultValue: 'No results found' })}</div>`;
        return;
    }

    window._dbStoreResults = safeDb;
    window._nominatimResults = safeNom;

    let html = '';
    if (safeDb.length > 0) {
        html += `<div class="nominatim-section-label"><i class="fa-solid fa-database"></i> ${t('dialogs.changeStore.yourStores', { defaultValue: 'Your stores' })}</div>`;
        html += safeDb.map((s, i) => {
            const sub = [s.address, s.city, s.country].filter(Boolean).join(', ');
            return `<div class="nominatim-result-item" onclick="selectDbStore(${i})">
                <div class="nominatim-result-name">${esc(s.name)}</div>
                ${sub ? `<div class="nominatim-result-sub">${esc(sub)}</div>` : ''}
            </div>`;
        }).join('');
    }
    if (safeNom.length > 0) {
        html += `<div class="nominatim-section-label"><i class="fa-solid fa-map-location-dot"></i> ${t('dialogs.changeStore.addressSearch', { defaultValue: 'Address search' })}</div>`;
        html += safeNom.map((r, i) => `
            <div class="nominatim-result-item" onclick="selectNominatimResult(${i})">
                <div class="nominatim-result-name">${esc(r.display_name || '')}</div>
            </div>`).join('');
    }
    resultsEl.innerHTML = html;
}

async function searchDbStores(query) { /* legacy stub — searchCombined handles both now */ }

function selectDbStore(index) {
    const results = window._dbStoreResults;
    if (!results || !results[index]) return;
    const s = results[index];
    const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.value = val || ''; };
    setVal('csName', s.name);
    setVal('csAddress', s.address);
    setVal('csCity', s.city);
    setVal('csCountry', s.country);
    setVal('csPostal', s.postalCode);
    setVal('csPhone', s.phoneNumber);
    setVal('csWebsite', s.website);
    setVal('csLat', s.latitude != null ? String(s.latitude) : '');
    setVal('csLng', s.longitude != null ? String(s.longitude) : '');
    if (s.latitude && s.longitude && _changeStoreMap && _changeStoreMarker) {
        _changeStoreMarker.setLatLng([s.latitude, s.longitude]);
        _changeStoreMap.setView([s.latitude, s.longitude], 16);
    }
    document.getElementById('nominatimResults').style.display = 'none';
    document.getElementById('nominatimSearch').value = s.name || '';
    window._nominatimPlaceId = null;
    window._nominatimSnapshot = null;
    _selectedDbStoreId = s.id; // remember so saveChangeStore can reuse this store directly
    toast(t('dialogs.changeStore.filledFromExisting', { defaultValue: 'Store filled from your existing stores' }), 'success');
}

async function searchNominatim(query) {
    const resultsEl = document.getElementById('nominatimResults');
    if (!resultsEl) return;
    resultsEl.style.display = 'block';
    resultsEl.innerHTML = `<div class="nominatim-loading"><i class="fa-solid fa-spinner fa-spin"></i> ${t('dialogs.changeStore.searching', { defaultValue: 'Searching...' })}</div>`;

    try {
        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=5`;
        const res = await fetch(url, { headers: { 'Accept-Language': getActiveLocale(), 'User-Agent': 'ExpenseTracker/1.0' } });
        const results = await res.json();

        if (!results || results.length === 0) {
            resultsEl.innerHTML = `<div class="nominatim-loading">${t('dialogs.changeStore.noResults', { defaultValue: 'No results found' })}</div>`;
            return;
        }

        resultsEl.innerHTML = results.map((r, i) => `
            <div class="nominatim-result-item" onclick="selectNominatimResult(${i})">
                <div class="nominatim-result-name">${esc(r.display_name || '')}</div>
            </div>`).join('');

        window._nominatimResults = results;
    } catch (err) {
        resultsEl.innerHTML = `<div class="nominatim-loading">${t('dialogs.changeStore.searchFailed', { defaultValue: 'Search failed. Check connection.' })}</div>`;
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
    toast(t('dialogs.changeStore.addressPopulated', { defaultValue: 'Address populated from search result' }), 'success');
}

async function saveChangeStore(expenseId, storeId) {
    // If user picked from DB store list, reuse that store's ID directly
    const effectiveStoreId = _selectedDbStoreId || storeId || null;

    let sourceId = null;
    if (!_selectedDbStoreId && window._nominatimPlaceId && window._nominatimSnapshot) {
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
        id: effectiveStoreId || null,
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
    toast(t('dialogs.changeStore.updated', { defaultValue: 'Store updated!' }), 'success');
    closeChangeStoreDialog();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

function closeChangeStoreDialog() {
    _unregisterEscHandler('changeStoreOverlay');
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
function openItemDialog(expenseId, itemId, itemName, quantity, unitPrice, adjustment) {
    if (!window._expenseIsOwner) return;
    const isEdit = !!itemId;
    const overlay = document.createElement('div');
    overlay.className = 'item-dialog-overlay';
    overlay.id = 'itemDialogOverlay';
    overlay.innerHTML = `
        <div class="item-dialog">
            <h3 class="item-dialog-title">${isEdit ? t('dialogs.item.editTitle', { defaultValue: 'Edit Item' }) : t('dialogs.item.addTitle', { defaultValue: 'Add Item' })}</h3>
            <div class="item-dialog-body">
            <div class="form-group">
                <label>${t('dialogs.item.itemName', { defaultValue: 'Item Name' })}</label>
                <input type="text" class="form-control" id="dlgItemName" value="${isEdit ? itemName : ''}" placeholder="${t('dialogs.item.itemNamePlaceholder', { defaultValue: 'Item name' })}">
            </div>
            <div class="form-row" style="display:grid; grid-template-columns:1fr 1fr 1fr; gap:0.75rem;">
                <div class="form-group">
                    <label>${t('dialogs.item.quantity', { defaultValue: 'Quantity' })}</label>
                    <input type="number" step="0.01" class="form-control" id="dlgItemQty" value="${isEdit ? quantity : 1}">
                </div>
                <div class="form-group">
                    <label>${t('dialogs.item.unitPrice', { defaultValue: 'Unit Price' })}</label>
                    <input type="number" step="0.01" class="form-control" id="dlgItemPrice" value="${isEdit ? unitPrice : ''}">
                </div>
                <div class="form-group">
                    <label>${t('dialogs.item.adjustment', { defaultValue: 'Adjustment' })} <span style="font-size:0.7rem; color:var(--text-light)">±</span></label>
                    <input type="number" step="0.01" class="form-control" id="dlgItemAdjustment" value="${isEdit && adjustment != null && adjustment != 0 ? adjustment : ''}" placeholder="0.00">
                </div>
            </div>
            </div>
            <div class="item-dialog-actions">
                <button class="btn btn-primary btn-sm" onclick="saveItemDialog('${expenseId}','${itemId || ''}')">
                    <i class="fa-solid fa-save"></i> ${isEdit ? t('common.save', { defaultValue: 'Save' }) : t('dialogs.item.add', { defaultValue: 'Add' })}
                </button>
                ${isEdit ? `<button class="btn btn-danger btn-sm" onclick="deleteItemDialog('${expenseId}','${itemId}')">
                    <i class="fa-solid fa-trash"></i> ${t('expenseDetail.delete', { defaultValue: 'Delete' })}
                </button>` : ''}
                <button class="btn btn-outline btn-sm" onclick="document.getElementById('itemDialogOverlay').remove()">
                    <i class="fa-solid fa-xmark"></i> ${t('common.cancel', { defaultValue: 'Cancel' })}
                </button>
            </div>
        </div>`;
    document.body.appendChild(overlay);
    document.getElementById('dlgItemName').focus();
    _registerEscHandler('itemDialogOverlay', () => document.getElementById('itemDialogOverlay')?.remove());
}

async function saveItemDialog(expenseId, itemId) {
    const name = document.getElementById('dlgItemName').value;
    const qty = parseFloat(document.getElementById('dlgItemQty').value);
    const price = parseFloat(document.getElementById('dlgItemPrice').value);
    const adjustmentVal = document.getElementById('dlgItemAdjustment').value;
    if (!name) { toast(t('dialogs.item.nameRequired', { defaultValue: 'Item name is required' }), 'error'); return; }

    const adjustment = adjustmentVal !== '' ? parseFloat(adjustmentVal) : null;
    const totalPrice = (qty > 0 && price >= 0) ? parseFloat((qty * price + (adjustment || 0)).toFixed(2)) : null;
    const item = { itemName: name, quantity: qty, unitPrice: price };
    if (adjustment != null) item.adjustment = adjustment;
    if (totalPrice != null) item.totalPrice = totalPrice;

    if (itemId) {
        await api(`/api/expenses/${expenseId}/items/${itemId}`, { method: 'PUT', body: item });
        toast(t('dialogs.item.updated', { defaultValue: 'Item updated' }), 'success');
    } else {
        await api(`/api/expenses/${expenseId}/items`, { method: 'POST', body: item });
        toast(t('dialogs.item.added', { defaultValue: 'Item added' }), 'success');
    }
    document.getElementById('itemDialogOverlay')?.remove();
    _unregisterEscHandler('itemDialogOverlay');
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

async function deleteItemDialog(expenseId, itemId) {
    if (!confirm(t('dialogs.item.deleteConfirm', { defaultValue: 'Delete this item?' }))) return;
    await api(`/api/expenses/${expenseId}/items/${itemId}`, { method: 'DELETE' });
    toast(t('dialogs.item.deleted', { defaultValue: 'Item deleted' }), 'success');
    document.getElementById('itemDialogOverlay')?.remove();
    _unregisterEscHandler('itemDialogOverlay');
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

    overlay.innerHTML = `
        <div class="change-store-dialog" id="expenseDetailsDialogInner">
            <div class="change-store-header" style="padding-top:1.5rem; padding-left:1.5rem; padding-right:1.5rem; flex-shrink:0;">
                <h3 class="item-dialog-title" style="margin-bottom:0"><i class="fa-solid fa-pen-to-square"></i> ${t('dialogs.expenseDetails.title', { defaultValue: 'Edit Expense Details' })}</h3>
                <button class="btn btn-outline btn-sm" onclick="closeExpenseDetailsDialog()"><i class="fa-solid fa-xmark"></i></button>
            </div>
            <div class="change-store-dialog-body">
                <div class="exp-dlg-fields-grid" style="margin-top:0.75rem;">
                    <div class="form-group exp-dlg-f-date"><label>${t('expenseDetail.dateTime', { defaultValue: 'Date & Time' })}</label>
                        <div class="detail-datetime-wrap">
                            <input type="text" class="form-control" id="dlgExpDateDisplay" value="${e.transactionDatetime ? esc(formatShortHumanDateTime(e.transactionDatetime)) : ''}" placeholder="${t('expenseDetail.dateTimePlaceholder', { defaultValue: 'e.g. Oct 13, 2026, 3:45 PM' })}" onblur="syncPickerDateTimeFromDisplay('dlgExpDateDisplay','dlgExpDate')">
                            <input type="datetime-local" id="dlgExpDate" value="${toDateTimeLocalValue(e.transactionDatetime)}" style="position:absolute; opacity:0; pointer-events:none; width:1px; height:1px;" onchange="syncDisplayDateTimeFromPicker('dlgExpDateDisplay','dlgExpDate')">
                            <button type="button" class="btn btn-outline btn-sm" onclick="openDateTimePicker('dlgExpDateDisplay','dlgExpDate')"><i class="fa-solid fa-calendar-days"></i></button>
                        </div>
                    </div>
                    <div class="form-group exp-dlg-f-cat"><label>${t('profile.trash.category', { defaultValue: 'Category' })}</label>
                        <input type="text" class="form-control" id="dlgExpCategory" value="${esc(e.category)}" list="dlgExpCategoryList" autocomplete="off">
                        <datalist id="dlgExpCategoryList">
                            ${allCats.map(c => `<option value="${esc(c)}">`).join('')}
                        </datalist>
                    </div>
                    <div class="form-group exp-dlg-f-amt"><label>${t('profile.trash.amount', { defaultValue: 'Amount' })}</label>
                        <input type="number" step="0.01" class="form-control" id="dlgExpAmount" value="${e.amount || ''}">
                    </div>
                    <div class="form-group exp-dlg-f-curr"><label>${t('auth.baseCurrency', { defaultValue: 'Currency' })}</label>
                        <input type="text" class="form-control" id="dlgExpCurrency" list="dlgExpCurrencyList" value="${esc(e.currency)}" placeholder="${t('dialogs.expenseDetails.currencyPlaceholder', { defaultValue: 'e.g. USD' })}">
                        <datalist id="dlgExpCurrencyList">
                            ${['USD','EUR','GBP','SGD','JPY','AUD','CAD','CHF'].map(c => `<option value="${c}"></option>`).join('')}
                        </datalist>
                    </div>
                    <div class="form-group exp-dlg-f-exr"><label>${t('expenseDetail.exchangeRate', { defaultValue: 'Exchange Rate' })}</label>
                        <input type="number" step="0.000001" class="form-control" id="dlgExpExRate" value="${e.exchangeRate || ''}" placeholder="${t('dialogs.expenseDetails.auto', { defaultValue: 'Auto' })}">
                    </div>
                </div>
                <div class="form-group" style="margin-top:0.75rem;">
                    <label>${t('expenseDetail.receiptNumber', { defaultValue: 'Receipt Number' })}</label>
                    <input type="text" class="form-control" id="dlgExpReceipt" value="${esc(e.receiptNumber)}">
                </div>
                <div class="form-group"><label>${t('expenseDetail.tags', { defaultValue: 'Tags' })}</label>
                    <div class="tags-container" id="dlgExpTagsContainer">
                        <input type="text" class="tag-input" id="dlgExpTagInput" placeholder="${t('expenseDetail.addTag', { defaultValue: 'Add tag...' })}">
                    </div>
                </div>
                <div class="form-group"><label>${t('expenseDetail.notes', { defaultValue: 'Notes' })}</label>
                    <textarea class="form-control" id="dlgExpNotes" rows="2">${esc(e.notes)}</textarea>
                </div>
            </div>
            <div class="item-dialog-actions">
                <button class="btn btn-primary" onclick="saveExpenseDetailsDialog('${expenseId}')">
                    <i class="fa-solid fa-save"></i> ${t('common.save', { defaultValue: 'Save' })}
                </button>
                <button class="btn btn-outline" onclick="closeExpenseDetailsDialog()">
                    <i class="fa-solid fa-xmark"></i> ${t('common.cancel', { defaultValue: 'Cancel' })}
                </button>
            </div>
        </div>`;

    document.body.appendChild(overlay);
    renderTags('dlgExpTagsContainer', 'dlgExpTagInput', window._dlgExpTags);
    if (typeof bindExchangeRateRefreshOnCurrencyChange === 'function') {
        bindExchangeRateRefreshOnCurrencyChange('dlgExpCurrency', 'dlgExpExRate');
    }
    _registerEscHandler('expenseDetailsOverlay', closeExpenseDetailsDialog);
}

async function saveExpenseDetailsDialog(expenseId) {
    commitPendingTag('dlgExpTagInput', window._dlgExpTags || []);
    const transactionDatetime = getDateTimeForApi('dlgExpDateDisplay', 'dlgExpDate');
    const updates = {
        transactionDatetime,
        amount: parseFloat(document.getElementById('dlgExpAmount').value),
        currency: document.getElementById('dlgExpCurrency').value,
        category: document.getElementById('dlgExpCategory').value,
        receiptNumber: document.getElementById('dlgExpReceipt').value,
        notes: document.getElementById('dlgExpNotes').value,
        tags: window._dlgExpTags || []
    };
    const exRate = (document.getElementById('dlgExpExRate').value || '').trim();
    if (exRate) updates.exchangeRate = parseFloat(exRate);

    await api(`/api/expenses/${expenseId}`, { method: 'PUT', body: updates });
    toast(t('expenseDetail.updated', { defaultValue: 'Expense updated!' }), 'success');
    window._allExpenseCategories = null;
    closeExpenseDetailsDialog();
    renderExpenseDetail(document.getElementById('app'), expenseId);
}

function closeExpenseDetailsDialog() {
    _unregisterEscHandler('expenseDetailsOverlay');
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

    const hasNativeShare = typeof navigator.share === 'function';

    menu.innerHTML = `
        <div class="share-menu-item" onclick="copyExpenseLink('${expenseId}', true)">
            <i class="fa-solid fa-link"></i> ${t('dialogs.share.copyLink', { defaultValue: 'Copy link' })}
        </div>
        ${hasNativeShare ? `<div class="share-menu-item" onclick="nativeShareExpense('${expenseId}', true)">
            <i class="fa-solid fa-share-nodes"></i> ${t('expenseDetail.share', { defaultValue: 'Share' })}
        </div>` : ''}
        <div class="share-menu-item" onclick="revokeExpenseShare('${expenseId}', true)">
            <i class="fa-solid fa-ban"></i> ${t('dialogs.share.revokeShareLink', { defaultValue: 'Revoke share link' })}
        </div>
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

function refreshExpenseShareUI(expenseId) {
    if (window.location.hash === `#/expenses/${expenseId}`) {
        renderExpenseDetail(document.getElementById('app'), expenseId);
    }
}

function revealExpenseShareStatusCard(expenseId) {
    if (typeof showExpenseShareStatusCard === 'function') {
        showExpenseShareStatusCard(expenseId);
    }
}

async function copyTextToClipboard(text) {
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        await navigator.clipboard.writeText(text);
        return;
    }

    const input = document.createElement('textarea');
    input.value = text;
    input.setAttribute('readonly', 'readonly');
    input.style.position = 'fixed';
    input.style.opacity = '0';
    document.body.appendChild(input);
    input.select();
    document.execCommand('copy');
    input.remove();
}

async function createExpenseShare(expenseId, refreshAfter = false, ttlDays = null) {
    revealExpenseShareStatusCard(expenseId);
    const body = ttlDays ? { ttlDays } : {};
    const created = await api(`/api/expenses/${expenseId}/share`, { method: 'POST', body });
    if (created && created.active && created.shareUrl) {
        toast(t('dialogs.share.linkReady', { defaultValue: 'Share link ready' }), 'success');
        if (refreshAfter) refreshExpenseShareUI(expenseId);
        return created;
    }

    toast(t('dialogs.share.createFailed', { defaultValue: 'Could not create share link' }), 'error');
    return null;
}

async function getOrCreateExpenseShareLink(expenseId) {
    const existing = await api(`/api/expenses/${expenseId}/share`, { noAuthRedirect: true });
    if (existing && existing.active && existing.shareUrl) {
        return { url: getAbsoluteShareUrl(existing.shareUrl), created: false };
    }

    const created = await createExpenseShare(expenseId, true);
    return created && created.shareUrl
        ? { url: getAbsoluteShareUrl(created.shareUrl), created: true }
        : { url: null, created: false };
}

async function copyExpenseLink(expenseId, refreshAfter = false) {
    closeShareMenu();
    revealExpenseShareStatusCard(expenseId);
    try {
        const { url, created } = await getOrCreateExpenseShareLink(expenseId);
        if (!url) throw new Error('share-link-missing');
        await copyTextToClipboard(url);
        if (refreshAfter || created) refreshExpenseShareUI(expenseId);
        toast(created
            ? t('dialogs.share.createdAndCopied', { defaultValue: 'Share link created and copied!' })
            : t('dialogs.share.linkCopied', { defaultValue: 'Link copied!' }), 'success');
    } catch {
        if (refreshAfter) refreshExpenseShareUI(expenseId);
        toast(t('dialogs.share.copyFailed', { defaultValue: 'Could not copy link' }), 'error');
    }
}

async function nativeShareExpense(expenseId, refreshAfter = false) {
    closeShareMenu();
    revealExpenseShareStatusCard(expenseId);
    try {
        const { url, created } = await getOrCreateExpenseShareLink(expenseId);
        if (!url) throw new Error('share-link-missing');
        if (refreshAfter || created) refreshExpenseShareUI(expenseId);
        await navigator.share({ title: 'Expense', url });
    } catch (err) {
        if (refreshAfter && err?.name !== 'AbortError') refreshExpenseShareUI(expenseId);
        if (err.name !== 'AbortError') toast(t('dialogs.share.failed', { defaultValue: 'Share failed' }), 'error');
    }
}

async function revokeExpenseShare(expenseId, refreshAfter = false) {
    closeShareMenu();
    revealExpenseShareStatusCard(expenseId);
    const result = await api(`/api/expenses/${expenseId}/share`, { method: 'DELETE' });
    if (result && result.active === false) {
        if (refreshAfter) refreshExpenseShareUI(expenseId);
        toast(t('dialogs.share.revoked', { defaultValue: 'Share link revoked' }), 'success');
    } else {
        if (refreshAfter) refreshExpenseShareUI(expenseId);
        toast(t('dialogs.share.noActiveLink', { defaultValue: 'No active share link' }), 'info');
    }
}
