(function () {
    const grid = document.getElementById('archiveGrid');
    if (!grid) return;
    const cards = [...grid.querySelectorAll('.ar-card')];
    const sort = document.getElementById('archiveSort');
    const none = document.getElementById('archiveNone');
    const numberOffset = Math.max(0, Number(grid.dataset.numberOffset) || 0);

    function apply() {
        const shown = [...cards].sort((a, b) => {
            if (!sort || sort.value === 'newest') return Number(b.dataset.id || 0) - Number(a.dataset.id || 0);
            if (sort.value === 'oldest') return Number(a.dataset.id || 0) - Number(b.dataset.id || 0);
            return (a.dataset.title || '').localeCompare(b.dataset.title || '', 'ko');
        });
        cards.forEach(card => card.classList.add('ar-off'));
        shown.forEach((card, index) => {
            card.classList.remove('ar-off');
            const badge = card.querySelector('[data-archive-number]');
            if (badge) badge.textContent = String(numberOffset + index + 1);
            grid.appendChild(card);
        });
        if (none) none.hidden = shown.length !== 0;
        grid.dispatchEvent(new CustomEvent('archive:updated'));
    }

    sort?.addEventListener('change', apply);
    const form = document.getElementById('archiveFilterForm');
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
    const track = document.getElementById('archiveTrack');
    const recentPrevious = document.getElementById('archivePrev');
    const recentNext = document.getElementById('archiveNext');
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    function updateRecentControls() {
        if (!track) return;
        if (recentPrevious) recentPrevious.disabled = track.scrollLeft <= 1;
        if (recentNext) recentNext.disabled = track.scrollLeft + track.clientWidth >= track.scrollWidth - 1;
    }
    recentPrevious?.addEventListener('click', () => track?.scrollBy({ left: -380, behavior: motion.matches ? 'auto' : 'smooth' }));
    recentNext?.addEventListener('click', () => track?.scrollBy({ left: 380, behavior: motion.matches ? 'auto' : 'smooth' }));
    track?.addEventListener('scroll', updateRecentControls, { passive: true });
    if (track && window.ResizeObserver) new ResizeObserver(updateRecentControls).observe(track);
    updateRecentControls();
    apply();
})();
