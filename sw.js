const CACHE_NAME = 'positively-geared-v3';
const AUDIO_CACHE = 'positively-geared-audio-v3';

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
  const isAudio = AUDIO_FILES.some(f => url.includes(f));

  if (isAudio) {
    e.respondWith(
      caches.open(AUDIO_CACHE).then(async cache => {
        const cached = await cache.match(e.request);
        if (cached) return cached;
        try {
          // Use no-cors for GitHub release assets
          const response = await fetch(e.request, { mode: 'cors', credentials: 'omit' });
          if (response.ok) cache.put(e.request, response.clone());
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

self.addEventListener('message', e => {
  if (e.data?.type === 'DOWNLOAD_CHAPTER') {
    const url = e.data.url;
    caches.open(AUDIO_CACHE).then(async cache => {
      const existing = await cache.match(url);
      if (existing) {
        notifyClients({ type: 'DOWNLOAD_DONE', url, alreadyCached: true });
        return;
      }
      try {
        const response = await fetch(url, { mode: 'cors', credentials: 'omit' });
        if (response.ok) {
          await cache.put(url, response.clone());
          notifyClients({ type: 'DOWNLOAD_DONE', url });
        } else {
          notifyClients({ type: 'DOWNLOAD_ERROR', url, status: response.status });
        }
      } catch(err) {
        notifyClients({ type: 'DOWNLOAD_ERROR', url, error: err.message });
      }
    });
  }

  if (e.data?.type === 'CHECK_ALL_CACHED') {
    caches.open(AUDIO_CACHE).then(async cache => {
      const results = {};
      for (const f of AUDIO_FILES) {
        const fullUrl = BASE + f;
        const cached = await cache.match(fullUrl);
        const chNum = f.match(/chapter_(\d+)/)[1];
        results[chNum] = !!cached;
      }
      notifyClients({ type: 'CACHE_STATUS', results });
    });
  }
});

async function notifyClients(msg) {
  const clients = await self.clients.matchAll({ includeUncontrolled: true });
  clients.forEach(c => c.postMessage(msg));
}
