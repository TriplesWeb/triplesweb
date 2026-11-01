// Rebuild after editing templates, shared layout, or style.css.
// Usage from repository root: npm run build:css:site
const { buildTailwindBundle } = require('./build-tw4.cjs');

buildTailwindBundle({
  name: 'site',
  inputCss: 'scripts/css/site.input.css',
  outPath: 'src/main/resources/static/css/site.css',
  iconFace: `@font-face{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-display:swap;src:url('/fonts/site-icons.ttf') format('truetype')}\n`
});
