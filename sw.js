const CACHE_NAME = 'positively-geared-v5';
const AUDIO_CACHE = 'positively-geared-audio-v5';

const SHELL_FILES = ['./', './index.html', './manifest.json'];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(SHELL_FILES))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys
        .filter(k => k !== CACHE_NAME && k !== AUDIO_CACHE)
        .map(k => caches.delete(k))
      )
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  // Only intercept shell files — let audio play directly (Google Drive handles CORS fine)
  const url = e.request.url;
  if (url.includes('drive.google.com')) return; // let browser handle Google Drive directly

  e.respondWith(
    fetch(e.request)
      .then(r => {
        if (r.ok) caches.open(CACHE_NAME).then(c => c.put(e.request, r.clone()));
        return r;
      })
      .catch(() => caches.match(e.request))
  );
});

// Download a chapter and cache it by chapter ID
self.addEventListener('message', async e => {
  if (e.data?.type === 'DOWNLOAD_CHAPTER') {
    const { url, id } = e.data;
    const cacheKey = 'audio::ch' + id;
    const cache = await caches.open(AUDIO_CACHE);

    const existing = await cache.match(cacheKey);
    if (existing) {
      notifyClients({ type: 'DOWNLOAD_DONE', id, alreadyCached: true });
      return;
    }

    try {
      const response = await fetch(url, { redirect: 'follow' });
      if (response.ok) {
        await cache.put(cacheKey, response.clone());
        notifyClients({ type: 'DOWNLOAD_DONE', id });
      } else {
        notifyClients({ type: 'DOWNLOAD_ERROR', id, status: response.status });
      }
    } catch(err) {
      notifyClients({ type: 'DOWNLOAD_ERROR', id, error: err.message });
    }
  }

  if (e.data?.type === 'CHECK_ALL_CACHED') {
    const cache = await caches.open(AUDIO_CACHE);
    const results = {};
    for (let i = 1; i <= 11; i++) {
      const cached = await cache.match('audio::ch' + i);
      results[i] = !!cached;
    }
    notifyClients({ type: 'CACHE_STATUS', results });
  }
});

async function notifyClients(msg) {
  const clients = await self.clients.matchAll({ includeUncontrolled: true });
  clients.forEach(c => c.postMessage(msg));
}
