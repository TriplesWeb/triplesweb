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
    const status = document.getElementById('objektPickerStatus');
    const memberFilter = document.getElementById('objektFilterMember');
    const seasonFilter = document.getElementById('objektFilterSeason');
    const classFilter = document.getElementById('objektFilterClass');
    const searchFilter = document.getElementById('objektFilterSearch');
    const resetBtn = document.getElementById('objektFilterReset');
    const sideHaveBtn = document.getElementById('objektSideHave');
    const sideWantBtn = document.getElementById('objektSideWant');
    const havePreview = document.getElementById('objektHavePreview');
    const wantPreview = document.getElementById('objektWantPreview');
    const api = section.dataset.objektApi;
    const pageSize = 24;
    const maxLinked = 20;
    let open = false;
    let offset = 0;
    let total = 0;
    let loading = false;
    let requestId = 0;
    let searchTimer = 0;
    let activeSide = 'have';
    const itemCache = new Map();

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

    function syncWantAvailability() {
      if (!tradeSplit) return;
      const unlocked = haveCount() > 0;
      if (sideWantBtn) {
        sideWantBtn.disabled = !unlocked;
        if (!unlocked && activeSide === 'want') setActiveSide('have');
      }
      // Want는 Have가 비어도 유지한다. Want부터 개별 삭제 가능.
    }

    function setActiveSide(side) {
      if (!tradeSplit) return;
      if (side === 'want' && haveCount() === 0) return;
      activeSide = side === 'want' ? 'want' : 'have';
      if (sideHaveBtn) {
        const on = activeSide === 'have';
        sideHaveBtn.classList.toggle('is-active', on);
        sideHaveBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
      }
      if (sideWantBtn) {
        const on = activeSide === 'want';
        sideWantBtn.classList.toggle('is-active', on);
        sideWantBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
      }
      refreshOptionChecks();
    }

    function updateSummary() {
      const count = totalCount();
      if (summary) {
        if (tradeSplit) {
          summary.textContent = 'Have ' + haveCount() + '개 · Want ' + wantCount() + '개 (합계 ' + count + '/' + maxLinked + ')';
        } else {
          summary.textContent = count > 0 ? '선택된 오브젝트 ' + count + '개' : '선택된 오브젝트 0개';
        }
      }
      if (toggle) {
        toggle.textContent = open
          ? '목록 닫기'
          : (count > 0 ? '오브젝트 선택 (' + count + ')' : '오브젝트 선택');
      }
      syncWantAvailability();
    }

    function memberSelection() {
      const value = memberFilter ? memberFilter.value.trim() : '';
      if (value === '__other__') return { group: 'other', member: '' };
      if (value) return { group: 'members', member: value };
      return { group: 'all', member: '' };
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
            alert('같은 오브젝트를 Have와 Want에 동시에 넣을 수 없습니다.');
            return false;
          }
          return true;
        }
        if (tradeSplit && targetSide === 'want' && haveCount() === 0) {
          alert('Have 오브젝트를 먼저 선택해주세요.');
          return false;
        }
        if (totalCount() >= maxLinked) {
          alert('오브젝트는 최대 ' + maxLinked + '개까지 선택할 수 있습니다.');
          return false;
        }
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

    function chip(item, side) {
      const el = document.createElement('div');
      el.className = 'objekt-trade-chip';
      el.dataset.objektId = item.id;
      el.dataset.side = side || 'have';
      if (item.thumbnailImage) {
        const img = document.createElement('img');
        img.src = item.thumbnailImage;
        img.alt = '';
        img.loading = 'lazy';
        el.appendChild(img);
      }
      const span = document.createElement('span');
      span.textContent = item.collectionId || item.id || '';
      el.appendChild(span);
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'objekt-trade-chip-remove';
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

    function renderPreviews() {
      if (!tradeSplit) return;
      if (havePreview) {
        havePreview.replaceChildren();
        selectedNodes('have').forEach((input) => {
          const meta = itemCache.get(input.value) || { id: input.value, collectionId: input.value };
          havePreview.appendChild(chip(meta, 'have'));
        });
      }
      if (wantPreview) {
        wantPreview.replaceChildren();
        const wants = selectedNodes('want');
        if (wants.length === 0) {
          const hint = document.createElement('p');
          hint.className = 'objekt-trade-zone-empty';
          hint.id = 'objektWantEmptyHint';
          hint.textContent = haveCount() > 0
            ? 'Want 오브젝트를 선택하세요'
            : 'Have를 고른 뒤 Want를 선택하세요';
          wantPreview.appendChild(hint);
        } else {
          wants.forEach((input) => {
            const meta = itemCache.get(input.value) || { id: input.value, collectionId: input.value };
            wantPreview.appendChild(chip(meta, 'want'));
          });
        }
      }
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
            alert('같은 오브젝트를 Have와 Want에 동시에 넣을 수 없습니다.');
            return;
          }
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
      title.className = 'block truncate text-xs font-semibold';
      title.textContent = item.collectionId || item.id || '';
      const meta = document.createElement('span');
      meta.className = 'block truncate text-[11px] text-slate-400';
      meta.textContent = [item.member, item.collectionNo].filter(Boolean).join(' · ');
      label.appendChild(title);
      label.appendChild(meta);
      itemCache.set(String(item.id), item);
      return label;
    }

    function refreshOptionChecks() {
      if (!list) return;
      const chosen = selectedIds();
      Array.from(list.children).forEach((label) => {
        const box = label.querySelector('input.objekt-checkbox');
        if (!box) return;
        const id = box.dataset.objektId;
        const checked = !!(id && chosen.has(String(id)));
        box.checked = checked;
        label.classList.toggle('border-teal-300/60', checked);
        label.classList.toggle('bg-teal-400/10', checked);
        label.classList.toggle('border-white/10', !checked);
      });
    }

    function statusText() {
      const shown = list ? list.children.length : 0;
      if (tradeSplit) {
        return '표시 ' + shown + ' / 필터 ' + total + '개 · Have ' + haveCount() + ' · Want ' + wantCount() + ' / ' + maxLinked;
      }
      return '표시 ' + shown + ' / 필터 ' + total + '개 · 선택 ' + totalCount() + ' / ' + maxLinked + '개';
    }

    async function load(reset) {
      if (!api || !list) return;
      if (loading && !reset) return;
      if (reset) {
        offset = 0;
        total = 0;
        list.replaceChildren();
      }
      loading = true;
      const current = ++requestId;
      if (status) status.textContent = '오브젝트를 불러오는 중...';
      const selection = memberSelection();
      const params = new URLSearchParams();
      params.set('group', selection.group);
      if (selection.member) params.set('member', selection.member);
      if (seasonFilter && seasonFilter.value) params.set('season', seasonFilter.value);
      if (classFilter && classFilter.value) params.set('class', classFilter.value);
      if (searchFilter && searchFilter.value.trim()) params.set('q', searchFilter.value.trim());
      params.set('offset', String(offset));
      params.set('limit', String(pageSize));
      try {
        const response = await fetch(api + '?' + params.toString(), { credentials: 'same-origin' });
        if (!response.ok) throw new Error('load failed');
        const data = await response.json();
        if (current !== requestId) return;
        total = Number(data.total) || 0;
        const chosen = selectedIds();
        (data.items || []).forEach((item) => {
          if (!item || !item.id) return;
          itemCache.set(String(item.id), item);
          const cardEl = card(item, chosen.has(String(item.id)));
          // Keep objekt id on checkbox for later sync.
          const box = cardEl.querySelector('input.objekt-checkbox');
          if (box) box.dataset.objektId = String(item.id);
          list.appendChild(cardEl);
        });
        offset += (data.items || []).length;
        if (loadMore) loadMore.hidden = offset >= total;
        if (status) status.textContent = statusText();
        renderPreviews();
      } catch (_) {
        if (current !== requestId) return;
        if (status) status.textContent = '오브젝트를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
      } finally {
        if (current === requestId) loading = false;
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
    if (loadMore) loadMore.addEventListener('click', () => load(false));
    if (sideHaveBtn) sideHaveBtn.addEventListener('click', () => setActiveSide('have'));
    if (sideWantBtn) sideWantBtn.addEventListener('click', () => setActiveSide('want'));
    [memberFilter, seasonFilter, classFilter].forEach((el) => {
      if (el) el.addEventListener('change', () => { if (open) load(true); });
    });
    if (searchFilter) {
      searchFilter.addEventListener('input', () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => { if (open) load(true); }, 250);
      });
      searchFilter.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          window.clearTimeout(searchTimer);
          if (open) load(true);
        }
      });
    }
    const searchBtn = document.getElementById('objektFilterSearchBtn');
    if (searchBtn) {
      searchBtn.addEventListener('click', () => {
        window.clearTimeout(searchTimer);
        if (open) load(true);
        else setOpen(true);
      });
    }
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        if (memberFilter) memberFilter.value = '';
        if (seasonFilter) seasonFilter.value = '';
        if (classFilter) classFilter.value = '';
        if (searchFilter) searchFilter.value = '';
        if (open) load(true);
      });
    }

    // Seed cache from server-rendered chips / hidden inputs.
    selectedNodes().forEach((input) => {
      if (!itemCache.has(input.value)) {
        itemCache.set(input.value, { id: input.value, collectionId: input.value });
      }
    });
    if (tradeSplit) {
      document.querySelectorAll('#objektHavePreview .objekt-trade-chip, #objektWantPreview .objekt-trade-chip').forEach((chipEl) => {
        const id = chipEl.dataset.objektId;
        if (!id) return;
        const img = chipEl.querySelector('img');
        const span = chipEl.querySelector('span');
        itemCache.set(id, {
          id: id,
          collectionId: span ? span.textContent : id,
          thumbnailImage: img ? img.src : ''
        });
      });
    }

    window.ArticleObjektPicker = {
      validateTrade: function () {
        if (!tradeSplit) return true;
        if (haveCount() < 1) {
          alert('Have 오브젝트를 1개 이상 선택해주세요.');
          return false;
        }
        if (wantCount() < 1) {
          alert('Want 오브젝트를 1개 이상 선택해주세요.');
          return false;
        }
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
    setOpen(totalCount() > 0);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initArticleObjektPicker);
  } else {
    initArticleObjektPicker();
  }
})();
