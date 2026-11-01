// Rebuild after editing Gravity templates or shared header classes.
// Usage from repository root: npm run build:css:gravity
const { buildTailwindBundle } = require('./build-tw4.cjs');

buildTailwindBundle({
  name: 'gravity',
  inputCss: 'scripts/css/gravity.input.css',
  outPath: 'src/main/resources/static/css/gravity.css',
  iconFace: `@font-face{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-display:block;src:url('/fonts/site-icons.ttf') format('truetype')}\n`,
  extras: [
    'src/main/resources/static/css/about-admin.css',
    'src/main/resources/static/css/gravity-admin.css'
  ]
});
