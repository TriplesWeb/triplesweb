(function (global) {
  let savedRange = null;

  function rememberSelection(editor) {
    const sel = global.getSelection?.();
    if (!sel || !sel.rangeCount || !editor) return;
    const range = sel.getRangeAt(0);
    if (!editor.contains(range.commonAncestorContainer)) return;
    savedRange = range.cloneRange();
  }

  function placeCaretIn(node, atStart) {
    const sel = global.getSelection?.();
    if (!sel || !node) return;
    const range = document.createRange();
    range.selectNodeContents(node);
    range.collapse(!!atStart);
    sel.removeAllRanges();
    sel.addRange(range);
  }

  function continueAfter(mediaNode) {
    if (!mediaNode || !mediaNode.parentNode) return null;
    let next = mediaNode.nextSibling;
    if (!next || next.nodeType !== Node.TEXT_NODE) {
      next = document.createTextNode('\u00a0');
      mediaNode.parentNode.insertBefore(next, mediaNode.nextSibling);
    }
    const sel = global.getSelection?.();
    if (!sel) return next;
    const range = document.createRange();
    range.setStart(next, next.textContent.length);
    range.collapse(true);
    sel.removeAllRanges();
    sel.addRange(range);
    savedRange = range.cloneRange();
    return next;
  }

  function insertNodeAtCaret(container, node) {
    const sel = global.getSelection?.();
    let range = null;
    if (sel && sel.rangeCount && container.contains(sel.getRangeAt(0).commonAncestorContainer)) {
      range = sel.getRangeAt(0);
    } else if (savedRange && container.contains(savedRange.commonAncestorContainer)) {
      range = savedRange;
    }
    if (range) {
      range.deleteContents();
      range.insertNode(node);
      savedRange = null;
      return;
    }
    container.appendChild(node);
  }

  function placeMediaInEditor(editor, wrap) {
    insertNodeAtCaret(editor, wrap);
    return continueAfter(wrap);
  }

  function bindSelectionMemory(editor, chooserRoot) {
    if (!editor) return;
    ['keyup', 'mouseup', 'input'].forEach((type) => {
      editor.addEventListener(type, () => rememberSelection(editor));
    });
    editor.addEventListener('blur', () => rememberSelection(editor));
    chooserRoot?.addEventListener('mousedown', () => rememberSelection(editor));
  }

  global.ArticleEditorMedia = {
    rememberSelection,
    placeCaretIn,
    continueAfter,
    insertNodeAtCaret,
    placeMediaInEditor,
    bindSelectionMemory
  };
})(window);
