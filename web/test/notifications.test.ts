import { describe, expect, it } from "vitest";
import { applicationServerKey, serviceWorkerUrl } from "../src/lib/notifications";

describe("notifications (#235)", () => {
  it("gives the service worker its text in the address", () => {
    const url = new URL(serviceWorkerUrl(), "https://app.example");
    expect(url.pathname).toBe("/sw.js");
    expect(url.searchParams.get("title")).toBe("Content");
    expect(url.searchParams.get("body")).toBe("Something was shared with you.");
  });

  it("turns the VAPID public key into the bytes the browser wants", () => {
    const key = "BNxDkb73QhMVGb40MyiJtum9jpTaOHqeEmpxRWuVRvHiF-0dKAJarvVQxJoM6AUVeopOE4rOKo859OK-wcTK8Sk";
    const bytes = applicationServerKey(key);
    expect(bytes).toHaveLength(65);
    expect(bytes[0]).toBe(4);
  });
});
