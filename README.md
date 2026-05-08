# Niqdah

Niqdah is a personal Android finance app for disciplined budgeting and wedding preparation, built with Kotlin, Jetpack Compose, Material 3, Firebase Auth, Firestore, and Firebase Cloud Functions.

Phase 3 includes email/password auth, a manual finance engine, dashboard calculations, transaction tracking, savings envelopes, debt tracking, editable settings, persistent Firestore data under each authenticated user, and a secure AI Chat backend.

## Tech Stack

- Native Android app in Kotlin
- Jetpack Compose and Material 3
- Minimum SDK 26
- Package name: `com.musab.niqdah`
- Firebase Authentication for email/password auth
- Firestore persistence for profile, budgets, transactions, goals, debt, and monthly snapshots
- Firebase Cloud Functions callable backend for AI Chat
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

## Cloud Functions And AI Setup

The Android app does not contain an OpenAI API key and does not call OpenAI directly. It calls the Firebase callable function `askNiqdah` in `us-central1`. The function reads `OPENAI_API_KEY` from Firebase Secret Manager and then calls the OpenAI Responses API.

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
  -> FirebaseFunctions.getInstance("us-central1")
  -> getHttpsCallable("askNiqdah")
  -> Firebase Cloud Function
  -> OpenAI Responses API
```

Payload sent to the backend includes the user message, in-session chat history, and a trimmed finance context: profile income, savings target, debt tracker, current month snapshot, category budgets, savings goals, and recent transactions.

## Firestore Data Shape

Niqdah stores finance data under the authenticated Firebase user UID:

```text
users/{uid}/finance/profile
users/{uid}/finance/debt
users/{uid}/budgetCategories/{categoryId}
users/{uid}/transactions/{transactionId}
users/{uid}/savingsGoals/{goalId}
users/{uid}/monthlySnapshots/{yearMonth}
```

The first sign-in seeds the default salary, extra income, rent, food/transport budget, marriage savings target, debt tracker, and wedding-preparation envelopes.

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
  domain/ai/           AI chat models and repository contract
  domain/auth/         Auth repository contract and auth state
  domain/finance/      Finance models, defaults, repository contract, dates, calculations
  ui/ai/               AI Chat screen and ViewModel
  ui/auth/             Login and register screens
  ui/finance/          Dashboard, transactions, goals, settings, and shared finance UI
  ui/shell/            Authenticated bottom-navigation shell
  ui/theme/            Material 3 light/dark theme
functions/
  src/index.ts         Callable Cloud Function using OpenAI Responses API
docs/
  PHASES.md
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
- Financial profile setup
- Monthly income setup
- Fixed expenses and category budgets
- Manual transaction add, edit, delete, note, month filter, category filter, and necessity level
- Marriage fund and wedding-preparation envelopes
- Debt tracker and debt payment updates
- Dashboard calculations for income, spending, safe-to-spend, savings, debt, overspending alerts, and health summary
- Firestore persistence under `users/{uid}`
- AI Chat with in-session history
- Secure Cloud Function backend for OpenAI Responses API
- Finance context sent to backend for disciplined purchase and budget guidance

Not included yet:

- SMS permissions or parsing
- Payment, bank, or card integrations
- Notification listeners
