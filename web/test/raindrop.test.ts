import { describe, expect, it } from "vitest";
import { appLinks } from "../src/lib/raindrop";

describe("the Raindrop callback page", () => {
  it("hands the code to the app and to the test app", () => {
    const links = appLinks("reader.example", "?code=abc&state=def")!;
    expect(links.app).toBe("intent://reader.example/raindrop/callback?code=abc&state=def#Intent;scheme=https;package=be.wimvandonck.reader;end");
    expect(links.dev).toContain("package=be.wimvandonck.reader.debug;end");
  });

  it("passes a refusal on too, and nothing without either", () => {
    expect(appLinks("reader.example", "?error=access_denied")).not.toBeNull();
    expect(appLinks("reader.example", "")).toBeNull();
  });
});
