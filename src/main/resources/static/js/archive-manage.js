(function () {
    const csrf = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    const headerMap = {};
    if (csrf && csrfHeader && csrf.content && csrfHeader.content) {
        headerMap[csrfHeader.content] = csrf.content;
    }
    const headers = window.AboutAdminAuth ? AboutAdminAuth.applyAuthHeaders(headerMap) : headerMap;

    function showEdit(button) {
        const form = document.getElementById('archiveEditForm');
        const modal = document.getElementById('editModal');
        if (!form || !modal || typeof bootstrap === 'undefined') return;
        form.reset();
        form.querySelector('[name="title"]').value = button.dataset.title || '';
        form.querySelector('[name="artistNames"]').value = button.dataset.artists || '';
        form.dataset.id = button.dataset.id;
        const preview = document.getElementById('editPreview');
        if (preview) preview.innerHTML = '';
        bootstrap.Modal.getOrCreateInstance(modal).show();
    }

    async function removeArchive(id) {
        if (!confirm('이 자료를 삭제할까요? 삭제하면 되돌릴 수 없습니다.')) return;
        const res = await fetch('/archive/' + id, { method: 'DELETE', headers, credentials: 'same-origin' });
        if (res.ok) {
            location.reload();
            return;
        }
        alert(res.status === 403 ? '관리자만 삭제할 수 있습니다.' : '삭제에 실패했습니다.');
    }

    document.addEventListener('click', function (event) {
        const edit = event.target.closest('[data-archive-edit]');
        if (edit) {
            showEdit(edit);
            return;
        }
        const remove = event.target.closest('[data-archive-delete]');
        if (remove) removeArchive(remove.dataset.id);
    });

    const editForm = document.getElementById('archiveEditForm');
    if (editForm) {
        editForm.addEventListener('submit', async function (event) {
            event.preventDefault();
            const body = new FormData(editForm);
            const file = body.get('file');
            if (file instanceof File && file.size === 0) body.delete('file');
            const res = await fetch('/archive/' + editForm.dataset.id, { method: 'POST', body, headers, credentials: 'same-origin' });
            if (res.ok) {
                location.reload();
                return;
            }
            const message = await res.text();
            alert(res.status === 403 ? '관리자만 수정할 수 있습니다.' : (message || '수정에 실패했습니다.'));
        });
    }
})();
