/**
 * Production environment. nginx serves the built app and proxies /api
 * to the backend container — still no hardcoded hostnames.
 */
export const environment = {
  production: true,
  appName: 'SENTINEL',
  mapRefreshMs: 5000,
  anomalyPollMs: 10000,
};
