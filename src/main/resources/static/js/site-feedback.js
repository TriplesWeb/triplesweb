(() => {
  "use strict";

  const english = document.documentElement.lang.toLowerCase().startsWith("en");
  const labels = english
    ? { notice: "Notice", confirm: "Confirm action", accept: "Confirm", cancel: "Cancel", loading: "Loading…" }
    : { notice: "안내", confirm: "작업 확인", accept: "확인", cancel: "취소", loading: "불러오는 중…" };

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
    dialog.querySelector("h2").textContent = active.confirm ? labels.confirm : labels.notice;
    dialog.querySelector("p").textContent = active.message;
    cancel.hidden = !active.confirm;
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

  function ask(message, confirm) {
    return new Promise((resolve) => {
      queue.push({ message, confirm, resolve });
      showNext();
    });
  }

  const progress = document.createElement("div");
  progress.className = "site-feedback-progress";
  progress.hidden = true;
  progress.setAttribute("role", "progressbar");
  progress.setAttribute("aria-label", labels.loading);
  progress.append(document.createElement("span"));

  let pending = 0;
  function begin() {
    pending++;
    progress.hidden = false;
    let finished = false;
    return () => {
      if (finished) return;
      finished = true;
      pending--;
      progress.hidden = pending === 0;
    };
  }

  function mountProgress() {
    document.body.append(progress);
  }
  if (document.body) mountProgress();
  else document.addEventListener("DOMContentLoaded", mountProgress, { once: true });
  if (document.readyState !== "complete") {
    window.addEventListener("load", begin(), { once: true });
  }

  // Track requests from every page, including overlapping requests and body reads.
  const fetch = window.fetch.bind(window);
  window.fetch = async (...args) => {
    const finish = begin();
    try {
      const response = await fetch(...args);
      for (const method of ["arrayBuffer", "blob", "bytes", "formData", "json", "text"]) {
        if (typeof response[method] !== "function") continue;
        const read = response[method].bind(response);
        response[method] = async (...args) => {
          const finishRead = begin();
          try {
            return await read(...args);
          } finally {
            finishRead();
          }
        };
      }
      return response;
    } finally {
      finish();
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
    event.signal.addEventListener("abort", startNavigation(), { once: true });
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
          form.requestSubmit(event.submitter || undefined);
        }
      } finally {
        confirmed.delete(form);
        awaiting.delete(form);
      }
      return;
    }
    if (!window.navigation && form.target !== "_blank") {
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
        if (!event.defaultPrevented) startNavigation();
      });
    });
  }

  window.SiteFeedback = {
    alert: (message) => ask(message, false),
    confirm: (message) => ask(message, true),
    beginProgress: begin
  };
})();
