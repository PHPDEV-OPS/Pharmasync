import type { MiddlewareHandler } from 'hono';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { HttpError } from './support.js';

const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID ?? 'pharmasync-e5102';

// Firebase ID tokens are signed with Google's securetoken keys; jose caches the key set.
const firebaseKeys = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
);

export interface AuthUser {
  uid: string;
  email: string;
  emailVerified: boolean;
}

export type AuthEnv = { Variables: { user: AuthUser } };

async function verify(header: string | undefined): Promise<AuthUser> {
  const token = header?.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token) throw new HttpError(401, 'unauthenticated', 'Sign in required');
  try {
    const { payload } = await jwtVerify(token, firebaseKeys, {
      issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
      audience: FIREBASE_PROJECT_ID,
      algorithms: ['RS256'],
    });
    if (typeof payload.sub !== 'string' || payload.sub.length === 0) throw new Error('Missing subject');
    return {
      uid: payload.sub,
      email: typeof payload.email === 'string' ? payload.email : '',
      emailVerified: payload.email_verified === true,
    };
  } catch {
    throw new HttpError(401, 'invalid_token', 'Your session has expired. Please sign in again.');
  }
}

/** Requires a valid Firebase ID token. [requireVerified] also requires a verified email. */
export function requireAuth({ requireVerified = true } = {}): MiddlewareHandler<AuthEnv> {
  return async (c, next) => {
    const user = await verify(c.req.header('Authorization'));
    if (requireVerified && !user.emailVerified) {
      throw new HttpError(403, 'email_not_verified', 'Verify your email address to continue');
    }
    c.set('user', user);
    await next();
  };
}
