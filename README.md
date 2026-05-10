# Niqdah

Niqdah is a personal Android finance app for disciplined budgeting, debt pressure, savings goals, and everyday spending control, built with Kotlin, Jetpack Compose, Material 3, Firebase Auth, Firestore, and Firebase Cloud Functions.

Phase 7A adds a personalized first-run onboarding flow, neutral finance defaults, a more premium visual system, a splash experience, and an upgraded adaptive icon placeholder. Existing manual expenses, AI Chat, SMS import, internal transfer pairing, balance tracking, reminders, and release prep remain intact.

## Tech Stack

- Native Android app in Kotlin
- Jetpack Compose and Material 3
- Minimum SDK 26
- Package name: `com.musab.niqdah`
- Firebase Authentication for email/password auth
- Firestore persistence for profile, budgets, transactions, goals, debt, and monthly snapshots
- Firebase Cloud Functions HTTPS backend for AI Chat
- OpenAI Responses API from the backend only

## Firebase Setup

Do not commit `google-services.json`. It is ignored by Git.

1. Create a Firebase project.
2. Add an Android app in Firebase with package name `com.musab.niqdah`.
3. Download `google-services.json`.
4. Place it at `app/google-services.json`.
5. In Firebase Console, enable Authentication > Sign-in method > Email/Password.
6. Create a Firestore database.
7. Sync the project in Android Studio.

Without `app/google-services.json`, the project can still open and sync, but the app will show a Firebase setup message instead of the login flow.

Deploy Firestore security rules after selecting the Firebase project:

```powershell
firebase deploy --only firestore:rules
```

## Cloud Functions And AI Setup

The Android app does not contain an OpenAI API key and does not call OpenAI directly. It force-refreshes the signed-in user's Firebase ID token, then calls the HTTPS function `askNiqdahHttp` in `us-central1` with `Authorization: Bearer <Firebase ID token>`. The function verifies the token with Firebase Admin, reads `OPENAI_API_KEY` from Firebase Secret Manager, and then calls the OpenAI Responses API.

Prerequisites:

1. Install Node.js 20 for Firebase Functions deployment.
2. Install and sign in to Firebase CLI:

```powershell
npm install -g firebase-tools
firebase login
```

Install backend dependencies:

```powershell
cd functions
npm install
npm run build
cd ..
```

Set the OpenAI API key as a Firebase secret:

```powershell
firebase functions:secrets:set OPENAI_API_KEY
```

Optional local model override:

```powershell
copy functions\.env.example functions\.env
```

Then edit `functions\.env` if you want a different `OPENAI_MODEL`. Do not commit `.env` files.

Deploy the function:

```powershell
firebase deploy --only functions
```

Android call path:

```text
AI Chat screen
  -> FirebaseAiChatRepository
  -> FirebaseAuth.currentUser.getIdToken(true)
  -> HTTPS POST https://us-central1-niqdah.cloudfunctions.net/askNiqdahHttp
  -> Authorization: Bearer <Firebase ID token>
  -> Firebase Admin verifyIdToken
  -> OpenAI Responses API
```

Payload sent to the backend includes the user message, in-session chat history, and a trimmed finance context: profile income, savings target, debt tracker, current month snapshot, category budgets, savings goals, discipline status, necessary items due, goal countdown, and recent transactions. Bank-like SMS text pasted into AI Chat is withheld locally; AI receives only a parsed financial summary for that draft.

## Firestore Data Shape

Niqdah stores finance data under the authenticated Firebase user UID:

```text
users/{uid}/finance/profile
users/{uid}/finance/userFinancialProfile
users/{uid}/finance/debt
users/{uid}/finance/bankMessageSettings
users/{uid}/finance/reminderSettings
users/{uid}/budgetCategories/{categoryId}
users/{uid}/transactions/{transactionId}
users/{uid}/incomeTransactions/{transactionId}
users/{uid}/pendingBankImports/{importHash}
users/{uid}/bankMessageImportHistory/{importHash}
users/{uid}/accountBalanceSnapshots/{accountKind-importHash}
users/{uid}/savingsGoals/{goalId}
users/{uid}/necessaryItems/{necessaryItemId}
users/{uid}/monthlySnapshots/{yearMonth}
```

The first sign-in now creates only neutral setup documents. If `onboardingCompleted` is false, Niqdah shows the onboarding flow before the main app. Onboarding writes the user's real income, salary day, fixed expenses, debt plan, primary savings goal, category budgets, SMS sender configuration, and reminder preferences under the signed-in UID. Existing users with old data see a migration screen that can keep the current plan without overwriting transactions, balances, goals, or SMS settings.

## Phase 4A Manual Bank Message Import

Transactions now includes an **Import bank message** section where the user manually pastes a bank SMS or bank-app message. Niqdah parses the pasted text locally in the app and shows an editable preview before saving.

The parser can detect sender, source profile, amount, currency, debit/spending, credit/income, savings transfer, available balance, description or merchant text, and date when present. If a date is missing, the preview defaults to the current date.

Settings includes **Bank message sources**:

- Daily-use sender name and enable/disable toggle
- Savings sender name and enable/disable toggle
- Comma-separated debit, credit, and savings-transfer keywords

Daily-use debit messages save as expense transactions. Category inference is rule-based and falls back to **Uncategorized**. Savings transfer messages update the current month imported savings contribution and the user's primary savings goal when one exists. Credit messages save as income transaction records and are included in dashboard monthly income.

Manual import remains available even when SMS permission is denied or automatic import is disabled.

## Phase 4B Automatic Incoming SMS Import

Phase 4B adds experimental incoming SMS import for a personal/dev APK. It requests `RECEIVE_SMS` only so Niqdah can react to new incoming SMS messages. It still does **not** request `READ_SMS`, so it cannot scan historical SMS inbox content.

Automatic SMS import is opt-in from Settings. The app explains the permission as: “Niqdah can read new bank SMS messages from your selected senders to prepare expense and savings drafts.” The user can deny permission and continue using manual paste/import.

Privacy and control rules:

- Only messages from configured daily-use or savings bank senders are processed.
- The receiver runs the local `BankMessageParser`; raw SMS is not logged and is never sent to OpenAI.
- The backend cannot read SMS and does not write transactions.
- Matching SMS messages create pending imports only. User review is required before saving.
- Pending imports can be saved, edited, or dismissed from Transactions.
- Duplicate protection uses a hash of sender, message body, and a rounded received timestamp.

Tapping a bank-import notification opens the Transactions tab, where **Pending bank imports** shows sender, type, amount, currency, suggested category, necessity, date, confidence, and parsed review notes. Automatic pending imports keep `rawMessage` empty in Firestore.

## Phase 4C Balance Tracking And Notification Actions

Phase 4C stores local account balance snapshots when a matching bank SMS contains an available balance phrase such as `Available balance AED 1,234.00`, `Avl Bal AED 1,234.00`, `Balance: AED 1,234.00`, `Current balance AED 1,234.00`, or common available-limit wording. The parser keeps balance amounts separate from transaction amounts, so a balance-only message does not become an expense.

Dashboard and Settings show the latest daily-use balance, latest savings balance, and last balance update time. Balance snapshots are stored under the signed-in user's Firestore data and are keyed from the pending import hash; they do not require inbox scanning.

Foreign currency card purchases can keep the original foreign amount and currency. If the previous balance for the same account and the new AED balance are both known, Niqdah may suggest the AED debit from the balance difference and marks the draft with medium confidence plus the note `AED amount inferred from balance change.` Because other transactions can happen between bank messages, inferred AED amounts always stay in pending review and are never auto-finalized.

Pending import notifications now show parsed financial summaries only, never the full SMS body. Notifications can include Save, Edit, and Dismiss actions:

- Save writes the pending import through the same review save behavior, removes it from pending imports, and posts `Saved to Niqdah.` when notifications are allowed. If no Firebase user is signed in, it opens the app/login and does not save.
- Edit opens the Transactions pending review area without saving.
- Dismiss marks the pending import dismissed and removes it from the pending list.

Android 13+ devices also request `POST_NOTIFICATIONS` when automatic SMS import is enabled so review notifications and confirmation notifications can appear. The app still does not request `READ_SMS`, does not send SMS content to OpenAI, does not let the backend read SMS, and does not auto-save raw SMS without user action.

## Phase 5 Discipline Reminders

Phase 5 adds local, gentle financial discipline reminders and dashboard guidance:

- Monthly savings transfer reminder based on the user's onboarding goal and calculated monthly target.
- Missed savings reminder after a configured day if the current month is short of the target.
- Category budget warnings at 75%, 100%, and over 100% for variable spending categories.
- Avoid-category save warning: "This was marked Avoid. Consider whether it was necessary."
- Necessary recurring items list generated from onboarding fixed costs, debt payment, and savings target, with manual editing in Settings.
- Dashboard discipline card with savings status, categories near limit, due necessary items, avoid spending this month, and safe-to-spend.
- Goal countdown with editable target date and fund amount.

Reminder settings controls live in Settings. WorkManager schedules Android-friendly local checks for monthly savings, missed savings, overspending, and necessary items. Notifications are posted only if `POST_NOTIFICATIONS` is already granted; Phase 5 does not add another notification permission prompt. Reminder wording is practical and non-shaming.

Privacy posture remains the same: no `READ_SMS`, no SMS content sent to OpenAI, no OpenAI API key in the Android app, and no backend SMS access. AI Chat receives discipline summaries such as savings progress, overspent categories, due necessary items, safe-to-spend, and goal countdown status. If bank-like SMS text is pasted into AI Chat, the Android app keeps the raw text local and sends only a parsed summary. AI cannot create reminders or write data; the Android UI must present any saveable action for review.

## Phase 7A Personalized Premium Setup

Phase 7A moves Niqdah away from one hardcoded personal plan. New users now complete a story-style onboarding flow after registration/login. The flow asks about control focus, monthly income, fixed costs, debt, savings goal, categories, bank SMS tracking, and reminders, then shows a plan confirmation before the main app.

The onboarding output becomes the user's Firestore plan. The dashboard then uses the user's primary goal name and target date instead of universal marriage or January wording. Old users are preserved through a migration screen that marks setup complete only after they choose to keep their existing plan.

The visual system now includes shared premium Compose primitives such as `AppScaffold`, `PageHeader`, `PremiumCard`, `MetricCard`, `InsightCard`, `ActionCard`, `SectionHeader`, `EmptyState`, `StatusPill`, `WarningBanner`, `SuccessBanner`, and `ProgressRing`. The app also has a splash theme and an adaptive vector icon placeholder based on a coin-and-shield mark.

## Phase 6 Polish, Security, And Release Prep

Phase 6 adds a first-run setup checklist in Settings, a short in-app Privacy note, friendlier save feedback, central notification channel definitions, Firestore security rules, and personal APK preparation docs.

Firestore rules are in `firestore.rules` and allow each signed-in user to read and write only `users/{uid}` and nested data under their own UID. All other reads and writes are denied by default.

Release and QA references:

- `docs/RELEASE.md`
- `docs/QA_CHECKLIST.md`

## Open And Run In Android Studio

1. Open Android Studio.
2. Select **File > Open**.
3. Choose the repository root: `C:\Users\Administrator\Documents\Niqdah`.
4. Wait for Gradle sync to finish.
5. Add `app/google-services.json` if you have not already.
6. Select the `app` run configuration.
7. Choose an emulator or connected Android device.
8. Click **Run**.

Command-line debug build:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Project Structure

```text
app/src/main/java/com/musab/niqdah/
  core/firebase/       Firebase initialization helpers
  data/auth/           Firebase Auth repository and error mapping
  data/ai/             Firebase callable AI chat repository
  data/finance/        Firestore finance repository and mappers
  data/firestore/      Firestore collection names for future data work
  data/sms/            Incoming SMS receiver for review-first bank import
  domain/ai/           AI chat models and repository contract
  domain/auth/         Auth repository contract and auth state
  domain/finance/      Finance models, parser, defaults, repository contract, dates, calculations
  ui/ai/               AI Chat screen and ViewModel
  ui/auth/             Login and register screens
  ui/finance/          Dashboard, transactions, goals, settings, and shared finance UI
  ui/onboarding/       First-run onboarding and existing-plan migration
  ui/shell/            Authenticated bottom-navigation shell
  ui/theme/            Material 3 light/dark theme
functions/
  src/index.ts         Callable Cloud Function using OpenAI Responses API
docs/
  PHASES.md
  PRODUCT_DIRECTION.md
  QA_CHECKLIST.md
  RELEASE.md
```

## Current Scope

Included:

- Login screen
- Register screen
- Authenticated app shell
- Bottom navigation for Dashboard, Transactions, Goals, AI Chat, and Settings
- Logout from Settings
- Loading state while auth state is checked
- Friendly auth failure messages
- First-run onboarding and existing-plan migration
- Personalized financial profile setup
- Monthly income setup
- Fixed expenses and category budgets
- Manual transaction add, edit, delete, note, month filter, category filter, and necessity level
- Manual bank message import with parser preview and editable fields
- Experimental incoming SMS bank import with pending review drafts
- Account balance snapshots for daily-use and savings bank messages
- Save, Edit, and Dismiss notification actions for pending bank import drafts
- Monthly savings reminders, missed savings reminders, overspending warnings, avoid warnings, necessary item reminders, and goal countdown
- Daily-use and savings bank message source settings
- Rule-based category suggestions for pasted bank messages
- Imported income records from credit messages
- Personalized primary savings goal and optional envelopes
- Debt tracker and debt payment updates
- Dashboard calculations for income, spending, safe-to-spend, savings, debt, overspending alerts, discipline status, goal countdown, and health summary
- Dashboard and Settings latest balance status fields
- Firestore persistence under `users/{uid}`
- AI Chat with in-session history
- Secure Cloud Function backend for OpenAI Responses API
- Finance context sent to backend for disciplined purchase, reminder-aware, and budget guidance
- Firestore rules scoped to `users/{uid}`
- Personal release and QA documentation

Not included yet:

- READ_SMS inbox scanning or notification listeners
- Payment, bank, or card integrations
