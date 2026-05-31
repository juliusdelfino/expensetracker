# Performance Guidelines

## Database

- **N+1 queries**: Use `JOIN FETCH` or `@EntityGraph` to avoid lazy-loading loops.
- **Pagination**: Always paginate list endpoints. Never return unbounded result sets.
- **Indexes**: Add indexes for all foreign keys and columns used in `WHERE`/`ORDER BY`.
- **Connection pool**: Use HikariCP (Spring Boot default). Size = CPU cores × 2 + 1 as baseline.
- **Batch operations**: Use `saveAll()` / batch inserts for bulk operations.

## Caching

- Cache expensive, rarely-changing data (categories list, user settings).
- Use Spring `@Cacheable` with a simple in-memory cache (Caffeine) for single-instance deployment.
- Set explicit TTLs. Never cache user-specific mutable data without invalidation strategy.

## API & HTTP

- **Compression**: Enable gzip for responses > 1 KB.
- **Static assets**: Serve with long cache headers (`Cache-Control: max-age=31536000, immutable`) and content-hash filenames or versioned paths.
- **Pagination defaults**: `size=20`, max `size=100`.
- **Avoid over-fetching**: Use projections or summary DTOs for list endpoints.

## AI / OCR Calls

- These are slow (seconds). Always execute asynchronously.
- Set timeouts on HTTP clients (connect: 5s, read: 30s).
- Cache OCR results — don't re-process the same receipt.

## File I/O

- Use streaming (`InputStream`/`OutputStream`) for receipt uploads/downloads — never load entire files into memory.
- Limit upload size (e.g., 10 MB).

## JVM

- Use G1GC (Java 21 default).
- Set `-Xmx` appropriately for deployment environment.
- Enable virtual threads for I/O-bound operations if using Spring Boot 3.2+ (`spring.threads.virtual.enabled=true`).

## Monitoring

- Expose `/actuator/health` and `/actuator/metrics` (protect with auth).
- Monitor: response times (p50, p95, p99), DB connection pool usage, heap usage, GC pauses.
- Set alerts for response time > 2s (p95) or error rate > 1%.

## Frontend

- Minimize DOM manipulations — batch updates.
- Lazy-load maps (Leaflet) only when needed.
- Debounce search inputs (already implemented: 400ms).
- Use `fetch` with `AbortController` for cancellable requests.

