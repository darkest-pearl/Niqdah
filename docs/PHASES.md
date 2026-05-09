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

## Phase 4C Broader Import Options

Consider notification-listener import, statement import, or inbox scan support only after explicit user opt-in and a separate privacy review.

## Phase 5 Reminders

Add reminders for spending reviews, debt payments, savings contributions, and weekly plan check-ins.

## Phase 6 Polish/Release

Refine UI, accessibility, security rules, analytics, release signing, store metadata, QA, and production readiness.
