// Rebuild after editing About triples templates or shared layout classes.
// Usage from repository root: npm run build:css:triples
const { buildTailwindBundle } = require('./build-tw4.cjs');

buildTailwindBundle({
  name: 'triples',
  inputCss: 'scripts/css/triples.input.css',
  outPath: 'src/main/resources/static/css/triples.css',
  extras: [
    'src/main/resources/static/css/about-admin.css',
    'src/main/resources/static/css/album-detail.css',
    'src/main/resources/static/css/artist.css',
    'src/main/resources/static/css/chemistry.css',
    'src/main/resources/static/css/member-portrait-picker.css'
  ]
});
