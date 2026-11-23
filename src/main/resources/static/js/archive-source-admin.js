(() => {
    const API = '/api/archive/sources';
    const modal = document.getElementById('archiveSourceManageModal');
    // 필터 갱신 시 .ar-source-tabs(버튼 포함)가 교체되므로 버튼은 위임으로 연다.
    if (!modal) return;

    const listEl = document.getElementById('archiveSourceAdminList');
    const form = document.getElementById('archiveSourceAdminForm');
    const errorEl = document.getElementById('archiveSourceAdminError');
    const saveBtn = document.getElementById('archiveSourceAdminSave');
    const codeInput = form?.querySelector('[name="code"]');
    const labelInput = form?.querySelector('[name="label"]');
    const colorInput = form?.querySelector('[name="color"]');
    const sortInput = form?.querySelector('[name="sortOrder"]');
    const visibleInput = form?.querySelector('[name="visible"]');
    const idInput = form?.querySelector('[name="id"]');

    let rows = [];

    /** CookieCsrfTokenRepository: 헤더에는 쿠키 raw 토큰을 보낸다(meta XOR 값은 거부됨). */
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

    function showError(message) {
        if (!errorEl) return;
        errorEl.hidden = !message;
        errorEl.textContent = message || '';
    }

    function resetForm() {
        if (!form) return;
        idInput.value = '';
        codeInput.value = '';
        codeInput.disabled = false;
        labelInput.value = '';
        colorInput.value = '#00f5d4';
        sortInput.value = '';
        visibleInput.checked = true;
        saveBtn.textContent = '추가';
        showError('');
    }

    function fillForm(row) {
        idInput.value = String(row.id);
        codeInput.value = row.code || '';
        codeInput.disabled = true;
        labelInput.value = row.label || '';
        colorInput.value = row.color || '#00f5d4';
        sortInput.value = row.sortOrder == null ? '' : String(row.sortOrder);
        visibleInput.checked = row.visible !== false;
        saveBtn.textContent = '수정';
        showError('');
    }

    function renderList() {
        if (!listEl) return;
        listEl.replaceChildren();
        rows.forEach(row => {
            const item = document.createElement('li');
            item.className = 'ar-source-admin-item' + (row.visible ? '' : ' is-hidden');
            item.dataset.id = String(row.id);

            const swatch = document.createElement('span');
            swatch.className = 'ar-source-admin-swatch';
            swatch.style.setProperty('--source-color', row.color || '#00f5d4');

            const meta = document.createElement('button');
            meta.type = 'button';
            meta.className = 'ar-source-admin-pick';
            meta.innerHTML = `<strong>${escapeHtml(row.label)}</strong><span>${escapeHtml(row.code)}</span>`;
            meta.addEventListener('click', () => fillForm(row));

            const del = document.createElement('button');
            del.type = 'button';
            del.className = 'site-btn site-btn--danger site-btn--sm';
            del.textContent = '삭제';
            del.addEventListener('click', () => removeRow(row));

            item.append(swatch, meta, del);
            listEl.appendChild(item);
        });
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;');
    }

    async function loadRows() {
        const res = await fetch(`${API}?all=1`, { credentials: 'same-origin' });
        if (!res.ok) {
            throw new Error(await readError(res, '출처 목록을 불러오지 못했습니다.'));
        }
        rows = await res.json();
        renderList();
    }

    async function readError(res, fallback) {
        try {
            const text = await res.text();
            if (!text) return fallback;
            try {
                const json = JSON.parse(text);
                return json.message || json.error || text || fallback;
            } catch {
                return text || fallback;
            }
        } catch {
            return fallback;
        }
    }

    async function openModal() {
        modal.hidden = false;
        document.body.classList.add('ar-dialog-open');
        resetForm();
        showError('');
        try {
            await loadRows();
        } catch (err) {
            showError(err.message || '출처 목록을 불러오지 못했습니다.');
        }
    }

    function closeModal() {
        modal.hidden = true;
        document.body.classList.remove('ar-dialog-open');
        showError('');
    }

    async function removeRow(row) {
        if (!window.confirm(`「${row.label}」출처를 삭제할까요?`)) return;
        const res = await fetch(`${API}/${row.id}`, {
            method: 'DELETE',
            credentials: 'same-origin',
            headers: csrfHeaders(),
        });
        if (!res.ok) {
            showError(await readError(res, '삭제하지 못했습니다.'));
            return;
        }
        await loadRows();
        if (idInput.value === String(row.id)) resetForm();
        window.location.reload();
    }

    document.addEventListener('click', (event) => {
        const btn = event.target.closest('#archiveSourceManageBtn');
        if (!btn) return;
        openModal().catch(err => showError(err.message || '열지 못했습니다.'));
    });

    modal.querySelectorAll('[data-source-admin-close], .ar-dialog-backdrop').forEach(el => {
        el.addEventListener('click', closeModal);
    });

    form?.querySelector('[data-source-admin-reset]')?.addEventListener('click', resetForm);

    form?.addEventListener('submit', async event => {
        event.preventDefault();
        showError('');
        const id = idInput.value.trim();
        const payload = {
            label: labelInput.value.trim(),
            color: colorInput.value,
            sortOrder: sortInput.value === '' ? null : Number(sortInput.value),
            visible: visibleInput.checked,
        };
        if (!id) payload.code = codeInput.value.trim();
        const res = await fetch(id ? `${API}/${id}` : API, {
            method: id ? 'PUT' : 'POST',
            credentials: 'same-origin',
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify(payload),
        });
        if (!res.ok) {
            showError(await readError(res, '저장하지 못했습니다.'));
            return;
        }
        window.location.reload();
    });
})();
