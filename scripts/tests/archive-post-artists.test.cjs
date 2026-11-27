const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

const root = resolve(__dirname, '../..');
const manage = readFileSync(resolve(root, 'src/main/resources/static/js/archive-manage.js'), 'utf8');
const detail = readFileSync(resolve(root, 'src/main/resources/static/js/archive-detail.js'), 'utf8');
const card = readFileSync(resolve(root, 'src/main/resources/templates/partials/archive-photo-card.mustache'), 'utf8');

test('archive UI does not expose legacy per-image artists', () => {
  assert.doesNotMatch(manage, /imageArtistsJson|dataset\.imageArtists/);
  assert.doesNotMatch(detail, /dataset\.imageArtists/);
  assert.doesNotMatch(card, /data-image-artists/);
});
