(function () {
    const grid = document.getElementById('archiveGrid');
    if (!grid) return;
    const cards = [...grid.querySelectorAll('.ar-card')];
    const artist = document.getElementById('archiveArtistFilter');
    const sort = document.getElementById('archiveSort');
    const visible = document.getElementById('archiveVisible');
    const none = document.getElementById('archiveNone');

    function apply() {
        const name = artist ? artist.value : '';
        const shown = cards.filter(card => {
            const names = card.dataset.artists || '';
            return !name || names.split(',').map(part => part.trim()).includes(name);
        }).sort((a, b) => {
            if (!sort || sort.value === 'newest') return Number(b.dataset.id || 0) - Number(a.dataset.id || 0);
            if (sort.value === 'oldest') return Number(a.dataset.id || 0) - Number(b.dataset.id || 0);
            return (a.dataset.title || '').localeCompare(b.dataset.title || '', 'ko');
        });
        cards.forEach(card => card.classList.add('ar-off'));
        shown.forEach(card => {
            card.classList.remove('ar-off');
            grid.appendChild(card);
        });
        if (visible) visible.textContent = String(shown.length);
        if (none) none.hidden = shown.length !== 0;
    }

    sort?.addEventListener('change', apply);
    const form = document.getElementById('archiveFilterForm');
    const panel = document.getElementById('archiveMemberPanel');
    document.getElementById('archiveMemberToggle')?.addEventListener('click', () => {
        if (!panel) return;
        panel.hidden = !panel.hidden;
        document.getElementById('archiveMemberToggle').setAttribute('aria-expanded', panel.hidden ? 'false' : 'true');
    });
    if (window.MemberPortraitPicker && panel) {
        MemberPortraitPicker.bindMultiSelect(panel);
        if (panel.querySelector('.mpp-item.is-active')) panel.hidden = false;
    }
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
    document.getElementById('archivePrev')?.addEventListener('click', () => track?.scrollBy({ left: -380, behavior: 'smooth' }));
    document.getElementById('archiveNext')?.addEventListener('click', () => track?.scrollBy({ left: 380, behavior: 'smooth' }));
})();
