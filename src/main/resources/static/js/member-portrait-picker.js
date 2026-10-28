/**
 * 멤버 초상화 다중선택 그리드 헬퍼.
 * - 프레임 이미지 바인딩 (S1~S24)
 * - 클릭으로 is-active 토글 (다중선택)
 */
(function (global) {
  const FRAME_BASES = [
    "/uploads/images/" + encodeURIComponent("3인이상 프로필 테두리") + "/",
    "/uploads/images/artist-tab-frames/"
  ];

  const PLACEHOLDER = "/images/image-upload-pending.png";

  function resolvePicker(root) {
    if (!root || root === document) return document.querySelector(".member-portrait-picker");
    if (root.classList && root.classList.contains("member-portrait-picker")) return root;
    return root.querySelector(".member-portrait-picker");
  }

  function bindFrameSrc(frame, n) {
    let attempt = 0;
    const tryNext = () => {
      if (attempt >= FRAME_BASES.length) {
        frame.hidden = true;
        return;
      }
      const src = FRAME_BASES[attempt++] + n + ".png";
      frame.onerror = tryNext;
      frame.onload = () => { frame.onerror = null; };
      frame.hidden = false;
      frame.src = src;
    };
    tryNext();
  }

  function ensureFrame(avatar, btn, index) {
    if (!avatar) return null;
    if (!avatar.querySelector('.artist-tab-shine')) {
      const shine=document.createElement('span');shine.className='artist-tab-shine';
      shine.setAttribute('aria-hidden','true');avatar.appendChild(shine);
    }
    let frame = avatar.querySelector(".artist-tab-frame");
    if (!frame) {
      frame = document.createElement("img");
      frame.className = "artist-tab-frame";
      frame.alt = "";
      frame.setAttribute("aria-hidden", "true");
      avatar.appendChild(frame);
    }
    const n = Number(btn.dataset.frame) || index + 1;
    if (n < 1 || n > 24) {
      frame.hidden = true;
      return frame;
    }
    bindFrameSrc(frame, n);
    return frame;
  }

  function hideWithoutImage(root) {
    const scope = root || document;
    const items = (scope.classList && scope.classList.contains("member-portrait-picker"))
      ? scope.querySelectorAll(".mpp-item")
      : scope.querySelectorAll(".member-portrait-picker .mpp-item");
    items.forEach((btn, index) => {
      const img = btn.querySelector(".artist-tab-photo");
      if (!img) {
        ensureFrame(btn.querySelector(".artist-tab-avatar"), btn, index);
        return;
      }
      const drop = () => {
        if (btn.dataset.keepOption === 'true') {
          const avatar = btn.querySelector('.artist-tab-avatar');
          if (avatar) {
            const fallback = document.createElement('span');
            fallback.className = 'artist-tab-fallback';
            fallback.textContent = btn.dataset.name || btn.dataset.value || '•';
            fallback.setAttribute('aria-hidden', 'true');
            avatar.replaceChildren(fallback);
            ensureFrame(avatar, btn, index);
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
    const items = (scope.classList && scope.classList.contains("member-portrait-picker"))
      ? scope.querySelectorAll(".mpp-item")
      : scope.querySelectorAll(".member-portrait-picker .mpp-item");
    items.forEach((btn, i) => {
      ensureFrame(btn.querySelector(".artist-tab-avatar"), btn, i);
    });
  }

  function selectedValues(root) {
    const picker = resolvePicker(root) || root;
    return Array.from((picker || document).querySelectorAll(".mpp-item.is-active"))
      .map((btn) => btn.dataset.value || btn.dataset.name || "")
      .filter(Boolean);
  }

  function setActive(btn, active) {
    btn.classList.toggle("is-active", Boolean(active));
    btn.setAttribute("aria-pressed", active ? "true" : "false");
  }

  function clear(root) {
    const picker = resolvePicker(root) || root;
    (picker || document).querySelectorAll(".mpp-item.is-active").forEach((btn) => {
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
    const picker = resolvePicker(root);
    if (!picker) return;

    bindFrames(picker);

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
        options.onChange(selectedValues(picker));
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
