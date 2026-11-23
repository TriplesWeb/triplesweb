(function () {
    const form = document.getElementById('mediaFilterForm');
    const sort = document.getElementById('mediaSort');
    const panel = document.getElementById('mediaMemberPanel');
    const toggle = document.getElementById('mediaMemberToggle');
    const label = toggle?.querySelector('[data-member-toggle-label]');
    const memberBox = document.getElementById('mediaMemberInputs');
    const memberCount = document.getElementById('mediaMemberCount');
    const search = form?.querySelector('.ar-search');

    function selectedMembers() {
        if (!window.MemberPortraitPicker || !panel) return [];
        return MemberPortraitPicker.selectedValues(panel);
    }

    function memberKey() {
        return selectedMembers().join('\n');
    }

    function paintMembers() {
        const values = selectedMembers();
        if (memberBox) {
            memberBox.replaceChildren();
            values.forEach((name) => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'members';
                input.value = name;
                memberBox.appendChild(input);
            });
        }
        const count = values.length;
        if (label) label.textContent = count === 0 ? '멤버 선택' : count === 1 ? values[0] : count + '명';
        if (memberCount) {
            memberCount.hidden = count === 0;
            memberCount.textContent = String(count);
        }
        toggle?.classList.toggle('has-selection', count > 0);
    }

    if (form && sort) {
        function enhanceSort(select) {
            const control = select.closest('.ar-sort-control');
            if (!control || control.classList.contains('is-enhanced')) return;
            control.classList.add('is-enhanced');
            select.setAttribute('aria-hidden', 'true');
            select.tabIndex = -1;
            const trigger = document.createElement('button');
            trigger.type = 'button';
            trigger.className = 'ar-sort-trigger';
            trigger.setAttribute('aria-haspopup', 'listbox');
            trigger.setAttribute('aria-expanded', 'false');
            trigger.setAttribute('aria-label', '정렬');
            const menu = document.createElement('div');
            menu.className = 'ar-sort-menu';
            menu.hidden = true;
            menu.setAttribute('role', 'listbox');
            function sync() {
                const selected = [...select.options].find((option) => option.value === select.value);
                trigger.textContent = selected ? selected.textContent.trim() : '';
                [...menu.querySelectorAll('.ar-sort-option')].forEach((option) => {
                    option.setAttribute('aria-selected', option.dataset.value === select.value ? 'true' : 'false');
                });
            }
            function setOpen(open) {
                menu.hidden = !open;
                trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
            }
            [...select.options].forEach((option) => {
                const item = document.createElement('button');
                item.type = 'button';
                item.className = 'ar-sort-option';
                item.setAttribute('role', 'option');
                item.dataset.value = option.value;
                item.textContent = option.textContent.trim();
                item.addEventListener('click', () => {
                    if (select.value !== option.value) {
                        select.value = option.value;
                        select.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    sync();
                    setOpen(false);
                });
                menu.appendChild(item);
            });
            trigger.addEventListener('click', () => {
                const willOpen = menu.hidden;
                window.closeArchiveOverlays?.(trigger);
                setOpen(willOpen);
            });
            select.after(trigger);
            control.appendChild(menu);
            sync();
        }

        function dateSnapshot() {
            return ['from', 'to'].map((name) => {
                const input = form.querySelector('[data-date-filter] input[name="' + name + '"]');
                return name + '=' + (input ? input.value : '');
            }).join('&');
        }

        let appliedDates = dateSnapshot();
        let appliedMembers = memberKey();
        let composing = false;
        let filterTimer = 0;
        let filterSeq = 0;
        enhanceSort(sort);
        sort.addEventListener('change', scheduleResults);

        function filterUrl() {
            paintMembers();
            const params = new URLSearchParams();
            new FormData(form).forEach((value, key) => {
                if (String(value).trim() === '') return;
                params.append(key, value);
            });
            const action = form.getAttribute('action') || '/media';
            const query = params.toString();
            return query ? action + (action.includes('?') ? '&' : '?') + query : action;
        }

        function swapSection(currentShell, nextShell, className) {
            const current = currentShell.querySelector('section.' + className);
            const next = nextShell.querySelector('section.' + className);
            if (current && next) {
                current.replaceWith(document.importNode(next, true));
                return;
            }
            if (current) {
                current.remove();
                return;
            }
            if (!next) return;
            const imported = document.importNode(next, true);
            const filters = currentShell.querySelector('section.media-filters');
            const feature = currentShell.querySelector('section.media-feature');
            const archive = currentShell.querySelector('section.media-archive');
            if (className === 'media-feature' && filters) filters.after(imported);
            else if (className === 'media-archive') (feature || filters)?.after(imported);
            else if (className === 'media-shorts') (archive || feature || filters)?.after(imported);
        }

        async function applyResults(nextHref, fromPager) {
            const url = nextHref || filterUrl();
            const seq = ++filterSeq;
            const y = window.scrollY;
            try {
                const response = await fetch(url, { credentials: 'same-origin' });
                if (seq !== filterSeq) return;
                if (!response.ok) throw new Error(String(response.status));
                const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
                const shell = document.querySelector('main.media-shell');
                const nextShell = doc.querySelector('main.media-shell');
                if (!shell || !nextShell) throw new Error('shell');
                swapSection(shell, nextShell, 'media-feature');
                swapSection(shell, nextShell, 'media-archive');
                swapSection(shell, nextShell, 'media-shorts');
                const catalog = document.getElementById('mediaCatalog');
                const nextCatalog = doc.getElementById('mediaCatalog');
                if (catalog && nextCatalog) {
                    catalog.textContent = nextCatalog.textContent;
                    reloadCatalog();
                }
                const reset = form.querySelector('.ar-reset');
                const nextReset = nextShell.querySelector('.ar-reset');
                if (reset && nextReset) reset.hidden = nextReset.hidden;
                capturePlaylist();
                renderPlaylist();
                upgradeStills();
                const nextUrl = new URL(url, location.href);
                const same = nextUrl.pathname === location.pathname && nextUrl.search === location.search;
                if (!same) history.pushState({ mediaLive: true }, '', nextUrl.pathname + nextUrl.search);
                if (fromPager) document.querySelector('.media-archive')?.scrollIntoView({ block: 'start' });
                else window.scrollTo(0, y);
            } catch (error) {
                if (seq === filterSeq) window.location.assign(url);
            }
        }

        document.addEventListener('click', (event) => {
            const link = event.target.closest('main.media-shell .ar-pager a[href]:not(.is-disabled)');
            if (!link) return;
            event.preventDefault();
            applyResults(link.href, true);
        });
        document.addEventListener('submit', (event) => {
            const jump = event.target.closest('form.ar-page-jump');
            if (!jump || !jump.closest('main.media-shell')) return;
            event.preventDefault();
            const formData = new FormData(jump);
            const params = new URLSearchParams();
            formData.forEach((value, key) => {
                if (key === 'go' || key === 'page') return;
                if (value != null && String(value) !== '') params.append(key, value);
            });
            const requested = Number(formData.get('go'));
            const page = Number.isFinite(requested) ? Math.max(1, Math.trunc(requested)) : 1;
            if (page > 1) params.set('page', String(page));
            applyResults('/media?' + params.toString(), true);
        });

        function scheduleResults() {
            window.clearTimeout(filterTimer);
            filterTimer = window.setTimeout(() => {
                if (composing) return;
                applyResults();
            }, 180);
        }

        function commitMembers() {
            paintMembers();
            const next = memberKey();
            if (next === appliedMembers) return;
            appliedMembers = next;
            scheduleResults();
        }

        toggle?.addEventListener('click', () => {
            if (!panel) return;
            const willOpen = panel.hidden;
            window.closeArchiveOverlays?.(toggle);
            panel.hidden = !willOpen;
            toggle.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
            toggle.classList.toggle('is-open', willOpen);
            if (!willOpen) commitMembers();
        });
        if (panel && window.MutationObserver) {
            new MutationObserver(() => {
                if (panel.hidden) commitMembers();
            }).observe(panel, { attributes: true, attributeFilter: ['hidden'] });
        }
        if (window.MemberPortraitPicker && panel) {
            MemberPortraitPicker.bindMultiSelect(panel, {
                multi: true,
                onChange: commitMembers
            });
        }
        form.addEventListener('archive-date-close', () => {
            const next = dateSnapshot();
            if (next === appliedDates) return;
            appliedDates = next;
            scheduleResults();
        });
        form.querySelectorAll('[data-date-filter] input').forEach((input) => {
            input.addEventListener('change', () => {
                const next = dateSnapshot();
                if (next === appliedDates) return;
                appliedDates = next;
                scheduleResults();
            });
        });
        search?.addEventListener('compositionstart', () => {
            composing = true;
            window.clearTimeout(filterTimer);
        });
        search?.addEventListener('compositionend', () => {
            composing = false;
            scheduleResults();
        });
        search?.addEventListener('input', () => {
            if (composing) return;
            scheduleResults();
        });
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            scheduleResults();
        });
        window.addEventListener('popstate', () => window.location.reload());
    }

    const PLAYLIST_KEY = 'triples-media-playlist';
    let playlistList = null;
    let playlistTitle = null;
    let playlistCount = null;
    let originalPlaylist = '';
    let originalTitle = '추천 재생목록';
    let originalCount = '00';

    function capturePlaylist() {
        playlistList = document.getElementById('mediaPlaylistList');
        playlistTitle = document.getElementById('mediaPlaylistTitle');
        playlistCount = document.getElementById('mediaPlaylistCount');
        originalPlaylist = playlistList ? playlistList.innerHTML : '';
        originalTitle = playlistTitle ? playlistTitle.textContent : '추천 재생목록';
        originalCount = playlistCount ? playlistCount.textContent : '00';
    }

    function readPlaylist() {
        try {
            const parsed = JSON.parse(localStorage.getItem(PLAYLIST_KEY) || '[]');
            return Array.isArray(parsed) ? parsed.filter((item) => item && item.youtubeId) : [];
        } catch (error) {
            return [];
        }
    }

    function writePlaylist(items) {
        localStorage.setItem(PLAYLIST_KEY, JSON.stringify(items.slice(0, 50)));
    }

    function paintAddButtons(items) {
        const ids = new Set(items.map((item) => item.youtubeId));
        document.querySelectorAll('[data-playlist-add]').forEach((button) => {
            const added = ids.has(button.dataset.ytid || '');
            button.classList.toggle('is-added', added);
            button.setAttribute('aria-pressed', added ? 'true' : 'false');
            if (!button.classList.contains('media-playlist-add--head')) {
                button.setAttribute('aria-label', added ? '재생목록에서 빼기' : '재생목록 추가');
            }
        });
    }

    function playlistRow(item) {
        const row = document.createElement('div');
        row.className = 'media-playlist__row';
        const link = document.createElement('a');
        link.className = 'media-playlist__item';
        link.href = '/media/' + encodeURIComponent(item.tab || 'mv') + '/' + encodeURIComponent(item.youtubeId);
        link.dataset.ytid = item.youtubeId;
        link.dataset.tab = item.tab || 'mv';
        link.dataset.kind = item.tabLabel || '';
        link.dataset.title = item.title || '';
        link.dataset.duration = item.duration || '';
        link.dataset.views = item.viewCountFmt || '';
        link.dataset.published = item.publishedLabel || '';
        const thumb = document.createElement('span');
        thumb.className = 'media-playlist__thumb';
        const image = document.createElement('img');
        image.src = 'https://img.youtube.com/vi/' + encodeURIComponent(item.youtubeId) + '/hqdefault.jpg';
        image.alt = '';
        thumb.appendChild(image);
        if (item.duration) {
            const time = document.createElement('em');
            time.textContent = item.duration;
            thumb.appendChild(time);
        }
        const copy = document.createElement('span');
        copy.className = 'media-playlist__copy';
        const kind = document.createElement('small');
        kind.textContent = item.tabLabel || '';
        const title = document.createElement('strong');
        title.textContent = item.title || '';
        const meta = document.createElement('em');
        meta.textContent = '조회수 ' + (item.viewCountFmt || '0') + (item.publishedLabel ? ' · ' + item.publishedLabel : '');
        copy.append(kind, title, meta);
        link.append(thumb, copy);
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'media-playlist-remove';
        remove.dataset.playlistRemove = item.youtubeId;
        remove.setAttribute('aria-label', '재생목록에서 빼기');
        remove.textContent = '×';
        row.append(link, remove);
        return row;
    }

    function setCollapsed(node, collapsed) {
        if (!node) return;
        node.classList.toggle('is-collapsed', collapsed);
        node.hidden = collapsed;
    }

    function renderPlaylist() {
        const items = readPlaylist();
        paintAddButtons(items);
        const playlist = document.getElementById('mediaPlaylist');
        const feature = document.querySelector('.media-feature');
        const managing = items.length > 0;
        playlist?.classList.toggle('is-managing', managing);
        setCollapsed(feature, !managing);
        if (!playlistList) return;
        if (!items.length) {
            playlistList.replaceChildren();
            if (playlistTitle) playlistTitle.textContent = originalTitle;
            if (playlistCount) playlistCount.textContent = originalCount;
            clearHero();
            paintSuggestions(null);
            return;
        }
        if (playlistTitle) playlistTitle.textContent = '재생목록';
        if (playlistCount) playlistCount.textContent = String(items.length).padStart(2, '0');
        playlistList.replaceChildren(...items.map(playlistRow));
        focusPlaylist();
        paintSuggestions(items[0]);
    }

    function clearHero() {
        const hero = document.querySelector('.media-feature__hero');
        if (!hero) return;
        hero.removeAttribute('data-ytid');
        hero.removeAttribute('data-site-toggle');
        hero.removeAttribute('data-site-target');
        hero.href = '/media';
        const still = hero.querySelector('.media-feature__still');
        if (still) {
            still.dataset.ytStill = '';
            still.removeAttribute('src');
        }
        const title = document.getElementById('mediaFeatureTitle');
        if (title) title.textContent = '';
        const meta = document.getElementById('mediaFeatureMeta');
        if (meta) meta.textContent = '';
        const duration = document.getElementById('mediaFeatureDuration');
        if (duration) duration.textContent = '';
    }

    function suggestCard(item) {
        const card = document.createElement('a');
        card.className = 'media-suggest__card';
        card.href = '/media/' + encodeURIComponent(item.tab || 'mv') + '/' + encodeURIComponent(item.youtubeId);
        card.dataset.siteToggle = 'modal';
        card.dataset.siteTarget = '#ytModal';
        card.dataset.ytid = item.youtubeId;
        card.dataset.tab = item.tab || '';
        card.dataset.kind = item.tabLabel || '';
        card.dataset.title = item.title || '';
        card.dataset.duration = item.duration || '';
        card.dataset.views = item.viewCountFmt || '';
        card.dataset.published = item.publishedLabel || '';
        const thumb = document.createElement('span');
        thumb.className = 'media-suggest__thumb';
        const image = document.createElement('img');
        image.src = 'https://img.youtube.com/vi/' + encodeURIComponent(item.youtubeId) + '/hqdefault.jpg';
        image.alt = '';
        thumb.append(image);
        if (item.duration) {
            const time = document.createElement('em');
            time.textContent = item.duration;
            thumb.append(time);
        }
        const copy = document.createElement('span');
        copy.className = 'media-suggest__copy';
        const kind = document.createElement('small');
        kind.textContent = item.tabLabel || '';
        const title = document.createElement('strong');
        title.textContent = item.title || '';
        copy.append(kind, title);
        card.append(thumb, copy);
        return card;
    }

    function paintSuggestions(anchor) {
        const box = document.getElementById('mediaSuggest');
        const list = document.getElementById('mediaSuggestList');
        if (!box || !list) return;
        if (!anchor || !anchor.youtubeId) {
            list.replaceChildren();
            setCollapsed(box, true);
            return;
        }
        const pool = archiveVideos().filter((row) => row.youtubeId !== anchor.youtubeId);
        const same = pool.filter((row) => row.tab && row.tab === anchor.tab);
        const chosen = (same.length ? same : pool).slice(0, 4);
        list.replaceChildren(...chosen.map(suggestCard));
        setCollapsed(box, chosen.length === 0);
    }

    function removePlaylist(id) {
        writePlaylist(readPlaylist().filter((item) => item.youtubeId !== id));
        renderPlaylist();
    }

    let byId = new Map();
    function reloadCatalog() {
        const catalogNode = document.getElementById('mediaCatalog');
        let catalog = [];
        try {
            catalog = JSON.parse(catalogNode?.textContent || '[]');
        } catch (error) {
            catalog = [];
        }
        byId = new Map(catalog.map((row) => [row.youtubeId, row]));
    }
    reloadCatalog();
    const ytModal = document.getElementById('ytModal');
    let ytPlayer = document.getElementById('ytPlayer');
    const editModal = document.getElementById('mediaEditModal');
    const editPicker = document.getElementById('media-edit-picker');
    let currentVideo = null;
    let youtubePlayer = null;
    let youtubePlayerReady = null;
    let requestedVideoId = null;

    function playMediaVideo(id) {
        requestedVideoId = id;
        if (youtubePlayer) {
            youtubePlayer.loadVideoById(id);
            return;
        }
        if (youtubePlayerReady) return;
        // Start only after the YouTube API has initialized the safe volume.
        ytPlayer.src = 'https://www.youtube.com/embed/' + encodeURIComponent(id) + '?rel=0&enablejsapi=1';
        const pendingPlayer = window.SiteVideoVolume.attachYouTube(ytPlayer);
        youtubePlayerReady = pendingPlayer;
        pendingPlayer.then((player) => {
            if (youtubePlayerReady !== pendingPlayer) return;
            youtubePlayer = player;
            if (requestedVideoId === id) player.playVideo();
            else player.loadVideoById(requestedVideoId);
        }, () => {
            if (youtubePlayerReady === pendingPlayer) youtubePlayerReady = null;
        });
    }

    function text(id, value) {
        const node = document.getElementById(id);
        if (node) node.textContent = value || '';
    }

    function openVideo(trigger) {
        const id = trigger.getAttribute('data-ytid');
        if (!ytModal || !ytPlayer || !id) return;
        const row = byId.get(id) || {
            youtubeId: id,
            tab: trigger.dataset.tab || '',
            tabLabel: trigger.dataset.kind || '',
            title: trigger.dataset.title || trigger.querySelector('strong')?.textContent || '',
            description: '',
            publishedOn: '',
            publishedLabel: trigger.dataset.published || '',
            duration: trigger.dataset.duration || '',
            viewCountFmt: trigger.dataset.views || '',
            artistNames: ''
        };
        currentVideo = row;
        playMediaVideo(id);
        text('ytModalTitle', row.title || '영상 재생');
        text('ytModalKind', row.tabLabel || '');
        text('ytModalHeading', row.title || '');
        const bits = [];
        if (row.viewCountFmt) bits.push('조회수 ' + row.viewCountFmt);
        if (row.publishedLabel) bits.push(row.publishedLabel);
        if (row.duration) bits.push(row.duration);
        text('ytModalMeta', bits.join(' · '));
        paintModalMembers(row);
        text('ytModalDesc', row.description && String(row.description).trim() ? row.description : '등록된 상세 설명이 없습니다.');
        const deleteTab = document.getElementById('ytDeleteTab');
        const deleteId = document.getElementById('ytDeleteId');
        if (deleteTab) deleteTab.value = row.tab || '';
        if (deleteId) deleteId.value = id;
        showModal(ytModal);
    }

    function paintModalMembers(video) {
        const cast = document.getElementById('ytModalCast');
        const members = document.getElementById('ytModalMembers');
        if (!cast || !members) return;
        members.replaceChildren();
        members.classList.remove('is-many');
        delete members.dataset.memberCount;
        const badges = Array.isArray(video.memberBadges) ? video.memberBadges : [];
        if (!badges.length) {
            cast.hidden = true;
            return;
        }
        if (badges.length === 1 && badges[0].fullLineup) {
            const link = document.createElement('a');
            link.className = 'ar-detail-lineup';
            link.href = '/triples';
            link.setAttribute('aria-label', 'tripleS 24명');
            const photo = document.createElement('span');
            photo.className = 'ar-detail-lineup-photo';
            const img = document.createElement('img');
            img.alt = '';
            img.referrerPolicy = 'no-referrer';
            if (badges[0].image) img.src = badges[0].image;
            photo.append(img);
            const label = document.createElement('span');
            label.className = 'ar-detail-lineup-label';
            label.textContent = 'tripleS';
            link.append(photo, label);
            members.append(link);
            members.dataset.memberCount = '24';
            cast.hidden = false;
            return;
        }
        badges.forEach((badge) => {
            const chip = document.createElement(badge.artistId ? 'a' : 'span');
            chip.className = 'unit-member-chip';
            chip.style.setProperty('--member-color', badge.color || '#b9cac4');
            chip.title = badge.name || '';
            if (badge.artistId) chip.href = '/triples/artist/' + encodeURIComponent(badge.artistId);
            if (badge.image) {
                const img = document.createElement('img');
                img.src = badge.image;
                img.alt = badge.name || '';
                img.referrerPolicy = 'no-referrer';
                chip.append(img);
            }
            const label = document.createElement('span');
            label.textContent = badge.name || '';
            chip.append(label);
            members.append(chip);
        });
        members.dataset.memberCount = String(badges.length);
        members.classList.toggle('is-many', badges.length >= 10);
        cast.hidden = false;
    }

    function showModal(modal) {
        if (!modal) return;
        if (modal.parentElement !== document.body) document.body.appendChild(modal);
        modal.style.zIndex = '5000';
        window.SiteUi?.modal(modal).show();
    }

    function syncEditMembers() {
        const box = document.getElementById('mediaEditMembers');
        if (!box || !window.MemberPortraitPicker || !editPicker) return;
        box.replaceChildren();
        MemberPortraitPicker.selectedValues(editPicker).forEach((name) => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'artistNames';
            input.value = name;
            box.appendChild(input);
        });
    }

    if (window.MemberPortraitPicker && editPicker) {
        MemberPortraitPicker.bindMultiSelect(editPicker, { multi: true, onChange: syncEditMembers });
    }

    function videoFrom(trigger) {
        const id = trigger?.getAttribute('data-ytid') || '';
        return byId.get(id) || {
            youtubeId: id,
            tab: trigger?.dataset.tab || '',
            tabLabel: trigger?.dataset.kind || '',
            title: trigger?.dataset.title || '',
            description: '',
            publishedOn: '',
            publishedLabel: trigger?.dataset.published || '',
            duration: trigger?.dataset.duration || '',
            viewCountFmt: trigger?.dataset.views || '',
            artistNames: ''
        };
    }

    function openEdit(video) {
        if (!editModal || !video || !video.youtubeId) return;
        currentVideo = video;
        const tab = document.getElementById('mediaEditTab');
        const id = document.getElementById('mediaEditId');
        const date = document.getElementById('mediaEditDate');
        const desc = document.getElementById('mediaEditDesc');
        if (tab) tab.value = video.tab || 'contents';
        if (id) id.value = video.youtubeId || '';
        if (date) {
            date.value = String(video.publishedOn || '').slice(0, 10);
            date.dispatchEvent(new Event('input'));
        }
        if (desc) desc.value = video.description || '';
        const names = String(video.artistNames || '').split(',').map((name) => name.trim()).filter(Boolean);
        if (window.MemberPortraitPicker && editPicker) {
            MemberPortraitPicker.setValues(editPicker, names);
            syncEditMembers();
        }
        showModal(editModal);
    }

    function applyHero(item) {
        const hero = document.querySelector('.media-feature__hero');
        if (!hero || !item || !item.youtubeId) return;
        const tab = item.tab || 'mv';
        hero.href = '/media/' + encodeURIComponent(tab) + '/' + encodeURIComponent(item.youtubeId);
        hero.dataset.ytid = item.youtubeId;
        hero.dataset.siteToggle = 'modal';
        hero.dataset.siteTarget = '#ytModal';
        const still = hero.querySelector('.media-feature__still');
        if (still) {
            still.dataset.ytStill = item.youtubeId;
            still.src = 'https://i.ytimg.com/vi/' + encodeURIComponent(item.youtubeId) + '/maxresdefault.jpg';
        }
        const title = document.getElementById('mediaFeatureTitle');
        if (title) title.textContent = item.title || '';
        const meta = document.getElementById('mediaFeatureMeta');
        if (meta) {
            const bits = [];
            if (item.viewCountFmt) bits.push('조회수 ' + item.viewCountFmt);
            if (item.publishedLabel) bits.push(item.publishedLabel);
            meta.textContent = bits.join(' · ');
        }
        const duration = document.getElementById('mediaFeatureDuration');
        if (duration) duration.textContent = item.duration || '';
    }

    function focusPlaylist() {
        const current = document.querySelector('#mediaPlaylistList .media-playlist__item.is-current')
            || document.querySelector('#mediaPlaylistList .media-playlist__item');
        if (!current) return;
        document.querySelectorAll('#mediaPlaylistList .media-playlist__item').forEach((item) => {
            item.classList.toggle('is-current', item === current);
        });
        applyHero(videoFrom(current));
    }

    function shortDuration(raw) {
        const parts = String(raw || '').trim().split(':');
        if (parts.length < 2 || parts.length > 3) return false;
        const nums = parts.map((part) => Number(part));
        if (nums.some((num) => Number.isNaN(num))) return false;
        const seconds = nums.length === 2 ? nums[0] * 60 + nums[1] : nums[0] * 3600 + nums[1] * 60 + nums[2];
        return seconds > 0 && seconds <= 60;
    }

    function archiveVideos() {
        return [...byId.values()].filter((row) => row && row.youtubeId && !shortDuration(row.duration));
    }

    function openPickModal() {
        const grid = document.getElementById('mediaPickGrid');
        const modal = document.getElementById('mediaPickModal');
        if (!grid || !modal) return;
        grid.replaceChildren();
        const videos = archiveVideos();
        if (!videos.length) {
            const empty = document.createElement('p');
            empty.className = 'media-pick-empty';
            empty.textContent = '담을 수 있는 영상이 없습니다.';
            grid.appendChild(empty);
        }
        videos.forEach((item) => {
            const card = document.createElement('label');
            card.className = 'media-pick-card';
            const check = document.createElement('input');
            check.type = 'checkbox';
            check.className = 'media-pick-check';
            check.value = item.youtubeId;
            check.setAttribute('aria-label', (item.title || '영상') + ' 선택');
            const thumb = document.createElement('span');
            thumb.className = 'media-pick-thumb';
            const image = document.createElement('img');
            image.src = 'https://img.youtube.com/vi/' + encodeURIComponent(item.youtubeId) + '/hqdefault.jpg';
            image.alt = '';
            thumb.appendChild(image);
            const copy = document.createElement('span');
            copy.className = 'media-pick-copy';
            const kind = document.createElement('small');
            kind.textContent = item.tabLabel || '';
            const title = document.createElement('strong');
            title.textContent = item.title || '';
            copy.append(kind, title);
            card.append(check, thumb, copy);
            grid.appendChild(card);
        });
        showModal(modal);
    }

    function confirmPicks() {
        const existing = readPlaylist();
        const seen = new Set(existing.map((item) => item.youtubeId));
        const incoming = [];
        document.querySelectorAll('#mediaPickGrid .media-pick-check:checked').forEach((input) => {
            const item = byId.get(input.value);
            if (!item || seen.has(item.youtubeId)) return;
            seen.add(item.youtubeId);
            incoming.push(item);
        });
        writePlaylist([...incoming, ...existing]);
        const modal = document.getElementById('mediaPickModal');
        if (modal && window.SiteUi) window.SiteUi.modal(modal).hide();
        renderPlaylist();
    }

    document.getElementById('ytModalEdit')?.addEventListener('click', () => {
        if (!currentVideo) return;
        openEdit(currentVideo);
    });

    document.addEventListener('click', (event) => {
        if (event.target.closest('#mediaPickConfirm')) {
            event.preventDefault();
            confirmPicks();
            return;
        }
        const remove = event.target.closest('[data-playlist-remove]');
        if (remove) {
            event.preventDefault();
            event.stopPropagation();
            removePlaylist(remove.dataset.playlistRemove || '');
            return;
        }
        const edit = event.target.closest('[data-media-edit]');
        if (edit) {
            event.preventDefault();
            event.stopPropagation();
            openEdit(videoFrom(edit));
            return;
        }
        const add = event.target.closest('[data-playlist-add]');
        if (add) {
            event.preventDefault();
            event.stopPropagation();
            openPickModal();
            return;
        }
        const playlistItem = event.target.closest('#mediaPlaylistList .media-playlist__item');
        if (playlistItem) {
            event.preventDefault();
            focusPlaylistItem(playlistItem);
            return;
        }
        const card = event.target.closest('[data-site-target="#ytModal"][data-ytid]');
        if (!card) return;
        event.preventDefault();
        openVideo(card);
    });

    function focusPlaylistItem(link) {
        document.querySelectorAll('#mediaPlaylistList .media-playlist__item').forEach((item) => {
            item.classList.toggle('is-current', item === link);
        });
        applyHero(videoFrom(link));
    }

    ytModal?.addEventListener('site-modal:hidden', () => {
        requestedVideoId = null;
        if (youtubePlayer) {
            youtubePlayer.stopVideo();
        } else if (ytPlayer?.hasAttribute('src')) {
            youtubePlayerReady = null;
            // Detach the old iframe so late API callbacks cannot affect the next video.
            const replacement = ytPlayer.cloneNode(false);
            replacement.removeAttribute('src');
            ytPlayer.replaceWith(replacement);
            ytPlayer = replacement;
        }
    });

    function upgradeStills() {
    document.querySelectorAll('img[data-yt-still]').forEach((img) => {
        if (img.dataset.stillBound === '1') return;
        img.dataset.stillBound = '1';
        const use = (name) => {
            const id = img.dataset.ytStill;
            if (!id) return;
            img.dataset.fallback = name;
            img.src = 'https://i.ytimg.com/vi/' + id + '/' + name + '.jpg';
        };
        const soften = () => {
            if (img.dataset.fallback === 'hqdefault') return;
            if (img.naturalWidth > 0 && img.naturalWidth <= 120) {
                use(img.dataset.fallback === 'sddefault' ? 'hqdefault' : 'sddefault');
            }
        };
        img.addEventListener('error', () => {
            if (img.dataset.fallback === 'sddefault') use('hqdefault');
            else if (img.dataset.fallback !== 'hqdefault') use('sddefault');
        });
        img.addEventListener('load', soften);
        if (!img.dataset.ytStill) return;
        if (img.complete && img.naturalWidth === 0) use('sddefault');
        else if (img.complete) soften();
    });
    }

    capturePlaylist();
    upgradeStills();
    renderPlaylist();

    const SYNC_KEY = 'triples-media-channel-synced';
    const syncForm = document.querySelector('form.media-sync');
    syncForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        event.stopPropagation();
        const message = syncForm.getAttribute('data-confirm');
        if (message) {
            const ok = window.SiteFeedback?.confirm
                ? await window.SiteFeedback.confirm(message)
                : window.confirm(message);
            if (!ok) return;
        }
        const button = syncForm.querySelector('button');
        if (button) button.disabled = true;
        try {
            const response = await fetch(syncForm.action, {
                method: 'POST',
                body: new FormData(syncForm),
                credentials: 'same-origin'
            });
            if (!response.ok) throw new Error(String(response.status));
            localStorage.setItem(SYNC_KEY, '1');
            document.documentElement.classList.add('media-sync-done');
            window.location.reload();
        } catch (error) {
            if (button) button.disabled = false;
            const fail = '채널 동기화에 실패했습니다.';
            if (window.SiteFeedback?.alert) window.SiteFeedback.alert(fail);
            else window.alert(fail);
        }
    });
})();
