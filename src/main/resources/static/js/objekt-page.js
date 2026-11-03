/* Extracted from cosmo/objekt.mustache */
(() => {
    const form = document.getElementById('objekt-search-form');
    const kindSelect = document.querySelector('.objekt-kind-select');
    const sortSelect = document.querySelector('.objekt-sort-select');
    const sortToggle = document.getElementById('objekt-sort-toggle');
    const sortMenu = document.getElementById('sort-menu');
    const sortLabel = document.getElementById('sort-label');

    function closeSortMenu() {
        sortMenu.classList.add('hidden');
        sortToggle.setAttribute('aria-expanded', 'false');
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
        ['season-menu', 'class-menu'].forEach(id => document.getElementById(id)?.classList.add('hidden'));
        sortMenu.classList.remove('hidden');
        sortToggle.setAttribute('aria-expanded', 'true');
        const options = [...sortMenu.querySelectorAll('[role="option"]')];
        const focusIndex = index ?? options.findIndex(option => option.dataset.value === sortSelect.value);
        options.forEach((option, i) => { option.tabIndex = i === focusIndex ? 0 : -1; });
        options[focusIndex]?.focus();
    }

    [...sortSelect.options].forEach((option) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'objekt-kind-option';
        button.dataset.value = option.value;
        button.textContent = option.textContent;
        button.setAttribute('role', 'option');
        button.addEventListener('click', () => {
            const changed = sortSelect.value !== option.value;
            sortSelect.value = option.value;
            syncSortMenu();
            closeSortMenu();
            sortToggle.focus();
            if (changed) sortSelect.dispatchEvent(new Event('change', { bubbles: true }));
        });
        sortMenu.appendChild(button);
    });

    sortToggle.addEventListener('click', () => {
        if (sortMenu.classList.contains('hidden')) openSortMenu();
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

    function filterKindOptions(query) {
        const q = normalizeKindValue(query);
        const needle = q === 'all' ? '' : q.toLowerCase();
        let shown = 0;
        kindOptions.forEach((opt) => {
            const value = String(opt.dataset.value || '').toLowerCase();
            const match = !needle || value.includes(needle);
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
        kindMenu.classList.remove('hidden');
        kindSelect.setAttribute('aria-expanded', 'true');
        document.getElementById('season-menu')?.classList.add('hidden');
        document.getElementById('class-menu')?.classList.add('hidden');
    }

    function closeKindMenu() {
        if (!kindMenu || !kindSelect) return;
        kindMenu.classList.add('hidden');
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
        kindSelect.addEventListener('focus', () => openKindMenu(false));
        kindSelect.addEventListener('click', () => openKindMenu(false));
        kindSelect.addEventListener('input', () => {
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
            markActiveFilters();
            scheduleApplyFilters();
        }));
        updateDropdownLabel(type);
    });

    function markActiveFilters() {
        const seasonText = document.getElementById('season-label')?.textContent.trim();
        document.getElementById('season-dropdown')?.classList.toggle('is-filtered', !!seasonText && seasonText !== '전체');
        const classText = document.getElementById('class-label')?.textContent.trim();
        document.getElementById('class-dropdown')?.classList.toggle('is-filtered', !!classText && classText !== '전체');
        const kindValue = normalizeKindValue(kindSelect?.value);
        kindSelect?.closest('.objekt-filter')?.classList.toggle('is-filtered', kindValue !== 'all');
        sortSelect.closest('.objekt-filter')?.classList.toggle('is-filtered', sortSelect.value && sortSelect.value !== 'latest');
        const searchValue = (form.querySelector('input[name="search"]')?.value || '').trim();
        document.querySelector('.objekt-search')?.classList.toggle('is-filtered', searchValue.length > 0);
        const memberOn = memberGroupInput.value !== 'all' || document.querySelectorAll('.member-checkbox:checked').length > 0;
        memberRoot?.classList.toggle('is-filtered', memberOn);
    }
    markActiveFilters();
    form.addEventListener('change', () => {
        markActiveFilters();
    });

    window.toggleDropdown = function(id) {
        closeKindMenu();
        closeSortMenu();
        document.querySelectorAll('[id$="-menu"]').forEach(menu => {
            if (menu.id !== id) menu.classList.add('hidden');
        });
        document.getElementById(id)?.classList.toggle('hidden');
    };

    window.addEventListener('click', e => {
        if (!e.target.closest('#sort-dropdown')) closeSortMenu();
        if (!e.target.closest('#season-dropdown') && !e.target.closest('#class-dropdown') && !e.target.closest('#kind-dropdown')) {
            ['season-menu', 'class-menu'].forEach(id => document.getElementById(id)?.classList.add('hidden'));
            closeKindMenu();
        }
    });

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        const page = form.querySelector('input[name="page"]');
        if (page) page.value = '0';
        if (kindSelect) kindSelect.value = displayKindValue(kindSelect.value);
        applyFilters();
    });

    document.getElementById('objekt-reset').addEventListener('click', () => {
        window.location.href = '/cosmo/objekt';
    });

    const grid = document.getElementById('objekt-card-grid');
    const summary = document.getElementById('objekt-result-summary');
    const loadedEl = document.getElementById('objekt-loaded-count');
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
    const descriptionEditor = document.getElementById('objektDescriptionEditor');
    const descriptionForm = document.getElementById('objektDescriptionForm');
    const descriptionInput = document.getElementById('objektModalDescription');
    const descriptionStatus = document.getElementById('objektDescriptionStatus');
    const descriptionDelete = document.getElementById('objektDescriptionDelete');
    let descriptionUrl = '', descriptionSaving = false, detailRequest = 0;
    let activeObjektKey = '';
    let detailLoading = false;

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

    function setFavoriteButton(on) {
        if (!detailFavorite) return;
        detailFavorite.classList.toggle('is-on', Boolean(on));
        detailFavorite.textContent = on ? '♥ 찜 해제' : '♡ 찜하기';
        detailFavorite.dataset.favorite = on ? 'true' : 'false';
    }

    function resetModalFlip(front, back, hasBack) {
        if (!detailImage || !detailFlip || !detailFace) return;
        const frontUrl = front || '';
        const backUrl = hasBack ? (back || '') : '';
        detailImage.dataset.front = frontUrl;
        detailImage.dataset.back = backUrl;
        detailImage.dataset.showingBack = 'false';
        detailImage.src = frontUrl;
        detailFace.textContent = '앞면';
        detailFlip.disabled = !hasBack;
        detailFlip.classList.toggle('is-flippable', Boolean(hasBack));
        detailFlip.setAttribute('aria-label', hasBack ? '이미지 클릭 시 뒷면으로 전환' : '앞면');
    }

    function flipModalImage() {
        if (!detailImage || !detailFlip || detailFlip.disabled) return;
        const showingBack = detailImage.dataset.showingBack === 'true';
        const next = showingBack ? detailImage.dataset.front : detailImage.dataset.back;
        if (!next) return;
        detailImage.src = next;
        detailImage.dataset.showingBack = showingBack ? 'false' : 'true';
        if (detailFace) detailFace.textContent = showingBack ? '앞면' : '뒷면';
        detailFlip.setAttribute('aria-label', showingBack ? '이미지 클릭 시 뒷면으로 전환' : '이미지 클릭 시 앞면으로 전환');
    }

    let lastApollo = null;
    function fillDetailStory(data) {
        if (data && Object.prototype.hasOwnProperty.call(data, 'apollo')) {
            lastApollo = data.apollo && typeof data.apollo === 'object' ? data.apollo : null;
        }
        const local = String(data && data.description || '').trim();
        const apollo = lastApollo || {};
        const chunks = [];
        if (local) chunks.push(local);
        [apollo.description, apollo.eventName, apollo.eventDescription, apollo.eraName]
            .map(value => String(value || '').trim())
            .filter(Boolean)
            .forEach(bit => {
                if (!chunks.some(existing => existing === bit || existing.includes(bit))) chunks.push(bit);
            });
        const text = chunks.join('\n\n');
        if (detailDesc) {
            detailDesc.textContent = text || '아직 등록된 상세 설명이 없습니다.';
            detailDesc.classList.toggle('is-empty', !text);
        }
        const eventNode = document.getElementById('objektDetailEvent');
        if (eventNode) {
            const type = String(apollo.eventType || '').trim();
            eventNode.hidden = !type;
            eventNode.textContent = type;
        }
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
        lastApollo = data.apollo && typeof data.apollo === 'object' ? data.apollo : null;
        fillDetailStory(data);
        if (detailImage) detailImage.alt = data.collectionId || '';
        resetModalFlip(data.frontImage || data.thumbnailImage || '', data.backImage || '', Boolean(data.hasBack));
        setFavoriteButton(Boolean(data.isFavorite));
        detailFavorite.disabled = false;
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
        window.ObjektIssued?.show(
            data.issuedUrl || ('/cosmo/objekt/detail/' + encodeURIComponent(key) + '/issued')
        );
        const address = new URL(location.href);
        address.searchParams.set('objekt', key);
        history.replaceState(null, '', address.pathname + address.search);
        descriptionEditor.hidden = !data.isAdmin;
        descriptionEditor.open = false;
        descriptionInput.value = data.description || '';
        descriptionStatus.textContent = '';
        descriptionUrl = data.descriptionUrl || '';
    }

    function finishDetailClose() {
        window.ObjektIssued?.close();
        window.ObjektSerialLookup?.setCollection('');
        detailModal.classList.remove('is-open');
        detailModal.setAttribute('aria-hidden', 'true');
        activeObjektKey = '';
        lastApollo = null;
        detailRequest++;
        if (detailFavorite) detailFavorite.dataset.key = '';
        const address = new URL(location.href);
        address.searchParams.delete('objekt');
        history.replaceState(null, '', address.pathname + address.search);
    }

    function closeDetailModal() {
        if (!detailModal) return;
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
        detailModal.hidden = false;
        detailModal.classList.add('is-open');
        detailModal.setAttribute('aria-hidden', 'false');
        if (window.AboutModal) {
            window.AboutModal.openModal(detailModal, {
                focusSelector: '#objektDetailClose',
                onClose: finishDetailClose
            });
            detailModal.classList.add('is-open');
        } else {
            detailModal.classList.add('flex');
            document.body.style.overflow = 'hidden';
            detailClose?.focus();
        }
    }

    async function openObjektDetail(key) {
        if (!key || detailLoading) return;
        detailLoading = true;
        const request = ++detailRequest;
        activeObjektKey = '';
        if (detailFavorite) {
            detailFavorite.dataset.key = '';
            detailFavorite.disabled = true;
        }
        const actions = document.querySelector('.objekt-detail-actions');
        if (actions) actions.inert = true;
        [detailMember, detailSeason, detailClass, detailNo].forEach((node) => {
            if (node) node.textContent = '—';
        });
        window.ObjektIssued?.show('');
        window.ObjektSerialLookup?.setCollection('');
        descriptionEditor.hidden = true;
        openDetailModalShell();
        if (detailDesc) {
            detailDesc.textContent = '불러오는 중…';
            detailDesc.classList.add('is-empty');
        }
        if (detailTitle) detailTitle.textContent = '불러오는 중…';
        try {
            const res = await fetch('/cosmo/objekt/detail/' + encodeURIComponent(key), {
                headers: csrfHeaders({ Accept: 'application/json' }),
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (!res.ok) throw new Error('detail failed');
            const data = await res.json();
            if (request === detailRequest) fillDetailModal(data);
        } catch (_) {
            if (detailDesc) {
                detailDesc.textContent = '상세 정보를 불러오지 못했습니다.';
                detailDesc.classList.add('is-empty');
            }
        } finally {
            detailLoading = false;
        }
    }

    async function saveModalDescription(clear) {
        if (descriptionSaving || !descriptionUrl || descriptionEditor.hidden) return;
        if (clear && !window.confirm('이 오브젝트의 상세 설명을 삭제하시겠습니까?')) return;
        if (!clear && !descriptionForm.reportValidity()) return;
        const key = activeObjektKey;
        descriptionSaving = true;
        const buttons = descriptionForm.querySelectorAll('button');
        buttons.forEach(button => button.disabled = true);
        descriptionStatus.textContent = clear ? '삭제 중…' : '저장 중…';
        try {
            const response = await fetch(descriptionUrl, {
                method: 'POST', headers: csrfHeaders(), credentials: 'same-origin',
                body: new URLSearchParams({description: clear ? '' : descriptionInput.value})
            });
            if (!response.ok) throw new Error();
            const data = await response.json();
            if (typeof data.description !== 'string') throw new Error();
            if (activeObjektKey !== key) return;
            descriptionInput.value = data.description;
            fillDetailStory({description: data.description, apollo: lastApollo});
            descriptionStatus.textContent = clear ? '상세 설명을 삭제했습니다.' : '상세 설명을 저장했습니다.';
        } catch {
            if (activeObjektKey === key) descriptionStatus.textContent = '저장하지 못했습니다. 관리자 로그인 상태를 확인한 뒤 다시 시도해 주세요.';
        } finally {
            descriptionSaving = false; buttons.forEach(button => button.disabled = false);
        }
    }
    descriptionForm?.addEventListener('submit', event => { event.preventDefault(); saveModalDescription(false); });
    descriptionDelete?.addEventListener('click', () => saveModalDescription(true));

    function shouldOpenModalFromEvent(event) {
        if (event.defaultPrevented) return false;
        if (event.button != null && event.button !== 0) return false;
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return false;
        return true;
    }

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
        openObjektDetail(card.dataset.objektKey);
    });

    detailClose?.addEventListener('click', closeDetailModal);
    detailModal?.addEventListener('click', (event) => {
        if (event.target === detailModal) closeDetailModal();
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
            setFavoriteButton(Boolean(data.favorite ?? data.isFavorite));
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
        if (loadedEl) loadedEl.textContent = String(loadedCount);
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
                 data-number="${attr(item.numberValue || '')}" data-date="${attr(item.createdAt || '')}" data-member="${attr(member)}" data-search="${attr(item.searchText || '')}">
            <button type="button" class="objekt-open-detail" aria-label="${attr(collectionId)} 상세 보기">
                <img src="${attr(thumb)}" alt="${attr(collectionId)}" loading="lazy" decoding="async" class="objekt-image" onerror="this.onerror=null;this.src='${attr(front)}';">
            </button>
            <div class="card-body objekt-card-body">
                <div class="objekt-card-badges">
                    <span class="badge badge-sm objekt-card-season" style="background:${attr(seasonBadgeColor(item, season))}">${escapeHtml(season)}</span>
                    <a href="/cosmo/objekt/detail/${attr(id)}" class="objekt-detail-link" title="${attr(collectionId)}">${escapeHtml(title)}</a>
                </div>
                <div class="objekt-card-foot">
                    <a href="/cosmo/objekt/member?name=${encodeURIComponent(member)}" class="objekt-member-chip truncate" style="--artist-color:${attr(item.memberColor || '#00f5d4')}">${escapeHtml(member)}</a>
                    ${klass ? `<span class="badge badge-sm badge-neutral objekt-card-class">${escapeHtml(klass)}</span>` : ''}
                </div>
                ${Number.isFinite(item.mintCount) ? `<p class="objekt-card-supply">발행량 ${escapeHtml(String(item.mintCount))}개</p>` : ''}
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
        if (loadedEl) loadedEl.textContent = String(loadedCount);
        const allEl = document.getElementById('objekt-all-count');
        if (allEl) allEl.textContent = String(allCount);
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
})();
