/**
 * Development environment. The dev server proxies /api to the backend
 * (see proxy.conf.json) so the app code never needs a hostname.
 */
export const environment = {
  production: false,
  appName: 'SENTINEL',
  mapRefreshMs: 5000,
  anomalyPollMs: 10000,
};
