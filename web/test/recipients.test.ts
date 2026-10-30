import { describe, expect, it } from "vitest";
import { defaultSettings } from "../src/lib/app";
import { decodeRecipients, encodeRecipients, forgetRecipient, MAX_RECIPIENTS, rememberRecipient } from "../src/lib/recipients";
import { applySynced, RECIPIENTS, syncedSettings } from "../src/lib/syncSettings";

describe("the people shared with (#235)", () => {
  it("puts the last one first, once, in lower case", () => {
    const list = rememberRecipient(rememberRecipient([], "Ann@Example.com "), "bob@example.com");
    expect(list).toEqual(["bob@example.com", "ann@example.com"]);
    expect(rememberRecipient(list, "ANN@example.com")).toEqual(["ann@example.com", "bob@example.com"]);
    expect(rememberRecipient(list, "  ")).toBe(list);
  });

  it("keeps at most twelve", () => {
    let list: string[] = [];
    for (let i = 1; i <= 20; i++) list = rememberRecipient(list, `p${i}@example.com`);
    expect(list).toHaveLength(MAX_RECIPIENTS);
    expect(list[0]).toBe("p20@example.com");
  });

  it("reads back what it wrote, the same as the Android app", () => {
    const list = ["ann@example.com", "bob@example.com"];
    expect(encodeRecipients(list)).toBe('["ann@example.com","bob@example.com"]');
    expect(decodeRecipients(encodeRecipients(list))).toEqual(list);
    expect(decodeRecipients('["Ann@Example.com", "ann@example.com", 3, "not an address"]')).toEqual(["ann@example.com"]);
    expect(decodeRecipients("nonsense")).toBeNull();
    expect(decodeRecipients(undefined)).toBeNull();
  });

  it("forgets a person, whatever the case", () => {
    const list = ["ann@example.com", "bob@example.com"];
    expect(forgetRecipient(list, " Ann@Example.com")).toEqual(["bob@example.com"]);
    expect(forgetRecipient(list, "carol@example.com")).toEqual(list);
  });

  it("travel with the account", () => {
    const mine = { ...defaultSettings, recipients: ["ann@example.com"] };
    const values = syncedSettings(mine);
    expect(values[RECIPIENTS]).toBe('["ann@example.com"]');
    expect(applySynced(defaultSettings, values).recipients).toEqual(["ann@example.com"]);
    expect(applySynced(mine, {}).recipients).toEqual(["ann@example.com"]);
  });
});
