(() => {
  const root = document.querySelector('[data-account-transfers]');
  if (!root) return;
  const $ = s => root.querySelector(s);
  const nickname = root.dataset.account || '';
  const timeline = $('[data-timeline]'), status = $('[data-status]');
  const more = $('[data-more]'), retry = $('[data-retry]'), empty = $('[data-empty]');
  let rows = [], next = null, loading = false, filter = 'all', failedAppend = false;
  let state = 'idle';
  const dayFormat = new Intl.DateTimeFormat('ko-KR', {year: 'numeric', month: 'long', day: 'numeric', weekday: 'short'});
  const timeFormat = new Intl.DateTimeFormat('ko-KR', {hour: '2-digit', minute: '2-digit', hour12: false});
  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function accountLink(name) {
    const node = el(name ? 'a' : 'strong', '', name || '닉네임 미확인');
    if (name) node.href = `/cosmo/transfers?${new URLSearchParams({nickname: name})}`;
    return node;
  }
  function cardRow(row) {
    const li = el('li', 'tr-row');
    const thumb = el('div', 'tr-thumb', 'OBJEKT');
    try {
      const url = new URL(row.thumbnailImage);
      if (url.protocol === 'https:' && !url.username && !url.password) {
        const image = el('img'); image.src = url.href; image.alt = ''; image.loading = 'lazy';
        image.referrerPolicy = 'no-referrer';
        image.addEventListener('error', () => image.remove(), {once: true}); thumb.append(image);
      }
    } catch { /* Keep the card placeholder when no valid image is available. */ }
    const card = el('div', 'tr-card');
    card.append(el('strong', 'tr-card-name', [row.member, row.collectionNo].filter(Boolean).join(' · ') || row.collectionId || '오브젝트'));
    const meta = el('div', 'tr-card-meta', [row.season, row.class].filter(Boolean).join(' / ') || row.collectionId || '카드 정보 미확인');
    if (Number.isSafeInteger(row.reportedSerial) && row.reportedSerial > 0) {
      const serial = el('span', 'tr-serial', `#${row.reportedSerial.toLocaleString('ko-KR')}`);
      serial.title = '공개 인덱스가 보고한 시리얼 · 추정치가 포함될 수 있습니다'; meta.append(serial);
    }
    card.append(meta);
    const direction = row.direction;
    const mint = row.action === 'mint';
    const badge = el('span', `tr-badge ${direction === '받음' ? 'in' : direction === '보냄' ? 'out' : ''}`,
      mint ? '⊕ 민트' : `${direction === '받음' ? '↙ ' : direction === '보냄' ? '↗ ' : '↔ '}${direction}`);
    const peer = el('div', 'tr-peer');
    peer.append(el('span', 'tr-peer-label', direction === '받음' ? '보낸 계정' : direction === '보냄' ? '받는 계정' : '동일 계정'),
      mint ? el('strong', '', '코스모 · 신규 발행') : accountLink(direction === '받음' ? row.fromNickname : direction === '보냄' ? row.toNickname : nickname));
    const time = el('time', 'tr-time', timeFormat.format(row.date)); time.dateTime = row.timestamp;
    time.title = row.date.toLocaleString('ko-KR'); time.append(el('small', '', '전송 시각'));
    li.append(thumb, card, badge, peer, time); return li;
  }
  function render() {
    const search = $('[data-search]').value.trim().toLocaleLowerCase();
    const visible = rows.filter(row => (filter === 'all' || row.direction === filter) &&
      (!$('[data-season]').value || row.season === $('[data-season]').value) &&
      (!$('[data-class]').value || row.class === $('[data-class]').value) &&
      (!$('[data-action]').value || (row.action || 'transfer') === $('[data-action]').value) &&
      [row.member, row.collectionNo, row.collectionId, row.season, row.class, row.fromNickname, row.toNickname, row.reportedSerial]
        .some(value => String(value || '').toLocaleLowerCase().includes(search)));
    const groups = new Map();
    visible.forEach(row => { const key = dayFormat.format(row.date); if (!groups.has(key)) groups.set(key, []); groups.get(key).push(row); });
    const fragment = document.createDocumentFragment();
    groups.forEach((items, day) => {
      const section = el('section', 'tr-day'), heading = el('h3', '', day), list = el('ul');
      heading.append(el('span', '', `${items.length}건`)); items.forEach(row => list.append(cardRow(row)));
      section.append(heading, list); fragment.append(section);
    });
    timeline.replaceChildren(fragment);
    for (const key of ['all', '받음', '보냄']) $('[data-count="' + key + '"]').textContent =
      rows.filter(row => key === 'all' || row.direction === key).length.toLocaleString('ko-KR');
    empty.hidden = loading || state !== 'available' || visible.length > 0;
    $('[data-empty-title]').textContent = rows.length ? '조건에 맞는 내역이 없습니다' : '아직 전송 내역이 없습니다';
    $('[data-empty-text]').textContent = rows.length ? '검색어나 필터를 바꾸거나, 이전 내역을 더 불러와 보세요.' : '공개 인덱스에 기록된 전송이 생기면 이곳에 표시됩니다.';
    if (state === 'available' && !loading) status.textContent = `${visible.length.toLocaleString('ko-KR')}건 표시 · 불러온 ${rows.length.toLocaleString('ko-KR')}건에서 검색 · 최신순`;
  }
  async function load(append = false) {
    if (loading || !nickname) return;
    loading = true; failedAppend = append; retry.hidden = true; more.disabled = true;
    root.setAttribute('aria-busy', 'true'); $('[data-loading]').hidden = append;
    empty.hidden = true; status.textContent = append ? '이전 전송 내역을 불러오는 중…' : '전송 내역을 불러오는 중…';
    try {
      const query = new URLSearchParams({nickname});
      if (append && next) { query.set('cursorTimestamp', next.timestamp); query.set('cursorId', next.id); }
      const response = await fetch(`${root.dataset.url}?${query}`, {headers: {Accept: 'application/json'}, credentials: 'same-origin', cache: 'no-store'});
      const data = await response.json(); if (!response.ok) throw new Error();
      if (['hidden', 'not_found'].includes(data.status)) {
        state = data.status; rows = []; next = null; render();
        status.textContent = data.status === 'hidden' ? '이 계정의 전송 내역은 현재 제공되지 않습니다.' : '계정 또는 전송 내역을 찾지 못했습니다.';
        return;
      }
      if (data.status !== 'available' || !Array.isArray(data.transfers) || typeof data.hasNext !== 'boolean') throw new Error();
      const incoming = data.transfers.map(row => {
        const date = new Date(row.timestamp);
        if (!Number.isFinite(date.getTime()) || !['받음', '보냄', '자기 전송'].includes(row.direction)) throw new Error();
        return {...row, date};
      });
      let following = null;
      if (data.hasNext) {
        if (typeof data.cursorTimestamp !== 'string' || !Number.isFinite(Date.parse(data.cursorTimestamp)) || typeof data.cursorId !== 'string' || !data.cursorId || !incoming.length) throw new Error();
        following = {timestamp: data.cursorTimestamp, id: data.cursorId};
        if (append && next && following.timestamp === next.timestamp && following.id === next.id) throw new Error();
      }
      rows = (append ? rows.concat(incoming) : incoming).sort((a,b) => b.date - a.date);
      for (const [selector, field] of [['[data-season]', 'season'], ['[data-class]', 'class']]) {
        const select = $(selector), selected = select.value;
        select.replaceChildren(el('option', '', '전체')); select.firstChild.value = '';
        [...new Set(rows.map(row => row[field]).filter(Boolean))].sort().forEach(value => {
          const option = el('option', '', value); option.value = value; select.append(option);
        });
        select.value = selected;
      }
      next = following; state = 'available';
    } catch {
      state = 'error'; retry.hidden = false;
      status.textContent = append ? '이전 내역을 불러오지 못했습니다. 표시된 내역은 유지됩니다. 다시 시도해 주세요.' : '전송 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
    } finally {
      loading = false; more.disabled = false; more.hidden = !next || state === 'hidden' || state === 'not_found';
      $('[data-loading]').hidden = true; root.removeAttribute('aria-busy');
      if (state === 'available') render();
    }
  }
  root.querySelectorAll('[data-filter]').forEach(button => button.addEventListener('click', () => {
    filter = button.dataset.filter;
    root.querySelectorAll('[data-filter]').forEach(tab => { tab.classList.toggle('is-active', tab === button); tab.setAttribute('aria-pressed', String(tab === button)); });
    render();
  }));
  $('[data-search]').addEventListener('input', render);
  ['[data-season]', '[data-class]', '[data-action]'].forEach(selector => $(selector).addEventListener('change', render));
  more.addEventListener('click', () => load(true)); retry.addEventListener('click', () => load(failedAppend));
  if (nickname) {
    $('[data-account-title]').textContent = `@${nickname}`;
    $('[data-account-initial]').textContent = Array.from(nickname)[0].toUpperCase();
    const profile = $('[data-profile-link]'); profile.href = `/cosmo/profile/${encodeURIComponent(nickname)}`; profile.hidden = false;
    load();
  }
})();
