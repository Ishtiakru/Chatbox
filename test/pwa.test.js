import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import vm from 'node:vm';

test('install manifest uses real icons and the root scope', () => {
  const manifest = JSON.parse(readFileSync('public/manifest.webmanifest', 'utf8'));
  assert.equal(manifest.start_url, '/');
  assert.equal(manifest.scope, '/');
  assert.deepEqual(manifest.icons.map(icon => icon.sizes), ['192x192', '512x512']);
  for (const icon of manifest.icons) assert.ok(existsSync(`public${icon.src}`));
});
test('offline worker never intercepts auth, chat data, or foreign origins', async () => {
  const listeners = {};
  const fallback = { status: 200, kind: 'offline-page' };
  const self = { location:{origin:'https://example.test'}, addEventListener:(type, fn) => { listeners[type] = fn; } };
  const caches = { match: async path => path === '/offline.html' ? fallback : undefined };
  vm.runInNewContext(readFileSync('public/sw.js', 'utf8'), {self, caches, URL, fetch: async () => { throw new Error('offline'); }, Response});
  for (const request of [
    {url:'https://example.test/api/chat',mode:'cors'},
    {url:'https://example.test/__/auth/handler',mode:'navigate'},
    {url:'https://other.test/',mode:'navigate'},
    {url:'https://example.test/assets/app.js',mode:'no-cors'},
  ]) {
    let intercepted = false;
    listeners.fetch({request, respondWith:() => {intercepted = true;}});
    assert.equal(intercepted, false, request.url);
  }
  let response;
  listeners.fetch({request:{url:'https://example.test/', mode:'navigate'}, respondWith: promise => {response = promise;}});
  assert.equal(await response, fallback);
});
