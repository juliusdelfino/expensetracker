const CACHE_NAME = 'expense-tracker-v1';
const PRECACHE_URLS = [
  '/',
  '/index.html',
  '/css/base.css',
  '/css/navbar.css',
  '/css/dashboard.css',
  '/css/expense-detail.css',
  '/css/mobile.css',
  '/css/chat.css',
  '/css/responsive.css',
  '/js/utils.js',
  '/js/router.js',
  '/js/scan.js',
  '/js/dashboard-widgets.js',
  '/js/home.js',
  '/js/chat.js',
  '/js/auth.js',
  '/js/expenses.js',
  '/js/expense-new.js',
  '/js/expense-detail.js',
  '/js/expense-dialogs.js',
  '/images/logo-large.png',
  '/manifest.json'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // Always go to network for API calls
  if (url.pathname.startsWith('/api/')) return;

  event.respondWith(
    fetch(event.request)
      .then(response => {
        // Cache successful GET responses for static assets
        if (event.request.method === 'GET' && response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});

