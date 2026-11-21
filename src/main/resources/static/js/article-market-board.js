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
        multi: true,
        onChange: function (values) {
          var url = new URL(window.location.href);
          if (values.length) url.searchParams.set("member", values.join(","));
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

  function marketMoreDialog() {
    return document.getElementById("marketFeedMoreDialog");
  }

  function marketObjektDialog() {
    return document.getElementById("marketObjektDialog");
  }

  function closeMarketMore() {
    var dialog = marketMoreDialog();
    if (dialog && typeof dialog.close === "function") dialog.close();
  }

  function closeMarketObjekt() {
    var dialog = marketObjektDialog();
    if (dialog && typeof dialog.close === "function") dialog.close();
  }

  var objektDetailCache = new Map();
  var objektDetailRequest = 0;

  function setText(id, value) {
    var el = document.getElementById(id);
    if (el) el.textContent = value || "—";
  }

  function renderObjektDesc(el, raw) {
    if (!el) return;
    var text = String(raw || "").trim();
    el.classList.toggle("is-empty", !text);
    if (!text) {
      el.textContent = "아직 등록된 상세 설명이 없습니다.";
      return;
    }
    el.textContent = text;
  }

  function openMarketObjekt(key, tip) {
    if (!key) return;
    var dialog = marketObjektDialog();
    if (!dialog) return;
    var req = ++objektDetailRequest;
    var title = document.getElementById("marketObjektTitle");
    var img = document.getElementById("marketObjektImage");
    var desc = document.getElementById("marketObjektDesc");
    if (title) title.textContent = tip || "오브젝트";
    if (img) {
      img.removeAttribute("src");
      img.alt = tip || "";
    }
    setText("marketObjektMember", "—");
    setText("marketObjektSeason", "—");
    setText("marketObjektClass", "—");
    setText("marketObjektNo", "—");
    if (desc) {
      desc.classList.add("is-empty");
      desc.textContent = "불러오는 중…";
    }
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");

    var cached = objektDetailCache.get(key);
    var fetchDetail = cached
      ? Promise.resolve(cached)
      : fetch("/cosmo/objekt/detail/" + encodeURIComponent(key), {
          headers: { Accept: "application/json" },
          credentials: "same-origin",
          cache: "no-store"
        }).then(function (res) {
          if (!res.ok) throw new Error("detail failed");
          return res.json();
        }).then(function (data) {
          objektDetailCache.set(key, data);
          return data;
        });

    fetchDetail
      .then(function (data) {
        if (req !== objektDetailRequest) return;
        if (title) title.textContent = data.collectionId || tip || "오브젝트";
        if (img) {
          var src = data.frontImage || data.thumbnailImage || "";
          if (src) img.src = src;
          img.alt = data.collectionId || tip || "";
        }
        setText("marketObjektMember", data.member);
        setText("marketObjektSeason", data.season);
        setText("marketObjektClass", data.class);
        setText("marketObjektNo", data.collectionNo);
        if (data.memberColor) {
          var memberEl = document.getElementById("marketObjektMember");
          if (memberEl) memberEl.style.setProperty("--artist-color", data.memberColor);
        }
        renderObjektDesc(desc, data.description);
      })
      .catch(function () {
        if (req !== objektDetailRequest) return;
        renderObjektDesc(desc, "");
        if (desc) desc.textContent = "설명을 불러오지 못했습니다.";
      });
  }

  function openMarketMore(card, side) {
    if (!card) return;
    var dialog = marketMoreDialog();
    var body = document.getElementById("marketFeedMoreBody");
    var title = document.getElementById("marketFeedMoreTitle");
    var tpl = card.querySelector("template[data-market-feed-full]");
    if (!dialog || !body || !tpl) return;
    var root = tpl.content.cloneNode(true);
    var keepSide = side === "want" ? "want" : "have";
    Array.prototype.forEach.call(root.querySelectorAll(".market-feed-side"), function (el) {
      if ((el.getAttribute("data-side") || "") !== keepSide) el.remove();
    });
    Array.prototype.forEach.call(root.querySelectorAll(".market-feed-swap"), function (el) {
      el.remove();
    });
    var deal = root.querySelector(".market-feed-deal, .objekt-detail-deal");
    if (deal) deal.classList.add("is-single");
    Array.prototype.forEach.call(root.querySelectorAll(".market-feed-side-head"), function (el) {
      el.remove();
    });
    body.replaceChildren(root);
    if (title) title.textContent = keepSide === "want" ? "WANT" : "HAVE";


    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
  }

  function onDocumentClick(event) {
    if (event.button !== 0) return;
    var objektBtn = event.target.closest("[data-objekt-open]");
    if (objektBtn) {
      event.preventDefault();
      event.stopPropagation();
      openMarketObjekt(
        objektBtn.getAttribute("data-objekt-open"),
        objektBtn.getAttribute("data-tip") || objektBtn.getAttribute("title") || ""
      );
      return;
    }
    var moreBtn = event.target.closest("[data-market-feed-more]");
    if (moreBtn) {
      event.preventDefault();
      event.stopPropagation();
      openMarketMore(
        moreBtn.closest(".market-feed-card"),
        moreBtn.getAttribute("data-market-feed-more") || "have"
      );
      return;
    }
    if (event.target.closest("[data-market-feed-more-close]")) {
      event.preventDefault();
      closeMarketMore();
      return;
    }
    if (event.target.closest("[data-market-objekt-close]")) {
      event.preventDefault();
      closeMarketObjekt();
      return;
    }
    if (event.defaultPrevented) return;
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

  document.addEventListener("click", onDocumentClick, true);
  window.addEventListener("popstate", onPopState);
  document.addEventListener("keydown", function (event) {
    if (event.key !== "Escape") return;
    closeMarketObjekt();
    closeMarketMore();
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initMarketBoard);
  } else {
    initMarketBoard();
  }
})();
