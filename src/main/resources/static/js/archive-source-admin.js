(() => {
  window.SiteTabManager.create({
    id: "archiveSourceManageModal",
    trigger: "#archiveSourceManageBtn",
    apiBase: "/api/archive/sources",
    listQuery: "?all=true",
    idKey: "id",
    nameKey: "label",
    title: "출처 관리",
    nameLabel: "출처 이름",
    nameMaxLength: 64,
    emptyText: "등록된 출처가 없습니다.",
    deleteCopy: (name) => `「${name}」 출처를 삭제할까요?`,
    fields: { color: true },
    onChange: (sources) => {
      const selects = document.querySelectorAll('#archiveAdminForms select[name="source"], #archiveSourceBulkSelect');
      for (const select of selects) {
        const previous = select.value;
        select.replaceChildren(new Option("출처를 선택하세요", ""), ...sources.map((source) => {
          const option = new Option(source.label, source.code);
          option.dataset.color = source.color;
          return option;
        }));
        select.value = sources.some((source) => source.code === previous) ? previous : "";
        select.dispatchEvent(new Event("change", { bubbles: true }));
      }
      return window.refreshArchiveSourceTabs?.(sources);
    },
  });
})();
