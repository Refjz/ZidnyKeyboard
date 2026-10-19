import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import worker, { checkVerdict, linePrompt, parseReplies, polish, polishPrompt, requestHashInput, sha256Hex, translate, translationPrompt, validate, verifyIntegrity } from "../src/index.js";

const PACKAGE = "com.zidny.keyboard";
const NOW = 1_800_000_000_000;

function genuinePayload(hash) {
  return {
    requestDetails: { requestPackageName: PACKAGE, requestHash: hash, timestampMillis: String(NOW - 1000) },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PACKAGE },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
    accountDetails: { appLicensingVerdict: "LICENSED" },
  };
}

test("validate accepts a normal request and rejects bad ones", () => {
  assert.equal(validate({ text: "halo", source: "id", target: "en" }), null);
  assert.ok(validate({ text: "", source: "id", target: "en" }));
  assert.ok(validate({ text: "halo", source: "id", target: "de" }));
  assert.ok(validate({ text: "halo", source: "id", target: "id" }));
  assert.ok(validate({ text: "x".repeat(2001), source: "id", target: "en" }));
});

test("validate checks style, mode and explain", () => {
  assert.equal(validate({ text: "halo", source: "id", target: "en", style: "formal", mode: "translate", explain: true }), null);
  assert.equal(validate({ text: "i has a cat", source: "en", target: "en", mode: "polish" }), null);
  assert.ok(validate({ text: "halo", source: "id", target: "en", style: "rude" }));
  assert.ok(validate({ text: "halo", source: "id", target: "en", mode: "summarize" }));
  assert.ok(validate({ text: "halo", source: "id", target: "en", mode: "polish" }));
  assert.ok(validate({ text: "halo", source: "id", target: "en", explain: "yes" }));
});

test("requestHashInput keeps the old format for old apps and binds every option for new ones", () => {
  assert.equal(requestHashInput({ text: "a|b", source: "id", target: "en" }), "id|en|a|b");
  assert.equal(
    requestHashInput({ text: "hi", source: "en", target: "en", style: "polite", mode: "polish", explain: true }),
    "v2|en|en|polite|polish|true|hi",
  );
});

test("sha256Hex matches the known digest", async () => {
  assert.equal(await sha256Hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
});

test("checkVerdict accepts a genuine Play install", () => {
  assert.deepEqual(checkVerdict(genuinePayload("h"), PACKAGE, "h", NOW), { ok: true });
});

test("checkVerdict rejects sideloaded, modified, replayed and rooted cases", () => {
  const cases = [
    ["sideloaded", (p) => (p.accountDetails.appLicensingVerdict = "UNLICENSED")],
    ["modified", (p) => (p.appIntegrity.appRecognitionVerdict = "UNRECOGNIZED_VERSION")],
    ["other request", (p) => (p.requestDetails.requestHash = "other")],
    ["old token", (p) => (p.requestDetails.timestampMillis = String(NOW - 10 * 60 * 1000))],
    ["rooted", (p) => (p.deviceIntegrity.deviceRecognitionVerdict = ["MEETS_BASIC_INTEGRITY"])],
  ];
  for (const [name, change] of cases) {
    const payload = genuinePayload("h");
    change(payload);
    assert.equal(checkVerdict(payload, PACKAGE, "h", NOW).ok, false, name);
  }
});

test("verifyIntegrity signs a Google token request and decodes the integrity token", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const env = {
    PACKAGE_NAME: PACKAGE,
    GOOGLE_SERVICE_ACCOUNT: JSON.stringify({
      client_email: "bot@example.iam.gserviceaccount.com",
      private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
    }),
  };
  const calls = [];
  const fakeFetch = async (url, init) => {
    calls.push(url);
    if (url.startsWith("https://oauth2.googleapis.com/")) {
      assert.match(String(init.body), /assertion=[\w-]+\.[\w-]+\.[\w-]+/);
      return Response.json({ access_token: "access", expires_in: 3600 });
    }
    assert.equal(init.headers.Authorization, "Bearer access");
    assert.equal(JSON.parse(init.body).integrity_token, "tok");
    return Response.json({ tokenPayloadExternal: genuinePayload("h") });
  };
  assert.deepEqual(await verifyIntegrity(env, "tok", "h", fakeFetch, NOW), { ok: true });
  assert.equal(calls.length, 2);
  assert.equal((await verifyIntegrity(env, null, "h", fakeFetch, NOW)).ok, false);
});

test("worker rejects requests without a genuine token and translates otherwise", async () => {
  const body = { text: "kamu di mana", source: "id", target: "en" };
  const request = (headers = {}) =>
    new Request("https://x/translate", { method: "POST", body: JSON.stringify(body), headers });
  const ai = { run: async (model, input) => ({ translated_text: `[${input.target_lang}] ${input.text}` }) };

  const strict = await worker.fetch(request(), { AI: ai, PACKAGE_NAME: PACKAGE, REQUIRE_INTEGRITY: "true" });
  assert.equal(strict.status, 403);

  const open = await worker.fetch(request(), { AI: ai, PACKAGE_NAME: PACKAGE, REQUIRE_INTEGRITY: "false" });
  assert.equal(open.status, 200);
  assert.deepEqual(await open.json(), { text: "[en] kamu di mana" });
});

test("worker adds the Indonesian meaning when asked, and polishes in the same language", async () => {
  const seen = [];
  const ai = {
    run: async (model, input) => {
      seen.push(input.messages[0].content);
      return { response: input.messages[0].content.includes("to Indonesian") ? "arti" : "result" };
    },
  };
  const env = { AI: ai, PACKAGE_NAME: PACKAGE, REQUIRE_INTEGRITY: "false" };
  const post = (body) => worker.fetch(new Request("https://x/translate", { method: "POST", body: JSON.stringify(body) }), env);

  const explained = await post({ text: "halo", source: "id", target: "en", style: "formal", mode: "translate", explain: true });
  assert.deepEqual(await explained.json(), { text: "result", meaning: "arti" });
  assert.match(seen[0], /formal, professional/);

  const polished = await post({ text: "i has cat", source: "en", target: "en", style: "casual", mode: "polish", explain: false });
  assert.deepEqual(await polished.json(), { text: "result" });
  assert.match(seen.at(-1), /You fix chat messages written in English/);

  // Into Indonesian there is nothing to explain.
  seen.length = 0;
  const intoIndonesian = await post({ text: "hello", source: "en", target: "id", style: "casual", mode: "translate", explain: true });
  assert.deepEqual(await intoIndonesian.json(), { text: "arti" });
  assert.equal(seen.length, 1);
});

test("translate uses the chat model and falls back to m2m100 when it fails", async () => {
  const chat = { run: async (model, input) => (input.messages ? { response: " just try it bro \n" } : { translated_text: "x" }) };
  assert.equal(await translate(chat, "cobain aja bro", "id", "en"), "just try it bro");

  const broken = {
    run: async (model, input) => {
      if (input.messages) throw new Error("model down");
      return { translated_text: `[${input.target_lang}] ${input.text}` };
    },
  };
  assert.equal(await translate(broken, "halo", "id", "en"), "[en] halo");
  assert.match(translationPrompt("id", "ja"), /from Indonesian to Japanese/);
  assert.match(translationPrompt("id", "en"), /Javanese, Sundanese/);
  assert.match(translationPrompt("en", "id", "polite"), /politely/);
});

test("polish has no fallback model", async () => {
  const broken = { run: async () => ({ response: "" }) };
  await assert.rejects(polish(broken, "i has cat", "en"));
  assert.match(polishPrompt("ja", "formal"), /Japanese/);
});

test("validate accepts reply mode in any language pair", () => {
  assert.equal(validate({ text: "are you free tonight?", source: "en", target: "en", mode: "reply" }), null);
  assert.equal(validate({ text: "are you free tonight?", source: "en", target: "id", mode: "reply", style: "polite" }), null);
  assert.ok(validate({ text: "hi", source: "en", target: "en", mode: "reply", explain: true }));
});

test("parseReplies strips numbering and quotes and keeps meanings only when asked", () => {
  const raw = '1. "Sure, see you at 7!" ||| Oke, sampai jumpa jam 7!\n2) What time works for you? ||| Jam berapa?\n\n- Sorry, I can\'t tonight.|||Maaf, malam ini nggak bisa.\n4. extra';
  assert.deepEqual(parseReplies(raw, true), [
    { text: "Sure, see you at 7!", meaning: "Oke, sampai jumpa jam 7!" },
    { text: "What time works for you?", meaning: "Jam berapa?" },
    { text: "Sorry, I can't tonight.", meaning: "Maaf, malam ini nggak bisa." },
  ]);
  assert.deepEqual(parseReplies("Boleh\nNanti dulu ya ||| x", false), [{ text: "Boleh" }, { text: "Nanti dulu ya" }]);
});

test("worker returns reply suggestions in reply mode", async () => {
  const seen = [];
  const ai = {
    run: async (model, input) => {
      seen.push(input);
      return { response: "Sure! ||| Boleh!\nWhen? ||| Kapan?\nMaybe later ||| Nanti aja" };
    },
  };
  const env = { AI: ai, PACKAGE_NAME: PACKAGE, REQUIRE_INTEGRITY: "false" };
  const body = { text: "wanna grab dinner?", source: "en", target: "en", style: "casual", mode: "reply", explain: false };
  const response = await worker.fetch(new Request("https://x/translate", { method: "POST", body: JSON.stringify(body) }), env);
  assert.deepEqual(await response.json(), {
    replies: [
      { text: "Sure!", meaning: "Boleh!" },
      { text: "When?", meaning: "Kapan?" },
      { text: "Maybe later", meaning: "Nanti aja" },
    ],
  });
  assert.match(seen[0].messages[0].content, /received this chat message in English/);
  assert.equal(requestHashInput(body), "v2|en|en|casual|reply|false|wanna grab dinner?");
});

test("validate accepts a line request only with a known mood", () => {
  assert.equal(validate({ text: "romantic\nbuat pacar", source: "id", target: "id", mode: "line" }), null);
  assert.equal(validate({ text: "roast\n", source: "en", target: "en", mode: "line" }), null);
  assert.ok(validate({ text: "constructor\nx", source: "id", target: "id", mode: "line" }));
  assert.ok(validate({ text: "evil\nsomeone", source: "id", target: "id", mode: "line" }));
  assert.ok(validate({ text: "funny", source: "id", target: "id", mode: "line", explain: true }));
});

test("linePrompt asks for meanings unless the lines are Indonesian", () => {
  assert.match(linePrompt("funny", "ja"), /Japanese/);
  assert.match(linePrompt("funny", "ja"), /\|\|\|/);
  assert.doesNotMatch(linePrompt("birthday", "id"), /\|\|\|/);
});

test("line mode returns lines from the chat model", async () => {
  const env = {
    REQUIRE_INTEGRITY: "false",
    AI: { run: async () => ({ response: "Kamu cantik ||| You are pretty\nHalo ||| Hello\nHai ||| Hi" }) },
  };
  const request = new Request("https://x/translate", {
    method: "POST",
    body: JSON.stringify({ text: "romantic\n", source: "en", target: "en", mode: "line" }),
  });
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.replies.length, 3);
  assert.equal(body.replies[0].meaning, "You are pretty");
});
