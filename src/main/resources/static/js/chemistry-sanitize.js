/**
 * 케미 설명 HTML 클라이언트 정제 (서버 ChemistryHtmlCleaner 와 정책 맞춤).
 * chemistry.js 가 window.ChemistrySanitize.sanitize 를 사용한다.
 */
(() => {
  function extractElementColor(el) {
    if (!el || !el.style) return "";
    return (el.style.color || el.getAttribute("color") || "").trim();
  }

  function extractElementBackground(el) {
    if (!el || !el.style) return "";
    return (el.style.backgroundColor || el.getAttribute("bgcolor") || "").trim();
  }

  function applySpanColors(span, color, background) {
    if (color) span.style.color = color;
    if (background) span.style.backgroundColor = background;
  }

  function normalizeFontColorsIn(root) {
    if (!root) return;
    root.querySelectorAll("font").forEach((font) => {
      const span = document.createElement("span");
      applySpanColors(span, extractElementColor(font), extractElementBackground(font));
      while (font.firstChild) span.appendChild(font.firstChild);
      font.replaceWith(span);
    });
  }

  function sanitize(html) {
    const allowed = new Set(["B", "STRONG", "I", "EM", "U", "SPAN", "BR", "A", "DIV", "P", "FONT", "MARK"]);
    const wrap = document.createElement("div");
    wrap.innerHTML = String(html || "");
    normalizeFontColorsIn(wrap);

    function clean(node) {
      Array.from(node.childNodes).forEach((child) => {
        if (child.nodeType === Node.TEXT_NODE) return;
        if (child.nodeType !== Node.ELEMENT_NODE) {
          child.remove();
          return;
        }
        if (child.tagName === "FONT" || child.tagName === "MARK") {
          const span = document.createElement("span");
          const color = extractElementColor(child);
          const background =
            extractElementBackground(child) || (child.tagName === "MARK" ? "#ffe566" : "");
          applySpanColors(span, color, background);
          while (child.firstChild) span.appendChild(child.firstChild);
          node.insertBefore(span, child);
          child.remove();
          clean(span);
          return;
        }
        if (!allowed.has(child.tagName)) {
          while (child.firstChild) node.insertBefore(child.firstChild, child);
          child.remove();
          clean(node);
          return;
        }
        if (child.tagName === "SPAN") {
          const color = extractElementColor(child);
          const background = extractElementBackground(child);
          Array.from(child.attributes).forEach((attr) => child.removeAttribute(attr.name));
          applySpanColors(child, color, background);
        } else if (child.tagName === "A") {
          const href = child.getAttribute("href") || "";
          Array.from(child.attributes).forEach((attr) => child.removeAttribute(attr.name));
          if (/^https?:\/\//i.test(href) && !/^javascript:/i.test(href)) {
            child.setAttribute("href", href);
            child.setAttribute("target", "_blank");
            child.setAttribute("rel", "noopener");
            child.className = "chem-highlight";
          } else {
            while (child.firstChild) node.insertBefore(child.firstChild, child);
            child.remove();
            clean(node);
            return;
          }
        } else {
          Array.from(child.attributes).forEach((attr) => child.removeAttribute(attr.name));
        }
        clean(child);
      });
    }

    clean(wrap);
    return wrap.innerHTML
      .replace(/<\/div>\s*<div>/gi, "<br>")
      .replace(/<\/?div>/gi, "")
      .replace(/<\/p>\s*<p>/gi, "<br>")
      .replace(/<\/?p>/gi, "");
  }

  window.ChemistrySanitize = { sanitize, normalizeFontColorsIn, applySpanColors, extractElementColor, extractElementBackground };
})();
