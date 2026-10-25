(() => {
  document.querySelectorAll("[data-date-filter]").forEach((root) => {
    const toggle = root.querySelector("[data-date-toggle]");
    const pop = root.querySelector("[data-date-popover]");
    if (!toggle || !pop) return;
    toggle.addEventListener("click", (event) => {
      event.stopPropagation();
      const open = pop.hasAttribute("hidden");
      document.querySelectorAll("[data-date-popover]").forEach((other) => {
        if (other !== pop) other.hidden = true;
      });
      pop.hidden = !open;
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    root.querySelector("[data-date-clear]")?.addEventListener("click", () => {
      root.querySelectorAll('input[type="date"]').forEach((input) => { input.value = ""; });
      root.closest("form")?.requestSubmit();
    });
  });
  document.addEventListener("click", (event) => {
    if (event.target.closest("[data-date-filter]")) return;
    document.querySelectorAll("[data-date-popover]").forEach((pop) => { pop.hidden = true; });
    document.querySelectorAll("[data-date-toggle]").forEach((toggle) => toggle.setAttribute("aria-expanded", "false"));
  });
})();
