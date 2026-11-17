/**
 * Consistent dismissal for custom modals.
 * Backdrop taps never dismiss a modal. X, Escape and Android Back dismiss the
 * uppermost modal through its existing close button/cleanup logic.
 * Native <dialog> elements already receive browser close requests.
 */
(() => {
  'use strict';

  const MODALS = '.site-modal, .about-modal, .artist-modal, .chem-modal, .ar-dialog, .modal, #siteAdminLoginModal, #objektDetailModal';
  const OPEN = '.site-modal.is-open, .about-modal.flex, .artist-modal.flex, .chem-modal.flex, .ar-dialog:not([hidden]), .modal.show, .modal.is-open, #siteAdminLoginModal.is-open, #objektDetailModal.is-open';
  const CLOSE_BUTTON = [
    '#objektDetailClose', '[data-site-admin-close]',
    '[data-site-dismiss="modal"]', '[data-bs-dismiss="modal"]',
    '[data-ar-close]:not(.ar-dialog-backdrop)',
    '.chem-close', '.about-close', '.artist-edit-close', '.artist-group-close',
    '.site-modal-close', '.btn-close', '[aria-label="닫기"]'
  ].join(', ');
  const tracked = new Map();
  let nextToken = 0;
  let pendingHistoryBack = 0;
  let scanning = false;

  function isOpen(element) {
    return element.isConnected &&
      !element.hidden &&
      !element.classList.contains('hidden') &&
      element.getAttribute('aria-hidden') !== 'true' &&
      element.matches(OPEN) &&
      getComputedStyle(element).display !== 'none';
  }

  function topModal() {
    return [...tracked.values()].at(-1);
  }

  function closeViaExistingHandler(entry) {
    if (!tracked.has(entry.element) || entry.closing) return;
    entry.closing = true;
    const close = entry.element.querySelector(CLOSE_BUTTON);
    if (close) {
      close.click();
    } else {
      // Defensive fallback for a custom modal without a close control.
      if (entry.element.matches('.site-modal') && window.SiteUi?.modal) {
        window.SiteUi.modal(entry.element).hide();
        return;
      }
      if (entry.element.matches('.modal') && window.bootstrap?.Modal) {
        window.bootstrap.Modal.getOrCreateInstance(entry.element).hide();
        return;
      }
      entry.element.hidden = true;
      entry.element.classList.remove('flex', 'show', 'is-open');
      entry.element.classList.add('hidden');
      entry.element.setAttribute('aria-hidden', 'true');
      if (!document.querySelector(OPEN)) document.body.style.overflow = '';
    }
    queueMicrotask(() => {
      scan();
      if (tracked.get(entry.element) === entry) entry.closing = false;
    });
  }

  function track(element) {
    const entry = { element, watcher: null, mode: 'watcher', closing: false, fromPop: false, token: ++nextToken };
    tracked.set(element, entry);
    if ('CloseWatcher' in window) {
      try {
        entry.watcher = new CloseWatcher();
        entry.watcher.addEventListener('close', () => {
          if (topModal() === entry) closeViaExistingHandler(entry);
        });
        return;
      } catch (_) { /* Use the history fallback. */ }
    }
    entry.mode = 'history';
    history.pushState({ ...history.state, __siteModalToken: entry.token }, '', location.href);
  }

  function untrack(element) {
    const entry = tracked.get(element);
    if (!entry) return;
    tracked.delete(element);
    entry.watcher?.destroy();
    if (entry.mode === 'history' && !entry.fromPop) {
      pendingHistoryBack++;
      history.back();
    }
  }

  function scan() {
    document.querySelectorAll(MODALS).forEach(element => {
      if (element.matches('dialog')) return; // Browser manages native dialogs.
      if (isOpen(element) && !tracked.has(element)) track(element);
      if (!isOpen(element) && tracked.has(element)) untrack(element);
    });
    for (const element of tracked.keys()) {
      if (!element.isConnected) untrack(element);
    }
  }

  function scheduleScan() {
    if (scanning) return;
    scanning = true;
    queueMicrotask(() => {
      scanning = false;
      scan();
    });
  }

  function start() {
    scan();
    const observer = new MutationObserver(mutations => {
      if (mutations.some(mutation => {
        if (mutation.type === 'attributes') return mutation.target.matches?.(MODALS);
        return [...mutation.addedNodes, ...mutation.removedNodes].some(node =>
          node.nodeType === 1 && (node.matches?.(MODALS) || node.querySelector?.(MODALS))
        );
      })) scheduleScan();
    });
    observer.observe(document.documentElement, {
      subtree: true, childList: true, attributes: true,
      attributeFilter: ['class', 'hidden', 'style', 'aria-hidden']
    });
  }

  // Capture before page-specific popstate listeners (which may reload a list).
  window.addEventListener('popstate', event => {
    if (pendingHistoryBack) {
      pendingHistoryBack--;
      event.stopImmediatePropagation();
      return;
    }
    const entry = topModal();
    if (!entry || entry.mode !== 'history') return;
    entry.fromPop = true;
    event.stopImmediatePropagation();
    closeViaExistingHandler(entry);
  }, true);

  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape' || event.defaultPrevented) return;
    if (document.querySelector('dialog[open]')) return; // Native dialog owns Escape.
    const entry = topModal();
    if (!entry) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    closeViaExistingHandler(entry);
  }, true);

  window.SiteModalNavigation = { scan, get openCount() { return tracked.size; } };
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
})();
