/**
 * Apollo GET /api/objekts/metadata/{slug} 의 data.description 만 수집한다.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const targetsPath = path.join(root, 'data/apollo-description-targets.json');
const outPath = path.join(root, 'data/apollo-objekt-descriptions.json');
const progressPath = path.join(root, 'data/apollo-scrape-progress.json');

const targets = JSON.parse(fs.readFileSync(targetsPath, 'utf8'));
const progress = fs.existsSync(progressPath)
  ? JSON.parse(fs.readFileSync(progressPath, 'utf8'))
  : { done: {}, failed: [] };

const concurrency = Number(process.env.FETCH_CONCURRENCY || 6);
const delayMs = Number(process.env.FETCH_DELAY_MS || 80);
const pending = targets.filter((t) => !progress.done[t.slug]);

console.log(`targets=${targets.length} pending=${pending.length}`);

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function fetchOne(target) {
  const url = `https://apollo.cafe/api/objekts/metadata/${encodeURIComponent(target.slug)}`;
  const res = await fetch(url, {
    headers: {
      Accept: 'application/json',
      Referer: `https://apollo.cafe/?id=${target.slug}`,
      'User-Agent': 'Mozilla/5.0 triplesweb-description-import',
    },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const body = await res.json();
  const en = String(body?.data?.description || '').trim();
  if (!en) throw new Error('empty description');
  progress.done[target.slug] = {
    descriptionKey: target.descriptionKey,
    season: target.season,
    collectionNo: target.collectionNo,
    slug: target.slug,
    en,
  };
}

function save() {
  const entries = Object.values(progress.done);
  // dedupe failed by slug keeping last
  const failedBySlug = new Map();
  for (const f of progress.failed) failedBySlug.set(f.slug, f);
  progress.failed = [...failedBySlug.values()].filter((f) => !progress.done[f.slug]);
  fs.writeFileSync(progressPath, JSON.stringify(progress, null, 2));
  fs.writeFileSync(
    outPath,
    JSON.stringify(
      {
        meta: {
          source: 'apollo.cafe /api/objekts/metadata/{slug}',
          field: 'data.description',
          collected: entries.length,
          failed: progress.failed.length,
          totalTargets: targets.length,
        },
        entries,
      },
      null,
      2
    )
  );
}

let ok = 0;
let fail = 0;
for (let i = 0; i < pending.length; i += concurrency) {
  const batch = pending.slice(i, i + concurrency);
  await Promise.all(
    batch.map(async (t) => {
      try {
        await fetchOne(t);
        ok += 1;
      } catch (error) {
        progress.failed.push({ slug: t.slug, error: String(error.message || error) });
        fail += 1;
      }
    })
  );
  save();
  console.log(`progress ${Math.min(i + concurrency, pending.length)}/${pending.length} ok=${ok} fail=${fail}`);
  await sleep(delayMs);
}

save();
console.log(`done collected=${Object.keys(progress.done).length} failed=${progress.failed.length}`);
