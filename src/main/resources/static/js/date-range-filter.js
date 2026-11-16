(() => {
  const formatCal = (iso) => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || "");
    if (!match) return "yyyy/mm/dd";
    return match[1] + "/" + match[2] + "/" + match[3];
  };
  const bindCalendarField = (input) => {
    if (!input) return;
    if (input.dataset.calReady) {
      input.dispatchEvent(new Event("input"));
      return;
    }
    input.dataset.calReady = "true";
    input.classList.add("ar-cal-native");
    input.setAttribute("lang", "ko");
    const wrap = document.createElement("span");
    wrap.className = "ar-cal is-empty";
    input.before(wrap);
    wrap.append(input);
    const text = document.createElement("span");
    text.className = "ar-cal-text";
    text.setAttribute("aria-hidden", "true");
    wrap.append(text);
    const sync = () => {
      text.textContent = formatCal(input.value);
      wrap.classList.toggle("is-empty", !input.value);
    };
    input.addEventListener("input", sync);
    input.addEventListener("change", sync);
    wrap.addEventListener("click", (event) => {
      if (event.target === input) return;
      if (typeof input.showPicker === "function") input.showPicker();
      else input.focus();
    });
    sync();
  };
  window.bindArchiveDateFields = (root) => {
    (root || document).querySelectorAll('input[type="date"][name="registeredOn"]').forEach(bindCalendarField);
  };
  window.bindArchiveDateFields(document);

  document.querySelectorAll("[data-date-filter]").forEach((root) => {
    const toggle = root.querySelector("[data-date-toggle]");
    const pop = root.querySelector("[data-date-popover]");
    const exact = root.querySelector("[data-date-exact]");
    const from = root.querySelector("[data-date-from]");
    const to = root.querySelector("[data-date-to]");
    if (!toggle || !pop) return;
    [exact, from, to].filter(Boolean).forEach(bindCalendarField);
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
      if (from) {
        from.value = "";
        from.dispatchEvent(new Event("input"));
      }
      if (to) {
        to.value = "";
        to.dispatchEvent(new Event("input"));
      }
    });
    const clearExact = () => {
      if (exact && (from?.value || to?.value)) {
        exact.value = "";
        exact.dispatchEvent(new Event("input"));
      }
    };
    from?.addEventListener("change", clearExact);
    to?.addEventListener("change", clearExact);
    root.querySelector("[data-date-clear]")?.addEventListener("click", () => {
      root.querySelectorAll('input[type="date"]').forEach((input) => {
        input.value = "";
        input.dispatchEvent(new Event("input"));
      });
      root.closest("form")?.requestSubmit();
    });
  });
  document.addEventListener("click", (event) => {
    if (event.target.closest("[data-date-filter]")) return;
    document.querySelectorAll("[data-date-popover]").forEach((pop) => { pop.hidden = true; });
    document.querySelectorAll("[data-date-toggle]").forEach((toggle) => toggle.setAttribute("aria-expanded", "false"));
  });
})();
