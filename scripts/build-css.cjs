// Orchestrate all Tailwind CSS bundles.
// Usage from repository root: npm run build:css
const { execFileSync } = require('node:child_process');
const path = require('node:path');

const root = path.join(__dirname, '..');
const scripts = [
  'build-site-css.cjs',
  'build-triples-css.cjs',
  'build-objekt-css.cjs',
  'build-gravity-css.cjs',
  'build-v2-css.cjs'
];

for (const name of scripts) {
  console.log(`\n=== ${name} ===`);
  execFileSync(process.execPath, [path.join(__dirname, name), ...process.argv.slice(2)], {
    cwd: root,
    stdio: 'inherit'
  });
}

console.log('\nAll CSS bundles updated.');
