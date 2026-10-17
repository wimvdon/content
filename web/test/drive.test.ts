import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { GoogleAuth, SyncError } from "../src/lib/drive";

const KEY = "rss-reader-drive-token";

/** How many times Google's sign-in script (and with it the popup) was asked for. */
const googleScripts = () => document.head.querySelectorAll('script[src*="accounts.google.com"]').length;

describe("Google sign-in", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => document.head.querySelectorAll("script").forEach((s) => s.remove()));

  it("uses the token kept from an earlier visit without showing Google's popup", async () => {
    localStorage.setItem(KEY, JSON.stringify({ token: "abc", expires: Date.now() + 30 * 60_000 }));
    await expect(new GoogleAuth("client").token()).resolves.toBe("abc");
    expect(googleScripts()).toBe(0);
  });

  it("asks for a click instead of opening Google's popup when the token has expired", async () => {
    localStorage.setItem(KEY, JSON.stringify({ token: "old", expires: Date.now() - 1000 }));
    const error = await new GoogleAuth("client").token().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(SyncError);
    expect((error as SyncError).problem).toBe("needsApproval");
    expect(googleScripts()).toBe(0);
  });

  it("asks for a click when there is no token at all", async () => {
    await expect(new GoogleAuth("client").token()).rejects.toThrow("needsApproval");
    expect(googleScripts()).toBe(0);
  });

  it("forgets the token on disconnect", () => {
    localStorage.setItem(KEY, JSON.stringify({ token: "abc", expires: Date.now() + 30 * 60_000 }));
    new GoogleAuth("client").forget();
    expect(localStorage.getItem(KEY)).toBeNull();
  });
});
