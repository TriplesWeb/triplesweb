(function () {
    const detail = document.getElementById('archiveDetail');
    if (!detail) return;
    const image = document.getElementById('archiveDetailImage');
    const title = document.getElementById('archiveDetailTitle');
    const date = document.getElementById('archiveDetailDate');
    const source = document.getElementById('archiveDetailSource');
    const members = document.getElementById('archiveDetailMembers');
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
        if (source) {
            const label = card.dataset.sourceLabel || '기타/팬메이드';
            source.hidden = false;
            source.dataset.source = (card.dataset.source || '').toLowerCase();
            source.textContent = label;
        }
        if (members) {
            const chips = card.querySelector('.ar-card-members');
            members.replaceChildren();
            if (chips) members.innerHTML = chips.innerHTML;
            members.dataset.memberCount = String(members.querySelectorAll('.unit-member-chip').length);
            window.paintArchiveChemistry?.(members);
        }
        [edit, remove].forEach(button => {
            if (!button) return;
            button.dataset.id = card.dataset.id || '';
            button.dataset.title = name;
            button.dataset.artists = card.dataset.artists || '';
            button.dataset.url = url;
            button.dataset.registered = card.dataset.registered || '';
            button.dataset.source = card.dataset.source || '';
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
