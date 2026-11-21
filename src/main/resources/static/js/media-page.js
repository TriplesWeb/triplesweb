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
        let searchTimer = 0;
        enhanceSort(sort);
        sort.addEventListener('change', () => form.requestSubmit());

        function commitMembers() {
            paintMembers();
            const next = memberKey();
            if (next === appliedMembers) return;
            appliedMembers = next;
            form.requestSubmit();
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
                onChange: paintMembers
            });
        }
        form.addEventListener('archive-date-close', () => {
            const next = dateSnapshot();
            if (next === appliedDates) return;
            appliedDates = next;
            form.requestSubmit();
        });
        function scheduleSearch() {
            window.clearTimeout(searchTimer);
            searchTimer = window.setTimeout(() => {
                if (composing) return;
                form.requestSubmit();
            }, 450);
        }
        search?.addEventListener('compositionstart', () => {
            composing = true;
            window.clearTimeout(searchTimer);
        });
        search?.addEventListener('compositionend', () => {
            composing = false;
            scheduleSearch();
        });
        search?.addEventListener('input', () => {
            if (composing) return;
            scheduleSearch();
        });
        form.addEventListener('submit', () => {
            if (search && !search.value.trim()) search.disabled = true;
            memberBox?.querySelectorAll('input').forEach((input) => {
                if (!input.value.trim()) input.disabled = true;
            });
            form.querySelectorAll('[data-date-filter] input').forEach((input) => {
                if (!input.value.trim()) input.disabled = true;
            });
        });
    }

    const PLAYLIST_KEY = 'triples-media-playlist';
    const playlistList = document.getElementById('mediaPlaylistList');
    const playlistTitle = document.getElementById('mediaPlaylistTitle');
    const playlistCount = document.getElementById('mediaPlaylistCount');
    const originalPlaylist = playlistList ? playlistList.innerHTML : '';
    const originalTitle = playlistTitle ? playlistTitle.textContent : '추천 재생목록';
    const originalCount = playlistCount ? playlistCount.textContent : '00';

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

    function itemFrom(button) {
        return {
            youtubeId: button.dataset.ytid || '',
            tab: button.dataset.tab || 'mv',
            tabLabel: button.dataset.kind || '',
            title: button.dataset.title || '',
            duration: button.dataset.duration || '',
            viewCountFmt: button.dataset.views || '',
            publishedLabel: button.dataset.published || ''
        };
    }

    function paintAddButtons(items) {
        const ids = new Set(items.map((item) => item.youtubeId));
        document.querySelectorAll('[data-playlist-add]').forEach((button) => {
            const added = ids.has(button.dataset.ytid || '');
            button.classList.toggle('is-added', added);
            button.setAttribute('aria-pressed', added ? 'true' : 'false');
            const icon = button.querySelector('.material-symbols-outlined');
            if (icon) icon.textContent = added ? 'check' : 'add';
            const featured = Boolean(button.closest('.media-archive__head'));
            button.setAttribute('aria-label', added
                ? (featured ? '주목 영상을 재생목록에서 빼기' : '재생목록에서 빼기')
                : (featured ? '주목 영상을 재생목록에 추가' : '재생목록에 추가'));
        });
    }

    function playlistRow(item) {
        const row = document.createElement('div');
        row.className = 'media-playlist__row';
        const link = document.createElement('a');
        link.className = 'media-playlist__item';
        link.href = '/media/' + encodeURIComponent(item.tab || 'mv') + '/' + encodeURIComponent(item.youtubeId);
        link.dataset.siteToggle = 'modal';
        link.dataset.siteTarget = '#ytModal';
        link.dataset.ytid = item.youtubeId;
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

    function renderPlaylist() {
        const items = readPlaylist();
        paintAddButtons(items);
        if (!playlistList) return;
        if (!items.length) {
            playlistList.innerHTML = originalPlaylist;
            if (playlistTitle) playlistTitle.textContent = originalTitle;
            if (playlistCount) playlistCount.textContent = originalCount;
            return;
        }
        if (playlistTitle) playlistTitle.textContent = '재생목록';
        if (playlistCount) playlistCount.textContent = String(items.length).padStart(2, '0');
        playlistList.replaceChildren(...items.map(playlistRow));
    }

    function togglePlaylist(button) {
        const item = itemFrom(button);
        if (!item.youtubeId) return;
        const items = readPlaylist();
        const index = items.findIndex((row) => row.youtubeId === item.youtubeId);
        if (index >= 0) items.splice(index, 1);
        else items.unshift(item);
        writePlaylist(items);
        renderPlaylist();
    }

    function removePlaylist(id) {
        writePlaylist(readPlaylist().filter((item) => item.youtubeId !== id));
        renderPlaylist();
    }

    const catalogNode = document.getElementById('mediaCatalog');
    let catalog = [];
    try {
        catalog = JSON.parse(catalogNode?.textContent || '[]');
    } catch (error) {
        catalog = [];
    }
    const byId = new Map(catalog.map((row) => [row.youtubeId, row]));
    const ytModal = document.getElementById('ytModal');
    const ytPlayer = document.getElementById('ytPlayer');
    const editModal = document.getElementById('mediaEditModal');
    const editPicker = document.getElementById('media-edit-picker');
    let currentVideo = null;

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
        ytPlayer.src = 'https://www.youtube.com/embed/' + encodeURIComponent(id) + '?autoplay=1&rel=0';
        text('ytModalTitle', row.title || '영상 재생');
        text('ytModalKind', row.tabLabel || '');
        text('ytModalHeading', row.title || '');
        const bits = [];
        if (row.viewCountFmt) bits.push('조회수 ' + row.viewCountFmt);
        if (row.publishedLabel) bits.push(row.publishedLabel);
        if (row.duration) bits.push(row.duration);
        text('ytModalMeta', bits.join(' · '));
        text('ytModalDesc', row.description && String(row.description).trim() ? row.description : '등록된 상세 설명이 없습니다.');
        const deleteTab = document.getElementById('ytDeleteTab');
        const deleteId = document.getElementById('ytDeleteId');
        if (deleteTab) deleteTab.value = row.tab || '';
        if (deleteId) deleteId.value = id;
        window.SiteUi?.modal(ytModal).show();
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

    document.getElementById('ytModalEdit')?.addEventListener('click', () => {
        if (!currentVideo || !editModal) return;
        const id = document.getElementById('mediaEditId');
        const date = document.getElementById('mediaEditDate');
        const desc = document.getElementById('mediaEditDesc');
        if (id) id.value = currentVideo.youtubeId || '';
        if (date) date.value = String(currentVideo.publishedOn || '').slice(0, 10);
        if (desc) desc.value = currentVideo.description || '';
        const names = String(currentVideo.artistNames || '').split(',').map((name) => name.trim()).filter(Boolean);
        if (window.MemberPortraitPicker && editPicker) MemberPortraitPicker.setValues(editPicker, names);
        syncEditMembers();
        window.SiteUi?.modal(editModal).show();
    });

    document.addEventListener('click', (event) => {
        const remove = event.target.closest('[data-playlist-remove]');
        if (remove) {
            event.preventDefault();
            event.stopPropagation();
            removePlaylist(remove.dataset.playlistRemove || '');
            return;
        }
        const add = event.target.closest('[data-playlist-add]');
        if (add) {
            event.preventDefault();
            event.stopPropagation();
            togglePlaylist(add);
            return;
        }
        const card = event.target.closest('[data-site-target="#ytModal"][data-ytid]');
        if (!card) return;
        event.preventDefault();
        openVideo(card);
    });

    ytModal?.addEventListener('site-modal:hidden', () => {
        if (ytPlayer) ytPlayer.src = '';
    });

    renderPlaylist();
})();
