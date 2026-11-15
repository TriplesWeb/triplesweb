(function () {
    const detail = document.getElementById('archiveDetail');
    if (!detail) return;
    const image = document.getElementById('archiveDetailImage');
    const title = document.getElementById('archiveDetailTitle');
    const date = document.getElementById('archiveDetailDate');
    const members = document.getElementById('archiveDetailMembers');
    const original = document.getElementById('archiveDetailOriginal');
    const edit = document.getElementById('archiveDetailEdit');
    const remove = document.getElementById('archiveDetailDelete');

    function fill(card) {
        const url = card.dataset.url || '';
        const name = card.dataset.title || '사진';
        if (image) {
            image.src = url;
            image.alt = name;
        }
        if (title) title.textContent = name;
        if (date) {
            date.hidden = !card.dataset.registered;
            const label = document.querySelector('.archive-page')?.dataset.dateLabel || '날짜';
            date.textContent = card.dataset.registered ? label + ' ' + card.dataset.registered : '';
        }
        if (members) {
            const source = card.querySelector('.ar-card-members') || card.querySelector('.ar-meta');
            members.replaceChildren();
            if (source) members.innerHTML = source.innerHTML;
        }
        if (original) {
            original.href = url || '#';
            original.hidden = !url;
        }
        [edit, remove].forEach(button => {
            if (!button) return;
            button.dataset.id = card.dataset.id || '';
            button.dataset.title = name;
            button.dataset.artists = card.dataset.artists || '';
            button.dataset.url = url;
            button.dataset.registered = card.dataset.registered || '';
        });
    }

    edit?.addEventListener('click', () => {
        detail.hidden = true;
        if (!document.querySelector('.ar-dialog:not([hidden])')) document.body.style.overflow = '';
    });

    document.addEventListener('click', event => {
        const opener = event.target.closest('[data-archive-open]');
        if (!opener) return;
        event.preventDefault();
        const card = opener.closest('[data-id]');
        if (!card) return;
        fill(card);
        detail.hidden = false;
        document.body.style.overflow = 'hidden';
        detail.querySelector('[data-ar-close]')?.focus();
    });
})();
