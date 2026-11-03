(() => {
  "use strict";
  const mobileButton = document.getElementById("v2-menu-toggle");
  const mobileMenu = document.getElementById("v2-mobile-menu");
  const themeButton = document.getElementById("v2-theme-toggle");
  function setMenu(open) {
    if (!mobileButton || !mobileMenu) return;
    mobileMenu.hidden = !open;
    mobileButton.setAttribute("aria-expanded", String(open));
    mobileButton.setAttribute("aria-label", open ? "메뉴 닫기" : "메뉴 열기");
  }
  if (mobileButton) mobileButton.addEventListener("click", () => setMenu(mobileMenu.hidden));
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      setMenu(false);
      if (mobileButton && document.activeElement && mobileMenu && mobileMenu.contains(document.activeElement)) mobileButton.focus();
      document.querySelectorAll(".v2-nav-group.is-open").forEach((node) => node.classList.remove("is-open"));
    }
  });
  if (themeButton) {
    themeButton.addEventListener("click", () => {
      const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      document.documentElement.dataset.theme = next;
      try { localStorage.setItem("v2-theme", next); } catch (_) {}
      themeButton.setAttribute("aria-label", next === "dark" ? "라이트 모드 전환" : "다크 모드 전환");
    });
    themeButton.setAttribute("aria-label", document.documentElement.dataset.theme === "dark" ? "라이트 모드 전환" : "다크 모드 전환");
  }
  document.querySelectorAll(".v2-nav-toggle").forEach((button) => button.addEventListener("click", () => {
    const group = button.closest(".v2-nav-group");
    const open = !group.classList.contains("is-open");
    document.querySelectorAll(".v2-nav-group.is-open").forEach((node) => { node.classList.remove("is-open"); const b=node.querySelector(".v2-nav-toggle"); if(b)b.setAttribute("aria-expanded","false"); });
    group.classList.toggle("is-open", open);
    button.setAttribute("aria-expanded", String(open));
  }));
  const yt = document.getElementById("home-yt-facade");
  if (yt) yt.addEventListener("click", () => {
    const id = yt.dataset.youtubeId;
    if (!/^[a-zA-Z0-9_-]{11}$/.test(id || "")) return;
    const frame = document.createElement("iframe");
    frame.title = yt.dataset.youtubeTitle || "tripleS 공식 영상";
    frame.loading = "eager";
    frame.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
    frame.allowFullscreen = true;
    frame.src = "https://www.youtube.com/embed/" + id + "?autoplay=1&rel=0";
    frame.style.cssText = "display:block;width:100%;height:100%;border:0";
    yt.replaceChildren(frame);
  }, { once:true });
  const cal = document.getElementById("miniCalendar");
  const data = document.getElementById("v2-schedule-data");
  if (!cal || !data) return;
  let events = [];
  try { events = JSON.parse(data.textContent || "[]"); } catch (_) {}
  if (!Array.isArray(events)) events = [];
  // A zero-dependency monthly view: no FullCalendar package, iframe, or network fetch.
  const formatYmd = (date) => [date.getFullYear(), String(date.getMonth()+1).padStart(2,"0"), String(date.getDate()).padStart(2,"0")].join("-");
  const today = new Date();
  let month = new Date(today.getFullYear(), today.getMonth(), 1);
  function hasEvent(date) {
    const day = formatYmd(date);
    return events.some((e) => {
      if (!e || !e.start) return false;
      const start = String(e.start).slice(0,10);
      const end = e.end ? String(e.end).slice(0,10) : "";
      return start <= day && (end ? day < end || start === day : start === day);
    });
  }
  function draw() {
    const y=month.getFullYear(), m=month.getMonth();
    cal.replaceChildren();
    const nav=document.createElement("div");nav.className="v2-calendar-nav";
    const prev=document.createElement("button");prev.type="button";prev.textContent="‹";prev.setAttribute("aria-label","이전 달");
    const title=document.createElement("strong");title.textContent=y+"년 "+(m+1)+"월";
    const next=document.createElement("button");next.type="button";next.textContent="›";next.setAttribute("aria-label","다음 달");
    prev.addEventListener("click",()=>{month=new Date(y,m-1,1);draw()});
    next.addEventListener("click",()=>{month=new Date(y,m+1,1);draw()});
    nav.append(prev,title,next);cal.append(nav);
    const grid=document.createElement("div");grid.className="v2-calendar-grid";
    ["일","월","화","수","목","금","토"].forEach((day)=>{const cell=document.createElement("span");cell.className="v2-weekday";cell.textContent=day;grid.append(cell)});
    const start=new Date(y,m,1);const offset=start.getDay();const total=Math.ceil((offset+new Date(y,m+1,0).getDate())/7)*7;
    for(let index=0;index<total;index++){
      const d=new Date(y,m,1-offset+index);
      const link=document.createElement("a");link.className="v2-calendar-day";link.href="/schedule";link.textContent=String(d.getDate());
      link.setAttribute("aria-label",formatYmd(d)+(hasEvent(d)?" 일정 있음":""));
      if(d.getMonth()!==m)link.classList.add("is-outside");
      if(formatYmd(d)===formatYmd(today))link.classList.add("is-today");
      if(hasEvent(d)){const dot=document.createElement("i");dot.className="v2-event-dot";dot.setAttribute("aria-hidden","true");link.append(dot)}
      grid.append(link);
    }
    cal.append(grid);
  }
  const init = () => draw();
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver((entries) => { if (entries.some((entry) => entry.isIntersecting)) { observer.disconnect(); init(); } }, {rootMargin:"120px"});
    observer.observe(cal);
  } else init();
})();
