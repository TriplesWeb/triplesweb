// V2 branch only. Freeze tracked legacy bundles until the relevant pages are replaced.
// To regenerate legacy CSS intentionally: V2_BUILD_LEGACY_CSS=1 npm run build:css
const {execFileSync}=require("node:child_process"),path=require("node:path");
const root=path.join(__dirname,"..");
const jobs=process.env.V2_BUILD_LEGACY_CSS==="1"?["build-site-css.cjs","build-triples-css.cjs","build-objekt-css.cjs","build-gravity-css.cjs"]:[];
jobs.push("build-v2-css.cjs");
for(const name of jobs){console.log("\n=== "+name+" ===");execFileSync(process.execPath,[path.join(__dirname,name),...process.argv.slice(2)],{cwd:root,stdio:"inherit"})}
console.log("\nV2 CSS updated (legacy assets frozen unless opted in).");
