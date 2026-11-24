const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const resources = path.join(__dirname, "../../src/main/resources");
const partial = fs.readFileSync(path.join(resources, "templates/partials/ui/tab-manager.mustache"), "utf8");
const fixture = `<!doctype html><html lang="ko" data-theme="triplesdark"><head><meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" href="/css/site.css"><link rel="stylesheet" href="/css/site-ui.css">
  <link rel="stylesheet" href="/css/site-tab-manager.css">
</head><body><button id="open">출처 관리</button>${partial}</body></html>`;

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ["--no-sandbox"] });
  try {
    const page = await browser.newPage({ viewport: { width: 375, height: 720 } });
    const rows = [
      { id: 1, label: "첫 출처", code: "FIRST", color: "#00f5d4" },
      { id: 2, label: "두 번째 출처", code: "SECOND", color: "#ff5a5f" },
    ];
    let deleteConflict = true;
    await page.route("http://tab-manager.test/**", async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.pathname.startsWith("/css/")) {
        await route.fulfill({ path: path.join(resources, "static", url.pathname), contentType: "text/css" });
      } else if (url.pathname === "/api/sources/2/move") {
        assert.equal(url.searchParams.get("direction"), "-1");
        rows.reverse();
        await route.fulfill({ json: rows });
      } else if (url.pathname === "/api/sources/2" && request.method() === "DELETE") {
        if (deleteConflict) {
          await route.fulfill({ status: 409, json: { message: "사용 중인 출처는 삭제할 수 없습니다." } });
        } else {
          rows.shift();
          await route.fulfill({ json: { deleted: true } });
        }
      } else if (url.pathname === "/api/sources/2" && request.method() === "PUT") {
        if (request.postDataJSON().label === rows[0].label) {
          await route.fulfill({ status: 400, contentType: "text/html; charset=utf-8",
            body: '<p data-site-error-detail>이미 사용 중인 구분명입니다: 첫 출처</p>' });
          return;
        }
        Object.assign(rows.find((row) => row.id === 2), request.postDataJSON());
        await route.fulfill({ json: rows.find((row) => row.id === 2) });
      } else if (url.pathname === "/api/sources" && request.method() === "POST") {
        assert.equal(request.postDataJSON().label, rows[0].label);
        await route.fulfill({ status: 400, json: { message: "이미 사용 중인 구분명입니다: 첫 출처" } });
      } else if (url.pathname === "/api/sources") {
        assert.equal(url.searchParams.get("all"), "true");
        await route.fulfill({ json: rows });
      } else {
        await route.fulfill({ body: fixture, contentType: "text/html" });
      }
    });
    await page.goto("http://tab-manager.test/");
    await page.addScriptTag({ path: path.join(resources, "static/js/site-tab-manager.js") });
    await page.evaluate(() => {
      window.SiteTabManager.create({
        id: "manager", trigger: "#open", title: "출처 관리", placeholder: "출처 이름",
        apiBase: "/api/sources", listQuery: "?all=true", idKey: "id", nameKey: "label",
        fields: { color: true }, emptyText: "등록된 출처가 없습니다.",
        fixedName: (row) => row.fromEvent ? "공연" : "",
        deleteCopy: (name) => `${name} 삭제`,
      });
    });

    await page.locator("#open").press("Enter");
    const manager = page.locator("#manager[open]");
    const first = manager.locator('[data-row-id="1"]');
    const second = manager.locator('[data-row-id="2"]');
    assert.equal(await first.locator('[data-move="-1"]').isDisabled(), true);
    assert.equal(await second.locator('[data-move="1"]').isDisabled(), true);
    assert.equal(await manager.locator("[data-row-hidden], [data-add-visible], input[name=visible]").count(), 0);
    const name = manager.getByRole("textbox", { name: "구분 이름" });
    assert.equal(await manager.locator('input[name="code"]').count(), 0);
    const field = name.locator("../..");
    await name.focus();
    assert.ok(await field.evaluate((node) => node.classList.contains("is-up")));
    await name.fill("출처 이름");
    await manager.locator("[data-manager-close]").focus();
    assert.ok(await field.evaluate((node) => node.classList.contains("is-up")));
    await name.clear();
    await manager.locator("[data-manager-close]").focus();
    assert.equal(await field.evaluate((node) => node.classList.contains("is-up")), false);
    const selectors = ['.tab-manager-input[name="name"]',
      '.tab-manager-add button[type="submit"]', '[data-manager-close]', '.u033-view',
      '[data-move="-1"]', '[data-move="1"]', '[data-manager-delete]'];
    const heights = await manager.evaluate((element, targets) => targets.map((selector) =>
      element.querySelector(selector).getBoundingClientRect().height), selectors);
    assert.deepEqual(heights, selectors.map(() => 36));
    const assertColorAlignment = async () => {
      const positions = await manager.evaluate((element) => {
        const rect = (selector) => element.querySelector(selector).getBoundingClientRect();
        const center = (box) => box.top + box.height / 2;
        const same = (left, right) => Math.abs(left - right) < 0.05;
        const name = rect('[name="name"]');
        const addColor = rect(".tab-manager-add-color summary");
        const add = rect('.tab-manager-add button[type="submit"]');
        const color = rect(".u297-bl summary");
        const swatch = rect(".u297-bl summary .tab-manager-swatch");
        const rowName = element.querySelector(".u297-bl .u033-view");
        const nameStyle = getComputedStyle(rowName);
        const nameTextLeft = rowName.getBoundingClientRect().left
          + parseFloat(nameStyle.paddingLeft) + parseFloat(nameStyle.borderLeftWidth);
        const remove = rect(".u297-bl [data-manager-delete]");
        const row = rect(".u297-bl .u297-wf");
        return [name.left === addColor.left, center(add) === center(addColor),
          add.height === addColor.height, same(center(color), center(swatch)),
          same(color.left, nameTextLeft), color.top >= remove.bottom, same(remove.right, row.right - 8)];
      });
      assert.ok(positions.every(Boolean), "color controls must align in both rows");
    };
    assert.equal(await manager.locator("[data-manager-list] code").count(), 0);
    await assertColorAlignment();
    const addColor = manager.locator(".tab-manager-add-color summary");
    await addColor.press("Enter");
    await assertColorAlignment();
    await addColor.press("Enter");
    const rowColor = first.locator("summary");
    await rowColor.press("Enter");
    await assertColorAlignment();
    await rowColor.press("Enter");
    const listTop = () => manager.locator("[data-manager-list]").evaluate((el) => el.offsetTop);
    const initialTop = await listTop();

    const error = manager.locator("[data-manager-error]");
    await name.fill("  첫 출처  ");
    await manager.locator('button[type="submit"]').click();
    await error.waitFor();
    assert.equal(await error.textContent(), "이미 사용 중인 구분명입니다: 첫 출처");
    assert.equal(await manager.locator("[data-manager-list] > li").count(), 2);

    await second.locator(".u033-view").press("Enter");
    await second.locator(".u033-in").fill("  첫 출처  ");
    const renameRejected = page.waitForResponse((response) =>
      response.request().method() === "PUT" && response.status() === 400);
    await second.locator(".u033-in").press("Enter");
    await renameRejected;
    await error.waitFor();
    assert.equal(await error.textContent(), "이미 사용 중인 구분명입니다: 첫 출처");
    assert.ok(await second.locator(".u033-in").isVisible());
    await second.locator(".u033-in").press("Escape");
    assert.equal(await second.locator(".u033-view").textContent(), "두 번째 출처");

    await second.locator(".u033-view").press("Enter");
    await second.locator(".u033-in").fill("취소");
    await second.locator(".u033-in").press("Escape");
    assert.equal(await second.locator(".u033-view").textContent(), "두 번째 출처");
    await second.locator(".u033-view").press("Enter");
    await second.locator(".u033-in").fill("편집한 출처");
    await second.locator(".u033-in").press("Enter");
    await second.getByRole("button", { name: "편집한 출처 이름 수정" }).waitFor();
    assert.equal(await listTop(), initialTop, "status text must not shift the list");

    await page.emulateMedia({ reducedMotion: "reduce" });
    const moving = page.waitForResponse((response) => response.url().includes("/move?"));
    await second.locator('[data-move="-1"]').click();
    await moving;
    await page.waitForFunction(() => document.querySelector("[data-manager-list]").firstElementChild.dataset.rowId === "2");
    assert.equal(await second.evaluate((el) => el.getAnimations().length), 0);
    await second.locator("summary").press("Enter");
    assert.equal(await second.locator("[data-save-color]").evaluate((el) => el.getBoundingClientRect().height), 36);
    const swatch = second.locator(".u479-sw").first();
    assert.equal(await swatch.evaluate((el) => el.getBoundingClientRect().height), 36);
    const hue = second.locator('.u479-hue [role="slider"]');
    await hue.press("Home");
    await hue.press("ArrowRight");
    assert.equal(await hue.getAttribute("aria-valuenow"), "1");
    await second.locator("[data-save-color]").click();
    await second.locator(".u479-c").waitFor({ state: "hidden" });
    assert.equal(await manager.evaluate((el) => el.clientWidth === el.scrollWidth), true);

    await second.locator("[data-manager-delete]").click();
    const confirmation = page.getByRole("alertdialog");
    const cancel = confirmation.getByRole("button", { name: "취소" });
    const remove = confirmation.getByRole("button", { name: "삭제" });
    assert.equal(await cancel.evaluate((el) => document.activeElement === el), true);
    await page.keyboard.press("Shift+Tab");
    assert.equal(await remove.evaluate((el) => document.activeElement === el), true);
    await remove.press("Enter");
    await confirmation.getByText("사용 중인 출처는 삭제할 수 없습니다.").waitFor();
    await page.keyboard.press("Escape");
    await confirmation.waitFor({ state: "hidden" });
    assert.equal(await second.locator("[data-manager-delete]").evaluate((el) => document.activeElement === el), true);

    deleteConflict = false;
    await second.locator("[data-manager-delete]").click();
    await remove.click();
    await second.waitFor({ state: "detached" });
    await page.keyboard.press("Escape");
    await manager.waitFor({ state: "hidden" });
    assert.equal(await page.locator("#open").evaluate((el) => document.activeElement === el), true);
    rows.push({ id: 3, label: "연동 공연", fromEvent: true });
    await page.locator("#open").press("Enter");
    const fixed = manager.locator('[data-row-id="3"]');
    await fixed.locator("[data-fixed-name]").waitFor();
    assert.equal(await fixed.locator("[data-fixed-name]").textContent(), "연동 공연");
    assert.equal(await fixed.locator(".u056-nm b em").textContent(), "공연");
    assert.equal(await fixed.locator(".tab-manager-fixed-hint").textContent(), "공연/이벤트 페이지에서 수정");
    assert.ok(await fixed.locator(".u033-view").isHidden());
    assert.ok(await fixed.locator("[data-manager-delete]").isHidden());
    assert.ok(await fixed.locator('[data-move="-1"]').isEnabled());
    for (const width of [375, 1280]) {
      await page.setViewportSize({ width, height: 720 });
      await assertColorAlignment();
      const aligned = await manager.evaluate((element) => {
        const normal = element.querySelector('[data-row-id="1"] .u033-view');
        const fixedName = element.querySelector('[data-row-id="3"] [data-fixed-name]');
        const badge = element.querySelector('[data-row-id="3"] [data-fixed-badge]');
        const hint = element.querySelector('[data-row-id="3"] .tab-manager-fixed-hint');
        const center = (node) => { const box = node.getBoundingClientRect(); return box.top + box.height / 2; };
        const regularStyle = getComputedStyle(normal);
        const fixedStyle = getComputedStyle(fixedName);
        const badgeStyle = getComputedStyle(badge);
        const hintStyle = getComputedStyle(hint);
        return badgeStyle.fontSize === "10.5px"
          && badgeStyle.fontWeight === "800"
          && badgeStyle.fontStyle === "normal"
          && badgeStyle.borderRadius === "6px"
          && badgeStyle.paddingLeft === "7px"
          && badgeStyle.paddingRight === "7px"
          && badge.getBoundingClientRect().height === 20
          && Math.abs(badge.getBoundingClientRect().left - fixedName.getBoundingClientRect().right - 6) < 0.05
          && hintStyle.marginTop === "3px"
          && hintStyle.fontSize === "12.5px"
          && hintStyle.fontWeight === "600"
          && regularStyle.fontSize === fixedStyle.fontSize
          && regularStyle.fontWeight === fixedStyle.fontWeight
          && Math.abs(center(fixedName) - center(badge)) < 0.05
          && hint.getBoundingClientRect().top >= fixedName.getBoundingClientRect().bottom;
      });
      assert.ok(aligned, "fixed name typography, badge centering and hint");
    }
    console.log("Tab manager: keyboard editing, boundaries, reduced motion, color controls, delete errors and focus restoration passed");
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
