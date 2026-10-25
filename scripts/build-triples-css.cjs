// Rebuild after editing About triples templates or shared layout classes.
// Usage from repository root: node scripts/build-triples-css.cjs
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'triples-css-'));
try {
  const input = path.join(dir, 'input.css');
  const output = path.join(dir, 'output.css');
  fs.writeFileSync(input, '@tailwind base;\n@tailwind components;\n@tailwind utilities;\n');
  execFileSync('npx', ['--yes', '--package=tailwindcss@3.4.17', 'tailwindcss',
    '--config', 'scripts/tailwind-triples.config.cjs', '--input', input,
    '--output', output, '--minify'], { stdio: 'inherit' });
  const base = fs.readFileSync('src/main/resources/static/css/style.css', 'utf8')
    .replace(/^@charset.*$/igm, '').replace(/^@import.*$/igm, '');
  const outPath = 'src/main/resources/static/css/triples.css';
  fs.writeFileSync(outPath,
    '/* Generated with scripts/build-triples-css.cjs; do not edit directly. */\n'
      + fs.readFileSync(output, 'utf8') + '\n' + base
      + fs.readFileSync('src/main/resources/static/css/about-admin.css', 'utf8')
      + fs.readFileSync('src/main/resources/static/css/album-detail.css', 'utf8')
      + fs.readFileSync('src/main/resources/static/css/artist.css', 'utf8')
      + fs.readFileSync('src/main/resources/static/css/chemistry.css', 'utf8')
      + fs.readFileSync('src/main/resources/static/css/member-portrait-picker.css', 'utf8'));
  console.log('Wrote', outPath);
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
