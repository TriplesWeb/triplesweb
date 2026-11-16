(function () {
    const headline = document.querySelector('.ar-headline');
    const statsToggle = document.querySelector('[data-stats-toggle]');
    const stats = document.getElementById('archiveStats');
    function setStatsOpen(open) {
        headline?.classList.toggle('is-stats-collapsed', !open);
        if (stats) stats.hidden = !open;
        if (!statsToggle) return;
        statsToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        statsToggle.setAttribute('aria-label', open ? '통계 접기' : '통계 펼치기');
        const icon = statsToggle.querySelector('[data-stats-icon]');
        if (icon) icon.textContent = open ? '−' : '+';
    }
    statsToggle?.addEventListener('click', () => {
        window.closeArchiveOverlays?.(statsToggle);
        setStatsOpen(statsToggle.getAttribute('aria-expanded') !== 'true');
    });

    const grid = document.getElementById('archiveGrid');
    if (!grid) return;
    let cards = [...grid.querySelectorAll('.ar-card')];
    const sort = document.getElementById('archiveSort');
    let numberOffset = Math.max(0, Number(grid.dataset.numberOffset) || 0);

    const MEMBER_INDEX = (() => {
        const names = [
            ['윤서연', '서연', 'seoyeon', 's1'],
            ['정혜린', '혜린', 'hyerin', 's2'],
            ['이지우', '지우', 'jiwoo', 's3'],
            ['김채연', '채연', 'chaeyeon', 's4'],
            ['김유연', '유연', 'yooyeon', 's5'],
            ['김수민', '수민', 'soomin', 's6'],
            ['김나경', '나경', 'nakyoung', 's7'],
            ['공유빈', '유빈', 'yubin', 's8'],
            ['카에데', 'kaede', 's9'],
            ['서다현', '다현', 'dahyun', 's10'],
            ['코토네', 'kotone', 's11'],
            ['곽연지', '연지', 'yeonji', 's12'],
            ['니엔', 'nien', 's13'],
            ['박소현', '소현', 'sohyun', 's14'],
            ['신위', 'xinyu', 's15'],
            ['마유', 'mayu', 's16'],
            ['린', 'lynn', 's17'],
            ['주빈', 'joobin', 's18'],
            ['정하연', '하연', 'hayeon', 's19'],
            ['박시온', '시온', 'shion', 's20'],
            ['김채원', '채원', 'chaewon', 's21'],
            ['설린', 'sullin', 's22'],
            ['서아', 'seoah', 's23'],
            ['지연', 'jiyeon', 's24']
        ];
        const index = Object.create(null);
        names.forEach((aliases, order) => {
            aliases.forEach(alias => { index[alias] = order; });
        });
        return index;
    })();

    function earliestMemberOrder(raw) {
        let earliest = 24;
        String(raw || '').split(/[,/|]+/).forEach(part => {
            const key = part.trim().toLowerCase().replace(/\s+/g, '');
            if (key && key in MEMBER_INDEX) earliest = Math.min(earliest, MEMBER_INDEX[key]);
        });
        return earliest;
    }

    function memberOrder(card) {
        const raw = card.dataset.memberOrder;
        if (raw !== undefined && raw !== '') {
            const parsed = Number(raw);
            if (Number.isFinite(parsed)) return parsed;
        }
        return earliestMemberOrder(card.dataset.artists);
    }

    function memberRank(card, lastFirst) {
        const order = memberOrder(card);
        if (!lastFirst) return order;
        return order === 24 ? 24 : 23 - order;
    }

    function apply() {
        const shown = [...cards].sort((a, b) => {
            const idDelta = Number(b.dataset.id || 0) - Number(a.dataset.id || 0);
            if (!sort || sort.value === 'newest') return idDelta;
            if (sort.value === 'oldest') return Number(a.dataset.id || 0) - Number(b.dataset.id || 0);
            if (sort.value === 'first-member' || sort.value === 'last-member') {
                const lastFirst = sort.value === 'last-member';
                const delta = memberRank(a, lastFirst) - memberRank(b, lastFirst);
                return delta !== 0 ? delta : idDelta;
            }
            return (a.dataset.title || '').localeCompare(b.dataset.title || '', 'ko') || idDelta;
        });
        cards.forEach(card => card.classList.add('ar-off'));
        shown.forEach((card, index) => {
            card.classList.remove('ar-off');
            const badge = card.querySelector('[data-archive-number]');
            if (badge) badge.textContent = String(numberOffset + index + 1);
            grid.appendChild(card);
        });
        grid.dispatchEvent(new CustomEvent('archive:updated'));
    }

    sort?.addEventListener('change', apply);
    function enhanceSort(select) {
        const control = select && select.closest('.ar-sort-control');
        if (!control || control.classList.contains('is-enhanced')) return;
        control.classList.add('is-enhanced');
        select.setAttribute('aria-hidden', 'true');
        select.tabIndex = -1;
        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'ar-sort-trigger';
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');
        trigger.setAttribute('aria-label', select.getAttribute('aria-label') || '정렬');
        const menu = document.createElement('div');
        menu.className = 'ar-sort-menu';
        menu.hidden = true;
        menu.setAttribute('role', 'listbox');
        function selectedLabel() {
            return select.options[select.selectedIndex]?.textContent.trim() || '';
        }
        function sync() {
            trigger.textContent = selectedLabel();
            [...menu.querySelectorAll('.ar-sort-option')].forEach(option => {
                option.setAttribute('aria-selected', option.dataset.value === select.value ? 'true' : 'false');
            });
        }
        function setOpen(open) {
            menu.hidden = !open;
            trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
        }
        [...select.options].forEach(option => {
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
                trigger.focus();
            });
            menu.appendChild(item);
        });
        trigger.addEventListener('click', () => {
            const willOpen = menu.hidden;
            window.closeArchiveOverlays?.(trigger);
            setOpen(willOpen);
        });
        document.addEventListener('click', event => {
            if (window.closeArchiveOverlays) return;
            if (!control.contains(event.target)) setOpen(false);
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') setOpen(false);
        });
        select.after(trigger);
        control.appendChild(menu);
        sync();
    }
    enhanceSort(sort);
    const chem = document.getElementById('archiveChem');
    const form = document.getElementById('archiveFilterForm');
    const panel = document.getElementById('archiveMemberPanel');
    const memberToggle = document.getElementById('archiveMemberToggle');
    const memberCount = document.getElementById('archiveMemberCount');
    const memberLabel = memberToggle?.querySelector('[data-member-toggle-label]');
    const defaultMemberLabel = memberLabel?.textContent.trim() || '멤버 선택';
    const folderTitle = document.querySelector('.ar-folder-title');
    const baseFolderTitle = folderTitle?.dataset.baseTitle || folderTitle?.textContent.trim() || '';
    const chemMatches = panel?.querySelector('[data-chem-matches]');

    function memberKey(name) {
        return String(name || '').trim().toLowerCase().replace(/\s+/g, '');
    }

    function sameMemberSet(left, right) {
        if (!left.length || left.length !== right.length) return false;
        const leftKeys = left.map(memberKey);
        const rightKeys = new Set(right.map(memberKey));
        if (new Set(leftKeys).size !== rightKeys.size) return false;
        return leftKeys.every((key) => rightKeys.has(key));
    }

    function selectEntries() {
        return [...(chem?.options || [])]
            .filter((option) => option.value)
            .map((option) => ({
                value: option.value,
                name: option.dataset.chemName || option.textContent.split('·')[0].trim(),
                members: String(option.dataset.members || '').split(',').map((name) => name.trim()).filter(Boolean)
            }));
    }

    function catalogEntries() {
        const node = document.getElementById('archiveChemistryNames');
        if (!node) return [];
        let data = [];
        try {
            data = JSON.parse(node.textContent || '[]');
        } catch (error) {
            data = [];
        }
        if (!Array.isArray(data)) return [];
        return data.map((entry) => ({
            value: '',
            name: String(entry?.label || '').trim(),
            members: (Array.isArray(entry?.members) ? entry.members : String(entry?.members || '').split(','))
                .map((name) => String(name).trim()).filter(Boolean)
        })).filter((entry) => entry.name && entry.members.length >= 2);
    }

    function chemEntries() {
        const selected = selectEntries();
        return selected.length ? selected : catalogEntries();
    }

    function matchingChems(selected) {
        if (!selected.length) return [];
        const keys = selected.map(memberKey);
        return chemEntries().filter((entry) => {
            const entryKeys = entry.members.map(memberKey);
            return keys.every((key) => entryKeys.includes(key));
        });
    }

    function exactChem(selected) {
        return matchingChems(selected).find((entry) => sameMemberSet(entry.members, selected) && entry.name) || null;
    }

    function memberNamesFrom(root) {
        return [...root.querySelectorAll('.unit-member-chip')].map((chip) => {
            return (chip.querySelector('span')?.textContent || chip.getAttribute('title') || '').trim();
        }).filter(Boolean);
    }

    window.archiveChemistryLabel = function (names) {
        const selected = (Array.isArray(names) ? names : []).map((name) => String(name).trim()).filter(Boolean);
        const exact = exactChem(selected);
        return exact ? exact.name : '';
    };

    window.paintArchiveChemistry = function (membersEl) {
        if (!membersEl) return;
        const name = window.archiveChemistryLabel(memberNamesFrom(membersEl));
        const parent = membersEl.parentElement;
        let label = parent ? parent.querySelector(':scope > .ar-detail-chem') : null;
        if (!name) {
            label?.remove();
            return;
        }
        if (!label && parent) {
            label = document.createElement('p');
            label.className = 'ar-detail-chem';
            membersEl.before(label);
        }
        if (label) label.textContent = name;
    };

    function renderChemMatches(selected) {
        if (!chemMatches) return;
        chemMatches.querySelectorAll('.ar-chem-match').forEach((node) => node.remove());
        const hint = chemMatches.querySelector('[data-chem-hint]');
        const matches = matchingChems(selected);
        const exact = exactChem(selected);
        if (hint) {
            hint.hidden = matches.length > 0;
            hint.textContent = selected.length
                ? '이름 붙은 케미가 없습니다'
                : '멤버를 고르면 케미가 나타납니다';
        }
        matches.forEach((entry) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'ar-chem-match' + (exact && exact.value === entry.value ? ' is-exact' : '');
            button.dataset.chem = entry.value;
            button.textContent = entry.name;
            button.title = entry.members.join(' × ');
            button.addEventListener('click', () => applyChemistry(entry));
            chemMatches.appendChild(button);
        });
    }

    function applyChemistry(entry) {
        if (!entry) return;
        if (window.MemberPortraitPicker && panel) {
            MemberPortraitPicker.setValues(panel, entry.members);
        }
        if (chem && chem.value !== entry.value) {
            chem.value = entry.value;
        }
        updateMemberToggle(entry.members);
        form?.requestSubmit();
    }

    function updateMemberToggle(values) {
        const selected = values || (window.MemberPortraitPicker && panel
            ? MemberPortraitPicker.selectedValues(panel)
            : []);
        const count = selected.length;
        const exact = exactChem(selected);
        if (chem) {
            if (exact) chem.value = exact.value;
            else if (Array.isArray(values)) chem.value = '';
        }
        if (memberCount) {
            memberCount.hidden = count === 0;
            memberCount.textContent = String(count);
        }
        if (memberLabel) {
            memberLabel.textContent = count === 1
                ? selected[0]
                : (exact ? exact.name : (count ? count + '명' : defaultMemberLabel));
        }
        if (chem && folderTitle && !folderTitle.classList.contains('is-solo') && (exact || Array.isArray(values) || !chem.value)) {
            folderTitle.textContent = exact ? exact.name : baseFolderTitle;
        }
        memberToggle?.classList.toggle('has-selection', count > 0);
        memberToggle?.classList.toggle('is-open', Boolean(panel && !panel.hidden));
        renderChemMatches(selected);
    }

    function paintFilterSummary() {
        const summary = document.querySelector('.ar-filter-summary');
        if (!summary) return;
        const typeTab = document.querySelector('nav[aria-label="아카이브 종류"] [aria-current="page"]');
        const sourceTab = document.querySelector('.ar-source-tabs [aria-current="page"]');
        const kind = document.querySelector('[data-archive-kind]')?.dataset.archiveKind;
        const bits = [];
        bits.push({ text: typeTab?.textContent.trim() || (kind === 'video' ? '영상' : '사진') });
        bits.push({ text: sourceTab?.textContent.trim() || '전체 출처' });
        const selected = window.MemberPortraitPicker && panel
            ? MemberPortraitPicker.selectedValues(panel)
            : [];
        const chemName = selected.length >= 2 ? (window.archiveChemistryLabel(selected) || '') : '';
        if (chemName) {
            bits.push({ text: chemName });
        } else if (selected.length) {
            const button = panel.querySelector('.mpp-item.is-active');
            const color = button ? getComputedStyle(button).getPropertyValue('--artist-color').trim() : '';
            bits.push({ member: selected[0], color: color || '#b9cac4', more: selected.length - 1 });
        }
        const query = form?.querySelector('.ar-search')?.value.trim() || '';
        if (query) bits.push({ text: query });
        summary.closest('.ar-stat-filter')?.style.setProperty('--filter-count', String(Math.max(1, bits.length)));
        summary.replaceChildren();
        bits.forEach((bit, index) => {
            if (index > 0) {
                const sep = document.createElement('span');
                sep.className = 'ar-filter-sep';
                sep.textContent = ',';
                summary.append(sep);
            }
            if (bit.member) {
                const member = document.createElement('span');
                member.className = 'ar-filter-member';
                member.style.setProperty('--member-color', bit.color);
                member.textContent = bit.member;
                summary.append(member);
                if (bit.more > 0) {
                    const more = document.createElement('span');
                    more.className = 'ar-filter-more';
                    more.textContent = '외 ' + bit.more + '명';
                    summary.append(more);
                }
                return;
            }
            const text = document.createElement('strong');
            text.textContent = bit.text;
            summary.append(text);
        });
    }

    memberToggle?.addEventListener('click', () => {
        if (!panel) return;
        const willOpen = panel.hidden;
        window.closeArchiveOverlays?.(memberToggle);
        panel.hidden = !willOpen;
        memberToggle.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
        updateMemberToggle();
    });
    let filterTimer = 0;
    let submitWhenDateCloses = false;
    function dateOverlayOpen() {
        return Boolean(document.querySelector('.ar-cal-board:not([hidden]), [data-date-popover]:not([hidden])'));
    }
    function scheduleFilterSubmit() {
        window.clearTimeout(filterTimer);
        if (dateOverlayOpen()) {
            submitWhenDateCloses = true;
            return;
        }
        filterTimer = window.setTimeout(() => {
            if (dateOverlayOpen()) {
                submitWhenDateCloses = true;
                return;
            }
            form?.requestSubmit();
        }, 280);
    }
    form?.querySelector('.ar-search')?.addEventListener('input', () => {
        paintFilterSummary();
        scheduleFilterSubmit();
    });
    const dateSnapshot = () => ['on', 'from', 'to'].map((name) => {
        const input = form?.querySelector('[data-date-filter] input[name="' + name + '"]');
        return name + '=' + (input ? input.value : '');
    }).join('&');
    let appliedDates = dateSnapshot();
    form?.addEventListener('archive-date-close', () => {
        const next = dateSnapshot();
        const datesChanged = next !== appliedDates;
        if (datesChanged) appliedDates = next;
        if (!datesChanged && !submitWhenDateCloses) return;
        submitWhenDateCloses = false;
        scheduleFilterSubmit();
    });
    let memberFilterReady = false;
    if (window.MemberPortraitPicker && panel) {
        MemberPortraitPicker.bindMultiSelect(panel, {
            onChange: (values) => {
                updateMemberToggle(values);
                if (!memberFilterReady) return;
                paintFilterSummary();
                scheduleFilterSubmit();
            }
        });
    }
    memberFilterReady = true;
    updateMemberToggle();
    function archiveFilterUrl() {
        form.querySelectorAll('input[name="members"]').forEach(node => node.remove());
        if (window.MemberPortraitPicker && panel) {
            MemberPortraitPicker.selectedValues(panel).forEach(value => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'members';
                input.value = value;
                form.appendChild(input);
            });
        }
        const params = new URLSearchParams();
        new FormData(form).forEach((value, key) => {
            if (String(value).trim() === '') return;
            params.append(key, value);
        });
        const action = form.getAttribute('action') || location.pathname;
        const query = params.toString();
        return query ? action + (action.includes('?') ? '&' : '?') + query : action;
    }
    let filterSeq = 0;
    async function loadArchiveFilter(url, push) {
        const seq = ++filterSeq;
        const y = window.scrollY;
        const x = window.scrollX;
        const response = await fetch(url, { credentials: 'same-origin', headers: { 'X-Archive-Filter': '1' } });
        if (seq !== filterSeq) return;
        if (!response.ok) throw new Error(String(response.status));
        const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
        if (seq !== filterSeq) return;
        const nextGrid = doc.getElementById('archiveGrid');
        if (!nextGrid) throw new Error('grid');
        const nextOffset = nextGrid.dataset.numberOffset || '0';
        if (grid.innerHTML !== nextGrid.innerHTML || grid.dataset.numberOffset !== nextOffset) {
            grid.innerHTML = nextGrid.innerHTML;
            grid.dataset.numberOffset = nextOffset;
            numberOffset = Math.max(0, Number(nextOffset) || 0);
            cards = [...grid.querySelectorAll('.ar-card')];
            apply();
        }
        const nextEmpty = doc.querySelector('.archive-page > .ar-empty');
        const empty = document.querySelector('.archive-page > .ar-empty');
        if (nextEmpty) {
            if (!empty || empty.textContent !== nextEmpty.textContent) {
                const imported = document.importNode(nextEmpty, true);
                if (empty) empty.replaceWith(imported);
                else grid.after(imported);
            }
        } else {
            empty?.remove();
        }
        const nextPager = doc.querySelector('.ar-pager');
        const pager = document.querySelector('.ar-pager');
        if (nextPager && pager && pager.innerHTML !== nextPager.innerHTML) {
            pager.replaceWith(document.importNode(nextPager, true));
        }
        const nextStats = doc.getElementById('archiveStats');
        if (nextStats && stats) {
            const open = !stats.hidden;
            const dateNode = nextStats.querySelector('.ar-stat-date strong');
            const currentDate = stats.querySelector('.ar-stat-date strong');
            const countNode = nextStats.querySelector('.ar-stat strong');
            const currentCount = stats.querySelector('.ar-stat strong');
            if (dateNode && currentDate && currentDate.textContent !== dateNode.textContent) currentDate.textContent = dateNode.textContent;
            if (countNode && currentCount && currentCount.innerHTML !== countNode.innerHTML) currentCount.innerHTML = countNode.innerHTML;
            const pageNode = nextStats.querySelectorAll('.ar-stat strong')[2];
            const currentPage = stats.querySelectorAll('.ar-stat strong')[2];
            if (pageNode && currentPage && currentPage.innerHTML !== pageNode.innerHTML) currentPage.innerHTML = pageNode.innerHTML;
            stats.hidden = !open;
        }
        paintFilterSummary();
        const nextUrl = new URL(url, location.href);
        const same = nextUrl.pathname === location.pathname && nextUrl.search === location.search;
        if (push !== false && !same) history.pushState({ archiveLive: true }, '', nextUrl.pathname + nextUrl.search);
        window.scrollTo(x, y);
        requestAnimationFrame(() => window.scrollTo(x, y));
    }
    form?.addEventListener('submit', (event) => {
        event.preventDefault();
        const url = archiveFilterUrl();
        loadArchiveFilter(url).catch(() => { window.location.assign(url); });
    });
    window.addEventListener('popstate', () => {
        if (!form) return;
        window.location.reload();
    });
    apply();
})();
