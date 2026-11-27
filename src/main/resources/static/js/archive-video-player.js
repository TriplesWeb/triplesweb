(function () {
    const dialog = document.getElementById('archiveVideoPlayer');
    const video = document.getElementById('archiveVideoElement');
    if (!dialog || !video || typeof dialog.showModal !== 'function') return;
    video.volume = 0.3; // HTMLMediaElement volume uses a 0–1 scale.
    const title = document.getElementById('archiveVideoTitle');
    const status = document.getElementById('archiveVideoStatus');
    const meta = document.getElementById('archiveVideoMeta');
    const date = document.getElementById('archiveVideoDate');
    const dateValue = date?.querySelector('time');
    const source = document.getElementById('archiveVideoSource');
    const members = document.getElementById('archiveVideoMembers');
    const cast = members?.closest('.ar-detail-cast');
    const edit = document.getElementById('archiveVideoEdit');
    const remove = document.getElementById('archiveVideoDelete');
    let trigger = null;
    let previousOverflow = '';

    function message(text) {
        if (!status) return;
        status.textContent = text;
        status.hidden = !text;
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

    function showCast(card) {
        if (!members || !card) return;
        const source = card.querySelector('.ar-card-members');
        members.innerHTML = source?.innerHTML || '';
        const countMembers = members.querySelectorAll('.unit-member-chip').length;
        members.dataset.memberCount = String(countMembers);
        members.classList.toggle('is-many', countMembers >= 10);
        if (cast) cast.hidden = countMembers === 0;
        fitMembers();
        window.paintArchiveChemistry?.(members);
    }

    function closePlayer() {
        if (dialog.open) dialog.close();
    }

    window.closeArchiveVideoPlayer = closePlayer;

    document.addEventListener('error', event => {
        if (event.target.matches?.('[data-archive-video] img')) event.target.hidden = true;
    }, true);
    for (const image of document.querySelectorAll('[data-archive-video] img')) {
        if (image.complete && !image.naturalWidth) image.hidden = true;
    }

    document.addEventListener('click', event => {
        const link = event.target.closest('[data-archive-video]');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        const card = link.closest('.ar-card');
        if (!card) return;
        event.preventDefault();
        trigger = link;
        if (title) title.textContent = card.dataset.title || '영상 재생';
        video.src = link.href;
        if (link.dataset.poster) video.poster = link.dataset.poster;
        else video.removeAttribute('poster');
        const { id, registered, sourceLabel, source: sourceValue } = card.dataset;
        if (date && dateValue) {
            date.hidden = !registered;
            dateValue.textContent = registered || '';
            if (registered) dateValue.dateTime = registered;
            else dateValue.removeAttribute('datetime');
        }
        if (source) {
            const sourceColor = (card.dataset.sourceColor || '').trim() || '#00f5d4';
            source.hidden = !sourceLabel;
            source.dataset.source = sourceValue || '';
            source.style.setProperty('--source-color', sourceColor);
            source.textContent = sourceLabel || '';
        }
        const unit = document.getElementById('archiveVideoUnit');
        if (unit) {
            const badges = [...card.querySelectorAll('.ar-card-badges > .ar-unit-badge')];
            unit.replaceChildren(...badges.map(badge => badge.cloneNode(true)));
            unit.hidden = !badges.length;
        }
        if (meta) meta.hidden = (!date || date.hidden) && (!source || source.hidden) && (!unit || unit.hidden);

        if (edit) Object.assign(edit.dataset, card.dataset);
        if (remove) Object.assign(remove.dataset, { id, title: card.dataset.title });
        message('');
        if (!dialog.open) {
            previousOverflow = document.body.style.overflow;
            document.body.style.overflow = 'hidden';
            dialog.showModal();
        }
        showCast(card);
        requestAnimationFrame(() => {
            fitMembers();
            requestAnimationFrame(fitMembers);
        });
        document.getElementById('archiveVideoClose')?.focus();
        const playing = video.play();
        playing?.catch(() => {
            if (dialog.open && trigger === link && !video.error) {
                message('재생 버튼을 눌러 영상을 시작해 주세요.');
            }
        });
    });

    window.addEventListener('resize', () => {
        if (dialog.open) fitMembers();
    });

    document.getElementById('archiveVideoClose')?.addEventListener('click', closePlayer);
    edit?.addEventListener('click', closePlayer);
    document.addEventListener('archive:deleted', event => {
        if (dialog.open && event.detail === remove?.dataset.id) closePlayer();
    });
    dialog.addEventListener('close', () => {
        video.pause();
        video.removeAttribute('src');
        video.removeAttribute('poster');
        video.load();
        message('');
        if (!document.querySelector('.ar-dialog:not([hidden])')) {
            document.body.style.overflow = previousOverflow;
            const focus = trigger?.isConnected
                ? trigger
                : document.querySelector('#archiveGrid [data-archive-video], .ar-sort-trigger');
            focus?.focus();
        }
    });
    video.addEventListener('error', () => {
        if (dialog.open) message('브라우저에서 재생하지 못했습니다.');
    });
    video.addEventListener('playing', () => message(''));
})();
