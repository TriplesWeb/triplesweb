(function () {
    if (!("IntersectionObserver" in window)) return;
    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var nodes = document.querySelectorAll(".main-fade-up");
    if (!nodes.length) return;
    if (reduce) return;
    nodes.forEach(function (node) { node.classList.add("is-pending"); });
    var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
            if (!entry.isIntersecting) return;
            entry.target.classList.add("is-visible");
            entry.target.classList.remove("is-pending");
            io.unobserve(entry.target);
        });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.12 });
    nodes.forEach(function (node) { io.observe(node); });
})();
