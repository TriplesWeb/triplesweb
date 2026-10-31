(function (global) {
    const ALL_LABEL = '전체';
    const ALL_VALUE = 'all';

    function sanitizeKind(raw) {
        return String(raw || '').replace(/[^A-Za-z0-9]/g, '');
    }

    function normalizeKindValue(raw) {
        const trimmed = String(raw || '').trim();
        if (!trimmed || trimmed.toLowerCase() === ALL_VALUE || trimmed === ALL_LABEL) return ALL_VALUE;
        const clean = sanitizeKind(trimmed);
        return clean ? clean.toUpperCase() : ALL_VALUE;
    }

    function displayKindValue(raw) {
        const value = normalizeKindValue(raw);
        return value === ALL_VALUE ? ALL_LABEL : value;
    }

    function isAllDisplay(raw) {
        const trimmed = String(raw || '').trim();
        return !trimmed || trimmed === ALL_LABEL || trimmed.toLowerCase() === ALL_VALUE;
    }

    function bindKindCombo(options) {
        const input = options.input;
        const menu = options.menu;
        const toggle = options.toggle;
        const hiddenInput = options.hiddenInput;
        const empty = options.empty;
        const onChange = options.onChange;
        const closeOthers = options.closeOthers;
        if (!input || !menu) return null;

        const kindOptions = [...menu.querySelectorAll('.objekt-kind-option')];
        let activeIndex = -1;

        function currentValue() {
            return normalizeKindValue(hiddenInput ? hiddenInput.value : (input.dataset.kindValue || input.value));
        }

        function markFiltered() {
            const value = currentValue();
            input.closest('.objekt-filter')?.classList.toggle('is-filtered', value !== ALL_VALUE);
            onChange?.(value);
        }

        function commit(raw, display) {
            const value = normalizeKindValue(raw);
            if (hiddenInput) hiddenInput.value = value;
            input.dataset.kindValue = value;
            if (display !== false) input.value = displayKindValue(value);
            markFiltered();
            return value;
        }

        function filterOptions(query) {
            const needle = isAllDisplay(query) ? '' : sanitizeKind(query).toLowerCase();
            let shown = 0;
            kindOptions.forEach((opt) => {
                const value = String(opt.dataset.value || '').toLowerCase();
                const match = !needle || value.includes(needle);
                opt.hidden = !match;
                if (match) shown += 1;
            });
            if (empty) empty.hidden = shown > 0;
            activeIndex = -1;
            kindOptions.forEach((opt) => opt.classList.remove('is-active'));
            return shown;
        }

        function isClosed() {
            return menu.classList.contains('hidden') || menu.hasAttribute('hidden');
        }

        function openMenu(showAll) {
            closeOthers?.();
            if (showAll || isAllDisplay(input.value)) input.value = '';
            filterOptions(showAll ? '' : input.value);
            menu.classList.remove('hidden');
            menu.removeAttribute('hidden');
            input.setAttribute('aria-expanded', 'true');
        }

        function closeMenu() {
            menu.classList.add('hidden');
            menu.setAttribute('hidden', '');
            input.setAttribute('aria-expanded', 'false');
            activeIndex = -1;
            kindOptions.forEach((opt) => opt.classList.remove('is-active'));
        }

        function visibleOptions() {
            return kindOptions.filter((opt) => !opt.hidden);
        }

        function moveActive(delta) {
            const visible = visibleOptions();
            if (!visible.length) return;
            activeIndex = (activeIndex + delta + visible.length) % visible.length;
            visible.forEach((opt, i) => opt.classList.toggle('is-active', i === activeIndex));
            visible[activeIndex]?.scrollIntoView({ block: 'nearest' });
        }

        input.addEventListener('focus', () => openMenu(false));
        input.addEventListener('click', () => openMenu(false));
        input.addEventListener('input', () => {
            const cleaned = sanitizeKind(input.value);
            if (input.value !== cleaned) input.value = cleaned;
            openMenu(false);
            markFiltered();
        });
        input.addEventListener('keydown', (event) => {
            if (event.key === 'ArrowDown') {
                event.preventDefault();
                if (isClosed()) openMenu(false);
                else moveActive(1);
            } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                if (isClosed()) openMenu(false);
                else moveActive(-1);
            } else if (event.key === 'Enter') {
                event.preventDefault();
                const visible = visibleOptions();
                if (!isClosed() && activeIndex >= 0 && visible[activeIndex]) {
                    commit(visible[activeIndex].dataset.value);
                    closeMenu();
                    return;
                }
                const typed = input.value;
                const exact = kindOptions.find((opt) => normalizeKindValue(opt.dataset.value) === normalizeKindValue(typed));
                if (exact) commit(exact.dataset.value);
                else if (visible.length === 1) commit(visible[0].dataset.value);
                else commit(typed);
                closeMenu();
            } else if (event.key === 'Escape') {
                closeMenu();
                commit(input.dataset.kindValue || hiddenInput?.value || ALL_VALUE);
            }
        });
        input.addEventListener('blur', () => {
            setTimeout(() => {
                if (menu.contains(document.activeElement) || document.activeElement === toggle) return;
                commit(input.value);
                closeMenu();
            }, 120);
        });
        toggle?.addEventListener('mousedown', (event) => event.preventDefault());
        toggle?.addEventListener('click', () => {
            if (isClosed()) {
                input.focus();
                openMenu(true);
            } else {
                closeMenu();
            }
        });
        kindOptions.forEach((opt) => {
            opt.addEventListener('mousedown', (event) => event.preventDefault());
            opt.addEventListener('click', () => {
                commit(opt.dataset.value);
                closeMenu();
            });
        });

        commit(hiddenInput?.value || input.value || ALL_VALUE);
        return { commit, closeMenu, normalizeKindValue, displayKindValue };
    }

    global.ObjektKindCombo = {
        bindKindCombo,
        normalizeKindValue,
        displayKindValue,
        sanitizeKind
    };
})(window);
