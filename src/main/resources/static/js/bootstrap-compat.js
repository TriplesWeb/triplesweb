(function () {
  'use strict';

  function backdrop() {
    let el = document.querySelector('.modal-backdrop');
    if (!el) {
      el = document.createElement('div');
      el.className = 'modal-backdrop fade show';
      document.body.appendChild(el);
    }
    return el;
  }

  function removeBackdrop() {
    document.querySelectorAll('.modal-backdrop').forEach((el) => el.remove());
  }

  function openModal(modal, relatedTarget) {
    if (!modal) return;
    modal.classList.add('show', 'is-open');
    modal.style.display = 'block';
    modal.removeAttribute('aria-hidden');
    modal.setAttribute('aria-modal', 'true');
    backdrop();
    document.body.style.overflow = 'hidden';
    const showEvt = new CustomEvent('show.bs.modal', { detail: { relatedTarget: relatedTarget || null } });
    Object.defineProperty(showEvt, 'relatedTarget', { value: relatedTarget || null });
    modal.dispatchEvent(showEvt);
    const shownEvt = new CustomEvent('shown.bs.modal', { detail: { relatedTarget: relatedTarget || null } });
    Object.defineProperty(shownEvt, 'relatedTarget', { value: relatedTarget || null });
    modal.dispatchEvent(shownEvt);
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove('show', 'is-open');
    modal.style.display = 'none';
    modal.setAttribute('aria-hidden', 'true');
    modal.removeAttribute('aria-modal');
    if (!document.querySelector('.modal.show, .modal.is-open')) {
      removeBackdrop();
      document.body.style.overflow = '';
    }
    modal.dispatchEvent(new Event('hide.bs.modal'));
    modal.dispatchEvent(new Event('hidden.bs.modal'));
  }

  document.addEventListener('click', (event) => {
    const openBtn = event.target.closest('[data-bs-toggle="modal"]');
    if (openBtn) {
      const sel = openBtn.getAttribute('data-bs-target') || openBtn.getAttribute('href');
      if (sel && sel.startsWith('#')) openModal(document.querySelector(sel), openBtn);
      return;
    }
    const closeBtn = event.target.closest('[data-bs-dismiss="modal"]');
    if (closeBtn) {
      closeModal(closeBtn.closest('.modal'));
      return;
    }
    if (event.target.classList.contains('modal')) {
      closeModal(event.target);
    }
  });

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    document.querySelectorAll('.modal.show, .modal.is-open').forEach(closeModal);
  });

  function initCarousel(root) {
    const items = [...root.querySelectorAll('.carousel-item')];
    if (!items.length) return;
    let index = Math.max(0, items.findIndex((el) => el.classList.contains('active')));
    function go(next) {
      items[index].classList.remove('active');
      index = (next + items.length) % items.length;
      items[index].classList.add('active');
    }
    root.querySelector('[data-bs-slide="prev"]')?.addEventListener('click', (e) => {
      e.preventDefault();
      go(index - 1);
    });
    root.querySelector('[data-bs-slide="next"]')?.addEventListener('click', (e) => {
      e.preventDefault();
      go(index + 1);
    });
    if (root.getAttribute('data-bs-ride') === 'carousel' && items.length > 1) {
      setInterval(() => go(index + 1), 5000);
    }
  }

  document.querySelectorAll('.carousel').forEach(initCarousel);

  const instances = new WeakMap();

  function Modal(el) {
    this._el = el;
    instances.set(el, this);
  }
  Modal.prototype.show = function () { openModal(this._el); };
  Modal.prototype.hide = function () { closeModal(this._el); };
  Modal.getInstance = function (el) { return instances.get(el) || null; };
  Modal.getOrCreateInstance = function (el) {
    return Modal.getInstance(el) || new Modal(el);
  };

  window.bootstrap = { Modal };
})();

