// Rebuild after editing Gravity templates or shared header classes.
// Usage from repository root: node scripts/build-gravity-css.cjs
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gravity-css-'));
try {
  const input = path.join(dir, 'input.css');
  const output = path.join(dir, 'output.css');
  fs.writeFileSync(input, '@tailwind base;\n@tailwind components;\n@tailwind utilities;\n');
  execFileSync('npx', ['--yes', '--package=tailwindcss@3.4.17', 'tailwindcss',
    '--config', 'scripts/tailwind-gravity.config.cjs', '--input', input,
    '--output', output, '--minify'], { stdio: 'inherit' });
  const base = fs.readFileSync('src/main/resources/static/css/style.css', 'utf8')
    .replace(/^@charset.*$/igm, '').replace(/^@import.*$/igm, '');
  const icons = `@font-face{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-display:block;src:url('/fonts/gravity-icons.ttf') format('truetype')}\n`;
  fs.writeFileSync('src/main/resources/static/css/gravity.css',
    '/* Generated with scripts/build-gravity-css.cjs; do not edit directly. */\n'
      + fs.readFileSync(output, 'utf8') + '\n' + icons + base
      + fs.readFileSync('src/main/resources/static/css/about-admin.css', 'utf8')
      + fs.readFileSync('src/main/resources/static/css/gravity-admin.css', 'utf8'));
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
