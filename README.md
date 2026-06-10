# WakeMeThere 🌍🚍

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

## 🛠️ Technical Stack

- **UI**: Jetpack Compose (Material 3)
- **Maps**: Google Maps SDK for Android & Maps Compose
- **Search**: Google Places API (New)
- **Routing**: Google Routes API (New)
- **Database**: Room Persistence Library with KSP
- **Architecture**: MVVM with ViewModel and StateFlow
- **Service**: Android Foreground Service for persistent tracking

## ⚠️ Known Issues & Setup Requirements

1.  **API Key Configuration**: To use the map, search, and routing features, you must provide your own Google Maps API Key in `app/build.gradle.kts`.
2.  **Road-Based Routing (403 Error)**: 
    - **Issue**: The blue path might appear as a straight line if the **Routes API (New)** is not fully set up.
    - **Fix**: Ensure your Google Cloud project has an active **Billing Account** linked and that the "Routes API" is enabled and unrestricted for your API key.
3.  **Permissions**: The app requires "Always Allow" location access and notification permissions to trigger alarms reliably in the background.

## 📦 How to Build

1. Clone the repository.
2. Add your API Key to `app/build.gradle.kts`.
3. Sync project with Gradle.
4. Run `./gradlew assembleDebug` for a test build or `./gradlew assembleRelease` for the final optimized APK.
