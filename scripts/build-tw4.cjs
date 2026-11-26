// Shared Tailwind v4 + daisyUI build helper.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { resolveTailwindCli } = require('./tailwind-cli.cjs');

function buildTailwindBundle({ name, inputCss, outPath, extras = [], iconFace = null, formatOutput = null }) {
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
    // Lightning CSS can round computed OKLab coordinates differently across platforms.
    const compiled = fs.readFileSync(output, 'utf8').replace(
      /\boklab\(([\d.]+%) (-?[\d.]+) (-?[\d.]+)(?=[ /)])/g,
      (_, lightness, a, b) => `oklab(${lightness} ${Number(Number(a).toFixed(5))} ${Number(Number(b).toFixed(5))}`
    );
    fs.writeFileSync(outPath, header + (formatOutput ? formatOutput(compiled) : compiled) + '\n'
      + (iconFace || '') + base + extrasCss);
    console.log('Wrote', outPath);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

module.exports = { buildTailwindBundle };
