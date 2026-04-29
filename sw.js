const CACHE_NAME = 'positively-geared-v2';
const AUDIO_CACHE = 'positively-geared-audio-v2';

const SHELL_FILES = [
  './',
  './index.html',
  './manifest.json',
];

const AUDIO_FILES = [
  './audio/chapter_1.mp3',
  './audio/chapter_2.mp3',
  './audio/chapter_3.mp3',
  './audio/chapter_4.mp3',
  './audio/chapter_5.mp3',
  './audio/chapter_6.mp3',
  './audio/chapter_7.mp3',
  './audio/chapter_8.mp3',
  './audio/chapter_9.mp3',
  './audio/chapter_10.mp3',
  './audio/chapter_11.mp3',
];

// Install — cache app shell immediately
self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(SHELL_FILES))
      .then(() => self.skipWaiting())
  );
});

// Activate — clean old caches
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

// Fetch — serve from cache when offline
self.addEventListener('fetch', e => {
  const url = e.request.url;
  const isAudio = url.includes('/audio/') && url.endsWith('.mp3');

  if (isAudio) {
    // Cache-first for audio
    e.respondWith(
      caches.open(AUDIO_CACHE).then(async cache => {
        const cached = await cache.match(e.request);
        if (cached) return cached;
        try {
          const response = await fetch(e.request);
          if (response.ok) cache.put(e.request, response.clone());
          return response;
        } catch {
          return new Response('Audio not available offline', { status: 503 });
        }
      })
    );
  } else {
    // Network-first for shell
    e.respondWith(
      fetch(e.request)
        .then(response => {
          if (response.ok) {
            caches.open(CACHE_NAME)
              .then(cache => cache.put(e.request, response.clone()));
          }
          return response;
        })
        .catch(() => caches.match(e.request))
    );
  }
});

// Download a chapter on demand (called from app JS)
self.addEventListener('message', e => {
  if (e.data?.type === 'DOWNLOAD_CHAPTER') {
    const url = e.data.url;
    caches.open(AUDIO_CACHE).then(async cache => {
      const existing = await cache.match(url);
      if (existing) {
        // Already cached — tell the page
        const clients = await self.clients.matchAll();
        clients.forEach(c => c.postMessage({ type: 'DOWNLOAD_DONE', url, alreadyCached: true }));
        return;
      }
      try {
        const response = await fetch(url);
        if (response.ok) {
          await cache.put(url, response.clone());
          const clients = await self.clients.matchAll();
          clients.forEach(c => c.postMessage({ type: 'DOWNLOAD_DONE', url, alreadyCached: false }));
        }
      } catch (err) {
        const clients = await self.clients.matchAll();
        clients.forEach(c => c.postMessage({ type: 'DOWNLOAD_ERROR', url }));
      }
    });
  }

  if (e.data?.type === 'CHECK_ALL_CACHED') {
    caches.open(AUDIO_CACHE).then(async cache => {
      const results = {};
      for (const f of AUDIO_FILES) {
        const fullUrl = new URL(f, self.location).href;
        const cached = await cache.match(fullUrl);
        const chNum = f.match(/chapter_(\d+)/)[1];
        results[chNum] = !!cached;
      }
      const clients = await self.clients.matchAll();
      clients.forEach(c => c.postMessage({ type: 'CACHE_STATUS', results }));
    });
  }
});
