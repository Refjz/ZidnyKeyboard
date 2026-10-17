import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import worker, { checkVerdict, requestHashInput, sha256Hex, validate, verifyIntegrity } from "../src/index.js";

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
  assert.equal(requestHashInput("id", "en", "a|b"), "id|en|a|b");
});
