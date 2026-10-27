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
  // CSS 규칙만 바꾼 경우 기존 Tailwind utilities를 재사용한다.
  // 템플릿 class를 바꿨다면 일반 빌드로 다시 스캔해야 한다.
  if (process.argv.includes('--styles-only')) {
    const current = fs.readFileSync('src/main/resources/static/css/triples.css', 'utf8');
    const lines = current.split('\n');
    if (!lines[0].startsWith('/* Generated') || !lines[1].startsWith('*,')) {
      throw new Error('기존 Tailwind utilities를 찾을 수 없습니다. 일반 빌드를 실행하세요.');
    }
    fs.writeFileSync(output, lines[1]);
  } else {
    execFileSync('npx', ['--yes', '--package=tailwindcss@3.4.17', 'tailwindcss',
      '--config', 'scripts/tailwind-triples.config.cjs', '--input', input,
      '--output', output, '--minify'], { stdio: 'inherit' });
  }
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
