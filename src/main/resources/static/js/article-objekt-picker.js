(function () {
  function initArticleObjektPicker() {
    const section = document.getElementById('objektPickerSection');
    if (!section) return;

    const panel = document.getElementById('objektPickerPanel');
    const toggle = document.getElementById('objektPickerToggle');
    const summary = document.getElementById('objektSelectedSummary');
    const list = document.getElementById('objektOptionsList');
    const selectedInputs = document.getElementById('objektSelectedInputs');
    const loadMore = document.getElementById('loadMoreObjekts');
    const status = document.getElementById('objektPickerStatus');
    const memberFilter = document.getElementById('objektFilterMember');
    const memberRoot = document.getElementById('objekt-picker-member');
    const memberMeta = document.getElementById('objektPickerMemberMeta');
    const memberClear = document.getElementById('objektPickerMemberClear');
    const seasonFilter = document.getElementById('objektFilterSeason');
    const classFilter = document.getElementById('objektFilterClass');
    const searchFilter = document.getElementById('objektFilterSearch');
    const resetBtn = document.getElementById('objektFilterReset');
    const api = section.dataset.objektApi;
    const pageSize = 24;
    let open = false;
    let offset = 0;
    let total = 0;
    let loading = false;
    let requestId = 0;
    let searchTimer = 0;

    function selectedNodes() {
      return selectedInputs ? Array.from(selectedInputs.querySelectorAll('input[name="selectedObjektIds"]')) : [];
    }

    function selectedIds() {
      return new Set(selectedNodes().map((input) => input.value));
    }

    function updateSummary() {
      const count = selectedNodes().length;
      if (summary) summary.textContent = count > 0 ? `선택된 오브젝트 ${count}개` : '선택된 오브젝트 0개';
      if (toggle) toggle.textContent = open ? '목록 닫기' : (count > 0 ? `오브젝트 선택 (${count})` : '오브젝트 선택');
    }

    function syncMemberMeta() {
      const value = memberFilter ? memberFilter.value.trim() : '';
      if (memberMeta) memberMeta.textContent = value || '전체 멤버';
    }

    function setSelected(id, on) {
      if (!selectedInputs || !id) return;
      const existing = selectedInputs.querySelector(`input[data-objekt-id="${CSS.escape(id)}"]`);
      if (on) {
        if (existing) return;
        if (selectedNodes().length >= 20) {
          alert('오브젝트는 최대 20개까지 선택할 수 있습니다.');
          return false;
        }
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'selectedObjektIds';
        input.value = id;
        input.dataset.objektId = id;
        selectedInputs.appendChild(input);
      } else if (existing) {
        existing.remove();
      }
      return true;
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
        const ok = setSelected(item.id, box.checked);
        if (ok === false) box.checked = false;
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
      return label;
    }

    function statusText() {
      const shown = list ? list.children.length : 0;
      return `표시 ${shown} / 필터 ${total}개 · 선택 ${selectedNodes().length} / 20개`;
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
      const params = new URLSearchParams();
      if (memberFilter && memberFilter.value) params.set('member', memberFilter.value);
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
          list.appendChild(card(item, chosen.has(String(item.id))));
        });
        offset += (data.items || []).length;
        if (loadMore) loadMore.hidden = offset >= total;
        if (status) status.textContent = statusText();
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

    if (window.MemberPortraitPicker && memberRoot) {
      MemberPortraitPicker.bindMultiSelect(memberRoot, {
        multi: false,
        onChange: (values) => {
          if (memberFilter) memberFilter.value = values[0] || '';
          syncMemberMeta();
          if (open) load(true);
        }
      });
    }
    if (memberClear) {
      memberClear.addEventListener('click', () => {
        if (window.MemberPortraitPicker && memberRoot) MemberPortraitPicker.clear(memberRoot);
        if (memberFilter) memberFilter.value = '';
        syncMemberMeta();
        if (open) load(true);
      });
    }

    if (toggle) toggle.addEventListener('click', () => setOpen(!open));
    if (loadMore) loadMore.addEventListener('click', () => load(false));
    [seasonFilter, classFilter].forEach((el) => {
      if (el) el.addEventListener('change', () => { if (open) load(true); });
    });
    if (searchFilter) {
      searchFilter.addEventListener('input', () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => { if (open) load(true); }, 250);
      });
    }
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        if (window.MemberPortraitPicker && memberRoot) MemberPortraitPicker.clear(memberRoot);
        if (memberFilter) memberFilter.value = '';
        if (seasonFilter) seasonFilter.value = '';
        if (classFilter) classFilter.value = '';
        if (searchFilter) searchFilter.value = '';
        syncMemberMeta();
        if (open) load(true);
      });
    }

    syncMemberMeta();
    updateSummary();
    setOpen(false);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initArticleObjektPicker);
  } else {
    initArticleObjektPicker();
  }
})();
