(() => {
  "use strict";

  const english = document.documentElement.lang.toLowerCase().startsWith("en");
  const labels = english
    ? {
        notice: "Notice",
        confirm: "Confirm action",
        accept: "Confirm",
        cancel: "Cancel",
        loading: "Loading…",
        busy: "Working…"
      }
    : {
        notice: "안내",
        confirm: "작업 확인",
        accept: "확인",
        cancel: "취소",
        loading: "불러오는 중…",
        busy: "처리 중…"
      };

  const dialog = document.createElement("dialog");
  dialog.className = "site-feedback-dialog";
  dialog.setAttribute("role", "alertdialog");
  dialog.setAttribute("aria-labelledby", "site-feedback-title");
  dialog.setAttribute("aria-describedby", "site-feedback-message");
  dialog.innerHTML = `
    <h2 id="site-feedback-title"></h2>
    <p id="site-feedback-message"></p>
    <form method="dialog" class="site-feedback-actions">
      <button type="submit" value="cancel"></button>
      <button type="submit" value="accept"></button>
    </form>`;
  const [cancel, accept] = dialog.querySelectorAll("button");
  cancel.textContent = labels.cancel;
  accept.textContent = labels.accept;

  let active;
  const queue = [];
  function showNext() {
    if (active || !queue.length) return;
    active = queue.shift();
    if (!dialog.isConnected) document.body.append(dialog);
    const options = active.options || {};
    dialog.classList.toggle("is-compact", Boolean(options.compact));
    dialog.querySelector("h2").textContent = options.title || (active.confirm ? labels.confirm : labels.notice);
    dialog.querySelector("h2").hidden = Boolean(options.compact);
    dialog.querySelector("p").textContent = active.message;
    cancel.hidden = !active.confirm;
    cancel.textContent = options.cancel || labels.cancel;
    accept.textContent = options.accept || labels.accept;
    dialog.returnValue = "";
    dialog.showModal();
    (active.confirm ? cancel : accept).focus();
  }

  dialog.addEventListener("close", () => {
    const request = active;
    active = null;
    request.resolve(dialog.returnValue === "accept");
    showNext();
  });
  // Underlying editors must not handle Escape while this dialog is open.
  window.addEventListener("keydown", (event) => {
    if (dialog.open && event.key === "Escape") event.stopImmediatePropagation();
  }, true);

  function ask(message, confirm, options) {
    return new Promise((resolve) => {
      queue.push({ message, confirm, options: options || {}, resolve });
      showNext();
    });
  }

  const progress = document.createElement("div");
  progress.className = "site-feedback-progress";
  progress.hidden = true;
  progress.setAttribute("role", "progressbar");
  progress.setAttribute("aria-label", labels.loading);
  progress.append(document.createElement("span"));

  const busy = document.createElement("div");
  busy.className = "site-feedback-busy";
  busy.hidden = true;
  busy.setAttribute("role", "progressbar");
  busy.setAttribute("aria-label", labels.busy);
  busy.setAttribute("aria-live", "polite");
  busy.innerHTML = `<div class="site-feedback-busy-panel"><span class="site-feedback-busy-spinner" aria-hidden="true"></span><p></p></div>`;
  busy.querySelector("p").textContent = labels.busy;

  let pending = 0;
  let busyPending = 0;

  function syncProgress() {
    progress.hidden = pending === 0 || busyPending > 0;
  }

  function syncBusy() {
    busy.hidden = busyPending === 0;
    document.documentElement.toggleAttribute("data-site-feedback-busy", busyPending > 0);
  }

  function begin() {
    pending++;
    syncProgress();
    let finished = false;
    return () => {
      if (finished) return;
      finished = true;
      pending--;
      syncProgress();
    };
  }

  function beginBusy() {
    busyPending++;
    syncBusy();
    syncProgress();
    let finished = false;
    return () => {
      if (finished) return;
      finished = true;
      busyPending--;
      syncBusy();
      syncProgress();
    };
  }

  function mountChrome() {
    document.body.append(progress, busy);
  }
  if (document.body) mountChrome();
  else document.addEventListener("DOMContentLoaded", mountChrome, { once: true });
  if (document.readyState !== "complete") {
    window.addEventListener("load", begin(), { once: true });
  }

  function isSilent(input, init) {
    if (init && init.siteFeedbackSilent === true) return true;
    if (input && typeof input === "object" && input.siteFeedbackSilent === true) return true;
    return false;
  }

  function trackBody(response, inherit) {
    for (const method of ["arrayBuffer", "blob", "bytes", "formData", "json", "text"]) {
      if (typeof response[method] !== "function") continue;
      const read = response[method].bind(response);
      response[method] = async (...args) => {
        let finishRead = null;
        if (inherit && !inherit.claimed) {
          inherit.claimed = true;
          finishRead = inherit.finish;
        } else {
          finishRead = begin();
        }
        try {
          return await read(...args);
        } finally {
          finishRead();
        }
      };
    }
    return response;
  }

  // Track requests from every page, including overlapping requests and body reads.
  const fetch = window.fetch.bind(window);
  window.fetch = async (input, init) => {
    if (isSilent(input, init)) {
      const options = init ? { ...init } : undefined;
      if (options) delete options.siteFeedbackSilent;
      return fetch(input, options);
    }
    const inherit = { finish: begin(), claimed: false };
    try {
      const response = await fetch(input, init);
      trackBody(response, inherit);
      // After promise .then body readers (microtasks), release unused network pending.
      setTimeout(() => {
        if (!inherit.claimed) inherit.finish();
      }, 0);
      return response;
    } catch (error) {
      inherit.finish();
      throw error;
    }
  };

  let finishNavigation;
  let navigationTimeout;
  function startNavigation() {
    clearTimeout(navigationTimeout);
    finishNavigation?.();
    finishNavigation = begin();
    if (!window.navigation) navigationTimeout = setTimeout(finishNavigation, 30000);
    return finishNavigation;
  }
  window.navigation?.addEventListener("navigate", (event) => {
    if (event.destination.sameDocument || event.hashChange || event.downloadRequest !== null) return;
    if (new URL(event.destination.url).origin !== location.origin) return;
    const finish = startNavigation();
    event.signal.addEventListener("abort", finish, { once: true });
  });
  window.addEventListener("pageshow", () => finishNavigation?.());

  const confirmed = new WeakSet();
  const awaiting = new WeakSet();

  // Wait until form-level validation has run, then resubmit with the same button.
  document.addEventListener("submit", async (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || event.defaultPrevented || form.method === "dialog"
        || form.hasAttribute("data-admin-fetch")) return;
    const message = form.dataset.confirm;
    if (message && !confirmed.has(form)) {
      event.preventDefault();
      if (awaiting.has(form)) return;
      awaiting.add(form);
      try {
        if (await ask(message, true) && form.isConnected) {
          confirmed.add(form);
          beginBusy();
          form.requestSubmit(event.submitter || undefined);
        }
      } finally {
        confirmed.delete(form);
        awaiting.delete(form);
      }
      return;
    }
    if (!window.navigation && form.target !== "_blank" && busyPending === 0) {
      queueMicrotask(() => {
        if (!event.defaultPrevented) startNavigation();
      });
    }
  });

  document.addEventListener("click", async (event) => {
    const control = event.target.closest?.("[data-confirm-action]");
    if (!control || confirmed.has(control)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    if (awaiting.has(control)) return;
    awaiting.add(control);
    try {
      if (await ask(control.dataset.confirmAction, true) && control.isConnected) {
        confirmed.add(control);
        control.click();
      }
    } finally {
      confirmed.delete(control);
      awaiting.delete(control);
    }
  }, true);

  if (!window.navigation) {
    document.addEventListener("click", (event) => {
      const link = event.target.closest?.("a[href]");
      if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey
          || link.download || (link.target && link.target !== "_self")) return;
      const url = new URL(link.href);
      if (url.origin !== location.origin || (url.pathname === location.pathname && url.search === location.search && url.hash)) return;
      queueMicrotask(() => {
        if (!event.defaultPrevented && busyPending === 0) startNavigation();
      });
    });
  }

  window.SiteFeedback = {
    alert: (message) => ask(message, false),
    confirm: (message, options) => ask(message, true, options),
    beginProgress: begin,
    beginBusy
  };
})();
