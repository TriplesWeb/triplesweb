const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

const root = resolve(__dirname, '../..');
const controller = readFileSync(resolve(root, 'src/main/java/com/example/triplesweb/controller/ArchiveController.java'), 'utf8');
const body = readFileSync(resolve(root, 'src/main/resources/templates/partials/archive-card-body.mustache'), 'utf8');
const manage = readFileSync(resolve(root, 'src/main/resources/static/js/archive-manage.js'), 'utf8');
const detail = readFileSync(resolve(root, 'src/main/resources/static/js/archive-detail.js'), 'utf8');
const card = readFileSync(resolve(root, 'src/main/resources/templates/partials/archive-photo-card.mustache'), 'utf8');

test('archive uses the post cast across the list and detail', () => {
  assert.doesNotMatch(controller, /getImageArtistsJson|imageCasts/);
  assert.doesNotMatch(body, /imageCasts|data-image-cast/);
  assert.doesNotMatch(manage, /imageArtistsJson|dataset\.imageArtists/);
  assert.doesNotMatch(detail, /dataset\.imageArtists|data-image-cast/);
  assert.doesNotMatch(card, /data-image-artists/);
});
