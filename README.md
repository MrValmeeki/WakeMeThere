# WakeMeThere

**WakeMeThere** is a modern Android travel assistant designed to ensure you never miss your stop again. Whether you're on a bus, train, or car, the app tracks your journey in real-time and triggers a loud alarm before you reach your destination.

## 🚀 Features

- **Google Maps Integration**: Select your destination directly on the map or use the powerful Material 3 Search Bar.
- **Smart Alarm Threshold**: Set how many minutes (5-60) before arrival you want to be woken up.
- **Background Tracking**: Uses a Foreground Service to keep tracking your location reliably even when your phone is locked or the app is minimized.
- **Advanced Animations**:
    - **Rotating Globe**: A premium procedural globe animation during tracking.
    - **Uber-Style Progress**: A custom striped loading bar with a moving vector bus icon that fills as you approach your stop.
- **Saved Destinations**: Store your favorite or frequent locations in a local Room database for quick access.
- **Live Journey Map**: Expand the map during your trip to see your real-time position along the shortest road-based route.

## 📥 Getting Started

### For Users
Simply go to the **[Releases](https://github.com/[YOUR_USERNAME]/WakeMeThere/releases)** section of this repository and download the latest `.apk` file. 
- This version is **ready to use** and requires no technical setup or API keys.
- You may need to "Allow installation from unknown sources" on your Android device to install the APK.

### For Developers
If you are cloning the source code to build or modify the project personally:
1. Obtain an API key from the [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the following APIs: **Maps SDK for Android**, **Places API (New)**, and **Routes API**.
3. Open `app/build.gradle.kts` and replace `"YOUR_API_KEY_HERE"` with your actual key in the `manifestPlaceholders`.
4. Sync project with Gradle and run.

## 🛠️ Technical Stack

- **UI**: Jetpack Compose (Material 3)
- **Maps**: Google Maps SDK for Android & Maps Compose
- **Search**: Google Places API (New)
- **Routing**: Google Routes API (New)
- **Database**: Room Persistence Library with KSP
- **Architecture**: MVVM with ViewModel and StateFlow
- **Service**: Android Foreground Service for persistent tracking

## ⚠️ Known Issues & Setup Requirements

**Note on Routing (Straight Line Path):**
If the journey path appears as a **straight line** instead of following roads, this is a known setup requirement related to Google Cloud project configuration.

### For Developers (Personal Builds)
If you are building the app from source and encounter this issue, follow these steps to resolve it:
1.  **Enable Routes API**: In your Google Cloud Console, ensure the **"Routes API"** (New) is enabled.
2.  **Check Billing**: The modern Routes API **requires** a billing account to be linked to your project, even if usage stays within the $200 free monthly credit.
3.  **API Restrictions**: If your API key is restricted, go to **APIs & Services > Credentials** and ensure "Routes API" is added to the "Allowed APIs" list.
4.  **Cleartext Traffic**: Ensure `android:usesCleartextTraffic="true"` is set in the `AndroidManifest.xml` (already included in source) to allow the app to communicate with Google's routing servers.

### For Users (Release APK)
Ensure you have a stable internet connection. If the issue persists, the built-in API key may have reached its quota or billing restrictions.

## 📦 How to Build

1. Clone the repository.
2. Add your API Key to `app/build.gradle.kts`.
3. Sync project with Gradle.
4. Run `./gradlew assembleDebug` for a test build or `./gradlew assembleRelease` for the final optimized APK.
