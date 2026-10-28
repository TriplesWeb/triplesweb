(() => {
  const favorite = document.querySelector('[data-objekt-favorite]');
  if (favorite) favorite.addEventListener('submit', async event => {
    event.preventDefault();
    const button = favorite.querySelector('button');
    if (button.disabled) return;
    const input = favorite.elements.namedItem('favorite');
    const status = favorite.querySelector('[data-favorite-status]');
    button.disabled = true;
    status.textContent = '저장 중…';
    try {
      const response = await fetch(favorite.action, {
        method: 'PUT', body: new URLSearchParams(new FormData(favorite)),
        headers: window.AboutAdminAuth?.applyAuthHeaders({Accept:'application/json'}) || {Accept:'application/json'}, credentials: 'same-origin'
      });
      if (!response.ok) throw new Error();
      const data = await response.json();
      if (typeof data.favorite !== 'boolean') throw new Error();
      button.textContent = data.favorite ? '♥ 찜 해제' : '♡ 찜하기';
      button.setAttribute('aria-pressed', String(data.favorite));
      input.value = String(!data.favorite);
      status.textContent = data.favorite ? '찜 목록에 추가했습니다.' : '찜 목록에서 해제했습니다.';
    } catch {
      status.textContent = '저장하지 못했습니다. 다시 눌러 주세요.';
    } finally { button.disabled = false; }
  });

  const owner = document.querySelector('[data-objekt-owner]');
  if (owner) owner.addEventListener('submit', async event => {
    event.preventDefault();
    const button = owner.querySelector('button');
    if (button.disabled || !owner.reportValidity()) return;
    const serial = owner.elements.namedItem('serial').value;
    if (!Number.isSafeInteger(Number(serial)) || Number(serial) < 1) return;
    const result = document.querySelector('[data-owner-result]');
    result.replaceChildren();
    result.textContent = '소유자를 확인하는 중…';
    button.disabled = true;
    owner.setAttribute('aria-busy', 'true');
    try {
      const response = await fetch(`${owner.action}?${new URLSearchParams({ serial })}`, {
        headers: { Accept: 'application/json' }, credentials: 'same-origin', cache: 'no-store'
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || '조회에 실패했습니다. 다시 시도해 주세요.');
      result.replaceChildren();
      if (data.status !== 'found') {
        result.textContent = ({ not_found: '아직 발행되지 않았거나 인덱스에 반영되지 않은 시리얼입니다.',
          hidden: '이 시리얼의 소유자 정보는 현재 제공되지 않습니다.', burned: '소각되어 현재 소유자가 없습니다.' })[data.status]
          || '소유자 정보를 확인할 수 없습니다.';
        return;
      }
      const text = value => {
        const line = document.createElement('p'); line.textContent = value; result.append(line);
      };
      text(`#${data.serial} · ${data.nickname || '현재 공개된 COSMO 닉네임을 확인하지 못했습니다.'}`);
      if (data.nickname && typeof data.profileUrl === 'string' && data.profileUrl.startsWith('/cosmo/profile/')) {
        const link = document.createElement('a');
        link.href = data.profileUrl; link.textContent = `${data.nickname} 님의 오브젝트 보기 →`;
        link.className = 'inline-block text-teal-300 underline'; result.append(link);
      }
    } catch (error) {
      result.textContent = error.message || '조회하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    } finally {
      button.disabled = false;
      owner.removeAttribute('aria-busy');
    }
  });
})();
