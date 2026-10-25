/**
 * 멤버 초상화 다중선택 그리드 헬퍼.
 * - 프레임 이미지 바인딩 (S1~S24)
 * - 클릭으로 is-active 토글 (다중선택)
 */
(function (global) {
  const FRAME_BASE = "/uploads/images/artist-tab-frames/";

  const PLACEHOLDER = "/images/image-upload-pending.png";

  function hideWithoutImage(root) {
    const scope = root || document;
    scope.querySelectorAll(".member-portrait-picker .mpp-item").forEach((btn) => {
      const img = btn.querySelector(".artist-tab-photo");
      if (!img) return;
      const drop = () => {
        if (btn.dataset.keepOption === 'true') {
          const avatar = btn.querySelector('.artist-tab-avatar');
          if (avatar) {
            const fallback = document.createElement('span');
            fallback.className = 'artist-tab-fallback';
            const number = Number(btn.dataset.frame);
            fallback.textContent = number >= 1 && number <= 24 ? `S${number}` : '•';
            fallback.setAttribute('aria-hidden', 'true');
            avatar.replaceChildren(fallback);
          }
          return;
        }
        const value = btn.dataset.value || "";
        if (value) {
          scope.querySelectorAll(".member-checkbox[data-mpp-value]").forEach((box) => {
            if (box.getAttribute("data-mpp-value") === value) box.remove();
          });
        }
        btn.remove();
      };
      const src = (img.getAttribute("src") || "").trim();
      if (!src || src.indexOf("image-upload-pending") >= 0 || src === PLACEHOLDER) {
        drop();
        return;
      }
      img.addEventListener("error", drop);
    });
  }

  function bindFrames(root) {
    const scope = root || document;
    hideWithoutImage(scope);
    scope.querySelectorAll(".member-portrait-picker .mpp-item").forEach((btn, i) => {
      const frame = btn.querySelector(".artist-tab-frame");
      if (!frame) return;
      const n = Number(btn.dataset.frame) || i + 1;
      if (n < 1 || n > 24) return;
      frame.src = FRAME_BASE + n + ".png";
    });
  }

  function selectedValues(root) {
    return Array.from((root || document).querySelectorAll(".member-portrait-picker .mpp-item.is-active"))
      .map((btn) => btn.dataset.value || btn.dataset.name || "")
      .filter(Boolean);
  }

  function setActive(btn, active) {
    btn.classList.toggle("is-active", Boolean(active));
    btn.setAttribute("aria-pressed", active ? "true" : "false");
  }

  function clear(root) {
    (root || document).querySelectorAll(".member-portrait-picker .mpp-item.is-active").forEach((btn) => {
      setActive(btn, false);
    });
  }

  /**
   * @param {ParentNode} root
   * @param {{ onChange?: (values: string[]) => void, multi?: boolean }} opts
   */
  function bindMultiSelect(root, opts) {
    const options = opts || {};
    const multi = options.multi !== false;
    const picker = (root || document).querySelector(".member-portrait-picker");
    if (!picker) return;

    bindFrames(root);

    picker.addEventListener("click", (e) => {
      const btn = e.target.closest(".mpp-item");
      if (!btn || !picker.contains(btn)) return;
      e.preventDefault();
      const willActive = !btn.classList.contains("is-active");
      if (!multi) {
        picker.querySelectorAll(".mpp-item.is-active").forEach((other) => {
          if (other !== btn) setActive(other, false);
        });
      }
      setActive(btn, willActive);
      if (typeof options.onChange === "function") {
        options.onChange(selectedValues(root));
      }
    });
  }

  global.MemberPortraitPicker = {
    bindFrames,
    bindMultiSelect,
    selectedValues,
    setActive,
    clear
  };
})(window);
