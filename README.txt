FOX ANGEL — Android test project 0.1

This is an Android Studio source project, not a prebuilt APK.
Features:
- Dark FOX ANGEL screen
- IP field defaulting to 192.168.0.178
- REST API key masked in the UI
- CONNECT tests GET /<key>/get_state/
- A: GET set_state/?state=on
- 0: GET set_state/?state=off
- STATE: GET get_state/
- V: starts one Android speech-recognition session; expects ANGEL + command
- Saves IP and API key in app-private SharedPreferences (not encrypted)

Important test notes:
- Phone and FOX must be on the same reachable network.
- The app uses HTTP because the supplied Python source uses http://FOX.
- Speech recognition depends on Android's installed speech service and network/language support.
- This first test build does not provide continuous background wake-word listening.
- Verify the FOX response/state before relying on relay control.

Build APK:
1. Open this folder in Android Studio (recent version).
2. Allow Gradle sync and install Android SDK Platform 35 if prompted.
3. Build > Build Bundle(s) / APK(s) > Build APK(s).
4. APK output: app/build/outputs/apk/debug/app-debug.apk
