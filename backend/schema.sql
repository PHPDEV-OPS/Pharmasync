-- Pharmasync schema (Neon Postgres). Applied to the `neondb` database on the production branch.
-- Users sign in with Firebase Auth; `uid` is the Firebase user id.

CREATE TABLE IF NOT EXISTS users (
  uid text PRIMARY KEY,
  email text NOT NULL,
  name text NOT NULL DEFAULT '',
  business_name text NOT NULL DEFAULT '',
  address text NOT NULL DEFAULT '',
  phone text NOT NULL DEFAULT '',
  role text NOT NULL CHECK (role IN ('pharmacist', 'supplier')),
  photo_url text NOT NULL DEFAULT '',
  photo_version bigint NOT NULL DEFAULT 0,
  latitude double precision,
  longitude double precision,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS users_role_idx ON users (role);

-- Pharmacy inventory ('inventory') and supplier catalogs ('catalog') share one table.
CREATE TABLE IF NOT EXISTS medicines (
  id uuid PRIMARY KEY,
  owner_id text NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
  collection text NOT NULL CHECK (collection IN ('inventory', 'catalog')),
  name text NOT NULL CHECK (length(trim(name)) > 0),
  description text NOT NULL DEFAULT '',
  category text NOT NULL DEFAULT 'General',
  price numeric(12, 2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  stock integer NOT NULL DEFAULT 0 CHECK (stock >= 0),
  low_stock_threshold integer NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
  image_url text NOT NULL DEFAULT '',
  manufacturer text NOT NULL DEFAULT '',
  ndc text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS medicines_owner_collection_idx ON medicines (owner_id, collection);
CREATE INDEX IF NOT EXISTS medicines_public_idx ON medicines (collection, lower(name)) WHERE stock > 0;

CREATE TABLE IF NOT EXISTS orders (
  id uuid PRIMARY KEY,
  pharmacist_id text NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
  supplier_id text NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
  medicine_id uuid REFERENCES medicines (id) ON DELETE SET NULL,
  medicine_name text NOT NULL,
  quantity integer NOT NULL CHECK (quantity > 0),
  unit_price numeric(12, 2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'accepted', 'dispatched', 'delivered', 'declined', 'cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (pharmacist_id <> supplier_id)
);
CREATE INDEX IF NOT EXISTS orders_pharmacist_idx ON orders (pharmacist_id, created_at DESC);
CREATE INDEX IF NOT EXISTS orders_supplier_idx ON orders (supplier_id, created_at DESC);

-- Invoice images live in the private bucket; only the object key is stored.
CREATE TABLE IF NOT EXISTS invoices (
  id uuid PRIMARY KEY,
  owner_id text NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
  name text NOT NULL CHECK (length(trim(name)) > 0),
  description text NOT NULL DEFAULT '',
  image_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS invoices_owner_idx ON invoices (owner_id, created_at DESC);
