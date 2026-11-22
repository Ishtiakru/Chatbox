// Only cache a static offline notice. Never cache auth, API responses or messages.
const CACHE = 'chatbox-offline-v2';
const OFFLINE = ['/offline.html', '/chatbox-logo.jpg'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(OFFLINE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('chatbox-offline-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.mode !== 'navigate' || url.origin !== self.location.origin || url.pathname !== '/') return;
  event.respondWith(fetch(event.request, { cache: 'no-store' }).catch(async () => (await caches.match('/offline.html')) || Response.error()));
});
