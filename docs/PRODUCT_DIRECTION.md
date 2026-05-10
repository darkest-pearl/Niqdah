# Niqdah Product Direction

## Identity

Niqdah is a financial-control app for people who want calm, direct help before money pressure becomes chaotic. The product should feel premium, private, and steady: a place to see the plan, the pressure points, and the next right action.

## Premium UI Principles

- Calm hierarchy: the most important number or decision appears first.
- Clean density: screens should be organized and useful without feeling crowded.
- Premium cards: use restrained elevation, 8dp radius, strong spacing, and purposeful color.
- Plain language: avoid shame, hype, and generic finance jargon.
- Personal wording: use the user's goal name, debt state, category choices, and currency.
- Privacy-visible design: SMS and AI boundaries should be easy to find and understand.

## Onboarding Flow

First-run onboarding creates the user's plan before the main app opens:

1. Control focus
2. Monthly income, currency, and salary day
3. Fixed expenses
4. Debt branch and repayment details
5. Primary savings goal and monthly target suggestion
6. Spending categories and budgets
7. Bank SMS sender setup
8. Reminder preferences
9. Plan confirmation

Existing users with old seeded data see a migration screen and can keep their current plan without overwriting history.

## Privacy Commitments

- No `READ_SMS`.
- Incoming SMS import uses `RECEIVE_SMS` only for new messages after permission.
- Only configured senders are processed.
- Automatic pending imports store parsed summaries and keep raw SMS empty in Firestore.
- SMS content is never sent to OpenAI or the backend.
- The Android app does not contain an OpenAI API key.
- AI Chat can suggest saveable actions, but the app UI must present them for review.

## Future Positioning

Niqdah should grow toward a premium personal finance coach for safe-to-spend decisions, goal discipline, debt pressure, and bank-message-assisted tracking. Future work can add stronger goal editing, richer onboarding persistence, statement import, and Play Store-ready branding after a separate privacy and security review.
