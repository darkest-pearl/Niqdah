# Niqdah Manual QA Checklist

Run these against a fresh install and an existing account before sharing a personal APK.

## Auth

- Register a new account.
- Log out and log back in.
- Try a wrong password and confirm the error is friendly.

## Manual Finance

- Add a manual expense.
- Edit the expense amount, category, date, note, and necessity.
- Delete the expense.
- Record a debt payment.
- Update a savings goal amount.

## Bank Message Import

- Paste a normal debit SMS and save it as an expense.
- Paste a savings transfer credit SMS and save it as a savings transfer.
- Paste debit-first internal transfer SMS, then matching credit SMS; confirm one paired transfer and one savings contribution.
- Paste credit-first internal transfer SMS, then matching debit SMS; confirm the same grouped result.
- Paste an informational SMS and confirm it is ignored or blocked with a clear reason.
- Confirm pending automatic imports do not show raw SMS text unless a local/manual draft intentionally has local text.

## AI Chat

- Ask for purchase advice and confirm a concise classification.
- Paste a bank-like SMS into AI Chat and confirm the UI says raw SMS was not sent to AI.
- Save an AI draft action.
- Try saving an invalid AI draft and confirm a friendly error appears.

## Settings And Setup

- Confirm the setup checklist updates for login, sender names, account suffixes, SMS permission, notification permission, monthly savings target, and January target.
- Confirm the Privacy note is visible.
- Save bank sender settings.
- Save reminder settings.
- Add, edit, mark done/skipped, and delete a reminder item.

## Notifications

- Enable automatic SMS import and grant `RECEIVE_SMS`.
- On Android 13+, grant and deny `POST_NOTIFICATIONS` in separate runs.
- Confirm bank import notification text contains only parsed summaries, not raw SMS body.
- Confirm Save, Edit, and Dismiss notification actions behave clearly.
- Confirm internal transfer safety notifications use transfer summaries only.
- Confirm discipline reminders use user-friendly titles and stable behavior.

## Offline Or Poor Connection

- Turn on airplane mode.
- Try saving a transaction and confirm failure is visible.
- Restore connection and save again.
- Try AI Chat offline and confirm the error is friendly.

## Build Checks

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```
