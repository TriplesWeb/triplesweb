const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const { performance } = require('node:perf_hooks');
const source = readFileSync('src/main/resources/static/js/home-calendar.js', 'utf8');
function element(tag) {
    return { tag, children: [], attributes: {}, style: {setProperty() {}},
        append(...children) { this.children.push(...children); },
        replaceChildren(...children) { this.children = children; },
        setAttribute(k, v) { this.attributes[k] = v; },
        addEventListener(k, v) { this[k] = v; }, focus() {},
        createTHead() { const e = element('thead'); this.append(e); return e; },
        createTBody() { const e = element('tbody'); this.append(e); return e; },
        insertRow() { const e = element('tr'); this.append(e); return e; },
        insertCell() { const e = element('td'); this.append(e); return e; },
        querySelectorAll() { return this.children[0].children[1].children; }
    };
}
test('calendar renders under one second with range events and year navigation', () => {
    const root = element('div');
    const data = [{start:'2026-11-01',end:'2026-11-03',title:'two days',color:'#ff0000'}];
    const document = { createElement: element, getElementById: id => id === 'miniCalendar' ? root : {textContent:JSON.stringify(data)} };
    class Clock extends Date { constructor(...args) { super(...(args.length ? args : [2026,10,5])); } }
    const start = performance.now();
    runInNewContext(source, {document, Date:Clock});
    assert.ok(performance.now() - start < 1000);
    const cells = root.children[1].children[1].children.flatMap(row=>row.children);
    assert.equal(cells.length,35);
    assert.equal(cells[0].children[0].title,'two days');
    assert.equal(cells[1].children[0].title,'two days');
    assert.equal(cells[2].children[0].title,undefined);
    assert.equal(cells[4].children[0].attributes['aria-current'],'date');
    root.querySelectorAll()[1].click();
    root.querySelectorAll()[1].click();
    assert.equal(root.children[0].children[0].textContent,'2027년 1월');
    root.querySelectorAll()[0].click();
    assert.equal(root.children[0].children[0].textContent,'2026년 12월');
});
