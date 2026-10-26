(() => {
  const root = document.querySelector('[data-serial-panel]');
  if (!root) return;
  const $ = selector => root.querySelector(selector);
  const form = $('[data-serial-lookup]'), status = $('[data-serial-status]');
  const owner = $('[data-serial-owner]'), history = $('[data-serial-history]');
  const list = $('[data-serial-rows]'), more = $('[data-serial-more]');
  let loading = false, currentSerial = '', nextPage = null;
  function el(tag, cls, text) {
    const node = document.createElement(tag); if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text; return node;
  }
  function profile(name) {
    const node = el(name ? 'a' : 'span', '', name || '공개 닉네임 미확인');
    if (name) node.href = `/cosmo/profile/${encodeURIComponent(name)}`; return node;
  }
  function date(value) {
    const result = new Date(value); if (!Number.isFinite(result.getTime())) throw new Error(); return result;
  }
  async function load(append) {
    if (loading || (!append && !form.reportValidity())) return;
    const serial = append ? currentSerial : form.elements.namedItem('serial').value;
    if (!Number.isSafeInteger(Number(serial)) || Number(serial) < 1 || (append && nextPage === null)) return;
    loading = true; form.querySelector('button').disabled = true; more.disabled = true;
    root.setAttribute('aria-busy', 'true');
    if (!append) { owner.hidden = true; history.hidden = true; owner.replaceChildren(); list.replaceChildren(); more.hidden = true; nextPage = null; }
    status.textContent = '시리얼과 전송 내역을 확인하는 중…';
    try {
      const query = new URLSearchParams({serial, page: String(append ? nextPage : 0)});
      const response = await fetch(`${root.dataset.url}?${query}`, {headers:{Accept:'application/json'}, credentials:'same-origin', cache:'no-store'});
      const data = await response.json(); if (!response.ok) throw new Error();
      if (['hidden','not_found'].includes(data.status)) {
        owner.hidden = true; history.hidden = true; list.replaceChildren(); nextPage = null; more.hidden = true;
        status.textContent = data.status === 'hidden' ? '이 시리얼의 정보는 현재 제공되지 않습니다.' : '발행되지 않았거나 아직 반영되지 않은 시리얼입니다.'; return;
      }
      if (data.status !== 'available' || !Array.isArray(data.transfers) || !data.owner || !Number.isSafeInteger(data.page) || data.page < 0 || !Number.isSafeInteger(data.total) || data.total < 0 || typeof data.hasNext !== 'boolean') throw new Error();
      if (append && data.page !== nextPage) throw new Error();
      const elements = data.transfers.map((row, i) => {
        const timestamp = date(row.timestamp), li = el('li', !append && i === 0 && data.owner.status === 'indexed' ? 'is-latest' : '');
        const name = el('p'); name.append(profile(row.nickname));
        if (li.className) name.append(el('span','serial-latest-label','최근 수신'));
        const time = el('time','',timestamp.toLocaleString('ko-KR')); time.dateTime = row.timestamp;
        li.append(name,time); return li;
      });
      const block = document.createDocumentFragment();
      const heading = el('div','serial-owner-label',`#${Number(serial).toLocaleString('ko-KR')} · 보유 계정`);
      heading.append(el('span','','최근 전송 기준')); block.append(heading);
      const name = el('div','serial-owner-name');
      if (data.owner.status === 'indexed') name.append(profile(data.owner.nickname));
      else name.textContent = data.owner.status === 'burned' ? '소각된 오브젝트' : '보유 계정 확인 불가';
      block.append(name);
      if (data.owner.timestamp) block.append(el('time','serial-owner-time',`마지막 전송 · ${date(data.owner.timestamp).toLocaleString('ko-KR')}`));
      owner.replaceChildren(block); owner.hidden = false;
      elements.forEach(node => list.append(node)); history.hidden = false;
      $('[data-serial-count]').textContent = `${data.total.toLocaleString('ko-KR')}건`;
      currentSerial = serial; nextPage = data.hasNext ? data.page + 1 : null; more.hidden = nextPage === null;
      status.textContent = data.total ? '' : '기록된 전송 내역이 없습니다.';
    } catch {
      status.textContent = append ? '이전 내역을 불러오지 못했습니다. 더 보기 버튼으로 다시 시도해 주세요.' : '조회하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    } finally {
      loading = false; form.querySelector('button').disabled = false; more.disabled = false; root.removeAttribute('aria-busy');
    }
  }
  form.addEventListener('submit', event => {event.preventDefault(); load(false);});
  more.addEventListener('click', () => load(true));
})();
