# Niqdah Planned Phases

## Phase 1 Foundation

Create the Android app foundation with Kotlin, Jetpack Compose, Material 3, Firebase Auth, Firestore dependency setup, and a basic authenticated shell. Complete.

## Phase 2 Manual Finance Engine

Add manual transaction entry, categories, fixed expense setup, dashboard calculations, marriage fund progress, debt progress, savings envelopes, editable profile settings, and persistent Firestore data models. Complete for the first manual version.

## Phase 3 AI Chat Backend

Add a backend-backed AI chat experience that can help set budgets, classify expenses, and reason about purchases against the user's plan. The Android app calls Firebase Cloud Functions, and the Cloud Function calls the OpenAI Responses API using a secret-backed `OPENAI_API_KEY`. Complete for the first secure chat version.

## Phase 4A Manual Import Parser

Add manual copy-paste bank message parsing with source settings, editable preview, expense/income records, and savings transfer contribution updates. Complete for manual import without SMS permissions.

## Phase 4B Automatic Import

Add opt-in incoming SMS import after the manual parser proves stable. Phase 4B uses `RECEIVE_SMS` only for new incoming bank messages from configured senders, keeps `READ_SMS` out of the app, creates pending review drafts, and requires user review before saving. Complete for personal/dev APK experimentation.

## Phase 4C Balance Tracking And Rich Pending Imports

Add local account balance snapshots for daily-use and savings bank messages, richer pending-import notifications, and Save/Edit/Dismiss notification actions. Complete for review-first SMS imports with `RECEIVE_SMS` only and Android 13+ `POST_NOTIFICATIONS` runtime prompting.

Privacy constraints remain unchanged: no `READ_SMS`, no raw SMS in notifications, no SMS content sent to OpenAI, no backend SMS access, and no automatic save of raw SMS without user action. Foreign-currency AED debit inference is allowed only as a medium-confidence pending draft with an explicit review note.

## Phase 4D Broader Import Options

Consider notification-listener import, statement import, or inbox scan support only after explicit user opt-in and a separate privacy review.

## Phase 5 Reminders

Add personal financial discipline reminders and warning systems. Complete for the first local-notification version.

Included:

- Monthly marriage savings transfer reminder with editable day, time, and target amount.
- Missed savings reminder that gently reports the current shortfall after a configured check day.
- Overspending warnings for variable categories at 75%, 100%, and over 100% of budget.
- Avoid-category warning after save without shame wording.
- Necessary items list with optional amount, due day/date, monthly or one-time recurrence, pending/done/skipped status, and notification toggle.
- Dashboard discipline card and January countdown card with editable target date and total fund target.
- AI Chat context includes savings progress, overspent categories, necessary items due, safe-to-spend, and January countdown status.

Notification behavior uses WorkManager local checks and posts only when `POST_NOTIFICATIONS` is already granted. Privacy constraints remain unchanged: no `READ_SMS`, no SMS content sent to OpenAI, no backend SMS access, no OpenAI API key in the Android app, and no reminder creation by AI without explicit app UI support.

## Phase 6 Polish/Release

Refine UI consistency, save feedback, privacy notes, setup checklist, notification channels, Firebase security rules, personal APK release docs, and QA coverage. Complete for personal/dev APK preparation, not Play Store submission.

Privacy constraints remain unchanged and are tightened in AI Chat: no `READ_SMS`, no raw automatic SMS body persisted to Firestore, no SMS content sent to OpenAI/backend, no OpenAI API key in the Android app, and no backend SMS access. Bank-like SMS text pasted into AI Chat is kept local and replaced with a parsed summary before backend calls.
