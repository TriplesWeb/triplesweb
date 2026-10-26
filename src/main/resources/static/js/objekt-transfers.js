(() => {
  const root = document.querySelector('[data-transfer-history]');
  if (!root) return;
  const status = root.querySelector('[data-transfer-status]');
  const list = root.querySelector('[data-transfer-rows]');
  const more = root.querySelector('[data-transfer-more]');
  const form = root.querySelector('form');
  let next = null, loading = false;
  const nickname = root.dataset.account || '';
  function params() {
    return nickname ? new URLSearchParams({nickname})
      : new URLSearchParams({serial: form.elements.namedItem('serial').value});
  }
  async function load(append) {
    if (loading || (!nickname && !form.reportValidity())) return;
    const query = append && next ? new URLSearchParams(next) : params();
    if (!nickname && (!Number.isSafeInteger(Number(query.get('serial'))) || Number(query.get('serial')) < 1)) return;
    loading = true; more.disabled = true;
    if (form) form.querySelector('button').disabled = true;
    root.setAttribute('aria-busy', 'true');
    if (!append) { list.replaceChildren(); next = null; more.hidden = true; }
    status.textContent = '전송 내역을 불러오는 중…';
    try {
      const response = await fetch(`${root.dataset.url}?${query}`, {
        headers: {Accept: 'application/json'}, credentials: 'same-origin', cache: 'no-store'
      });
      const data = await response.json();
      if (!response.ok) throw new Error();
      if (data.status !== 'available') {
        if (!['hidden', 'not_found'].includes(data.status)) throw new Error();
        list.replaceChildren(); next = null; more.hidden = true;
        status.textContent = data.status === 'hidden' ? '이 전송 내역은 현재 제공되지 않습니다.' : '계정 또는 전송 내역을 찾지 못했습니다.';
        return;
      }
      if (!Array.isArray(data.transfers) || typeof data.hasNext !== 'boolean') throw new Error();
      const rows = data.transfers.map(row => {
        const date = new Date(row.timestamp);
        if (!Number.isFinite(date.getTime())) throw new Error();
        const li = document.createElement('li');
        li.textContent = nickname
          ? `${date.toLocaleString('ko-KR')} · ${row.direction || ''} · ${row.collectionId || ''} · ${row.fromNickname || '닉네임 미확인'} → ${row.toNickname || '닉네임 미확인'}`
          : `${date.toLocaleString('ko-KR')} · 받는 계정: ${row.nickname || '닉네임 미확인'}`;
        return li;
      });
      let following = null;
      if (data.hasNext) {
        following = new URLSearchParams(query);
        if (nickname) {
          if (!data.cursorTimestamp || !data.cursorId) throw new Error();
          following.set('cursorTimestamp', data.cursorTimestamp); following.set('cursorId', data.cursorId);
        } else {
          if (!Number.isSafeInteger(data.page) || data.page < 0) throw new Error();
          following.set('page', String(data.page + 1));
        }
        if (following.toString() === query.toString()) throw new Error();
      }
      rows.forEach(row => list.append(row));
      next = following; more.hidden = !next;
      status.textContent = list.children.length ? `전송 내역 ${list.children.length}건 · 공개 인덱스 기준` : '표시할 전송 내역이 없습니다.';
    } catch {
      status.textContent = '전송 내역을 불러오지 못했습니다. 다시 조회해 주세요.';
      // Preserve the previous page and cursor so a failed next-page request can be retried.
    } finally {
      loading = false; more.disabled = false;
      if (form) form.querySelector('button').disabled = false;
      root.removeAttribute('aria-busy');
    }
  }
  more.addEventListener('click', () => load(true));
  if (form) form.addEventListener('submit', event => { event.preventDefault(); return load(false); });
  if (nickname) load(false);
})();
