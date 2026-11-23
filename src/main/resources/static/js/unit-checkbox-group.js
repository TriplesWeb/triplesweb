/**
 * 소속 유닛 체크박스 그룹 헬퍼.
 * - setChecks / selectedIds: 아티스트 편집 등
 * - bindMemberHelper: 아카이브 — 유닛은 출연진 프리셋(1개).
 *   유닛 먼저 → 출연진을 그 구성으로 채움.
 *   멤버 먼저 → 유닛만 체크(출연진 유지, 수동 선택 유지).
 *   멤버 완전 일치 → 유닛 자동 체크(결성일 이후만). 유닛 해제 → 유닛만 끄고 멤버는 유지.
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
    // Set·배열 모두 허용. syncFromMembers는 Set을 넘긴다.
    return Array.from(new Set(Array.from(names || [], String)))
      .sort()
      .join("\0");
  }

  function suppressedSet(el) {
    if (!el._unitCheckSuppressed) el._unitCheckSuppressed = new Set();
    return el._unitCheckSuppressed;
  }

  function clearManual(el) {
    if (el) delete el._unitCheckManualId;
  }

  function setManual(el, id) {
    if (!el) return;
    el._unitCheckManualId = String(id);
  }

  function setChecks(root, selectedIds) {
    const el = rootEl(root);
    clearManual(el);
    const selected = new Set((selectedIds || []).map(String));
    checkboxes(root).forEach((cb) => {
      cb.checked = selected.has(String(cb.value));
    });
  }

  /** 출연진과 달라도 유지되는 수동 유닛 선택. */
  function selectManual(root, unitId) {
    const el = rootEl(root);
    const id = unitId == null || String(unitId).trim() === "" ? "" : String(unitId).trim();
    clearManual(el);
    checkboxes(root).forEach((cb) => {
      cb.checked = Boolean(id) && String(cb.value) === id;
    });
    if (id) setManual(el, id);
  }

  function selectedIds(root) {
    return checkboxes(root)
      .filter((cb) => cb.checked)
      .map((cb) => cb.value);
  }

  /**
   * 출연 멤버와 구성이 완전히 같은 유닛을 체크.
   * 결성일 이전(registeredOn &lt; formationDate)이면 자동 체크하지 않는다.
   * 수동으로 고른 유닛이 있으면 멤버·결성일 불일치여도 그 선택을 유지한다.
   * @param {Element|string} root
   * @param {string[]} memberNames
   * @param {{ registeredOn?: string }} [options]
   */
  function syncFromMembers(root, memberNames, options) {
    const el = rootEl(root);
    const selected = new Set((memberNames || []).map(String));
    const registeredOn = options && options.registeredOn != null ? String(options.registeredOn) : "";
    const key = membersKey(selected);
    if (el) {
      // 멤버 구성이 바뀌면 수동 해제 억제를 풀고, 수동 유닛도 멤버 변경에 맞춰 재평가한다.
      // 사진일만 바뀐 경우에는 수동 선택을 유지한다.
      if (el._unitCheckMembersKey !== key) {
        el._unitCheckSuppressed = new Set();
        el._unitCheckMembersKey = key;
        clearManual(el);
      }
    }
    const suppressed = el ? suppressedSet(el) : new Set();
    const manualId = el && el._unitCheckManualId ? String(el._unitCheckManualId) : "";
    checkboxes(root).forEach((cb) => {
      const id = String(cb.value);
      if (manualId && id === manualId && !suppressed.has(id)) {
        cb.checked = true;
        return;
      }
      const match = sameMemberSet(parseMembers(cb), selected) && formedByRegisteredOn(cb, registeredOn);
      cb.checked = match && !suppressed.has(id);
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
      const members = parseMembers(cb);
      if (cb.checked) {
        // 한 유닛만: 다른 체크 해제
        suppressedSet(el).clear();
        checkboxes(el).forEach((other) => {
          if (other !== cb) other.checked = false;
        });
        const current = (hooks.getMembers() || []).map(String).filter(Boolean);
        if (!current.length) {
          // 유닛 먼저: 출연진을 그 유닛 구성으로 채움
          clearManual(el);
          hooks.applyMembers(members);
        } else {
          // 멤버 먼저: 유닛만 체크하고 출연진은 유지
          setManual(el, cb.value);
          if (typeof hooks.onChange === "function") hooks.onChange();
        }
        return;
      }
      // 해제: 유닛만 끄고 출연진은 유지. 같은 구성으로 다시 자동 체크되지 않게 억제.
      clearManual(el);
      suppressedSet(el).add(String(cb.value));
      el._unitCheckMembersKey = membersKey(hooks.getMembers());
      if (typeof hooks.onChange === "function") hooks.onChange();
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
