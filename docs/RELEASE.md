# Niqdah Release APK Notes

This is for a personal APK, not Play Store submission.

## Build A Debug APK

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Create A Release Keystore Locally

Do not create the keystore inside the repository. Store it in a private location such as:

```text
C:\Users\Administrator\Documents\Niqdah-private\niqdah-release.jks
```

Create it with:

```powershell
keytool -genkeypair -v -keystore C:\Users\Administrator\Documents\Niqdah-private\niqdah-release.jks -alias niqdah -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and passwords in a password manager or other private backup. Losing the keystore means future signed updates may not install over the old APK.

## Configure Signing Without Committing Secrets

Use user-level Gradle properties or environment variables. Do not commit passwords, keystores, `.jks`, `.keystore`, or `keystore.properties`.

Example local-only properties in `%USERPROFILE%\.gradle\gradle.properties`:

```properties
NIQDAH_RELEASE_STORE_FILE=C:\\Users\\Administrator\\Documents\\Niqdah-private\\niqdah-release.jks
NIQDAH_RELEASE_STORE_PASSWORD=your-store-password
NIQDAH_RELEASE_KEY_ALIAS=niqdah
NIQDAH_RELEASE_KEY_PASSWORD=your-key-password
```

If signing is not configured, Gradle can still prepare an unsigned release APK for inspection.

## Build A Release APK

```powershell
.\gradlew.bat :app:assembleRelease
```

Release APK output:

```text
app/build/outputs/apk/release/
```

## Install On Phone

1. Enable installation from trusted local sources on the phone if needed.
2. Connect the phone with USB debugging enabled.
3. Install with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

For a signed release APK, replace the path with the release APK path.

## Rollback

Keep the last known-good APK in a private folder outside the repo. To roll back:

```powershell
adb install -r path\to\last-known-good.apk
```

If Android refuses to downgrade because of `versionCode`, uninstall first, then install the old APK. Uninstalling can remove local app data, so confirm Firebase sync before doing that.
