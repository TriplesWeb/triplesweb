const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/js/objekt-kind-combo.js'), 'utf8');

function loadCombo() {
  const window = {};
  vm.runInNewContext(code, {window, document: {}});
  return window.ObjektKindCombo;
}

test('kind combo treats all and 전체 as the all sentinel', () => {
  const combo = loadCombo();
  assert.equal(combo.normalizeKindValue(''), 'all');
  assert.equal(combo.normalizeKindValue('all'), 'all');
  assert.equal(combo.normalizeKindValue('ALL'), 'all');
  assert.equal(combo.normalizeKindValue('전체'), 'all');
  assert.equal(combo.displayKindValue('all'), '전체');
  assert.equal(combo.displayKindValue(''), '전체');
});

test('kind combo keeps English letters and digits only', () => {
  const combo = loadCombo();
  assert.equal(combo.sanitizeKind('10-1 가나다'), '101');
  assert.equal(combo.normalizeKindValue('ab12!'), 'AB12');
  assert.equal(combo.normalizeKindValue('@@@'), 'all');
  assert.equal(combo.displayKindValue('101z'), '101Z');
});

function classList() {
  const names = new Set();
  return {
    add: (name) => names.add(name),
    remove: (name) => names.delete(name),
    contains: (name) => names.has(name),
    toggle(name, force) {
      if (force === true) names.add(name);
      else if (force === false) names.delete(name);
      else if (names.has(name)) names.delete(name);
      else names.add(name);
    }
  };
}

function option(value) {
  return {
    dataset: { value },
    hidden: false,
    classList: classList(),
    addEventListener() {},
    scrollIntoView() {}
  };
}

function bindCombo(kindOptions) {
  const combo = loadCombo();
  const hiddenInput = { value: 'all' };
  const input = {
    value: '전체',
    dataset: {},
    closest: () => ({ classList: classList() }),
    addEventListener() {},
    setAttribute() {},
    focus() {}
  };
  const menu = {
    querySelectorAll: () => kindOptions,
    classList: classList(),
    hasAttribute: () => true,
    setAttribute() {},
    removeAttribute() {},
    contains: () => false
  };
  menu.classList.add('hidden');
  const bound = combo.bindKindCombo({ input, menu, hiddenInput, empty: { hidden: true } });
  return { combo, bound, hiddenInput, input, kindOptions };
}

test('kind combo hides options that are not in the selected class', () => {
  const all = option('all');
  const doubleKind = option('101Z');
  const specialKind = option('201A');
  const { bound, hiddenInput, input } = bindCombo([all, doubleKind, specialKind]);
  hiddenInput.value = '101Z';
  input.value = '101Z';
  input.dataset.kindValue = '101Z';
  const next = bound.syncToClasses(['Special'], { Double: ['101Z'], Special: ['201A'] });
  assert.equal(next, 'all');
  assert.equal(hiddenInput.value, 'all');
  assert.equal(input.value, '전체');
  assert.equal(all.hidden, false);
  assert.equal(doubleKind.hidden, true);
  assert.equal(specialKind.hidden, false);
  assert.equal(doubleKind.dataset.classAllowed, 'false');
  assert.equal(specialKind.dataset.classAllowed, 'true');
});

test('kind combo blocks letters outside English and digits', () => {
  const combo = loadCombo();
  const listeners = {};
  const input = {
    value: '',
    dataset: {},
    closest: () => ({ classList: classList() }),
    addEventListener(type, fn) { (listeners[type] ||= []).push(fn); },
    setAttribute() {},
    focus() {}
  };
  const menu = {
    querySelectorAll: () => [],
    classList: classList(),
    hasAttribute: () => true,
    setAttribute() {},
    removeAttribute() {},
    contains: () => false
  };
  menu.classList.add('hidden');
  combo.bindKindCombo({ input, menu, hiddenInput: { value: 'all' }, empty: { hidden: true } });
  const blocked = { inputType: 'insertText', data: '가', defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
  listeners.beforeinput.forEach((fn) => fn(blocked));
  assert.equal(blocked.defaultPrevented, true);
  const allowed = { inputType: 'insertText', data: '12A', defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
  listeners.beforeinput.forEach((fn) => fn(allowed));
  assert.equal(allowed.defaultPrevented, false);
  input.value = '12가A-';
  listeners.input.forEach((fn) => fn());
  assert.equal(input.value, '12A');
});

test('kind combo shows every kind when no class is selected', () => {
  const all = option('all');
  const doubleKind = option('101Z');
  const specialKind = option('201A');
  const { bound } = bindCombo([all, doubleKind, specialKind]);
  bound.syncToClasses(['Special'], { Double: ['101Z'], Special: ['201A'] });
  bound.syncToClasses([], { Double: ['101Z'], Special: ['201A'] });
  assert.equal(doubleKind.hidden, false);
  assert.equal(specialKind.hidden, false);
});
