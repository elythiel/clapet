# Clapet

Minimal Niagara-style launcher for the Xiaomi Mix Flip cover screen (HyperOS). Brings every app to the outer display — no root required.

> **Status:** early development. Nothing usable yet.

## Requirements

- Xiaomi Mix Flip running HyperOS 3 (Android 16)
- To build: Android Studio with JDK 17 or later, Android SDK platform 37

## Build

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## License

[MIT](LICENSE). Clapet is an independent project, not affiliated with Xiaomi.
