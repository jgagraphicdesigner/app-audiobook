const CACHE_NAME = 'positively-geared-v6';
const AUDIO_CACHE = 'positively-geared-audio-v6';
const SHELL_FILES = ['./', './index.html', './manifest.json'];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_NAME)
      .then(c => c.addAll(SHELL_FILES))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k !== CACHE_NAME && k !== AUDIO_CACHE).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const url = e.request.url;
  // Let Dropbox and external audio requests pass through untouched
  if (url.includes('dropbox.com')) return;
  // Shell: network first, cache fallback
  e.respondWith(
    fetch(e.request)
      .then(r => { if(r.ok) caches.open(CACHE_NAME).then(c => c.put(e.request, r.clone())); return r; })
      .catch(() => caches.match(e.request))
  );
});

self.addEventListener('message', async e => {
  if (e.data?.type === 'DOWNLOAD_CHAPTER') {
    const { url, id } = e.data;
    const cacheKey = 'audio::ch' + id;
    const cache = await caches.open(AUDIO_CACHE);
    if (await cache.match(cacheKey)) {
      notifyClients({ type: 'DOWNLOAD_DONE', id, alreadyCached: true }); return;
    }
    try {
      const r = await fetch(url, { redirect: 'follow' });
      if (r.ok) { await cache.put(cacheKey, r.clone()); notifyClients({ type: 'DOWNLOAD_DONE', id }); }
      else notifyClients({ type: 'DOWNLOAD_ERROR', id, status: r.status });
    } catch(err) { notifyClients({ type: 'DOWNLOAD_ERROR', id, error: err.message }); }
  }
  if (e.data?.type === 'CHECK_ALL_CACHED') {
    const cache = await caches.open(AUDIO_CACHE);
    const results = {};
    for (let i = 1; i <= 11; i++) results[i] = !!(await cache.match('audio::ch' + i));
    notifyClients({ type: 'CACHE_STATUS', results });
  }
});

async function notifyClients(msg) {
  const clients = await self.clients.matchAll({ includeUncontrolled: true });
  clients.forEach(c => c.postMessage(msg));
}
