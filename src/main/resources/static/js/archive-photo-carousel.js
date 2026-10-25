(function () {
    const grid = document.getElementById('archiveGrid');
    const controls = document.getElementById('archiveGalleryControls');
    if (!grid || !controls || !grid.closest('[data-archive-kind="image"]')) return;

    const previous = document.getElementById('archiveGalleryPrev');
    const next = document.getElementById('archiveGalleryNext');
    const position = document.getElementById('archiveGalleryPosition');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const threshold = 4;
    let slides = [];
    let active = 0;
    let frame = 0;

    function render() {
        frame = 0;
        if (controls.hidden || !slides.length) return;
        const center = grid.scrollLeft + grid.clientWidth / 2;
        let closest = Infinity;
        slides.forEach((slide, index) => {
            const distance = Math.abs(slide.offsetLeft + slide.offsetWidth / 2 - center);
            if (distance < closest) { closest = distance; active = index; }
        });
        slides.forEach((slide, index) => {
            slide.classList.toggle('is-current-photo', index === active);
            slide.classList.toggle('is-photo-before', index < active);
            slide.classList.toggle('is-photo-after', index > active);
            if (index === active) slide.setAttribute('aria-current', 'true');
            else slide.removeAttribute('aria-current');
        });
        const label = `${active + 1} / ${slides.length}`;
        if (position.textContent !== label) position.textContent = label;
        previous.disabled = active === 0;
        next.disabled = active === slides.length - 1;
    }

    function goTo(index, smooth = true) {
        if (controls.hidden || !slides.length) return;
        active = Math.max(0, Math.min(slides.length - 1, index));
        const slide = slides[active];
        grid.scrollTo({
            left: slide.offsetLeft - (grid.clientWidth - slide.offsetWidth) / 2,
            behavior: smooth && !reducedMotion.matches ? 'smooth' : 'instant'
        });
        if (!smooth || reducedMotion.matches) render();
    }

    function sync() {
        slides = [...grid.querySelectorAll('.ar-card:not(.ar-off)')];
        const enabled = slides.length >= threshold;
        controls.hidden = !enabled;
        grid.classList.toggle('is-photo-carousel', enabled);
        grid.querySelectorAll('.ar-card').forEach(slide => {
            slide.classList.remove('is-current-photo', 'is-photo-before', 'is-photo-after');
            slide.removeAttribute('aria-current');
            slide.removeAttribute('aria-roledescription');
            slide.removeAttribute('aria-label');
            slide.removeAttribute('role');
        });
        if (!enabled) {
            grid.removeAttribute('tabindex');
            grid.removeAttribute('aria-roledescription');
            grid.removeAttribute('role');
            position.textContent = '';
            grid.scrollLeft = 0;
            return;
        }
        grid.tabIndex = 0;
        grid.setAttribute('role', 'region');
        grid.setAttribute('aria-roledescription', '캐러셀');
        slides.forEach((slide, index) => {
            slide.setAttribute('role', 'group');
            slide.setAttribute('aria-roledescription', '슬라이드');
            slide.setAttribute('aria-label', `${index + 1} / ${slides.length}: ${slide.dataset.title || '사진'}`);
        });
        goTo(0, false);
    }

    previous.addEventListener('click', () => goTo(active - 1));
    next.addEventListener('click', () => goTo(active + 1));
    grid.addEventListener('scroll', () => {
        if (!frame) frame = requestAnimationFrame(render);
    }, { passive: true });
    grid.addEventListener('keydown', event => {
        if (event.target !== grid || controls.hidden) return;
        const target = { ArrowLeft: active - 1, ArrowRight: active + 1, Home: 0, End: slides.length - 1 }[event.key];
        if (target === undefined) return;
        event.preventDefault();
        goTo(target);
    });
    grid.addEventListener('focusin', event => {
        const slide = event.target.closest('.ar-card');
        const index = slides.indexOf(slide);
        if (index >= 0 && index !== active) goTo(index);
    });
    grid.addEventListener('archive:updated', sync);
    if (window.ResizeObserver) {
        new ResizeObserver(() => goTo(active, false)).observe(grid);
    } else {
        window.addEventListener('resize', () => goTo(active, false));
    }
    sync();
})();
