# KidLock TV

**A 100% offline parental app-lock for Amazon Fire TV, Fire tablets, and Android phones.**

KidLock TV lets a parent lock the apps *they* choose behind a PIN, so a child can only open what the
parent allows. When someone opens a locked app, KidLock shows a full-screen lock that requires the
parent's **PIN** or an optional **secret remote sequence** (a series of D-pad directions) to continue.

Everything runs on-device. There is **no internet permission, no account, no analytics, no ads, and no
payments.** Your PIN, locked-app list, and history never leave the device.

> The package id is still `com.fliptofocus` (the app's identity on the store), but the product is
> KidLock TV. It was previously a phone "focus blocker" called FlipToFocus.

---

## ✨ Features

- **Pick which apps to lock** from the apps installed on the device (phone/tablet launcher **and** Fire
  TV leanback launcher apps).
- **PIN unlock** (4–6 digits) entered on a fully D-pad-operable numeric pad.
- **Secret remote sequence** (optional) — a unique, TV-native unlock: press a private sequence of
  ↑ ↓ ← → on the remote, then the center button.
- **Recovery code** generated once at setup so a forgotten PIN never permanently locks you out.
- **In-app "Preview lock"** — see exactly what a child sees and verify the lock works without needing
  any special permission (also lets an app reviewer confirm the feature in-app).
- **Access log** — a local history of when locked apps were opened (unlocked / denied).
- **Self-protection** — KidLock's own settings are behind the PIN, so a child can't open KidLock and
  turn locking off.
- **Fully remote-navigable** dark UI with a clear focus indicator on every control.

---

## 🧱 Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3), D-pad-first components |
| Architecture | Clean-ish Architecture + MVVM |
| Async | Coroutines / Flow |
| DI | Hilt |
| Persistence | Room (SQLite) |
| Foreground detection | `AccessibilityService` (foreground package name only) |
| Lock surface | A full-screen `Activity` (**not** a `SYSTEM_ALERT_WINDOW` overlay) |
| Min / Target SDK | 26 / 34 |
| JDK | 17 |

---

## 🔑 Why this design (and why the previous version failed Amazon review)

The prior version required the **"Draw over other apps" (SYSTEM_ALERT_WINDOW)** permission and gated the
whole app behind it, and used **flip/shake motion gestures** to unlock. On a Fire TV Stick that is
impossible:

- Fire OS (6+) **removes the overlay-permission settings screen**, so `canDrawOverlays()` can only be
  granted via ADB — a reviewer/parent can never grant it with a remote. The old onboarding's
  "Next/Start" button stayed disabled forever → *"App is still asking to give access, unable to check
  in-app."*
- Fire TV Sticks have **no accelerometer/gyroscope and no touchscreen**, so flip/shake could never
  complete.

KidLock TV fixes both:

- **No overlay permission at all.** Locking uses an `AccessibilityService` (which *is* reachable from
  Settings → Accessibility on Fire TV) to detect the foreground app, then launches a normal full-screen
  lock `Activity`.
- **No sensors/touch required.** Unlock is PIN or a D-pad sequence; the whole UI is remote-navigable.
- **Never gated behind a permission.** Setup only requires creating a PIN. Enabling accessibility is
  clearly disclosed and **skippable**; even without it you can set the PIN, choose apps, and use
  **Preview lock**.

---

## 🔐 Permissions

| Permission | Manifest name | Why |
|---|---|---|
| Accessibility service | `BIND_ACCESSIBILITY_SERVICE` (held by the system) | Read the foreground app's **name** on-device to know when a locked app was opened. Never reads screen content. |

There is **no `INTERNET`, no `SYSTEM_ALERT_WINDOW`, no `QUERY_ALL_PACKAGES`, no `VIBRATE`, and no
foreground service.** The installed-app list is built with scoped
`queryIntentActivities(ACTION_MAIN / CATEGORY_LAUNCHER | CATEGORY_LEANBACK_LAUNCHER)` queries.

A **prominent disclosure** (`R.string.accessibility_disclosure`) is shown before sending the user to the
Accessibility settings screen.

---

## ⚙️ How locking works

- `service/FocusAccessibilityService` observes window-state changes and polls the foreground package.
- When a **locked** app comes to the foreground and is not currently unlocked, it launches
  `lock/LockActivity`, which shows the PIN / secret-sequence / recovery entry.
- A correct credential calls `LockController.grantForeground(pkg)`, logs an `UNLOCKED` access event, and
  finishes the lock so the app appears. Backing out logs `DENIED` and returns to the device home.
- `LockController` tracks grants: an unlocked app stays open while in the foreground; leaving it re-locks
  it (immediately, or after a configurable grace period set in Settings).
- Credentials are stored only as **salted SHA-256 hashes** (`util/PinSecurity`).

> **Note on cross-app locking:** Android 10+ restricts background activity starts. Fire OS generally
> permits the accessibility-driven `startActivity` used here, but where it doesn't, the app still works
> and is fully demonstrable via **Preview lock**. This is intentional so review and everyday use never
> depend on that edge.

---

## 🗂️ Project structure

```
app/src/main/java/com/fliptofocus/
├── FlipToFocusApp.kt · MainActivity.kt
├── di/          # DatabaseModule · RepositoryModule
├── domain/      # model/ (AppConfig, BlockedApp, FocusSession) + repository/ interfaces
├── data/        # local/{RoomData, Mappers} · repository/ impls · InstalledAppsProvider
├── service/     # FocusAccessibilityService (foreground detection + lock launch)
├── lock/        # LockController · LockActivity · LockScreenUI · Combo · LockCredentialsManager
├── ui/          # components (D-pad) · onboarding · home · blocklist · settings · navigation · theme
└── util/        # PinSecurity · DeviceUtils · PermissionUtils · Constants
```

---

## 🚀 Build & run

**Prerequisites:** Android Studio, JDK 17, Android SDK Platform 34 + Build-Tools 34.

```bash
# CLI sanity build (or just open in Android Studio and Run)
./gradlew assembleDebug        # macOS/Linux
gradlew.bat assembleDebug      # Windows
```

`local.properties` (SDK path) is git-ignored; Android Studio regenerates it, or create it with
`sdk.dir=/absolute/path/to/Android/Sdk`.

Test the lock end-to-end: complete setup (create a PIN), add an app on **Locked apps**, enable KidLock in
**Accessibility**, then open that app — or just use **Preview lock screen** on the home screen.

---

## 📦 Amazon Appstore submission checklist

- **Supported devices:** the manifest declares `touchscreen`/`leanback` as *not required*, so the same
  APK targets Fire TV, Fire tablet, and phones. Confirm the device list in the Developer Console.
- **TV banner:** `res/drawable/tv_banner.xml` renders on the Fire TV home. Upload a high-resolution PNG
  banner for the store listing separately in the console.
- **Privacy policy:** the app is fully offline and collects nothing; provide a short privacy policy
  stating that (Amazon requires a policy even for offline apps).
- **Accessibility usage:** be ready to explain that the accessibility service is used only to read the
  foreground app name to enforce a parental lock the user configured.
- **Reviewer path:** install → create PIN → **Preview lock screen** demonstrates the core feature with no
  special permission needed.

---

## 📄 License

Add your preferred license (e.g. MIT / Apache-2.0) before publishing.
