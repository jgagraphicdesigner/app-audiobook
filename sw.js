const CACHE_NAME = 'positively-geared-v1';
const AUDIO_CACHE = 'positively-geared-audio-v1';

// App shell files — cached immediately on install
const SHELL_FILES = [
  './',
  './index.html',
  './manifest.json',
];

// On install — cache the app shell
self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(SHELL_FILES))
  );
  self.skipWaiting();
});

// On activate — clean old caches
self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys.filter(k => k !== CACHE_NAME && k !== AUDIO_CACHE)
            .map(k => caches.delete(k))
      )
    )
  );
  self.clients.claim();
});

// Fetch strategy:
// - Audio files: cache-first (once downloaded, play offline forever)
// - Everything else: network-first with cache fallback
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  const isAudio = url.pathname.endsWith('.mp3');

  if(isAudio){
    // Cache-first for audio — serve from cache if available, else fetch & cache
    e.respondWith(
      caches.open(AUDIO_CACHE).then(async cache => {
        const cached = await cache.match(e.request);
        if(cached) return cached;
        const response = await fetch(e.request);
        if(response.ok) cache.put(e.request, response.clone());
        return response;
      })
    );
  } else {
    // Network-first for app shell
    e.respondWith(
      fetch(e.request).then(response => {
        if(response.ok){
          caches.open(CACHE_NAME).then(cache => cache.put(e.request, response.clone()));
        }
        return response;
      }).catch(() => caches.match(e.request))
    );
  }
});

// Listen for messages from the app to pre-cache a chapter
self.addEventListener('message', e => {
  if(e.data && e.data.type === 'CACHE_AUDIO'){
    const url = e.data.url;
    caches.open(AUDIO_CACHE).then(async cache => {
      const existing = await cache.match(url);
      if(!existing){
        fetch(url).then(r => { if(r.ok) cache.put(url, r); });
      }
    });
  }
  if(e.data && e.data.type === 'CHECK_CACHED'){
    const url = e.data.url;
    caches.open(AUDIO_CACHE).then(async cache => {
      const existing = await cache.match(url);
      const client = await self.clients.get(e.source.id);
      if(client) client.postMessage({ type: 'CACHED_STATUS', url, cached: !!existing });
    });
  }
});
