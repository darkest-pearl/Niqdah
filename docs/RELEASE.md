# Niqdah Release APK Notes

This is for real-device personal APK testing, not Play Store submission.

## Signing Inputs

Release signing is local-only. The Android build reads these from either the current Windows PowerShell session or local Gradle properties:

```text
NIQDAH_KEYSTORE_FILE
NIQDAH_KEYSTORE_PASSWORD
NIQDAH_KEY_ALIAS
NIQDAH_KEY_PASSWORD
```

Older `NIQDAH_RELEASE_*` names are still accepted for compatibility. Do not commit keystores, passwords, local signing property files, `google-services.json`, or copied APKs.

If all four signing values are present, `:app:assembleRelease` produces an installable signed release APK. If any value is missing, the release build can still produce an unsigned APK for inspection, but Android will not normally install an unsigned release APK.

## Generate A Keystore

Store the keystore outside the repository:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\Documents\Niqdah-private"
keytool -genkeypair -v -keystore "$env:USERPROFILE\Documents\Niqdah-private\niqdah-release.jks" -alias niqdah -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and passwords in a password manager. Losing this keystore means future signed updates cannot install over APKs signed with it.

## Set Signing Variables For This PowerShell Session

```powershell
$env:NIQDAH_KEYSTORE_FILE="$env:USERPROFILE\Documents\Niqdah-private\niqdah-release.jks"
$env:NIQDAH_KEYSTORE_PASSWORD="your-store-password"
$env:NIQDAH_KEY_ALIAS="niqdah"
$env:NIQDAH_KEY_PASSWORD="your-key-password"
```

Alternative local-only storage:

```powershell
notepad "$env:USERPROFILE\.gradle\gradle.properties"
```

Add:

```properties
NIQDAH_KEYSTORE_FILE=C:\\Users\\Administrator\\Documents\\Niqdah-private\\niqdah-release.jks
NIQDAH_KEYSTORE_PASSWORD=your-store-password
NIQDAH_KEY_ALIAS=niqdah
NIQDAH_KEY_PASSWORD=your-key-password
```

## Build APKs

Debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Signed release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Release output:

```text
app\build\outputs\apk\release\app-release.apk
```

If signing variables are missing, inspect:

```text
app\build\outputs\apk\release\app-release-unsigned.apk
```

That unsigned APK is not normally installable on a phone.

## Install On A Device

Enable USB debugging and authorize the computer on the phone, then verify:

```powershell
adb devices
```

Install signed release:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

If Android reports a signature mismatch because the debug APK is already installed:

```powershell
adb uninstall com.musab.niqdah
adb install app\build\outputs\apk\release\app-release.apk
```

Uninstalling removes local app data. Confirm Firebase sync or use a test account before doing it.

## Rollback

Keep known-good APKs in a private folder outside the repo. To install one:

```powershell
adb install -r C:\path\to\last-known-good.apk
```

If Android refuses to downgrade because of `versionCode`, uninstall first:

```powershell
adb uninstall com.musab.niqdah
adb install C:\path\to\last-known-good.apk
```
