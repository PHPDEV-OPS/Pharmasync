# Firebase setup for Pharmasync

The app is configured for Firebase project **`pharmasync-e5102`** (see `app/google-services.json`).
Accounts, suppliers, pharmacies, and orders are only shared between users when the steps below are
done in **that** project.

## 1. Authentication
Firebase console → **Authentication** → Sign-in method → enable **Email/Password**.

## 2. Cloud Firestore (required)
Firebase console → **Firestore Database** → **Create database** → choose a location → start in
**production mode**.

Without a Firestore database, every write stays in the phone's local cache. Each device then looks
as if it has data, but other accounts (suppliers, pharmacies, public explore) never see it. The app
now detects this and shows *"Cloud Firestore isn't set up for this Firebase project"*.

To check which project you're looking at, open the console URL. It must contain
`project/pharmasync-e5102`.

## 3. Security rules (required)
The rules are in `firebase/`. Deploy them with the Firebase CLI:

```sh
npm install -g firebase-tools
firebase login
firebase use pharmasync-e5102
firebase deploy --only firestore:rules          # add ",storage" once Storage exists
```

Without these rules, pharmacists can't list suppliers, and people browsing without an account can't
see pharmacies or medicines. The app reports *"Access was denied by Firestore security rules"*.

## 4. Cloud Storage (optional)
New Storage buckets (`*.firebasestorage.app`) require the **Blaze** (pay-as-you-go) plan. If
Storage isn't enabled, the app compresses profile photos, product images, and invoice images and
saves them directly in Firestore instead, so uploads still work. To use Storage, enable it in the
console, then run `firebase deploy --only storage`.

## 5. Maps
The pharmacy map uses **OpenStreetMap** tiles through osmdroid. It needs no API key and no billing
account. (Google Maps had shown blank tiles because the key's Google Cloud project has billing
disabled.)

## 6. Existing accounts created before Firestore existed
Profiles created while the database was missing were never uploaded. The simplest fix is to create
those accounts again, or to sign in to each one on the device that created it while online: pending
writes in that device's cache are uploaded once the database exists.
