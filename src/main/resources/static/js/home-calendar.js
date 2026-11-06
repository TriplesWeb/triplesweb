(function () {
    const root = document.getElementById("miniCalendar");
    if (!root) return;
    const schedules = JSON.parse(document.getElementById("homeScheduleData").textContent || "[]");
    const today = new Date();
    let month = new Date(today.getFullYear(), today.getMonth(), 1);
    const dateKey = date => date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
    function node(tag, className, text) {
        const el = document.createElement(tag);
        el.className = className;
        if (text != null) el.textContent = text;
        return el;
    }
    function render() {
        const toolbar = node("div", "main-calendar-toolbar");
        const title = node("h3", "main-calendar-title", month.getFullYear() + "년 " + (month.getMonth() + 1) + "월");
        title.setAttribute("aria-live", "polite");
        toolbar.append(title);
        const nav = node("div", "main-calendar-nav");
        [-1, 1].forEach(delta => {
            const button = node("button", "", delta < 0 ? "‹" : "›");
            button.type = "button";
            button.setAttribute("aria-label", delta < 0 ? "이전 달" : "다음 달");
            button.addEventListener("click", () => {
                month = new Date(month.getFullYear(), month.getMonth() + delta, 1);
                render();
                root.querySelectorAll("button")[delta < 0 ? 0 : 1].focus();
            });
            nav.append(button);
        });
        toolbar.append(nav);
        const table = node("table", "main-calendar-grid");
        table.setAttribute("aria-label", title.textContent);
        const head = table.createTHead().insertRow();
        ["일", "월", "화", "수", "목", "금", "토"].forEach(label => {
            const th = node("th", "", label);
            th.scope = "col";
            head.append(th);
        });
        const body = table.createTBody();
        const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
        const offset = month.getDay();
        let row;
        for (let i = 0; i < Math.ceil((offset + days) / 7) * 7; i++) {
            if (i % 7 === 0) row = body.insertRow();
            const cell = row.insertCell();
            const day = i - offset + 1;
            if (day < 1 || day > days) continue;
            const key = dateKey(new Date(month.getFullYear(), month.getMonth(), day));
            const link = node("a", "main-calendar-day", day + "일");
            link.href = "/schedule";
            link.setAttribute("aria-label", key);
            if (key === dateKey(today)) link.setAttribute("aria-current", "date");
            const events = schedules.filter(event => event.start && event.start <= key && (event.end ? key < event.end : key === event.start));
            const dots = node("span", "main-calendar-dots");
            events.slice(0, 3).forEach(event => {
                const dot = node("span", "main-calendar-dot");
                dot.style.setProperty("--schedule-color", event.color || "#00f5d4");
                dots.append(dot);
            });
            if (events.length) link.title = events.map(event => event.displayTitle || event.title || "").join("\n");
            link.append(dots);
            cell.append(link);
        }
        root.replaceChildren(toolbar, table);
    }
    render();
})();
