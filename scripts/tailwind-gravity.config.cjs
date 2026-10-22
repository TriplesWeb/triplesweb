const fs = require('node:fs');
const vm = require('node:vm');
const context = { tailwind: {} };
vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/tailwind.config.js', 'utf8'), context);
module.exports = {
  ...context.tailwind.config,
  content: [
    './src/main/resources/templates/layout/header.mustache',
    './src/main/resources/templates/layout/footer.mustache',
    './src/main/resources/templates/cosmo/gravity*.mustache',
    './src/main/resources/templates/cosmo/partials/gravity*.mustache',
    './src/main/resources/static/js/gravity-connect.js'
  ]
};
