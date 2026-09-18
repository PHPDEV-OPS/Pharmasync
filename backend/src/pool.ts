import type { Pool } from 'pg';

/**
 * Postgres drops idle clients (scale-to-zero, pooler reclaim). Without an 'error' listener that
 * becomes an uncaught exception and the isolate exits. The pool already discards the dead client.
 */
export function attachPoolErrorHandler(pool: Pool): void {
  pool.on('error', (error) => {
    console.warn('Idle Postgres client error (ignored):', error.message);
  });
}
