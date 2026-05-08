# Niqdah Planned Phases

## Phase 1 Foundation

Create the Android app foundation with Kotlin, Jetpack Compose, Material 3, Firebase Auth, Firestore dependency setup, and a basic authenticated shell. Complete.

## Phase 2 Manual Finance Engine

Add manual transaction entry, categories, fixed expense setup, dashboard calculations, marriage fund progress, debt progress, savings envelopes, editable profile settings, and persistent Firestore data models. Complete for the first manual version.

## Phase 3 AI Chat Backend

Add a backend-backed AI chat experience that can help set budgets, classify expenses, and reason about purchases against the user's plan. The Android app calls Firebase Cloud Functions, and the Cloud Function calls the OpenAI Responses API using a secret-backed `OPENAI_API_KEY`. Complete for the first secure chat version.

## Phase 4 SMS/Import Parser

Add opt-in SMS or statement import parsing after the finance model is stable. No SMS permissions are part of Phase 1.

## Phase 5 Reminders

Add reminders for spending reviews, debt payments, savings contributions, and weekly plan check-ins.

## Phase 6 Polish/Release

Refine UI, accessibility, security rules, analytics, release signing, store metadata, QA, and production readiness.
