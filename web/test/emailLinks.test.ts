import { describe, expect, it } from "vitest";
import { emailLinkToken, finishEmailLink, gravatarUrl, sendEmailLink } from "../src/lib/account";

const BASE = "https://worker.example";

describe("signing in with an email link (#166)", () => {
  it("reads the token of a link", () => {
    expect(emailLinkToken("#token=abc.def")).toBe("abc.def");
    expect(emailLinkToken("#x=1&token=abc")).toBe("abc");
    expect(emailLinkToken("")).toBeNull();
  });

  it("asks for a link, or one that adds the address with the device key", async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const doFetch = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return new Response(null, { status: 204 });
    };
    await sendEmailLink(" me@example.com ", null, doFetch, BASE);
    await sendEmailLink("me@example.com", "k.s", doFetch, BASE);
    expect(calls[0].url).toBe(BASE + "/account/email/start");
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ email: "me@example.com" });
    expect(calls[1].url).toBe(BASE + "/account/email/add");
    expect(new Headers(calls[1].init?.headers).get("Authorization")).toBe("Bearer k.s");
    await expect(sendEmailLink("me@example.com", null, async () => new Response("", { status: 502 }), BASE)).rejects.toMatchObject({ problem: "server" });
    await expect(
      sendEmailLink("me@example.com", null, async () => {
        throw new TypeError("offline");
      }, BASE),
    ).rejects.toMatchObject({ problem: "offline" });
  });

  it("uses a link: a sign-in, an added address, or why not", async () => {
    const answer = (status: number, body: unknown = null) => async () => new Response(body === null ? null : JSON.stringify(body), { status });
    expect(await finishEmailLink("t", answer(200, { key: "k.s", email: "me@example.com" }), BASE)).toEqual({ key: "k.s", email: "me@example.com" });
    expect(await finishEmailLink("t", answer(200, { email: "me@example.com" }), BASE)).toEqual({ key: null, email: "me@example.com" });
    expect(await finishEmailLink("t", answer(410), BASE)).toBe("expired");
    expect(await finishEmailLink("t", answer(409), BASE)).toBe("taken");
    expect(await finishEmailLink("t", answer(500), BASE)).toBe("failed");
    expect(await finishEmailLink("t", answer(200, { key: "k.s" }), BASE)).toBe("failed");
  });

  it("asks Gravatar for the address in one form", async () => {
    const url = await gravatarUrl("  Me@Example.com ");
    expect(url).toBe(await gravatarUrl("me@example.com"));
    expect(url).toMatch(/^https:\/\/gravatar\.com\/avatar\/[0-9a-f]{64}\?s=96&d=404$/);
  });
});
