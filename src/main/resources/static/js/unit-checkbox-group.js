/**
 * 소속 유닛 체크박스 그룹. 아카이브에서는 출연진을 유닛 구성으로 채울 수 있다.
 */
(function () {
  function rootEl(root) {
    if (!root) return null;
    return typeof root === "string" ? document.querySelector(root) : root;
  }

  function checkboxes(root) {
    const el = rootEl(root);
    if (!el) return [];
    return Array.from(el.querySelectorAll('input[type="checkbox"]'));
  }

  function parseMembers(cb) {
    return String(cb?.dataset?.members || "")
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean);
  }

  function sameMemberSet(members, selected) {
    if (!members.length || members.length !== selected.size) return false;
    return members.every((name) => selected.has(name));
  }

  /** 사진일(registeredOn)이 결성일 이전이면 자동 매칭하지 않는다. 날짜가 없으면 허용. */
  function formedByRegisteredOn(cb, registeredOn) {
    const formation = String(cb?.dataset?.formationDate || "").trim();
    const date = String(registeredOn || "").trim();
    if (!formation || !date) return true;
    return date >= formation;
  }

  function membersKey(names) {
    return Array.from(new Set(Array.from(names || [], String)))
      .sort()
      .join("\0");
  }

  const states = new WeakMap();

  function memberState(el) {
    if (!states.has(el)) {
      states.set(el, { manual: new Set(), suppressed: new Set(), memberKey: null, automaticKey: null });
    }
    return states.get(el);
  }

  function setChecks(root, selectedIds) {
    const el = rootEl(root);
    if (!el) return;
    states.delete(el);
    const selected = new Set((selectedIds || []).map(String));
    checkboxes(el).forEach((cb) => {
      cb.checked = selected.has(String(cb.value));
    });
  }

  /** 저장된 소속을 복원하고 현재 출연진의 자동 추정을 억제한다. */
  function selectManual(root, unitIds) {
    const el = rootEl(root);
    if (!el) return;
    const state = memberState(el);
    state.manual = new Set((unitIds || []).map(String));
    state.suppressed.clear();
    state.automaticKey = null;
    checkboxes(el).forEach((cb) => {
      const id = String(cb.value);
      cb.checked = state.manual.has(id);
      if (!cb.checked) state.suppressed.add(id);
    });
  }

  function selectedIds(root) {
    const el = rootEl(root);
    if (!el) return [];
    const checks = checkboxes(el);
    const values = new Set(checks.map((cb) => String(cb.value)));
    const selected = checks.filter((cb) => cb.checked).map((cb) => String(cb.value));
    // 관리 화면에 보이지 않는 유닛도 기존 글을 편집할 때 보존한다.
    for (const id of states.get(el)?.manual || []) {
      if (!values.has(id)) selected.push(id);
    }
    return selected;
  }

  /**
   * 수동 선택을 유지하며 출연진과 정확히 일치하는 결성된 유닛을 자동 체크한다.
   * 유닛 선택으로 출연진을 채운 동안에는 체크박스 선택을 그대로 유지한다.
   */
  function syncFromMembers(root, memberNames, options) {
    const el = rootEl(root);
    if (!el) return;
    const state = memberState(el);
    const selected = new Set((memberNames || []).map(String));
    const key = membersKey(selected);
    if (state.automaticKey === key) return;
    state.automaticKey = null;
    if (state.memberKey !== key) {
      state.suppressed.clear();
      state.memberKey = key;
    }
    checkboxes(el).forEach((cb) => {
      const id = String(cb.value);
      const match = sameMemberSet(parseMembers(cb), selected) && formedByRegisteredOn(cb, options?.registeredOn);
      cb.checked = state.manual.has(id) || (match && !state.suppressed.has(id));
    });
  }

  /**
   * @param {Element|string} unitRoot
   * @param {{
   *   getMembers: () => string[],
   *   applyMembers: (names: string[]) => void,
   *   onChange?: () => void
   * }} hooks
   */
  function bindMemberHelper(unitRoot, hooks) {
    const el = rootEl(unitRoot);
    if (!el || !hooks || typeof hooks.getMembers !== "function" || typeof hooks.applyMembers !== "function") {
      return;
    }
    if (el.dataset.unitHelperBound === "1") return;
    el.dataset.unitHelperBound = "1";

    el.addEventListener("change", (event) => {
      const cb = event.target;
      if (!(cb instanceof HTMLInputElement) || cb.type !== "checkbox") return;
      const state = memberState(el);
      const id = String(cb.value);
      const current = membersKey(hooks.getMembers());
      if (cb.checked) {
        state.manual.add(id);
        state.suppressed.delete(id);
      } else {
        state.manual.delete(id);
        state.suppressed.add(id);
      }
      if (!current || state.automaticKey === current) {
        const members = [...new Set(checkboxes(el).filter((option) => option.checked).flatMap(parseMembers))];
        state.automaticKey = membersKey(members);
        state.memberKey = state.automaticKey;
        hooks.applyMembers(members);
      } else {
        state.automaticKey = null;
        state.memberKey = current;
        hooks.onChange?.();
      }
    });
  }

  window.UnitCheckboxGroup = {
    setChecks,
    selectManual,
    selectedIds,
    syncFromMembers,
    bindMemberHelper,
    parseMembers,
    formedByRegisteredOn
  };
})();
