(function () {
  function initArticleObjektPicker() {
    const section = document.getElementById('objektPickerSection');
    if (!section) return;

    const panel = document.getElementById('objektPickerPanel');
    const toggle = document.getElementById('objektPickerToggle');
    const summary = document.getElementById('objektSelectedSummary');
    const cards = Array.from(section.querySelectorAll('.objekt-option'));
    const checkboxes = Array.from(section.querySelectorAll('.objekt-checkbox'));
    const loadMore = document.getElementById('loadMoreObjekts');
    const status = document.getElementById('objektPickerStatus');
    const memberFilter = document.getElementById('objektFilterMember');
    const seasonFilter = document.getElementById('objektFilterSeason');
    const classFilter = document.getElementById('objektFilterClass');
    const searchFilter = document.getElementById('objektFilterSearch');
    const resetBtn = document.getElementById('objektFilterReset');
    const batchSize = 20;
    let visibleCount = batchSize;
    let open = false;

    function normalize(value) {
      return (value || '').toString().trim().toLowerCase();
    }

    function selectedCount() {
      return checkboxes.filter((box) => box.checked).length;
    }

    function updateSummary() {
      const count = selectedCount();
      if (summary) {
        summary.textContent = count > 0
          ? `선택된 오브젝트 ${count}개`
          : '선택된 오브젝트 0개';
      }
      if (toggle) {
        toggle.textContent = open ? '목록 닫기' : (count > 0 ? `오브젝트 선택 (${count})` : '오브젝트 선택');
      }
    }

    function setOpen(nextOpen) {
      open = nextOpen;
      if (panel) {
        panel.hidden = !open;
        panel.classList.toggle('hidden', !open);
      }
      if (toggle) {
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      }
      updateSummary();
      if (open) render();
    }

    function matchesFilters(card) {
      const member = normalize(memberFilter && memberFilter.value);
      const season = normalize(seasonFilter && seasonFilter.value);
      const klass = normalize(classFilter && classFilter.value);
      const query = normalize(searchFilter && searchFilter.value);

      if (member && normalize(card.dataset.member) !== member) return false;
      if (season && normalize(card.dataset.season) !== season) return false;
      if (klass && normalize(card.dataset.class) !== klass) return false;
      if (query && !normalize(card.dataset.search).includes(query)) return false;
      return true;
    }

    function render() {
      const matched = cards.filter(matchesFilters);
      let shownMatched = 0;

      cards.forEach((card) => {
        const checkbox = card.querySelector('.objekt-checkbox');
        const isSelected = !!(checkbox && checkbox.checked);
        const matchedCard = matchesFilters(card);
        let shouldShow = false;
        if (isSelected) {
          shouldShow = true;
        } else if (matchedCard && shownMatched < visibleCount) {
          shouldShow = true;
          shownMatched += 1;
        }
        card.hidden = !shouldShow;
      });

      const count = selectedCount();
      const matchedCount = matched.length;
      if (status) {
        status.textContent =
          `표시 ${Math.min(visibleCount, matchedCount)} / 필터 ${matchedCount}개 · 선택 ${count} / 20개`;
      }
      if (loadMore) {
        loadMore.hidden = visibleCount >= matchedCount;
        loadMore.textContent =
          `더 보기 (${Math.min(batchSize, Math.max(0, matchedCount - visibleCount))}개)`;
      }
      updateSummary();
    }

    function resetVisible() {
      visibleCount = batchSize;
      render();
    }

    if (toggle) {
      toggle.addEventListener('click', () => setOpen(!open));
    }

    if (loadMore) {
      loadMore.addEventListener('click', () => {
        visibleCount += batchSize;
        render();
      });
    }

    [memberFilter, seasonFilter, classFilter].forEach((el) => {
      if (el) el.addEventListener('change', resetVisible);
    });
    if (searchFilter) {
      searchFilter.addEventListener('input', resetVisible);
    }
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        if (memberFilter) memberFilter.value = '';
        if (seasonFilter) seasonFilter.value = '';
        if (classFilter) classFilter.value = '';
        if (searchFilter) searchFilter.value = '';
        resetVisible();
      });
    }

    checkboxes.forEach((box) => {
      box.addEventListener('change', () => {
        const selected = checkboxes.filter((item) => item.checked);
        if (selected.length > 20) {
          box.checked = false;
          alert('오브젝트는 최대 20개까지 선택할 수 있습니다.');
        }
        const label = box.closest('.objekt-option');
        if (label) {
          label.classList.toggle('border-teal-300/60', box.checked);
          label.classList.toggle('bg-teal-400/10', box.checked);
        }
        if (open) render();
        else updateSummary();
      });
    });

    // 수정 폼에서 이미 선택된 항목이 있으면 요약만 보여주고, 목록은 버튼으로 연다
    setOpen(false);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initArticleObjektPicker);
  } else {
    initArticleObjektPicker();
  }
})();
