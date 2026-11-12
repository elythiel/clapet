# Clapet

Minimal Niagara-style launcher for the Xiaomi Mix Flip cover screen (HyperOS). Brings every app to the outer display — no root required.

## Install

Download the latest `clapet-x.y.z.apk` from [Releases](https://github.com/elythiel/clapet/releases) and open it on the phone.

## Requirements

- Xiaomi Mix Flip running HyperOS 3 (Android 16)
- To build: Android Studio with JDK 17 or later, Android SDK platform 37

## Build

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Release

1. In `app/build.gradle.kts`, bump `versionCode` by one and set `versionName` (`x.y.z`). Commit.
2. Tag and push:

   ```bash
   git tag -s vx.y.z -m "vx.y.z"
   git push && git push origin vx.y.z
   ```

3. Build the signed APK from the tagged commit:

   ```bash
   ./gradlew assembleRelease
   cp app/build/outputs/apk/release/app-release.apk build/clapet-x.y.z.apk
   ```

4. On GitHub, **Releases → Draft a new release**: pick the `vx.y.z` tag, write the notes, attach `build/clapet-x.y.z.apk`, publish.

## License

[MIT](LICENSE). Clapet is an independent project, not affiliated with Xiaomi.
