import OpenAI from "openai";
import {logger} from "firebase-functions";
import {defineSecret} from "firebase-functions/params";
import {HttpsError, onCall} from "firebase-functions/v2/https";

const openAiApiKey = defineSecret("OPENAI_API_KEY");
const DEFAULT_MODEL = "gpt-5.2";
const MAX_MESSAGE_LENGTH = 2000;
const MAX_CONTEXT_LENGTH = 10000;
const MAX_HISTORY_ITEMS = 8;

type ChatRole = "user" | "assistant";

type ChatMessage = {
  role: ChatRole;
  content: string;
};

const systemPrompt = `
You are Niqdah, a disciplined personal finance assistant for wedding preparation.

Be direct, practical, concise, and calm. Use the user's provided finance context as the source of truth.
Never encourage risky investments, gambling, borrowing for lifestyle spending, or new debt.

Priorities, in order:
1. Protect the January marriage fund.
2. Avoid new debt and reduce existing debt.
3. Preserve necessary expenses.
4. Keep bride, family, travel, visa, and wedding spending controlled.
5. Allow small realistic lifestyle comfort only when it fits the plan.

When the user proposes a purchase, classify it as exactly one of:
- Necessary
- Optional
- Avoid

For purchase checks, begin with "Classification: Necessary", "Classification: Optional", or "Classification: Avoid".
Then explain briefly why and give one practical next step.
`.trim();

export const askNiqdah = onCall(
  {
    region: "us-central1",
    secrets: [openAiApiKey],
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign in before chatting with Niqdah.");
    }

    const message = readString(request.data?.message, MAX_MESSAGE_LENGTH);
    if (!message) {
      throw new HttpsError("invalid-argument", "Send a message for Niqdah to answer.");
    }

    const financeContext = trimForPrompt(request.data?.financeContext, MAX_CONTEXT_LENGTH);
    const history = readHistory(request.data?.history);
    const model = process.env.OPENAI_MODEL || DEFAULT_MODEL;
    const client = new OpenAI({apiKey: openAiApiKey.value()});

    try {
      const response = await client.responses.create({
        model,
        instructions: systemPrompt,
        input: [
          {
            role: "user",
            content: `Current finance context for Firebase UID ${uid}:\n${financeContext}`,
          },
          ...history,
          {
            role: "user",
            content: message,
          },
        ],
        max_output_tokens: 500,
      });

      const reply = response.output_text?.trim();
      if (!reply) {
        throw new Error("OpenAI response did not include output text.");
      }

      return {
        reply,
        classification: extractClassification(reply),
        model,
      };
    } catch (error) {
      logger.error("askNiqdah failed", {
        uid,
        error: error instanceof Error ? error.message : String(error),
      });
      throw new HttpsError(
        "unavailable",
        "Niqdah AI is unavailable right now. Try again shortly."
      );
    }
  }
);

function readString(value: unknown, maxLength: number): string {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function readHistory(value: unknown): ChatMessage[] {
  if (!Array.isArray(value)) return [];

  return value
    .slice(-MAX_HISTORY_ITEMS)
    .map((item): ChatMessage | null => {
      if (!item || typeof item !== "object") return null;
      const record = item as Record<string, unknown>;
      const role = record.role === "assistant" ? "assistant" : "user";
      const content = readString(record.content, MAX_MESSAGE_LENGTH);
      return content ? {role, content} : null;
    })
    .filter((item): item is ChatMessage => item !== null);
}

function trimForPrompt(value: unknown, maxLength: number): string {
  const serialized = JSON.stringify(value ?? {}, null, 2);
  if (serialized.length <= maxLength) return serialized;
  return `${serialized.slice(0, maxLength)}\n...context trimmed...`;
}

function extractClassification(reply: string): string | null {
  const match = reply.match(/Classification:\s*(Necessary|Optional|Avoid)/i);
  return match?.[1] ?? null;
}
