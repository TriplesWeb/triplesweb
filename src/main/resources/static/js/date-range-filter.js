(() => {
  const placeholder = "yyyy/mm/dd";
  const isoDate = /^(\d{4})-(\d{2})-(\d{2})$/;

  const formatCal = (iso) => {
    const match = isoDate.exec(iso || "");
    if (!match) return "";
    return match[1] + "/" + match[2] + "/" + match[3];
  };

  const validIso = (iso) => {
    const match = isoDate.exec(iso || "");
    if (!match) return "";
    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    const date = new Date(Date.UTC(year, month - 1, day));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return "";
    return match[1] + "-" + match[2] + "-" + match[3];
  };

  const parseTyped = (raw) => {
    const digits = String(raw || "").replace(/\D/g, "").slice(0, 8);
    if (digits.length !== 8) return "";
    return validIso(digits.slice(0, 4) + "-" + digits.slice(4, 6) + "-" + digits.slice(6, 8));
  };

  const maskTyped = (raw) => {
    const digits = String(raw || "").replace(/\D/g, "").slice(0, 8);
    if (digits.length <= 4) return digits;
    if (digits.length <= 6) return digits.slice(0, 4) + "/" + digits.slice(4);
    return digits.slice(0, 4) + "/" + digits.slice(4, 6) + "/" + digits.slice(6);
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
    const typed = document.createElement("input");
    typed.type = "text";
    typed.className = "ar-cal-typed";
    typed.inputMode = "numeric";
    typed.autocomplete = "off";
    typed.spellcheck = false;
    typed.placeholder = placeholder;
    typed.setAttribute("aria-label", (input.getAttribute("aria-label") || input.name || "날짜") + " 직접 입력");
    wrap.append(typed);

    const syncFromNative = () => {
      typed.value = formatCal(input.value);
      wrap.classList.toggle("is-empty", !input.value);
    };
    const applyTyped = () => {
      const iso = parseTyped(typed.value);
      if (!iso) return false;
      if (input.value !== iso) {
        input.value = iso;
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
      }
      typed.value = formatCal(iso);
      wrap.classList.remove("is-empty");
      return true;
    };

    input.addEventListener("input", syncFromNative);
    input.addEventListener("change", syncFromNative);
    typed.addEventListener("input", () => {
      const start = typed.selectionStart;
      const before = typed.value;
      typed.value = maskTyped(typed.value);
      if (typeof start === "number") {
        const delta = typed.value.length - before.length;
        typed.setSelectionRange(Math.max(0, start + delta), Math.max(0, start + delta));
      }
      const iso = parseTyped(typed.value);
      wrap.classList.toggle("is-empty", !iso);
      if (iso && input.value !== iso) {
        input.value = iso;
        input.dispatchEvent(new Event("input", { bubbles: true }));
      }
    });
    typed.addEventListener("change", applyTyped);
    typed.addEventListener("blur", () => {
      if (!applyTyped()) {
        typed.value = formatCal(input.value);
        wrap.classList.toggle("is-empty", !input.value);
      }
    });
    typed.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        applyTyped();
        typed.blur();
      }
    });
    syncFromNative();
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
