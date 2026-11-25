(() => {
    const mount = document.getElementById('archiveAdminMount');
    const page = document.querySelector('[data-archive-kind]');
    if (!mount || !page) return;

    const scripts = [...mount.querySelectorAll('[data-admin-script]')].map(node => node.dataset.adminScript);
    const loadedScripts = new Set();
    let loading;

    async function loadAdmin() {
        if (!mount.querySelector('#archiveAdminForms')) {
            const params = new URLSearchParams({
                type: page.dataset.archiveKind,
                folderHome: String(page.classList.contains('is-folder-home'))
            });
            const response = await fetch('/archive/admin-forms?' + params, {
                headers: window.AboutAdminAuth?.applyAuthHeaders({})
            });
            if (!response.ok) throw new Error('Admin forms: ' + response.status);
            const parsed = new DOMParser().parseFromString(await response.text(), 'text/html');
            const forms = parsed.getElementById('archiveAdminForms');
            if (!forms) throw new Error('Admin forms missing');

            const bulk = forms.querySelector('#archiveSourceBulkBar');
            if (bulk) document.getElementById('archiveBulkMount')?.replaceWith(bulk);
            mount.replaceChildren(forms);
            window.FilePicker?.init(mount);
        }

        for (const src of scripts) {
            if (loadedScripts.has(src)) continue;
            await new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = src;
                script.onload = resolve;
                script.onerror = () => {
                    script.remove();
                    reject(new Error('Admin script failed to load'));
                };
                document.head.append(script);
            });
            loadedScripts.add(src);
        }
    }

    const actions = '[data-archive-add], [data-archive-edit], [data-archive-delete], #archiveSourceManageBtn, #archiveSourceBulkBtn';
    let ready = false;
    document.addEventListener('click', async event => {
        const button = event.target.closest(actions);
        if (!button || !document.body.classList.contains('site-admin-enabled')) return;

        if (ready) {
            if (button.matches('[data-archive-add]')) window.showAddModal();
            return;
        }

        event.preventDefault();
        event.stopImmediatePropagation();
        if (loading) return;
        try {
            loading = loadAdmin();
            await loading;
            ready = true;
            if (button.isConnected) button.click();
        } catch {
            window.SiteFeedback.alert('관리 도구를 불러오지 못했습니다. 다시 시도해 주세요.');
        } finally {
            loading = undefined;
        }
    }, true);
})();
