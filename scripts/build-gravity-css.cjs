// Rebuild after editing Gravity templates or shared header classes.
// Usage from repository root: npm run build:css:gravity
const { buildTailwindBundle } = require('./build-tw4.cjs');

buildTailwindBundle({
  name: 'gravity',
  inputCss: 'scripts/css/gravity.input.css',
  outPath: 'src/main/resources/static/css/gravity.css',
  iconFace: `@font-face{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-display:block;src:url('../fonts/site-icons.ttf') format('truetype')}\n`,
  // Preserve exact values using forms that CSS editors can parse reliably.
  formatOutput: (css) => css
    .replaceAll('oklch(0% 0 0/.4)', 'rgba(0,0,0,.4)')
    .replaceAll('border-radius:3.40282e38px', 'border-radius:340282000000000000000000000000000000000px'),
  extras: [
    'src/main/resources/static/css/unit-checks.css',
    'src/main/resources/static/css/about-admin.css',
    'src/main/resources/static/css/gravity-admin.css'
  ]
});
