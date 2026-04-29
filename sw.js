const CACHE_NAME = 'positively-geared-v4';
const AUDIO_CACHE = 'positively-geared-audio-v4';

const SHELL_FILES = ['./', './index.html', './manifest.json'];

const AUDIO_FILES = [
  'chapter_1.mp3','chapter_2.mp3','chapter_3.mp3','chapter_4.mp3',
  'chapter_5.mp3','chapter_6.mp3','chapter_7.mp3','chapter_8.mp3',
  'chapter_9.mp3','chapter_10.mp3','chapter_11.mp3'
];

const BASE = 'https://github.com/jgagraphicdesigner/app-audiobook/releases/download/audio-v1/';

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
  const url = e.request.url;
  const filename = AUDIO_FILES.find(f => url.includes(f));

  if (filename) {
    e.respondWith(
      caches.open(AUDIO_CACHE).then(async cache => {
        // Check cache by filename key (not the expiring URL)
        const cacheKey = 'audio::' + filename;
        const cached = await cache.match(cacheKey);
        if (cached) {
          return cached;
        }
        // Not cached — fetch from network
        try {
          const response = await fetch(BASE + filename, { redirect: 'follow' });
          if (response.ok) {
            // Store with stable filename key so expiring URLs don't matter
            await cache.put(cacheKey, response.clone());
          }
          return response;
        } catch {
          return new Response('Audio not available offline yet', { status: 503 });
        }
      })
    );
  } else {
    e.respondWith(
      fetch(e.request)
        .then(r => {
          if (r.ok) caches.open(CACHE_NAME).then(c => c.put(e.request, r.clone()));
          return r;
        })
        .catch(() => caches.match(e.request))
    );
  }
});

self.addEventListener('message', async e => {
  if (e.data?.type === 'DOWNLOAD_CHAPTER') {
    const filename = e.data.filename;
    const url = BASE + filename;
    const cacheKey = 'audio::' + filename;

    const cache = await caches.open(AUDIO_CACHE);
    const existing = await cache.match(cacheKey);
    if (existing) {
      notifyClients({ type: 'DOWNLOAD_DONE', filename, alreadyCached: true });
      return;
    }

    try {
      const response = await fetch(url, { redirect: 'follow' });
      if (response.ok) {
        await cache.put(cacheKey, response.clone());
        notifyClients({ type: 'DOWNLOAD_DONE', filename });
      } else {
        notifyClients({ type: 'DOWNLOAD_ERROR', filename, status: response.status });
      }
    } catch(err) {
      notifyClients({ type: 'DOWNLOAD_ERROR', filename, error: err.message });
    }
  }

  if (e.data?.type === 'CHECK_ALL_CACHED') {
    const cache = await caches.open(AUDIO_CACHE);
    const results = {};
    for (const f of AUDIO_FILES) {
      const cached = await cache.match('audio::' + f);
      const chNum = f.match(/chapter_(\d+)/)[1];
      results[chNum] = !!cached;
    }
    notifyClients({ type: 'CACHE_STATUS', results });
  }
});

async function notifyClients(msg) {
  const clients = await self.clients.matchAll({ includeUncontrolled: true });
  clients.forEach(c => c.postMessage(msg));
}
