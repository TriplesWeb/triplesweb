(function () {
    const dialog = document.getElementById('archiveVideoPlayer');
    const video = document.getElementById('archiveVideoElement');
    if (!dialog || !video || typeof dialog.showModal !== 'function') return;
    const title = document.getElementById('archiveVideoTitle');
    const original = document.getElementById('archiveVideoOriginal');
    const status = document.getElementById('archiveVideoStatus');
    let trigger = null;
    let previousOverflow = '';

    function message(text) {
        status.textContent = text;
        status.hidden = !text;
    }

    document.querySelectorAll('[data-archive-video] img').forEach(image => {
        function fallback() {
            if (image.hidden) return;
            image.hidden = true;
            const label = document.createElement('span');
            label.className = 'ar-video-placeholder';
            label.textContent = '영상 아카이브';
            image.parentElement.appendChild(label);
        }
        image.addEventListener('error', fallback);
        if (image.complete && image.naturalWidth === 0) fallback();
    });

    document.addEventListener('click', event => {
        const link = event.target.closest('[data-archive-video]');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        trigger = link;
        title.textContent = link.dataset.title || '영상 재생';
        original.href = link.href;
        video.src = link.href;
        if (link.dataset.poster) video.poster = link.dataset.poster;
        else video.removeAttribute('poster');
        message('');
        if (!dialog.open) {
            previousOverflow = document.body.style.overflow;
            document.body.style.overflow = 'hidden';
            dialog.showModal();
        }
        const playing = video.play();
        playing?.catch(() => {
            if (dialog.open && !video.error) message('재생 버튼을 눌러 영상을 시작해 주세요.');
        });
    });

    document.getElementById('archiveVideoClose')?.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => {
        if (event.target !== dialog) return;
        const bounds = dialog.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
    });
    dialog.addEventListener('close', () => {
        video.pause();
        video.removeAttribute('src');
        video.removeAttribute('poster');
        video.load();
        message('');
        document.body.style.overflow = previousOverflow;
        trigger?.focus();
    });
    video.addEventListener('error', () => {
        if (dialog.open) message('브라우저에서 재생하지 못했습니다. 아래 원본 영상 링크로 열어 주세요.');
    });
    video.addEventListener('playing', () => message(''));
})();
