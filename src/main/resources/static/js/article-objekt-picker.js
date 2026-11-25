(function () {
  function initArticleObjektPicker() {
    const section = document.getElementById('objektPickerSection');
    if (!section) return;

    const tradeSplit = section.dataset.tradeSplit === 'true';
    const panel = document.getElementById('objektPickerPanel');
    const toggle = document.getElementById('objektPickerToggle');
    const summary = document.getElementById('objektSelectedSummary');
    const list = document.getElementById('objektOptionsList');
    const selectedInputs = document.getElementById('objektSelectedInputs');
    const loadMore = document.getElementById('loadMoreObjekts');
    const scrollRoot = document.getElementById('objektOptionsScroll') || list;
    const sentinel = document.getElementById('objektOptionsSentinel');
    const status = document.getElementById('objektPickerStatus');
    const workspace = document.getElementById('objekt-workspace');
    const memberGroupInput = document.getElementById('objekt-member-group');
    const memberLabel = document.getElementById('member-label');
    const memberPicker = document.getElementById('objekt-member-picker');
    const memberPanel = document.getElementById('member-dropdown');
    const kindInput = document.getElementById('objekt-kind-input');
    const sortSelect = workspace?.querySelector('.objekt-sort-select');
    const searchFilter = document.getElementById('objekt-workspace-search');
    const resetBtn = document.getElementById('objekt-reset');
    const memberToggle = document.getElementById('objekt-member-toggle');
    const sideHaveBtn = document.getElementById('objektSideHave');
    const sideWantBtn = document.getElementById('objektSideWant');
    const havePreview = document.getElementById('objektHavePreview');
    const wantPreview = document.getElementById('objektWantPreview');
    const sellPreview = document.getElementById('objektSellPreview');
    const sellSideInput = document.getElementById('sellListingSide');
    const sellHaveBtn = document.getElementById('sellListingHaveBtn');
    const sellWantBtn = document.getElementById('sellListingWantBtn');
    const sellSideLabel = document.getElementById('objektSellSideLabel');
    const pickerError = document.getElementById('objektPickerError');
    const api = section.dataset.objektApi;
    const pageSize = 24;
    const defaultOpen = section.dataset.defaultOpen !== 'false';
    let open = false;

    function showPickerError(message) {
      if (pickerError) {
        pickerError.textContent = message || '';
        pickerError.hidden = !message;
      } else if (message) {
        window.SiteFeedback.alert(message);
      }
      const formError = document.getElementById('formInlineError');
      if (formError && message) {
        formError.textContent = message;
        formError.hidden = false;
      }
    }
    let offset = 0;
    let total = 0;
    let loading = false;
    let requestId = 0;
    let searchTimer = 0;
    let activeSide = 'have';
    const itemCache = new Map();

    function displayTitle(item) {
      return (item && (item.cardTitle || item.collectionId || item.id)) || '';
    }

    function inputName(side) {
      if (!tradeSplit) return 'selectedObjektIds';
      return side === 'want' ? 'selectedWantObjektIds' : 'selectedHaveObjektIds';
    }

    function selectedNodes(side) {
      if (!selectedInputs) return [];
      if (!tradeSplit) {
        return Array.from(selectedInputs.querySelectorAll('input[name="selectedObjektIds"]'));
      }
      if (side) {
        return Array.from(selectedInputs.querySelectorAll('input[data-side="' + side + '"]'));
      }
      return Array.from(selectedInputs.querySelectorAll('input[data-side]'));
    }

    function selectedIds(side) {
      return new Set(selectedNodes(side).map((input) => input.value));
    }

    function haveCount() {
      return tradeSplit ? selectedNodes('have').length : selectedNodes().length;
    }

    function wantCount() {
      return tradeSplit ? selectedNodes('want').length : 0;
    }

    function totalCount() {
      return selectedNodes().length;
    }

    function setActiveSide(side) {
      if (!tradeSplit) return;
      activeSide = side === 'want' ? 'want' : 'have';
      if (sideHaveBtn) {
        const on = activeSide === 'have';
        sideHaveBtn.classList.toggle('is-active', on);
        sideHaveBtn.setAttribute('aria-selected', on ? 'true' : 'false');
      }
      if (sideWantBtn) {
        const on = activeSide === 'want';
        sideWantBtn.classList.toggle('is-active', on);
        sideWantBtn.setAttribute('aria-selected', on ? 'true' : 'false');
      }
      refreshOptionChecks();
    }

    function updateSummary() {
      const count = totalCount();
      if (summary) {
        if (tradeSplit) {
          summary.textContent = 'Have ' + haveCount() + '개 · Want ' + wantCount() + '개 (합계 ' + count + '개)';
        } else {
          summary.textContent = count > 0 ? '선택된 오브젝트 ' + count + '개' : '선택된 오브젝트 0개';
        }
      }
      if (toggle) {
        toggle.textContent = open
          ? '접기'
          : (count > 0 ? '오브젝트 선택 (' + count + ')' : '오브젝트 선택');
      }
    }

    function checkedValues(selector) {
      return Array.from(document.querySelectorAll(selector))
        .filter((box) => box.checked && box.value && box.value !== 'all')
        .map((box) => box.value);
    }

    function normalizeKindValue(raw) {
      const value = String(raw || '').trim();
      if (!value || value === '전체' || value.toLowerCase() === 'all') return 'all';
      return value;
    }

    function filterState() {
      const group = memberGroupInput?.value || 'all';
      const members = checkedValues('.member-checkbox');
      return {
        group: group === 'other' ? 'other' : (members.length ? 'members' : group),
        members: members,
        season: checkedValues('.season-checkbox')[0] || '',
        className: checkedValues('.class-checkbox')[0] || '',
        kind: normalizeKindValue(kindInput?.dataset.kindValue || kindInput?.value),
        sort: (sortSelect && sortSelect.value) || 'latest',
        q: (searchFilter && searchFilter.value.trim()) || ''
      };
    }

    function syncMemberLabel() {
      if (!memberLabel || !memberGroupInput) return;
      const chosen = checkedValues('.member-checkbox');
      if (!chosen.length) {
        memberLabel.textContent = memberGroupInput.value === 'other'
          ? '기타 옵젝'
          : (memberGroupInput.value === 'members' ? '전체 멤버' : '전체 오브젝트');
        return;
      }
      memberLabel.textContent = chosen.length === 1 ? chosen[0] : (chosen[0] + ' 외 ' + (chosen.length - 1) + '명');
    }

    function syncMemberGroupUi() {
      const group = memberGroupInput?.value || 'all';
      document.querySelectorAll('[data-objekt-group]').forEach((button) => {
        button.setAttribute('aria-pressed', String(button.dataset.objektGroup === group));
      });
      if (memberPicker) memberPicker.hidden = group === 'other';
      const notice = document.getElementById('objekt-other-notice');
      if (notice) notice.hidden = group !== 'other';
      if (memberToggle) memberToggle.hidden = group === 'other';
      syncMemberLabel();
    }

    function setMemberPickerOpen(next) {
      if (!memberPanel) return;
      memberPanel.classList.toggle('is-collapsed', !next);
      if (memberToggle) memberToggle.setAttribute('aria-expanded', next ? 'true' : 'false');
    }

    function updateFacetLabel(type) {
      const label = document.getElementById(type + '-label');
      if (!label) return;
      const chosen = checkedValues('.' + type + '-checkbox');
      label.textContent = chosen.length ? chosen[0] : '전체';
    }

    function syncFilterChrome() {
      const state = filterState();
      const hasMember = state.group !== 'all' || state.members.length > 0;
      const hasSeason = !!state.season;
      const hasClass = !!state.className;
      const hasKind = state.kind !== 'all';
      const hasSort = state.sort !== 'latest';
      const hasSearch = !!state.q;
      if (resetBtn) {
        const showReset = hasMember || hasSeason || hasClass || hasKind || hasSort || hasSearch;
        resetBtn.hidden = !showReset;
        resetBtn.toggleAttribute('hidden', !showReset);
      }
      memberPanel?.classList.toggle('is-filtered', hasMember);
      document.getElementById('season-dropdown')?.classList.toggle('is-filtered', hasSeason);
      document.getElementById('class-dropdown')?.classList.toggle('is-filtered', hasClass);
      document.getElementById('kind-dropdown')?.classList.toggle('is-filtered', hasKind);
      document.getElementById('sort-dropdown')?.classList.toggle('is-filtered', hasSort);
      workspace?.querySelector('.objekt-search')?.classList.toggle('is-filtered', hasSearch);
    }

    function bindExclusiveFacet(type) {
      const boxes = Array.from(document.querySelectorAll('.' + type + '-checkbox'));
      const all = boxes.find((box) => box.value === 'all');
      const toggleBtn = document.getElementById(type + '-toggle');
      const menu = document.getElementById(type + '-menu');
      boxes.forEach((box) => {
        box.addEventListener('change', () => {
          if (box.value === 'all' && box.checked) {
            boxes.forEach((other) => { if (other !== box) other.checked = false; });
          } else if (box.value !== 'all' && box.checked) {
            boxes.forEach((other) => { if (other !== box) other.checked = false; });
            if (all) all.checked = false;
          }
          if (!boxes.some((other) => other.value !== 'all' && other.checked) && all) all.checked = true;
          updateFacetLabel(type);
          syncFilterChrome();
          if (open) load(true);
        });
      });
      toggleBtn?.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        if (window.ObjektDropdown) window.ObjektDropdown.toggle(menu);
        else if (menu) {
          const willOpen = menu.classList.contains('hidden') || menu.hidden;
          menu.hidden = !willOpen;
          menu.classList.toggle('hidden', !willOpen);
        }
      });
      updateFacetLabel(type);
      return {
        reset() {
          boxes.forEach((box) => { box.checked = box.value === 'all'; });
          updateFacetLabel(type);
        }
      };
    }

    function findInput(id) {
      if (!selectedInputs || !id) return null;
      return selectedInputs.querySelector('input[data-objekt-id="' + CSS.escape(id) + '"]');
    }

    function setSelected(id, on, side, meta) {
      if (!selectedInputs || !id) return;
      const existing = findInput(id);
      const targetSide = tradeSplit ? (side || activeSide) : null;
      if (on) {
        if (existing) {
          if (tradeSplit && existing.dataset.side !== targetSide) {
            showPickerError('같은 오브젝트를 Have와 Want에 동시에 넣을 수 없습니다.');
            return false;
          }
          return true;
        }
        showPickerError('');
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = inputName(targetSide);
        input.value = id;
        input.dataset.objektId = id;
        if (tradeSplit) input.dataset.side = targetSide;
        selectedInputs.appendChild(input);
        if (meta) itemCache.set(String(id), meta);
      } else if (existing) {
        existing.remove();
      }
      renderPreviews();
      return true;
    }

    function tipText(item) {
      return [item.season, item.memberDisplay || item.member || item.memberName, item.collectionNo || displayTitle(item)]
        .filter(Boolean)
        .join(' · ');
    }

    function chip(item, side) {
      const el = document.createElement('span');
      const tip = tipText(item);
      el.className = 'market-feed-thumb' + (item.thumbnailImage ? '' : ' is-empty');
      el.dataset.objektId = item.id;
      el.dataset.side = side || 'have';
      if (item.season) el.dataset.season = item.season;
      if (tip) {
        el.dataset.tip = tip;
        el.title = tip;
      }
      if (item.thumbnailImage) {
        const img = document.createElement('img');
        img.src = item.thumbnailImage;
        img.alt = '';
        img.loading = 'lazy';
        el.appendChild(img);
      } else {
        const fallback = document.createElement('span');
        fallback.textContent = item.collectionNo || displayTitle(item) || '?';
        el.appendChild(fallback);
      }
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'objekt-compose-remove';
      remove.setAttribute('aria-label', '선택 해제');
      remove.textContent = '×';
      remove.addEventListener('mousedown', (event) => {
        event.preventDefault();
        event.stopPropagation();
      });
      remove.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        setSelected(item.id, false, side || 'have', item);
        updateSummary();
        refreshOptionChecks();
        if (status) status.textContent = statusText();
      });
      el.appendChild(remove);
      return el;
    }

    function emptyHint(id, text) {
      const hint = document.createElement('p');
      hint.className = 'objekt-trade-zone-empty';
      if (id) hint.id = id;
      hint.textContent = text;
      return hint;
    }

    function renderPreviews() {
      if (tradeSplit) {
        if (havePreview) {
          havePreview.replaceChildren();
          const haves = selectedNodes('have');
          if (haves.length === 0) {
            havePreview.appendChild(emptyHint('objektHaveEmptyHint', 'Have 오브젝트를 선택하세요'));
          } else {
            haves.forEach((input) => {
              const meta = itemCache.get(input.value) || { id: input.value, collectionId: input.value };
              havePreview.appendChild(chip(meta, 'have'));
            });
          }
        }
        if (wantPreview) {
          wantPreview.replaceChildren();
          const wants = selectedNodes('want');
          if (wants.length === 0) {
            wantPreview.appendChild(emptyHint(
              'objektWantEmptyHint',
              'Want 오브젝트를 선택하세요'
            ));
          } else {
            wants.forEach((input) => {
              const meta = itemCache.get(input.value) || { id: input.value, collectionId: input.value };
              wantPreview.appendChild(chip(meta, 'want'));
            });
          }
        }
        return;
      }
      if (!sellPreview) return;
      sellPreview.replaceChildren();
      const selected = selectedNodes();
      if (selected.length === 0) {
        sellPreview.appendChild(emptyHint('objektSellEmptyHint', '오브젝트를 선택하세요'));
        return;
      }
      selected.forEach((input) => {
        const meta = itemCache.get(input.value) || { id: input.value, collectionId: input.value };
        sellPreview.appendChild(chip(meta, 'sell'));
      });
    }

    function sideOf(id) {
      const input = findInput(id);
      if (!input) return null;
      return tradeSplit ? (input.dataset.side || 'have') : 'have';
    }

    function card(item, checked) {
      const label = document.createElement('label');
      label.className = 'objekt-option cursor-pointer rounded-lg border p-2 hover:border-teal-300/50 '
        + (checked ? 'border-teal-300/60 bg-teal-400/10' : 'border-white/10');
      const box = document.createElement('input');
      box.type = 'checkbox';
      box.className = 'objekt-checkbox mb-2';
      box.checked = checked;
      box.addEventListener('change', () => {
        const existingSide = sideOf(item.id);
        if (box.checked) {
          if (tradeSplit && existingSide && existingSide !== activeSide) {
            box.checked = false;
            showPickerError('같은 오브젝트를 Have와 Want에 동시에 넣을 수 없습니다.');
            return;
          }
          showPickerError('');
          const ok = setSelected(item.id, true, activeSide, item);
          if (ok === false) box.checked = false;
        } else {
          setSelected(item.id, false, existingSide || activeSide, item);
        }
        label.classList.toggle('border-teal-300/60', box.checked);
        label.classList.toggle('bg-teal-400/10', box.checked);
        label.classList.toggle('border-white/10', !box.checked);
        updateSummary();
        if (status) status.textContent = statusText();
      });
      label.appendChild(box);
      if (item.thumbnailImage) {
        const img = document.createElement('img');
        img.src = item.thumbnailImage;
        img.alt = item.collectionId || '';
        img.loading = 'lazy';
        img.className = 'mb-2 aspect-[2/3] w-full rounded object-cover';
        label.appendChild(img);
      }
      const title = document.createElement('span');
      title.className = 'block text-xs font-semibold';
      title.style.overflowWrap = 'anywhere';
      title.textContent = (item.season ? item.season + ' · ' : '') + displayTitle(item);
      const meta = document.createElement('span');
      meta.className = 'block truncate text-[11px] text-slate-400';
      meta.textContent = [item.memberDisplay || item.member, item.collectionNo].filter(Boolean).join(' · ');
      label.appendChild(title);
      label.appendChild(meta);
      itemCache.set(String(item.id), item);
      return label;
    }

    function refreshOptionChecks() {
      if (!list) return;
      const chosen = selectedIds(tradeSplit ? activeSide : null);
      const otherSide = tradeSplit
        ? selectedIds(activeSide === 'want' ? 'have' : 'want')
        : new Set();
      Array.from(list.children).forEach((label) => {
        const box = label.querySelector('input.objekt-checkbox');
        if (!box) return;
        const id = box.dataset.objektId;
        const onOther = !!(id && otherSide.has(String(id)));
        const checked = !!(id && chosen.has(String(id)));
        box.checked = checked;
        box.disabled = onOther;
        label.classList.toggle('border-teal-300/60', checked);
        label.classList.toggle('bg-teal-400/10', checked);
        label.classList.toggle('border-white/10', !checked && !onOther);
        label.classList.toggle('opacity-40', onOther);
        label.classList.toggle('cursor-not-allowed', onOther);
        label.title = onOther
          ? (activeSide === 'want' ? 'Have에 담긴 오브젝트입니다' : 'Want에 담긴 오브젝트입니다')
          : '';
      });
    }

    function statusText() {
      const shown = list ? list.children.length : 0;
      if (tradeSplit) {
        return '표시 ' + shown + ' / 필터 ' + total + '개 · Have ' + haveCount() + ' · Want ' + wantCount();
      }
      return '표시 ' + shown + ' / 필터 ' + total + '개 · 선택 ' + totalCount() + '개';
    }

    function hasMore() {
      return offset < total;
    }

    function maybeLoadMore() {
      if (!open || loading || !hasMore()) return;
      load(false);
    }

    async function load(reset) {
      if (!api || !list) return;
      if (loading && !reset) return;
      if (!reset && !hasMore() && total > 0) return;
      if (reset) {
        offset = 0;
        total = 0;
        list.replaceChildren();
      }
      loading = true;
      const current = ++requestId;
      if (status) status.textContent = '오브젝트를 불러오는 중...';
      const state = filterState();
      const params = new URLSearchParams();
      params.set('group', state.group);
      if (state.members.length) params.set('member', state.members.join(','));
      if (state.season) params.set('season', state.season);
      if (state.className) params.set('class', state.className);
      if (state.kind && state.kind !== 'all') params.set('kind', state.kind);
      params.set('sort', state.sort || 'latest');
      if (state.q) params.set('q', state.q);
      params.set('offset', String(offset));
      params.set('limit', String(pageSize));
      try {
        const response = await fetch(api + '?' + params.toString(), { credentials: 'same-origin' });
        if (!response.ok) throw new Error('load failed');
        const data = await response.json();
        if (current !== requestId) return;
        total = Number(data.total) || 0;
        (data.items || []).forEach((item) => {
          if (!item || !item.id) return;
          itemCache.set(String(item.id), item);
          const cardEl = card(item, false);
          // Keep objekt id on checkbox for later sync.
          const box = cardEl.querySelector('input.objekt-checkbox');
          if (box) box.dataset.objektId = String(item.id);
          list.appendChild(cardEl);
        });
        refreshOptionChecks();
        const got = (data.items || []).length;
        offset += got;
        if (got === 0) total = offset;
        if (loadMore) loadMore.hidden = true;
        if (status) {
          status.textContent = hasMore()
            ? statusText() + ' · 스크롤하면 더 불러옵니다'
            : statusText();
        }
        renderPreviews();
      } catch (_) {
        if (current !== requestId) return;
        if (status) status.textContent = '오브젝트를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
      } finally {
        if (current === requestId) loading = false;
        // 첫 페이지가 짧아 센티널이 이미 보이면 이어서 더 불러온다.
        if (current === requestId && open && hasMore()) {
          requestAnimationFrame(maybeLoadMore);
        }
      }
    }

    function setOpen(nextOpen) {
      open = nextOpen;
      if (panel) {
        panel.hidden = !open;
        panel.classList.toggle('hidden', !open);
      }
      if (toggle) toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      updateSummary();
      if (open && list && list.children.length === 0) load(true);
    }

    if (toggle) toggle.addEventListener('click', () => setOpen(!open));
    if (loadMore) {
      loadMore.hidden = true;
      loadMore.addEventListener('click', () => load(false));
    }
    if (scrollRoot) {
      scrollRoot.addEventListener('scroll', () => {
        if (scrollRoot.scrollTop + scrollRoot.clientHeight >= scrollRoot.scrollHeight - 64) {
          maybeLoadMore();
        }
      }, { passive: true });
    }
    if (sentinel && scrollRoot && typeof IntersectionObserver === 'function') {
      const observer = new IntersectionObserver((entries) => {
        if (entries.some((entry) => entry.isIntersecting)) maybeLoadMore();
      }, { root: scrollRoot, rootMargin: '120px 0px', threshold: 0 });
      observer.observe(sentinel);
    }
    if (sideHaveBtn) sideHaveBtn.addEventListener('click', () => setActiveSide('have'));
    if (sideWantBtn) sideWantBtn.addEventListener('click', () => setActiveSide('want'));
    function closePickerMenus(except) {
      section.querySelectorAll('.objekt-dropdown-menu').forEach((menu) => {
        if (menu !== except) {
          menu.hidden = true;
          menu.classList.add('hidden');
        }
      });
      section.querySelectorAll('.objekt-filter').forEach((filter) => {
        if (!except || !filter.contains(except)) filter.classList.remove('is-open');
      });
      except?.closest('.objekt-filter')?.classList.toggle('is-open', Boolean(except) && !except.hidden);
    }

    const seasonMenu = bindExclusiveFacet('season');
    const classMenu = bindExclusiveFacet('class');

    function syncMemberChecks(values, apply) {
      const selected = new Set((values || []).map((v) => String(v).toLocaleLowerCase()));
      document.querySelectorAll('.member-checkbox').forEach((cb) => {
        cb.checked = selected.has(String(cb.value).toLocaleLowerCase());
      });
      if (apply && memberGroupInput) memberGroupInput.value = selected.size ? 'members' : 'all';
      syncMemberGroupUi();
      syncFilterChrome();
      if (apply && open) load(true);
    }

    if (memberPicker && window.MemberPortraitPicker) {
      window.MemberPortraitPicker.bindMultiSelect(memberPicker, {
        multi: true,
        onChange(values) {
          syncMemberChecks(values, true);
        }
      });
    }
    syncMemberGroupUi();
    setMemberPickerOpen(false);

    document.querySelectorAll('[data-objekt-group]').forEach((button) => {
      button.addEventListener('click', () => {
        const group = button.dataset.objektGroup;
        if (memberGroupInput) memberGroupInput.value = group;
        if (group !== 'members') {
          window.MemberPortraitPicker?.clear(memberPicker);
          syncMemberChecks([]);
        }
        syncMemberGroupUi();
        setMemberPickerOpen(group === 'members');
        syncFilterChrome();
        if (open) load(true);
      });
    });
    memberToggle?.addEventListener('click', () => {
      if (memberGroupInput?.value === 'other') {
        memberGroupInput.value = 'members';
        syncMemberGroupUi();
        setMemberPickerOpen(true);
        syncFilterChrome();
        if (open) load(true);
        return;
      }
      setMemberPickerOpen(!!memberPanel?.classList.contains('is-collapsed'));
    });

    window.ObjektKindCombo?.bindKindCombo({
      input: kindInput,
      menu: document.getElementById('kind-menu'),
      toggle: document.getElementById('objekt-kind-toggle'),
      hiddenInput: null,
      empty: document.getElementById('kind-empty'),
      closeOthers() {
        closePickerMenus(document.getElementById('kind-menu'));
      },
      onChange() {
        if (kindInput) kindInput.dataset.kindValue = normalizeKindValue(kindInput.value);
        syncFilterChrome();
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => { if (open) load(true); }, 250);
      }
    });

    const sortToggle = document.getElementById('objekt-sort-toggle');
    const sortMenu = document.getElementById('sort-menu');
    const sortNative = document.getElementById('sort-native-control');
    if (sortToggle && sortMenu && sortSelect) {
      if (sortNative) sortNative.hidden = true;
      sortToggle.hidden = false;
      const sortLabel = document.getElementById('sort-label');
      function syncSortLabel() {
        const opt = sortSelect.selectedOptions?.[0];
        if (sortLabel) sortLabel.textContent = opt ? opt.textContent : '최신순';
      }
      syncSortLabel();
      sortToggle.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        const willOpen = sortMenu.classList.contains('hidden') || sortMenu.hidden;
        sortMenu.hidden = !willOpen;
        sortMenu.classList.toggle('hidden', !willOpen);
        sortToggle.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
      });
      sortMenu.querySelectorAll('.objekt-kind-option').forEach((option) => {
        option.addEventListener('click', () => {
          sortSelect.value = option.dataset.value || 'latest';
          syncSortLabel();
          sortMenu.hidden = true;
          sortMenu.classList.add('hidden');
          syncFilterChrome();
          if (open) load(true);
        });
      });
    }

    document.addEventListener('click', (event) => {
      if (event.target.closest('#objekt-workspace .objekt-filter')) return;
      ['season-menu', 'class-menu', 'kind-menu', 'sort-menu'].forEach((id) => {
        const menu = document.getElementById(id);
        if (menu && window.ObjektDropdown) window.ObjektDropdown.setOpen(menu, false);
        else if (menu) {
          menu.hidden = true;
          menu.classList.add('hidden');
        }
      });
      closePickerMenus();
    });

    if (searchFilter) {
      searchFilter.addEventListener('input', () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => {
          syncFilterChrome();
          if (open) load(true);
        }, 250);
      });
      searchFilter.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          window.clearTimeout(searchTimer);
          syncFilterChrome();
          if (open) load(true);
        }
      });
    }
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        seasonMenu.reset();
        classMenu.reset();
        if (memberGroupInput) memberGroupInput.value = 'all';
        window.MemberPortraitPicker?.clear(memberPicker);
        syncMemberChecks([]);
        if (kindInput) {
          kindInput.value = '전체';
          kindInput.dataset.kindValue = 'all';
        }
        if (sortSelect) sortSelect.value = 'latest';
        const sortLabel = document.getElementById('sort-label');
        if (sortLabel) sortLabel.textContent = '최신순';
        if (searchFilter) searchFilter.value = '';
        setMemberPickerOpen(false);
        syncMemberGroupUi();
        syncFilterChrome();
        if (open) load(true);
      });
    }
    syncFilterChrome();

    // Seed cache from server-rendered thumbs / hidden inputs.
    selectedNodes().forEach((input) => {
      if (!itemCache.has(input.value)) {
        itemCache.set(input.value, { id: input.value, collectionId: input.value });
      }
    });
    document.querySelectorAll('#objektHavePreview .market-feed-thumb, #objektWantPreview .market-feed-thumb, #objektSellPreview .market-feed-thumb').forEach((chipEl) => {
      const id = chipEl.dataset.objektId;
      if (!id) return;
      const img = chipEl.querySelector('img');
      const tip = chipEl.dataset.tip || chipEl.title || id;
      itemCache.set(id, {
        id: id,
        collectionId: tip,
        cardTitle: tip,
        season: chipEl.dataset.season || '',
        thumbnailImage: img ? img.src : ''
      });
    });

    function setSellSide(side) {
      if (tradeSplit || !sellSideInput) return;
      const next = side === 'want' ? 'want' : 'have';
      sellSideInput.value = next;
      if (sellHaveBtn) {
        const on = next === 'have';
        sellHaveBtn.classList.toggle('is-active', on);
        sellHaveBtn.setAttribute('aria-selected', on ? 'true' : 'false');
      }
      if (sellWantBtn) {
        const on = next === 'want';
        sellWantBtn.classList.toggle('is-active', on);
        sellWantBtn.setAttribute('aria-selected', on ? 'true' : 'false');
      }
      if (sellSideLabel) sellSideLabel.textContent = next === 'want' ? 'WANT' : 'HAVE';
      const title = section.querySelector('h3');
      if (title) title.textContent = next === 'want' ? '구매 오브젝트' : '판매 오브젝트';
    }
    if (sellHaveBtn) sellHaveBtn.addEventListener('click', () => setSellSide('have'));
    if (sellWantBtn) sellWantBtn.addEventListener('click', () => setSellSide('want'));

    window.ArticleObjektPicker = {
      validateTrade: function () {
        if (tradeSplit) {
          if (haveCount() < 1) {
            showPickerError('Have 오브젝트를 1개 이상 선택해주세요.');
            return false;
          }
          if (wantCount() < 1) {
            showPickerError('Want 오브젝트를 1개 이상 선택해주세요.');
            return false;
          }
          showPickerError('');
          return true;
        }
        if (section.closest('form')?.dataset.marketBoard === 'true' && totalCount() < 1) {
          showPickerError('오브젝트를 1개 이상 선택해주세요.');
          return false;
        }
        showPickerError('');
        return true;
      },
      fingerprint: function () {
        if (!tradeSplit) {
          return selectedNodes().map((input) => input.value).sort().join('\n');
        }
        const have = selectedNodes('have').map((input) => 'H:' + input.value).sort();
        const want = selectedNodes('want').map((input) => 'W:' + input.value).sort();
        return have.concat(want).join('\n');
      }
    };

    updateSummary();
    renderPreviews();
    if (searchFilter && totalCount() === 1 && section.dataset.initialSearch) {
      searchFilter.value = section.dataset.initialSearch;
    }
    setOpen(defaultOpen || totalCount() > 0);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initArticleObjektPicker);
  } else {
    initArticleObjektPicker();
  }
})();
