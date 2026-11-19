(function () {
    const detail = document.getElementById('archiveDetail');
    if (!detail) return;
    const photo = document.getElementById('archiveDetailPhoto') || detail.querySelector('.ar-detail-photo');
    const image = document.getElementById('archiveDetailImage');
    const title = document.getElementById('archiveDetailTitle');
    const date = document.getElementById('archiveDetailDate');
    const source = document.getElementById('archiveDetailSource');
    const members = document.getElementById('archiveDetailMembers');
    const edit = document.getElementById('archiveDetailEdit');
    const remove = document.getElementById('archiveDetailDelete');
    const prev = document.getElementById('archiveDetailPrev');
    const next = document.getElementById('archiveDetailNext');
    const dots = document.getElementById('archiveDetailDots');
    const count = document.getElementById('archiveDetailCount');
    const maxDots = 12;
    let index = 0;
    let swipeX = null;
    let swipeY = null;

    function cards() {
        return [...document.querySelectorAll('#archiveGrid .ar-card:not(.ar-off)')]
            .filter(card => card.querySelector('[data-archive-open]'));
    }

    function fill(card) {
        if (!card) return;
        const url = card.dataset.url || '';
        const headline = card.dataset.headline || card.dataset.description || card.dataset.title || '사진';
        if (image) {
            image.src = url;
            image.alt = headline;
        }
        if (title) title.textContent = headline;
        if (date) {
            date.hidden = !card.dataset.registered;
            const label = document.querySelector('.archive-page')?.dataset.dateLabel || '날짜';
            date.textContent = card.dataset.registered ? label + ' ' + card.dataset.registered : '';
        }
        if (source) {
            const label = (card.dataset.sourceLabel || '').trim();
            source.hidden = !label;
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
            button.dataset.title = card.dataset.title || '';
            button.dataset.description = card.dataset.description || '';
            button.dataset.artists = card.dataset.artists || '';
            button.dataset.url = url;
            button.dataset.registered = card.dataset.registered || '';
            button.dataset.source = card.dataset.source || '';
        });
    }

    function paintNav() {
        const list = cards();
        const multi = list.length > 1;
        if (prev) {
            prev.hidden = !multi;
            prev.disabled = index <= 0;
        }
        if (next) {
            next.hidden = !multi;
            next.disabled = index >= list.length - 1;
        }
        if (count) {
            count.hidden = !multi;
            count.textContent = multi ? (index + 1) + ' / ' + list.length : '';
        }
        if (dots) {
            const showDots = multi && list.length <= maxDots;
            dots.hidden = !showDots;
            dots.replaceChildren();
            if (showDots) {
                list.forEach((_, i) => {
                    const dot = document.createElement('button');
                    dot.type = 'button';
                    dot.className = 'ar-detail-dot' + (i === index ? ' is-active' : '');
                    dot.setAttribute('aria-label', (i + 1) + '번째 사진');
                    if (i === index) dot.setAttribute('aria-current', 'true');
                    dot.addEventListener('click', () => goTo(i));
                    dots.appendChild(dot);
                });
            }
        }
        detail.classList.toggle('has-detail-nav', multi);
    }

    function goTo(nextIndex) {
        const list = cards();
        if (!list.length) return;
        index = Math.max(0, Math.min(list.length - 1, nextIndex));
        fill(list[index]);
        paintNav();
    }

    function open(card) {
        const list = cards();
        index = Math.max(0, list.indexOf(card));
        if (index < 0) index = 0;
        fill(list[index] || card);
        paintNav();
        detail.hidden = false;
        document.body.style.overflow = 'hidden';
        detail.querySelector('[data-ar-close]')?.focus();
    }

    edit?.addEventListener('click', () => {
        detail.hidden = true;
        if (!document.querySelector('.ar-dialog:not([hidden])')) document.body.style.overflow = '';
    });

    prev?.addEventListener('click', event => {
        event.preventDefault();
        goTo(index - 1);
    });
    next?.addEventListener('click', event => {
        event.preventDefault();
        goTo(index + 1);
    });

    document.addEventListener('keydown', event => {
        if (detail.hidden) return;
        if (event.target && /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)) return;
        if (event.key === 'ArrowLeft') {
            event.preventDefault();
            goTo(index - 1);
        } else if (event.key === 'ArrowRight') {
            event.preventDefault();
            goTo(index + 1);
        }
    });

    if (photo) {
        photo.addEventListener('touchstart', event => {
            if (event.touches.length !== 1) return;
            swipeX = event.touches[0].clientX;
            swipeY = event.touches[0].clientY;
        }, { passive: true });
        photo.addEventListener('touchend', event => {
            if (swipeX == null || event.changedTouches.length !== 1) {
                swipeX = null;
                swipeY = null;
                return;
            }
            const dx = event.changedTouches[0].clientX - swipeX;
            const dy = event.changedTouches[0].clientY - swipeY;
            swipeX = null;
            swipeY = null;
            if (Math.abs(dx) < 48 || Math.abs(dx) < Math.abs(dy)) return;
            goTo(index + (dx < 0 ? 1 : -1));
        }, { passive: true });
    }

    document.addEventListener('click', event => {
        const opener = event.target.closest('[data-archive-open]');
        if (!opener) return;
        event.preventDefault();
        const card = opener.closest('[data-id]');
        if (!card) return;
        open(card);
    });
})();
