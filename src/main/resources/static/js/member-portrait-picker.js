/**
 * 멤버 초상화 다중선택 그리드 헬퍼.
 * - 프레임 이미지 바인딩 (S1~S24)
 * - 클릭으로 is-active 토글 (다중선택)
 */
(function (global) {
  const FRAME_BASE = "/images/artist/tab-frames/";

  const PLACEHOLDER = "/images/image-upload-pending.png";

  function resolvePicker(root) {
    if (!root || root === document) return document.querySelector(".member-portrait-picker");
    if (root.classList && root.classList.contains("member-portrait-picker")) return root;
    return root.querySelector(".member-portrait-picker");
  }

  function paintFrameMask(avatar, frame) {
    if (!avatar || !frame) return;
    const src = frame.currentSrc || frame.src;
    if (!src) return;
    avatar.style.setProperty("--frame-mask", 'url("' + src.replace(/"/g, "") + '")');
    avatar.classList.add("has-frame-mask");
  }

  function bindFrameSrc(frame, n, avatar) {
    if (frame.dataset.boundFrame === String(n)) {
      paintFrameMask(avatar, frame);
      return;
    }
    frame.dataset.boundFrame = String(n);
    frame.onerror = () => {
      frame.hidden = true;
      if (avatar) {
        avatar.style.removeProperty("--frame-mask");
        avatar.classList.remove("has-frame-mask");
      }
    };
    frame.onload = () => {
      frame.onerror = null;
      paintFrameMask(avatar, frame);
    };
    frame.hidden = false;
    frame.src = FRAME_BASE + n + ".png";
    if (frame.complete && frame.naturalWidth) paintFrameMask(avatar, frame);
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
    bindFrameSrc(frame, n, avatar);
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
      if (!img.__memberErrorBound) { img.addEventListener("error", drop); img.__memberErrorBound = true; }
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

  /** 탭 프레임 PNG를 미리 받아 모달 첫 페인트가 막히지 않게 한다. */
  function preloadFrames() {
    for (let n = 1; n <= 24; n += 1) {
      const img = new Image();
      img.decoding = "async";
      img.src = FRAME_BASE + n + ".png";
    }
  }

  /**
   * 숨겨진 모달 안 lazy 초상화를 미리 받아 둔다.
   * (hidden 모달의 loading=lazy는 열릴 때 한꺼번에 요청되어 1초 넘게 비는 경우가 많다)
   */
  function warmPicker(root) {
    const picker = resolvePicker(root);
    if (!picker) return;
    picker.querySelectorAll(".artist-tab-photo").forEach((img) => {
      const src = (img.getAttribute("src") || "").trim();
      if (!src) return;
      img.loading = "eager";
      img.decoding = "async";
      const warm = new Image();
      warm.decoding = "async";
      warm.src = src;
    });
    bindFrames(picker);
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

  function setValues(root, values) {
    const picker = resolvePicker(root) || root;
    const wanted = new Set((values || []).map((value) => String(value)));
    (picker || document).querySelectorAll(".mpp-item").forEach((btn) => {
      const value = btn.dataset.value || btn.dataset.name || "";
      setActive(btn, wanted.has(value));
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

    picker.__memberOnChange = options.onChange;
    if (picker.__memberClickHandler) picker.removeEventListener("click", picker.__memberClickHandler);
    const handleClick = (e) => {
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
    };
    picker.__memberClickHandler = handleClick;
    picker.addEventListener("click", handleClick);
  }

  global.MemberPortraitPicker = {
    bindFrames,
    preloadFrames,
    warmPicker,
    bindMultiSelect,
    selectedValues,
    setValues,
    setActive,
    clear
  };
})(window);
