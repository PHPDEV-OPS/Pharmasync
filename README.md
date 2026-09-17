# Pharmasync

Pharmasync connects **pharmacies** and **medicine suppliers**. Pharmacies manage their inventory
and reorder from suppliers; suppliers publish a catalog and fulfil orders. Patients can browse
registered pharmacies and see which ones have a medicine in stock, without an account.

## Features

### Pharmacist account
- **Inventory**: add, edit, or delete medicines, with a photo, category, price, stock level, and a
  low-stock alert level. You can search the list and filter it to low-stock or out-of-stock items.
- **Barcode scanning**: CameraX and ML Kit read the barcode, then the openFDA NDC directory fills
  in the name, category, and manufacturer.
- **Name suggestions**: suggestions come from openFDA as you type.
- **Demo data**: import real drug products from openFDA with one tap.
- **Low-stock notifications**, plus a badge on the Inventory tab.
- **Suppliers**: browse registered supplier accounts and their live catalogs, and order with a live
  total. The app checks each order against the supplier's available stock.
- **Reorder** straight from an inventory item.
- **My orders**: follow each order's status in real time. You can cancel an order while it's
  pending, and when you mark it received the quantity is added to your inventory.
- **Invoices**: attach a photo of an invoice, search your invoices, and export one as a PDF to
  Downloads.
- **Profile**: upload a profile photo to Firebase Storage and edit your business details. You can
  also pin your shop on the public map from your current location.

### Supplier account
- **Orders**: incoming orders appear in real time. Accepting an order reserves the stock in a
  transaction; you can also decline an order or mark it dispatched. A badge and a notification
  appear when a new order arrives.
- **Catalog**: the same tools as the pharmacy inventory (photos, barcode lookup, openFDA
  suggestions, demo import, stock alerts).
- **Profile**: the same as the pharmacist profile.

### Public explore (no account)
- A pharmacy directory with **View on map** and **Directions** buttons.
- A medicine search across all pharmacies that only shows items in stock.
- A Google Map of every pharmacy that has shared its location.

### Order lifecycle
```
Pending ──accept──▶ Accepted ──dispatch──▶ Dispatched ──receive──▶ Delivered
   │                    └───────────────receive───────────────────────▲
   ├──decline (supplier)──▶ Declined
   └──cancel (pharmacy)───▶ Cancelled
```

## Architecture

```
app/src/main/java/com/example/pharmasync/
├── PharmasyncApp.kt, AppContainer.kt   Application + manual dependency container
├── data/
│   ├── model/        Domain models (Medicine, Order, UserProfile, …)
│   ├── firestore/    Collection names, document ⇄ model mapping, snapshot → Room sync
│   ├── local/        Room database, entities, DAOs (offline cache)
│   ├── remote/       Retrofit APIs: openFDA (free), Geoapify (optional)
│   └── repository/   Auth, User, Inventory, Order, Supplier, Invoice, Explore, Storage, Location
├── ui/
│   ├── splash/       Routing on launch
│   ├── auth/         Welcome, sign in, sign up
│   ├── session/      SessionViewModel + host activity shared by both roles
│   ├── shared/       Stock and Orders tabs (behave per role)
│   ├── pharmacist/   Home, Suppliers, Supplier catalog, Invoices
│   ├── supplier/     Home
│   ├── profile/      Profile tab
│   ├── explore/      Public pharmacies, medicines, map
│   ├── scanner/      Barcode scanner
│   └── common/       Adapters, dialogs, list screen helper, medicine editor
└── util/             Formatting, notifications, image loading/compression, messages
```

- **Firestore is the source of truth.** Snapshot listeners mirror each user's data into **Room**.
  Screens read from Room, so they render instantly and keep working offline. Writes go to Room
  immediately and Firestore queues them until the device is back online.
- Accepting an order and marking it received use **Firestore transactions**, so stock can't go
  negative or be counted twice.
- Existing Firestore collection and field names are unchanged, so data from earlier versions keeps
  working.

## Setup

1. Open the project in Android Studio (JDK 17+).
2. `app/google-services.json` is already configured for the `pharmasync-e5102` Firebase project.
3. In the Firebase console:
   - **Authentication**: enable the *Email/Password* provider.
   - **Firestore**: create the database.
   - **Storage**: create the default bucket. New `*.firebasestorage.app` buckets require the Blaze
     plan.
4. Deploy the security rules. Uploads and cross-account reads fail without them.
   ```sh
   npm i -g firebase-tools
   firebase login
   firebase use pharmasync-e5102
   firebase deploy --only firestore:rules,storage
   ```
5. In `local.properties`:
   ```properties
   MAPS_API_KEY=your-google-maps-key
   # Optional: geocoding fallback if the device Geocoder can't resolve an address
   GEOAPIFY_API_KEY=
   ```
6. Run the app. To try the full flow, create one **Pharmacist** account and one **Supplier**
   account and verify both email addresses. On each account, tap **Import demo data**, then place
   an order from the pharmacist account.

## Tech
Kotlin · ViewBinding · Material 3 (light and dark) · Coroutines/Flow · ViewModel · Room (KSP) ·
Firebase Auth, Firestore, and Storage · Retrofit · Glide · CameraX + ML Kit · Google Maps · openFDA
