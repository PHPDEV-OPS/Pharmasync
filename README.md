# Pharmasync


The **Pharmasync** app is a comprehensive solution designed to enhance the efficiency and reliability of medicine management for both pharmacists and suppliers. It streamlines inventory tracking, reordering, and store discovery.

## Key Features
- **Role-Based Access**: Specialized interfaces for Pharmacists and Suppliers.
- **Inventory Management**: Real-time stock tracking with low-stock alerts.
- **B2B Ordering**: Pharmacists can reorder stock directly from registered suppliers.
- **Store Discovery**: Google Maps integration to find registered medical shops.
- **Invoice Management**: Digital tracking of invoices and receipts.
- **Offline Sync**: Data is cached locally and synced with Firebase Firestore.

## Technologies Used
- **Kotlin**: Primary development language.
- **Retrofit**: For REST API interactions (Geoapify).
- **Firebase**: Firestore (Database), Auth, and Storage.
- **Google Maps SDK**: For location services.
- **Material Design 3**: For a modern UI.

## Getting Started
To get a local copy up and running, follow these steps:

### Prerequisites
- Android Studio Ladybug or later.
- A Firebase project with Firestore and Auth enabled.
- A Google Maps API key.
- A Geoapify API key.

### Installation
1. Clone the repo:
   ```sh
   git clone https://github.com/PHPDEV-OPS/Pharmasync.git
   ```
2. Open the project in Android Studio.
3. Add your `google-services.json` to the `app/` directory.
4. Add your `MAPS_API_KEY` to `local.properties`.
5. Build and run the app on an emulator or physical device.


