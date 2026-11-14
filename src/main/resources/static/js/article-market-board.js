/**
 * 교환/거래 게시판: 필터 바인딩 + 탭 소프트 전환(fetch + pushState).
 */
(function () {
  var MARKET_PATH = /^\/(trade|sell)\/?$/;
  var abortController = null;
  var outsideClickBound = false;

  function applyMarketFilterParam(key, value) {
    var url = new URL(window.location.href);
    if (value) url.searchParams.set(key, value);
    else url.searchParams.delete(key);
    window.location.href = url.pathname + url.search;
  }

  function bindMarketFacet(toggleId, menuId, checkboxClass, paramKey, labelId) {
    var toggle = document.getElementById(toggleId);
    var menu = document.getElementById(menuId);
    var label = document.getElementById(labelId);
    if (!toggle || !menu || !window.ObjektDropdown) return;
    toggle.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      window.ObjektDropdown.toggle(menu);
    });
    menu.addEventListener("change", function (event) {
      var input = event.target;
      if (!(input instanceof HTMLInputElement) || !input.classList.contains(checkboxClass)) return;
      var boxes = menu.querySelectorAll("." + checkboxClass);
      boxes.forEach(function (box) {
        box.checked = box === input;
      });
      if (input.value === "all") {
        if (label) label.textContent = "전체";
        applyMarketFilterParam(paramKey, "");
        return;
      }
      if (label) label.textContent = input.value;
      applyMarketFilterParam(paramKey, input.value);
    });
  }

  function closeMarketMenus(event) {
    if (event.target.closest(".market-filter-bar .objekt-filter")) return;
    ["market-season-menu", "market-class-menu"].forEach(function (id) {
      var menu = document.getElementById(id);
      if (menu && window.ObjektDropdown) window.ObjektDropdown.setOpen(menu, false);
    });
  }

  function initMarketBoard() {
    var picker = document.getElementById("market-member-picker");
    if (picker && window.MemberPortraitPicker) {
      window.MemberPortraitPicker.bindMultiSelect(picker, {
        multi: false,
        onChange: function (values) {
          var url = new URL(window.location.href);
          if (values.length) url.searchParams.set("member", values[0]);
          else url.searchParams.delete("member");
          window.location.href = url.pathname + url.search;
        }
      });
    }
    bindMarketFacet("market-season-toggle", "market-season-menu", "season-checkbox", "season", "market-season-label");
    bindMarketFacet("market-class-toggle", "market-class-menu", "class-checkbox", "class", "market-class-label");
    if (!outsideClickBound) {
      document.addEventListener("click", closeMarketMenus);
      outsideClickBound = true;
    }
  }

  function isMarketBoardPath(pathname) {
    return MARKET_PATH.test(pathname || "");
  }

  function navigateMarket(url, push) {
    if (abortController) abortController.abort();
    abortController = new AbortController();
    var target = typeof url === "string" ? url : url.pathname + url.search;
    return fetch(target, {
      signal: abortController.signal,
      headers: { Accept: "text/html", "X-Requested-With": "XMLHttpRequest" },
      credentials: "same-origin"
    })
      .then(function (res) {
        if (!res.ok) throw new Error("market board fetch failed");
        return res.text();
      })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, "text/html");
        var nextShell = doc.querySelector(".article-board-shell");
        var curShell = document.querySelector(".article-board-shell");
        if (!nextShell || !curShell) throw new Error("market board shell missing");
        curShell.replaceWith(nextShell);
        if (doc.title) document.title = doc.title;
        if (push) history.pushState({ marketBoard: true }, "", target);
        initMarketBoard();
      })
      .catch(function (err) {
        if (err && err.name === "AbortError") return;
        window.location.assign(target);
      });
  }

  function onDocumentClick(event) {
    if (event.defaultPrevented || event.button !== 0) return;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    var link = event.target.closest(".market-tabs a.site-section-tab");
    if (!link) return;
    var url;
    try {
      url = new URL(link.href, window.location.href);
    } catch (_) {
      return;
    }
    if (url.origin !== window.location.origin) return;
    if (!isMarketBoardPath(url.pathname)) return;
    if (url.pathname === window.location.pathname && url.search === window.location.search) {
      event.preventDefault();
      return;
    }
    event.preventDefault();
    navigateMarket(url.pathname + url.search, true);
  }

  function onPopState() {
    if (!isMarketBoardPath(window.location.pathname)) return;
    if (!document.querySelector(".article-board-shell")) return;
    navigateMarket(window.location.pathname + window.location.search, false);
  }

  document.addEventListener("click", onDocumentClick);
  window.addEventListener("popstate", onPopState);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initMarketBoard);
  } else {
    initMarketBoard();
  }
})();
