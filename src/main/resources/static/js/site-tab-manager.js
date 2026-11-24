// Lucian UX/UI Prompt #025 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#025
// Lucian UX/UI Prompt #297 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#297
// Lucian UX/UI Prompt #033 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#033
// Lucian UX/UI Prompt #436 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#436
// Lucian UX/UI Prompt #479 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#479

(() => {
  const reducedMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const defaultColor = "#00f5d4";
  const validColor = (value) => /^#[0-9a-f]{6}$/i.test(value || "") ? value : defaultColor;

  async function request(fetcher, url, { method = "GET", payload } = {}) {
    const headers = new Headers({ Accept: "application/json" });
    if (payload !== undefined) headers.set("Content-Type", "application/json");
    const response = await fetcher(url, {
      method,
      headers,
      body: payload === undefined ? undefined : JSON.stringify(payload),
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!response.ok) {
      let detail;
      if (response.headers.get("Content-Type")?.includes("html")) {
        const page = new DOMParser().parseFromString(await response.text(), "text/html");
        detail = page.querySelector("[data-site-error-detail]")?.textContent.trim();
      } else {
        const body = await response.json().catch(() => null);
        detail = body?.detail || body?.message;
      }
      throw new Error(detail || (response.status === 403 ? "관리자 로그인이 필요합니다." : "요청에 실패했습니다."));
    }
    if (method !== "DELETE") return response.json();
  }

  function message(element, text = "") {
    element.textContent = text;
    element.hidden = !text;
  }

  function paintSwatch(parent, color) {
    parent.querySelector(".tab-manager-swatch").style.setProperty("--tab-swatch", validColor(color));
  }

  function trapFocus(dialog) {
    dialog.addEventListener("keydown", (event) => {
      if (event.key !== "Tab") return;
      const controls = [...dialog.querySelectorAll("button, input, summary, [tabindex]")].filter((control) =>
        control.tabIndex >= 0 && !control.disabled && !control.closest("[inert]") && control.getClientRects().length);
      const first = controls[0];
      const last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    });
  }

  function create(config) {
    const dialog = document.querySelector("[data-tab-manager]");
    const confirmation = document.querySelector("[data-tab-confirm]");
    if (!dialog || !confirmation) return;
    trapFocus(dialog);
    trapFocus(confirmation);
    const {
      id, trigger, title, placeholder, nameLabel = "구분 이름", nameMaxLength = 120, emptyText, deleteCopy, apiBase,
      fields = {}, listQuery = "", nameKey = "name", idKey = "sectionId",
      fixedName = () => "", onChange,
    } = config;
    const fetcher = config.fetch ?? ((url, options) => fetch(url, {
      ...options,
      headers: window.AboutAdminAuth?.applyAuthHeaders(options.headers) ?? options.headers,
    }));
    const api = (url, options) => request(fetcher, url, options);
    const titleElement = dialog.querySelector("[data-manager-title]");
    const form = dialog.querySelector(".tab-manager-add");
    const nameInput = form.elements.namedItem("name");
    const addColor = form.querySelector(".tab-manager-add-color");
    const submitButton = form.querySelector('[type="submit"]');
    const list = dialog.querySelector("[data-manager-list]");
    const rowTemplate = dialog.querySelector("[data-manager-row]");
    const colorTemplate = dialog.querySelector("[data-color-template]");
    const empty = dialog.querySelector("[data-manager-empty]");
    const status = dialog.querySelector("[data-manager-status]");
    const error = dialog.querySelector("[data-manager-error]");
    const confirmCopy = confirmation.querySelector("[data-confirm-copy]");
    const confirmError = confirmation.querySelector("[data-confirm-error]");
    const cancelDelete = confirmation.querySelector(".u436-no");
    const acceptDelete = confirmation.querySelector(".u436-ok");
    let rows = [];
    let busy = false;
    let loaded = false;
    let opener;
    let deletion;

    dialog.id = id;
    titleElement.id = id + "Title";
    titleElement.textContent = title;
    dialog.setAttribute("aria-labelledby", titleElement.id);
    confirmation.querySelector("h3").id = id + "DeleteTitle";
    confirmCopy.id = id + "DeleteCopy";
    confirmation.setAttribute("aria-labelledby", id + "DeleteTitle");
    confirmation.setAttribute("aria-describedby", confirmCopy.id);
    const clip = confirmation.querySelector("clipPath");
    clip.id = id + "TrashClip";
    confirmation.querySelector("[data-trash-clip]").setAttribute("clip-path", "url(#" + clip.id + ")");
    nameInput.placeholder = placeholder || "";
    nameInput.maxLength = nameMaxLength;
    nameInput.closest(".u025-f").querySelector("label").textContent = nameLabel;
    addColor.hidden = !fields.color;
    form.classList.toggle("has-options", Boolean(fields.color));
    bindFloatingFields(form, id);
    const newColor = fields.color ? mountColor(addColor, defaultColor) : null;

    function mountColor(details, color) {
      const picker = colorTemplate.content.firstElementChild.cloneNode(true);
      details.querySelector("summary").after(picker);
      const save = details.querySelector("[data-save-color]");
      if (save) picker.querySelector(".u479-footer").append(save);
      return bindColorPicker(picker, validColor(color), (value) => paintSwatch(details, value));
    }

    function setBusy(value) {
      busy = value;
      dialog.setAttribute("aria-busy", String(value));
      submitButton.disabled = busy || !loaded;
    }

    function endpoint(row) {
      return apiBase + "/" + encodeURIComponent(row[idKey]);
    }

    function replaceRow(updated) {
      rows = rows.map((row) => row[idKey] === updated[idKey] ? updated : row);
      return updated;
    }

    async function changedRows(text, detail = {}) {
      status.textContent = text;
      try {
        await onChange?.([...rows], detail);
      } catch {
        message(error, "변경사항은 저장됐지만 탭을 갱신하지 못했습니다.");
      }
    }

    function focusRow(key, selector, fallback) {
      const row = [...list.children].find((item) => item.dataset.rowId === String(key));
      const control = row?.querySelector(selector);
      (control && !control.disabled ? control : row?.querySelector(fallback ?? ".u033-view:not([hidden])") || nameInput).focus();
    }

    async function perform(task, onError = (text) => message(error, text)) {
      if (busy) return;
      setBusy(true);
      message(error);
      status.textContent = "";
      try {
        await task();
      } catch (failure) {
        onError(failure.message || "요청에 실패했습니다.");
      } finally {
        setBusy(false);
      }
    }

    function render(moving = false) {
      const positions = new Map(moving && !reducedMotion()
        ? [...list.children].map((item) => [item.dataset.rowId, item.offsetTop]) : []);
      list.replaceChildren(...rows.map(renderRow));
      message(empty, rows.length ? "" : emptyText);
      for (const item of list.children) {
        const before = positions.get(item.dataset.rowId);
        if (before !== undefined && before !== item.offsetTop) {
          item.animate([{ transform: `translateY(${before - item.offsetTop}px)` }, { transform: "none" }],
            { duration: 380, easing: "cubic-bezier(.3,1.2,.5,1)" });
        }
      }
    }

    function renderRow(row, index) {
      const item = rowTemplate.content.firstElementChild.cloneNode(true);
      item.dataset.rowId = String(row[idKey]);
      const name = row[nameKey];
      const deleteButton = item.querySelector("[data-manager-delete]");
      const editor = item.querySelector(".u033");
      const label = fixedName(row);
      if (label) {
        editor.hidden = deleteButton.hidden = true;
        const fixed = item.querySelector(".tab-manager-fixed-name");
        fixed.hidden = false;
        fixed.querySelector("[data-fixed-name]").textContent = name;
        fixed.querySelector("[data-fixed-badge]").textContent = label;
      } else {
        bindNameEditor(editor, row);
        deleteButton.setAttribute("aria-label", name + " 삭제");
        deleteButton.addEventListener("click", () => {
          if (busy) return;
          deletion = { row, index, button: deleteButton };
          confirmation.classList.remove("is-del", "is-gone");
          confirmCopy.textContent = deleteCopy(name);
          message(confirmError);
          confirmation.showModal();
          cancelDelete.focus();
        });
      }

      for (const button of item.querySelectorAll("[data-move]")) {
        const direction = Number(button.dataset.move);
        button.disabled = index + direction < 0 || index + direction >= rows.length;
        button.addEventListener("click", () => perform(async () => {
          rows = await api(endpoint(row) + "/move?direction=" + direction, { method: "POST" });
          render(true);
          focusRow(row[idKey], `[data-move="${direction}"]`, `[data-move="${-direction}"]:enabled`);
          await changedRows("순서를 바꿨습니다.");
        }));
      }

      if (fields.color) {
        item.querySelector(".tab-manager-row-meta").hidden = false;
        const details = item.querySelector(".tab-manager-row-color");
        details.hidden = false;
        const picker = mountColor(details, row.color);
        details.querySelector("[data-save-color]").addEventListener("click", () => perform(async () => {
          const color = picker.value();
          if (!color) { details.querySelector("[aria-invalid=true]")?.focus(); return; }
          const updated = replaceRow(await api(endpoint(row), { method: "PUT", payload: { color } }));
          row.color = updated.color;
          picker.set(updated.color);
          details.open = false;
          details.querySelector("summary").focus();
          await changedRows("배지 색을 저장했습니다.");
        }));
        details.addEventListener("toggle", () => {
          if (!details.open) picker.set(row.color);
        });
      }
      return item;
    }

    function bindNameEditor(editor, row) {
      const view = editor.querySelector(".u033-view");
      const input = editor.querySelector(".u033-in");
      const controls = editor.querySelector(".u033-edit");
      input.maxLength = nameInput.maxLength;
      function drawName() {
        view.textContent = row[nameKey];
        view.setAttribute("aria-label", row[nameKey] + " 이름 수정");
      }
      function shake() {
        editor.classList.remove("is-shake");
        void editor.offsetWidth;
        editor.classList.add("is-shake");
      }
      function start() {
        if (busy) return;
        input.value = row[nameKey];
        editor.classList.add("is-edit");
        controls.inert = false;
        input.focus();
        input.select();
      }
      function finish() {
        editor.classList.remove("is-edit");
        controls.inert = true;
        view.focus();
      }
      function save() {
        if (busy) return;
        const value = input.value.trim();
        if (!value) { shake(); input.focus(); return; }
        if (value === row[nameKey]) { finish(); return; }
        perform(async () => {
          const updated = replaceRow(await api(endpoint(row), { method: "PUT", payload: { [nameKey]: value } }));
          row[nameKey] = updated[nameKey];
          drawName();
          editor.closest("li").querySelector("[data-manager-delete]").setAttribute("aria-label", row[nameKey] + " 삭제");
          finish();
          view.classList.remove("flash");
          void view.offsetWidth;
          view.classList.add("flash");
          await changedRows("이름을 저장했습니다.");
        });
      }
      function cancel() {
        if (busy) return;
        finish();
        shake();
      }
      drawName();
      view.addEventListener("click", start);
      editor.querySelector(".u033-pen").addEventListener("click", start);
      editor.querySelector(".u033-act.ok").addEventListener("click", save);
      editor.querySelector(".u033-act.no").addEventListener("click", cancel);
      editor.addEventListener("keydown", (event) => {
        if (event.isComposing || event.keyCode === 229 || !editor.classList.contains("is-edit")) return;
        if (event.key === "Escape" || (event.key === "Enter" && event.target === input)) {
          event.preventDefault();
          event.stopPropagation();
          if (event.key === "Enter") save();
          else cancel();
        }
      });
    }

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!loaded) return;
      const name = nameInput.value.trim();
      if (!name) { message(error, "이름을 입력해 주세요."); nameInput.focus(); return; }
      const payload = { [nameKey]: name };
      if (newColor) {
        payload.color = newColor.value();
        if (!payload.color) {
          addColor.open = true;
          addColor.querySelector("[aria-invalid=true]")?.focus();
          return;
        }
      }
      perform(async () => {
        rows.push(await api(apiBase, { method: "POST", payload }));
        render();
        form.reset();
        newColor?.set(defaultColor);
        addColor.open = false;
        nameInput.focus();
        await changedRows("추가했습니다.");
      });
    });

    function closeConfirmation() {
      if (busy) return;
      confirmation.close();
      deletion?.button.focus();
      deletion = null;
    }
    cancelDelete.addEventListener("click", closeConfirmation);
    confirmation.addEventListener("cancel", (event) => {
      event.preventDefault();
      closeConfirmation();
    });
    acceptDelete.addEventListener("click", () => perform(async () => {
      message(confirmError);
      confirmation.setAttribute("aria-busy", "true");
      cancelDelete.disabled = acceptDelete.disabled = true;
      try {
        await api(endpoint(deletion.row), { method: "DELETE" });
        rows = rows.filter((row) => row[idKey] !== deletion.row[idKey]);
        const refresh = changedRows("삭제했습니다.", { deleted: true });
        const animate = !reducedMotion();
        if (animate) {
          confirmation.classList.add("is-del");
          await new Promise((resolve) => setTimeout(resolve, 1350));
          confirmation.classList.add("is-gone");
          await new Promise((resolve) => setTimeout(resolve, 320));
        }
        const next = rows[Math.min(deletion.index, rows.length - 1)];
        confirmation.close();
        deletion = null;
        render();
        if (next) focusRow(next[idKey], "[data-manager-delete]:not([hidden])", "[data-move]:enabled");
        else nameInput.focus();
        await refresh;
      } finally {
        confirmation.setAttribute("aria-busy", "false");
        cancelDelete.disabled = acceptDelete.disabled = false;
        if (confirmation.open) acceptDelete.focus();
      }
    }, (text) => message(confirmError, text)));

    dialog.querySelector("[data-manager-close]").addEventListener("click", () => { if (!busy) dialog.close(); });
    dialog.addEventListener("cancel", (event) => {
      if (busy) event.preventDefault();
    });
    dialog.addEventListener("close", () => {
      (opener?.isConnected ? opener : document.querySelector(trigger))?.focus();
    });

    document.addEventListener("click", (event) => {
      const target = event.target.closest(trigger);
      if (!target || dialog.open) return;
      opener = target;
      loaded = false;
      rows = [];
      list.replaceChildren();
      message(empty);
      form.reset();
      newColor?.set(defaultColor);
      addColor.open = false;
      dialog.showModal();
      nameInput.focus();
      perform(async () => {
        rows = await api(apiBase + listQuery);
        loaded = true;
        render();
      });
    });
  }

  // Render the configured tabs, including empty sections, around the existing server-rendered panes.
  function syncPageSections(prefix, rows, emptyText) {
    const currentTabs = document.getElementById(prefix + "SectionTabs");
    const content = document.getElementById(prefix + "SectionTabContent");
    if (!currentTabs || !content) return;
    const tabs = currentTabs.cloneNode(false);
    const paneById = new Map([...content.children].map((pane) => [pane.id, pane]));
    const tabByTarget = new Map([...currentTabs.querySelectorAll("[data-target]")]
      .map((tab) => [tab.dataset.target, tab]));
    const otherKey = prefix + "-section-other";
    const sections = rows.map((row) => ({ key: prefix + "-section-" + row.sectionId, name: row.name }));
    if (paneById.has(otherKey)) sections.push({ key: otherKey, name: "기타" });
    const panes = sections.map(({ key, name }) => {
      const tab = tabByTarget.get("#" + key)?.cloneNode(true) ?? document.createElement("button");
      tab.type = "button";
      tab.className = "site-section-tab nav-tab-btn " + prefix + "-section-tab";
      tab.id = "tab-" + key;
      tab.dataset.target = "#" + key;
      tab.setAttribute("role", "tab");
      tab.setAttribute("aria-controls", key);
      tab.title = tab.textContent = name;
      tabs.append(tab);

      const existing = paneById.get(key);
      if (existing) return existing;
      const pane = document.createElement("div");
      pane.id = key;
      pane.className = "tab-pane hidden";
      pane.setAttribute("role", "tabpanel");
      pane.setAttribute("aria-labelledby", tab.id);
      const empty = document.createElement("p");
      empty.className = "tab-manager-section-empty";
      empty.textContent = emptyText;
      pane.append(empty);
      return pane;
    });
    content.replaceChildren(...panes);
    currentTabs.replaceWith(tabs);
    tabs.classList.toggle("hidden", !sections.length);
    const empty = document.getElementById(prefix + "SectionEmpty");
    if (empty) empty.hidden = Boolean(sections.length);
  }

  // Reload the server-rendered card panes so deleted sections move their cards into 기타.
  async function refreshPageSections(prefix, rows, emptyText) {
    const response = await fetch(location.href, { cache: "no-store" });
    if (!response.ok) throw new Error("탭 갱신 실패");
    const updated = new DOMParser().parseFromString(await response.text(), "text/html");
    for (const suffix of ["SectionTabs", "SectionTabContent", "SectionEmpty"]) {
      const id = prefix + suffix;
      const current = document.getElementById(id);
      const next = updated.getElementById(id);
      if (current && next) current.replaceWith(next);
    }
    syncPageSections(prefix, rows, emptyText);
  }

  function bindPageSections({ prefix, apiBase, emptyText, onRender, onCardsReplaced }) {
    let changed = false;
    fetch(apiBase)
      .then((response) => response.ok ? response.json() : null)
      .then((rows) => {
        if (!rows || changed) return;
        syncPageSections(prefix, rows, emptyText);
        onRender();
      })
      .catch(() => { /* 서버에서 렌더링한 탭을 유지한다. */ });

    return async (rows, { deleted }) => {
      changed = true;
      if (deleted) {
        await refreshPageSections(prefix, rows, emptyText);
        onCardsReplaced();
      } else {
        syncPageSections(prefix, rows, emptyText);
      }
      onRender();
    };
  }

  function bindFloatingFields(form, id) {
    const fields = [...form.querySelectorAll(".u025-f")].map((field) => {
      const input = field.querySelector(".u025-in");
      const label = field.querySelector(".u025-lab");
      const border = field.querySelector(".u025-line");
      input.id = label.htmlFor = id + "-" + input.name;
      function lift() {
        const focused = document.activeElement === input;
        const up = focused || input.value.length > 0;
        field.classList.toggle("is-focus", focused);
        field.classList.toggle("is-up", up);
        border.style.setProperty("--gap", up ? (label.offsetWidth * 0.8 + 2) + "px" : "0px");
      }
      for (const event of ["focus", "blur", "input"]) input.addEventListener(event, lift);
      return lift;
    });
    form.addEventListener("reset", () => queueMicrotask(() => fields.forEach((lift) => lift())));
  }

  function bindColorPicker(root, initial, onChange) {
    let sv = root.querySelector('.u479-sv'), hue = root.querySelector('.u479-hue');
    let hs = sv.querySelector('.u479-h'), hh = hue.querySelector('.u479-h'), pv = root.querySelector('.u479-pv');
    let hxw = root.querySelector('.u479-hx'), hx = hxw.querySelector('input'), sws = [].slice.call(root.querySelectorAll('.u479-sw'));
    const T = { s: '채도 ', v: ', 명도 ' };
    let H = 0, S = 0, V = 0;
    function hsv2rgb(h, s, v) {
      let f = function (n) { let k = (n + h / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
      return [f(5), f(3), f(1)].map(function (x) { return Math.round(x * 255); });
    }
    function rgb2hsv(r, g, b) {
      r /= 255; g /= 255; b /= 255;
      let mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn, h = 0;
      if (d) h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
      return [(h * 60 + 360) % 360, mx ? d / mx : 0, mx];
    }
    function hex(rgb) { return rgb.map(function (x) { return ('0' + x.toString(16)).slice(-2); }).join('').toUpperCase(); }
    function lum(rgb) { let c = rgb.map(function (x) { x /= 255; return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4); }); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }
    // 화면에 반영: 손잡이 자리, 사각형의 순수 색상, 미리보기 칩의 글자색(대비가 큰 쪽), HEX 칸, 견본 표시
    function draw(fromInput) {
      let rgb = hsv2rgb(H, S, V), h6 = hex(rgb), c = '#' + h6;
      root.style.setProperty('--u479-c', c);
      sv.style.setProperty('--u479-hc', '#' + hex(hsv2rgb(H, 1, 1)));
      hs.style.setProperty('--u479-x', S.toFixed(4)); hs.style.setProperty('--u479-y', (1 - V).toFixed(4));
      hh.style.setProperty('--u479-x', (H / 360).toFixed(4));
      hh.style.setProperty('--u479-c', '#' + hex(hsv2rgb(H, 1, 1)));
      let L = lum(rgb);
      pv.style.setProperty('--u479-fg', (L + 0.05) / 0.05 > 1.05 / (L + 0.05) ? '#16151B' : '#FFFFFF');
      if (!fromInput) hx.value = h6;
      onChange(c);
      hs.setAttribute('aria-valuenow', String(Math.round(S * 100)));
      hs.setAttribute('aria-valuetext', T.s + Math.round(S * 100) + '%' + T.v + Math.round(V * 100) + '%');
      hh.setAttribute('aria-valuenow', String(Math.round(H)));
      sws.forEach(function (b) { b.setAttribute('aria-pressed', String(b.dataset.hex === h6)); });
    }
    function setHex(h6, fromInput) {
      let m = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(h6);
      if (!m) return false;
      let s = m[1].length === 3 ? m[1].replace(/./g, '$&$&') : m[1];
      let hsv = rgb2hsv(parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16));
      // 회색처럼 색상이 정해지지 않는 색은 색상 손잡이를 그 자리에 둡니다
      if (hsv[1] > 0 && hsv[2] > 0) H = hsv[0];
      S = hsv[1]; V = hsv[2];
      draw(fromInput);
      return true;
    }
    // 끌기: 누른 순간에는 손잡이가 그 자리로 출렁이며 가고, 움직이기 시작하면 바로 따라옵니다
    function dragger(area, handle, apply) {
      let on = false, x0 = 0, y0 = 0;
      function at(e) { let r = area.getBoundingClientRect(); apply(Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)), Math.max(0, Math.min(1, (e.clientY - r.top) / r.height))); draw(); }
      area.addEventListener('pointerdown', function (e) {
        if (e.button !== 0) return;
        on = true; x0 = e.clientX; y0 = e.clientY;
        handle.classList.add('is-press');
        try { area.setPointerCapture(e.pointerId); } catch (er) { /* 시연 포인터 */ }
        at(e);
        handle.focus({ preventScroll: true });
        e.preventDefault();
      });
      area.addEventListener('pointermove', function (e) {
        if (!on) return;
        if (Math.abs(e.clientX - x0) + Math.abs(e.clientY - y0) > 3) handle.classList.add('is-drag');
        at(e);
      });
      function end() { if (!on) return; on = false; handle.classList.remove('is-press', 'is-drag'); }
      area.addEventListener('pointerup', end);
      area.addEventListener('pointercancel', end);
      area.addEventListener('lostpointercapture', end);
    }
    dragger(sv, hs, function (x, y) { S = x; V = 1 - y; });
    dragger(hue, hh, function (x) { H = Math.min(359.9, x * 360); });
    function keys(e) {
      let k = e.shiftKey ? 10 : 1, d = { ArrowLeft: [-k, 0], ArrowRight: [k, 0], ArrowUp: [0, k], ArrowDown: [0, -k] }[e.key];
      if (!d && e.key !== 'Home' && e.key !== 'End') return null;
      e.preventDefault();
      return d || (e.key === 'Home' ? [-1000, -1000] : [1000, 1000]);
    }
    hs.addEventListener('keydown', function (e) {
      let d = keys(e); if (!d) return;
      S = Math.max(0, Math.min(1, S + d[0] / 100)); V = Math.max(0, Math.min(1, V + d[1] / 100)); draw();
    });
    hh.addEventListener('keydown', function (e) {
      let d = keys(e); if (!d) return;
      H = Math.max(0, Math.min(359, H + d[0] + d[1])); draw();
    });
    hx.addEventListener('input', function () { hxw.classList.remove('is-bad'); hx.removeAttribute('aria-invalid'); if (/^#?[0-9a-f]{6}$/i.test(hx.value.trim())) setHex(hx.value.trim(), true); });
    function commit() {
      if (setHex(hx.value.trim(), false)) {
        hx.removeAttribute('aria-invalid');
        return '#' + hx.value;
      }
      hxw.classList.remove('is-bad');
      void hxw.offsetWidth;
      hxw.classList.add('is-bad');
      hx.setAttribute('aria-invalid', 'true');
      return null;
    }
    hx.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); commit(); } });
    hx.addEventListener('blur', commit);
    sws.forEach(function (b) { b.addEventListener('click', function () { setHex(b.dataset.hex, false); }); });
    function set(value) {
      hxw.classList.remove('is-bad');
      hx.removeAttribute('aria-invalid');
      setHex(value, false);
    }
    set(initial);
    return { set, value: commit };
  }

  window.SiteTabManager = { create, bindPageSections };
})();
