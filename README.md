# Cliqo Cheq (Android, Kotlin)

A native Android app that shows **which apps have been opened on this phone**, with timestamps, over the last 24 hours. It works without staying open in the background, because Android itself records app opens at the system level — Cliqo Cheq just reads that history when you open it.

This uses the official `UsageStatsManager` API (the same one Digital Wellbeing uses). It only works after the phone's owner grants the visible **"Usage access"** permission in Settings, which can be turned off any time. History goes back a few days — whatever the system retains — which is all this app needs.

---

## How do I get the installable app (the .apk)?

You do **not** use PyCharm — that's for Python. Android apps are built with **Android Studio** (free from Google) or by a cloud service. Pick whichever path suits you. Both produce an `app-debug.apk` file, which is the installable app.

### Path A — Android Studio (recommended, one-time setup)

1. Download and install **Android Studio**: https://developer.android.com/studio
2. Unzip this project somewhere.
3. In Android Studio: **File → Open** → select the `Cliqo Cheq` (AppUsageTracker) folder → **OK**.
4. Wait for it to finish "syncing" (it downloads the Android pieces automatically the first time — a few minutes).
5. To get the installable file: menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. When it finishes, a small popup appears bottom-right — click **locate**. That folder contains **`app-debug.apk`**. Copy it to your phone and tap to install (you'll allow "install from unknown sources" once).

   *Or* to run it straight onto a phone/emulator: plug in your phone (with USB debugging on) or start an emulator, then click the green **▶ Run** button.

### Path B — Build in the cloud with GitHub (no installing anything)

This project includes a GitHub Actions workflow that builds the APK on GitHub's servers for free.

1. Create a free account at https://github.com if you don't have one.
2. Make a new repository, then upload all the files from this project (the **Add file → Upload files** button accepts a drag-and-drop of the whole folder).
3. Go to the **Actions** tab → the "Build APK" workflow runs automatically. (If it asks, click the button to enable workflows.)
4. When the run finishes (green check), open it and scroll to **Artifacts** → download **`CliqoCheq-debug-apk`**. Inside is `app-debug.apk`.
5. Copy that `.apk` to your phone and tap to install.

> Why couldn't you just hand me the .apk directly? The environment I was built in is blocked from reaching Google's build servers, so I can't compile it here. Both paths above do the actual compile where that access exists.

---

## Using the app

1. Launch **Cliqo Cheq**.
2. Tap **Open Settings** → find Cliqo Cheq under **Usage access** → toggle it **on**.
3. Return to the app. You'll see the list of recently opened apps (name, time, package). Tap the refresh icon to reload.

---

## What's inside

```
AppUsageTracker/
├── .github/workflows/build-apk.yml   # cloud build (Path B)
├── settings.gradle.kts
├── build.gradle.kts                  # plugin versions (Kotlin 2.0.21, AGP 8.5.2)
├── gradle.properties
├── gradle/wrapper/…                  # Android Studio completes this on first sync
├── branding/cliqo_cheq_icon_512.png  # large logo (e.g. for a store listing)
└── app/
    ├── build.gradle.kts              # SDK levels, Compose, dependencies
    └── src/main/
        ├── AndroidManifest.xml       # declares PACKAGE_USAGE_STATS + the icon
        ├── res/
        │   ├── mipmap-*/…            # app launcher icons (all densities, adaptive)
        │   ├── drawable/logo.png     # in-app logo
        │   └── values/…              # app name "Cliqo Cheq", colors, theme
        └── java/com/example/appusagetracker/
            ├── UsageRepository.kt     # queries UsageStatsManager.queryEvents()
            ├── UsageViewModel.kt      # permission check + load state
            ├── MainActivity.kt        # Jetpack Compose UI
            └── Theme.kt               # Material3 theme
```

## How it works (for later tinkering)

1. **Permission check** — `UsageRepository.hasUsageAccess()` asks `AppOpsManager` whether usage access is allowed.
2. **Granting** — if not, the UI shows a prompt + "Open Settings" button that launches the system Usage-access screen.
3. **Reading history** — `getAppOpenEvents()` calls `UsageStatsManager.queryEvents(start, end)` and keeps the "app came to foreground" events, newest first.
4. **Refresh on resume** — `MainActivity.onResume()` re-checks permission and reloads, so returning from Settings updates the screen.

Change the lookback window in `UsageRepository.getAppOpenEvents(lookbackMillis = …)`. Ideas for next: app icons in the list, per-app open counts, grouping by day.

## Versions

- Android Gradle Plugin 8.5.2, Kotlin 2.0.21 (Compose compiler plugin)
- compileSdk / targetSdk 35, minSdk 23
- Compose BOM 2024.09.00, Material3

If Android Studio offers to upgrade AGP/Kotlin, accepting its recommendation is fine.
