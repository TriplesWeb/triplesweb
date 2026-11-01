/* 브라우저에서 Tailwind를 재계산하지 않도록 목록과 동적 카드의 스타일을 미리 만든다. */
const { buildTailwindBundle } = require('./build-tw4.cjs');

buildTailwindBundle({
  name: 'objekt',
  inputCss: 'scripts/css/objekt.input.css',
  outPath: 'src/main/resources/static/css/objekt.css'
});
