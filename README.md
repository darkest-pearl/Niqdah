# Niqdah

Niqdah is a personal Android finance app for disciplined budgeting and wedding preparation, built with Kotlin, Jetpack Compose, Material 3, Firebase Auth, and Firestore.

Phase 2 includes email/password auth, a manual finance engine, dashboard calculations, transaction tracking, savings envelopes, debt tracking, editable settings, and persistent Firestore data under each authenticated user.

## Tech Stack

- Native Android app in Kotlin
- Jetpack Compose and Material 3
- Minimum SDK 26
- Package name: `com.musab.niqdah`
- Firebase Authentication for email/password auth
- Firestore persistence for profile, budgets, transactions, goals, debt, and monthly snapshots

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
  data/finance/        Firestore finance repository and mappers
  data/firestore/      Firestore collection names for future data work
  domain/auth/         Auth repository contract and auth state
  domain/finance/      Finance models, defaults, repository contract, dates, calculations
  ui/auth/             Login and register screens
  ui/finance/          Dashboard, transactions, goals, settings, and shared finance UI
  ui/shell/            Authenticated bottom-navigation shell
  ui/theme/            Material 3 light/dark theme
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

Not included yet:

- OpenAI API integration
- SMS permissions or parsing
- Payment, bank, or card integrations
- Notification listeners
