const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const src = fs.readFileSync(
  path.resolve(__dirname, '../../src/main/resources/static/js/date-range-filter.js'),
  'utf8'
);

assert.match(src, /const shouldBindCalendar = /, 'shouldBindCalendar 가드가 있어야 한다');
assert.match(src, /data-ar-cal/, 'data-ar-cal opt-in 이 있어야 한다');
assert.match(src, /\[data-date-filter\], \.ar-date-field/, '아카이브 컨테이너 스코프가 있어야 한다');
assert.match(src, /if \(!shouldBindCalendar\(input\)\) return;/, 'bind 전에 스코프 검사가 있어야 한다');

/** date-range-filter.js 의 shouldBindCalendar 와 동일 규칙 */
function shouldBindCalendar(input) {
  if (!(input instanceof Object) || input.type !== 'date') return false;
  if (input.dataset.nativeDate != null || input.getAttribute('data-ar-cal') === '0') return false;
  if (input.hasAttribute('data-ar-cal')) return true;
  if (input.hasAttribute('data-date-exact')
    || input.hasAttribute('data-date-from')
    || input.hasAttribute('data-date-to')) {
    return true;
  }
  return Boolean(input.closest('[data-date-filter], .ar-date-field, [data-ar-cal-root]'));
}

function fakeInput(attrs = {}, closestResult = null) {
  const dataset = {};
  if (Object.prototype.hasOwnProperty.call(attrs, 'data-native-date')) {
    dataset.nativeDate = attrs['data-native-date'];
  }
  if (Object.prototype.hasOwnProperty.call(attrs, 'data-ar-cal')) {
    dataset.arCal = attrs['data-ar-cal'];
  }
  return {
    type: attrs.type || 'date',
    dataset,
    hasAttribute: (name) => Object.prototype.hasOwnProperty.call(attrs, name),
    getAttribute: (name) => (Object.prototype.hasOwnProperty.call(attrs, name) ? attrs[name] : null),
    closest: () => closestResult
  };
}

assert.equal(shouldBindCalendar(fakeInput({ 'data-ar-cal': '' })), true);
assert.equal(shouldBindCalendar(fakeInput({ 'data-date-from': '' })), true);
assert.equal(shouldBindCalendar(fakeInput({}, { tag: 'ar-date-field' })), true);
assert.equal(shouldBindCalendar(fakeInput({})), false);
assert.equal(shouldBindCalendar(fakeInput({ 'data-ar-cal': '0' })), false);
assert.equal(shouldBindCalendar(fakeInput({ type: 'text', 'data-ar-cal': '' })), false);

const archiveField = fs.readFileSync(
  path.resolve(__dirname, '../../src/main/resources/templates/partials/archive-date-field.mustache'),
  'utf8'
);
assert.match(archiveField, /data-ar-cal/, '아카이브 등록일 필드는 data-ar-cal 을 붙여야 한다');

const filterField = fs.readFileSync(
  path.resolve(__dirname, '../../src/main/resources/templates/partials/date-range-filter.mustache'),
  'utf8'
);
assert.match(filterField, /data-date-from data-ar-cal/, '필터 시작일도 data-ar-cal 이 있어야 한다');

console.log('date-calendar-scope: ok');
