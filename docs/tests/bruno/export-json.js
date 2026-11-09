// Exports the Bruno collection to Postman v2.1 JSON files in docs/tests/json/ (TEST-1 deliverable).
// One file per service folder and one for e2e, plus the two environments.
// The test scripts stay in Bruno syntax (bru.*, res.*): import the files back into Bruno to run them,
// or open them in Postman to read the requests.
//   npm run export
const fs = require('fs');
const path = require('path');
const { createCollectionJsonFromPathname } = require('@usebruno/cli/src/utils/collection');
const { brunoToPostman } = require('@usebruno/converters');

const root = __dirname;
const out = path.resolve(root, '..', 'json');
fs.mkdirSync(out, { recursive: true });

const collection = createCollectionJsonFromPathname(root);
const folder = (items, name) => {
  const found = items.find((i) => i.type === 'folder' && i.name === name);
  if (!found) throw new Error(`Folder not found: ${name}`);
  return found;
};

const write = (file, json) => {
  fs.writeFileSync(path.join(out, file), JSON.stringify(json, null, 2) + '\n');
  console.log(`docs/tests/json/${file}`);
};

const exportFolder = (name, items) => {
  const postman = brunoToPostman({ ...collection, name: `Monster Tamers - ${name}`, items }, { preserveScripts: true });
  write(`${name.replace(/\//g, '-')}.postman_collection.json`, postman);
};

const services = folder(collection.items, 'services');
for (const svc of ['auth', 'players', 'monsters', 'teams', 'encounters', 'history']) {
  exportFolder(`services-${svc}`, folder(services.items, svc).items);
}
exportFolder('e2e', folder(collection.items, 'e2e').items);

// Environments: plain key/value pairs read from environments/*.bru
for (const file of fs.readdirSync(path.join(root, 'environments'))) {
  if (!file.endsWith('.bru')) continue;
  const name = path.basename(file, '.bru');
  const text = fs.readFileSync(path.join(root, 'environments', file), 'utf8');
  const values = [...text.matchAll(/^\s+(\w+):\s*(\S+)\s*$/gm)].map(([, key, value]) => ({ key, value, enabled: true }));
  values.push({ key: 'TESTS_CLIENT_SECRET', value: '', enabled: true });
  write(`${name}.postman_environment.json`, { name, values, _postman_variable_scope: 'environment' });
}
