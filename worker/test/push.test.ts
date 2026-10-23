import { beforeEach, describe, expect, it } from "vitest";
import type { Env } from "../src/index";
import { fromBase64Url, poke, pushTargetOf, resetFcmToken, serviceAccountOf } from "../src/push";

/** A Firebase service account like the JSON key file, with a fresh RSA key. */
async function serviceAccount(): Promise<{ json: string; publicKey: CryptoKey }> {
  const pair = (await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
  const der = new Uint8Array((await crypto.subtle.exportKey("pkcs8", pair.privateKey)) as ArrayBuffer);
  let text = "";
  for (const b of der) text += String.fromCharCode(b);
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(text).replace(/(.{64})/g, "$1\n")}\n-----END PRIVATE KEY-----\n`;
  return { json: JSON.stringify({ type: "service_account", project_id: "content-test", client_email: "push@content-test.iam.gserviceaccount.com", private_key: pem }), publicKey: pair.publicKey };
}

describe("Android notifications through Firebase (#235)", () => {
  beforeEach(() => resetFcmToken());

  it("accepts a Firebase token as a device's push address", () => {
    const token = "dGVzdA:APA91bH" + "x".repeat(140);
    expect(pushTargetOf({ kind: "fcm", token })).toEqual({ kind: "fcm", token });
    expect(pushTargetOf({ kind: "fcm", token: "short" })).toBeNull();
    expect(pushTargetOf({ kind: "fcm", token: "has spaces in it, which no token has" })).toBeNull();
    expect(serviceAccountOf("not json")).toBeNull();
  });

  it("signs in with the service account, then sends a data-only message", async () => {
    const { json, publicKey } = await serviceAccount();
    const env = { ALLOWED_ORIGINS: "", FCM_SERVICE_ACCOUNT: json } as Env;
    const calls: { url: string; init?: RequestInit }[] = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      if (url === "https://oauth2.googleapis.com/token") return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }));
      if (String(init?.body).includes("gone-token")) return new Response('{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 });
      return new Response('{"name":"projects/content-test/messages/1"}');
    };
    const token = "phone:" + "a".repeat(150);
    expect(await poke({ kind: "fcm", token }, env, fetcher, 1_000_000_000_000)).toBe("sent");

    // The sign-in: a JWT for the messaging scope, signed with the service account's key.
    const assertion = new URLSearchParams(String(calls[0].init?.body)).get("assertion")!;
    const [header, payload, signature] = assertion.split(".");
    expect(JSON.parse(new TextDecoder().decode(fromBase64Url(payload)))).toMatchObject({
      iss: "push@content-test.iam.gserviceaccount.com",
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
    });
    expect(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, fromBase64Url(signature), new TextEncoder().encode(`${header}.${payload}`))).toBe(true);

    // The message: only that something was shared.
    expect(calls[1].url).toBe("https://fcm.googleapis.com/v1/projects/content-test/messages:send");
    expect((calls[1].init?.headers as Record<string, string>).Authorization).toBe("Bearer ya29.test");
    expect(JSON.parse(String(calls[1].init?.body))).toEqual({ message: { token, data: { kind: "shared" }, android: { priority: "high", ttl: "86400s" } } });

    // The access token is used again; a token Firebase no longer knows is gone.
    expect(await poke({ kind: "fcm", token: "gone-token" + "b".repeat(20) }, env, fetcher, 1_000_000_001_000)).toBe("gone");
    expect(calls.filter((c) => c.url === "https://oauth2.googleapis.com/token")).toHaveLength(1);
  });

  it("sends nothing without the service account", async () => {
    let called = false;
    const fetcher = async () => {
      called = true;
      return new Response("{}");
    };
    expect(await poke({ kind: "fcm", token: "t".repeat(40) }, { ALLOWED_ORIGINS: "" } as Env, fetcher)).toBe("failed");
    expect(called).toBe(false);
  });
});
