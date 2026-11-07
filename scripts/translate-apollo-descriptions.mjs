/**
 * data/apollo-objekt-descriptions.json 고유 영어 description → 한글.
 * Google gtx 엔드포인트 사용 (MyMemory 429 회피).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outPath = path.join(root, 'data/apollo-objekt-descriptions.json');
const cachePath = path.join(root, 'data/apollo-translate-cache.json');

const seed = JSON.parse(fs.readFileSync(outPath, 'utf8'));
const cache = fs.existsSync(cachePath) ? JSON.parse(fs.readFileSync(cachePath, 'utf8')) : {};

const KEEP = [
  'tripleS', 'byteS', 'COSMO', 'Atom01', 'Atom02', 'Binary01', 'Binary02',
  'Cream01', 'Cream02', 'Divine01', 'Ever01', 'LOVE&POP', 'Love&Pop', 'ASSEMBLE25',
  'ASSEMBLE24', 'ASSEMBLE', 'EVOLution', 'LOVElution', '+(KR)ystal Eyes', 'Acid Angel from Asia',
];

function looksKorean(value) {
  const hangul = (value.match(/[가-힣]/g) || []).length;
  if (!hangul) return false;
  const latin = (value.match(/[A-Za-z]/g) || []).length;
  return hangul >= latin;
}

function protect(source) {
  let text = source;
  const tokens = [];
  for (const keep of KEEP) {
    let index = 0;
    while (true) {
      const found = text.indexOf(keep, index);
      if (found < 0) break;
      const marker = `[[T${tokens.length}]]`;
      tokens.push(keep);
      text = text.slice(0, found) + marker + text.slice(found + keep.length);
      index = found + marker.length;
    }
  }
  return { text, tokens };
}

function restore(translated, tokens) {
  let text = translated;
  tokens.forEach((token, i) => {
    text = text.replaceAll(`[[T${i}]]`, token).replaceAll(`[[t${i}]]`, token);
  });
  return text;
}

async function translateOnce(en) {
  const protectedText = protect(en);
  let query = protectedText.text;
  if (query.length > 900) query = query.slice(0, 900);
  const url =
    'https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ko&dt=t&q=' +
    encodeURIComponent(query);
  const res = await fetch(url, {
    headers: { 'User-Agent': 'Mozilla/5.0 triplesweb-description-import' },
  });
  if (!res.ok) throw new Error(`translate HTTP ${res.status}`);
  const body = await res.json();
  const parts = Array.isArray(body?.[0]) ? body[0] : [];
  let translated = parts.map((p) => (Array.isArray(p) ? p[0] : '')).join('').trim();
  if (!translated || translated.toLowerCase() === query.toLowerCase()) {
    throw new Error('empty/same translation');
  }
  return restore(translated, protectedText.tokens);
}

async function translate(en) {
  if (cache[en]) return cache[en];
  if (looksKorean(en)) {
    cache[en] = en;
    return en;
  }
  let delay = 1000;
  for (let attempt = 1; attempt <= 5; attempt++) {
    try {
      const translated = await translateOnce(en);
      cache[en] = translated;
      return translated;
    } catch (error) {
      if (attempt < 5) {
        await new Promise((r) => setTimeout(r, delay));
        delay *= 2;
        continue;
      }
      throw error;
    }
  }
}

const unique = [...new Set(seed.entries.map((e) => e.en).filter(Boolean))];
const pending = unique.filter((en) => !cache[en]);
console.log(`entries=${seed.entries.length} uniqueEn=${unique.length} pending=${pending.length}`);

let fail = 0;
for (let i = 0; i < pending.length; i++) {
  const en = pending[i];
  try {
    await translate(en);
  } catch (error) {
    fail += 1;
    console.warn(`fail ${i + 1}/${pending.length}: ${en.slice(0, 60)}... (${error.message})`);
  }
  if ((i + 1) % 20 === 0 || i + 1 === pending.length) {
    fs.writeFileSync(cachePath, JSON.stringify(cache, null, 2));
    console.log(`progress ${i + 1}/${pending.length} cached=${Object.keys(cache).length} fail=${fail}`);
  }
  await new Promise((r) => setTimeout(r, 200));
}

for (const entry of seed.entries) {
  entry.ko = cache[entry.en] || '';
}

seed.meta = {
  ...seed.meta,
  translated: seed.entries.filter((e) => e.ko).length,
  uniqueEn: unique.length,
  translationCache: Object.keys(cache).length,
  translator: 'google-gtx',
};

fs.writeFileSync(cachePath, JSON.stringify(cache, null, 2));
fs.writeFileSync(outPath, JSON.stringify(seed, null, 2));
console.log(`done translated=${seed.meta.translated}/${seed.entries.length} failUnique=${fail}`);
