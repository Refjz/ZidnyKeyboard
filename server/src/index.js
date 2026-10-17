// Translation server for ZidnyKeyboard, running on Cloudflare Workers.
//
// POST /translate  {"text": "...", "source": "id", "target": "en"}
// Header X-Integrity-Token: a Play Integrity token whose request hash is
// sha256("<source>|<target>|<text>"). Requests from apps not installed from
// Google Play (pirated or modified copies) are rejected with 403.

export const LANGUAGES = ["id", "en", "fr", "zh", "ja"];
export const MAX_CHARS = 2000;
const MODEL = "@cf/meta/m2m100-1.2b";
const TOKEN_MAX_AGE_MS = 5 * 60 * 1000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/translate") {
      return json({ error: "not_found" }, 404);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "bad_request" }, 400);
    }
    const problem = validate(body);
    if (problem) return json({ error: "bad_request", reason: problem }, 400);
    const { text, source, target } = body;

    if (env.REQUIRE_INTEGRITY !== "false") {
      const hash = await sha256Hex(requestHashInput(source, target, text));
      const verdict = await verifyIntegrity(env, request.headers.get("X-Integrity-Token"), hash);
      if (!verdict.ok) return json({ error: "not_genuine", reason: verdict.reason }, 403);
    }

    try {
      const result = await env.AI.run(MODEL, { text, source_lang: source, target_lang: target });
      return json({ text: result.translated_text });
    } catch (e) {
      return json({ error: "translation_failed" }, 502);
    }
  },
};

export function validate(body) {
  if (!body || typeof body !== "object") return "body must be a JSON object";
  const { text, source, target } = body;
  if (typeof text !== "string" || text.trim() === "") return "text is required";
  if (text.length > MAX_CHARS) return `text is longer than ${MAX_CHARS} characters`;
  if (!LANGUAGES.includes(source)) return "unsupported source language";
  if (!LANGUAGES.includes(target)) return "unsupported target language";
  if (source === target) return "source and target are the same";
  return null;
}

/** What the app hashes into the integrity token, binding the token to this exact request. */
export function requestHashInput(source, target, text) {
  return `${source}|${target}|${text}`;
}

export async function sha256Hex(input) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Asks Google to decode the Play Integrity token and checks that it was made for this
 * request, by our app as installed from Google Play, on a genuine device.
 */
export async function verifyIntegrity(env, token, expectedHash, fetchImpl = fetch, now = Date.now()) {
  if (!token) return { ok: false, reason: "missing token" };
  if (!env.GOOGLE_SERVICE_ACCOUNT) return { ok: false, reason: "server not configured" };

  let payload;
  try {
    const accessToken = await googleAccessToken(env.GOOGLE_SERVICE_ACCOUNT, fetchImpl, now);
    const response = await fetchImpl(
      `https://playintegrity.googleapis.com/v1/${env.PACKAGE_NAME}:decodeIntegrityToken`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
        body: JSON.stringify({ integrity_token: token }),
      },
    );
    if (!response.ok) return { ok: false, reason: `decode failed (${response.status})` };
    payload = (await response.json()).tokenPayloadExternal;
  } catch (e) {
    return { ok: false, reason: "decode failed" };
  }
  return checkVerdict(payload, env.PACKAGE_NAME, expectedHash, now);
}

export function checkVerdict(payload, packageName, expectedHash, now) {
  const details = payload?.requestDetails ?? {};
  if (details.requestPackageName !== packageName) return { ok: false, reason: "wrong package" };
  if (details.requestHash !== expectedHash) return { ok: false, reason: "token not made for this request" };
  if (Math.abs(now - Number(details.timestampMillis)) > TOKEN_MAX_AGE_MS) return { ok: false, reason: "token expired" };
  if (payload?.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") return { ok: false, reason: "app not recognized by Play" };
  if (payload?.accountDetails?.appLicensingVerdict !== "LICENSED") return { ok: false, reason: "app not installed from Play" };
  const device = payload?.deviceIntegrity?.deviceRecognitionVerdict ?? [];
  if (!device.includes("MEETS_DEVICE_INTEGRITY")) return { ok: false, reason: "device integrity not met" };
  return { ok: true };
}

let cachedAccessToken = null;

/** An OAuth access token for the Play Integrity API, from the service account JSON key. */
async function googleAccessToken(serviceAccountJson, fetchImpl, now) {
  if (cachedAccessToken && cachedAccessToken.expiresAt - 60_000 > now) return cachedAccessToken.value;
  const account = JSON.parse(serviceAccountJson);
  const issuedAt = Math.floor(now / 1000);
  const claims = {
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/playintegrity",
    aud: "https://oauth2.googleapis.com/token",
    iat: issuedAt,
    exp: issuedAt + 3600,
  };
  const assertion = await signJwt(claims, account.private_key);
  const response = await fetchImpl("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new Error(`token request failed (${response.status})`);
  const data = await response.json();
  cachedAccessToken = { value: data.access_token, expiresAt: now + data.expires_in * 1000 };
  return data.access_token;
}

async function signJwt(claims, privateKeyPem) {
  const encode = (obj) => base64Url(new TextEncoder().encode(JSON.stringify(obj)));
  const unsigned = `${encode({ alg: "RS256", typ: "JWT" })}.${encode(claims)}`;
  const der = Uint8Array.from(
    atob(privateKeyPem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "")),
    (c) => c.charCodeAt(0),
  );
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

function base64Url(bytes) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
