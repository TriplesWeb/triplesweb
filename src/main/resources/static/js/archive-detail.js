(function () {
    const detail = document.getElementById('archiveDetail');
    if (!detail) return;
    const photo = document.getElementById('archiveDetailPhoto') || detail.querySelector('.ar-detail-photo');
    const image = document.getElementById('archiveDetailImage');
    const description = document.getElementById('archiveDetailDescription');
    const meta = document.getElementById('archiveDetailMeta');
    const date = document.getElementById('archiveDetailDate');
    const dateLabel = date?.querySelector('[data-detail-date-label]');
    const dateValue = date?.querySelector('[data-detail-date-value]');
    const source = document.getElementById('archiveDetailSource');
    const cast = detail.querySelector('.ar-detail-cast');
    const members = document.getElementById('archiveDetailMembers');
    const edit = document.getElementById('archiveDetailEdit');
    const remove = document.getElementById('archiveDetailDelete');
    const prev = document.getElementById('archiveDetailPrev');
    const next = document.getElementById('archiveDetailNext');
    const dots = document.getElementById('archiveDetailDots');
    const count = document.getElementById('archiveDetailCount');
    let activeCard = null;
    let urls = [];
    let index = 0;
    let swipeX = null;
    let swipeY = null;

    function parseImages(card) {
        if (!card) return [];
        const raw = (card.dataset.images || '').trim();
        if (raw) {
            try {
                const parsed = JSON.parse(raw);
                if (Array.isArray(parsed)) {
                    return parsed.map(url => String(url || '').trim()).filter(Boolean);
                }
            } catch (_) {
                /* fall through */
            }
        }
        const single = (card.dataset.url || '').trim();
        return single ? [single] : [];
    }

    function descriptionText(card) {
        if (!card) return '';
        return String(card.dataset.description || card.dataset.title || '').trim();
    }

    function fillMeta(card) {
        if (!card) return;
        const desc = descriptionText(card);
        if (description) {
            description.hidden = !desc;
            description.textContent = desc;
        }
        const registered = (card.dataset.registered || '').trim();
        if (date) {
            date.hidden = !registered;
            const label = document.querySelector('.archive-page')?.dataset.dateLabel || '날짜';
            if (dateLabel) dateLabel.textContent = label;
            if (dateValue) {
                dateValue.dateTime = registered;
                dateValue.textContent = registered;
            }
        }
        if (source) {
            const label = (card.dataset.sourceLabel || '').trim();
            source.hidden = !label;
            source.dataset.source = (card.dataset.source || '').toLowerCase();
            source.textContent = label;
        }
        if (meta) meta.hidden = Boolean(date?.hidden) && Boolean(source?.hidden);
        if (members) {
            const chips = card.querySelector('.ar-card-members');
            members.replaceChildren();
            if (chips) members.innerHTML = chips.innerHTML;
            const countMembers = members.querySelectorAll('.unit-member-chip').length;
            members.dataset.memberCount = String(countMembers);
            if (cast) cast.hidden = countMembers === 0;
            window.paintArchiveChemistry?.(members);
        } else if (cast) {
            cast.hidden = true;
        }
        const cover = urls[0] || card.dataset.url || '';
        [edit, remove].forEach(button => {
            if (!button) return;
            button.dataset.id = card.dataset.id || '';
            button.dataset.title = card.dataset.title || '';
            button.dataset.description = card.dataset.description || '';
            button.dataset.artists = card.dataset.artists || '';
            button.dataset.url = cover;
            button.dataset.images = card.dataset.images || '';
            button.dataset.registered = card.dataset.registered || '';
            button.dataset.source = card.dataset.source || '';
        });
    }

    function showImage() {
        if (!image || !activeCard) return;
        const url = urls[index] || '';
        image.src = url;
        image.alt = descriptionText(activeCard) || '사진';
    }

    function paintNav() {
        const multi = urls.length > 1;
        if (prev) {
            prev.hidden = !multi;
            prev.disabled = index <= 0;
        }
        if (next) {
            next.hidden = !multi;
            next.disabled = index >= urls.length - 1;
        }
        if (count) {
            count.hidden = !multi;
            count.textContent = multi ? (index + 1) + ' / ' + urls.length : '';
        }
        if (dots) {
            dots.hidden = !multi;
            dots.classList.toggle('is-compact', urls.length > 10);
            dots.replaceChildren();
            if (multi) {
                urls.forEach((_, i) => {
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
        if (!urls.length) return;
        index = Math.max(0, Math.min(urls.length - 1, nextIndex));
        showImage();
        paintNav();
    }

    function open(card) {
        activeCard = card;
        urls = parseImages(card);
        index = 0;
        fillMeta(card);
        showImage();
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
