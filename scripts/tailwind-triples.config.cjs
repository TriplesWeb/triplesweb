const fs = require('node:fs');
const vm = require('node:vm');
const context = { tailwind: {} };
vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/tailwind.config.js', 'utf8'), context);
module.exports = {
  ...context.tailwind.config,
  content: [
    './src/main/resources/templates/layout/**/*.mustache',
    './src/main/resources/templates/triples/**/*.mustache',
    './src/main/resources/templates/partials/**/*.mustache',
    './src/main/resources/static/js/artist.js',
    './src/main/resources/static/js/album.js',
    './src/main/resources/static/js/unit.js',
    './src/main/resources/static/js/chemistry.js',
    './src/main/resources/static/js/about-*.js',
    './src/main/resources/static/js/member-portrait-picker.js'
  ]
};
