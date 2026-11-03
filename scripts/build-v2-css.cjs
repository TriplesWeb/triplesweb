// Build a v2-only Tailwind utility layer; never append legacy style.css or daisyUI themes.
const fs=require("node:fs");const {execFileSync}=require("node:child_process");const {resolveTailwindCli}=require("./tailwind-cli.cjs");
const out="src/main/resources/static/css/v2-utility.css";
execFileSync(process.execPath,[resolveTailwindCli(),"-i","scripts/css/v2.input.css","-o",out,"--minify"],{stdio:"inherit"});
const icons="@font-face{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-display:swap;src:url('/fonts/site-icons.ttf') format('truetype')}\n.material-symbols-outlined{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-size:20px;line-height:1;letter-spacing:normal;text-transform:none;white-space:nowrap;word-wrap:normal;direction:ltr;font-feature-settings:'liga';-webkit-font-smoothing:antialiased}\n";
fs.appendFileSync(out,"\n"+icons);
console.log("Generated",out,fs.statSync(out).size,"bytes");
