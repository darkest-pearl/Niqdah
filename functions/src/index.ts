import OpenAI from "openai";
import {getApps, initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {logger} from "firebase-functions";
import {defineSecret} from "firebase-functions/params";
import {HttpsError, onCall, onRequest} from "firebase-functions/v2/https";

if (getApps().length === 0) {
  initializeApp();
}

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

type NiqdahResponse = {
  reply: string;
  classification: string | null;
  model: string;
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

You cannot write to Firestore, update settings, or log transactions. The Android app performs financial writes only after the user reviews and taps Save.
Never claim that you saved, logged, recorded, updated, added, or changed a transaction, savings transfer, debt payment, category, budget, goal, or setting.
For financial actions, say "I can prepare this for saving." or "Review and save it in the app."
Only say something was saved, logged, or updated if the request explicitly includes an app-provided confirmation that saving succeeded.
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
    logger.info("askNiqdah auth check", {
      hasAuth: Boolean(request.auth),
      uid: uid ?? null,
    });
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign in before chatting with Niqdah.");
    }

    const message = readString(request.data?.message, MAX_MESSAGE_LENGTH);
    if (!message) {
      throw new HttpsError("invalid-argument", "Send a message for Niqdah to answer.");
    }

    const financeContext = trimForPrompt(request.data?.financeContext, MAX_CONTEXT_LENGTH);
    const history = readHistory(request.data?.history);

    try {
      return await generateNiqdahReply(uid, message, financeContext, history);
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

export const askNiqdahHttp = onRequest(
  {
    region: "us-central1",
    secrets: [openAiApiKey],
    timeoutSeconds: 60,
    memory: "256MiB",
    cors: true,
  },
  async (request, response) => {
    setJsonCorsHeaders(response);

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    if (request.method !== "POST") {
      response.status(405).json({
        error: "Use POST to chat with Niqdah.",
      });
      return;
    }

    const authHeader = request.header("authorization") || "";
    logger.info("askNiqdahHttp auth header check", {
      hasAuthHeader: authHeader.toLowerCase().startsWith("bearer "),
    });

    const idToken = extractBearerToken(authHeader);
    if (!idToken) {
      response.status(401).json({
        error: "Missing authentication token. Please log in again before using AI Chat.",
      });
      return;
    }

    let uid: string;
    try {
      const decodedToken = await getAuth().verifyIdToken(idToken);
      uid = decodedToken.uid;
      logger.info("askNiqdahHttp token verified", {
        hasAuthHeader: true,
        uid,
      });
    } catch (error) {
      logger.warn("askNiqdahHttp token verification failed", {
        hasAuthHeader: true,
        error: error instanceof Error ? error.message : String(error),
      });
      response.status(401).json({
        error: "Your login token could not be verified. Please log out and log in again.",
      });
      return;
    }

    const body = readBody(request.body);
    const message = readString(body.message, MAX_MESSAGE_LENGTH);
    if (!message) {
      response.status(400).json({
        error: "Send a message for Niqdah to answer.",
      });
      return;
    }

    const financeContext = trimForPrompt(body.financeContext, MAX_CONTEXT_LENGTH);
    const history = readHistory(body.history);

    try {
      const reply = await generateNiqdahReply(uid, message, financeContext, history);
      response.status(200).json(reply);
    } catch (error) {
      logger.error("askNiqdahHttp failed", {
        uid,
        error: error instanceof Error ? error.message : String(error),
      });
      response.status(503).json({
        error: "Niqdah AI is unavailable right now. Try again shortly.",
      });
    }
  }
);

async function generateNiqdahReply(
  uid: string,
  message: string,
  financeContext: string,
  history: ChatMessage[]
): Promise<NiqdahResponse> {
  const model = process.env.OPENAI_MODEL || DEFAULT_MODEL;
  const client = new OpenAI({apiKey: openAiApiKey.value()});

  const openAiResponse = await client.responses.create({
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

  const rawReply = openAiResponse.output_text?.trim();
  if (!rawReply) {
    throw new Error("OpenAI response did not include output text.");
  }
  const reply = preventUnconfirmedPersistenceClaims(rawReply);

  return {
    reply,
    classification: extractClassification(reply),
    model,
  };
}

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

function preventUnconfirmedPersistenceClaims(reply: string): string {
  const claimPattern =
    /\b(?:i(?:'ve| have)?\s+(?:saved|logged|recorded|updated|added)|(?:saved|logged|recorded|updated|added)\s+(?:the\s+)?(?:transaction|expense|income|transfer|savings|debt|payment|setting|budget|goal)|(?:transaction|expense|income|transfer|savings|debt|payment|setting|budget|goal)\s+(?:has\s+been\s+|was\s+|is\s+)?(?:saved|logged|recorded|updated|added))\b/i;

  if (!claimPattern.test(reply)) return reply;

  const classification = extractClassification(reply);
  const prefix = classification ? `Classification: ${classification}\n` : "";
  return `${prefix}I can prepare this for saving. Review and save it in the app.`;
}

function extractBearerToken(authHeader: string): string {
  const match = authHeader.match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() ?? "";
}

function readBody(value: unknown): Record<string, unknown> {
  if (!value) return {};
  if (typeof value === "string") {
    return parseJsonBody(value);
  }
  if (Buffer.isBuffer(value)) {
    return parseJsonBody(value.toString("utf8"));
  }
  if (typeof value === "object") {
    return value as Record<string, unknown>;
  }
  return {};
}

function parseJsonBody(value: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(value);
    return typeof parsed === "object" && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

function setJsonCorsHeaders(response: {set: (field: string, value: string) => void}): void {
  response.set("Access-Control-Allow-Origin", "*");
  response.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  response.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  response.set("Content-Type", "application/json");
}
