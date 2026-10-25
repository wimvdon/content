import { describe, expect, it } from "vitest";
import { emptyChoice } from "../src/lib/saveChoice";
import { actionOf, canConfirm, isEmail, outcomeOf, people, toggle } from "../src/lib/sendTo";

describe("Save and share: sending with Content (#256), as on Android", () => {
  it("knows what looks like an email address", () => {
    expect(isEmail("an@example.com")).toBe(true);
    expect(isEmail("  an@example.com ")).toBe(true);
    expect(isEmail("an@example")).toBe(false);
    expect(isEmail("an example@x.com")).toBe(false);
  });

  it("offers those shared with before, then the ones added here, each once", () => {
    expect(people(["an@example.com", "bart@example.com"], ["Bart@Example.com", "new@example.com"])).toEqual(["an@example.com", "bart@example.com", "new@example.com"]);
  });

  it("picks someone and unpicks them again, case ignored", () => {
    const picked = toggle([], "an@example.com");
    expect(picked).toEqual(["an@example.com"]);
    expect(toggle(picked, "bart@example.com")).toEqual(["an@example.com", "bart@example.com"]);
    expect(toggle(picked, "AN@example.com")).toEqual([]);
  });

  it("saves, saves and sends, or only sends", () => {
    expect(actionOf(emptyChoice)).toBe("save");
    expect(actionOf({ ...emptyChoice, sendTo: ["an@example.com"] })).toBe("saveAndSend");
    expect(actionOf({ ...emptyChoice, keep: false, sendTo: ["an@example.com"] })).toBe("send");
  });

  it("needs someone to send to for Don't save, and a link for a new save", () => {
    expect(canConfirm(emptyChoice, true)).toBe(true);
    expect(canConfirm(emptyChoice, false)).toBe(false);
    expect(canConfirm({ ...emptyChoice, keep: false }, true)).toBe(false);
    expect(canConfirm({ ...emptyChoice, keep: false, sendTo: ["an@example.com"] }, true)).toBe(true);
  });

  it("says in one message who it reached, or the first one it didn't", () => {
    expect(outcomeOf([])).toBeNull();
    expect(
      outcomeOf([
        ["an@example.com", "sent"],
        ["bart@example.com", "sent"],
      ]),
    ).toEqual({ sent: ["an@example.com", "bart@example.com"] });
    expect(
      outcomeOf([
        ["an@example.com", "sent"],
        ["bart@example.com", "noAccount"],
        ["cas@example.com", "offline"],
      ]),
    ).toEqual({ failed: "bart@example.com", status: "noAccount" });
  });
});
