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
    const unit = document.getElementById('archiveDetailUnit');
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
    let suppressSwipe = false;
    let pan = { x: 0, y: 0, w: 0, h: 0 };
    let drag = null;

    function parseImages(card) {
        if (!card) return [];
        const raw = (card.dataset.viewImages || card.dataset.images || '').trim();
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
        if (unit) {
            const label = (card.dataset.unitLabel || '').trim();
            const color = (card.dataset.unitColor || '').trim() || '#a78bfa';
            unit.hidden = !label;
            unit.textContent = label;
            unit.title = label;
            unit.style.setProperty('--unit-color', color);
        }
        if (meta) {
            const unitHidden = !unit || unit.hidden;
            meta.hidden = Boolean(date?.hidden) && Boolean(source?.hidden) && unitHidden;
        }
        if (members) showCast(card);
        else if (cast) cast.hidden = true;
        const cover = card.dataset.url || parseImages(card)[0] || '';
        [edit, remove].forEach(button => {
            if (!button) return;
            button.dataset.id = card.dataset.id || '';
            button.dataset.title = card.dataset.title || '';
            button.dataset.description = card.dataset.description || '';
            button.dataset.artists = card.dataset.artists || '';
            button.dataset.imageArtists = card.dataset.imageArtists || '';
            button.dataset.url = cover;
            button.dataset.images = card.dataset.images || '';
            button.dataset.registered = card.dataset.registered || '';
            button.dataset.source = card.dataset.source || '';
        });
    }

    function showCast(card) {
        const sourceCard = card || activeCard;
        if (!members || !sourceCard) return;
        const frames = [...sourceCard.querySelectorAll('[data-image-cast]')];
        const frame = frames[index] || frames[0];
        const source = frame || sourceCard.querySelector('.ar-card-members');
        members.replaceChildren();
        if (source) members.innerHTML = frame ? frame.innerHTML : source.innerHTML;
        const countMembers = members.querySelectorAll('.unit-member-chip').length;
        members.dataset.memberCount = String(countMembers);
        members.classList.toggle('is-many', countMembers >= 10);
        fitMembers();
        if (cast) cast.hidden = countMembers === 0;
        window.paintArchiveChemistry?.(members);
    }

    function fitMembers() {
        if (!members) return;
        const count = members.querySelectorAll('.unit-member-chip').length;
        if (!count) {
            members.style.removeProperty('--detail-chip');
            return;
        }
        const width = members.clientWidth || members.parentElement?.clientWidth || 280;
        const gap = 8;
        const columns = count <= 4 ? count : count <= 9 ? 4 : count <= 16 ? 5 : 6;
        let chip = Math.floor((width - gap * (columns - 1)) / columns);
        chip = Math.round(chip * (count >= 6 ? 0.88 : 1));
        const max = count >= 10 ? 52 : count >= 6 ? 58 : 70;
        const min = count <= 2 ? 64 : 40;
        chip = Math.max(min, Math.min(max, chip));
        members.style.setProperty('--detail-chip', chip + 'px');
    }

    function clearPan() {
        drag = null;
        if (!image) return;
        image.classList.remove('is-pannable', 'is-panning');
        image.style.width = '';
        image.style.height = '';
        image.style.transform = '';
    }

    function applyPan() {
        if (!image) return;
        image.style.transform = 'translate(' + pan.x + 'px, ' + pan.y + 'px)';
    }

    function clampPan() {
        if (!photo) return;
        const box = photo.getBoundingClientRect();
        const minX = Math.min(0, box.width - pan.w);
        const minY = Math.min(0, box.height - pan.h);
        pan.x = Math.min(0, Math.max(minX, pan.x));
        pan.y = Math.min(0, Math.max(minY, pan.y));
    }

    function layoutPan() {
        if (!photo || !image || urls.length < 2) {
            clearPan();
            return;
        }
        const box = photo.getBoundingClientRect();
        const naturalWidth = image.naturalWidth;
        const naturalHeight = image.naturalHeight;
        if (!naturalWidth || !naturalHeight || box.width < 2 || box.height < 2) return;
        const scale = Math.max(box.width / naturalWidth, box.height / naturalHeight, 1);
        pan.w = naturalWidth * scale;
        pan.h = naturalHeight * scale;
        image.style.width = pan.w + 'px';
        image.style.height = pan.h + 'px';
        pan.x = (box.width - pan.w) / 2;
        pan.y = (box.height - pan.h) / 2;
        clampPan();
        applyPan();
        const overflows = pan.w > box.width + 1 || pan.h > box.height + 1;
        image.classList.toggle('is-pannable', overflows);
    }

    function showImage() {
        if (!image || !activeCard) return;
        const url = urls[index] || '';
        const multi = urls.length > 1;
        photo?.classList.toggle('is-pan', multi);
        if (!multi) clearPan();
        image.alt = descriptionText(activeCard) || '사진';
        image.onload = () => layoutPan();
        image.src = url;
        if (image.complete) layoutPan();
        showCast();
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
        fitMembers();
        layoutPan();
        detail.querySelector('[data-ar-close]')?.focus();
    }

    /** 그리드 soft-refresh 후 같은 게시글 카드로 상세·수정 버튼 메타를 맞춘다. */
    window.syncArchiveDetailCard = function (card) {
        if (!card) return;
        const id = String(card.dataset.id || '');
        if (!id) return;
        const sameEdit = edit && String(edit.dataset.id || '') === id;
        const sameView = activeCard && String(activeCard.dataset.id || '') === id;
        if (!sameEdit && !sameView) return;
        const prevUrl = urls[index];
        if (sameView) {
            activeCard = card;
            urls = parseImages(card);
            const found = urls.indexOf(prevUrl);
            index = found >= 0 ? found : Math.min(index, Math.max(0, urls.length - 1));
            fillMeta(card);
            showImage();
            paintNav();
        } else {
            fillMeta(card);
        }
    };

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
            if (event.target.closest('button, a, input, textarea, select')) return;
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
            if (suppressSwipe) {
                suppressSwipe = false;
                return;
            }
            if (Math.abs(dx) < 48 || Math.abs(dx) < Math.abs(dy)) return;
            goTo(index + (dx < 0 ? 1 : -1));
        }, { passive: true });
        photo.addEventListener('pointerdown', event => {
            if (!image?.classList.contains('is-pannable') || event.button > 0) return;
            if (event.target.closest('button, a, input, textarea, select')) return;
            drag = { id: event.pointerId, x: event.clientX, y: event.clientY, px: pan.x, py: pan.y, moved: false };
            image.classList.add('is-panning');
            photo.setPointerCapture?.(event.pointerId);
        });
        photo.addEventListener('pointermove', event => {
            if (!drag || drag.id !== event.pointerId) return;
            const dx = event.clientX - drag.x;
            const dy = event.clientY - drag.y;
            if (Math.abs(dx) + Math.abs(dy) > 4) drag.moved = true;
            pan.x = drag.px + dx;
            pan.y = drag.py + dy;
            clampPan();
            applyPan();
        });
        const endDrag = event => {
            if (!drag || drag.id !== event.pointerId) return;
            suppressSwipe = drag.moved;
            drag = null;
            image?.classList.remove('is-panning');
        };
        photo.addEventListener('pointerup', endDrag);
        photo.addEventListener('pointercancel', endDrag);
    }

    window.addEventListener('resize', () => {
        if (detail.hidden) return;
        fitMembers();
        layoutPan();
    });

    document.addEventListener('click', event => {
        const opener = event.target.closest('[data-archive-open]');
        if (!opener) return;
        event.preventDefault();
        const card = opener.closest('[data-id]');
        if (!card) return;
        open(card);
    });
})();
