const fs = require('node:fs');
const vm = require('node:vm');
const context = { tailwind: {} };
vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/tailwind.config.js', 'utf8'), context);
module.exports = {
  ...context.tailwind.config,
  content: [
    './src/main/resources/templates/**/*.mustache',
    './src/main/resources/static/js/**/*.js'
  ]
};
