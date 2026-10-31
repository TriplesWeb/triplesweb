const fs=require('node:fs'),vm=require('node:vm'),context={tailwind:{}};
vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/tailwind.config.js','utf8'),context);
module.exports={...context.tailwind.config,content:[
 './src/main/resources/templates/layout/**/*.mustache',
 './src/main/resources/templates/partials/**/*.mustache',
 './src/main/resources/templates/cosmo/objekt.mustache',
 './src/main/resources/static/js/objekt-issued.js',
 './src/main/resources/static/js/objekt-serial.js'
]};
