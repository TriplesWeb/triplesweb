// Shared Tailwind v4 + daisyUI build helper.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { resolveTailwindCli } = require('./tailwind-cli.cjs');

function buildTailwindBundle({ name, inputCss, outPath, extras = [], iconFace = null }) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `${name}-css-`));
  try {
    const output = path.join(dir, 'output.css');
    const cli = resolveTailwindCli();
    const args = [cli, '-i', inputCss, '-o', output, '--minify'];
    execFileSync(process.execPath, args, { stdio: 'inherit' });

    const base = fs.readFileSync('src/main/resources/static/css/style.css', 'utf8')
      .replace(/^@charset.*$/igm, '')
      .replace(/^@import.*$/igm, '');

    let extrasCss = '';
    for (const file of extras) {
      extrasCss += fs.readFileSync(file, 'utf8');
    }

    const header = `/* Generated with scripts/build-${name}-css.cjs; do not edit directly. */\n`;
    fs.writeFileSync(outPath, header + fs.readFileSync(output, 'utf8') + '\n'
      + (iconFace || '') + base + extrasCss);
    console.log('Wrote', outPath);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

module.exports = { buildTailwindBundle };
