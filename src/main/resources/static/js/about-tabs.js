(() => {
  /**
   * 공통 탭: aria-selected, 좌우 방향키, Home/End.
   * rootSelector 안의 [role=tab] 또는 .nav-tab-btn 을 대상으로 한다.
   */
  function enhanceTabs(rootSelector, options) {
    const root = typeof rootSelector === "string"
      ? document.querySelector(rootSelector)
      : rootSelector;
    if (!root) return;
    const opts = options || {};
    const getButtons = () => Array.from(
      root.querySelectorAll(opts.buttonSelector || '[role="tab"], .nav-tab-btn')
    );
    const onActivate = typeof opts.onActivate === "function" ? opts.onActivate : null;

    function syncAria(activeBtn) {
      getButtons().forEach((btn) => {
        const selected = btn === activeBtn;
        btn.setAttribute("aria-selected", selected ? "true" : "false");
        btn.tabIndex = selected ? 0 : -1;
        btn.classList.toggle("is-active", selected);
      });
    }

    getButtons().forEach((btn) => {
      if (!btn.hasAttribute("role")) btn.setAttribute("role", "tab");
      if (!btn.hasAttribute("aria-selected")) {
        btn.setAttribute("aria-selected", btn.classList.contains("is-active") ? "true" : "false");
      }
      btn.addEventListener("keydown", (e) => {
        const buttons = getButtons();
        const idx = buttons.indexOf(btn);
        if (idx < 0) return;
        let next = -1;
        if (e.key === "ArrowRight" || e.key === "ArrowDown") next = (idx + 1) % buttons.length;
        else if (e.key === "ArrowLeft" || e.key === "ArrowUp") next = (idx - 1 + buttons.length) % buttons.length;
        else if (e.key === "Home") next = 0;
        else if (e.key === "End") next = buttons.length - 1;
        if (next < 0) return;
        e.preventDefault();
        const target = buttons[next];
        target.focus();
        if (onActivate) onActivate(target);
        else target.click();
        syncAria(target);
      });
      btn.addEventListener("click", () => syncAria(btn));
    });

    const initial = getButtons().find((b) => b.classList.contains("is-active")) || getButtons()[0];
    if (initial) syncAria(initial);
  }

  window.AboutTabs = { enhanceTabs };
})();
