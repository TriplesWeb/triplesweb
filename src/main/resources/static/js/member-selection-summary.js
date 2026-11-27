/**
 * 멤버 선택 요약: 케미명 → 유닛명 → 단체 콘텐츠 → N명.
 * 미디어·아카이브 토글과 작성 모달이 같이 쓴다.
 */
(function (global) {
  const GROUP_LABEL = '단체 콘텐츠';
  const EMPTY_LABEL = '멤버 선택';
  const PAGE_SIZE = 12;

  function memberKey(name) {
    return String(name || '').trim().toLowerCase().replace(/\s+/g, '');
  }

  function sameMemberSet(left, right) {
    if (!left.length || left.length !== right.length) return false;
    const leftKeys = left.map(memberKey);
    const rightKeys = new Set(right.map(memberKey));
    if (new Set(leftKeys).size !== rightKeys.size) return false;
    return leftKeys.every((key) => rightKeys.has(key));
  }

  function parseJson(id) {
    const node = document.getElementById(id);
    if (!node) return [];
    try {
      const data = JSON.parse(node.textContent || '[]');
      return Array.isArray(data) ? data : [];
    } catch (error) {
      return [];
    }
  }

  function chemistryEntries() {
    const rows = [
      ...parseJson('archiveChemistryNames'),
      ...parseJson('memberChemistryNames')
    ];
    const select = document.getElementById('archiveChem');
    if (select) {
      [...select.options].filter((option) => option.value).forEach((option) => {
        rows.push({
          label: option.dataset.chemName || option.textContent.split('·')[0].trim(),
          href: '',
          members: String(option.dataset.members || '').split(',').map((name) => name.trim()).filter(Boolean)
        });
      });
    }
    return rows.map((entry) => ({
      name: String(entry?.label || entry?.name || '').trim(),
      href: typeof entry?.href === 'string' ? entry.href : '',
      members: (Array.isArray(entry?.members) ? entry.members : String(entry?.members || '').split(','))
        .map((name) => String(name).trim()).filter(Boolean)
    })).filter((entry) => entry.name && entry.members.length >= 2);
  }

  function unitEntries() {
    const rows = parseJson('memberUnitRoster');
    document.querySelectorAll('[data-unit-checks] input[data-members]').forEach((input) => {
      const name = (input.closest('label')?.querySelector('span')?.textContent || input.value || '').trim();
      const members = String(input.dataset.members || '').split(',').map((part) => part.trim()).filter(Boolean);
      if (name && members.length >= 2) rows.push({ name, members });
    });
    return rows.map((entry) => ({
      name: String(entry?.name || entry?.label || '').trim(),
      unitId: entry?.unitId == null || entry?.unitId === '' ? '' : String(entry.unitId),
      members: (Array.isArray(entry?.members) ? entry.members : String(entry?.members || '').split(','))
        .map((name) => String(name).trim()).filter(Boolean)
    })).filter((entry) => entry.name && entry.members.length >= 2);
  }

  function exact(selected, catalog) {
    if (!selected.length) return null;
    return catalog.find((entry) => sameMemberSet(entry.members, selected) && entry.name) || null;
  }

  function describe(names) {
    const selected = (Array.isArray(names) ? names : [])
      .map((name) => String(name || '').trim())
      .filter(Boolean);
    if (!selected.length) return { kind: 'empty', label: EMPTY_LABEL, members: [] };
    if (selected.length === 1) return { kind: 'single', label: selected[0], members: selected };
    const chem = exact(selected, chemistryEntries());
    if (chem) return { kind: 'chem', label: chem.name, href: chem.href || '', members: selected };
    const unit = exact(selected, unitEntries());
    if (unit) return { kind: 'unit', label: unit.name, members: selected, unitId: unit.unitId || '' };
    if (selected.length >= 24) return { kind: 'group', label: GROUP_LABEL, members: selected };
    return { kind: 'count', label: selected.length + '명', members: selected };
  }

  function paintToggle(toggle, countEl, names, emptyLabel) {
    const summary = describe(names);
    const count = summary.members.length;
    const label = toggle?.querySelector?.('[data-member-toggle-label]') || toggle;
    if (label) {
      label.title = summary.members.join(', ') || emptyLabel || EMPTY_LABEL;
      label.textContent = count === 0 ? (emptyLabel || EMPTY_LABEL) : summary.label;
    }
    if (countEl) {
      countEl.hidden = count === 0;
      countEl.textContent = String(count);
    }
    toggle?.classList?.toggle('has-selection', count > 0);
    toggle?.classList?.toggle('is-group', summary.kind === 'group');
    return summary;
  }

  function paintArtistMeta(root) {
    if (!root) return describe([]);
    const picker = root.querySelector?.('.member-portrait-picker') || root;
    const names = global.MemberPortraitPicker ? MemberPortraitPicker.selectedValues(picker) : [];
    const summary = describe(names);
    const count = root.querySelector('[data-picker-count]');
    if (count) count.textContent = String(names.length);
    const label = root.querySelector('[data-picker-summary]');
    if (label) {
      label.textContent = names.length === 0 ? '' : summary.label;
      label.hidden = names.length === 0;
    }
    const chips = root.querySelector('[data-artist-chips]');
    if (chips) {
      chips.replaceChildren();
      if (summary.kind === 'group') {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'ar-group-content';
        button.textContent = GROUP_LABEL;
        button.setAttribute('aria-label', GROUP_LABEL);
        chips.append(button);
      } else if (!names.length) {
        chips.textContent = '멤버를 선택하면 여기에 표시됩니다.';
      } else {
        names.forEach((name) => {
          const chip = document.createElement('button');
          chip.type = 'button';
          chip.className = 'ar-artist-chip';
          chip.textContent = name + ' ×';
          chip.setAttribute('aria-label', name + ' 선택 해제');
          chip.addEventListener('click', () => {
            picker.querySelectorAll('.mpp-item').forEach((item) => {
              if ((item.dataset.value || item.dataset.name) === name) {
                global.MemberPortraitPicker?.setActive(item, false);
              }
            });
            if (typeof picker.__memberOnChange === 'function') {
              picker.__memberOnChange(global.MemberPortraitPicker?.selectedValues(picker) || []);
            } else {
              paintArtistMeta(root);
            }
          });
          chips.append(chip);
        });
      }
    }
    const box = root.querySelector('[data-artist-inputs]');
    if (box) {
      box.replaceChildren();
      names.forEach((name) => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'artistNames';
        input.value = name;
        box.append(input);
      });
    }
    return summary;
  }

  function pagerMarkup(current, totalPages) {
    const nav = document.createElement('nav');
    nav.className = 'ar-panel ar-pager member-picker-pager';
    nav.setAttribute('aria-label', '멤버 페이지');
    const summary = document.createElement('p');
    summary.className = 'ar-pager-summary';
    summary.innerHTML = '<span class="ar-pager-count">멤버</span>';
    const group = document.createElement('div');
    group.className = 'ar-pager-nav';
    group.setAttribute('role', 'group');
    group.setAttribute('aria-label', '페이지 번호');
    const prev = document.createElement('button');
    prev.type = 'button';
    prev.className = 'ar-pager-step' + (current <= 1 ? ' is-disabled' : '');
    prev.dataset.memberPage = String(Math.max(1, current - 1));
    prev.textContent = '이전';
    if (current <= 1) prev.setAttribute('aria-disabled', 'true');
    const pages = document.createElement('div');
    pages.className = 'ar-pages';
    const start = Math.max(1, Math.min(current - 2, Math.max(1, totalPages - 4)));
    const end = Math.min(totalPages, start + 4);
    for (let i = start; i <= end; i += 1) {
      const num = document.createElement('button');
      num.type = 'button';
      num.className = 'ar-pager-num' + (i === current ? ' is-current' : '');
      num.dataset.memberPage = String(i);
      num.textContent = String(i);
      if (i === current) num.setAttribute('aria-current', 'page');
      pages.append(num);
    }
    const next = document.createElement('button');
    next.type = 'button';
    next.className = 'ar-pager-step' + (current >= totalPages ? ' is-disabled' : '');
    next.dataset.memberPage = String(Math.min(totalPages, current + 1));
    next.textContent = '다음';
    if (current >= totalPages) next.setAttribute('aria-disabled', 'true');
    group.append(prev, pages, next);
    const jump = document.createElement('div');
    jump.className = 'ar-page-jump';
    jump.setAttribute('aria-label', '페이지 이동');
    const meta = document.createElement('span');
    meta.className = 'ar-page-jump-meta';
    meta.innerHTML = current + '<span aria-hidden="true">/</span>' + totalPages;
    const field = document.createElement('label');
    field.className = 'ar-page-jump-field';
    const hiddenLabel = document.createElement('span');
    hiddenLabel.className = 'visually-hidden';
    hiddenLabel.textContent = '이동할 페이지';
    const input = document.createElement('input');
    input.type = 'number';
    input.name = 'go';
    input.min = '1';
    input.max = String(totalPages);
    input.value = String(current);
    input.setAttribute('aria-label', '이동할 페이지');
    field.append(hiddenLabel, input);
    const submit = document.createElement('button');
    submit.type = 'button';
    submit.dataset.memberJump = 'true';
    submit.textContent = '이동';
    jump.append(meta, field, submit);
    nav.append(summary, group, jump);
    return nav;
  }

  function bindPager(picker, pagerHost, pageSize) {
    if (!picker || !pagerHost) return;
    const size = Number(pageSize) > 0 ? Number(pageSize) : PAGE_SIZE;
    const items = [...picker.querySelectorAll('.mpp-item')];
    const total = items.length;
    const totalPages = Math.max(1, Math.ceil(total / size));
    let current = 1;

    function show(page) {
      current = Math.min(totalPages, Math.max(1, page));
      items.forEach((item, index) => {
        const itemPage = Math.floor(index / size) + 1;
        item.hidden = itemPage !== current;
      });
      pagerHost.replaceChildren();
      if (totalPages < 2) return;
      const nav = pagerMarkup(current, totalPages);
      nav.addEventListener('click', (event) => {
        const button = event.target.closest('[data-member-page]');
        if (!button || button.classList.contains('is-disabled')) return;
        event.preventDefault();
        show(Number(button.dataset.memberPage) || 1);
      });
      const jump = nav.querySelector('.ar-page-jump');
      const jumpInput = jump?.querySelector('input[name="go"]');
      const applyJump = () => show(Number(jumpInput?.value) || 1);
      jump?.querySelector('[data-member-jump]')?.addEventListener('click', (event) => {
        event.preventDefault();
        applyJump();
      });
      jumpInput?.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        applyJump();
      });
      pagerHost.append(nav);
    }

    picker._memberPagerShow = show;
    show(1);
  }

  global.MemberSelectionSummary = {
    GROUP_LABEL,
    EMPTY_LABEL,
    describe,
    paintToggle,
    paintArtistMeta,
    bindPager
  };
})(window);
