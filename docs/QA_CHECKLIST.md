# Niqdah Manual QA Checklist

Run these against a fresh install and an existing account before sharing a personal APK.

## Auth

- Register a new account.
- Confirm a new account opens the onboarding flow before the main app.
- Complete onboarding with custom income, debt, goal, categories, SMS senders, and reminders.
- Confirm the dashboard uses the custom goal name and does not show universal marriage/January defaults.
- Log into an existing account with pre-existing data and confirm the migration screen offers to keep the plan.
- Log out and log back in.
- Try a wrong password and confirm the error is friendly.

## Manual Finance

- Add a manual expense.
- Add a manual expense for AED 17.25 and confirm every list/dashboard display shows two decimals.
- Edit the expense amount, category, date, note, and necessity.
- Delete the expense.
- Record salary/deposit with AED 3,500.00 to the daily-use account and no balance; confirm the deposit is saved and the balance is estimated/unconfirmed.
- Record salary/deposit with a current balance after deposit; confirm the account balance shows Confirmed.
- Record a savings deposit/transfer and confirm it updates savings progress without counting as normal spending.
- Record a debt payment.
- Update a savings goal amount.

## Bank Message Import

- Paste a normal debit SMS and save it as an expense.
- Paste `AED 3500.00 has been deposited to your account no. XXXXXXXX4052...` and confirm it becomes income/deposit, not an expense.
- Paste a salary/payroll deposit and confirm it is marked as salary.
- Paste a refund credit and confirm it is not marked as salary.
- Paste a savings transfer credit SMS and save it as a savings transfer.
- Confirm a deposit/credit SMS without available balance creates an estimated or needs-review balance event, not a confirmed balance.
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

- Confirm the setup checklist updates for login, sender names, account suffixes, SMS permission, notification permission, monthly savings target, and goal target.
- Confirm Settings opens as menu cards: Profile & Setup, Accounts & Bank SMS, Categories & Budgets, Reminders & Discipline, Privacy & Security, and App & Release.
- Open each Settings detail page and confirm the Back button returns to the main Settings menu.
- Confirm the Privacy & Security page states no `READ_SMS`, raw automatic SMS storage stays empty, and raw SMS is not sent to AI.
- Save bank sender settings.
- Save reminder settings.
- Add, edit, mark done/skipped, and delete a reminder item.

## Visual System

- Confirm splash shows quickly and enters the app without a blank frame.
- Confirm the adaptive icon reads as a simple financial guard/coin mark, not a letter-only placeholder.
- Scan Dashboard, Transactions, Goals, AI Chat, and Settings for consistent spacing, rounded cards, readable section headers, and non-overlapping text in light and dark theme.
- Confirm AI Chat suggested prompts are visible and send the expected prompt text.

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
