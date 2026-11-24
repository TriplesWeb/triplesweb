const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { runInNewContext } = require('node:vm');

const source = readFileSync(resolve(__dirname, '../../src/main/resources/static/js/unit-checkbox-group.js'), 'utf8');

function setup(initialMembers = []) {
  class Checkbox {
    constructor(value, members, formationDate = '') {
      Object.assign(this, { value, type: 'checkbox', checked: false, dataset: { members, formationDate } });
    }
  }
  const checks = [
    new Checkbox('7', '윤서연,정혜린', '2024-04-01'),
    new Checkbox('8', '정혜린,이지우'),
    new Checkbox('9', '윤서연,정혜린,이지우')
  ];
  let change;
  const root = {
    dataset: {},
    querySelectorAll: () => checks,
    addEventListener: (type, handler) => {
      assert.equal(type, 'change');
      change = handler;
    }
  };
  const window = {};
  runInNewContext(source, { window, HTMLInputElement: Checkbox });
  const groups = window.UnitCheckboxGroup;
  let members = initialMembers;
  const sync = registeredOn => groups.syncFromMembers(root, members, { registeredOn });
  groups.bindMemberHelper(root, {
    getMembers: () => members,
    applyMembers: names => {
      members = Array.from(names);
      sync();
    }
  });
  return {
    root,
    groups,
    sync,
    checked: () => Array.from(groups.selectedIds(root)),
    members: () => members,
    selectMembers: names => {
      members = names;
      sync();
    },
    toggle(index, checked) {
      checks[index].checked = checked;
      change({ target: checks[index] });
    }
  };
}

test('unit selections fill a deduplicated cast and removing units recomputes it', () => {
  const form = setup();
  form.toggle(0, true);
  form.toggle(1, true);
  assert.deepEqual(form.checked(), ['7', '8']);
  assert.deepEqual(form.members(), ['윤서연', '정혜린', '이지우']);

  form.toggle(0, false);
  assert.deepEqual(form.checked(), ['8']);
  assert.deepEqual(form.members(), ['정혜린', '이지우']);

  form.toggle(1, false);
  assert.deepEqual(form.checked(), []);
  assert.deepEqual(form.members(), []);
});

test('unit toggles preserve a cast selected by hand, including edits after automatic fill', () => {
  const form = setup(['김유연']);
  form.toggle(0, true);
  form.toggle(1, true);
  form.toggle(0, false);
  assert.deepEqual(form.checked(), ['8']);
  assert.deepEqual(form.members(), ['김유연']);

  const prefixed = setup();
  prefixed.toggle(0, true);
  prefixed.selectMembers(['윤서연', '김유연']);
  prefixed.toggle(1, true);
  prefixed.toggle(0, false);
  prefixed.toggle(1, false);
  assert.deepEqual(prefixed.checked(), []);
  assert.deepEqual(prefixed.members(), ['윤서연', '김유연']);
});

test('restoring an edit keeps saved units and suppresses inferred units for the saved cast', () => {
  const form = setup(['윤서연', '정혜린']);
  form.sync('2024-03-31');
  assert.deepEqual(form.checked(), []);
  form.sync('2024-04-01');
  assert.deepEqual(form.checked(), ['7']);

  form.groups.selectManual(form.root, ['8', '9', '99']);
  form.sync('2024-04-02');
  assert.deepEqual(form.checked(), ['8', '9', '99']);
  form.groups.selectManual(form.root, []);
  form.sync('2024-04-02');
  assert.deepEqual(form.checked(), []);
});

test('artist editing can still restore an array of unit checkboxes with setChecks', () => {
  const form = setup();
  form.groups.setChecks(form.root, [7, 9]);
  assert.deepEqual(form.checked(), ['7', '9']);
  assert.deepEqual(form.members(), []);
});
