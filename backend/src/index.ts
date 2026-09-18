import { Hono } from 'hono';
import type { PoolClient } from 'pg';
import { requireAuth, type AuthEnv } from './auth.js';
import {
  HttpError, badRequest, conflict, decimal, forbidden, integer, isUuid, jsonBody, notFound,
  optionalCoordinate, pool, text, transaction, uuidParam,
} from './support.js';
import {
  PRIVATE_BUCKET, PUBLIC_BUCKET, createUpload, deleteQuietly, isOwnPublicObject, privateDownloadUrl, publicKeyFromUrl,
} from './storage.js';

type Role = 'pharmacist' | 'supplier';
type Collection = 'inventory' | 'catalog';

const app = new Hono<AuthEnv>();

// region Mapping (snake_case rows -> camelCase JSON)

const USER_COLUMNS = `uid, email, name, business_name, address, phone, role, photo_url, photo_version,
  latitude, longitude`;

const userJson = (r: any) => ({
  uid: r.uid,
  email: r.email,
  name: r.name,
  businessName: r.business_name,
  address: r.address,
  phone: r.phone,
  role: r.role,
  photoUrl: r.photo_url,
  photoVersion: Number(r.photo_version),
  latitude: r.latitude,
  longitude: r.longitude,
});

const MEDICINE_COLUMNS = `id, owner_id, collection, name, description, category, price::float8 AS price, stock,
  low_stock_threshold, image_url, manufacturer, ndc, (extract(epoch FROM created_at) * 1000)::bigint AS created_ms`;

const medicineJson = (r: any) => ({
  id: r.id,
  ownerId: r.owner_id,
  collection: r.collection,
  name: r.name,
  description: r.description,
  category: r.category,
  price: Number(r.price),
  stock: r.stock,
  lowStockThreshold: r.low_stock_threshold,
  imageUrl: r.image_url,
  manufacturer: r.manufacturer,
  ndc: r.ndc,
  createdAt: Number(r.created_ms),
});

const ORDER_SELECT = `SELECT o.id, o.pharmacist_id, p.business_name AS pharmacist_name, o.supplier_id,
    s.business_name AS supplier_name, o.medicine_id, o.medicine_name, o.quantity, o.unit_price::float8 AS unit_price,
    o.status, (extract(epoch FROM o.created_at) * 1000)::bigint AS created_ms,
    (extract(epoch FROM o.updated_at) * 1000)::bigint AS updated_ms
  FROM orders o JOIN users p ON p.uid = o.pharmacist_id JOIN users s ON s.uid = o.supplier_id`;

const orderJson = (r: any) => ({
  id: r.id,
  pharmacistId: r.pharmacist_id,
  pharmacistName: r.pharmacist_name,
  supplierId: r.supplier_id,
  supplierName: r.supplier_name,
  medicineId: r.medicine_id ?? '',
  medicineName: r.medicine_name,
  quantity: r.quantity,
  unitPrice: Number(r.unit_price),
  status: r.status,
  createdAt: Number(r.created_ms),
  updatedAt: Number(r.updated_ms),
});

// endregion

async function requireProfile(client: { query: PoolClient['query'] }, uid: string): Promise<{ uid: string; role: Role }> {
  const { rows } = await client.query('SELECT uid, role FROM users WHERE uid = $1', [uid]);
  if (rows.length === 0) throw new HttpError(404, 'profile_missing', 'Complete your profile to continue');
  return rows[0];
}

function collectionParam(value: unknown): Collection {
  if (value === 'inventory' || value === 'catalog') return value;
  throw badRequest('collection must be inventory or catalog');
}

function expectedCollection(role: Role): Collection {
  return role === 'supplier' ? 'catalog' : 'inventory';
}

app.onError((error, c) => {
  if (error instanceof HttpError) {
    return c.json({ error: { code: error.code, message: error.message, ...error.details } }, error.status);
  }
  console.error('Unhandled error', error);
  return c.json({ error: { code: 'internal', message: 'Something went wrong' } }, 500);
});

app.notFound((c) => c.json({ error: { code: 'not_found', message: 'Route not found' } }, 404));

app.get('/health', async (c) => {
  await pool.query('SELECT 1');
  return c.json({ ok: true });
});

// region Public (no account needed)

app.get('/public/pharmacies', async (c) => {
  const { rows } = await pool.query(
    `SELECT ${USER_COLUMNS} FROM users WHERE role = 'pharmacist' AND business_name <> '' ORDER BY lower(business_name)`,
  );
  return c.json(rows.map((r) => ({
    uid: r.uid, name: r.business_name, address: r.address, phone: r.phone,
    photoUrl: r.photo_url, latitude: r.latitude, longitude: r.longitude,
  })));
});

app.get('/public/medicines', async (c) => {
  const q = (c.req.query('q') ?? '').trim().slice(0, 100);
  const { rows } = await pool.query(
    `SELECT ${MEDICINE_COLUMNS} FROM medicines m
      WHERE collection = 'inventory' AND stock > 0 AND ($1 = '' OR name ILIKE '%' || $1 || '%' OR category ILIKE '%' || $1 || '%')
      ORDER BY lower(name) LIMIT 500`,
    [q],
  );
  return c.json(rows.map(medicineJson));
});

// endregion

// region Profile

// Profile creation happens right after sign-up, before the email is verified.
app.put('/me', requireAuth({ requireVerified: false }), async (c) => {
  const user = c.get('user');
  const body = await jsonBody(c);
  const role = body.role;
  const values = [
    user.uid,
    user.email,
    text(body, 'name', 120, true),
    text(body, 'businessName', 160, true),
    text(body, 'address', 300, true),
    text(body, 'phone', 40),
    optionalCoordinate(body, 'latitude', 90),
    optionalCoordinate(body, 'longitude', 180),
  ];
  const existing = await pool.query('SELECT role FROM users WHERE uid = $1', [user.uid]);
  if (existing.rows.length === 0) {
    if (role !== 'pharmacist' && role !== 'supplier') throw badRequest('role must be pharmacist or supplier');
    const { rows } = await pool.query(
      `INSERT INTO users (uid, email, name, business_name, address, phone, latitude, longitude, role)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING ${USER_COLUMNS}`,
      [...values, role],
    );
    return c.json(userJson(rows[0]), 201);
  }
  // The role is fixed once chosen.
  const { rows } = await pool.query(
    `UPDATE users SET email = $2, name = $3, business_name = $4, address = $5, phone = $6,
       latitude = COALESCE($7, latitude), longitude = COALESCE($8, longitude), updated_at = now()
     WHERE uid = $1 RETURNING ${USER_COLUMNS}`,
    values,
  );
  return c.json(userJson(rows[0]));
});

app.get('/me', requireAuth({ requireVerified: false }), async (c) => {
  const { rows } = await pool.query(`SELECT ${USER_COLUMNS} FROM users WHERE uid = $1`, [c.get('user').uid]);
  if (rows.length === 0) throw new HttpError(404, 'profile_missing', 'Complete your profile to continue');
  return c.json(userJson(rows[0]));
});

app.put('/me/location', requireAuth(), async (c) => {
  const body = await jsonBody(c);
  const latitude = optionalCoordinate(body, 'latitude', 90);
  const longitude = optionalCoordinate(body, 'longitude', 180);
  if (latitude === null || longitude === null) throw badRequest('latitude and longitude are required');
  const address = text(body, 'address', 300);
  const { rows } = await pool.query(
    `UPDATE users SET latitude = $2, longitude = $3, address = CASE WHEN $4 = '' THEN address ELSE $4 END, updated_at = now()
     WHERE uid = $1 RETURNING ${USER_COLUMNS}`,
    [c.get('user').uid, latitude, longitude, address],
  );
  if (rows.length === 0) throw new HttpError(404, 'profile_missing', 'Complete your profile to continue');
  return c.json(userJson(rows[0]));
});

app.put('/me/photo', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const photoUrl = text(await jsonBody(c), 'photoUrl', 1000, true);
  if (!isOwnPublicObject(photoUrl, uid, 'avatars')) throw badRequest('photoUrl must be an uploaded avatar');
  const previous = await pool.query('SELECT photo_url FROM users WHERE uid = $1', [uid]);
  if (previous.rows.length === 0) throw new HttpError(404, 'profile_missing', 'Complete your profile to continue');
  const { rows } = await pool.query(
    `UPDATE users SET photo_url = $2, photo_version = (extract(epoch FROM now()) * 1000)::bigint, updated_at = now()
     WHERE uid = $1 RETURNING ${USER_COLUMNS}`,
    [uid, photoUrl],
  );
  const oldKey = publicKeyFromUrl(previous.rows[0].photo_url);
  if (oldKey && previous.rows[0].photo_url !== photoUrl) await deleteQuietly(PUBLIC_BUCKET, oldKey);
  return c.json(userJson(rows[0]));
});

app.post('/uploads', requireAuth(), async (c) => {
  const body = await jsonBody(c);
  return c.json(await createUpload(c.get('user').uid, text(body, 'kind', 20, true), text(body, 'contentType', 40, true)));
});

// endregion

// region Suppliers & medicines

app.get('/suppliers', requireAuth(), async (c) => {
  const { rows } = await pool.query(
    `SELECT ${USER_COLUMNS} FROM users WHERE role = 'supplier' AND uid <> $1 ORDER BY lower(business_name)`,
    [c.get('user').uid],
  );
  return c.json(rows.map(userJson));
});

app.get('/medicines', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const collection = collectionParam(c.req.query('collection'));
  const ownerId = c.req.query('ownerId') || uid;
  // Anyone signed in can browse a supplier catalog; inventories are private to their pharmacy.
  if (ownerId !== uid && collection !== 'catalog') throw forbidden();
  const { rows } = await pool.query(
    `SELECT ${MEDICINE_COLUMNS} FROM medicines WHERE owner_id = $1 AND collection = $2 ORDER BY lower(name)`,
    [ownerId, collection],
  );
  return c.json(rows.map(medicineJson));
});

function medicineValues(body: Record<string, unknown>) {
  const imageUrl = text(body, 'imageUrl', 1000);
  if (imageUrl && !imageUrl.startsWith('https://')) {
    throw badRequest('imageUrl is invalid');
  }
  return {
    name: text(body, 'name', 200, true),
    description: text(body, 'description', 2000),
    category: text(body, 'category', 80) || 'General',
    price: decimal(body, 'price'),
    stock: integer(body, 'stock'),
    lowStockThreshold: integer(body, 'lowStockThreshold', { fallback: 0 }),
    imageUrl,
    manufacturer: text(body, 'manufacturer', 200),
    ndc: text(body, 'ndc', 40),
  };
}

async function upsertMedicine(client: { query: PoolClient['query'] }, uid: string, collection: Collection, id: string, v: ReturnType<typeof medicineValues>) {
  const { rows } = await client.query(
    `INSERT INTO medicines (id, owner_id, collection, name, description, category, price, stock, low_stock_threshold,
       image_url, manufacturer, ndc)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
     ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
       category = EXCLUDED.category, price = EXCLUDED.price, stock = EXCLUDED.stock,
       low_stock_threshold = EXCLUDED.low_stock_threshold, image_url = EXCLUDED.image_url,
       manufacturer = EXCLUDED.manufacturer, ndc = EXCLUDED.ndc, updated_at = now()
     WHERE medicines.owner_id = EXCLUDED.owner_id AND medicines.collection = EXCLUDED.collection
     RETURNING ${MEDICINE_COLUMNS}`,
    [id, uid, collection, v.name, v.description, v.category, v.price, v.stock, v.lowStockThreshold, v.imageUrl, v.manufacturer, v.ndc],
  );
  if (rows.length === 0) throw forbidden('This item belongs to another account');
  return rows[0];
}

app.put('/medicines/:id', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const id = uuidParam(c);
  const body = await jsonBody(c);
  const profile = await requireProfile(pool, uid);
  const collection = collectionParam(body.collection);
  if (collection !== expectedCollection(profile.role)) throw forbidden(`A ${profile.role} can't edit the ${collection}`);
  const values = medicineValues(body);
  const previous = await pool.query('SELECT image_url FROM medicines WHERE id = $1 AND owner_id = $2', [id, uid]);
  const row = await upsertMedicine(pool, uid, collection, id, values);
  const oldUrl: string | undefined = previous.rows[0]?.image_url;
  const oldKey = oldUrl && oldUrl !== values.imageUrl ? publicKeyFromUrl(oldUrl) : null;
  if (oldKey) await deleteQuietly(PUBLIC_BUCKET, oldKey);
  return c.json(medicineJson(row));
});

app.post('/medicines/import', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const body = await jsonBody(c);
  const profile = await requireProfile(pool, uid);
  const collection = collectionParam(body.collection);
  if (collection !== expectedCollection(profile.role)) throw forbidden();
  const items = body.items;
  if (!Array.isArray(items) || items.length === 0 || items.length > 100) throw badRequest('items must contain 1 to 100 medicines');
  const rows = await transaction(async (client) => {
    const saved = [];
    for (const raw of items) {
      if (!raw || typeof raw !== 'object' || !isUuid((raw as any).id)) throw badRequest('Each item needs a UUID id');
      const item = raw as Record<string, unknown>;
      saved.push(await upsertMedicine(client, uid, collection, (item.id as string).toLowerCase(), medicineValues(item)));
    }
    return saved;
  });
  return c.json(rows.map(medicineJson));
});

app.patch('/medicines/:id/stock', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const body = await jsonBody(c);
  const { rows } = await pool.query(
    `UPDATE medicines SET stock = $3, low_stock_threshold = $4, updated_at = now()
     WHERE id = $1 AND owner_id = $2 RETURNING ${MEDICINE_COLUMNS}`,
    [uuidParam(c), uid, integer(body, 'stock'), integer(body, 'lowStockThreshold', { fallback: 0 })],
  );
  if (rows.length === 0) throw notFound('Medicine');
  return c.json(medicineJson(rows[0]));
});

app.delete('/medicines/:id', requireAuth(), async (c) => {
  const { rows } = await pool.query(
    'DELETE FROM medicines WHERE id = $1 AND owner_id = $2 RETURNING image_url',
    [uuidParam(c), c.get('user').uid],
  );
  if (rows.length === 0) throw notFound('Medicine');
  const key = publicKeyFromUrl(rows[0].image_url);
  if (key) await deleteQuietly(PUBLIC_BUCKET, key);
  return c.body(null, 204);
});

// endregion

// region Orders

app.get('/orders', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const profile = await requireProfile(pool, uid);
  const column = profile.role === 'supplier' ? 'o.supplier_id' : 'o.pharmacist_id';
  const { rows } = await pool.query(`${ORDER_SELECT} WHERE ${column} = $1 ORDER BY o.created_at DESC LIMIT 1000`, [uid]);
  return c.json(rows.map(orderJson));
});

app.post('/orders', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const body = await jsonBody(c);
  if (!isUuid(body.id) || !isUuid(body.medicineId)) throw badRequest('id and medicineId must be UUIDs');
  const quantity = integer(body, 'quantity', { min: 1, max: 1_000_000 });
  const id = (body.id as string).toLowerCase();

  await transaction(async (client) => {
    const profile = await requireProfile(client, uid);
    if (profile.role !== 'pharmacist') throw forbidden('Only pharmacies can place orders');
    const item = await client.query(
      `SELECT owner_id, name, price::float8 AS price, stock FROM medicines WHERE id = $1 AND collection = 'catalog'`,
      [(body.medicineId as string).toLowerCase()],
    );
    if (item.rows.length === 0) throw notFound('Catalog item');
    const product = item.rows[0];
    if (quantity > product.stock) {
      throw conflict('insufficient_stock', `Only ${product.stock} available`, { available: product.stock });
    }
    // Idempotent: retrying the same order id is a no-op.
    await client.query(
      `INSERT INTO orders (id, pharmacist_id, supplier_id, medicine_id, medicine_name, quantity, unit_price)
       VALUES ($1, $2, $3, $4, $5, $6, $7) ON CONFLICT (id) DO NOTHING`,
      [id, uid, product.owner_id, (body.medicineId as string).toLowerCase(), product.name, quantity, product.price],
    );
  });
  const { rows } = await pool.query(`${ORDER_SELECT} WHERE o.id = $1 AND o.pharmacist_id = $2`, [id, uid]);
  if (rows.length === 0) throw conflict('order_exists', 'Order id already used');
  return c.json(orderJson(rows[0]), 201);
});

type Transition = { from: string[]; to: string; actor: Role };

const TRANSITIONS: Record<string, Transition> = {
  accept: { from: ['pending'], to: 'accepted', actor: 'supplier' },
  decline: { from: ['pending'], to: 'declined', actor: 'supplier' },
  dispatch: { from: ['accepted'], to: 'dispatched', actor: 'supplier' },
  cancel: { from: ['pending'], to: 'cancelled', actor: 'pharmacist' },
  receive: { from: ['accepted', 'dispatched'], to: 'delivered', actor: 'pharmacist' },
};

app.post('/orders/:id/:action', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const id = uuidParam(c);
  const action = c.req.param('action');
  const transition = TRANSITIONS[action];
  if (!transition) throw notFound('Action');

  await transaction(async (client) => {
    const { rows } = await client.query('SELECT * FROM orders WHERE id = $1 FOR UPDATE', [id]);
    const order = rows[0];
    const partyColumn = transition.actor === 'supplier' ? 'supplier_id' : 'pharmacist_id';
    if (!order || order[partyColumn] !== uid) throw notFound('Order');
    if (!transition.from.includes(order.status)) {
      throw conflict('invalid_status', `This order is already ${order.status}`, { status: order.status });
    }

    if (action === 'accept' && order.medicine_id) {
      // Reserve supplier stock atomically.
      const stock = await client.query('SELECT stock FROM medicines WHERE id = $1 FOR UPDATE', [order.medicine_id]);
      const available = stock.rows[0]?.stock ?? 0;
      if (stock.rows.length > 0) {
        if (available < order.quantity) {
          throw conflict('insufficient_stock', `Only ${available} available`, { available });
        }
        await client.query('UPDATE medicines SET stock = stock - $2, updated_at = now() WHERE id = $1', [order.medicine_id, order.quantity]);
      }
    }

    if (action === 'receive') {
      // Add to the pharmacy's matching inventory item, or create one from the supplier's product.
      const match = await client.query(
        `SELECT id FROM medicines WHERE owner_id = $1 AND collection = 'inventory' AND lower(name) = lower($2)
         ORDER BY created_at LIMIT 1 FOR UPDATE`,
        [uid, order.medicine_name],
      );
      if (match.rows.length > 0) {
        await client.query('UPDATE medicines SET stock = stock + $2, updated_at = now() WHERE id = $1', [match.rows[0].id, order.quantity]);
      } else {
        const source = order.medicine_id
          ? (await client.query('SELECT description, category, image_url, manufacturer, ndc FROM medicines WHERE id = $1', [order.medicine_id])).rows[0]
          : undefined;
        await client.query(
          `INSERT INTO medicines (id, owner_id, collection, name, description, category, price, stock, image_url, manufacturer, ndc)
           VALUES (gen_random_uuid(), $1, 'inventory', $2, $3, $4, $5, $6, $7, $8, $9)`,
          [uid, order.medicine_name, source?.description ?? '', source?.category ?? 'General', order.unit_price,
            order.quantity, source?.image_url ?? '', source?.manufacturer ?? '', source?.ndc ?? ''],
        );
      }
    }

    await client.query('UPDATE orders SET status = $2, updated_at = now() WHERE id = $1', [id, transition.to]);
  });

  const { rows } = await pool.query(`${ORDER_SELECT} WHERE o.id = $1`, [id]);
  return c.json(orderJson(rows[0]));
});

// endregion

// region Invoices

app.get('/invoices', requireAuth(), async (c) => {
  const { rows } = await pool.query(
    `SELECT id, name, description, image_key, (extract(epoch FROM created_at) * 1000)::bigint AS created_ms
     FROM invoices WHERE owner_id = $1 ORDER BY created_at DESC`,
    [c.get('user').uid],
  );
  const invoices = await Promise.all(rows.map(async (r) => ({
    id: r.id,
    ownerId: c.get('user').uid,
    name: r.name,
    description: r.description,
    imageUrl: await privateDownloadUrl(r.image_key),
    createdAt: Number(r.created_ms),
  })));
  return c.json(invoices);
});

app.post('/invoices', requireAuth(), async (c) => {
  const uid = c.get('user').uid;
  const body = await jsonBody(c);
  if (!isUuid(body.id)) throw badRequest('id must be a UUID');
  const imageKey = text(body, 'imageKey', 300, true);
  if (!imageKey.startsWith(`invoices/${uid}/`)) throw badRequest('imageKey must be one of your uploads');
  await requireProfile(pool, uid);
  const { rows } = await pool.query(
    `INSERT INTO invoices (id, owner_id, name, description, image_key) VALUES ($1, $2, $3, $4, $5)
     ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description
     WHERE invoices.owner_id = EXCLUDED.owner_id
     RETURNING id, name, description, image_key, (extract(epoch FROM created_at) * 1000)::bigint AS created_ms`,
    [(body.id as string).toLowerCase(), uid, text(body, 'name', 200, true), text(body, 'description', 2000), imageKey],
  );
  if (rows.length === 0) throw forbidden();
  const r = rows[0];
  return c.json({
    id: r.id, ownerId: uid, name: r.name, description: r.description,
    imageUrl: await privateDownloadUrl(r.image_key), createdAt: Number(r.created_ms),
  }, 201);
});

app.delete('/invoices/:id', requireAuth(), async (c) => {
  const { rows } = await pool.query(
    'DELETE FROM invoices WHERE id = $1 AND owner_id = $2 RETURNING image_key',
    [uuidParam(c), c.get('user').uid],
  );
  if (rows.length === 0) throw notFound('Invoice');
  await deleteQuietly(PRIVATE_BUCKET, rows[0].image_key);
  return c.body(null, 204);
});

// endregion

export default app;
