/**
 * 소속 유닛 체크박스 그룹 헬퍼.
 * - setChecks / selectedIds: 아티스트 편집 등
 * - bindMemberHelper: 아카이브 — 유닛은 출연진 프리셋(1개, 교체). 완전 일치만 체크 표시.
 *   멤버 선택 → 유닛 자동 체크. 유닛 해제 → 유닛만 끄고 멤버는 유지(같은 구성 재자동체크 억제).
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

  function membersKey(names) {
    // Set·배열 모두 허용. syncFromMembers는 Set을 넘긴다.
    return Array.from(new Set(Array.from(names || [], String)))
      .sort()
      .join("\0");
  }

  function suppressedSet(el) {
    if (!el._unitCheckSuppressed) el._unitCheckSuppressed = new Set();
    return el._unitCheckSuppressed;
  }

  function setChecks(root, selectedIds) {
    const selected = new Set((selectedIds || []).map(String));
    checkboxes(root).forEach((cb) => {
      cb.checked = selected.has(String(cb.value));
    });
  }

  function selectedIds(root) {
    return checkboxes(root)
      .filter((cb) => cb.checked)
      .map((cb) => cb.value);
  }

  /** 출연 멤버와 구성이 완전히 같은 유닛만 체크. (부분집합 매칭 금지) */
  function syncFromMembers(root, memberNames) {
    const el = rootEl(root);
    const selected = new Set((memberNames || []).map(String));
    const key = membersKey(selected);
    if (el) {
      // 멤버 구성이 바뀌면 수동 해제 억제를 풀고 다시 자동 매칭한다.
      if (el._unitCheckMembersKey !== key) {
        el._unitCheckSuppressed = new Set();
        el._unitCheckMembersKey = key;
      }
    }
    const suppressed = el ? suppressedSet(el) : new Set();
    checkboxes(root).forEach((cb) => {
      const match = sameMemberSet(parseMembers(cb), selected);
      cb.checked = match && !suppressed.has(String(cb.value));
    });
  }

  /**
   * @param {Element|string} unitRoot
   * @param {{ getMembers: () => string[], applyMembers: (names: string[]) => void }} hooks
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
      const members = parseMembers(cb);
      if (cb.checked) {
        // 한 유닛만: 다른 체크 해제 후 출연진을 그 유닛 구성으로 교체
        suppressedSet(el).clear();
        checkboxes(el).forEach((other) => {
          if (other !== cb) other.checked = false;
        });
        hooks.applyMembers(members);
        return;
      }
      // 해제: 유닛만 끄고 출연진은 유지. 같은 구성으로 다시 자동 체크되지 않게 억제.
      suppressedSet(el).add(String(cb.value));
      el._unitCheckMembersKey = membersKey(hooks.getMembers());
    });
  }

  window.UnitCheckboxGroup = {
    setChecks,
    selectedIds,
    syncFromMembers,
    bindMemberHelper,
    parseMembers
  };
})();
