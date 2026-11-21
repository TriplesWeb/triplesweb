(() => {
  if (window.__archiveDateFilterReady) {
    window.bindArchiveDateFields?.(document);
    return;
  }
  window.__archiveDateFilterReady = true;
  const isoDate = /^(\d{4})-(\d{2})-(\d{2})$/;
  const keepers = [
    "[data-date-filter]",
    ".ar-sort-control",
    ".ar-member-panel",
    ".ar-member-toggle"
  ].join(", ");
  const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

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
      const wasOpen = !pop.hidden;
      pop.hidden = true;
      pop.querySelectorAll(".ar-cal-board").forEach((board) => { board.hidden = true; });
      if (wasOpen) pop.dispatchEvent(new CustomEvent("archive-date-close", { bubbles: true }));
    });
    document.querySelectorAll(".ar-cal-board").forEach((board) => {
      if (keep && keep.closest(".ar-cal")?.contains(board)) return;
      if (keep && board.contains(keep)) return;
      board.hidden = true;
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
    document.querySelectorAll(".ar-member-panel").forEach((panel) => {
      const memberToggle = panel.id
        ? document.querySelector('.ar-member-toggle[aria-controls="' + panel.id + '"]')
        : null;
      if (keepRoot && (keepRoot === panel || keepRoot.contains(panel) || panel.contains(keepRoot) || keepRoot === memberToggle || memberToggle?.contains(keepRoot))) return;
      panel.hidden = true;
      memberToggle?.setAttribute("aria-expanded", "false");
      memberToggle?.classList.remove("is-open");
    });
  };
  window.closeArchiveOverlays = closeArchiveOverlays;

  /**
   * 커스텀 달력은 아카이브 날짜 UI만 대상으로 한다.
   * 이벤트/샵/일정 등 일반 input[type=date]까지 감싸면 관리 모달 CSS와 충돌한다.
   */
  const shouldBindCalendar = (input) => {
    if (!(input instanceof HTMLInputElement) || input.type !== "date") return false;
    if (input.dataset.nativeDate != null || input.getAttribute("data-ar-cal") === "0") return false;
    if (input.hasAttribute("data-ar-cal")) return true;
    if (input.hasAttribute("data-date-exact")
      || input.hasAttribute("data-date-from")
      || input.hasAttribute("data-date-to")) {
      return true;
    }
    return Boolean(input.closest("[data-date-filter], .ar-date-field, [data-ar-cal-root]"));
  };

  const bindCalendarField = (input) => {
    if (!shouldBindCalendar(input)) return;
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
    const year = makePart("year", 4, "년");
    const month = makePart("month", 2, "월");
    const day = makePart("day", 2, "일");
    const dot = () => {
      const sep = document.createElement("span");
      sep.className = "ar-cal-sep";
      sep.setAttribute("aria-hidden", "true");
      sep.textContent = ".";
      return sep;
    };
    parts.append(year, dot(), month, dot(), day);
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

    const board = document.createElement("div");
    board.className = "ar-cal-board";
    board.hidden = true;
    board.setAttribute("role", "dialog");
    board.setAttribute("aria-label", "날짜 선택");
    const openBtn = document.createElement("button");
    openBtn.type = "button";
    openBtn.className = "ar-cal-open";
    openBtn.setAttribute("aria-haspopup", "dialog");
    openBtn.setAttribute("aria-label", "달력 열기");
    openBtn.innerHTML = '<span class="material-symbols-outlined" aria-hidden="true">calendar_month</span>';
    wrap.append(openBtn, board);
    let viewYear = 0;
    let viewMonth = 0;
    const selectedDate = () => {
      const iso = validIso(input.value);
      return iso ? new Date(iso + "T00:00:00") : null;
    };
    const setViewFromValue = () => {
      const selected = selectedDate() || new Date();
      viewYear = selected.getFullYear();
      viewMonth = selected.getMonth();
    };
    const renderBoard = () => {
      const first = new Date(viewYear, viewMonth, 1);
      const startWeekday = first.getDay();
      const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
      const selected = selectedDate();
      const today = new Date();
      board.replaceChildren();
      const head = document.createElement("div");
      head.className = "ar-cal-board-head";
      const prev = document.createElement("button");
      prev.type = "button";
      prev.className = "ar-cal-nav";
      prev.setAttribute("aria-label", "이전 달");
      prev.textContent = "‹";
      const next = document.createElement("button");
      next.type = "button";
      next.className = "ar-cal-nav";
      next.setAttribute("aria-label", "다음 달");
      next.textContent = "›";
      const title = document.createElement("div");
      title.className = "ar-cal-board-title";
      const yearBtn = document.createElement("button");
      yearBtn.type = "button";
      yearBtn.className = "ar-cal-pick";
      yearBtn.textContent = viewYear + "년";
      yearBtn.setAttribute("aria-label", "연도 선택");
      const monthBtn = document.createElement("button");
      monthBtn.type = "button";
      monthBtn.className = "ar-cal-pick";
      monthBtn.textContent = (viewMonth + 1) + "월";
      monthBtn.setAttribute("aria-label", "월 선택");
      const menu = document.createElement("div");
      menu.className = "ar-cal-menu";
      menu.hidden = true;
      menu.setAttribute("role", "listbox");
      const yearRange = () => {
        const minAttr = Number(String(input.min || "").slice(0, 4));
        const maxAttr = Number(String(input.max || "").slice(0, 4));
        const now = new Date().getFullYear();
        const start = Math.min(Number.isFinite(minAttr) && minAttr >= 1900 ? minAttr : 2000, viewYear);
        const end = Math.max(Number.isFinite(maxAttr) && maxAttr >= 1900 ? maxAttr : now + 5, viewYear);
        return { start, end };
      };
      const openMenu = (kind) => {
        menu.hidden = false;
        menu.classList.toggle("is-year", kind === "year");
        menu.classList.toggle("is-month", kind === "month");
        menu.replaceChildren();
        yearBtn.classList.toggle("is-open", kind === "year");
        monthBtn.classList.toggle("is-open", kind === "month");
        if (kind === "year") {
          const range = yearRange();
          for (let y = range.end; y >= range.start; y -= 1) {
            const item = document.createElement("button");
            item.type = "button";
            item.textContent = y + "년";
            if (y === viewYear) item.classList.add("is-current");
            item.addEventListener("click", (event) => {
              event.preventDefault();
              event.stopPropagation();
              viewYear = y;
              renderBoard();
            });
            menu.append(item);
          }
        } else {
          for (let m = 0; m < 12; m += 1) {
            const item = document.createElement("button");
            item.type = "button";
            item.textContent = (m + 1) + "월";
            if (m === viewMonth) item.classList.add("is-current");
            item.addEventListener("click", (event) => {
              event.preventDefault();
              event.stopPropagation();
              viewMonth = m;
              renderBoard();
            });
            menu.append(item);
          }
        }
        requestAnimationFrame(() => {
          const current = menu.querySelector(".is-current");
          if (current && !menu.hidden) menu.scrollTop = Math.max(0, current.offsetTop - menu.clientHeight / 2);
        });
      };
      const toggleMenu = (kind, event) => {
        event.preventDefault();
        event.stopPropagation();
        const open = !menu.hidden && menu.classList.contains(kind === "year" ? "is-year" : "is-month");
        if (open) {
          menu.hidden = true;
          yearBtn.classList.remove("is-open");
          monthBtn.classList.remove("is-open");
          return;
        }
        openMenu(kind);
      };
      yearBtn.addEventListener("click", (event) => toggleMenu("year", event));
      monthBtn.addEventListener("click", (event) => toggleMenu("month", event));
      title.append(yearBtn, monthBtn, menu);
      prev.addEventListener("click", (event) => {
        event.stopPropagation();
        viewMonth -= 1;
        if (viewMonth < 0) {
          viewMonth = 11;
          viewYear -= 1;
        }
        renderBoard();
      });
      next.addEventListener("click", (event) => {
        event.stopPropagation();
        viewMonth += 1;
        if (viewMonth > 11) {
          viewMonth = 0;
          viewYear += 1;
        }
        renderBoard();
      });
      head.append(prev, title, next);
      const week = document.createElement("div");
      week.className = "ar-cal-board-week";
      WEEKDAYS.forEach((label, index) => {
        const cell = document.createElement("span");
        cell.textContent = label;
        if (index === 0) cell.classList.add("is-sun");
        if (index === 6) cell.classList.add("is-sat");
        week.append(cell);
      });
      const days = document.createElement("div");
      days.className = "ar-cal-board-days";
      for (let i = 0; i < startWeekday; i++) days.append(document.createElement("span"));
      for (let day = 1; day <= daysInMonth; day++) {
        const cell = document.createElement("button");
        cell.type = "button";
        cell.className = "ar-cal-day";
        cell.textContent = String(day);
        const weekday = new Date(viewYear, viewMonth, day).getDay();
        if (weekday === 0) cell.classList.add("is-sun");
        if (weekday === 6) cell.classList.add("is-sat");
        if (selected && selected.getFullYear() === viewYear && selected.getMonth() === viewMonth && selected.getDate() === day) {
          cell.classList.add("is-selected");
        }
        if (today.getFullYear() === viewYear && today.getMonth() === viewMonth && today.getDate() === day) {
          cell.classList.add("is-today");
        }
        cell.addEventListener("click", (event) => {
          event.stopPropagation();
          const iso = validIso(viewYear + "-" + String(viewMonth + 1).padStart(2, "0") + "-" + String(day).padStart(2, "0"));
          if (iso) applyIso(iso);
          board.hidden = true;
        });
        days.append(cell);
      }
      board.append(head, week, days);
    };
    openBtn.addEventListener("click", (event) => {
      event.preventDefault();
      const willOpen = board.hidden;
      document.querySelectorAll(".ar-cal-board").forEach((other) => {
        if (other !== board) other.hidden = true;
      });
      if (willOpen) {
        setViewFromValue();
        renderBoard();
      }
      board.hidden = !willOpen;
    });
    input.addEventListener("click", (event) => event.preventDefault());

    syncFromNative();
  };
  window.bindArchiveDateFields = (root) => {
    const scope = root || document;
    if (scope instanceof HTMLInputElement) {
      bindCalendarField(scope);
      return;
    }
    scope.querySelectorAll?.('input[type="date"]').forEach(bindCalendarField);
  };
  window.bindArchiveDateFields(document);
  const dateObserver = new MutationObserver((records) => {
    records.forEach((record) => {
      record.addedNodes.forEach((node) => {
        if (!(node instanceof Element)) return;
        if (node.matches('input[type="date"]')) bindCalendarField(node);
        node.querySelectorAll?.('input[type="date"]').forEach(bindCalendarField);
      });
    });
  });
  dateObserver.observe(document.documentElement, { childList: true, subtree: true });

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
      if (!willOpen) pop.dispatchEvent(new CustomEvent("archive-date-close", { bubbles: true }));
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
