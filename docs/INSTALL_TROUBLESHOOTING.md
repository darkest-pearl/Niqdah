# Niqdah Install Troubleshooting

## APK Is Unsigned

Symptom: Android says `App not installed`, or `adb install` reports that the APK is not signed.

Fix: configure all four signing variables and rebuild:

```powershell
$env:NIQDAH_KEYSTORE_FILE="$env:USERPROFILE\Documents\Niqdah-private\niqdah-release.jks"
$env:NIQDAH_KEYSTORE_PASSWORD="your-store-password"
$env:NIQDAH_KEY_ALIAS="niqdah"
$env:NIQDAH_KEY_PASSWORD="your-key-password"
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

Unsigned release artifacts are only for inspection.

## Debug App Already Installed

Symptom: `App not installed` or `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

Cause: the phone has `com.musab.niqdah` installed with a different debug signing certificate.

Fix:

```powershell
adb uninstall com.musab.niqdah
adb install app\build\outputs\apk\release\app-release.apk
```

## USB Debugging Or Authorization

Run:

```powershell
adb devices
```

If the device is missing, enable Developer options and USB debugging, reconnect the cable, and accept the RSA authorization prompt on the phone. If it shows `unauthorized`, revoke USB debugging authorizations on the phone and reconnect.

## Unknown App Install Permission

For manual file installs, Android may require permission for the app that opens the APK. Prefer `adb install` during QA. If installing from Files, Drive, WhatsApp, or a browser, allow that app to install unknown apps in Android settings.

## VersionCode Downgrade

Symptom: `INSTALL_FAILED_VERSION_DOWNGRADE`.

Fix: install a newer APK, or uninstall first:

```powershell
adb uninstall com.musab.niqdah
adb install app\build\outputs\apk\release\app-release.apk
```

## Firebase Project Mismatch

If `app/google-services.json` belongs to a different Firebase project, auth and Firestore data can point at the wrong backend. Confirm the Firebase Android app package is `com.musab.niqdah` and the file is from the intended project. Do not commit `google-services.json`.

## Release App Cannot Connect To Firebase

If release login or Firestore access fails while debug works, check the Firebase Android app configuration for the release signing certificate SHA. Add the SHA-1/SHA-256 for the release keystore in Firebase if required by the enabled Firebase products, download a fresh `google-services.json`, place it at `app/google-services.json`, and rebuild.

## SMS And Notification Permission Reset

Niqdah does not request `READ_SMS`. It only uses `RECEIVE_SMS` for new incoming SMS after permission is granted.

If SMS import stops after reinstalling, updating, or changing Android permission state:

```text
Settings > Apps > Niqdah > Permissions > SMS
```

Turn SMS permission off and on again, then open Niqdah and confirm Settings > Accounts & Bank SMS shows:

```text
SMS permission: granted
Automatic SMS import: enabled
```

On Android 13+, also confirm notification permission is granted so review alerts can appear.
