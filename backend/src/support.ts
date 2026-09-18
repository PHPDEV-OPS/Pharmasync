import type { Context } from 'hono';
import { Pool, type PoolClient } from 'pg';
import { attachPoolErrorHandler } from './pool.js';

export class HttpError extends Error {
  constructor(
    readonly status: 400 | 401 | 403 | 404 | 409 | 422 | 500,
    readonly code: string,
    message: string,
    readonly details?: Record<string, unknown>,
  ) {
    super(message);
  }
}

export const badRequest = (message: string) => new HttpError(400, 'bad_request', message);
export const notFound = (what: string) => new HttpError(404, 'not_found', `${what} not found`);
export const forbidden = (message = 'Not allowed') => new HttpError(403, 'forbidden', message);
export const conflict = (code: string, message: string, details?: Record<string, unknown>) =>
  new HttpError(409, code, message, details);

// One pool per isolate; keep it small because each isolate holds its own connections.
export const pool = new Pool({ connectionString: process.env.DATABASE_URL, max: 5 });
attachPoolErrorHandler(pool);

export async function transaction<T>(work: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await work(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    await client.query('ROLLBACK').catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function uuidParam(c: Context, name = 'id'): string {
  const value = c.req.param(name);
  if (!value || !UUID.test(value)) throw badRequest(`Invalid ${name}`);
  return value.toLowerCase();
}

export function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID.test(value);
}

export async function jsonBody(c: Context): Promise<Record<string, unknown>> {
  try {
    const body = await c.req.json();
    if (body && typeof body === 'object' && !Array.isArray(body)) return body as Record<string, unknown>;
  } catch {
    // fall through
  }
  throw badRequest('Expected a JSON object body');
}

export function text(body: Record<string, unknown>, key: string, max = 500, required = false): string {
  const value = body[key];
  if (value === undefined || value === null) {
    if (required) throw badRequest(`${key} is required`);
    return '';
  }
  if (typeof value !== 'string') throw badRequest(`${key} must be a string`);
  const trimmed = value.trim();
  if (required && trimmed.length === 0) throw badRequest(`${key} is required`);
  if (trimmed.length > max) throw badRequest(`${key} is too long`);
  return trimmed;
}

export function integer(body: Record<string, unknown>, key: string, { min = 0, max = 10_000_000, fallback }: { min?: number; max?: number; fallback?: number } = {}): number {
  const value = body[key];
  if ((value === undefined || value === null) && fallback !== undefined) return fallback;
  if (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max) {
    throw badRequest(`${key} must be a whole number between ${min} and ${max}`);
  }
  return value;
}

export function decimal(body: Record<string, unknown>, key: string, { min = 0, max = 10_000_000, fallback }: { min?: number; max?: number; fallback?: number } = {}): number {
  const value = body[key];
  if ((value === undefined || value === null) && fallback !== undefined) return fallback;
  if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
    throw badRequest(`${key} must be a number between ${min} and ${max}`);
  }
  return Math.round(value * 100) / 100;
}

export function optionalCoordinate(body: Record<string, unknown>, key: string, limit: number): number | null {
  const value = body[key];
  if (value === undefined || value === null) return null;
  if (typeof value !== 'number' || !Number.isFinite(value) || Math.abs(value) > limit) {
    throw badRequest(`${key} is invalid`);
  }
  return value;
}
