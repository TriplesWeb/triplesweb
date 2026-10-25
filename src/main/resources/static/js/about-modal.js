(() => {
  /**
   * 공통 모달: 포커스 트랩, Escape 닫기, 열기 전 포커스 복귀.
   */
  const FOCUSABLE = 'a[href],button:not([disabled]),textarea,input:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])';

  function openModal(modal, options) {
    if (!modal) return;
    const opts = options || {};
    modal.__aboutReturnFocus = document.activeElement;
    modal.hidden = false;
    modal.classList.remove("hidden");
    modal.classList.add("flex");
    modal.setAttribute("aria-modal", "true");
    modal.setAttribute("role", modal.getAttribute("role") || "dialog");
    document.body.style.overflow = "hidden";

    const focusTarget = opts.focusSelector
      ? modal.querySelector(opts.focusSelector)
      : modal.querySelector(FOCUSABLE);
    (focusTarget || modal).focus();

    function onKey(e) {
      if (e.key === "Escape") {
        e.preventDefault();
        closeModal(modal, opts);
        return;
      }
      if (e.key !== "Tab") return;
      const fields = Array.from(modal.querySelectorAll(FOCUSABLE)).filter((el) => el.offsetParent !== null || el === document.activeElement);
      if (!fields.length) return;
      const first = fields[0];
      const last = fields[fields.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
    modal.__aboutKeyHandler = onKey;
    document.addEventListener("keydown", onKey);
  }

  function closeModal(modal, options) {
    if (!modal) return;
    const opts = options || {};
    modal.hidden = true;
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    document.body.style.overflow = "";
    if (modal.__aboutKeyHandler) {
      document.removeEventListener("keydown", modal.__aboutKeyHandler);
      modal.__aboutKeyHandler = null;
    }
    const ret = modal.__aboutReturnFocus;
    modal.__aboutReturnFocus = null;
    if (ret && typeof ret.focus === "function") {
      try { ret.focus(); } catch (_) { /* ignore */ }
    }
    if (typeof opts.onClose === "function") opts.onClose();
  }

  window.AboutModal = { openModal, closeModal };
})();
