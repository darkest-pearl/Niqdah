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

Use user-level Gradle properties or environment variables. Do not commit passwords, keystores, `.jks`, `.keystore`, `.p12`, or local signing property files.

Example local-only properties in `%USERPROFILE%\.gradle\gradle.properties`:

```properties
NIQDAH_KEYSTORE_FILE=C:\\Users\\Administrator\\Documents\\Niqdah-private\\niqdah-release.jks
NIQDAH_KEYSTORE_PASSWORD=your-store-password
NIQDAH_KEY_ALIAS=niqdah
NIQDAH_KEY_PASSWORD=your-key-password
```

Environment variable names are the same if you prefer setting them in PowerShell. Older `NIQDAH_RELEASE_*` names are still accepted for local compatibility.

If signing is not configured, Gradle can still prepare an unsigned release APK for inspection, but that unsigned release APK cannot be installed normally on a phone.

## Build A Release APK

```powershell
.\gradlew.bat :app:assembleRelease
```

Release APK output:

```text
app/build/outputs/apk/release/
```

When signing variables are present, the installable APK is usually:

```text
app/build/outputs/apk/release/app-release.apk
```

When signing variables are missing, Gradle may produce an unsigned artifact such as:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Use that unsigned APK only for inspection.

## Install On Phone

1. Enable installation from trusted local sources on the phone if needed.
2. Connect the phone with USB debugging enabled.
3. Install with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

For a signed release APK:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

If Android reports the APK is unsigned, confirm the four `NIQDAH_KEY*` values are set in `%USERPROFILE%\.gradle\gradle.properties` or in the current PowerShell session, then rebuild.

## Rollback

Keep the last known-good APK in a private folder outside the repo. To roll back:

```powershell
adb install -r path\to\last-known-good.apk
```

If Android refuses to downgrade because of `versionCode`, uninstall first, then install the old APK. Uninstalling can remove local app data, so confirm Firebase sync before doing that.
