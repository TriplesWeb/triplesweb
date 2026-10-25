(() => {
  const button = document.getElementById('cosmoObjektSync');
  const status = document.getElementById('cosmoObjektSyncStatus');
  button?.addEventListener('click', async () => {
    if (button.disabled) return;
    button.disabled = true;
    status.textContent = 'COSMO 보유 목록을 확인하고 저장하는 중…';
    try {
      const headers = window.AboutAdminAuth.applyAuthHeaders({});
      const response = await fetch('/api/cosmo/objekts/sync', {method: 'POST', credentials: 'same-origin', headers});
      if (!response.ok) throw new Error(response.status === 403 ? '관리자 로그인을 확인해 주세요.' : '동기화에 실패했습니다. 기존 DB는 유지됩니다.');
      const result = await response.json();
      status.textContent = `${result.collections}종류 · ${result.objekts}장 저장 완료 (${result.changed}건 변경)`;
    } catch (error) {
      status.textContent = error.message || '동기화에 실패했습니다. 잠시 후 다시 시도해 주세요.';
    } finally {
      button.disabled = false;
    }
  });
})();
