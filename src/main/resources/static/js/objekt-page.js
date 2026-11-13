/* Extracted from cosmo/objekt.mustache */
(() => {
    const form = document.getElementById('objekt-search-form');
    const kindSelect = document.querySelector('.objekt-kind-select');
    const sortSelect = document.querySelector('.objekt-sort-select');
    const sortToggle = document.getElementById('objekt-sort-toggle');
    const sortMenu = document.getElementById('sort-menu');
    const sortLabel = document.getElementById('sort-label');

    function setSortOpen(open) {
        sortToggle.closest('.objekt-filter')?.classList.toggle('is-open', open);
    }

    function closeSortMenu() {
        if (window.ObjektDropdown) window.ObjektDropdown.setOpen(sortMenu, false);
        else {
            sortMenu.classList.add('hidden');
            sortMenu.setAttribute('hidden', '');
        }
        sortToggle.setAttribute('aria-expanded', 'false');
        setSortOpen(false);
    }

    function syncSortMenu() {
        sortLabel.textContent = sortSelect.selectedOptions[0].textContent;
        sortMenu.querySelectorAll('[role="option"]').forEach((option) => {
            const selected = option.dataset.value === sortSelect.value;
            option.classList.toggle('is-active', selected);
            option.setAttribute('aria-selected', String(selected));
            option.tabIndex = selected ? 0 : -1;
        });
    }

    function openSortMenu(index) {
        closeKindMenu();
        ['season-menu', 'class-menu'].forEach((id) => {
            const menu = document.getElementById(id);
            if (window.ObjektDropdown) window.ObjektDropdown.setOpen(menu, false);
            else menu?.classList.add('hidden');
        });
        if (window.ObjektDropdown) window.ObjektDropdown.setOpen(sortMenu, true);
        else {
            sortMenu.classList.remove('hidden');
            sortMenu.removeAttribute('hidden');
        }
        sortToggle.setAttribute('aria-expanded', 'true');
        setSortOpen(true);
        const options = [...sortMenu.querySelectorAll('[role="option"]')];
        const focusIndex = index ?? options.findIndex(option => option.dataset.value === sortSelect.value);
        options.forEach((option, i) => { option.tabIndex = i === focusIndex ? 0 : -1; });
        options[focusIndex]?.focus();
    }

    sortMenu.querySelectorAll('[role="option"]').forEach((button) => {
        button.addEventListener('pointerdown', (event) => {
            if (event.button !== 0) return;
            event.preventDefault();
            const value = button.dataset.value || '';
            const changed = sortSelect.value !== value;
            sortSelect.value = value;
            syncSortMenu();
            if (changed) {
                sortSelect.dispatchEvent(new Event('change', { bubbles: true }));
                scheduleApplyFilters();
            }
        });
        button.addEventListener('click', (event) => {
            event.stopPropagation();
            closeSortMenu();
            sortToggle.focus();
        });
    });

    sortToggle.addEventListener('click', () => {
        if (sortMenu.classList.contains('hidden') || sortMenu.hidden) openSortMenu();
        else closeSortMenu();
    });
    document.getElementById('sort-dropdown').addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            closeSortMenu();
            sortToggle.focus();
        } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
            event.preventDefault();
            const options = [...sortMenu.querySelectorAll('[role="option"]')];
            const current = options.indexOf(document.activeElement);
            const selected = options.findIndex(option => option.dataset.value === sortSelect.value);
            const index = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
                : current < 0 ? selected
                : (current + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
            openSortMenu(index);
        } else if (event.key === 'Tab') {
            closeSortMenu();
            sortToggle.focus();
        }
    });
    document.getElementById('sort-dropdown').addEventListener('focusout', (event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) closeSortMenu();
    });
    const cards = Array.from(document.querySelectorAll('.objekt-item'));
    const empty = document.getElementById('objekt-empty');
    const memberRoot = document.getElementById('member-dropdown');
    const memberLabel = document.getElementById('member-label');

    const params = new URLSearchParams(window.location.search);
    const kindMenu = document.getElementById('kind-menu');
    const kindToggle = document.getElementById('objekt-kind-toggle');
    const kindEmpty = document.getElementById('kind-empty');
    const kindOptions = kindMenu ? [...kindMenu.querySelectorAll('.objekt-kind-option')] : [];
    let kindActiveIndex = -1;
    const kindByClass = (() => {
        const node = document.getElementById('objekt-kind-by-class');
        if (!node) return {};
        try {
            const parsed = JSON.parse(node.textContent || '{}');
            return parsed && typeof parsed === 'object' ? parsed : {};
        } catch (_) {
            return {};
        }
    })();

    function normalizeKindValue(raw) {
        if (window.ObjektKindCombo) return ObjektKindCombo.normalizeKindValue(raw);
        const value = String(raw || '').trim();
        if (!value || value.toLowerCase() === 'all' || value === '전체') return 'all';
        return value.toUpperCase();
    }

    function displayKindValue(raw) {
        if (window.ObjektKindCombo) return ObjektKindCombo.displayKindValue(raw);
        const value = normalizeKindValue(raw);
        return value === 'all' ? '전체' : value;
    }

    function setKindValue(raw, { apply = true } = {}) {
        if (!kindSelect) return;
        const next = normalizeKindValue(raw);
        const changed = normalizeKindValue(kindSelect.value) !== next;
        kindSelect.value = displayKindValue(next);
        kindSelect.dataset.kindValue = next;
        markActiveFilters();
        if (apply && changed) scheduleApplyFilters();
    }

    function selectedClassValues() {
        return [...document.querySelectorAll('.class-checkbox')]
            .filter((cb) => cb.value !== 'all' && cb.checked)
            .map((cb) => cb.value);
    }

    function allowedKindsForClasses(classes) {
        if (!classes.length) return null;
        const keys = Object.keys(kindByClass);
        if (!keys.length) return null;
        const allowed = new Set();
        let matched = false;
        for (const klass of classes) {
            const match = keys.find((key) => key.toLocaleLowerCase() === String(klass).toLocaleLowerCase());
            if (!match) continue;
            matched = true;
            const numbers = kindByClass[match];
            if (!Array.isArray(numbers)) continue;
            numbers.forEach((number) => allowed.add(normalizeKindValue(number)));
        }
        return matched ? allowed : null;
    }

    function syncKindOptionsToClass({ resetInvalid = true } = {}) {
        const allowed = allowedKindsForClasses(selectedClassValues());
        kindOptions.forEach((opt) => {
            if (opt.dataset.value === 'all') {
                opt.dataset.classAllowed = 'true';
                return;
            }
            const ok = !allowed || allowed.has(normalizeKindValue(opt.dataset.value));
            opt.dataset.classAllowed = ok ? 'true' : 'false';
        });
        if (resetInvalid && kindSelect) {
            const current = normalizeKindValue(kindSelect.value);
            if (current !== 'all' && allowed && !allowed.has(current)) {
                setKindValue('all', { apply: false });
            }
        }
        filterKindOptions(kindSelect?.value);
    }

    function filterKindOptions(query) {
        const q = normalizeKindValue(query);
        const needle = q === 'all' ? '' : q.toLowerCase();
        let shown = 0;
        kindOptions.forEach((opt) => {
            const classOk = opt.dataset.value === 'all' || opt.dataset.classAllowed !== 'false';
            const value = String(opt.dataset.value || '').toLowerCase();
            const match = classOk && (!needle || value.includes(needle));
            opt.hidden = !match;
            if (match) shown += 1;
        });
        if (kindEmpty) kindEmpty.hidden = shown > 0;
        kindActiveIndex = -1;
        kindOptions.forEach((opt) => opt.classList.remove('is-active'));
        return shown;
    }

    function openKindMenu(showAll) {
        if (!kindMenu || !kindSelect) return;
        closeSortMenu();
        filterKindOptions(showAll ? '' : kindSelect.value);
        if (window.ObjektDropdown) {
            window.ObjektDropdown.closeAll(kindMenu);
            window.ObjektDropdown.setOpen(kindMenu, true);
        } else {
            kindMenu.classList.remove('hidden');
            kindMenu.removeAttribute('hidden');
            ['season-menu', 'class-menu'].forEach((id) => {
                const menu = document.getElementById(id);
                menu?.classList.add('hidden');
                menu?.setAttribute('hidden', '');
            });
        }
        kindSelect.setAttribute('aria-expanded', 'true');
    }

    function closeKindMenu() {
        if (!kindMenu || !kindSelect) return;
        if (window.ObjektDropdown) window.ObjektDropdown.setOpen(kindMenu, false);
        else {
            kindMenu.classList.add('hidden');
            kindMenu.setAttribute('hidden', '');
        }
        kindSelect.setAttribute('aria-expanded', 'false');
        kindActiveIndex = -1;
        kindOptions.forEach((opt) => opt.classList.remove('is-active'));
    }

    function visibleKindOptions() {
        return kindOptions.filter((opt) => !opt.hidden);
    }

    function moveKindActive(delta) {
        const visible = visibleKindOptions();
        if (!visible.length) return;
        kindActiveIndex = (kindActiveIndex + delta + visible.length) % visible.length;
        visible.forEach((opt, i) => opt.classList.toggle('is-active', i === kindActiveIndex));
        visible[kindActiveIndex]?.scrollIntoView({ block: 'nearest' });
    }

    const initialKind = normalizeKindValue(params.get('kind') || kindSelect?.value || 'all');
    if (kindSelect) {
        kindSelect.value = displayKindValue(initialKind);
        kindSelect.dataset.kindValue = initialKind;
    }
    sortSelect.value = params.get('sort') || 'latest';
    if (!sortSelect.value) sortSelect.value = 'latest';
    syncSortMenu();
    sortSelect.addEventListener('change', () => {
        syncSortMenu();
        scheduleApplyFilters();
    });
    document.getElementById('sort-native-control').hidden = true;
    sortToggle.hidden = false;

    if (kindSelect && kindMenu) {
        kindSelect.addEventListener('beforeinput', (event) => {
            if (window.ObjektKindCombo) ObjektKindCombo.rejectKindBeforeInput(event);
            else if (event.data && /[^A-Za-z0-9]/.test(event.data)) event.preventDefault();
        });
        kindSelect.addEventListener('focus', () => openKindMenu(false));
        kindSelect.addEventListener('click', () => openKindMenu(false));
        kindSelect.addEventListener('input', () => {
            const cleaned = window.ObjektKindCombo
                ? ObjektKindCombo.sanitizeKind(kindSelect.value)
                : String(kindSelect.value || '').replace(/[^A-Za-z0-9]/g, '');
            if (kindSelect.value !== cleaned) kindSelect.value = cleaned;
            openKindMenu(false);
            markActiveFilters();
        });
        kindSelect.addEventListener('keydown', (e) => {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (kindMenu.classList.contains('hidden')) openKindMenu(false);
                else moveKindActive(1);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                if (kindMenu.classList.contains('hidden')) openKindMenu(false);
                else moveKindActive(-1);
            } else if (e.key === 'Enter') {
                e.preventDefault();
                const visible = visibleKindOptions().filter((opt) => opt.dataset.value !== 'all');
                if (!kindMenu.classList.contains('hidden') && kindActiveIndex >= 0) {
                    const allVisible = visibleKindOptions();
                    if (allVisible[kindActiveIndex]) {
                        setKindValue(allVisible[kindActiveIndex].dataset.value);
                        closeKindMenu();
                        return;
                    }
                }
                const typed = normalizeKindValue(kindSelect.value);
                const exact = kindOptions.find((opt) => normalizeKindValue(opt.dataset.value) === typed);
                if (exact) setKindValue(exact.dataset.value);
                else if (visible.length === 1) setKindValue(visible[0].dataset.value);
                else setKindValue(typed);
                closeKindMenu();
            } else if (e.key === 'Escape') {
                closeKindMenu();
            }
        });
        kindSelect.addEventListener('blur', () => {
            // 옵션 클릭이 blur보다 먼저 처리되도록 약간 지연
            setTimeout(() => {
                if (!kindMenu.contains(document.activeElement) && document.activeElement !== kindToggle) {
                    setKindValue(kindSelect.value);
                    closeKindMenu();
                }
            }, 120);
        });
        kindToggle?.addEventListener('mousedown', (e) => e.preventDefault());
        kindToggle?.addEventListener('click', () => {
            if (kindMenu.classList.contains('hidden')) {
                kindSelect.focus();
                openKindMenu(true);
            } else {
                closeKindMenu();
            }
        });
        kindOptions.forEach((opt) => {
            opt.addEventListener('mousedown', (e) => e.preventDefault());
            opt.addEventListener('click', () => {
                setKindValue(opt.dataset.value);
                closeKindMenu();
            });
        });
    }

    function updateDropdownLabel(type) {
        const checks = [...document.querySelectorAll(`.${type}-checkbox:checked`)];
        const all = document.querySelector(`.${type}-checkbox[value="all"]`);
        const target = document.getElementById(`${type}-label`);
        if (!target) return;
        const chosen = checks.filter(cb => cb !== all);
        const labelOf = (box) => box.closest('label')?.querySelector('.objekt-choice-text')?.textContent.trim() || '';
        if (!chosen.length || (all && all.checked)) target.textContent = '전체';
        else if (chosen.length === 1) target.textContent = labelOf(chosen[0]);
        else target.textContent = `${labelOf(chosen[0])} 외 ${chosen.length - 1}개`;
    }

    const memberGroupInput = document.getElementById('objekt-member-group');
    function syncMemberGroup() {
        const group = memberGroupInput.value;
        document.querySelectorAll('[data-objekt-group]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.objektGroup === group)));
        document.getElementById('objekt-member-picker').hidden = group === 'other';
        document.getElementById('objekt-other-notice').hidden = group !== 'other';
        const memberToggle = document.getElementById('objekt-member-toggle');
        if (memberToggle) memberToggle.hidden = group === 'other';
    }
    function syncMemberLabel() {
        const chosen = [...document.querySelectorAll('.member-checkbox:checked')];
        if (!memberLabel) return;
        if (!chosen.length) {
            memberLabel.textContent = memberGroupInput.value === 'other' ? '기타 옵젝' : memberGroupInput.value === 'members' ? '전체 멤버' : '전체 오브젝트';
            return;
        }
        const first = chosen[0].value;
        memberLabel.textContent = chosen.length === 1
            ? first
            : `${first} 외 ${chosen.length - 1}명`;
    }

    function syncMemberChecks(values, { apply = false } = {}) {
        if (apply) memberGroupInput.value = 'members';
        const selected = new Set((values || []).map(v => String(v).toLocaleLowerCase()));
        document.querySelectorAll('.member-checkbox').forEach((cb) => {
            cb.checked = selected.has(String(cb.value).toLocaleLowerCase());
        });
        syncMemberGroup();
        syncMemberLabel();
        if (typeof markActiveFilters === 'function') markActiveFilters();
        if (apply) scheduleApplyFilters();
    }

    // 멤버 초상화 다중선택
    const selectedMembers = [...document.querySelectorAll('.member-checkbox:checked')].map(box => box.value);
    if (window.MemberPortraitPicker && memberRoot) {
        MemberPortraitPicker.bindMultiSelect(memberRoot, {
            multi: true,
            onChange: (values) => syncMemberChecks(values, { apply: true })
        });
        memberRoot.querySelectorAll('.mpp-item').forEach((btn) => {
            const value = (btn.dataset.value || '').toLocaleLowerCase();
            const active = selectedMembers.some(v => String(v).toLocaleLowerCase() === value);
            MemberPortraitPicker.setActive(btn, active);
        });
        syncMemberChecks(
            MemberPortraitPicker.selectedValues(memberRoot)
        );
    } else {
        syncMemberGroup(); syncMemberLabel();
    }

    const memberToggle = document.getElementById('objekt-member-toggle');
    function setMemberPickerOpen(open) {
        memberRoot.classList.toggle('is-collapsed', !open);
        if (memberToggle) {
            memberToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        }
    }
    setMemberPickerOpen(selectedMembers.length > 0 || memberGroupInput.value === 'members');
    document.querySelectorAll('[data-objekt-group]').forEach(button => button.addEventListener('click', () => {
        const group = button.dataset.objektGroup;
        memberGroupInput.value = group;
        if (group !== 'members') {
            window.MemberPortraitPicker?.clear(memberRoot);
            syncMemberChecks([]);
        } else { syncMemberGroup(); syncMemberLabel(); }
        setMemberPickerOpen(group === 'members');
        markActiveFilters();
        scheduleApplyFilters();
    }));
    memberToggle?.addEventListener('click', () => {
        if (memberGroupInput.value === 'other') {
            memberGroupInput.value = 'members'; syncMemberGroup(); syncMemberLabel();
            setMemberPickerOpen(true); markActiveFilters(); scheduleApplyFilters(); return;
        }
        setMemberPickerOpen(memberRoot.classList.contains('is-collapsed'));
    });

    // 시즌·클래스 드롭다운 (기존 다중선택 유지)
    ['season', 'class'].forEach(type => {
        const selected = params.getAll(type).map(v => v.toLocaleLowerCase());
        const boxes = [...document.querySelectorAll(`.${type}-checkbox`)];
        boxes.forEach(cb => {
            if (cb.value === 'all') return;
            cb.checked = selected.includes(cb.value.toLocaleLowerCase());
        });
        const all = document.querySelector(`.${type}-checkbox[value="all"]`);
        if (all) all.checked = selected.length === 0;
        boxes.forEach(cb => cb.addEventListener('change', () => {
            if (cb.value === 'all' && cb.checked) {
                boxes.forEach(other => { if (other !== cb) other.checked = false; });
            } else if (cb.value !== 'all' && cb.checked && all) {
                all.checked = false;
            }
            const chosen = boxes.some(other => other.value !== 'all' && other.checked);
            if (all && !chosen) all.checked = true;
            updateDropdownLabel(type);
            if (type === 'class') syncKindOptionsToClass();
            markActiveFilters();
            scheduleApplyFilters();
        }));
        const toggle = document.getElementById(`${type}-toggle`);
        toggle?.addEventListener('click', () => window.toggleDropdown(`${type}-menu`));
        updateDropdownLabel(type);
        if (type === 'class') syncKindOptionsToClass({ resetInvalid: true });
    });

    function markActiveFilters() {
        const seasonText = document.getElementById('season-label')?.textContent.trim();
        const seasonOn = !!seasonText && seasonText !== '전체';
        document.getElementById('season-dropdown')?.classList.toggle('is-filtered', seasonOn);
        const classText = document.getElementById('class-label')?.textContent.trim();
        const classOn = !!classText && classText !== '전체';
        document.getElementById('class-dropdown')?.classList.toggle('is-filtered', classOn);
        const kindValue = normalizeKindValue(kindSelect?.value);
        const kindOn = kindValue !== 'all';
        kindSelect?.closest('.objekt-filter')?.classList.toggle('is-filtered', kindOn);
        const sortOn = Boolean(sortSelect.value && sortSelect.value !== 'latest');
        sortSelect.closest('.objekt-filter')?.classList.toggle('is-filtered', sortOn);
        const searchValue = (form.querySelector('input[name="search"]')?.value || '').trim();
        document.querySelector('.objekt-search')?.classList.toggle('is-filtered', searchValue.length > 0);
        const memberOn = memberGroupInput.value !== 'all' || document.querySelectorAll('.member-checkbox:checked').length > 0;
        memberRoot?.classList.toggle('is-filtered', memberOn);
        const resetBtn = document.getElementById('objekt-reset');
        if (resetBtn) {
            const showReset = seasonOn || classOn || kindOn || sortOn;
            resetBtn.hidden = !showReset;
            resetBtn.toggleAttribute('hidden', !showReset);
        }
    }
    markActiveFilters();
    form.addEventListener('change', () => {
        markActiveFilters();
    });

    function syncOpenFilters() {
        document.querySelectorAll('#objekt-search-form .objekt-filter').forEach((filter) => {
            const menu = filter.querySelector('.objekt-dropdown-menu');
            const open = Boolean(menu && !menu.classList.contains('hidden') && !menu.hidden);
            if (filter.id !== 'sort-dropdown') filter.classList.toggle('is-open', open);
        });
    }

    function syncMenuExpanded(menu, open) {
        if (!menu?.id) return;
        document.querySelectorAll(`[aria-controls="${menu.id}"]`).forEach((el) => {
            el.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
    }

    window.toggleDropdown = function(id) {
        closeKindMenu();
        closeSortMenu();
        const target = document.getElementById(id);
        if (window.ObjektDropdown) {
            window.ObjektDropdown.toggle(target);
        } else {
            document.querySelectorAll('[id$="-menu"]').forEach(menu => {
                if (menu.id !== id) {
                    menu.classList.add('hidden');
                    menu.setAttribute('hidden', '');
                    syncMenuExpanded(menu, false);
                }
            });
            if (target) {
                const open = target.classList.contains('hidden') || target.hasAttribute('hidden');
                target.classList.toggle('hidden', !open);
                target.toggleAttribute('hidden', !open);
                syncMenuExpanded(target, open);
            }
        }
        syncOpenFilters();
    };

    window.addEventListener('click', e => {
        if (!e.target.closest('#sort-dropdown')) closeSortMenu();
        if (!e.target.closest('#season-dropdown') && !e.target.closest('#class-dropdown') && !e.target.closest('#kind-dropdown') && !e.target.closest('#objekt-cols-dropdown')) {
            ['season-menu', 'class-menu'].forEach((id) => {
                const menu = document.getElementById(id);
                if (window.ObjektDropdown) window.ObjektDropdown.setOpen(menu, false);
                else {
                    menu?.classList.add('hidden');
                    menu?.setAttribute('hidden', '');
                    syncMenuExpanded(menu, false);
                }
            });
            closeKindMenu();
        }
        syncOpenFilters();
    });

    const searchInput = form.querySelector('input[name="search"]');
    let searchTimer = 0;

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        clearTimeout(searchTimer);
        const page = form.querySelector('input[name="page"]');
        if (page) page.value = '0';
        if (kindSelect) kindSelect.value = displayKindValue(kindSelect.value);
        applyFilters();
    });

    searchInput?.addEventListener('input', () => {
        markActiveFilters();
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            const page = form.querySelector('input[name="page"]');
            if (page) page.value = '0';
            applyFilters();
        }, 280);
    });

    document.getElementById('objekt-reset').addEventListener('click', () => {
        window.location.href = '/cosmo/objekt';
    });

    const grid = document.getElementById('objekt-card-grid');
    const summary = document.getElementById('objekt-result-summary');
    const sentinel = document.getElementById('objekt-scroll-sentinel');
    const statusEl = document.getElementById('objekt-scroll-status');
    const spinner = document.getElementById('objekt-scroll-spinner');

    let loadedCount = Number(summary?.dataset.loaded || cards.length || 0);
    let resultCount = Number(summary?.dataset.resultCount || 0);
    let allCount = Number(summary?.dataset.allCount || 0);
    let nextPage = Number(sentinel?.dataset.nextPage || 1);
    let hasMore = sentinel?.dataset.hasMore === 'true';
    let loading = false;
    let listGeneration = 0;
    let filterTimer = 0;

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function attr(value) {
        return escapeHtml(value);
    }

    function seasonBadgeColor(item, season) {
        const fromItem = typeof item?.seasonColor === 'string' ? item.seasonColor.trim() : '';
        if (fromItem) return fromItem;
        return window.SeasonChips?.colorFor(season) || '#A4B7B4';
    }

    const detailModal = document.getElementById('objektDetailModal');
    const detailImage = document.getElementById('objektDetailImage');
    const detailVideo = document.getElementById('objektDetailVideo');
    const detailFlip = document.getElementById('objektDetailFlip');
    const detailFace = document.getElementById('objektDetailFace');
    const detailTitle = document.getElementById('objektDetailTitle');
    const detailMember = document.getElementById('objektDetailMember');
    const detailSeason = document.getElementById('objektDetailSeason');
    const detailClass = document.getElementById('objektDetailClass');
    const detailNo = document.getElementById('objektDetailNo');
    const detailDesc = document.getElementById('objektDetailDesc');
    const detailFavorite = document.getElementById('objektDetailFavorite');
    const detailMemberLink = document.getElementById('objektDetailMemberLink');
    const detailFullLink = document.getElementById('objektDetailFullLink');
    const detailClose = document.getElementById('objektDetailClose');
    const detailTradeLink = document.getElementById('objektDetailTradeLink');
    const detailSellLink = document.getElementById('objektDetailSellLink');
    const descriptionEdit = document.getElementById('objektDescriptionEdit');
    const descriptionForm = document.getElementById('objektDescriptionForm');
    const descriptionInput = document.getElementById('objektModalDescription');
    const descriptionStatus = document.getElementById('objektDescriptionStatus');
    const descriptionDelete = document.getElementById('objektDescriptionDelete');
    const descriptionScope = document.getElementById('objektDescriptionScope');
    const descriptionScopeHint = document.getElementById('objektDescriptionScopeHint');
    const descriptionMembersEl = document.getElementById('objektDescriptionMembers');
    const descriptionNumbersEl = document.getElementById('objektDescriptionNumbers');
    const descriptionMembersPanel = document.getElementById('objektDescriptionMembersPanel');
    const descriptionNumbersPanel = document.getElementById('objektDescriptionNumbersPanel');
    const descriptionMembersToggle = document.getElementById('objektDescriptionMembersToggle');
    const descriptionNumbersToggle = document.getElementById('objektDescriptionNumbersToggle');
    const descriptionMembersSummary = document.getElementById('objektDescriptionMembersSummary');
    const descriptionNumbersSummary = document.getElementById('objektDescriptionNumbersSummary');
    const descriptionSeasonLabel = document.getElementById('objektDescriptionSeasonLabel');
    let descriptionUrl = '', descriptionSaving = false, detailRequest = 0;
    let descriptionMeta = {
        scope: 'single',
        label: '',
        count: 1,
        member: '',
        number: '',
        season: '',
        members: [],
        numbers: [],
        selectedMembers: [],
        selectedNumbers: []
    };
    let activeObjektKey = '';
    let detailLoading = false;
    let isAdmin = false;
    let lastStoryText = '';
    let detailOpenedAt = 0;
    const detailPrefetch = new Map();

    function csrfHeaders(extra) {
        const headers = new Headers(extra || {});
        headers.set('Accept', 'application/json');
        if (window.AboutAdminAuth && typeof window.AboutAdminAuth.applyAuthHeaders === 'function') {
            return window.AboutAdminAuth.applyAuthHeaders(headers);
        }
        const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
        if (match) headers.set('X-XSRF-TOKEN', decodeURIComponent(match[1]));
        return headers;
    }

    function setFavoriteButton(on, count) {
        if (!detailFavorite) return;
        const parsed = Number(count);
        const total = Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed))
            : Math.max(0, Number(detailFavorite.dataset.count || 0) || 0);
        detailFavorite.classList.toggle('is-on', Boolean(on));
        const icon = detailFavorite.querySelector('.objekt-fav-icon');
        const countEl = detailFavorite.querySelector('.objekt-fav-count');
        if (icon) icon.textContent = on ? '♥' : '♡';
        if (countEl) countEl.textContent = String(total);
        detailFavorite.dataset.count = String(total);
        const label = (on ? '찜 해제' : '찜하기') + ', ' + total.toLocaleString('ko-KR') + '개';
        detailFavorite.setAttribute('aria-label', label);
        detailFavorite.setAttribute('aria-pressed', String(Boolean(on)));
        detailFavorite.dataset.favorite = on ? 'true' : 'false';
    }

    function requestDetail(key) {
        if (!key) return Promise.reject(new Error('missing key'));
        const existing = detailPrefetch.get(key);
        if (existing) return existing;
        const request = fetch('/cosmo/objekt/detail/' + encodeURIComponent(key), {
            headers: csrfHeaders({ Accept: 'application/json' }),
            credentials: 'same-origin',
            cache: 'no-store'
        }).then(async (res) => {
            if (!res.ok) throw new Error('detail failed');
            return res.json();
        }).finally(() => {
            if (detailPrefetch.get(key) === request) detailPrefetch.delete(key);
        });
        detailPrefetch.set(key, request);
        return request;
    }

    function paintFromCard(card) {
        if (!card) return;
        const titleLink = card.querySelector('.objekt-detail-link');
        const collectionId = (titleLink?.getAttribute('title') || '').trim()
            || (card.querySelector('.objekt-open-detail')?.getAttribute('aria-label') || '').replace(/\s*상세 보기\s*$/, '').trim();
        if (detailTitle) detailTitle.textContent = collectionId || '—';
        const memberName = (card.querySelector('.objekt-member-name')?.textContent || card.dataset.member || '').trim();
        if (detailMember) {
            detailMember.textContent = memberName || '—';
            const color = card.querySelector('.objekt-member-chip')?.style.getPropertyValue('--artist-color') || '';
            if (color) detailMember.style.setProperty('--artist-color', color);
            const href = card.querySelector('.objekt-member-chip')?.getAttribute('href');
            if (href) detailMember.href = href;
            else detailMember.removeAttribute('href');
        }
        if (detailSeason) {
            detailSeason.textContent = (card.querySelector('.objekt-card-season')?.textContent || card.dataset.season || '—').trim() || '—';
        }
        if (detailClass) {
            detailClass.textContent = (card.querySelector('.objekt-card-class')?.textContent || card.dataset.class || '—').trim() || '—';
        }
        if (detailNo) detailNo.textContent = (card.dataset.collectionNo || '—').trim() || '—';
        const img = card.querySelector('.objekt-image');
        resetModalFlip(img?.currentSrc || img?.src || '', '', false);
    }

    function startSerialPrefetch(key) {
        if (!key) return;
        window.ObjektSerialLookup?.setCollection('/cosmo/objekt/detail/' + encodeURIComponent(key) + '/lookup');
        window.ObjektSerialLookup?.prefetch('1');
    }

    function safeMediaUrl(raw) {
        const value = String(raw || '').trim();
        if (!/^https?:\/\//i.test(value)) return '';
        try {
            const url = new URL(value);
            if (url.protocol !== 'http:' && url.protocol !== 'https:') return '';
            return url.href;
        } catch (_) {
            return '';
        }
    }

    function stopDetailMotion() {
        if (!detailVideo) return;
        detailVideo.dataset.active = 'false';
        try { detailVideo.pause(); } catch (_) { /* ignore */ }
        detailVideo.removeAttribute('src');
        detailVideo.hidden = true;
    }

    function showStillImage(url) {
        if (!detailImage) return;
        detailImage.hidden = false;
        detailImage.src = url || '';
        if (detailVideo) detailVideo.hidden = true;
    }

    function playDetailVideo() {
        if (!detailVideo || detailVideo.dataset.active !== 'true') return;
        if (detailImage) detailImage.hidden = true;
        detailVideo.hidden = false;
        try {
            const play = detailVideo.play();
            if (play && typeof play.catch === 'function') play.catch(() => {
                showStillImage(detailImage?.dataset.front || '');
            });
        } catch (_) {
            showStillImage(detailImage?.dataset.front || '');
        }
    }
    detailVideo?.addEventListener('error', () => {
        if (detailVideo.dataset.active !== 'true') return;
        stopDetailMotion();
        showStillImage(detailImage?.dataset.front || '');
    });

    function motionMediaUrl(data) {
        const existing = safeMediaUrl(data?.frontMedia);
        if (existing) return existing;
        if (String(data?.class || '').toLowerCase() !== 'motion') return '';
        const slug = String(data?.slug || '').trim().toLowerCase();
        if (!/^[a-z0-9]+(?:-[a-z0-9]+)+$/.test(slug)) return '';
        const artist = String(data?.artist || 'triples').trim().toLowerCase() || 'triples';
        return `https://cdn.apollo.cafe/mco/${artist}/${slug}.mp4`;
    }

    function applyDetailMotion(data, frontUrl) {
        stopDetailMotion();
        if (detailImage) detailImage.dataset.motion = '';
        showStillImage(frontUrl);
        const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const motion = String(data?.class || '').toLowerCase() === 'motion';
        const media = motionMediaUrl(data);
        if (!motion || !media || reduce) return;
        if (/\.(gif|webp|apng|png|jpe?g)(\?|#|$)/i.test(media) && detailImage) {
            detailImage.dataset.motion = media;
            detailImage.src = media;
            return;
        }
        if (!detailVideo) return;
        detailVideo.dataset.active = 'true';
        detailVideo.src = media;
        playDetailVideo();
    }

    function resetModalFlip(front, back, hasBack) {
        if (!detailImage || !detailFlip || !detailFace) return;
        const frontUrl = front || '';
        const backUrl = hasBack ? (back || '') : '';
        stopDetailMotion();
        detailImage.dataset.front = frontUrl;
        detailImage.dataset.back = backUrl;
        detailImage.dataset.motion = '';
        detailImage.dataset.showingBack = 'false';
        showStillImage(frontUrl);
        detailFace.hidden = !hasBack;
        detailFace.textContent = '앞면';
        detailFlip.disabled = !hasBack;
        detailFlip.classList.toggle('is-flippable', Boolean(hasBack));
        detailFlip.setAttribute('aria-disabled', hasBack ? 'false' : 'true');
        detailFlip.tabIndex = hasBack ? 0 : -1;
        detailFlip.setAttribute('aria-label', hasBack ? '이미지 클릭 시 뒷면으로 전환' : '오브젝트 이미지');
    }

    function flipModalImage() {
        if (!detailImage || !detailFlip || !detailFlip.classList.contains('is-flippable')) return;
        const showingBack = detailImage.dataset.showingBack === 'true';
        const next = showingBack ? (detailImage.dataset.motion || detailImage.dataset.front) : detailImage.dataset.back;
        if (!next && !(showingBack && detailVideo?.dataset.active === 'true')) return;
        detailImage.dataset.showingBack = showingBack ? 'false' : 'true';
        if (showingBack && detailVideo?.dataset.active === 'true') {
            playDetailVideo();
        } else {
            if (detailVideo) {
                detailVideo.pause();
                detailVideo.hidden = true;
            }
            showStillImage(next);
        }
        if (detailFace) {
            detailFace.hidden = false;
            detailFace.textContent = showingBack ? '앞면' : '뒷면';
        }
        detailFlip.setAttribute('aria-label', showingBack ? '이미지 클릭 시 뒷면으로 전환' : '이미지 클릭 시 앞면으로 전환');
    }

    function hasHangul(line) {
        return /[\uAC00-\uD7A3]/.test(line);
    }

    function isLatinLine(line) {
        return /[A-Za-z]/.test(line) && !hasHangul(line);
    }

    function displayStoryText(raw) {
        const lines = String(raw || '').trim().replace(/\r\n/g, '\n').split('\n');
        const out = [];
        for (let i = 0; i < lines.length; i++) {
            const current = lines[i];
            const next = lines[i + 1];
            out.push(current);
            if (current && next && hasHangul(current) && isLatinLine(next)) out.push('');
        }
        return out.join('\n');
    }

    function renderStoryText(el, raw) {
        const text = String(raw || '').trim();
        const shown = displayStoryText(text);
        el.classList.toggle('is-empty', !text);
        if (!shown) {
            el.textContent = '아직 등록된 상세 설명이 없습니다.';
            return;
        }
        const frag = document.createDocumentFragment();
        const lines = shown.split('\n');
        let buffer = [];
        const flushBlock = () => {
            if (!buffer.length) return;
            const block = document.createElement('span');
            block.className = 'objekt-detail-desc-block';
            block.textContent = buffer.join('\n');
            frag.appendChild(block);
            buffer = [];
        };
        for (const line of lines) {
            if (line === '') {
                flushBlock();
                const gap = document.createElement('span');
                gap.className = 'objekt-detail-desc-break';
                gap.setAttribute('aria-hidden', 'true');
                frag.appendChild(gap);
            } else {
                buffer.push(line);
            }
        }
        flushBlock();
        el.replaceChildren(frag);
    }

    function fillDetailStory(data) {
        const text = String(data && data.description || '').trim();
        lastStoryText = text;
        if (detailDesc) renderStoryText(detailDesc, text);
        if (descriptionInput && descriptionForm?.hidden) descriptionInput.value = text;
        const eventNode = document.getElementById('objektDetailEvent');
        if (eventNode) {
            eventNode.hidden = true;
            eventNode.textContent = '';
        }
    }

    function selectedShareMembers() {
        if (!descriptionMembersEl) return descriptionMeta.member ? [descriptionMeta.member] : [];
        return Array.from(descriptionMembersEl.querySelectorAll('input[name="descriptionMembers"]:checked'))
            .map((input) => input.value)
            .filter(Boolean);
    }

    function selectedShareNumbers() {
        if (!descriptionNumbersEl) return descriptionMeta.number ? [descriptionMeta.number] : [];
        return Array.from(descriptionNumbersEl.querySelectorAll('input[name="descriptionNumbers"]:checked'))
            .map((input) => input.value)
            .filter(Boolean);
    }

    function memberLabelForToken(token) {
        const row = descriptionMeta.members.find((item) => String(item.token || '') === token);
        return row && row.label ? String(row.label) : token;
    }

    function shareMembersSummaryText() {
        const selected = selectedShareMembers();
        const total = descriptionMembersEl
            ? descriptionMembersEl.querySelectorAll('input[name="descriptionMembers"]').length
            : descriptionMeta.members.length || selected.length;
        if (!selected.length) return '멤버 선택';
        if (total > 0 && selected.length >= total) return '전체 ' + total + '명';
        if (selected.length === 1) return memberLabelForToken(selected[0]);
        const first = memberLabelForToken(selected[0]);
        return first + ' 외 ' + (selected.length - 1) + '명';
    }

    function shareNumbersSummaryText() {
        const selected = selectedShareNumbers();
        const total = descriptionNumbersEl
            ? descriptionNumbersEl.querySelectorAll('input[name="descriptionNumbers"]').length
            : descriptionMeta.numbers.length || selected.length;
        if (!selected.length) return '번호 선택';
        if (total > 0 && selected.length >= total) return '전체 ' + total + '개';
        if (selected.length === 1) return selected[0];
        return selected[0] + ' 외 ' + (selected.length - 1) + '개';
    }

    function refreshShareSummaries() {
        if (descriptionMembersSummary) descriptionMembersSummary.textContent = shareMembersSummaryText();
        if (descriptionNumbersSummary) descriptionNumbersSummary.textContent = shareNumbersSummaryText();
        if (descriptionScopeHint) descriptionScopeHint.textContent = descriptionScopeHintText();
    }

    function descriptionScopeHintText() {
        const members = selectedShareMembers();
        const numbers = selectedShareNumbers();
        const memberCount = members.length || 1;
        const numberCount = numbers.length || 1;
        const season = descriptionMeta.season || '이 시즌';
        if (memberCount <= 1 && numberCount <= 1) {
            return '이 멤버·이 번호에만 저장합니다.';
        }
        if (memberCount > 1 && numberCount > 1) {
            return season + '에서 고른 멤버 ' + memberCount + '명 · 번호 ' + numberCount + '개에 같은 설명을 넣습니다.';
        }
        if (memberCount > 1) {
            return '이 번호에서 고른 멤버 ' + memberCount + '명끼리 설명을 공유합니다. 선택하지 않은 멤버는 그대로 둡니다.';
        }
        return season + '에서 고른 번호 ' + numberCount + '개에 같은 설명을 넣습니다.';
    }

    function setSharePanelOpen(panel, toggle, open) {
        if (!panel || !toggle) return;
        panel.hidden = !open;
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    function renderShareOptions(container, name, items, lockedValue) {
        if (!container) return;
        container.replaceChildren();
        items.forEach((item) => {
            const value = typeof item === 'string' ? item : String(item.token || item.value || '');
            if (!value) return;
            const labelText = typeof item === 'string' ? item : String(item.label || value);
            const locked = value === lockedValue;
            const selected = locked || (typeof item === 'string' ? false : Boolean(item.selected));
            const label = document.createElement('label');
            label.className = 'objekt-description-share-option' + (locked ? ' is-current' : '');
            label.title = labelText;
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.name = name;
            input.value = value;
            input.checked = selected || locked;
            if (locked) input.disabled = true;
            const text = document.createElement('span');
            text.textContent = labelText;
            label.append(input, text);
            container.append(label);
        });
    }

    function applyMemberSharePreset(mode) {
        if (!descriptionMembersEl) return;
        const boxes = descriptionMembersEl.querySelectorAll('input[name="descriptionMembers"]');
        boxes.forEach((input) => {
            if (input.disabled) {
                input.checked = true;
                return;
            }
            input.checked = mode === 'all';
        });
        refreshShareSummaries();
    }

    function syncDescriptionScopeUi() {
        if (!descriptionScope) return;
        const canShare = descriptionMeta.scope === 'group' || descriptionMeta.scope === 'member';
        descriptionScope.hidden = !canShare;
        if (!canShare) return;
        if (descriptionSeasonLabel) {
            descriptionSeasonLabel.textContent = descriptionMeta.season ? '(' + descriptionMeta.season + ')' : '';
        }
        const selectedMemberSet = new Set(
            (descriptionMeta.selectedMembers || []).map((value) => String(value || '').trim()).filter(Boolean)
        );
        if (descriptionMeta.member) selectedMemberSet.add(descriptionMeta.member);
        const selectedNumberSet = new Set(
            (descriptionMeta.selectedNumbers || []).map((value) => String(value || '').trim().toLowerCase()).filter(Boolean)
        );
        if (descriptionMeta.number) selectedNumberSet.add(descriptionMeta.number.toLowerCase());
        const memberItems = (descriptionMeta.members.length
            ? descriptionMeta.members
            : (descriptionMeta.member ? [{token: descriptionMeta.member, label: descriptionMeta.member, selected: true, current: true}] : [])
        ).map((item) => {
            const token = String(item.token || item.value || '');
            return {
                ...item,
                token,
                selected: selectedMemberSet.size
                    ? selectedMemberSet.has(token)
                    : Boolean(item.selected) || token === descriptionMeta.member,
                current: token === descriptionMeta.member
            };
        });
        const numberItems = (descriptionMeta.numbers.length ? descriptionMeta.numbers : [descriptionMeta.number].filter(Boolean))
            .map((number) => ({
                token: number,
                label: number,
                selected: selectedNumberSet.has(String(number).toLowerCase()),
                current: number === descriptionMeta.number
            }));
        renderShareOptions(descriptionMembersEl, 'descriptionMembers', memberItems, descriptionMeta.member);
        renderShareOptions(descriptionNumbersEl, 'descriptionNumbers', numberItems, descriptionMeta.number);
        setSharePanelOpen(descriptionMembersPanel, descriptionMembersToggle, false);
        setSharePanelOpen(descriptionNumbersPanel, descriptionNumbersToggle, false);
        refreshShareSummaries();
    }

    function rememberDescriptionMeta(data) {
        const count = Number(data && data.descriptionGroupCount);
        const members = Array.isArray(data && data.descriptionShareMembers) ? data.descriptionShareMembers : null;
        const numbers = Array.isArray(data && data.descriptionSeasonNumbers) ? data.descriptionSeasonNumbers : null;
        const selectedMembers = Array.isArray(data && data.descriptionSelectedMembers)
            ? data.descriptionSelectedMembers.map((value) => String(value || '').trim()).filter(Boolean)
            : null;
        const selectedNumbers = Array.isArray(data && data.descriptionSelectedNumbers)
            ? data.descriptionSelectedNumbers.map((value) => String(value || '').trim()).filter(Boolean)
            : null;
        descriptionMeta = {
            scope: data && data.descriptionScope === 'member' ? 'member'
                : data && data.descriptionScope === 'group' ? 'group' : 'single',
            label: String(data && data.descriptionGroupLabel || '').trim(),
            count: Number.isFinite(count) && count > 0 ? count : 1,
            member: String(data && data.descriptionMember || descriptionMeta.member || '').trim(),
            number: String(data && data.descriptionNumber || descriptionMeta.number || '').trim(),
            season: String(data && data.descriptionSeason || descriptionMeta.season || '').trim(),
            members: members && members.length ? members : descriptionMeta.members,
            numbers: numbers && numbers.length
                ? numbers.map((value) => String(value || '').trim()).filter(Boolean)
                : descriptionMeta.numbers,
            selectedMembers: selectedMembers && selectedMembers.length ? selectedMembers : descriptionMeta.selectedMembers,
            selectedNumbers: selectedNumbers && selectedNumbers.length ? selectedNumbers : descriptionMeta.selectedNumbers
        };
    }

    function exitDescriptionEdit() {
        if (descriptionForm) descriptionForm.hidden = true;
        if (detailDesc) detailDesc.hidden = false;
        if (descriptionEdit) descriptionEdit.hidden = !isAdmin;
        if (descriptionStatus) descriptionStatus.textContent = '';
        if (descriptionInput) descriptionInput.value = lastStoryText;
        if (descriptionScope) descriptionScope.hidden = true;
    }

    function enterDescriptionEdit() {
        if (!isAdmin || !descriptionForm || !descriptionInput) return;
        descriptionInput.value = lastStoryText;
        if (detailDesc) detailDesc.hidden = true;
        descriptionForm.hidden = false;
        if (descriptionEdit) descriptionEdit.hidden = true;
        if (descriptionStatus) descriptionStatus.textContent = '';
        syncDescriptionScopeUi();
        descriptionInput.focus();
    }

    function fillDetailModal(data) {
        const key = data.objektKey || data.id || activeObjektKey;
        activeObjektKey = key;
        if (detailTitle) detailTitle.textContent = data.collectionId || '—';
        if (detailMember) {
            const memberName = data.member || '—';
            detailMember.textContent = memberName;
            detailMember.style.setProperty('--artist-color', data.memberColor || '#00f5d4');
            const href = data.membersTabUrl || data.memberUrl;
            if (href) detailMember.href = href;
            else detailMember.removeAttribute('href');
        }
        if (detailSeason) detailSeason.textContent = data.season || '—';
        if (detailClass) detailClass.textContent = data.class || '—';
        if (detailNo) detailNo.textContent = data.collectionNo || '—';
        fillDetailStory(data);
        if (detailImage) detailImage.alt = data.collectionId || '';
        const frontUrl = data.frontImage || data.thumbnailImage || '';
        resetModalFlip(frontUrl, data.backImage || '', Boolean(data.hasBack));
        applyDetailMotion(data, frontUrl);
        setFavoriteButton(Boolean(data.isFavorite), data.favoriteCount);
        if (detailFavorite) detailFavorite.disabled = false;
        const actions = document.querySelector('.objekt-detail-actions');
        if (actions) actions.inert = false;
        document.getElementById('objektFavoriteStatus').hidden = true;
        if (detailMemberLink) detailMemberLink.href = data.memberUrl || '/cosmo/objekt';
        if (detailFullLink) detailFullLink.href = data.detailUrl || ('/cosmo/objekt/detail/' + encodeURIComponent(key));
        if (detailFavorite) detailFavorite.dataset.key = key;
        if (detailTradeLink) detailTradeLink.href = data.tradeUrl || '/article/trade/new';
        if (detailSellLink) detailSellLink.href = data.sellUrl || '/article/sell/new';
        window.ObjektSerialLookup?.setCollection(
            data.lookupUrl || ('/cosmo/objekt/detail/' + encodeURIComponent(key) + '/lookup')
        );
        window.ObjektSerialLookup?.prefetch('1');
        const card = document.querySelector('.objekt-card[data-objekt-key="' + CSS.escape(key) + '"]');
        window.ObjektIssued?.show(
            data.issuedUrl || ('/cosmo/objekt/detail/' + encodeURIComponent(key) + '/issued'),
            data.mintCount ?? card?.dataset.supplyCount
        );
        const address = new URL(location.href);
        address.searchParams.set('objekt', key);
        history.replaceState(null, '', address.pathname + address.search);
        isAdmin = Boolean(data.isAdmin);
        descriptionUrl = data.descriptionUrl || '';
        rememberDescriptionMeta(data);
        exitDescriptionEdit();
    }

    function finishDetailClose() {
        stopDetailMotion();
        window.ObjektIssued?.close();
        window.ObjektSerialLookup?.setCollection('');
        detailModal.classList.remove('is-open', 'is-opening');
        detailModal.setAttribute('aria-hidden', 'true');
        activeObjektKey = '';
        lastStoryText = '';
        isAdmin = false;
        descriptionMeta = {
            scope: 'single',
            label: '',
            count: 1,
            member: '',
            number: '',
            season: '',
            members: [],
            numbers: [],
            selectedMembers: [],
            selectedNumbers: []
        };
        exitDescriptionEdit();
        detailRequest++;
        if (detailFavorite) detailFavorite.dataset.key = '';
        const address = new URL(location.href);
        address.searchParams.delete('objekt');
        history.replaceState(null, '', address.pathname + address.search);
    }

    function closeDetailModal(fromBackdrop) {
        if (!detailModal) return;
        if (fromBackdrop && (detailModal.classList.contains('is-opening') || Date.now() - detailOpenedAt < 500)) return;
        if (window.AboutModal) {
            window.AboutModal.closeModal(detailModal, { onClose: finishDetailClose });
        } else {
            detailModal.hidden = true;
            detailModal.classList.remove('flex');
            document.body.style.overflow = '';
            finishDetailClose();
        }
    }

    function openDetailModalShell() {
        if (!detailModal) return;
        detailOpenedAt = Date.now();
        window.ObjektDropdown?.closeAll();
        closeKindMenu();
        closeSortMenu();
        detailModal.hidden = false;
        detailModal.classList.add('is-open', 'is-opening');
        detailModal.setAttribute('aria-hidden', 'false');
        window.setTimeout(() => detailModal.classList.remove('is-opening'), 450);
        if (window.AboutModal) {
            window.AboutModal.openModal(detailModal, {
                focusSelector: '.objekt-detail-panel',
                onClose: finishDetailClose
            });
            detailModal.classList.add('is-open');
        } else {
            detailModal.classList.add('flex');
            document.body.style.overflow = 'hidden';
            detailModal.querySelector('.objekt-detail-panel')?.focus({ preventScroll: true });
        }
    }

    async function openObjektDetail(key) {
        if (!key) return;
        detailLoading = true;
        const request = ++detailRequest;
        activeObjektKey = '';
        if (detailFavorite) {
            detailFavorite.dataset.key = '';
            detailFavorite.disabled = true;
        }
        const actions = document.querySelector('.objekt-detail-actions');
        if (actions) actions.inert = true;
        const openingCard = document.querySelector('.objekt-card[data-objekt-key="' + CSS.escape(key) + '"]');
        window.ObjektIssued?.show('', openingCard?.dataset.supplyCount);
        isAdmin = false;
        exitDescriptionEdit();
        if (descriptionEdit) descriptionEdit.hidden = true;
        if (detailDesc) {
            detailDesc.textContent = '불러오는 중…';
            detailDesc.classList.add('is-empty');
        }
        if (openingCard) paintFromCard(openingCard);
        else {
            if (detailTitle) detailTitle.textContent = '불러오는 중…';
            [detailMember, detailSeason, detailClass, detailNo].forEach((node) => {
                if (node) node.textContent = '—';
            });
        }
        openDetailModalShell();
        startSerialPrefetch(key);
        try {
            const data = await requestDetail(key);
            if (request === detailRequest) fillDetailModal(data);
        } catch (_) {
            if (request !== detailRequest) return;
            if (detailDesc) {
                detailDesc.textContent = '상세 정보를 불러오지 못했습니다.';
                detailDesc.classList.add('is-empty');
            }
        } finally {
            if (request === detailRequest) detailLoading = false;
        }
    }

    async function saveModalDescription(clear) {
        if (descriptionSaving || !descriptionUrl || !isAdmin || descriptionForm?.hidden) return;
        const canShare = descriptionScope && !descriptionScope.hidden;
        const members = canShare ? selectedShareMembers() : [];
        const numbers = canShare ? selectedShareNumbers() : [];
        if (canShare && (!members.length || !numbers.length)) {
            if (descriptionStatus) descriptionStatus.textContent = '공유할 멤버와 적용할 번호를 골라 주세요.';
            return;
        }
        if (clear) {
            const memberCount = members.length || 1;
            const numberCount = numbers.length || 1;
            const message = memberCount > 1 || numberCount > 1
                ? ('선택한 멤버 ' + memberCount + '명 · 번호 ' + numberCount + '개의 설명을 삭제할까요?')
                : '이 오브젝트의 상세 설명을 삭제하시겠습니까?';
            if (!window.confirm(message)) return;
        }
        if (!clear && !descriptionForm.reportValidity()) return;
        const key = activeObjektKey;
        descriptionSaving = true;
        const controls = descriptionForm.querySelectorAll('button, textarea');
        controls.forEach(control => control.disabled = true);
        descriptionStatus.textContent = clear ? '삭제 중…' : '저장 중…';
        try {
            const body = new URLSearchParams({description: clear ? '' : descriptionInput.value});
            members.forEach((member) => body.append('members', member));
            numbers.forEach((number) => body.append('numbers', number));
            const response = await fetch(descriptionUrl, {
                method: 'POST', headers: csrfHeaders(), credentials: 'same-origin',
                body
            });
            if (!response.ok) throw new Error();
            const data = await response.json();
            if (typeof data.description !== 'string') throw new Error();
            if (activeObjektKey !== key) return;
            rememberDescriptionMeta(data);
            fillDetailStory({description: data.description});
            exitDescriptionEdit();
            const memberCount = members.length || 1;
            const numberCount = numbers.length || 1;
            descriptionStatus.textContent = clear
                ? (memberCount > 1 || numberCount > 1
                    ? '선택한 범위의 설명을 삭제했습니다.'
                    : '상세 설명을 삭제했습니다.')
                : (memberCount > 1 || numberCount > 1
                    ? '선택한 멤버·번호에 설명을 저장했습니다.'
                    : '상세 설명을 저장했습니다.');
        } catch {
            if (activeObjektKey === key) descriptionStatus.textContent = '저장하지 못했습니다. 관리자 로그인 상태를 확인한 뒤 다시 시도해 주세요.';
        } finally {
            descriptionSaving = false; controls.forEach(control => control.disabled = false);
        }
    }
    descriptionEdit?.addEventListener('click', enterDescriptionEdit);
    descriptionForm?.addEventListener('submit', event => { event.preventDefault(); saveModalDescription(false); });
    descriptionDelete?.addEventListener('click', () => saveModalDescription(true));
    descriptionForm?.addEventListener('change', (event) => {
        if (!(event.target instanceof HTMLInputElement)) return;
        if (event.target.name !== 'descriptionMembers' && event.target.name !== 'descriptionNumbers') return;
        refreshShareSummaries();
    });
    descriptionMembersToggle?.addEventListener('click', () => {
        const open = descriptionMembersPanel?.hidden !== false;
        setSharePanelOpen(descriptionMembersPanel, descriptionMembersToggle, open);
    });
    descriptionNumbersToggle?.addEventListener('click', () => {
        const open = descriptionNumbersPanel?.hidden !== false;
        setSharePanelOpen(descriptionNumbersPanel, descriptionNumbersToggle, open);
    });
    descriptionForm?.addEventListener('click', (event) => {
        const button = event.target instanceof Element
            ? event.target.closest('[data-share-members]')
            : null;
        if (!button) return;
        event.preventDefault();
        applyMemberSharePreset(button.getAttribute('data-share-members') === 'all' ? 'all' : 'current');
    });

    function shouldOpenModalFromEvent(event) {
        if (event.defaultPrevented) return false;
        if (event.button != null && event.button !== 0) return false;
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return false;
        return true;
    }

    grid?.addEventListener('pointerdown', (event) => {
        if (event.button != null && event.button !== 0) return;
        const memberChip = event.target.closest('.objekt-member-chip');
        if (memberChip) return;
        const card = event.target.closest('.objekt-card[data-objekt-key]');
        const openHit = event.target.closest('.objekt-open-detail, .objekt-detail-link');
        if (!card || !openHit) return;
        requestDetail(card.dataset.objektKey).catch(() => {});
    });
    grid?.addEventListener('click', (event) => {
        const memberChip = event.target.closest('.objekt-member-chip');
        if (memberChip) return;
        const card = event.target.closest('.objekt-card[data-objekt-key]');
        if (!card) return;
        const openHit = event.target.closest('.objekt-open-detail, .objekt-detail-link');
        if (!openHit && !event.target.closest('.objekt-card')) return;
        if (!openHit) return;
        if (!shouldOpenModalFromEvent(event)) return;
        event.preventDefault();
        event.stopPropagation();
        openObjektDetail(card.dataset.objektKey);
    });

    detailClose?.addEventListener('click', () => closeDetailModal(false));
    detailModal?.addEventListener('click', (event) => {
        if (event.target === detailModal) closeDetailModal(true);
    });
    detailFlip?.addEventListener('click', flipModalImage);
    detailFavorite?.addEventListener('click', async () => {
        const key = detailFavorite.dataset.key || activeObjektKey;
        if (!key || detailFavorite.disabled) return;
        const desired = detailFavorite.dataset.favorite !== 'true';
        const status = document.getElementById('objektFavoriteStatus');
        status.hidden = true;
        detailFavorite.disabled = true;
        const save = () => fetch('/cosmo/objekt/detail/' + encodeURIComponent(key) + '/favorite?favorite=' + desired, {
            method: 'PUT', headers: csrfHeaders({Accept:'application/json'}), credentials:'same-origin'
        });
        try {
            let response = await save();
            if (response.status === 403) {
                await fetch('/api/artist/admin/status', {credentials:'same-origin', cache:'no-store'});
                response = await save();
            }
            if (!response.ok) throw new Error(String(response.status));
            const data = await response.json();
            if (activeObjektKey !== key) return;
            setFavoriteButton(Boolean(data.favorite ?? data.isFavorite), data.favoriteCount);
            detailPrefetch.delete(key);
            status.textContent = desired ? '찜 목록에 저장했습니다.' : '찜 목록에서 해제했습니다.';
            status.hidden = false;
        } catch (error) {
            if (activeObjektKey !== key) return;
            status.textContent = error.message === '401' || error.message === '403'
                ? '페이지를 새로고침하고 로그인 상태를 확인한 뒤 다시 시도해 주세요.'
                : '찜 상태를 저장하지 못했습니다. 잠시 후 다시 눌러 주세요.';
            status.hidden = false;
        } finally {
            if (activeObjektKey === key) detailFavorite.disabled = false;
        }
    });

    function updateSummary() {
        if (empty) empty.classList.toggle('hidden', loadedCount > 0);
        if (!statusEl) return;
        if (loading) statusEl.textContent = '불러오는 중…';
        else if (hasMore) statusEl.textContent = '스크롤하면 더 불러옵니다';
        else if (loadedCount > 0) statusEl.textContent = `모두 불러왔습니다 · ${loadedCount} / ${resultCount}`;
        else statusEl.textContent = '';
        spinner?.classList.toggle('hidden', !loading);
    }

    function cardTitleFrom(collectionId, season) {
        const title = (collectionId || '').trim();
        const prefix = (season || '').trim();
        if (!title || !prefix) return title;
        if (title.toLowerCase().startsWith(prefix.toLowerCase() + ' ')) {
            return title.slice(prefix.length).trim() || title;
        }
        return title;
    }

    function memberChipHtml(item, member) {
        const color = item.memberColor || '#00f5d4';
        const logo = typeof item.memberLogo === 'string' ? item.memberLogo.trim() : '';
        const mark = logo
            ? `<img class="objekt-member-logo" src="${attr(logo)}" alt="" aria-hidden="true">`
            : '<span class="objekt-member-logo" aria-hidden="true"></span>';
        return `<a href="/cosmo/objekt/member?name=${encodeURIComponent(member)}" class="objekt-member-chip" style="--artist-color:${attr(color)}">${mark}<span class="objekt-member-name">${escapeHtml(member)}</span></a>`;
    }

    function buildCard(item) {
        const collectionId = item.collectionId || '';
        const member = item.member || item.memberName || '';
        const collectionNo = item.collectionNo || item.collectionNumber || '';
        const id = item.publicKey || item.id || '';
        const thumb = item.thumbnailImage || item.frontImage || '';
        const front = item.frontImage || thumb;
        const klass = item.class || item.classKey || '';
        const season = item.season || item.seasonKey || '';
        const title = item.cardTitle || cardTitleFrom(collectionId, season);
        return `<article class="card objekt-card objekt-item group relative border rounded-2xl overflow-hidden transition-all duration-300 hover:-translate-y-1"
                 data-objekt-key="${attr(id)}"
                 data-season="${attr(item.seasonKey || season)}" data-class="${attr(item.classKey || klass)}" data-artist="${attr(item.artist || '')}" data-collection-no="${attr(collectionNo)}"
                 data-number="${attr(item.numberValue || '')}" data-date="${attr(item.createdAt || '')}" data-member="${attr(member)}" data-search="${attr(item.searchText || '')}"${Number.isFinite(item.mintCount) ? ` data-supply-count="${attr(item.mintCount)}" data-supply-done="true"` : ''}>
            <button type="button" class="objekt-open-detail" aria-label="${attr(collectionId)} 상세 보기">
                <img src="${attr(thumb)}" alt="${attr(collectionId)}" loading="lazy" decoding="async" class="objekt-image" onerror="this.onerror=null;this.src='${attr(front)}';">
            </button>
            <div class="card-body objekt-card-body">
                <div class="objekt-card-badges">
                    <span class="badge badge-sm objekt-card-season" style="background:${attr(seasonBadgeColor(item, season))}">${escapeHtml(season)}</span>
                    <a href="/cosmo/objekt/detail/${attr(id)}" class="objekt-detail-link" title="${attr(collectionId)}">${escapeHtml(title)}</a>
                </div>
                <div class="objekt-card-foot">
                    ${memberChipHtml(item, member)}
                    ${klass ? `<span class="badge badge-sm badge-neutral objekt-card-class">${escapeHtml(klass)}</span>` : ''}
                </div>
                <p class="objekt-card-supply" data-supply-count${Number.isFinite(item.mintCount) ? ` data-supply-done="true"` : ''}>${Number.isFinite(item.mintCount) ? `발행량 ${escapeHtml(String(item.mintCount))}개` : '발행량 확인 중'}</p>
            </div>
        </article>`;
    }

    function currentFilterParams() {
        const params = new URLSearchParams(new FormData(form));
        params.delete('page');
        if (kindSelect) params.set('kind', normalizeKindValue(kindSelect.value));
        return params;
    }

    function scheduleApplyFilters() {
        clearTimeout(filterTimer);
        filterTimer = setTimeout(applyFilters, 80);
    }

    function syncFilterAddress(params) {
        const address = new URL(location.href);
        const objekt = address.searchParams.get('objekt');
        address.search = params.toString();
        if (objekt) address.searchParams.set('objekt', objekt);
        history.replaceState(null, '', address.pathname + address.search);
    }

    function renderLoadedCards(items) {
        if (!grid) return;
        const wrap = document.createElement('div');
        wrap.innerHTML = items.map(buildCard).join('');
        const fragment = document.createDocumentFragment();
        while (wrap.firstChild) fragment.appendChild(wrap.firstChild);
        grid.replaceChildren(fragment);
    }

    function rememberCounts() {
        if (summary) {
            summary.dataset.loaded = String(loadedCount);
            summary.dataset.resultCount = String(resultCount);
            summary.dataset.allCount = String(allCount);
            const strong = summary.querySelector('strong');
            if (strong) strong.textContent = String(resultCount);
        }
        if (empty) empty.classList.toggle('hidden', loadedCount > 0);
    }

    async function applyFilters() {
        if (!grid) return;
        const request = ++listGeneration;
        loading = true;
        hasMore = false;
        updateSummary();
        const params = currentFilterParams();
        params.set('page', '0');
        try {
            const res = await fetch(`/cosmo/objekt/more?${params.toString()}`, {
                headers: { Accept: 'application/json' },
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (!res.ok) throw new Error('load failed');
            const data = await res.json();
            if (request !== listGeneration) return;
            const items = Array.isArray(data.items) ? data.items : [];
            renderLoadedCards(items);
            loadedCount = items.length;
            resultCount = Number(data.resultCount ?? 0);
            allCount = Number(data.allCount ?? allCount);
            hasMore = Boolean(data.hasMore) && items.length > 0;
            nextPage = data.nextPage == null ? 1 : Number(data.nextPage);
            rememberCounts();
            params.delete('page');
            syncFilterAddress(params);
        } catch (_) {
            if (request !== listGeneration) return;
            if (statusEl) statusEl.textContent = '검색 결과를 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            if (request === listGeneration) {
                loading = false;
                updateSummary();
            }
        }
    }

    async function loadMore() {
        if (!hasMore || loading || !grid) return;
        const request = listGeneration;
        loading = true;
        updateSummary();
        try {
            const params = currentFilterParams();
            params.set('page', String(nextPage));
            const res = await fetch(`/cosmo/objekt/more?${params.toString()}`, {
                headers: { Accept: 'application/json' },
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (!res.ok) throw new Error('load failed');
            const data = await res.json();
            if (request !== listGeneration) return;
            const items = Array.isArray(data.items) ? data.items : [];
            if (items.length) {
                const wrap = document.createElement('div');
                wrap.innerHTML = items.map(buildCard).join('');
                const fragment = document.createDocumentFragment();
                while (wrap.firstChild) fragment.appendChild(wrap.firstChild);
                grid.appendChild(fragment);
            }
            loadedCount += items.length;
            resultCount = Number(data.resultCount ?? resultCount);
            allCount = Number(data.allCount ?? allCount);
            hasMore = Boolean(data.hasMore) && items.length > 0;
            nextPage = data.nextPage == null ? nextPage + 1 : Number(data.nextPage);
            rememberCounts();
        } catch (_) {
            if (request !== listGeneration) return;
            hasMore = false;
            if (statusEl) statusEl.textContent = '더 불러오지 못했습니다. 잠시 후 다시 스크롤해 주세요.';
        } finally {
            if (request === listGeneration) {
                loading = false;
                updateSummary();
            }
        }
    }

    if (empty) empty.classList.toggle('hidden', cards.length > 0);
    updateSummary();

    if (sentinel && 'IntersectionObserver' in window) {
        const io = new IntersectionObserver((entries) => {
            if (entries.some((entry) => entry.isIntersecting)) loadMore();
        }, { rootMargin: '400px 0px' });
        io.observe(sentinel);
    } else if (hasMore) {
        // 구형 브라우저: 수동 버튼으로 대체
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'rounded-lg border border-slate-600 px-4 py-2 text-sm text-white hover:border-teal-400';
        btn.textContent = '더 보기';
        btn.addEventListener('click', loadMore);
        sentinel?.appendChild(btn);
    }

    const initialObjekt = new URLSearchParams(location.search).get('objekt');
    if (initialObjekt) openObjektDetail(initialObjekt);

    window.ObjektPage = {
        visibleKeys() {
            if (!grid) return [];
            const height = window.innerHeight || document.documentElement.clientHeight || 0;
            return [...grid.querySelectorAll('.objekt-card[data-objekt-key]')]
                .filter((card) => {
                    const box = card.getBoundingClientRect();
                    return box.bottom > 0 && box.top < height;
                })
                .map((card) => card.dataset.objektKey)
                .filter(Boolean)
                .slice(0, 24);
        },
        reloadVisible() {
            return applyFilters();
        }
    };
})();
