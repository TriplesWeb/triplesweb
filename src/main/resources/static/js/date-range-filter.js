(() => {
  const isoDate = /^(\d{4})-(\d{2})-(\d{2})$/;
  const keepers = [
    "[data-date-filter]",
    ".ar-sort-control",
    "#archiveMemberPanel",
    "#archiveMemberToggle",
    ".ar-member-toggle"
  ].join(", ");

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

  const digitsOnly = (raw, max) => String(raw || "").replace(/\D/g, "").slice(0, max);

  const closeArchiveOverlays = (except) => {
    const keep = except instanceof Element ? except : null;
    const keepRoot = keep?.closest(keepers);
    document.querySelectorAll("[data-date-popover]").forEach((pop) => {
      if (keepRoot && keepRoot.contains(pop)) return;
      pop.hidden = true;
    });
    document.querySelectorAll("[data-date-toggle]").forEach((toggle) => {
      if (keepRoot && keepRoot.contains(toggle)) return;
      toggle.setAttribute("aria-expanded", "false");
    });
    document.querySelectorAll(".ar-sort-menu").forEach((menu) => {
      if (keepRoot && keepRoot.contains(menu)) return;
      menu.hidden = true;
    });
    document.querySelectorAll(".ar-sort-trigger").forEach((trigger) => {
      if (keepRoot && keepRoot.contains(trigger)) return;
      trigger.setAttribute("aria-expanded", "false");
    });
    const panel = document.getElementById("archiveMemberPanel");
    const memberToggle = document.getElementById("archiveMemberToggle");
    if (panel && !(keepRoot && (keepRoot === panel || keepRoot.contains(panel) || panel.contains(keepRoot) || keepRoot === memberToggle || memberToggle?.contains(keepRoot)))) {
      panel.hidden = true;
      memberToggle?.setAttribute("aria-expanded", "false");
      memberToggle?.classList.remove("is-open");
    }
  };
  window.closeArchiveOverlays = closeArchiveOverlays;

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
    const parts = document.createElement("span");
    parts.className = "ar-cal-parts";
    const label = input.getAttribute("aria-label") || input.name || "날짜";
    parts.setAttribute("aria-label", label + " 직접 입력");

    const makePart = (key, max, placeholder) => {
      const field = document.createElement("input");
      field.type = "text";
      field.className = "ar-cal-part";
      field.dataset.part = key;
      field.maxLength = max;
      field.inputMode = "numeric";
      field.autocomplete = "off";
      field.spellcheck = false;
      field.placeholder = placeholder;
      field.setAttribute("aria-label", placeholder);
      field.setAttribute("pattern", "\\d*");
      return field;
    };
    const year = makePart("year", 4, "yyyy");
    const month = makePart("month", 2, "mm");
    const day = makePart("day", 2, "dd");
    const slash = () => {
      const sep = document.createElement("span");
      sep.className = "ar-cal-sep";
      sep.setAttribute("aria-hidden", "true");
      sep.textContent = "/";
      return sep;
    };
    parts.append(year, slash(), month, slash(), day);
    wrap.append(parts);
    const fields = [year, month, day];

    const composeIso = (pad) => {
      const y = digitsOnly(year.value, 4);
      const m = digitsOnly(month.value, 2);
      const d = digitsOnly(day.value, 2);
      if (y.length !== 4 || !m || !d) return "";
      if (!pad && (m.length !== 2 || d.length !== 2)) return "";
      return validIso(y + "-" + m.padStart(2, "0") + "-" + d.padStart(2, "0"));
    };

    const syncEmpty = () => {
      wrap.classList.toggle("is-empty", !input.value && !year.value && !month.value && !day.value);
    };

    const syncFromNative = () => {
      const match = isoDate.exec(input.value || "");
      if (match) {
        year.value = match[1];
        month.value = match[2];
        day.value = match[3];
      } else {
        year.value = "";
        month.value = "";
        day.value = "";
      }
      syncEmpty();
    };

    const applyIso = (iso) => {
      if (!iso) return false;
      if (input.value !== iso) {
        input.value = iso;
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
      }
      const match = isoDate.exec(iso);
      if (match) {
        year.value = match[1];
        month.value = match[2];
        day.value = match[3];
      }
      wrap.classList.remove("is-empty");
      return true;
    };

    const fillFromDigits = (raw) => {
      const digits = digitsOnly(raw, 8);
      if (!digits) return false;
      year.value = digits.slice(0, 4);
      month.value = digits.slice(4, 6);
      day.value = digits.slice(6, 8);
      const iso = composeIso(digits.length >= 8);
      if (iso) applyIso(iso);
      else syncEmpty();
      return digits.length >= 8 && Boolean(iso);
    };

    input.addEventListener("input", syncFromNative);
    input.addEventListener("change", syncFromNative);

    fields.forEach((field, index) => {
      const max = Number(field.maxLength);
      const next = fields[index + 1];
      const prev = fields[index - 1];
      field.addEventListener("beforeinput", (event) => {
        if (event.inputType && event.inputType.startsWith("insert") && event.data && /\D/.test(event.data)) {
          event.preventDefault();
        }
      });
      field.addEventListener("input", () => {
        field.value = digitsOnly(field.value, max);
        const iso = composeIso(false);
        wrap.classList.toggle("is-empty", !iso && !year.value && !month.value && !day.value);
        if (iso) applyIso(iso);
        if (/^\d+$/.test(field.value) && field.value.length >= max && next) next.focus();
      });
      field.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          applyIso(composeIso(true)) || syncFromNative();
          field.blur();
          return;
        }
        if (event.key === "Backspace" && !field.value && prev) {
          event.preventDefault();
          prev.focus();
          prev.value = prev.value.slice(0, -1);
          prev.dispatchEvent(new Event("input"));
        }
      });
    });

    parts.addEventListener("paste", (event) => {
      const text = event.clipboardData?.getData("text") || "";
      const digits = digitsOnly(text, 8);
      if (digits.length < 4) return;
      event.preventDefault();
      fillFromDigits(digits);
      (digits.length >= 8 ? day : digits.length >= 6 ? day : month).focus();
    });

    wrap.addEventListener("focusout", (event) => {
      if (wrap.contains(event.relatedTarget)) return;
      const iso = composeIso(true);
      if (iso) applyIso(iso);
      else syncFromNative();
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
    toggle.addEventListener("click", () => {
      const willOpen = pop.hidden;
      closeArchiveOverlays(toggle);
      pop.hidden = !willOpen;
      toggle.setAttribute("aria-expanded", willOpen ? "true" : "false");
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
    closeArchiveOverlays(event.target);
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeArchiveOverlays();
  });
})();
