// Fail if committed generated CSS differs from a fresh build.
const { execFileSync } = require('node:child_process');
const path = require('node:path');

const root = path.join(__dirname, '..');
const outputs = [
  'src/main/resources/static/css/site.css',
  'src/main/resources/static/css/triples.css',
  'src/main/resources/static/css/objekt.css',
  'src/main/resources/static/css/gravity.css'
];

execFileSync(process.execPath, [path.join(__dirname, 'build-css.cjs')], {
  cwd: root,
  stdio: 'inherit'
});

const status = execFileSync('git', ['status', '--porcelain', '--', ...outputs], {
  cwd: root,
  encoding: 'utf8'
}).trim();

if (status) {
  console.error('Generated CSS is out of date. Run npm run build:css and commit the result.\n');
  console.error(status);
  process.exit(1);
}

console.log('CSS drift check passed.');
