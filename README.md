# Pharmasync

Pharmasync is an Android pharmacy inventory and ordering application for pharmacists and suppliers. It helps users manage medicine stock, discover nearby pharmacies, scan product barcodes, place supplier orders, and work with invoices from a single mobile app.

## Overview

The project contains:

- An Android application written primarily in Kotlin.
- A TypeScript backend API built with Hono and esbuild.
- Neon Postgres and Neon Object Storage integration for application data and files.
- Firebase Authentication for user sign-in and identity verification.
- Local Room storage for device-side persistence and offline-friendly workflows.

## Features

- **Role-based workflows** for pharmacists and suppliers.
- **Authentication and profile setup** using Firebase Authentication.
- **Inventory management** for tracking medicines and stock levels.
- **Barcode scanning** with CameraX and Google ML Kit.
- **Supplier catalog browsing** and business-to-business ordering.
- **Invoice preview and management**.
- **Pharmacy and store discovery** with location support and OpenStreetMap-based maps.
- **Local persistence** using Room for responsive, offline-friendly data access.
- **Notifications and location services** supported by Android permissions.

## Technology Stack

### Android application

- Kotlin
- Android SDK 34
- Minimum Android SDK 26
- Java 17 / Kotlin JVM target 17
- AndroidX and Material Design
- View Binding
- Kotlin Coroutines
- Room Database
- Retrofit and OkHttp
- Firebase Authentication
- OpenStreetMap tiles through osmdroid
- Android location services
- CameraX
- Google ML Kit Barcode Scanning
- Glide for image loading

### Backend

- TypeScript
- Node.js
- Hono
- PostgreSQL via `pg`
- `jose` for token-related functionality
- esbuild
- Neon Functions, Postgres, and Object Storage

## Repository Structure

```text
.
├── app/                 # Android application source and resources
├── backend/             # TypeScript backend API
├── firebase/            # Firebase security rules
├── firebase.json         # Firebase rules configuration
├── build.gradle.kts     # Root Gradle build configuration
├── settings.gradle.kts  # Gradle project settings
└── README.md
```

## Requirements

Before building Pharmasync locally, install or configure:

- Android Studio with Android SDK 34 support.
- JDK 17.
- An Android emulator or Android device running Android 8.0/API 26 or newer.
- Node.js and npm for backend development.
- A Firebase project with Authentication enabled.
- Access to the Pharmasync backend and its Neon resources.

Optional services and keys:

- `GEOAPIFY_API_KEY` for the geocoding fallback.
- `CARTO_API_KEY` for CARTO map tiles. When it is not supplied, the app can fall back to plain OpenStreetMap tiles.
- `API_BASE_URL` for overriding the backend API endpoint.

## Android Setup

1. Clone the repository:

   ```bash
   git clone https://github.com/PHPDEV-OPS/Pharmasync.git
   cd Pharmasync
   ```

2. Open the project in Android Studio.

3. Create `local.properties` in the repository root if it does not already exist. Add the configuration required for your environment:

   ```properties
   sdk.dir=/path/to/Android/sdk
   API_BASE_URL=https://your-api-endpoint/
   GEOAPIFY_API_KEY=your-geoapify-key
   CARTO_API_KEY=your-carto-key
   ```

4. Add the Firebase Android configuration file supplied by your Firebase project at:

   ```text
   app/google-services.json
   ```

5. Sync the project with Gradle.

6. Build and install the debug application:

   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

   On Windows, use `gradlew.bat` instead of `./gradlew`.

Do not commit `local.properties`, API keys, Firebase credentials, or other environment-specific secrets.

## Backend Setup

The backend is located in `backend/` and uses TypeScript with Node.js tooling.

```bash
cd backend
npm install
npm run typecheck
npm run build
```

The backend expects environment-specific configuration for database access, object storage, and Firebase token verification. Review the backend source and deployment configuration before running it in a local or hosted environment.

## Firebase Rules

Firebase configuration is stored in `firebase.json`. The repository includes rules for:

- Firestore: `firebase/firestore.rules`
- Storage: `firebase/storage.rules`

Review and deploy rules with the Firebase CLI only after authenticating against the intended Firebase project.

## Configuration Reference

The Android build reads these values from `local.properties` and exposes them through generated `BuildConfig` fields:

| Property | Purpose | Required |
| --- | --- | --- |
| `API_BASE_URL` | Pharmasync backend API base URL | Yes for API features |
| `GEOAPIFY_API_KEY` | Optional geocoding fallback | No |
| `CARTO_API_KEY` | Optional CARTO raster map tile key | No |

The app also requires the Firebase configuration file at `app/google-services.json` for Firebase Authentication integration.

## Permissions

Depending on the feature being used, Android may request permission for:

- Internet and network state.
- Camera access for barcode scanning.
- Fine or coarse location for location-based features.
- Notifications on supported Android versions.

Permissions should be granted only when needed by the corresponding feature.

## Testing and Validation

Run the Android unit and instrumentation tests from the repository root:

```bash
./gradlew test
./gradlew connectedAndroidTest
```

Run backend type checking and the production build with:

```bash
cd backend
npm run typecheck
npm run build
```

## Build Outputs

The Android APK generated by Gradle is written beneath:

```text
app/build/outputs/apk/
```

The exact output path depends on the selected build variant, such as `debug` or `release`.

## Security Notes

- Keep `google-services.json` and all API credentials appropriate for the target environment.
- Do not commit secrets in `local.properties` or backend environment files.
- Review Firebase rules and backend authorization before deploying to production.
- Use HTTPS for backend API communication.

## License

No license file is currently defined in this repository. Contact the repository maintainers before redistributing or using Pharmasync outside the project.
