/**
 * 게시글 에디터 본문 HTML 정리 (작성/수정 공통).
 * - 편집용 삭제 버튼·래퍼 제거
 * - /uploads/ 미디어 src를 상대 경로로 정규화
 */
(function (global) {
  function toRelativeUploadUrl(src) {
    if (!src) return src;
    const s = String(src).trim().replace(/^"(.*)"$/, '$1');
    const idx = s.indexOf('/uploads/');
    return idx >= 0 ? s.substring(idx) : s;
  }

  function cleanEditorHtml(html) {
    const wrap = document.createElement('div');
    wrap.innerHTML = html || '';

    wrap.querySelectorAll('.remove-btn').forEach((el) => el.remove());

    wrap.querySelectorAll('.content-media').forEach((box) => {
      const parent = box.parentNode;
      if (!parent) return;
      while (box.firstChild) parent.insertBefore(box.firstChild, box);
      parent.removeChild(box);
    });

    wrap.querySelectorAll('img[src], video[src], source[src]').forEach((el) => {
      const src = el.getAttribute('src');
      if (!src) return;
      el.setAttribute('src', toRelativeUploadUrl(src));
      delete el.dataset.savedName;
      delete el.dataset.fileId;
      el.removeAttribute('data-saved-name');
      el.removeAttribute('data-file-id');
    });

    return wrap.innerHTML.trim();
  }

  function hasVisibleContent(html) {
    const box = document.createElement('div');
    box.innerHTML = html || '';
    const text = (box.textContent || '').replace(/\u00a0/g, '').replace(/\s/g, '');
    const media = [...box.querySelectorAll('[src]')].some((node) => (node.getAttribute('src') || '').trim());
    return text.length > 0 || media;
  }

  global.articleHasVisibleContent = hasVisibleContent;
  global.ArticleContent = {
    toRelativeUploadUrl,
    cleanEditorHtml,
    hasVisibleContent
  };
})(window);
