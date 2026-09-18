import { createHash, createHmac, randomUUID } from 'node:crypto';
import { badRequest } from './support.js';

/*
 * Neon Object Storage is S3-compatible. Rather than bundling the AWS SDK, this signs presigned
 * URLs with AWS Signature Version 4 directly (query-string auth, path-style addressing).
 * Neon injects the branch-scoped credentials below into every function.
 */
const endpoint = (process.env.AWS_ENDPOINT_URL_S3 ?? '').replace(/\/$/, '');
const region = process.env.AWS_REGION ?? 'us-east-2';
const accessKeyId = process.env.AWS_ACCESS_KEY_ID ?? '';
const secretAccessKey = process.env.AWS_SECRET_ACCESS_KEY ?? '';

export const PUBLIC_BUCKET = process.env.PUBLIC_BUCKET ?? 'pharmasync-public';
export const PRIVATE_BUCKET = process.env.PRIVATE_BUCKET ?? 'pharmasync-private';

export type UploadKind = 'avatar' | 'product' | 'invoice';

const FOLDERS: Record<UploadKind, { bucket: string; folder: string }> = {
  avatar: { bucket: PUBLIC_BUCKET, folder: 'avatars' },
  product: { bucket: PUBLIC_BUCKET, folder: 'products' },
  invoice: { bucket: PRIVATE_BUCKET, folder: 'invoices' },
};

const CONTENT_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

// region SigV4

const rfc3986 = (value: string) =>
  encodeURIComponent(value).replace(/[!'()*]/g, (ch) => `%${ch.charCodeAt(0).toString(16).toUpperCase()}`);

const sha256Hex = (value: string) => createHash('sha256').update(value, 'utf8').digest('hex');
const hmac = (key: Buffer | string, value: string) => createHmac('sha256', key).update(value, 'utf8').digest();

export function presign(method: 'GET' | 'PUT' | 'DELETE', bucket: string, key: string, expiresInSeconds: number): string {
  const url = new URL(endpoint);
  const now = new Date();
  const amzDate = now.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, ''); // YYYYMMDDTHHMMSSZ
  const dateStamp = amzDate.slice(0, 8);
  const scope = `${dateStamp}/${region}/s3/aws4_request`;
  const canonicalUri = `/${[bucket, ...key.split('/')].map(rfc3986).join('/')}`;

  const params: Record<string, string> = {
    'X-Amz-Algorithm': 'AWS4-HMAC-SHA256',
    'X-Amz-Credential': `${accessKeyId}/${scope}`,
    'X-Amz-Date': amzDate,
    'X-Amz-Expires': String(expiresInSeconds),
    'X-Amz-SignedHeaders': 'host',
  };
  const canonicalQuery = Object.keys(params)
    .sort()
    .map((name) => `${rfc3986(name)}=${rfc3986(params[name])}`)
    .join('&');

  const canonicalRequest = [method, canonicalUri, canonicalQuery, `host:${url.host}`, '', 'host', 'UNSIGNED-PAYLOAD'].join('\n');
  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, scope, sha256Hex(canonicalRequest)].join('\n');
  const signingKey = hmac(hmac(hmac(hmac(`AWS4${secretAccessKey}`, dateStamp), region), 's3'), 'aws4_request');
  const signature = createHmac('sha256', signingKey).update(stringToSign, 'utf8').digest('hex');

  return `${url.origin}${canonicalUri}?${canonicalQuery}&X-Amz-Signature=${signature}`;
}

// endregion

export function publicUrl(key: string): string {
  return `${endpoint}/${PUBLIC_BUCKET}/${key}`;
}

/** Presigned PUT for a new object under the caller's own folder. */
export async function createUpload(uid: string, kind: string, contentType: string) {
  if (!(kind in FOLDERS)) throw badRequest('kind must be avatar, product or invoice');
  if (!CONTENT_TYPES.has(contentType)) throw badRequest('Unsupported image type');
  const { bucket, folder } = FOLDERS[kind as UploadKind];
  const extension = contentType === 'image/png' ? 'png' : contentType === 'image/webp' ? 'webp' : 'jpg';
  const key = `${folder}/${uid}/${randomUUID()}.${extension}`;
  return {
    key,
    uploadUrl: presign('PUT', bucket, key, 600),
    contentType,
    publicUrl: bucket === PUBLIC_BUCKET ? publicUrl(key) : null,
  };
}

/** Short-lived download URL for a private object (invoice images). */
export async function privateDownloadUrl(key: string): Promise<string> {
  return presign('GET', PRIVATE_BUCKET, key, 3600);
}

/** True when [url] points at an object the user uploaded into [folder] of the public bucket. */
export function isOwnPublicObject(url: string, uid: string, folder: 'avatars' | 'products'): boolean {
  return url.startsWith(publicUrl(`${folder}/${uid}/`));
}

export async function deleteQuietly(bucket: string, key: string): Promise<void> {
  if (!key) return;
  try {
    const response = await fetch(presign('DELETE', bucket, key, 60), { method: 'DELETE' });
    if (!response.ok && response.status !== 404) console.warn(`Delete ${bucket}/${key} failed: ${response.status}`);
  } catch (error) {
    console.warn(`Could not delete ${bucket}/${key}:`, (error as Error).message);
  }
}

export function publicKeyFromUrl(url: string): string | null {
  const prefix = `${endpoint}/${PUBLIC_BUCKET}/`;
  return url.startsWith(prefix) ? url.slice(prefix.length) : null;
}
