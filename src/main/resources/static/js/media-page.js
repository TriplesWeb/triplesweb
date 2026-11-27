(function () {
    const form = document.getElementById('mediaFilterForm');
    const sort = document.getElementById('mediaSort');
    const panel = document.getElementById('mediaMemberPanel');
    const toggle = document.getElementById('mediaMemberToggle');
    const label = toggle?.querySelector('[data-member-toggle-label]');
    const memberBox = document.getElementById('mediaMemberInputs');
    const memberCount = document.getElementById('mediaMemberCount');
    const search = form?.querySelector('.ar-search');
    let refreshSavedMedia;

    function revealActiveSignalEra() {
        const active = document.querySelector('.media-era-tabs [aria-current="page"]');
        const tabs = active?.closest('.media-tabs');
        if (!tabs) return;
        const itemBounds = active.getBoundingClientRect();
        const tabBounds = tabs.getBoundingClientRect();
        if (itemBounds.right > tabBounds.right) tabs.scrollLeft += itemBounds.right - tabBounds.right;
        if (itemBounds.left < tabBounds.left) tabs.scrollLeft -= tabBounds.left - itemBounds.left;
    }

    revealActiveSignalEra();

    function selectedMembers() {
        if (!window.MemberPortraitPicker || !panel) return [];
        return MemberPortraitPicker.selectedValues(panel);
    }

    function memberKey() {
        return selectedMembers().join('\n');
    }

    function paintMembers() {
        const values = selectedMembers();
        toggle?.closest('.ar-member-filter')?.classList.toggle('is-filtered', values.length > 0);
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
        if (window.MemberSelectionSummary) {
            MemberSelectionSummary.paintToggle(toggle, memberCount, values, '멤버 선택');
        } else {
            const count = values.length;
            if (label) label.textContent = count === 0 ? '멤버 선택' : count === 1 ? values[0] : count + '명';
            if (memberCount) {
                memberCount.hidden = count === 0;
                memberCount.textContent = String(count);
            }
            toggle?.classList.toggle('has-selection', count > 0);
        }
    }

    function enhanceSort(select, ariaLabel) {
        const control = select?.closest('.ar-sort-control');
        if (!select || !control || control.classList.contains('is-enhanced')) return;
        control.classList.add('is-enhanced');
        select.setAttribute('aria-hidden', 'true');
        select.tabIndex = -1;
        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'ar-sort-trigger';
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');
        trigger.setAttribute('aria-label', ariaLabel || select.getAttribute('aria-label') || '정렬');
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

    const COLS_KEY = 'triples-media-archive-cols';
    const archiveGrid = document.querySelector('.media-grid');
    const serverHasFiveColumns = archiveGrid?.dataset.cols === '5';
    const requestedCols = new URLSearchParams(location.search).get('cols');
    const storedCols = localStorage.getItem(COLS_KEY);
    let selectedCols = ['3', '4', '5'].includes(requestedCols) ? requestedCols
        : (storedCols === '4' || storedCols === '5' ? storedCols : '3');
    function mediaColsFilter() {
        return document.querySelector('[data-media-cols-filter]');
    }
    function mediaColsMenu() {
        return document.querySelector('[data-media-cols-menu]');
    }
    function setMediaColsOpen(open) {
        const filter = mediaColsFilter();
        const menu = mediaColsMenu();
        const toggle = filter?.querySelector('[data-media-cols-toggle]');
        if (!menu) return;
        menu.hidden = !open;
        filter?.classList.toggle('is-open', open);
        toggle?.setAttribute('aria-expanded', open ? 'true' : 'false');
    }
    function paintMediaCols() {
        const cols = selectedCols;
        document.querySelectorAll('.media-grid').forEach((grid) => {
            grid.dataset.cols = cols;
        });
        document.querySelectorAll('[data-media-cols-label]').forEach((el) => {
            el.textContent = cols + '줄';
        });
        document.querySelectorAll('[data-media-cols-option]').forEach((btn) => {
            const on = btn.getAttribute('data-media-cols-option') === cols;
            btn.classList.toggle('is-active', on);
            btn.setAttribute('aria-selected', on ? 'true' : 'false');
        });
        const toggle = mediaColsFilter()?.querySelector('[data-media-cols-toggle]');
        if (toggle) toggle.setAttribute('aria-label', '한 줄에 볼 영상 수, 현재 ' + cols + '줄');
    }
    document.addEventListener('click', (event) => {
        const filter = event.target.closest('[data-media-cols-filter]');
        if (!filter) {
            setMediaColsOpen(false);
            return;
        }
        if (event.target.closest('[data-media-cols-toggle]')) {
            const menu = mediaColsMenu();
            if (!menu) return;
            const willOpen = menu.hidden;
            window.closeArchiveOverlays?.(filter);
            setMediaColsOpen(willOpen);
            return;
        }
        const cols = event.target.closest('[data-media-cols-option]')?.dataset.mediaColsOption;
        if (cols === '3' || cols === '4' || cols === '5') {
            const pageSizeChanged = (cols === '5') !== (selectedCols === '5');
            selectedCols = cols;
            localStorage.setItem(COLS_KEY, cols);
            paintMediaCols();
            setMediaColsOpen(false);
            if (pageSizeChanged) form?.dispatchEvent(new Event('media-page-size-change'));
        }
    });
    paintMediaCols();

    if (form && sort) {
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
        enhanceSort(sort, '정렬');
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

        async function applyResults(nextHref, fromPager, replaceHistory, keepPosition = false) {
            const nextUrl = new URL(nextHref || filterUrl(), location.href);
            if (!keepPosition) {
                if (document.querySelector('.media-archive') && selectedCols === '5') nextUrl.searchParams.set('cols', '5');
                else nextUrl.searchParams.delete('cols');
            }
            const url = nextUrl.pathname + nextUrl.search;
            const seq = ++filterSeq;
            const y = window.scrollY;
            try {
                const response = await fetch(url, { credentials: 'same-origin' });
                if (seq !== filterSeq) return;
                if (!response.ok) throw new Error(String(response.status));
                const html = await response.text();
                if (seq !== filterSeq) return;
                const doc = new DOMParser().parseFromString(html, 'text/html');
                const shell = document.querySelector('main.media-shell');
                const nextShell = doc.querySelector('main.media-shell');
                if (!shell || !nextShell) throw new Error('shell');
                const hasFeature = !!shell.querySelector('.media-feature');
                const needsFeature = !!nextShell.querySelector('.media-feature');
                if (hasFeature !== needsFeature) {
                    swapSection(shell, nextShell, 'media-feature');
                    capturePlaylist();
                    renderPlaylist();
                }
                swapSection(shell, nextShell, 'media-archive');
                swapSection(shell, nextShell, 'media-shorts');
                const eraTabs = shell.querySelector('.media-era-tabs');
                const nextEraTabs = nextShell.querySelector('.media-era-tabs');
                if (eraTabs && nextEraTabs) {
                    eraTabs.replaceWith(document.importNode(nextEraTabs, true));
                    revealActiveSignalEra();
                }
                const catalog = document.getElementById('mediaCatalog');
                const nextCatalog = doc.getElementById('mediaCatalog');
                if (catalog && nextCatalog) {
                    catalog.textContent = nextCatalog.textContent;
                    reloadCatalog();
                }
                const reset = form.querySelector('.ar-reset');
                const nextReset = nextShell.querySelector('.ar-reset');
                if (reset && nextReset) reset.hidden = nextReset.hidden;
                upgradeStills();
                const same = nextUrl.pathname === location.pathname && nextUrl.search === location.search;
                if (!same) {
                    if (replaceHistory) history.replaceState({ mediaLive: true }, '', url);
                    else history.pushState({ mediaLive: true }, '', url);
                }
                paintMediaCols();
                if (fromPager) document.querySelector('.media-archive')?.scrollIntoView({ block: 'start' });
                else window.scrollTo(0, y);
            } catch (error) {
                if (keepPosition) throw error;
                if (seq === filterSeq) window.location.assign(url);
            }
        }

        refreshSavedMedia = () => applyResults(location.href, false, true, true);
        form.addEventListener('media-page-size-change', () => {
            window.clearTimeout(filterTimer);
            applyResults();
        });
        if (archiveGrid && (selectedCols === '5') !== serverHasFiveColumns) {
            applyResults(location.href, false, true);
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
            ++filterSeq;
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
            toggle.closest('.ar-member-filter')?.classList.toggle('is-open', willOpen);
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

        (function waitForYoutubeSearch() {
            let tries = 0;
            let sawPending = false;
            const max = 24;
            const tick = async () => {
                try {
                    const response = await fetch('/api/media/youtube/search-status', { credentials: 'same-origin' });
                    const type = response.headers.get('content-type') || '';
                    if (!response.ok || !type.includes('application/json')) return;
                    const status = await response.json();
                    if (status.ready) {
                        if (sawPending && filterSeq === 0 && !composing &&
                            !document.querySelector('#mediaPickModal.is-open, #ytModal.is-open, #mediaPickMemberModal.is-open')) {
                            await applyResults(location.href);
                        }
                        return;
                    }
                    sawPending = true;
                } catch (error) {
                    return;
                }
                tries += 1;
                if (tries < max) window.setTimeout(tick, 2500);
            };
            tick();
        })();
    }

    const PLAYLIST_KEY = 'triples-media-playlist';
    const PLAYLIST_COLLAPSED_KEY = 'triples-media-playlist-collapsed';
    let playlistList = null;
    let playlistTitle = null;
    let playlistCount = null;
    let originalTitle = '추천 재생목록';
    let originalCount = '00';
    let featuredVideo = null;
    let recommendations = new Map();
    let recommendationAnchor = null;
    let recommendationRequest = null;

    function capturePlaylist() {
        playlistList = document.getElementById('mediaPlaylistList');
        playlistTitle = document.getElementById('mediaPlaylistTitle');
        playlistCount = document.getElementById('mediaPlaylistCount');
        originalTitle = playlistTitle ? playlistTitle.textContent : '추천 재생목록';
        originalCount = playlistCount ? playlistCount.textContent : '00';
        paintPlaylistCollapse(localStorage.getItem(PLAYLIST_COLLAPSED_KEY) === '1');
    }

    function paintPlaylistCollapse(collapsed) {
        const playlist = document.getElementById('mediaPlaylist');
        const toggle = playlist?.querySelector('[data-media-playlist-toggle]');
        if (!toggle || !playlistList) return;
        playlist.classList.toggle('is-collapsed', collapsed);
        playlistList.hidden = collapsed;
        toggle.setAttribute('aria-expanded', String(!collapsed));
        toggle.setAttribute('aria-label', collapsed ? '재생목록 펼치기' : '재생목록 접기');
        toggle.textContent = collapsed ? '+' : '−';
    }

    document.addEventListener('click', (event) => {
        const toggle = event.target.closest('[data-media-playlist-toggle]');
        if (!toggle) return;
        const collapsed = toggle.getAttribute('aria-expanded') === 'true';
        localStorage.setItem(PLAYLIST_COLLAPSED_KEY, collapsed ? '1' : '0');
        paintPlaylistCollapse(collapsed);
    });

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
        const saved = readPlaylist();
        paintAddButtons(saved);
        const tab = form?.elements.namedItem('tab')?.value || 'all';
        const era = form?.elements.namedItem('era')?.value;
        const items = saved.map((item) => byId.get(item.youtubeId) || item).filter((item) =>
            (tab === 'all' || item.tab === tab) && (!era || item.signalEra === era));
        const playlist = document.getElementById('mediaPlaylist');
        playlist?.classList.toggle('is-managing', items.length > 0);
        if (!playlistList) {
            paintSuggestions(null);
            return;
        }
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
        featuredVideo = null;
        const hero = document.querySelector('.media-feature__hero');
        if (!hero) return;
        hero.classList.add('is-empty');
        hero.removeAttribute('data-ytid');
        hero.removeAttribute('data-site-toggle');
        hero.removeAttribute('data-site-target');
        hero.href = '/media';
        const still = hero.querySelector('.media-feature__still');
        if (still) {
            still.dataset.ytStill = '';
            still.dataset.noPlaceholder = '1';
            still.removeAttribute('src');
            still.removeAttribute('srcset');
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

    async function paintSuggestions(anchor) {
        const box = document.getElementById('mediaSuggest');
        const list = document.getElementById('mediaSuggestList');
        const youtubeId = list ? anchor?.youtubeId || null : null;
        if (youtubeId === recommendationAnchor) return;
        recommendationAnchor = youtubeId;
        recommendationRequest?.abort();
        recommendationRequest = null;
        recommendations = new Map();
        if (!box || !list) return;
        list.replaceChildren();
        setCollapsed(box, true);
        if (!youtubeId) return;

        const request = new AbortController();
        recommendationRequest = request;
        try {
            const params = new URLSearchParams({ youtubeId, tab: anchor.tab || '' });
            const response = await fetch('/media/recommendations.json?' + params, {
                credentials: 'same-origin',
                signal: request.signal
            });
            if (!response.ok) throw new Error(String(response.status));
            const items = await response.json();
            if (request !== recommendationRequest || !list.isConnected) return;
            recommendations = new Map(items.map((item) => [item.youtubeId, item]));
            list.replaceChildren(...items.map(suggestCard));
            setCollapsed(box, items.length === 0);
        } catch (error) {
            if (request === recommendationRequest) recommendationAnchor = null;
        } finally {
            if (request === recommendationRequest) recommendationRequest = null;
        }
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
        ytPlayer.src = 'https://www.youtube.com/embed/' + encodeURIComponent(id) + '?rel=0&enablejsapi=1&playsinline=1';
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
        const row = videoFrom(trigger);
        currentVideo = row;
        const shortClip = row.tab === 'shorts';
        ytModal.classList.toggle('is-short-player', shortClip);
        ytPlayer.title = row.title || 'YouTube 영상 재생';
        playMediaVideo(id);
        text('ytModalTitle', shortClip ? 'Shorts & 릴스' : (row.title || '영상 재생'));
        text('ytModalKind', shortClip ? '' : (row.tabLabel || ''));
        const source = document.getElementById('ytModalSource');
        if (source) {
            source.hidden = !shortClip;
            if (shortClip) source.href = 'https://www.youtube.com/shorts/' + encodeURIComponent(id);
            else source.removeAttribute('href');
        }
        text('ytModalHeading', row.title || '');
        const bits = [];
        if (row.viewCountFmt) bits.push('조회수 ' + row.viewCountFmt);
        if (row.publishedLabel) bits.push(row.publishedLabel);
        if (row.duration) bits.push(row.duration);
        text('ytModalMeta', bits.join(' · '));
        paintModalMembers(row);
        const description = String(row.description || '').trim();
        const descriptionNode = document.getElementById('ytModalDesc');
        if (descriptionNode) descriptionNode.hidden = shortClip && !description;
        text('ytModalDesc', description || (shortClip ? '' : '등록된 상세 설명이 없습니다.'));
        const deleteTab = document.getElementById('ytDeleteTab');
        const deleteId = document.getElementById('ytDeleteId');
        if (deleteTab) deleteTab.value = row.tab || '';
        if (deleteId) deleteId.value = id;
        showModal(ytModal);
    }

    function paintModalMembers(video) {
        const cast = document.getElementById('ytModalCast');
        const members = document.getElementById('ytModalMembers');
        const unit = document.getElementById('ytModalUnit');
        if (!cast || !members) return;
        members.replaceChildren();
        members.classList.remove('is-many');
        delete members.dataset.memberCount;
        if (unit) {
            unit.hidden = true;
            unit.textContent = '';
            unit.removeAttribute('title');
            unit.removeAttribute('href');
            unit.style.removeProperty('--unit-color');
        }
        const badges = Array.isArray(video.memberBadges) ? video.memberBadges : [];
        if (!badges.length) {
            cast.hidden = true;
            return;
        }
        if (badges.length === 1 && badges[0].fullLineup) {
            const link = document.createElement('a');
            link.className = 'ar-detail-lineup media-full-lineup';
            link.href = '/triples';
            link.setAttribute('aria-label', 'tripleS 완전체 24명');
            for (const symbol of badges[0].symbols || []) {
                const image = document.createElement('img');
                image.src = symbol.image;
                image.alt = '';
                image.title = symbol.name;
                image.width = 20;
                image.height = 20;
                image.dataset.noPlaceholder = '1';
                link.append(image);
            }
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
            const photo = String(badge.portrait || badge.image || '');
            if (photo && !photo.includes('image-upload-pending')) {
                const img = document.createElement('img');
                img.src = photo;
                img.alt = badge.name || '';
                img.referrerPolicy = 'no-referrer';
                img.dataset.noPlaceholder = '1';
                chip.append(img);
            }
            const label = document.createElement('span');
            label.textContent = badge.name || '';
            chip.append(label);
            members.append(chip);
        });
        members.dataset.memberCount = String(badges.length);
        members.classList.toggle('is-many', badges.length >= 10);
        const names = badges.map((badge) => String(badge.name || '').trim()).filter(Boolean);
        const summary = window.MemberSelectionSummary?.describe?.(names);
        if (unit && summary?.kind === 'unit' && summary.label) {
            unit.hidden = false;
            unit.textContent = summary.label;
            unit.title = summary.label;
            unit.style.setProperty('--unit-color', '#a78bfa');
        }
        cast.hidden = false;
    }

    function showModal(modal) {
        if (!modal) return;
        if (modal.parentElement !== document.body) document.body.appendChild(modal);
        modal.style.zIndex = '5000';
        window.SiteUi?.modal(modal).show();
    }

    function openArDialog(dialog) {
        if (!dialog) return;
        if (dialog.parentElement !== document.body) document.body.appendChild(dialog);
        dialog.hidden = false;
        document.body.style.overflow = 'hidden';
        dialog.querySelector('input, select, textarea, button')?.focus({ preventScroll: true });
        window.bindArchiveDateFields?.(dialog);
    }

    function closeArDialog(dialog) {
        if (!dialog) return;
        dialog.hidden = true;
        if (!document.querySelector('.ar-dialog:not([hidden])')) document.body.style.overflow = '';
    }

    function closeArDialogButton(event) {
        const close = event.target.closest('[data-ar-close]');
        if (!close) return;
        const dialog = close.closest('.ar-dialog');
        if (dialog) {
            event.preventDefault();
            closeArDialog(dialog);
        }
    }
    document.addEventListener('click', closeArDialogButton);

    function bindMemberForm(root) {
        if (!root || !window.MemberPortraitPicker) return;
        const picker = root.querySelector('.member-portrait-picker');
        if (!picker) return;
        const paint = () => {
            window.MemberSelectionSummary?.paintArtistMeta(root);
            window.UnitCheckboxGroup?.syncFromMembers(root.querySelector('[data-unit-checks]'), MemberPortraitPicker.selectedValues(picker));
        };
        MemberPortraitPicker.bindMultiSelect(picker, { multi: true, onChange: paint });
        window.UnitCheckboxGroup?.bindMemberHelper(root.querySelector('[data-unit-checks]'), {
            getMembers: () => MemberPortraitPicker.selectedValues(picker),
            applyMembers: (names) => {
                MemberPortraitPicker.setValues(picker, names);
                paint();
            }
        });
        paint();
    }

    function syncEditMembers() {
        const root = document.getElementById('mediaEditForm');
        window.MemberSelectionSummary?.paintArtistMeta(root);
    }

    const mediaEditForm = document.getElementById('mediaEditForm');
    bindMemberForm(mediaEditForm);
    mediaEditForm?.addEventListener('submit', (event) => {
        event.preventDefault();
        saveMediaForm(mediaEditForm);
    });
    bindMemberForm(document.getElementById('mediaAddForm'));

    function videoFrom(trigger) {
        const id = trigger?.getAttribute('data-ytid') || '';
        return byId.get(id) || recommendations.get(id) || (featuredVideo?.youtubeId === id ? featuredVideo : null) || {
            youtubeId: id,
            tab: trigger?.dataset.tab || '',
            tabLabel: trigger?.dataset.kind || '',
            title: trigger?.dataset.title || trigger?.querySelector('strong')?.textContent || '',
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
        window.UnitCheckboxGroup?.syncFromMembers(editModal.querySelector('[data-unit-checks]'), names);
        openArDialog(editModal);
    }

    function applyHero(item) {
        const hero = document.querySelector('.media-feature__hero');
        if (!hero || !item || !item.youtubeId) return;
        featuredVideo = item;
        hero.classList.remove('is-empty');
        const tab = item.tab || 'mv';
        hero.href = '/media/' + encodeURIComponent(tab) + '/' + encodeURIComponent(item.youtubeId);
        hero.dataset.ytid = item.youtubeId;
        hero.dataset.siteToggle = 'modal';
        hero.dataset.siteTarget = '#ytModal';
        const still = hero.querySelector('.media-feature__still');
        if (still) {
            still.dataset.noPlaceholder = '1';
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
        const current = playlistList?.querySelector('.media-playlist__item');
        if (current) focusPlaylistItem(current);
    }

    function durationSeconds(raw) {
        const parts = String(raw || '').trim().split(':');
        if (parts.length < 2 || parts.length > 3) return null;
        const nums = parts.map((part) => Number(part));
        if (nums.some((num) => Number.isNaN(num))) return null;
        return nums.length === 2 ? nums[0] * 60 + nums[1] : nums[0] * 3600 + nums[1] * 60 + nums[2];
    }

    let pickRows = null;
    let pickRowsPending = null;
    const pickFilters = document.getElementById('mediaPickMini');
    const pickDateFilter = pickFilters?.querySelector('[data-date-filter]');

    function pickDateRange() {
        const from = pickDateFilter?.querySelector('[data-date-from]')?.value || '';
        const to = pickDateFilter?.querySelector('[data-date-to]')?.value || '';
        return from && to && from > to ? [to, from] : [from, to];
    }

    function foldPickTitle(value) {
        return String(value || '').normalize('NFC').toLowerCase()
            .replace(/[\u0000-\u001f\u007f-\u009f\u200b\ufeff]/g, '').replace(/\s+/g, ' ').trim();
    }

    function pickCatalog() {
        if (Array.isArray(pickRows)) return pickRows;
        const node = document.getElementById('mediaPickCatalog');
        let rows = [];
        try {
            rows = JSON.parse(node?.textContent || '[]');
        } catch (error) {
            rows = [];
        }
        if (!Array.isArray(rows) || !rows.length) return [...byId.values()];
        return rows.filter((row) => row?.youtubeId);
    }

    function loadPickCatalog() {
        if (Array.isArray(pickRows)) return Promise.resolve(pickRows);
        if (pickRowsPending) return pickRowsPending;
        pickRowsPending = fetch('/media/pick-catalog.json?includeShorts=true', {
            credentials: 'same-origin',
            headers: { Accept: 'application/json' }
        }).then((response) => {
            if (!response.ok) throw new Error('pick catalog');
            return response.json();
        }).then((rows) => {
            pickRows = Array.isArray(rows) ? rows.filter((row) => row?.youtubeId) : [];
            return pickRows;
        }).catch(() => {
            pickRowsPending = null;
            return pickCatalog();
        });
        return pickRowsPending;
    }

    function pickSelectedMembers() {
        const picker = document.getElementById('media-pick-picker');
        if (!window.MemberPortraitPicker || !picker) return [];
        return MemberPortraitPicker.selectedValues(picker);
    }

    function pickFiltered() {
        const tab = document.getElementById('mediaPickTab')?.value || '';
        const query = foldPickTitle(document.getElementById('mediaPickSearch')?.value);
        const sort = document.getElementById('mediaPickSort')?.value || 'latest';
        const [from, to] = pickDateRange();
        const members = pickSelectedMembers().map((name) => String(name).trim().toLowerCase().replace(/\s+/g, ''));
        const rows = pickCatalog().filter((row) => {
            if (tab && row.tab !== tab) return false;
            if (query && !foldPickTitle(row.title).includes(query)) return false;
            if (from || to) {
                const day = String(row.publishedOn || '').slice(0, 10);
                if (!day || (from && day < from) || (to && day > to)) return false;
            }
            if (members.length) {
                if (row.memberBadges?.some((badge) => badge.fullLineup) && members.length < 24) return false;
                const names = String(row.artistNames || '').split(',').map((name) => name.trim().toLowerCase().replace(/\s+/g, '')).filter(Boolean);
                if (!members.some((member) => names.includes(member))) return false;
            }
            return true;
        });
        const published = (row) => Date.parse(String(row.publishedOn || '').slice(0, 10));
        const views = (row) => Number(row.views) || 0;
        const compare = (left, right, descending = false) => {
            const leftKnown = Number.isFinite(left);
            const rightKnown = Number.isFinite(right);
            if (!leftKnown || !rightKnown) return Number(rightKnown) - Number(leftKnown);
            return descending ? right - left : left - right;
        };
        const id = (row) => Number(row.id) || 0;
        rows.sort((left, right) => {
            if (sort === 'registered') return id(right) - id(left);
            if (sort === 'oldest') return compare(published(left), published(right)) || id(left) - id(right);
            if (sort === 'views') return views(right) - views(left) || id(right) - id(left);
            if (sort === 'short') return compare(durationSeconds(left.duration), durationSeconds(right.duration)) || id(left) - id(right);
            if (sort === 'long') {
                return compare(durationSeconds(left.duration), durationSeconds(right.duration), true) || id(right) - id(left);
            }
            return compare(published(left), published(right), true) || id(right) - id(left);
        });
        return rows;
    }

    const PICK_PAGE_SIZE = 12;
    let pickPage = 1;
    const pickChecked = new Set();

    function paintPickPage() {
        const grid = document.getElementById('mediaPickGrid');
        const pager = document.getElementById('mediaPickPager');
        if (!grid) return;
        grid.replaceChildren();
        const videos = pickFiltered();
        if (!videos.length) {
            const empty = document.createElement('p');
            empty.className = 'media-pick-empty';
            empty.textContent = '담을 수 있는 영상이 없습니다.';
            grid.appendChild(empty);
            pager?.replaceChildren();
            return;
        }
        const totalPages = Math.max(1, Math.ceil(videos.length / PICK_PAGE_SIZE));
        pickPage = Math.min(totalPages, Math.max(1, pickPage));
        const start = (pickPage - 1) * PICK_PAGE_SIZE;
        videos.slice(start, start + PICK_PAGE_SIZE).forEach((item) => {
            const card = document.createElement('label');
            card.className = 'media-pick-card';
            const check = document.createElement('input');
            check.type = 'checkbox';
            check.className = 'media-pick-check';
            check.value = item.youtubeId;
            check.checked = pickChecked.has(item.youtubeId);
            check.setAttribute('aria-label', (item.title || '영상') + ' 선택');
            check.addEventListener('change', () => {
                if (check.checked) pickChecked.add(item.youtubeId);
                else pickChecked.delete(item.youtubeId);
            });
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
        if (!pager) return;
        pager.replaceChildren();
        if (totalPages < 2) return;
        const nav = document.createElement('nav');
        nav.className = 'ar-panel ar-pager';
        nav.setAttribute('aria-label', '재생목록 영상 페이지');
        const group = document.createElement('div');
        group.className = 'ar-pager-nav';
        const prev = document.createElement('button');
        prev.type = 'button';
        prev.className = 'ar-pager-step' + (pickPage <= 1 ? ' is-disabled' : '');
        prev.textContent = '이전';
        prev.addEventListener('click', () => { pickPage -= 1; paintPickPage(); });
        const pages = document.createElement('div');
        pages.className = 'ar-pages';
        const from = Math.max(1, Math.min(pickPage - 2, Math.max(1, totalPages - 4)));
        const to = Math.min(totalPages, from + 4);
        for (let i = from; i <= to; i += 1) {
            const num = document.createElement('button');
            num.type = 'button';
            num.className = 'ar-pager-num' + (i === pickPage ? ' is-current' : '');
            num.textContent = String(i);
            num.addEventListener('click', () => { pickPage = i; paintPickPage(); });
            pages.append(num);
        }
        const next = document.createElement('button');
        next.type = 'button';
        next.className = 'ar-pager-step' + (pickPage >= totalPages ? ' is-disabled' : '');
        next.textContent = '다음';
        next.addEventListener('click', () => { pickPage += 1; paintPickPage(); });
        group.append(prev, pages, next);
        const jump = document.createElement('form');
        jump.className = 'ar-page-jump';
        jump.setAttribute('aria-label', '페이지 이동');
        const meta = document.createElement('span');
        meta.className = 'ar-page-jump-meta';
        meta.append(String(pickPage), Object.assign(document.createElement('span'), { textContent: '/' }), String(totalPages));
        meta.querySelector('span').setAttribute('aria-hidden', 'true');
        const field = document.createElement('label');
        field.className = 'ar-page-jump-field';
        const input = document.createElement('input');
        input.type = 'number';
        input.min = '1';
        input.max = String(totalPages);
        input.value = String(pickPage);
        input.setAttribute('aria-label', '이동할 페이지');
        field.append(input);
        const go = document.createElement('button');
        go.type = 'submit';
        go.textContent = '이동';
        jump.append(meta, field, go);
        jump.addEventListener('submit', (event) => {
            event.preventDefault();
            pickPage = Number(input.value) || 1;
            paintPickPage();
        });
        nav.append(group, jump);
        pager.append(nav);
    }

    function openPickModal() {
        const modal = document.getElementById('mediaPickModal');
        if (!modal) return;
        pickPage = 1;
        pickChecked.clear();
        showModal(modal);
        loadPickCatalog().then(() => paintPickPage());
    }

    function resetPickPage() {
        pickPage = 1;
        paintPickPage();
    }
    document.getElementById('mediaPickSearch')?.addEventListener('input', resetPickPage);
    document.getElementById('mediaPickTab')?.addEventListener('change', resetPickPage);
    document.getElementById('mediaPickSort')?.addEventListener('change', resetPickPage);
    function applyPickDates() {
        if (!pickDateFilter) return;
        const [from, to] = pickDateRange();
        const label = pickDateFilter.querySelector('[data-date-toggle] .truncate');
        if (label) label.textContent = from || to ? from + ' ~ ' + to : '전체';
        pickDateFilter.classList.toggle('is-filtered', Boolean(from || to));
        resetPickPage();
    }
    pickFilters?.addEventListener('submit', (event) => {
        event.preventDefault();
        const popover = pickDateFilter?.querySelector('[data-date-popover]');
        if (popover && !popover.hidden && window.closeArchiveOverlays) window.closeArchiveOverlays();
        else applyPickDates();
    });
    pickDateFilter?.addEventListener('archive-date-close', applyPickDates);
    enhanceSort(document.getElementById('mediaPickTab'), '분류');
    enhanceSort(document.getElementById('mediaPickSort'), '정렬');
    (function bindPickMembers() {
        const toggle = document.getElementById('mediaPickMemberToggle');
        const count = document.getElementById('mediaPickMemberCount');
        const modal = document.getElementById('mediaPickMemberModal');
        const picker = document.getElementById('media-pick-picker');
        if (!toggle || !modal) return;
        const paintToggle = () => {
            const names = pickSelectedMembers();
            toggle.closest('.ar-member-filter')?.classList.toggle('is-filtered', names.length > 0);
            toggle.classList.toggle('is-open', modal.classList.contains('is-open'));
            toggle.setAttribute('aria-expanded', modal.classList.contains('is-open') ? 'true' : 'false');
            window.MemberSelectionSummary?.paintToggle(toggle, count, names, '멤버 선택');
        };
        toggle.addEventListener('click', () => {
            showModal(modal);
            modal.style.zIndex = '5100';
            paintToggle();
        });
        modal.addEventListener('site-modal:hidden', () => {
            paintToggle();
            resetPickPage();
        });
        if (window.MemberPortraitPicker && picker) {
            MemberPortraitPicker.bindMultiSelect(picker, {
                multi: true,
                onChange: paintToggle
            });
        }
    })();

    function confirmPicks() {
        const existing = readPlaylist();
        const seen = new Set(existing.map((item) => item.youtubeId));
        const incoming = [];
        const catalog = new Map(pickCatalog().concat(pickFiltered()).map((row) => [row.youtubeId, row]));
        pickChecked.forEach((id) => {
            const item = catalog.get(id) || byId.get(id);
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
            if (item === link) item.setAttribute('aria-current', 'true');
            else item.removeAttribute('aria-current');
        });
        const saved = readPlaylist().find((item) => item.youtubeId === link.dataset.ytid);
        applyHero(byId.get(link.dataset.ytid) || saved || videoFrom(link));
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
        ytModal.classList.remove('is-short-player');
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

    function authHeaders(extra) {
        if (window.AboutAdminAuth?.applyAuthHeaders) return window.AboutAdminAuth.applyAuthHeaders(extra);
        return extra || {};
    }

    function setField(id, value) {
        const el = document.getElementById(id);
        if (el && value != null) el.value = value;
    }

    async function fetchYoutubeInfo() {
        const url = document.getElementById('mediaAddUrl')?.value.trim();
        const statusEl = document.getElementById('mediaAddStatus');
        if (!url) {
            window.SiteFeedback?.alert('URL 또는 ID를 입력해주세요.');
            return false;
        }
        if (statusEl) statusEl.textContent = '영상 정보를 불러오는 중...';
        try {
            const res = await fetch('/api/media/resolve?urlOrId=' + encodeURIComponent(url), { credentials: 'same-origin' });
            if (!res.ok) throw new Error('resolve failed');
            const data = await res.json();
            if (data.youtubeId) setField('mediaAddUrl', data.youtubeId);
            setField('mediaAddTitle', data.title || '');
            setField('mediaAddDuration', data.duration || '');
            setField('mediaAddViews', data.views != null ? data.views : '');
            setField('mediaAddViewsHidden', data.views != null ? data.views : '');
            if (data.publishedOn) {
                setField('mediaAddDate', data.publishedOn);
                document.getElementById('mediaAddDate')?.dispatchEvent(new Event('input'));
            }
            const thumb = document.getElementById('mediaAddThumb');
            if (thumb) {
                thumb.replaceChildren();
                if (data.thumbnail) {
                    const wrap = document.createElement('div');
                    wrap.className = 'media-add-thumb';
                    const hint = document.createElement('p');
                    hint.className = 'ar-field-hint';
                    hint.textContent = '썸네일 미리보기';
                    const img = document.createElement('img');
                    img.src = data.thumbnail;
                    img.alt = '썸네일';
                    wrap.append(hint, img);
                    thumb.append(wrap);
                }
            }
            if (statusEl) statusEl.textContent = '영상 정보를 불러왔습니다.';
            return true;
        } catch (error) {
            if (statusEl) statusEl.textContent = '영상 정보를 가져오지 못했습니다. URL을 확인하세요.';
            window.SiteFeedback?.alert('영상 정보를 가져오지 못했습니다.');
            return false;
        }
    }

    function resetAddForm() {
        const form = document.getElementById('mediaAddForm');
        if (!form) return;
        form.reset();
        document.getElementById('mediaAddThumb')?.replaceChildren();
        const status = document.getElementById('mediaAddStatus');
        if (status) status.textContent = '';
        const picker = document.getElementById('media-add-picker');
        if (picker && window.MemberPortraitPicker) {
            MemberPortraitPicker.clear(picker);
            window.MemberSelectionSummary?.paintArtistMeta(form);
        }
        window.UnitCheckboxGroup?.setChecks(form.querySelector('[data-unit-checks]'), []);
    }

    function openAddModal() {
        const dialog = document.getElementById('mediaAddModal');
        if (!dialog) return;
        resetAddForm();
        openArDialog(dialog);
    }

    document.getElementById('mediaAddOpen')?.addEventListener('click', (event) => {
        event.preventDefault();
        openAddModal();
    });
    document.getElementById('mediaAddFetch')?.addEventListener('click', () => { fetchYoutubeInfo(); });

    document.getElementById('mediaAddForm')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const form = event.target;
        window.MemberSelectionSummary?.paintArtistMeta(form);
        const title = document.getElementById('mediaAddTitle');
        if (!title?.value.trim()) {
            const ok = await fetchYoutubeInfo();
            if (!ok || !title?.value.trim()) {
                window.SiteFeedback?.alert('영상 정보를 먼저 가져와 주세요.');
                return;
            }
        }
        if (window.SiteFeedback?.confirm && !await window.SiteFeedback.confirm('이 영상을 등록하시겠습니까?')) return;
        const viewsHidden = document.getElementById('mediaAddViewsHidden');
        if (viewsHidden) viewsHidden.disabled = !viewsHidden.value;
        await saveMediaForm(form);
    });

    async function saveMediaForm(form) {
        const returnTo = form.elements.namedItem('returnTo');
        if (returnTo) returnTo.value = location.pathname + location.search;
        const submit = form.querySelector('button[type="submit"]');
        if (submit?.disabled) return;
        if (submit) submit.disabled = true;
        const doneBusy = window.SiteFeedback?.beginBusy?.();
        try {
            const response = await fetch(form.action, {
                method: 'POST',
                body: new FormData(form),
                credentials: 'same-origin',
                headers: authHeaders({ 'X-Requested-With': 'XMLHttpRequest' })
            });
            if (response.ok) {
                closeArDialog(form.closest('.ar-dialog'));
                try {
                    await refreshSavedMedia?.();
                } catch (_) {
                    window.SiteFeedback?.alert('저장했습니다. 목록을 새로 고치지 못했습니다.');
                }
                return;
            }
            const detail = (await response.text()).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
            window.SiteFeedback?.alert(detail || '영상을 저장하지 못했습니다.');
        } catch (error) {
            window.SiteFeedback?.alert('요청에 실패했습니다. 네트워크 연결을 확인해 주세요.');
        } finally {
            if (typeof doneBusy === 'function') doneBusy();
            if (submit) submit.disabled = false;
        }
    }

    if (window.__openMediaAdd) openAddModal();
})();
