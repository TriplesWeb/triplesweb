// Download a Material Symbols Outlined subset used across the site.
// Usage: node scripts/download-site-icons.cjs
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const https = require('node:https');
const path = require('node:path');

const ICONS = [
  'account_circle','add','add_box','add_circle','admin_panel_settings','album','arrow_back','arrow_drop_down',
  'arrow_forward','article','calendar_month','calendar_today','campaign','chat_bubble_outline',
  'chevron_left','chevron_right','close','delete','edit','edit_calendar','edit_note','error',
  'event','expand_more','favorite','format_list_bulleted','forum','graphic_eq','grid_view',
  'group','groups','home','image','inbox','layers','link','live_tv','local_fire_department','location_on','logout',
  'menu','movie','notes','person','person_off','person_search','photo_library','play_arrow',
  'play_circle','refresh','restart_alt','search','search_off','send','shopping_bag','sort','videocam_off',
  'visibility'
];

const UA = 'Mozilla/5.0 (compatible; triplesweb-icon-subset/1.0)';

function getWithHttps(url, headers = {}) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        return getWithHttps(res.headers.location, headers).then(resolve, reject);
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} for ${url}`));
          return;
        }
        resolve(Buffer.concat(chunks));
      });
    }).on('error', reject);
  });
}

function getWithCurl(url) {
  const result = spawnSync('curl', ['-fsSL', '-A', UA, url], { encoding: null, maxBuffer: 20 * 1024 * 1024 });
  if (result.status !== 0) {
    throw new Error((result.stderr && result.stderr.toString()) || `curl failed for ${url}`);
  }
  return Buffer.from(result.stdout);
}

async function get(url, headers = {}) {
  try {
    return await getWithHttps(url, headers);
  } catch (err) {
    console.warn('https.get failed, falling back to curl:', err.message);
    return getWithCurl(url);
  }
}

(async () => {
  const cssUrl = 'https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0&icon_names='
    + ICONS.join(',');
  const css = (await get(cssUrl, { 'User-Agent': UA })).toString('utf8');
  const match = css.match(/url\((https:\/\/[^)]+)\)\s+format\('truetype'\)/)
    || css.match(/url\((https:\/\/[^)]+\.ttf[^)]*)\)/)
    || css.match(/url\((https:\/\/fonts\.gstatic\.com[^)]+)\)/);
  if (!match) {
    console.error('CSS response:\n', css.slice(0, 500));
    throw new Error('Could not find font URL in Google Fonts CSS');
  }
  const fontUrl = match[1].replace(/['"]/g, '');
  const font = await get(fontUrl);
  const outDir = 'src/main/resources/static/fonts';
  const outFile = path.join(outDir, 'site-icons.ttf');
  fs.writeFileSync(outFile, font);
  // Keep gravity pages working with the same subset.
  fs.copyFileSync(outFile, path.join(outDir, 'gravity-icons.ttf'));
  const licenseSrc = path.join(outDir, 'gravity-icons.LICENSE');
  if (fs.existsSync(licenseSrc)) {
    fs.copyFileSync(licenseSrc, path.join(outDir, 'site-icons.LICENSE'));
  }
  console.log('Wrote', outFile, `(${font.length} bytes) from`, fontUrl);
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
