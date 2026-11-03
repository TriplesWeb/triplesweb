// Build independent CSS layers without injecting legacy color/shape tokens.
"use strict";
const fs=require("node:fs"),{execFileSync}=require("node:child_process"),{resolveTailwindCli}=require("./tailwind-cli.cjs");
const output="src/main/resources/static/css/v2-utility.css";
execFileSync(process.execPath,[resolveTailwindCli(),"-i","scripts/css/v2.input.css","-o",output,"--minify"],{stdio:"inherit"});
const icon="@font-face{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-display:swap;src:url('/fonts/site-icons.ttf') format('truetype')}\n.material-symbols-outlined{font-family:'Material Symbols Outlined';font-style:normal;font-weight:400;font-size:20px;line-height:1;letter-spacing:normal;text-transform:none;white-space:nowrap;word-wrap:normal;direction:ltr;font-feature-settings:'liga';-webkit-font-smoothing:antialiased}\n";
fs.appendFileSync(output,"\n"+icon);
execFileSync(process.execPath,["scripts/build-v2-structure.cjs"],{stdio:"inherit"});
console.log("Generated independent v2 CSS layers.");
