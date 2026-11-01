// Resolve the local Tailwind v4 CLI from node_modules (no npx download).
const fs = require('node:fs');
const path = require('node:path');

function resolveTailwindCli() {
  const candidates = [
    path.join(__dirname, '..', 'node_modules', '@tailwindcss', 'cli', 'dist', 'index.mjs'),
    path.join(__dirname, '..', 'node_modules', '@tailwindcss', 'cli', 'dist', 'index.js')
  ];
  for (const cli of candidates) {
    if (fs.existsSync(cli)) return cli;
  }
  throw new Error(
    '@tailwindcss/cli가 없습니다. 저장소 루트에서 npm ci (또는 npm install) 후 다시 실행하세요.'
  );
}

module.exports = { resolveTailwindCli };
