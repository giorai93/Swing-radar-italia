const assert = require('assert');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
function rows(name) {
  return fs.readFileSync(path.join(root, 'assets', name), 'utf8').trim().split(/\r?\n/).slice(1).map(x => x.split(','));
}
const base = rows('italian_stocks.csv');
const expanded = rows('italian_expanded.csv');
const tickers = new Set([...base, ...expanded].map(x => x[0]));
assert.strictEqual(base.length, 84, 'Previous universe must be retained');
assert(expanded.length >= 100, 'Offline fallback should expand the universe');
assert(tickers.size > base.length, 'At least some companies must be new');
assert(tickers.has('STLAM'), 'Stellantis must not be removed');
assert(expanded.some(x => x[2] === 'MID'), 'Mid Cap tier missing');
assert(expanded.some(x => x[2] === 'SMALL'), 'Small Cap tier missing');
assert(expanded.every(x => ['LARGE','MID','SMALL'].includes(x[2])), 'Invalid tier');
console.log(`OK: ${base.length} original + ${expanded.length} screen snapshot = ${tickers.size} unique Milano symbols; STLAM preserved`);
