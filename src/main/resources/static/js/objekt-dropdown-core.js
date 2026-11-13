/* 공통 드롭다운 open/close API — objekt 페이지·교환 필터 공유 */
(() => {
  if (window.ObjektDropdown) return;

  function isClosed(menu) {
    if (!menu) return true;
    return menu.hasAttribute("hidden") || menu.classList.contains("hidden");
  }

  function setOpen(menu, open) {
    if (!menu) return;
    const show = Boolean(open);
    menu.hidden = !show;
    menu.toggleAttribute("hidden", !show);
    menu.classList.toggle("hidden", !show);
    const host = menu.closest(".objekt-filter, [data-objekt-cols-filter]");
    host?.classList.toggle("is-open", show);
    const toggles = [];
    if (menu.id) {
      document.querySelectorAll('[aria-controls="' + menu.id + '"]').forEach((el) => toggles.push(el));
    }
    host?.querySelectorAll("[data-objekt-cols-toggle], .objekt-kind-toggle").forEach((el) => toggles.push(el));
    toggles.forEach((el) => el.setAttribute("aria-expanded", show ? "true" : "false"));
  }

  function closeAll(except) {
    document.querySelectorAll(".objekt-dropdown-menu").forEach((menu) => {
      if (menu !== except) setOpen(menu, false);
    });
  }

  function toggle(menu) {
    const open = isClosed(menu);
    closeAll(open ? menu : null);
    setOpen(menu, open);
    return open;
  }

  window.ObjektDropdown = { isClosed, setOpen, closeAll, toggle };
})();
