(function () {
    const grid = document.getElementById('archiveGrid');
    if (!grid) return;
    const cards = [...grid.querySelectorAll('.ar-card')];
    const sort = document.getElementById('archiveSort');
    const numberOffset = Math.max(0, Number(grid.dataset.numberOffset) || 0);

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
        trigger.addEventListener('click', () => setOpen(menu.hidden));
        document.addEventListener('click', event => {
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
    enhanceSort(chem);
    const form = document.getElementById('archiveFilterForm');
    chem?.addEventListener('change', () => form?.requestSubmit());
    const panel = document.getElementById('archiveMemberPanel');
    const memberToggle = document.getElementById('archiveMemberToggle');
    const memberCount = document.getElementById('archiveMemberCount');

    function updateMemberToggle(values) {
        const selected = values || (window.MemberPortraitPicker && panel
            ? MemberPortraitPicker.selectedValues(panel)
            : []);
        const count = selected.length;
        if (memberCount) {
            memberCount.hidden = count === 0;
            memberCount.textContent = String(count);
        }
        memberToggle?.classList.toggle('has-selection', count > 0);
        memberToggle?.classList.toggle('is-open', Boolean(panel && !panel.hidden));
    }

    memberToggle?.addEventListener('click', () => {
        if (!panel) return;
        panel.hidden = !panel.hidden;
        memberToggle.setAttribute('aria-expanded', panel.hidden ? 'false' : 'true');
        updateMemberToggle();
    });
    if (window.MemberPortraitPicker && panel) {
        MemberPortraitPicker.bindMultiSelect(panel, { onChange: updateMemberToggle });
    }
    updateMemberToggle();
    form?.addEventListener('submit', () => {
        form.querySelectorAll('input[name="members"]').forEach(node => node.remove());
        if (!window.MemberPortraitPicker || !panel) return;
        MemberPortraitPicker.selectedValues(panel).forEach(value => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'members';
            input.value = value;
            form.appendChild(input);
        });
    });
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
        setStatsOpen(statsToggle.getAttribute('aria-expanded') !== 'true');
    });
    apply();
})();
