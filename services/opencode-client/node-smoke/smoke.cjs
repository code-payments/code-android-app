// CI's Node smoke test: plain `node` loads the Kotlin/JS library build of :services:opencode-client
// and, when OCP_LIVE=1, makes one read call (rates over StreamLiveMintData) to production.
//
//   node smoke.cjs <path to the library's .js entry>
const path = require('node:path');

const entry = process.argv[2];
if (!entry) {
  console.error('usage: node smoke.cjs <library .js entry>');
  process.exit(2);
}
// Kotlin/JS nests @JsExport declarations under their package.
const smokeFetchRates = require(path.resolve(entry)).com?.getcode?.opencode?.client?.smokeFetchRates;
if (typeof smokeFetchRates !== 'function') {
  console.error('com.getcode.opencode.client.smokeFetchRates is not exported by', entry);
  process.exit(1);
}
console.log('loaded', entry);

if (process.env.OCP_LIVE !== '1') {
  console.log('OCP_LIVE is not 1; skipping the live rates call');
  process.exit(0);
}
smokeFetchRates(process.env.OCP_HOST || 'ocp-v2.api.flipcash-infra.net').then(
  (summary) => {
    console.log(summary);
    process.exit(0);
  },
  (error) => {
    console.error('rates call failed:', error);
    process.exit(1);
  },
);
