(() => {
    const API = '/api/archive/bulk-source';
    const toggleBtn = document.getElementById('archiveSourceBulkBtn');
    const bar = document.getElementById('archiveSourceBulkBar');
    const countEl = document.getElementById('archiveSourceBulkCount');
    const selectEl = document.getElementById('archiveSourceBulkSelect');
    const applyBtn = document.getElementById('archiveSourceBulkApply');
    const cancelBtn = document.getElementById('archiveSourceBulkCancel');
    if (!toggleBtn || !bar || !selectEl || !applyBtn || !cancelBtn) return;

    const selected = new Set();
    let active = false;
    let applying = false;

    function grid() {
        return document.getElementById('archiveGrid');
    }

    function csrfHeaders(extra) {
        const base = extra && typeof extra === 'object' ? { ...extra } : {};
        if (window.AboutAdminAuth && typeof window.AboutAdminAuth.applyAuthHeaders === 'function') {
            const headers = window.AboutAdminAuth.applyAuthHeaders(base);
            const out = {};
            headers.forEach((value, key) => {
                out[key] = value;
            });
            return out;
        }
        const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        if (match) {
            try {
                base['X-XSRF-TOKEN'] = decodeURIComponent(match[1]);
            } catch {
                base['X-XSRF-TOKEN'] = match[1];
            }
        }
        return base;
    }

    function syncSelectionUi() {
        const root = grid();
        if (!root) return;
        root.querySelectorAll('.ar-card[data-id]').forEach(card => {
            const id = card.dataset.id;
            card.classList.toggle('is-bulk-selected', !!(id && selected.has(id)));
        });
        const n = selected.size;
        if (countEl) countEl.textContent = n + '개 선택';
        applyBtn.disabled = applying || n === 0 || !selectEl.value;
    }

    function enterMode() {
        active = true;
        selected.clear();
        toggleBtn.setAttribute('aria-pressed', 'true');
        bar.hidden = false;
        grid()?.classList.add('is-source-edit');
        syncSelectionUi();
    }

    function exitMode() {
        active = false;
        selected.clear();
        applying = false;
        toggleBtn.setAttribute('aria-pressed', 'false');
        bar.hidden = true;
        selectEl.value = '';
        applyBtn.disabled = true;
        const root = grid();
        if (root) {
            root.classList.remove('is-source-edit');
            root.querySelectorAll('.ar-card.is-bulk-selected').forEach(card => {
                card.classList.remove('is-bulk-selected');
            });
        }
        if (countEl) countEl.textContent = '0개 선택';
    }

    function toggleCard(card) {
        const id = card?.dataset?.id;
        if (!id) return;
        if (selected.has(id)) selected.delete(id);
        else selected.add(id);
        syncSelectionUi();
    }

    function softRefresh() {
        if (typeof window.refreshArchiveGrid === 'function') {
            return window.refreshArchiveGrid().catch(() => {
                window.location.reload();
            });
        }
        window.location.reload();
        return Promise.resolve();
    }

    toggleBtn.addEventListener('click', () => {
        if (active) exitMode();
        else enterMode();
    });

    cancelBtn.addEventListener('click', () => exitMode());

    selectEl.addEventListener('change', () => syncSelectionUi());

    document.addEventListener('click', (event) => {
        if (!active || applying) return;
        const root = grid();
        if (!root || !root.classList.contains('is-source-edit')) return;
        const card = event.target.closest?.('#archiveGrid .ar-card[data-id]');
        if (!card || !root.contains(card)) return;
        event.preventDefault();
        event.stopPropagation();
        toggleCard(card);
    }, true);

    applyBtn.addEventListener('click', async () => {
        if (applying) return;
        const ids = [...selected].map((id) => Number(id)).filter((id) => Number.isFinite(id) && id > 0);
        const source = selectEl.value?.trim();
        if (!ids.length) {
            window.SiteFeedback?.alert?.('자료를 선택해 주세요.');
            return;
        }
        if (!source) {
            window.SiteFeedback?.alert?.('출처를 선택해 주세요.');
            return;
        }
        applying = true;
        applyBtn.disabled = true;
        try {
            const response = await fetch(API, {
                method: 'POST',
                credentials: 'same-origin',
                headers: csrfHeaders({ 'Content-Type': 'application/json', Accept: 'application/json' }),
                body: JSON.stringify({ ids, source }),
            });
            if (!response.ok) {
                let message = '출처를 바꾸지 못했습니다.';
                try {
                    const text = await response.text();
                    if (text) {
                        try {
                            const json = JSON.parse(text);
                            message = json.detail || json.message || message;
                        } catch {
                            message = text.length < 200 ? text : message;
                        }
                    }
                } catch {
                    /* ignore */
                }
                window.SiteFeedback?.alert?.(message);
                applying = false;
                syncSelectionUi();
                return;
            }
            exitMode();
            await softRefresh();
        } catch {
            window.SiteFeedback?.alert?.('네트워크 오류로 출처를 바꾸지 못했습니다.');
            applying = false;
            syncSelectionUi();
        }
    });

    function onArchiveUpdated() {
        if (!active) return;
        const root = grid();
        if (!root) {
            exitMode();
            return;
        }
        root.classList.add('is-source-edit');
        const present = new Set();
        root.querySelectorAll('.ar-card[data-id]').forEach(card => {
            if (card.dataset.id) present.add(card.dataset.id);
        });
        for (const id of [...selected]) {
            if (!present.has(id)) selected.delete(id);
        }
        syncSelectionUi();
    }

    // archive-browse는 그리드에 bubble:false로 디스패치하므로 그리드에서 직접 듣는다.
    grid()?.addEventListener('archive:updated', onArchiveUpdated);
})();
