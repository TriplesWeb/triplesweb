(function () {
    const dialog = document.getElementById('archiveVideoPlayer');
    const video = document.getElementById('archiveVideoElement');
    if (!dialog || !video || typeof dialog.showModal !== 'function') return;
    const title = document.getElementById('archiveVideoTitle');
    const original = document.getElementById('archiveVideoOriginal');
    const status = document.getElementById('archiveVideoStatus');
    const meta = document.getElementById('archiveVideoMeta');
    const date = document.getElementById('archiveVideoDate');
    const dateValue = date.querySelector('time');
    const source = document.getElementById('archiveVideoSource');
    const members = document.getElementById('archiveVideoMembers');
    const cast = members.closest('.ar-detail-cast');
    const edit = document.getElementById('archiveVideoEdit');
    const remove = document.getElementById('archiveVideoDelete');
    let trigger = null;
    let previousOverflow = '';

    function message(text) {
        status.textContent = text;
        status.hidden = !text;
    }

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
        title.textContent = card.dataset.title || '영상 재생';
        original.href = link.href;
        video.src = link.href;
        if (link.dataset.poster) video.poster = link.dataset.poster;
        else video.removeAttribute('poster');
        const { id, registered, sourceLabel, source: sourceValue, unitLabel, unitColor } = card.dataset;
        date.hidden = !registered;
        dateValue.textContent = registered || '';
        if (registered) dateValue.dateTime = registered;
        else dateValue.removeAttribute('datetime');
        source.hidden = !sourceLabel;
        source.dataset.source = sourceValue || '';
        source.textContent = sourceLabel || '';
        const unit = document.getElementById('archiveVideoUnit');
        if (unit) {
            const label = (unitLabel || '').trim();
            unit.hidden = !label;
            unit.textContent = label;
            unit.title = label;
            unit.style.setProperty('--unit-color', (unitColor || '').trim() || '#a78bfa');
        }
        meta.hidden = date.hidden && source.hidden && (!unit || unit.hidden);

        const chips = card.querySelector('.ar-card-members');
        members.replaceChildren(...Array.from(chips?.children || [], chip => chip.cloneNode(true)));
        cast.hidden = !members.childElementCount;
        window.paintArchiveChemistry?.(members);
        Object.assign(edit.dataset, card.dataset);
        Object.assign(remove.dataset, { id, title: card.dataset.title });
        message('');
        if (!dialog.open) {
            previousOverflow = document.body.style.overflow;
            document.body.style.overflow = 'hidden';
            dialog.showModal();
        }
        const playing = video.play();
        playing?.catch(() => {
            if (dialog.open && trigger === link && !video.error) message('재생 버튼을 눌러 영상을 시작해 주세요.');
        });
    });

    document.getElementById('archiveVideoClose')?.addEventListener('click', () => dialog.close());
    // The edit form is outside the native dialog and needs to receive focus.
    edit.addEventListener('click', () => dialog.close());
    document.addEventListener('archive:deleted', event => {
        if (dialog.open && event.detail === remove.dataset.id) dialog.close();
    });
    dialog.addEventListener('close', () => {
        video.pause();
        video.removeAttribute('src');
        video.removeAttribute('poster');
        video.load();
        message('');
        if (!document.querySelector('.ar-dialog:not([hidden])')) {
            document.body.style.overflow = previousOverflow;
            const focus = trigger?.isConnected ? trigger : document.querySelector('#archiveGrid [data-archive-video], .ar-sort-trigger');
            focus?.focus();
        }
    });
    video.addEventListener('error', () => {
        if (dialog.open) message('브라우저에서 재생하지 못했습니다. 아래 원본 영상 링크로 열어 주세요.');
    });
    video.addEventListener('playing', () => message(''));
})();
