// Lucian UX/UI Prompt #032 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#032
// Lucian UX/UI Prompt #432 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#432
// Lucian UX/UI Prompt #470 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#470

(function (global) {
  'use strict';

  var UNDO_LIFE_MS = 5000;
  var TYPE_COLOR = {
    pdf: '#E5484D',
    png: '#3D8BFF',
    jpg: '#22A06B',
    jpeg: '#22A06B',
    jfif: '#22A06B',
    webp: '#8B6CFF',
    gif: '#E58A17',
    bmp: '#6B7280',
    mp4: '#2B63D9',
    mov: '#2B63D9',
    webm: '#2B63D9'
  };

  function fileKey(file) {
    if (!file) return '';
    return String(file.name || '') + '\0' + String(file.size || 0) + '\0' + String(file.lastModified || 0);
  }

  function matchesAccept(file, accept) {
    if (!accept || !String(accept).trim()) return true;
    if (!file) return false;
    var tokens = String(accept)
      .split(',')
      .map(function (t) { return t.trim().toLowerCase(); })
      .filter(Boolean);
    if (!tokens.length) return true;

    var mime = String(file.type || '').toLowerCase();
    var name = String(file.name || '').toLowerCase();

    return tokens.some(function (token) {
      if (token === '*' || token === '*/*') return true;
      if (token.charAt(0) === '.') {
        return name.endsWith(token);
      }
      if (token.endsWith('/*')) {
        var prefix = token.slice(0, -1);
        return mime.startsWith(prefix);
      }
      return mime === token;
    });
  }

  function mergeFiles(existingFiles, incomingFiles, options) {
    var opts = options || {};
    var mode = opts.mode === 'multi' ? 'multi' : 'single';
    var accept = opts.accept || '';
    var maxFiles = Number(opts.maxFiles);
    var hasMax = Number.isFinite(maxFiles) && maxFiles > 0;
    var reserved = Math.max(0, Number(opts.reserved) || 0);
    var incoming = Array.isArray(incomingFiles) ? incomingFiles : Array.prototype.slice.call(incomingFiles || []);

    var accepted = [];
    var rejectedAccept = [];
    incoming.forEach(function (file) {
      if (!file) return;
      if (matchesAccept(file, accept)) accepted.push(file);
      else rejectedAccept.push(file);
    });

    if (mode === 'single') {
      var truncatedSingle = accepted.length > 1;
      var chosen = accepted.length ? [accepted[0]] : (Array.isArray(existingFiles) ? existingFiles.slice(0, 1) : []);
      return {
        files: chosen,
        added: accepted.length ? [accepted[0]] : [],
        rejectedAccept: rejectedAccept,
        rejectedDuplicate: [],
        rejectedMax: [],
        truncatedSingle: truncatedSingle
      };
    }

    var current = Array.isArray(existingFiles) ? existingFiles.slice() : [];
    var seen = new Set(current.map(fileKey));
    var added = [];
    var rejectedDuplicate = [];
    var rejectedMax = [];

    accepted.forEach(function (file) {
      var key = fileKey(file);
      if (seen.has(key)) {
        rejectedDuplicate.push(file);
        return;
      }
      if (hasMax && current.length + reserved >= maxFiles) {
        rejectedMax.push(file);
        return;
      }
      seen.add(key);
      current.push(file);
      added.push(file);
    });

    return {
      files: current,
      added: added,
      rejectedAccept: rejectedAccept,
      rejectedDuplicate: rejectedDuplicate,
      rejectedMax: rejectedMax,
      truncatedSingle: false
    };
  }

  function reconcileSelection(existingFiles, incomingFiles, options) {
    var res = mergeFiles(existingFiles, incomingFiles, options);
    return {
      files: res.files,
      added: res.added,
      rejectedAccept: res.rejectedAccept,
      rejectedDuplicate: res.rejectedDuplicate,
      rejectedMax: res.rejectedMax,
      truncatedSingle: res.truncatedSingle,
      shouldSyncInput: true
    };
  }

  function removeFileAt(files, index, pendingUndo) {
    var list = Array.isArray(files) ? files.slice() : [];
    if (index < 0 || index >= list.length) {
      return { files: list, pendingUndo: pendingUndo || null, removed: null };
    }
    var removed = list.splice(index, 1)[0];
    return {
      files: list,
      removed: removed,
      pendingUndo: { file: removed, index: index, key: fileKey(removed) }
    };
  }

  function undoRemove(files, pendingUndo) {
    var list = Array.isArray(files) ? files.slice() : [];
    if (!pendingUndo || !pendingUndo.file) {
      return { files: list, restored: null, pendingUndo: null };
    }
    var key = fileKey(pendingUndo.file);
    var already = list.some(function (f) { return fileKey(f) === key; });
    if (!already) {
      var insertAt = Math.max(0, Math.min(pendingUndo.index, list.length));
      list.splice(insertAt, 0, pendingUndo.file);
    }
    return {
      files: list,
      restored: pendingUndo.file,
      pendingUndo: null
    };
  }

  function formatSize(bytes) {
    var b = Number(bytes) || 0;
    return b >= 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(b / 1024)) + ' KB';
  }

  function getExt(name) {
    var m = /\.([a-z0-9]+)$/i.exec(String(name || ''));
    return m ? m[1].toLowerCase() : 'file';
  }

  function syncInputFiles(input, files) {
    if (!input) return;
    if (typeof DataTransfer === 'function') {
      try {
        var dt = new DataTransfer();
        files.forEach(function (f) { dt.items.add(f); });
        input.files = dt.files;
        return;
      } catch (_err) {
        // Fallback when input.files is read-only in non-browser stubs
      }
    }
  }

  var stateMap = new WeakMap();

  function getState(input) {
    return input ? stateMap.get(input) || null : null;
  }

  function buildStatusMessage(result, maxFiles) {
    var parts = [];
    if (result.rejectedAccept && result.rejectedAccept.length) {
      parts.push('지원하지 않는 형식의 파일은 제외했습니다.');
    }
    if (result.truncatedSingle) {
      parts.push('한 장만 선택할 수 있어 첫 번째 파일만 적용했습니다.');
    }
    if (result.rejectedMax && result.rejectedMax.length) {
      parts.push('최대 ' + maxFiles + '개까지 선택할 수 있어 초과 파일은 제외했습니다.');
    }
    if (result.rejectedDuplicate && result.rejectedDuplicate.length) {
      parts.push('이미 추가된 파일(' + result.rejectedDuplicate.length + '개)은 건너뛰었습니다.');
    }
    return {
      text: parts.join(' '),
      isError: !!(result.rejectedAccept.length || result.rejectedMax.length)
    };
  }

  function initSingle(container, input) {
    var labelText = container.dataset.fileLabel || '파일';
    var originalSrc = container.dataset.originalSrc || '';
    var externalPreviewSelector = container.dataset.preview || '';
    var dndOnly = container.hasAttribute('data-dnd-only');

    var state = {
      mode: 'single',
      container: container,
      input: input,
      files: [],
      originalSrc: originalSrc,
      objectUrl: '',
      externalPreviewSelector: externalPreviewSelector,
      dndOnly: dndOnly,
      statusEl: null,
      rootEl: null,
      imgEl: null,
      nameEl: null,
      restoreBtn: null
    };
    stateMap.set(input, state);

    function setStatus(text, isError) {
      if (!state.statusEl) return;
      state.statusEl.textContent = text || '';
      state.statusEl.classList.toggle('is-error', !!isError);
    }

    function revokeObjectUrl() {
      if (state.objectUrl) {
        try { URL.revokeObjectURL(state.objectUrl); } catch (_e) {}
        state.objectUrl = '';
      }
    }

    function getExternalPreviewImg() {
      if (!state.externalPreviewSelector) return null;
      var formScope = container.closest && container.closest('form');
      return (formScope && formScope.querySelector(state.externalPreviewSelector))
        || document.querySelector(state.externalPreviewSelector);
    }

    function updateView() {
      var file = state.files[0] || null;
      var extImg = getExternalPreviewImg();
      revokeObjectUrl();

      if (file) {
        if (String(file.type || '').startsWith('image/')) {
          state.objectUrl = URL.createObjectURL(file);
        }
        if (extImg) {
          if (state.objectUrl) {
            extImg.src = state.objectUrl;
            extImg.classList.remove('hidden');
            extImg.hidden = false;
          }
        } else if (state.rootEl) {
          state.rootEl.classList.add('is-up');
          if (state.imgEl) {
            if (state.objectUrl) {
              state.imgEl.src = state.objectUrl;
              state.imgEl.hidden = false;
            } else {
              state.imgEl.removeAttribute('src');
              state.imgEl.hidden = true;
            }
          }
          if (state.nameEl) {
            state.nameEl.innerHTML = '';
            state.nameEl.appendChild(document.createTextNode(file.name + ' '));
            var sm = document.createElement('small');
            sm.textContent = '· ' + formatSize(file.size);
            state.nameEl.appendChild(sm);
          }
        }
        showRestore(true);
        return;
      }

      if (extImg) {
        if (state.originalSrc) {
          extImg.src = state.originalSrc;
          extImg.classList.remove('hidden');
          extImg.hidden = false;
        } else {
          extImg.removeAttribute('src');
          extImg.classList.add('hidden');
          extImg.hidden = true;
        }
      } else if (state.rootEl) {
        if (state.originalSrc) {
          state.rootEl.classList.add('is-up');
          if (state.imgEl) {
            state.imgEl.src = state.originalSrc;
            state.imgEl.hidden = false;
          }
          if (state.nameEl) {
            state.nameEl.textContent = '현재 ' + labelText;
          }
        } else {
          state.rootEl.classList.remove('is-up');
          if (state.imgEl) {
            state.imgEl.removeAttribute('src');
            state.imgEl.hidden = true;
          }
          if (state.nameEl) state.nameEl.textContent = '';
        }
      }
      showRestore(false);
    }

    // #470 되돌리기 알약. 새 파일을 고른 동안 저장 전까지 계속 보인다(카운트다운 없음).
    function showRestore(on) {
      var btn = state.restoreBtn;
      if (!btn) return;
      btn.classList.toggle('is-on', on);
      btn.tabIndex = on ? 0 : -1;
      if (on) btn.removeAttribute('aria-hidden');
      else btn.setAttribute('aria-hidden', 'true');
    }

    function dispatchChange(added) {
      var wasDispatching = state._internalDispatch;
      state._internalDispatch = true;
      input.dispatchEvent(new CustomEvent('file-picker:change', {
        bubbles: true,
        detail: { files: state.files.slice(), mode: 'single', added: (added || []).slice() }
      }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      state._internalDispatch = wasDispatching;
    }

    function applyIncoming(fileList, shouldDispatch) {
      var res = reconcileSelection(state.files, fileList, {
        mode: 'single',
        accept: input.getAttribute('accept') || ''
      });
      var msg = buildStatusMessage(res, 1);
      setStatus(msg.text, msg.isError);
      state.files = res.files;
      syncInputFiles(input, state.files);
      if (res.added.length) {
        updateView();
        if (shouldDispatch) dispatchChange(res.added);
      }
    }

    state.setFiles = function (files, options) {
      var list = Array.isArray(files) ? files.slice(0, 1) : Array.prototype.slice.call(files || [], 0, 1);
      state.files = list;
      syncInputFiles(input, state.files);
      setStatus('', false);
      updateView();
      if (!options || !options.silent) dispatchChange(list);
    };

    if (dndOnly) {
      var status = document.createElement('p');
      status.className = 'file-drop-status';
      status.setAttribute('role', 'status');
      container.appendChild(status);
      state.statusEl = status;

      var depthOnly = 0;
      container.addEventListener('dragenter', function (e) {
        e.preventDefault();
        depthOnly++;
        container.classList.add('is-drag');
      });
      container.addEventListener('dragover', function (e) {
        e.preventDefault();
        if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
      });
      container.addEventListener('dragleave', function () {
        depthOnly = Math.max(0, depthOnly - 1);
        if (!depthOnly) container.classList.remove('is-drag');
      });
      container.addEventListener('drop', function (e) {
        e.preventDefault();
        depthOnly = 0;
        container.classList.remove('is-drag');
        if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
          applyIncoming(e.dataTransfer.files, true);
        }
      });
      input.addEventListener('change', function () {
        if (state._internalDispatch) return;
        var chosen = Array.prototype.slice.call(input.files || []);
        if (!chosen.length) {
          state.files = [];
          setStatus('', false);
          updateView();
          input.dispatchEvent(new CustomEvent('file-picker:change', {
            bubbles: true,
            detail: { files: [], mode: 'single' }
          }));
          return;
        }
        applyIncoming(chosen, true);
      });
      return state;
    }

    input.classList.add('file-drop-input');
    input.tabIndex = -1;
    input.setAttribute('aria-hidden', 'true');

    var root = document.createElement('div');
    root.className = 'ux u432';
    root.innerHTML =
      '<div class="u432-dz" tabindex="0" role="button" aria-label="' + labelText + ' 올리기">' +
      '  <div class="u432-idle">' +
      '    <span class="u432-ar" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 16V5M7 9.5 12 4.5l5 5"/><path d="M5 15v2.5A2.5 2.5 0 0 0 7.5 20h9a2.5 2.5 0 0 0 2.5-2.5V15"/></svg></span>' +
      '    <b>' + labelText + ' 여기로 끌어다 놓기</b><b aria-hidden="true">놓으면 바로 적용돼요</b>' +
      '    <small>또는 <button type="button" class="u432-br" tabindex="-1">찾아보기</button></small>' +
      '  </div>' +
      '  <div class="u432-up">' +
      '    <div class="u432-preview-frame">' +
      '      <img class="u432-preview-img" alt="' + labelText + ' 미리보기" hidden>' +
      '    </div>' +
      '    <p class="u432-nm"></p>' +
      '    <div class="u432-actions">' +
      '      <button type="button" class="u470-undo u432-undo" tabindex="-1" aria-hidden="true">' +
      '        <svg class="u470-ar" viewBox="0 0 16 16" aria-hidden="true"><path d="M4.5 3.5 2 6l2.5 2.5"/><path d="M2.5 6h7a4 4 0 0 1 0 8H7"/></svg>' +
      '        <span>되돌리기</span>' +
      '      </button>' +
      '    </div>' +
      '  </div>' +
      '</div>' +
      '<p class="file-drop-status" role="status"></p>';

    container.insertBefore(root, input);
    var dz = root.querySelector('.u432-dz');
    dz.appendChild(input);

    state.rootEl = root;
    state.imgEl = root.querySelector('.u432-preview-img');
    state.nameEl = root.querySelector('.u432-nm');
    state.restoreBtn = root.querySelector('.u432-undo');
    state.statusEl = root.querySelector('.file-drop-status');

    dz.addEventListener('click', function (e) {
      if (e.target.closest('.u432-undo') || e.target === input) return;
      input.click();
    });
    dz.addEventListener('keydown', function (e) {
      if (e.target.closest('.u432-undo')) return;
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        input.click();
      }
    });
    state.restoreBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var keyboard = e.detail === 0;
      state.files = [];
      syncInputFiles(input, []);
      if (input.value) {
        try { input.value = ''; } catch (_e) {}
      }
      setStatus(state.originalSrc ? '원래 이미지로 되돌렸습니다.' : '선택을 취소했습니다.', false);
      updateView();
      dispatchChange();
      if (keyboard) dz.focus();
    });

    input.addEventListener('change', function () {
      if (state._internalDispatch) return;
      var chosen = Array.prototype.slice.call(input.files || []);
      if (!chosen.length) {
        state.files = [];
        updateView();
        input.dispatchEvent(new CustomEvent('file-picker:change', {
          bubbles: true,
          detail: { files: [], mode: 'single' }
        }));
        return;
      }
      applyIncoming(chosen, true);
    });

    var depth = 0;
    dz.addEventListener('dragenter', function (e) {
      e.preventDefault();
      depth++;
      root.classList.add('is-drag');
    });
    dz.addEventListener('dragover', function (e) {
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
    });
    dz.addEventListener('dragleave', function () {
      depth = Math.max(0, depth - 1);
      if (!depth) root.classList.remove('is-drag');
    });
    dz.addEventListener('drop', function (e) {
      e.preventDefault();
      depth = 0;
      root.classList.remove('is-drag');
      if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
        applyIncoming(e.dataTransfer.files, true);
      }
    });

    state.setOriginal = function (url) {
      state.originalSrc = url || '';
      state.files = [];
      syncInputFiles(input, []);
      try { input.value = ''; } catch (_e) {}
      setStatus('', false);
      updateView();
    };

    state.add = function (files) { applyIncoming(files, true); };

    state.reset = function () {
      state.files = [];
      syncInputFiles(input, []);
      try { input.value = ''; } catch (_e) {}
      setStatus('', false);
      updateView();
      dispatchChange();
    };

    updateView();
    return state;
  }

  function urlName(url) {
    var clean = String(url || '').split('#')[0].split('?')[0];
    var last = clean.substring(clean.lastIndexOf('/') + 1);
    try { last = decodeURIComponent(last); } catch (_e) {}
    return last || '저장된 사진';
  }

  function normalizeExisting(items) {
    var list = Array.isArray(items) ? items : Array.prototype.slice.call(items || []);
    return list
      .map(function (item) {
        if (!item) return null;
        if (typeof item === 'string') return { url: item, name: urlName(item) };
        if (!item.url) return null;
        return { url: String(item.url), name: item.name || urlName(item.url) };
      })
      .filter(Boolean);
  }

  var TRASH_SVG = '<svg viewBox="0 0 18 18" aria-hidden="true"><path class="lid" d="M2.5 4.5h13M7 4.5V3a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v1.5"/><path d="M4 6.5l.8 8.2a1.5 1.5 0 0 0 1.5 1.3h5.4a1.5 1.5 0 0 0 1.5-1.3L14 6.5M7.5 9v4M10.5 9v4"/></svg>';

  function initMulti(container, input) {
    var labelText = container.dataset.fileLabel || '파일';
    var maxFiles = Number(container.dataset.maxFiles || '0');
    var immediate = container.dataset.uploadMode === 'immediate';
    var noList = container.dataset.fileList === 'none';
    var sortable = container.hasAttribute('data-sortable') && !noList;
    var withPrimary = container.hasAttribute('data-primary') && !noList;
    var selectable = container.hasAttribute('data-selectable') && !noList;
    var C = 2 * Math.PI * 10;

    var state = {
      mode: 'multi',
      container: container,
      input: input,
      files: [],
      existing: [],
      existingOriginal: [],
      pendingUndo: null,
      undoTimerRaf: 0,
      undoLastTime: 0,
      undoLeftMs: UNDO_LIFE_MS,
      undoPaused: false,
      immediate: immediate,
      order: [],
      primaryKey: '',
      selectedKey: '',
      uploadTasks: new Map()
    };
    stateMap.set(input, state);

    input.classList.add('file-drop-input');
    input.tabIndex = -1;
    input.setAttribute('aria-hidden', 'true');

    var root = document.createElement('div');
    root.className = 'ux u032';
    var maxHint = maxFiles > 0 ? ' · 최대 ' + maxFiles + '개' : '';
    var emptyText = (immediate ? '올린 ' : '선택한 ') + labelText + ' 없음';
    var listMarkup =
      '<div class="u470' + (sortable ? ' is-sortable' : '') + '">' +
      '  <div class="u470-cd">' +
      '    <ul class="u470-ls" aria-label="' + labelText + ' 목록"></ul>' +
      '    <div class="u470-em"><p>' + emptyText + '</p></div>' +
      '  </div>' +
      '  <button type="button" class="u470-undo" tabindex="-1" aria-hidden="true">' +
      '    <span class="u470-rg" aria-hidden="true"><svg viewBox="0 0 24 24"><circle class="bg" cx="12" cy="12" r="10"/><circle class="fg" cx="12" cy="12" r="10"/></svg><b>5</b></span>' +
      '    <span>되돌리기</span>' +
      '    <svg class="u470-ar" viewBox="0 0 16 16" aria-hidden="true"><path d="M4.5 3.5 2 6l2.5 2.5"/><path d="M2.5 6h7a4 4 0 0 1 0 8H7"/></svg>' +
      '  </button>' +
      '</div>';
    root.innerHTML =
      '<button class="u032-zone" type="button">' +
      '  <svg class="u032-dash" aria-hidden="true"><rect width="100%" height="100%" rx="25"/></svg>' +
      '  <span class="u032-orb" aria-hidden="true"><svg viewBox="0 0 28 28"><path d="M8.5 20.5H7a5 5 0 0 1-.6-10 7 7 0 0 1 13.4-1.6A5.5 5.5 0 0 1 21 20.5h-1.5M14 13v10M10.5 16.5L14 13l3.5 3.5"/></svg></span>' +
      '  <b>' + labelText + ' 끌어다 놓거나 <em>골라 주세요</em></b>' +
      '  <small>' + (immediate ? '놓는 즉시 올라갑니다' : '여러 장을 추가하면 목록에 누적됩니다') + maxHint + '</small>' +
      '</button>' +
      '<p class="file-drop-status" role="status"></p>' +
      (immediate ? '<ul class="u032-list" aria-live="polite"></ul>' : '') +
      (noList ? '' : listMarkup);

    container.insertBefore(root, input);
    root.appendChild(input);

    var zone = root.querySelector('.u032-zone');
    var statusEl = root.querySelector('.file-drop-status');
    var inflightEl = immediate ? root.querySelector('.u032-list') : null;
    var listEl = root.querySelector('.u470-ls');
    var emptyEl = root.querySelector('.u470-em');
    var undoBtn = root.querySelector('.u470-undo');
    var undoFg = root.querySelector('.u470-rg .fg');
    var undoNum = root.querySelector('.u470-rg b');

    function setStatus(text, isError) {
      statusEl.textContent = text || '';
      statusEl.classList.toggle('is-error', !!isError);
    }

    // 즉시 업로드 칸은 올라간 파일이 '기존 항목'으로 옮겨 가므로, 목록에는 기존 항목만 센다.
    function listedCount() {
      return state.existing.length + (immediate ? 0 : state.files.length);
    }

    function syncEmpty() {
      if (emptyEl) emptyEl.classList.toggle('is-on', listedCount() === 0 && !(inflightEl && inflightEl.children.length));
    }

    function existingKey(item) { return 'e:' + item.url; }
    function fileOrderKey(file) { return 'f:' + fileKey(file); }

    // 목록 순서(state.order)를 기준으로 기존 항목·새 파일 배열을 다시 줄 세운다.
    // 즉시 업로드 칸의 새 파일은 목록이 아니라 진행 막대에 있으므로 순서에 넣지 않는다.
    function normalizeOrder() {
      var listedFiles = immediate ? [] : state.files;
      var present = {};
      state.existing.forEach(function (it) { present[existingKey(it)] = it; });
      listedFiles.forEach(function (f) { present[fileOrderKey(f)] = f; });
      var next = state.order.filter(function (k) { return present[k]; });
      Object.keys(present).forEach(function (k) { if (next.indexOf(k) < 0) next.push(k); });
      state.order = next;
      var rank = {};
      next.forEach(function (k, i) { rank[k] = i; });
      state.existing.sort(function (x, y) { return rank[existingKey(x)] - rank[existingKey(y)]; });
      if (!immediate) {
        state.files.sort(function (x, y) { return rank[fileOrderKey(x)] - rank[fileOrderKey(y)]; });
      }
    }

    function orderTokens() {
      return state.order.map(function (k) { return k.charAt(0) === 'e' ? k.slice(2) : ''; });
    }

    // 목록 순서대로 항목을 돌려준다. 저장된 항목은 { url, name }, 새 파일은 File 그대로.
    function orderedItems() {
      var byKey = {};
      state.existing.forEach(function (it) { byKey[existingKey(it)] = { url: it.url, name: it.name }; });
      if (!immediate) state.files.forEach(function (f) { byKey[fileOrderKey(f)] = f; });
      return state.order.map(function (k) { return byKey[k]; }).filter(Boolean);
    }

    function primaryIndex() {
      if (!withPrimary || !state.order.length) return -1;
      var i = state.order.indexOf(state.primaryKey);
      return i < 0 ? 0 : i;
    }

    function syncMarks() {
      if (!listEl) return;
      var pIndex = primaryIndex();
      var pKey = pIndex >= 0 ? state.order[pIndex] : '';
      Array.prototype.forEach.call(listEl.children, function (row) {
        var key = row.dataset.orderKey;
        row.classList.toggle('is-active', selectable && key === state.selectedKey);
        var pri = row.querySelector('.u470-pri');
        if (pri) {
          var on = key === pKey;
          pri.setAttribute('aria-pressed', String(on));
          pri.textContent = on ? '대표' : '대표로';
        }
      });
    }

    function dispatchChange(added) {
      var wasDispatching = state._internalDispatch;
      state._internalDispatch = true;
      input.dispatchEvent(new CustomEvent('file-picker:change', {
        bubbles: true,
        detail: {
          files: state.files.slice(),
          mode: 'multi',
          added: (added || []).slice(),
          existing: state.existing.map(function (e) { return e.url; }),
          order: orderTokens(),
          items: orderedItems(),
          primary: primaryIndex()
        }
      }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      state._internalDispatch = wasDispatching;
    }

    function showUndoPill(on, keyboard) {
      if (!undoBtn) return;
      undoBtn.classList.toggle('is-on', on);
      undoBtn.tabIndex = on ? 0 : -1;
      if (on) undoBtn.removeAttribute('aria-hidden');
      else undoBtn.setAttribute('aria-hidden', 'true');
      if (on && keyboard) undoBtn.focus();
    }

    function commitUndo() {
      if (!state.pendingUndo) return;
      if (state.pendingUndo.rowEl && state.pendingUndo.rowEl.parentNode) {
        state.pendingUndo.rowEl.parentNode.removeChild(state.pendingUndo.rowEl);
      }
      state.pendingUndo = null;
      if (state.undoTimerRaf) {
        cancelAnimationFrame(state.undoTimerRaf);
        state.undoTimerRaf = 0;
      }
      showUndoPill(false, false);
      syncEmpty();
    }

    function tickUndo(now) {
      state.undoTimerRaf = 0;
      if (!root.isConnected || !state.pendingUndo) return;
      if (!state.undoPaused) {
        state.undoLeftMs -= Math.min(100, now - state.undoLastTime);
      }
      state.undoLastTime = now;
      var frac = Math.max(0, state.undoLeftMs / UNDO_LIFE_MS);
      if (undoFg) undoFg.style.strokeDasharray = (C * frac).toFixed(2) + ' ' + C.toFixed(2);
      if (undoNum) undoNum.textContent = String(Math.max(1, Math.ceil(state.undoLeftMs / 1000)));
      if (state.undoLeftMs <= 0) {
        commitUndo();
        return;
      }
      state.undoTimerRaf = requestAnimationFrame(tickUndo);
    }

    function buildRow(thumbSrc, name, meta, fallbackExt) {
      var li = document.createElement('li');
      li.className = 'u470-r';
      var wrap = document.createElement('div');
      var inner = document.createElement('div');
      inner.className = 'u470-in';
      if (thumbSrc) {
        var img = document.createElement('img');
        img.className = 'u470-thumb';
        img.alt = '';
        img.loading = 'lazy';
        img.decoding = 'async';
        img.src = thumbSrc;
        inner.appendChild(img);
      } else {
        var ic = document.createElement('i');
        ic.className = 'u470-ic';
        ic.style.setProperty('--u470-c', TYPE_COLOR[fallbackExt] || '#6B7280');
        ic.setAttribute('aria-hidden', 'true');
        ic.textContent = (fallbackExt || 'file').slice(0, 3).toUpperCase();
        inner.appendChild(ic);
      }
      var tx = document.createElement('span');
      tx.className = 'u470-tx';
      var b = document.createElement('b');
      b.textContent = name;
      var sm = document.createElement('small');
      sm.textContent = meta;
      tx.appendChild(b);
      tx.appendChild(sm);
      inner.appendChild(tx);
      if (sortable) {
        var grip = document.createElement('button');
        grip.type = 'button';
        grip.className = 'u470-grip';
        grip.setAttribute('aria-label', name + ' 순서 이동 (끌거나 위·아래 화살표)');
        grip.innerHTML = '<svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="6" cy="4" r="1.2"/><circle cx="10" cy="4" r="1.2"/><circle cx="6" cy="8" r="1.2"/><circle cx="10" cy="8" r="1.2"/><circle cx="6" cy="12" r="1.2"/><circle cx="10" cy="12" r="1.2"/></svg>';
        inner.insertBefore(grip, inner.firstChild);
        var up = document.createElement('button');
        up.type = 'button';
        up.className = 'u470-mv';
        up.dataset.dir = '-1';
        up.setAttribute('aria-label', name + ' 위로');
        up.innerHTML = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 10l4-4 4 4"/></svg>';
        var down = document.createElement('button');
        down.type = 'button';
        down.className = 'u470-mv';
        down.dataset.dir = '1';
        down.setAttribute('aria-label', name + ' 아래로');
        down.innerHTML = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 6l4 4 4-4"/></svg>';
        inner.appendChild(up);
        inner.appendChild(down);
      }
      if (withPrimary) {
        var pri = document.createElement('button');
        pri.type = 'button';
        pri.className = 'u470-pri';
        pri.setAttribute('aria-pressed', 'false');
        pri.setAttribute('aria-label', name + ' 대표로 지정');
        pri.textContent = '대표로';
        inner.appendChild(pri);
      }
      if (selectable) {
        tx.classList.add('u470-pick');
      }
      var del = document.createElement('button');
      del.type = 'button';
      del.className = 'u470-del';
      del.setAttribute('aria-label', name + ' 삭제');
      del.innerHTML = TRASH_SVG;
      inner.appendChild(del);
      wrap.appendChild(inner);
      li.appendChild(wrap);
      return li;
    }

    function createFileRow(file) {
      var isImage = String(file.type || '').startsWith('image/') && typeof URL !== 'undefined' && URL.createObjectURL;
      var url = isImage ? URL.createObjectURL(file) : '';
      var li = buildRow(url, file.name, formatSize(file.size), getExt(file.name));
      li.dataset.fileKey = fileKey(file);
      li.dataset.orderKey = fileOrderKey(file);
      if (url) {
        li.querySelector('img').addEventListener('load', function () {
          try { URL.revokeObjectURL(url); } catch (_e) {}
        }, { once: true });
      }
      return li;
    }

    function createExistingRow(item) {
      var li = buildRow(item.url, item.name, '저장됨', getExt(item.name));
      li.dataset.existingUrl = item.url;
      li.dataset.orderKey = existingKey(item);
      return li;
    }

    function renderList() {
      normalizeOrder();
      if (!listEl) return;
      listEl.innerHTML = '';
      var byKey = {};
      state.existing.forEach(function (it) { byKey[existingKey(it)] = createExistingRow(it); });
      if (!immediate) state.files.forEach(function (f) { byKey[fileOrderKey(f)] = createFileRow(f); });
      state.order.forEach(function (k) { if (byKey[k]) listEl.appendChild(byKey[k]); });
      syncMarks();
      syncEmpty();
    }

    function renderImmediateRow(file) {
      var key = fileKey(file);
      var ext = getExt(file.name);
      var li = document.createElement('li');
      li.className = 'u032-row';
      li.dataset.fileKey = key;
      li.innerHTML =
        '<span class="u032-ic" style="--c:' + (TYPE_COLOR[ext] || '#8E8A97') + '" aria-hidden="true">' + (ext.slice(0, 3).toUpperCase() || 'FILE') + '</span>' +
        '<span class="u032-meta"><b></b><small></small></span>' +
        '<span class="u032-st"><span class="u032-spin" aria-hidden="true"></span></span>' +
        '<span class="u032-bar" aria-hidden="true"></span>';
      li.querySelector('b').textContent = file.name;
      li.querySelector('small').textContent = formatSize(file.size) + ' · 대기 중';
      inflightEl.insertBefore(li, inflightEl.firstChild);
      state.uploadTasks.set(key, li);
      syncEmpty();
      return li;
    }

    function applyIncoming(incoming, shouldDispatch) {
      commitUndo();
      var res = reconcileSelection(state.files, incoming, {
        mode: 'multi',
        accept: input.getAttribute('accept') || '',
        maxFiles: maxFiles,
        reserved: state.existing.length
      });
      var msg = buildStatusMessage(res, maxFiles);
      setStatus(msg.text, msg.isError);
      state.files = res.files;
      syncInputFiles(input, state.files);

      if (res.added.length) {
        if (immediate) {
          res.added.forEach(renderImmediateRow);
        } else {
          normalizeOrder();
          syncInputFiles(input, state.files);
          if (listEl) res.added.forEach(function (file) { listEl.appendChild(createFileRow(file)); });
          syncEmpty();
        }
        if (shouldDispatch) dispatchChange(res.added);
      }
    }

    zone.addEventListener('click', function () {
      input.click();
    });

    input.addEventListener('change', function () {
      if (state._internalDispatch) return;
      var chosen = Array.prototype.slice.call(input.files || []);
      if (!chosen.length) {
        syncInputFiles(input, state.files);
        return;
      }
      applyIncoming(chosen, true);
    });

    var depth = 0;
    zone.addEventListener('dragenter', function (e) {
      e.preventDefault();
      depth++;
      root.classList.add('is-over');
    });
    zone.addEventListener('dragover', function (e) {
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
    });
    zone.addEventListener('dragleave', function () {
      depth = Math.max(0, depth - 1);
      if (!depth) root.classList.remove('is-over');
    });
    zone.addEventListener('drop', function (e) {
      e.preventDefault();
      depth = 0;
      root.classList.remove('is-over');
      if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
        applyIncoming(e.dataTransfer.files, true);
      }
    });

    function moveKey(key, toIndex, focusSelector) {
      var from = state.order.indexOf(key);
      if (from < 0) return;
      var to = Math.max(0, Math.min(state.order.length - 1, toIndex));
      if (to === from) return;
      commitUndo();
      state.order.splice(from, 1);
      state.order.splice(to, 0, key);
      normalizeOrder();
      syncInputFiles(input, state.files);
      renderList();
      setStatus('순서를 바꿨습니다. (' + (to + 1) + '번째)', false);
      dispatchChange();
      if (focusSelector) {
        var row = listEl.querySelector('[data-order-key="' + (window.CSS && CSS.escape ? CSS.escape(key) : key) + '"]');
        var target = row && row.querySelector(focusSelector);
        if (target) target.focus();
      }
    }

    function bindSortDrag() {
      if (!sortable || !listEl) return;
      listEl.addEventListener('pointerdown', function (e) {
        var grip = e.target.closest('.u470-grip');
        if (!grip || (e.button != null && e.button !== 0)) return;
        var row = grip.closest('.u470-r');
        if (!row) return;
        e.preventDefault();
        var pointerId = e.pointerId;
        var key = row.dataset.orderKey;
        row.classList.add('is-dragging');
        // 줄을 DOM에서 옮기면 포인터 캡처가 풀리므로, 이동·놓기는 창 전체에서 받는다.
        function onMove(ev) {
          if (ev.pointerId !== pointerId) return;
          ev.preventDefault();
          var rows = Array.prototype.filter.call(listEl.children, function (r) { return r !== row && !r.classList.contains('is-gone'); });
          var before = null;
          for (var i = 0; i < rows.length; i++) {
            var rect = rows[i].getBoundingClientRect();
            if (ev.clientY < rect.top + rect.height / 2) { before = rows[i]; break; }
          }
          if (before) { if (row.nextSibling !== before) listEl.insertBefore(row, before); }
          else if (listEl.lastChild !== row) listEl.appendChild(row);
        }
        function onUp(ev) {
          if (ev.pointerId !== pointerId) return;
          window.removeEventListener('pointermove', onMove, true);
          window.removeEventListener('pointerup', onUp, true);
          window.removeEventListener('pointercancel', onUp, true);
          row.classList.remove('is-dragging');
          var keys = Array.prototype.map.call(listEl.children, function (r) { return r.dataset.orderKey; })
            .filter(function (k) { return k && state.order.indexOf(k) >= 0; });
          var to = keys.indexOf(key);
          if (to === state.order.indexOf(key)) {
            renderList();
            return;
          }
          moveKey(key, to, '.u470-grip');
        }
        window.addEventListener('pointermove', onMove, true);
        window.addEventListener('pointerup', onUp, true);
        window.addEventListener('pointercancel', onUp, true);
      });
      listEl.addEventListener('keydown', function (e) {
        var grip = e.target.closest('.u470-grip');
        if (!grip || (e.key !== 'ArrowUp' && e.key !== 'ArrowDown')) return;
        e.preventDefault();
        var key = grip.closest('.u470-r').dataset.orderKey;
        moveKey(key, state.order.indexOf(key) + (e.key === 'ArrowUp' ? -1 : 1), '.u470-grip');
      });
    }
    bindSortDrag();

    if (listEl) listEl.addEventListener('click', function (e) {
      var pri = e.target.closest('.u470-pri');
      if (pri) {
        var prow = pri.closest('.u470-r');
        if (prow && prow.dataset.orderKey) {
          state.primaryKey = prow.dataset.orderKey;
          syncMarks();
          setStatus('대표 사진을 바꿨습니다.', false);
          dispatchChange();
        }
        return;
      }
      if (selectable && !e.target.closest('button')) {
        var srow = e.target.closest('.u470-r');
        if (srow && srow.dataset.orderKey && !srow.classList.contains('is-gone')) {
          state.selectedKey = srow.dataset.orderKey;
          syncMarks();
          var sIndex = state.order.indexOf(state.selectedKey);
          input.dispatchEvent(new CustomEvent('file-picker:select', {
            bubbles: true,
            detail: { index: sIndex, item: orderedItems()[sIndex] }
          }));
        }
        return;
      }
      var mv = e.target.closest('.u470-mv');
      if (mv) {
        var mrow = mv.closest('.u470-r');
        var mkey = mrow && mrow.dataset.orderKey;
        if (mkey) moveKey(mkey, state.order.indexOf(mkey) + Number(mv.dataset.dir), '.u470-mv[data-dir="' + mv.dataset.dir + '"]');
        return;
      }
      var delBtn = e.target.closest('.u470-del');
      if (!delBtn) return;
      var row = delBtn.closest('.u470-r');
      if (!row || row.classList.contains('is-gone')) return;

      if (state.pendingUndo) commitUndo();

      var pending;
      var removedName;
      if (row.dataset.existingUrl) {
        var url = row.dataset.existingUrl;
        var ei = state.existing.findIndex(function (it) { return it.url === url; });
        if (ei < 0) return;
        var item = state.existing.splice(ei, 1)[0];
        pending = { existing: item, index: ei };
        removedName = item.name;
      } else {
        var key = row.dataset.fileKey;
        var idx = state.files.findIndex(function (f) { return fileKey(f) === key; });
        if (idx < 0) return;
        var res = removeFileAt(state.files, idx, null);
        state.files = res.files;
        syncInputFiles(input, state.files);
        pending = res.pendingUndo;
        removedName = res.removed.name;
      }

      var keyboard = e.detail === 0;
      row.classList.remove('is-back');
      row.classList.add('is-gone');
      delBtn.tabIndex = -1;

      var orderKey = row.dataset.orderKey;
      var orderIndex = state.order.indexOf(orderKey);
      normalizeOrder();
      state.pendingUndo = Object.assign({}, pending, { rowEl: row, orderKey: orderKey, orderIndex: orderIndex });
      state.undoPaused = false;
      state.undoLeftMs = UNDO_LIFE_MS;
      state.undoLastTime = performance.now();
      if (undoFg) undoFg.style.strokeDasharray = C.toFixed(2) + ' ' + C.toFixed(2);
      if (undoNum) undoNum.textContent = '5';
      showUndoPill(true, keyboard);
      setStatus(removedName + ' 삭제됨. 5초 안에 되돌릴 수 있어요.', false);
      syncEmpty();
      state.undoTimerRaf = requestAnimationFrame(tickUndo);

      dispatchChange();
    });

    if (undoBtn) undoBtn.addEventListener('click', function (e) {
      if (!state.pendingUndo) return;
      var pending = state.pendingUndo;
      var row = pending.rowEl;
      var keyboard = e.detail === 0;
      state.pendingUndo = null;
      if (state.undoTimerRaf) {
        cancelAnimationFrame(state.undoTimerRaf);
        state.undoTimerRaf = 0;
      }
      var restoredName;
      if (pending.existing) {
        var at = Math.max(0, Math.min(pending.index, state.existing.length));
        state.existing.splice(at, 0, pending.existing);
        restoredName = pending.existing.name;
      } else {
        var res = undoRemove(state.files, pending);
        state.files = res.files;
        syncInputFiles(input, state.files);
        restoredName = pending.file ? pending.file.name : '파일';
      }
      if (pending.orderKey && pending.orderIndex >= 0 && state.order.indexOf(pending.orderKey) < 0) {
        state.order.splice(Math.min(pending.orderIndex, state.order.length), 0, pending.orderKey);
      }
      normalizeOrder();
      syncInputFiles(input, state.files);

      if (row) {
        row.classList.add('is-back');
        row.classList.remove('is-gone');
        var del = row.querySelector('.u470-del');
        if (del) {
          del.tabIndex = 0;
          if (keyboard) del.focus();
        }
      }
      syncEmpty();
      showUndoPill(false, false);
      setStatus(restoredName + ' 되돌림.', false);
      dispatchChange();
    });

    if (undoBtn) {
      undoBtn.addEventListener('pointerenter', function () { state.undoPaused = true; });
      undoBtn.addEventListener('pointerleave', function () { state.undoPaused = false; });
      undoBtn.addEventListener('focus', function () { state.undoPaused = true; });
      undoBtn.addEventListener('blur', function () { state.undoPaused = false; });
    }

    function finishUpload(file, url) {
      var key = fileKey(file);
      var li = state.uploadTasks.get(key);
      state.uploadTasks.delete(key);
      if (li && li.parentNode) li.parentNode.removeChild(li);
      state.files = state.files.filter(function (f) { return fileKey(f) !== key; });
      syncInputFiles(input, state.files);
      state.existing.push({ url: String(url), name: file.name });
      normalizeOrder();
      if (listEl) listEl.appendChild(createExistingRow(state.existing[state.existing.length - 1]));
      syncEmpty();
      dispatchChange();
    }

    state.setProgress = function (file, opts) {
      if (opts && opts.status === 'done' && opts.url) {
        finishUpload(file, opts.url);
        return;
      }
      var key = fileKey(file);
      var li = state.uploadTasks.get(key);
      if (!li) return;
      var status = (opts && opts.status) || 'uploading';
      var ratio = opts && typeof opts.ratio === 'number' ? Math.max(0, Math.min(1, opts.ratio)) : null;
      var msg = opts && opts.message ? opts.message : '';
      var sm = li.querySelector('small');
      var st = li.querySelector('.u032-st');

      li.classList.remove('done', 'fail');
      if (status === 'done') {
        li.classList.add('done');
        li.style.setProperty('--p', '1');
        if (sm) sm.textContent = formatSize(file.size) + ' · 올렸어요';
        if (st) st.innerHTML = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3.5 8.4l3 3 6-6.4"/></svg>';
      } else if (status === 'fail') {
        li.classList.add('fail');
        if (sm) sm.textContent = msg || '업로드에 실패했어요';
        if (st) st.textContent = '!';
        // 실패한 파일은 선택에서 빼서 같은 파일로 다시 시도할 수 있게 한다.
        state.files = state.files.filter(function (f) { return fileKey(f) !== key; });
        state.uploadTasks.delete(key);
        syncInputFiles(input, state.files);
      } else if (ratio !== null) {
        li.style.setProperty('--p', ratio.toFixed(3));
        if (sm) sm.textContent = formatSize(file.size * ratio) + ' / ' + formatSize(file.size) + ' · ' + Math.round(ratio * 100) + '%';
      } else if (sm) {
        sm.textContent = formatSize(file.size) + ' · 업로드 중';
      }
    };

    state.setFiles = function (files, options) {
      commitUndo();
      var list = Array.isArray(files) ? files.slice() : Array.prototype.slice.call(files || []);
      state.files = list;
      syncInputFiles(input, state.files);
      state.uploadTasks.clear();
      if (inflightEl) {
        inflightEl.innerHTML = '';
        state.files.forEach(renderImmediateRow);
      }
      renderList();
      setStatus('', false);
      if (!options || !options.silent) dispatchChange(list);
    };

    // 화면 코드가 가진 목록(저장된 항목·새 파일이 섞인 순서)을 그대로 넣는다. 같은 목록이면 다시 그리지 않는다.
    state.setItems = function (items, options) {
      var list = Array.isArray(items) ? items : Array.prototype.slice.call(items || []);
      var nextExisting = [];
      var nextFiles = [];
      var nextOrder = [];
      list.forEach(function (it) {
        if (!it) return;
        if (typeof File !== 'undefined' && it instanceof File) {
          nextFiles.push(it);
          nextOrder.push(fileOrderKey(it));
        } else {
          var ex = normalizeExisting([it])[0];
          if (!ex) return;
          nextExisting.push(ex);
          nextOrder.push(existingKey(ex));
        }
      });
      var opts = options || {};
      var same = nextOrder.join('\n') === state.order.join('\n');
      if (!same) {
        commitUndo();
        state.existing = nextExisting;
        state.existingOriginal = nextExisting.slice();
        state.files = nextFiles;
        state.order = nextOrder;
        syncInputFiles(input, state.files);
        renderList();
      } else {
        // 같은 키라도 파일 객체가 바뀌었을 수 있으니(편집 결과) 입력만 맞춘다.
        state.files = nextFiles;
        syncInputFiles(input, state.files);
      }
      if (typeof opts.primary === 'number' && opts.primary >= 0 && opts.primary < state.order.length) {
        state.primaryKey = state.order[opts.primary];
      }
      if (typeof opts.selected === 'number') {
        state.selectedKey = state.order[opts.selected] || '';
      }
      syncMarks();
    };

    state.add = function (files) { applyIncoming(files, true); };

    state.select = function (index) {
      state.selectedKey = state.order[index] || '';
      syncMarks();
    };

    state.items = function () {
      return { items: orderedItems(), primary: primaryIndex() };
    };

    state.setExisting = function (items) {
      commitUndo();
      state.existing = normalizeExisting(items);
      state.existingOriginal = state.existing.slice();
      state.order = [];
      renderList();
    };

    state.reset = function () {
      commitUndo();
      state.files = [];
      state.existing = state.existingOriginal.slice();
      state.order = [];
      syncInputFiles(input, []);
      try { input.value = ''; } catch (_e) {}
      if (inflightEl) inflightEl.innerHTML = '';
      state.uploadTasks.clear();
      renderList();
      setStatus('', false);
      dispatchChange();
    };

    renderList();
    return state;
  }

  function init(root) {
    var scope = root && typeof root.querySelectorAll === 'function' ? root : document;
    if (!scope || typeof scope.querySelectorAll !== 'function') return;
    var containers = scope.querySelectorAll('[data-file-drop]');
    Array.prototype.forEach.call(containers, function (container) {
      if (container.dataset.fileDropReady === 'true') return;
      var input = container.querySelector('input[type="file"]');
      if (!input) return;
      container.dataset.fileDropReady = 'true';
      var mode = container.dataset.fileDrop === 'multi' ? 'multi' : 'single';
      if (mode === 'multi') initMulti(container, input);
      else initSingle(container, input);
    });
  }

  function files(input) {
    var st = getState(input);
    if (st) return st.files.slice();
    return input && input.files ? Array.prototype.slice.call(input.files) : [];
  }

  function setOriginal(input, url) {
    var st = getState(input);
    if (st && typeof st.setOriginal === 'function') {
      st.setOriginal(url);
      return;
    }
    if (input) {
      var container = input.closest && input.closest('[data-file-drop]');
      if (container) container.dataset.originalSrc = url || '';
    }
  }

  function reset(input) {
    var st = getState(input);
    if (st && typeof st.reset === 'function') {
      st.reset();
      return;
    }
    if (input) {
      try { input.value = ''; } catch (_e) {}
    }
  }

  function setFiles(input, files, options) {
    var st = getState(input);
    if (st && typeof st.setFiles === 'function') {
      st.setFiles(files, options);
      return;
    }
    var list = Array.isArray(files) ? files.slice() : Array.prototype.slice.call(files || []);
    syncInputFiles(input, list);
    if (input && (!options || !options.silent)) {
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }

  function setExisting(input, items) {
    var st = getState(input);
    if (st && typeof st.setExisting === 'function') st.setExisting(items);
  }

  function setItems(input, items, options) {
    var st = getState(input);
    if (st && typeof st.setItems === 'function') st.setItems(items, options);
  }

  function add(input, files) {
    var st = getState(input);
    if (st && typeof st.add === 'function') {
      st.add(files);
      return true;
    }
    return false;
  }

  function select(input, index) {
    var st = getState(input);
    if (st && typeof st.select === 'function') st.select(index);
  }

  function existing(input) {
    var st = getState(input);
    return st && st.existing ? st.existing.map(function (e) { return e.url; }) : [];
  }

  function setProgress(input, file, opts) {
    var st = getState(input);
    if (st && typeof st.setProgress === 'function') {
      st.setProgress(file, opts);
    }
  }

  var FilePicker = {
    init: init,
    files: files,
    setFiles: setFiles,
    setOriginal: setOriginal,
    reset: reset,
    setProgress: setProgress,
    setExisting: setExisting,
    setItems: setItems,
    select: select,
    add: add,
    existing: existing,
    _logic: {
      fileKey: fileKey,
      matchesAccept: matchesAccept,
      mergeFiles: mergeFiles,
      reconcileSelection: reconcileSelection,
      removeFileAt: removeFileAt,
      undoRemove: undoRemove
    }
  };

  if (global) {
    global.FilePicker = FilePicker;
  }

  if (typeof document !== 'undefined' && document.addEventListener) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () { init(document); });
    } else {
      init(document);
    }
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = FilePicker;
  }
})(typeof window !== 'undefined' ? window : globalThis);
