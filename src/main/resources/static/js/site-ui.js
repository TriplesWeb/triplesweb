(function () {
  function modalApi(element) {
    if (!element) return { show() {}, hide() {}, get isOpen() { return false; } };
    return {
      show() {
        element.classList.add('is-open', 'show');
        element.setAttribute('aria-hidden', 'false');
        document.body.classList.add('site-modal-open');
        element.dispatchEvent(new CustomEvent('site-modal:shown', { bubbles: true }));
      },
      hide() {
        element.classList.remove('is-open', 'show');
        element.setAttribute('aria-hidden', 'true');
        if (!document.querySelector('.site-modal.is-open')) {
          document.body.classList.remove('site-modal-open');
        }
        element.dispatchEvent(new CustomEvent('site-modal:hidden', { bubbles: true }));
      },
      get isOpen() { return element.classList.contains('is-open'); }
    };
  }

  function hideThen(element, callback) {
    const done = () => { element.removeEventListener('site-modal:hidden', done); callback && callback(); };
    element.addEventListener('site-modal:hidden', done, { once: true });
    modalApi(element).hide();
    // already closed
    if (!element.classList.contains('is-open')) {
      element.removeEventListener('site-modal:hidden', done);
      callback && callback();
    }
  }

  function bindModals(root) {
    const scope = root || document;
    scope.querySelectorAll('[data-site-toggle="modal"]').forEach((btn) => {
      btn.addEventListener('click', (event) => {
        const target = document.querySelector(btn.getAttribute('data-site-target') || '');
        if (!target) return;
        event.preventDefault();
        modalApi(target).show();
        if (btn.hasAttribute('data-ytid')) {
          target.dispatchEvent(new CustomEvent('site-modal:open-from', {
            bubbles: true,
            detail: { trigger: btn }
          }));
        }
      });
    });
    scope.querySelectorAll('.site-modal').forEach((element) => {
      element.addEventListener('click', (event) => {
        if (event.target === element || event.target.closest('[data-site-dismiss="modal"]')) {
          event.preventDefault();
          modalApi(element).hide();
        }
      });
      element.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && element.classList.contains('is-open')) {
          event.preventDefault();
          modalApi(element).hide();
        }
      });
    });
  }

  function bindCarousels(root) {
    const scope = root || document;
    scope.querySelectorAll('[data-site-carousel]').forEach((carousel) => {
      const track = carousel.querySelector('.site-carousel-track');
      const slides = carousel.querySelectorAll('.site-carousel-slide');
      if (!track || slides.length === 0) return;
      let index = 0;
      const go = (next) => {
        index = (next + slides.length) % slides.length;
        track.style.transform = `translateX(${-index * 100}%)`;
      };
      carousel.querySelector('[data-site-carousel-prev]')?.addEventListener('click', () => go(index - 1));
      carousel.querySelector('[data-site-carousel-next]')?.addEventListener('click', () => go(index + 1));
      const auto = Number(carousel.dataset.autoplay || 0);
      if (auto > 0 && slides.length > 1) {
        setInterval(() => go(index + 1), auto);
      }
    });
  }

  window.SiteUi = { modal: modalApi, hideThen, bindModals, bindCarousels };
  document.addEventListener('DOMContentLoaded', () => {
    bindModals(document);
    bindCarousels(document);
  });
})();
