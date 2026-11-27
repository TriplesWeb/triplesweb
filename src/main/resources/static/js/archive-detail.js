(function () {
    const detail = document.getElementById('archiveDetail');
    if (!detail) return;
    const closeButton = detail.querySelector('.ar-detail-close');
    const photo = document.getElementById('archiveDetailPhoto') || detail.querySelector('.ar-detail-photo');
    const image = document.getElementById('archiveDetailImage');
    const originalLink = document.getElementById('archiveDetailOriginalLink');
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
            const color = (card.dataset.sourceColor || '').trim() || '#00f5d4';
            source.hidden = !label;
            source.dataset.source = (card.dataset.source || '').toLowerCase();
            source.style.setProperty('--source-color', color);
            source.textContent = label;
        }
        if (unit) {
            const badges = [...card.querySelectorAll('.ar-card-badges > .ar-unit-badge')];
            unit.replaceChildren(...badges.map(badge => badge.cloneNode(true)));
            unit.hidden = !badges.length;
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
            button.dataset.url = cover;
            button.dataset.images = card.dataset.images || '';
            button.dataset.registered = card.dataset.registered || '';
            button.dataset.source = card.dataset.source || '';
            button.dataset.unitIds = card.dataset.unitIds || '';
        });
    }

    const FULL_LINEUP = [
        '윤서연', '정혜린', '이지우', '김채연', '김유연', '김수민',
        '김나경', '공유빈', '카에데', '서다현', '코토네', '곽연지',
        '니엔', '박소현', '신위', '마유', '린', '주빈',
        '정하연', '박시온', '김채원', '설린', '서아', '지연'
    ];
    let groupPhotoUrl = '';
    let groupPhotoTask = null;

    function castNames() {
        return [...members.querySelectorAll('.unit-member-chip')].map((chip) => (
            chip.getAttribute('title') || chip.querySelector('span')?.textContent || ''
        ).trim()).filter(Boolean);
    }

    function isFullLineup(names) {
        const present = new Set(names);
        return FULL_LINEUP.every((name) => present.has(name));
    }

    function ensureGroupPhoto(img) {
        if (groupPhotoUrl) {
            img.src = groupPhotoUrl;
            return;
        }
        if (!groupPhotoTask) {
            groupPhotoTask = fetch('/api/artist/group', { credentials: 'same-origin' })
                .then((response) => response.ok ? response.json() : {})
                .then((data) => data.tabImageUrl || data.imageUrl || '')
                .catch(() => '');
        }
        groupPhotoTask.then((url) => {
            groupPhotoUrl = url || '';
            if (url && img.isConnected) img.src = url;
        });
    }

    function showFullLineup() {
        const link = document.createElement('a');
        link.className = 'ar-detail-lineup';
        link.href = '/triples';
        link.setAttribute('aria-label', 'tripleS 24명');
        const photo = document.createElement('span');
        photo.className = 'ar-detail-lineup-photo';
        const img = document.createElement('img');
        img.alt = '';
        img.referrerPolicy = 'no-referrer';
        photo.append(img);
        const label = document.createElement('span');
        label.className = 'ar-detail-lineup-label';
        label.textContent = 'tripleS';
        link.append(photo, label);
        members.replaceChildren(link);
        members.classList.remove('is-many');
        members.style.removeProperty('--detail-chip');
        members.dataset.memberCount = '24';
        if (cast) cast.hidden = false;
        ensureGroupPhoto(img);
    }

    function showCast(card) {
        if (!members || !card) return;
        const source = card.querySelector('.ar-card-members');
        members.innerHTML = source?.innerHTML || '';
        if (isFullLineup(castNames())) {
            showFullLineup();
            window.paintArchiveChemistry?.(members);
            return;
        }
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

    function resetImageFrame() {
        if (!image) return;
        image.classList.remove('is-pannable', 'is-panning');
        image.style.width = '';
        image.style.height = '';
        image.style.transform = '';
        photo?.classList.remove('is-pan');
    }

    function detailVariantUrl(url) {
        if (!url) return null;
        try {
            const local = new URL(url, window.location.href);
            if (local.origin !== window.location.origin || !local.pathname.startsWith('/uploads/')) return null;
            // GIF는 축소 JPEG로 바꾸면 애니메이션이 멈추므로 원본을 띄운다.
            if (/\.gif$/i.test(local.pathname)) return null;
            local.searchParams.set('w', '1600');
            return local.pathname + local.search + local.hash;
        } catch (_) {
            return null;
        }
    }

    function showImage() {
        if (!image || !activeCard) return;
        const url = urls[index] || '';
        // 큰 틀(object-fit: contain) 안에 맞춰 넣고, cover/팬으로 잘리지 않게 한다.
        resetImageFrame();
        image.alt = descriptionText(activeCard) || '사진';
        const variant = detailVariantUrl(url);
        image.src = variant || url;
        if (originalLink) {
            originalLink.parentElement.hidden = !variant;
            if (variant) originalLink.href = url;
            else originalLink.removeAttribute('href');
        }
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
                let activeDot = null;
                urls.forEach((_, i) => {
                    const dot = document.createElement('button');
                    dot.type = 'button';
                    const active = i === index;
                    dot.className = 'ar-detail-dot' + (active ? ' is-active' : '');
                    dot.setAttribute('aria-label', (i + 1) + '번째 사진');
                    if (active) {
                        dot.setAttribute('aria-current', 'true');
                        activeDot = dot;
                    }
                    dot.addEventListener('click', () => goTo(i));
                    dots.appendChild(dot);
                });
                if (activeDot) {
                    queueMicrotask(() => {
                        activeDot.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
                    });
                }
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
        const cover = (card.dataset.url || '').trim();
        const coverIndex = cover ? urls.indexOf(cover) : -1;
        index = coverIndex >= 0 ? coverIndex : 0;
        fillMeta(card);
        showCast(card);
        showImage();
        paintNav();
        detail.hidden = false;
        document.body.style.overflow = 'hidden';
        fitMembers();
        closeButton?.focus();
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
            showCast(card);
            showImage();
            paintNav();
        } else {
            fillMeta(card);
        }
    };

    function closeDetail() {
        detail.hidden = true;
        if (!document.querySelector('.ar-dialog:not([hidden])')) document.body.style.overflow = '';
    }

    closeButton?.addEventListener('click', () => {
        closeDetail();
        activeCard?.querySelector('[data-archive-open]')?.focus({ preventScroll: true });
    });
    edit?.addEventListener('click', closeDetail);

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
            if (Math.abs(dx) < 48 || Math.abs(dx) < Math.abs(dy)) return;
            goTo(index + (dx < 0 ? 1 : -1));
        }, { passive: true });
    }

    window.addEventListener('resize', () => {
        if (detail.hidden) return;
        fitMembers();
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
