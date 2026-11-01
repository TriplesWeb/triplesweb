// Rebuild after editing templates, shared layout, or style.css.
// Usage from repository root: node scripts/build-site-css.cjs
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'site-css-'));
try {
  const input = path.join(dir, 'input.css');
  const output = path.join(dir, 'output.css');
  fs.writeFileSync(input, '@tailwind base;\n@tailwind components;\n@tailwind utilities;\n');
  execFileSync('npx', ['--yes', '--package=tailwindcss@3.4.17', 'tailwindcss',
    '--config', 'scripts/tailwind-site.config.cjs', '--input', input,
    '--output', output, '--minify'], { stdio: 'inherit' });
  const base = fs.readFileSync('src/main/resources/static/css/style.css', 'utf8')
    .replace(/^@charset.*$/igm, '').replace(/^@import.*$/igm, '');
  const icons = `@font-face{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-display:swap;src:url('/fonts/site-icons.ttf') format('truetype')}\n`;
  const outPath = 'src/main/resources/static/css/site.css';
  // about-admin.css is linked only on admin-capable pages (not every visitor).
  fs.writeFileSync(outPath,
    '/* Generated with scripts/build-site-css.cjs; do not edit directly. */\n'
      + fs.readFileSync(output, 'utf8') + '\n' + icons + base);
  console.log('Wrote', outPath);
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
