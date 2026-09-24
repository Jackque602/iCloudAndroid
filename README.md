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
- Upload photos from your device — two ways, see below.
- Session persistence — closing and reopening the app skips sign-in as long
  as the saved session is still valid.
- Sign out clears all saved tokens/cookies from the device (including the
  in-app browser's).

### Uploading photos

The gallery's top bar has two upload entry points:

1. **☁️ Add photos (recommended).** Opens an in-app browser already signed
   into `icloud.com/photos` with your existing session — no re-login. Use
   Apple's own upload button there. This is the reliable path, since it's
   just Apple's real web app in a WebView.
2. **🧪 Experimental in-app upload.** A native system photo picker that
   uploads directly via a from-scratch reconstruction of CloudKit's private
   write API. Shown behind a warning dialog because, unlike the read path
   (which mirrors years of working open-source prior art — pyicloud,
   icloudpd), **no one has published a confirmed-working third-party iCloud
   Photos upload implementation**. It may simply fail — that's the expected,
   safe outcome — or in rare cases create a malformed library entry. See
   `UploadApi.kt` for exactly what it sends and why each part is uncertain.

## What it doesn't do (yet)

- Downloading/saving originals to local storage.
- Albums, favorites, search, or editing.
- Deleting or modifying existing items in your iCloud library.

These are natural next steps if you want to extend it — `PhotosApi` already
fetches most of the metadata (favorite/hidden flags, filenames, timestamps)
needed for them.

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
│   ├── PhotosRepository.kt  Pagination + upload wrapper
│   └── UploadApi.kt         EXPERIMENTAL native upload (see warning above)
└── ui/
    ├── login/    Sign-in + 2FA screens
    ├── gallery/  Photo grid (LazyVerticalGrid + Coil) + upload entry points
    ├── viewer/   Full-screen pager
    ├── upload/   WebView uploader (the reliable path)
    └── AppNav.kt Simple state-based screen switcher
```

Once signed in, `AppleAuthApi.accountLogin()` resolves the CloudKit database
web service URL for your account (`webservices.ckdatabasews.url` in Apple's
response) and everything else — thumbnails, full-resolution images —
authenticates purely via the session cookies already attached to the shared
`OkHttpClient`, exactly like a browser tab would.

## Downloading a build

Every push of a `vX.Y.Z` tag (or a manual run of the *Release APK* GitHub
Actions workflow) builds a debug-signed APK and attaches it to a new
[GitHub Release](../../releases) — no local Android SDK needed. Grab the
`.apk` from the latest release, transfer it to your phone, and install it
(Android will prompt you to allow "install unknown apps" for whichever app
you used to open the file, the first time). Every release is signed with the
same checked-in debug key (`app/debug.keystore`), so installing a newer
release updates the app in place instead of requiring an uninstall.

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
> not be compiled or run here to verify it end-to-end (the GitHub Actions
> release workflow, running on a normal GitHub-hosted runner, does not have
> this restriction). The code has been written and reviewed carefully
> against the known request/response shapes of this API, but please
> sanity-check the sign-in + gallery flow before relying on it — and open an
> issue (or just fix it) if a field name has drifted from what Apple's
> servers currently return.

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
- **Experimental in-app upload fails** — expected; use "Add photos" (the
  WebView uploader) instead. See the upload section above for why.
