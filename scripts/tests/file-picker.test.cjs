'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const filePickerPath = path.resolve(__dirname, '../../src/main/resources/static/js/file-picker.js');

function loadLogic() {
  delete require.cache[require.resolve(filePickerPath)];
  const FilePicker = require(filePickerPath);
  assert.ok(FilePicker, 'FilePicker가 노출되어야 합니다.');
  assert.ok(FilePicker._logic, 'FilePicker._logic이 노출되어야 합니다.');
  return FilePicker._logic;
}

function makeFile(name, size = 1024, type = 'image/png', lastModified = 1000) {
  return { name, size, type, lastModified };
}

test('matchesAccept filters mime wildcard, exact mime, and extensions', () => {
  const { matchesAccept } = loadLogic();
  const png = makeFile('a.png', 100, 'image/png');
  const jpg = makeFile('b.JPG', 100, 'image/jpeg');
  const txt = makeFile('c.txt', 100, 'text/plain');

  assert.equal(matchesAccept(png, ''), true);
  assert.equal(matchesAccept(png, 'image/*'), true);
  assert.equal(matchesAccept(txt, 'image/*'), false);
  assert.equal(matchesAccept(jpg, '.jpg,.png'), true);
  assert.equal(matchesAccept(txt, '.jpg,.png'), false);
  assert.equal(matchesAccept(png, 'image/png'), true);
});

test('multi mode accumulates files, deduplicates by name/size/lastModified, and enforces maxFiles', () => {
  const { mergeFiles } = loadLogic();
  const f1 = makeFile('one.png', 100, 'image/png', 10);
  const f1Dup = makeFile('one.png', 100, 'image/png', 10);
  const f1DiffTime = makeFile('one.png', 100, 'image/png', 20);
  const f2 = makeFile('two.jpg', 200, 'image/jpeg', 10);
  const f3 = makeFile('three.webp', 300, 'image/webp', 10);
  const bad = makeFile('bad.txt', 50, 'text/plain', 10);

  const r1 = mergeFiles([], [f1, f2, bad], { mode: 'multi', accept: 'image/*', maxFiles: 3 });
  assert.deepEqual(r1.files, [f1, f2]);
  assert.deepEqual(r1.added, [f1, f2]);
  assert.deepEqual(r1.rejectedAccept, [bad]);

  const r2 = mergeFiles(r1.files, [f1Dup, f1DiffTime, f3], { mode: 'multi', accept: 'image/*', maxFiles: 3 });
  assert.deepEqual(r2.files, [f1, f2, f1DiffTime]);
  assert.deepEqual(r2.rejectedDuplicate, [f1Dup]);
  assert.deepEqual(r2.rejectedMax, [f3]);
});

test('single mode accepts only the first valid file and flags truncatedSingle', () => {
  const { mergeFiles } = loadLogic();
  const f1 = makeFile('one.png', 100, 'image/png', 10);
  const f2 = makeFile('two.png', 200, 'image/png', 10);
  const bad = makeFile('bad.pdf', 300, 'application/pdf', 10);

  const r1 = mergeFiles([], [bad, f1, f2], { mode: 'single', accept: 'image/*' });
  assert.deepEqual(r1.files, [f1]);
  assert.deepEqual(r1.rejectedAccept, [bad]);
  assert.equal(r1.truncatedSingle, true);
});

test('removeFileAt and undoRemove restore removed file at original index', () => {
  const { removeFileAt, undoRemove } = loadLogic();
  const f1 = makeFile('a.png', 10);
  const f2 = makeFile('b.png', 20);
  const f3 = makeFile('c.png', 30);

  const rem = removeFileAt([f1, f2, f3], 1, null);
  assert.deepEqual(rem.files, [f1, f3]);
  assert.equal(rem.removed, f2);
  assert.ok(rem.pendingUndo);

  const undid = undoRemove(rem.files, rem.pendingUndo);
  assert.deepEqual(undid.files, [f1, f2, f3]);
  assert.equal(undid.restored, f2);
  assert.equal(undid.pendingUndo, null);
});

test('reconcileSelection in multi mode keeps existing files and flags shouldSyncInput when all incoming files are duplicate or rejected', () => {
  const { reconcileSelection } = loadLogic();
  const f1 = makeFile('one.png', 100, 'image/png', 10);
  const f1Dup = makeFile('one.png', 100, 'image/png', 10);
  const bad = makeFile('bad.txt', 50, 'text/plain', 10);

  const res = reconcileSelection([f1], [f1Dup, bad], { mode: 'multi', accept: 'image/*', maxFiles: 10 });
  assert.deepEqual(res.files, [f1]);
  assert.deepEqual(res.added, []);
  assert.deepEqual(res.rejectedDuplicate, [f1Dup]);
  assert.deepEqual(res.rejectedAccept, [bad]);
  assert.equal(res.shouldSyncInput, true);
});

test('reconcileSelection in single mode keeps existing file and flags shouldSyncInput when incoming file is rejected', () => {
  const { reconcileSelection } = loadLogic();
  const f1 = makeFile('one.png', 100, 'image/png', 10);
  const bad = makeFile('bad.pdf', 300, 'application/pdf', 10);

  const res = reconcileSelection([f1], [bad], { mode: 'single', accept: 'image/*' });
  assert.deepEqual(res.files, [f1]);
  assert.deepEqual(res.added, []);
  assert.deepEqual(res.rejectedAccept, [bad]);
  assert.equal(res.shouldSyncInput, true);
});
