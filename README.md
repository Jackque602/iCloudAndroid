# iCloud Photos for Android

A native Android app (Kotlin + Jetpack Compose) that lets you browse **your own**
iCloud Photos library in a Google-Photos-style grid, sign in with your own
Apple ID, and view photos/videos full-screen.

## Important: this uses an unofficial, undocumented API

Apple does not publish a public API for third-party apps to access iCloud
Photos. This app works the same way the open-source [pyicloud](https://github.com/picklepete/pyicloud)
and [icloudpd](https://github.com/icloud-photos-downloader/icloud_photos_downloader)
projects do: it replicates the private requests that `https://www.icloud.com`'s
own web app makes (the same sign-in endpoints, the same CloudKit-backed Photos
web service), using your own Apple ID credentials over HTTPS directly to
Apple's servers.

Consequences of that:

- **It can break at any time.** Apple can change these endpoints, payloads,
  or field names without notice, and this app has no official support
  contract with Apple.
- **It's for your own account only.** This is a personal client, not a
  general-purpose iCloud SDK — don't point it at accounts you don't own or
  automate it against many accounts.
- **Your password is sent directly to `idmsa.apple.com`** (Apple's real
  sign-in endpoint) over TLS — the app itself never stores it. What's
  persisted on-device (in `EncryptedSharedPreferences`, backed by the Android
  Keystore) is the session/trust tokens and cookies Apple issues after a
  successful sign-in, so you don't have to re-enter your password and 2FA
  code every launch.
- 2FA is supported: if your Apple ID has two-factor authentication enabled
  (it should), you'll be prompted for the 6-digit code sent to your trusted
  device the first time you sign in on this device.

## What it does

- Sign in with Apple ID + password, with a 2FA code step when required.
- Browse your iCloud Photos library in a scrolling, paginated 3-column grid
  (newest first), Google Photos-style.
- Tap a photo/video thumbnail to view it full-screen in a horizontal pager.
- Session persistence — closing and reopening the app skips sign-in as long
  as the saved session is still valid.
- Sign out clears all saved tokens/cookies from the device.

## What it doesn't do (yet)

- Uploading/backing up photos from the device to iCloud.
- Downloading/saving originals to local storage.
- Albums, favorites, search, or editing.
- Deleting or modifying anything in your iCloud library (the app only reads).

These are natural next steps if you want to extend it — the `PhotosApi`
already fetches most of the metadata (favorite/hidden flags, filenames,
timestamps) needed for them.

## Architecture

```
app/src/main/java/com/icloudandroid/
├── auth/            Apple ID sign-in state machine
│   ├── AppleAuthApi.kt      Raw HTTP calls (idmsa signin, 2FA, accountLogin)
│   ├── AuthRepository.kt    State machine used by the login screen
│   └── SessionStore.kt      Encrypted storage for tokens
├── network/
│   ├── HttpClientProvider.kt  Shared OkHttpClient + cookie jar
│   └── PersistentCookieJar.kt Encrypted, persisted cookie storage
├── photos/
│   ├── PhotosApi.kt         CloudKit "records/query" calls + parsing
│   └── PhotosRepository.kt  Pagination wrapper
└── ui/
    ├── login/    Sign-in + 2FA screens
    ├── gallery/  Photo grid (LazyVerticalGrid + Coil)
    ├── viewer/   Full-screen pager
    └── AppNav.kt Simple state-based screen switcher
```

Once signed in, `AppleAuthApi.accountLogin()` resolves the CloudKit database
web service URL for your account (`webservices.ckdatabasews.url` in Apple's
response) and everything else — thumbnails, full-resolution images —
authenticates purely via the session cookies already attached to the shared
`OkHttpClient`, exactly like a browser tab would.

## Building

Requires JDK 17 and the Android SDK (compileSdk/targetSdk 35, minSdk 26 —
Android 8.0+). The Gradle wrapper is checked in, so from a machine with
normal internet access (able to reach `dl.google.com` for AndroidX/AGP
artifacts):

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Opening the project in a
recent Android Studio (Ladybug or newer) and hitting Run works the same way.

> **Note:** this project was scaffolded and coded in a sandboxed cloud
> session whose network policy blocks `dl.google.com`, so the build could
> not be compiled or run here to verify it end-to-end. The code has been
> written and reviewed carefully against the known request/response shapes
> of this API, but please build it locally and sanity-check the sign-in +
> gallery flow before relying on it — and open an issue (or just fix it) if
> a field name has drifted from what Apple's servers currently return.

## Troubleshooting

- **"iCloud did not return a Photos service URL for this account"** — some
  accounts (e.g. those under Advanced Data Protection, or with iCloud Photos
  turned off) may not expose `ckdatabasews`. Turn on iCloud Photos in
  Settings → [your name] → iCloud → Photos on an Apple device first.
- **Stuck on the 2FA screen** — codes expire quickly; request a fresh one by
  going back and signing in again.
- **Sign-in works but the grid is empty** — Apple's CloudKit field names for
  photo/video resources (`resJPEGThumbRes`, etc.) are undocumented and have
  shifted before; check `PhotosApi.THUMB_FIELD_PRIORITY` /
  `FULL_FIELD_PRIORITY` against what your account's `records/query` response
  actually contains.
