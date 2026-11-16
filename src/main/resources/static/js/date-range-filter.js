(() => {
  const pad = (value) => String(value).padStart(2, "0");
  const parseIso = (value) => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
    if (!match) return { year: "", month: "", day: "" };
    return { year: match[1], month: match[2], day: match[3] };
  };
  const daysInMonth = (year, month) => {
    const y = Number(year);
    const m = Number(month);
    if (!y || !m) return 31;
    return new Date(y, m, 0).getDate();
  };
  const fillSelect = (select, start, end, blank, current, descending) => {
    select.replaceChildren();
    const empty = document.createElement("option");
    empty.value = "";
    empty.textContent = blank;
    select.append(empty);
    const step = descending ? -1 : 1;
    for (let value = start; descending ? value >= end : value <= end; value += step) {
      const option = document.createElement("option");
      option.value = pad(value);
      if (String(value).length === 4) option.value = String(value);
      option.textContent = option.value;
      select.append(option);
    }
    if (current) select.value = current.length === 4 ? current : pad(current);
    if (current && select.value !== (current.length === 4 ? current : pad(current))) select.value = "";
  };
  const enhanceDateInput = (input) => {
    if (!input) return;
    let wrap = input.parentElement?.querySelector("[data-ymd]");
    if (!wrap) {
      if (input.dataset.ymdReady) return;
      wrap = document.createElement("span");
      wrap.className = "ar-ymd";
      wrap.dataset.ymd = "";
      const yearSelect = document.createElement("select");
      const monthSelect = document.createElement("select");
      const daySelect = document.createElement("select");
      yearSelect.dataset.ymdYear = "";
      monthSelect.dataset.ymdMonth = "";
      daySelect.dataset.ymdDay = "";
      yearSelect.setAttribute("aria-label", "연도");
      monthSelect.setAttribute("aria-label", "월");
      daySelect.setAttribute("aria-label", "일");
      wrap.append(yearSelect, monthSelect, daySelect);
      input.before(wrap);
    }
    const year = wrap.querySelector("[data-ymd-year]");
    const month = wrap.querySelector("[data-ymd-month]");
    const day = wrap.querySelector("[data-ymd-day]");
    if (!year || !month || !day) return;
    const thisYear = new Date().getFullYear();
    const syncDays = () => {
      const parsed = parseIso(input.value);
      const max = daysInMonth(year.value, month.value);
      fillSelect(day, 1, max, "일", day.value || parsed.day, false);
    };
    const write = () => {
      if (year.value && month.value && day.value) {
        const max = daysInMonth(year.value, month.value);
        if (Number(day.value) > max) day.value = pad(max);
        input.value = `${year.value}-${month.value}-${day.value}`;
      } else {
        input.value = "";
      }
      input.dispatchEvent(new Event("change", { bubbles: true }));
    };
    const read = () => {
      const parsed = parseIso(input.value);
      fillSelect(year, thisYear + 1, 2018, "연도", parsed.year, true);
      fillSelect(month, 1, 12, "월", parsed.month, false);
      fillSelect(day, 1, daysInMonth(parsed.year, parsed.month), "일", parsed.day, false);
    };
    if (input.dataset.ymdReady) {
      read();
      return;
    }
    input.dataset.ymdReady = "true";
    year.addEventListener("change", () => { syncDays(); write(); });
    month.addEventListener("change", () => { syncDays(); write(); });
    day.addEventListener("change", write);
    input.addEventListener("input", read);
    input.form?.addEventListener("reset", () => queueMicrotask(read));
    input.closest("label")?.classList.add("is-ymd");
    read();
  };
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
    [exact, from, to].filter(Boolean).forEach(enhanceDateInput);
    if (pop.querySelector("[data-ymd]")) pop.classList.add("is-ymd");
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
      [from, to].filter(Boolean).forEach((input) => {
        const wrap = input.parentElement?.querySelector("[data-ymd]");
        if (!wrap) return;
        wrap.querySelector("[data-ymd-year]").value = "";
        wrap.querySelector("[data-ymd-month]").value = "";
        wrap.querySelector("[data-ymd-day]").value = "";
      });
    });
    const clearExact = () => {
      if (exact && (from?.value || to?.value)) {
        exact.value = "";
        const wrap = exact.parentElement?.querySelector("[data-ymd]");
        if (!wrap) return;
        wrap.querySelector("[data-ymd-year]").value = "";
        wrap.querySelector("[data-ymd-month]").value = "";
        wrap.querySelector("[data-ymd-day]").value = "";
      }
    };
    from?.addEventListener("change", clearExact);
    to?.addEventListener("change", clearExact);
    root.querySelector("[data-date-clear]")?.addEventListener("click", () => {
      root.querySelectorAll('input[type="date"]').forEach((input) => { input.value = ""; });
      root.querySelectorAll("[data-ymd] select").forEach((select) => { select.value = ""; });
      root.closest("form")?.requestSubmit();
    });
  });
  document.addEventListener("click", (event) => {
    if (event.target.closest("[data-date-filter]")) return;
    document.querySelectorAll("[data-date-popover]").forEach((pop) => { pop.hidden = true; });
    document.querySelectorAll("[data-date-toggle]").forEach((toggle) => toggle.setAttribute("aria-expanded", "false"));
  });
})();
