# Offline cache design

No railway or airport records are currently synchronized or cached locally. The
Android app retains bounded saved/recent navigation coordinates in existing
SharedPreferences. It does not provide an offline station/airport database.

The backend currently has an in-memory bounded LRU cache (max 256 entries,
configurable TTL default 120 seconds) for geocoding and airport-board/search
responses, subject to provider cache terms. It is volatile and is not an offline
database. Route calculation bypasses cache. Railway providers are unconfigured.

Before persistent cache implementation, obtain written/licensed data retention
rights, specify per-field freshness and expiry, use a versioned SQLite schema,
transactional sync, bounded size/eviction, and clearly mark all offline records
STALE/CACHED. No stale live status should be presented as current.

