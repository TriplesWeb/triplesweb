(() => {
  "use strict";
  const entries = [];
  let render = () => {};
  const record = (text) => {
    entries.push(new Date().toLocaleTimeString("ko-KR") + " " + text);
    if (entries.length > 60) entries.shift();
    render();
  };
  const safePath = (url) => {
    try { return new URL(url, location.href).pathname; } catch (_) { return "(주소 확인 불가)"; }
  };
  // 메시지에는 요청 본문·쿼리·인증 정보를 저장하지 않는다.
  addEventListener("error", (event) => record("스크립트 오류: " + safePath(event.filename) + ":" + (event.lineno || 0)));
  addEventListener("unhandledrejection", () => record("처리되지 않은 비동기 오류"));
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async function(input, init) {
    const started = performance.now();
    const path = safePath(typeof input === "string" || input instanceof URL ? input : input.url);
    const method = ((init && init.method) || (input && input.method) || "GET").toUpperCase();
    try {
      const response = await nativeFetch(input, init);
      record(method + " " + path + " → " + response.status + " (" + Math.round(performance.now() - started) + "ms)");
      return response;
    } catch (error) {
      record(method + " " + path + " → 연결 실패");
      throw error;
    }
  };
  document.addEventListener("DOMContentLoaded", () => {
    const host = document.createElement("aside");
    host.id = "pr-preview-tools";
    host.innerHTML = '<section id="pr-preview-panel" hidden aria-label="미리보기 진단"><h2>PR 미리보기</h2><p data-info>서버 확인 중…</p><p>별도 테스트 데이터입니다. 운영에는 반영되지 않습니다.</p><button type="button" data-copy>진단 복사</button><pre></pre><textarea hidden readonly aria-label="복사할 진단 내용"></textarea></section><button type="button" data-toggle aria-expanded="false" aria-controls="pr-preview-panel">미리보기 · 진단</button>';
    document.body.append(host);
    const panel = host.querySelector("section");
    const toggle = host.querySelector("[data-toggle]");
    const info = host.querySelector("[data-info]");
    const pre = host.querySelector("pre");
    render = () => { if (!panel.hidden) pre.textContent = entries.join("\n") || "기록된 오류나 요청이 없습니다."; };
    toggle.addEventListener("click", () => {
      panel.hidden = !panel.hidden;
      toggle.setAttribute("aria-expanded", String(!panel.hidden));
      render();
    });
    host.querySelector("[data-copy]").addEventListener("click", async () => {
      const text = info.textContent + "\n" + safePath(location.href) + "\n" + entries.join("\n");
      try {
        await navigator.clipboard.writeText(text);
      } catch (_) {
        const area = host.querySelector("textarea");
        area.hidden = false; area.value = text; area.focus(); area.select();
      }
    });
    nativeFetch("/api/preview/info", {cache:"no-store"}).then(r => {
      if (!r.ok) throw new Error("정보 조회 실패");
      return r.json();
    }).then(data => {
      info.textContent = "PR #" + data.pr + " · " + data.revision.slice(0,7) + " · 데이터 " + new Date(data.snapshotAt).toLocaleString("ko-KR");
    }).catch(() => { info.textContent = "미리보기 서버 정보를 불러오지 못했습니다."; });
  }, {once:true});
})();
