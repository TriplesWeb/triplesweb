(() => {
  const PLACEHOLDER = "/images/image-upload-pending.png";

  function isChemistryImage(img) {
    if (!(img instanceof HTMLImageElement)) return true;
    if (img.dataset.noPlaceholder === "1") return true;
    if (img.classList.contains("chem-modal-img")) return true;
    if (img.closest(".chem-carousel, .chem-edit-thumb, #chemEditImageThumbs")) return true;
    if (img.id === "chemistryImage" || img.id === "chemEditImagePreview") return true;
    return Boolean(
      img.closest(
        ".chem-page-main, .chem-table-wrap, .chem-modal, .chem-pair-card, #chemEditModal, #chemistryModal, #chemEditImagePreview"
      )
    );
  }

  function alreadyPlaceholder(img) {
    const src = img.getAttribute("src") || "";
    return src.includes("image-upload-pending.png") || img.dataset.ph === "1";
  }

  function applyPlaceholder(img) {
    if (isChemistryImage(img) || alreadyPlaceholder(img)) return;
    img.dataset.ph = "1";
    img.src = PLACEHOLDER;
  }

  function fillIfEmpty(img) {
    if (isChemistryImage(img) || alreadyPlaceholder(img)) return;
    const src = (img.getAttribute("src") || img.getAttribute("data-unit-src") || "").trim();
    if (!src) applyPlaceholder(img);
  }

  document.addEventListener(
    "error",
    (event) => {
      const img = event.target;
      if (img instanceof HTMLImageElement) applyPlaceholder(img);
    },
    true
  );

  function scan(root) {
    (root || document).querySelectorAll("img").forEach(fillIfEmpty);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => scan());
  } else {
    scan();
  }

  window.__imagePlaceholder = PLACEHOLDER;
  window.__applyImagePlaceholder = applyPlaceholder;
})();
