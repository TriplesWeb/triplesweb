(() => {
  document.querySelectorAll("[data-date-filter]").forEach((root) => {
    const toggle = root.querySelector("[data-date-toggle]");
    const pop = root.querySelector("[data-date-popover]");
    const exact = root.querySelector("[data-date-exact]");
    const from = root.querySelector("[data-date-from]");
    const to = root.querySelector("[data-date-to]");
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
    exact?.addEventListener("change", () => {
      if (!exact.value) return;
      if (from) from.value = "";
      if (to) to.value = "";
    });
    const clearExact = () => {
      if (exact && (from?.value || to?.value)) exact.value = "";
    };
    from?.addEventListener("change", clearExact);
    to?.addEventListener("change", clearExact);
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
