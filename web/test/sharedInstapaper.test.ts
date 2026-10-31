import { describe, expect, it } from "vitest";
import { decodeShared, encodeShared, localShared, sameUser, sharedAction } from "../src/lib/sharedInstapaper";

const ann = { token: "token-a", userId: 7, username: "ann" };
const annAgain = { token: "token-b", userId: 7, username: "ann" };
const bob = { token: "token-c", userId: 9, username: "bob" };

describe("the Instapaper sign-in shared through the account", () => {
  it("goes into the file in the Android app's text and comes back the same", () => {
    expect(encodeShared(ann)).toBe('{"token":"token-a","username":"ann","userId":7}');
    expect(decodeShared(encodeShared(ann))).toEqual(ann);
    const noId = { token: "t", userId: null, username: "" };
    expect(decodeShared(encodeShared(noId))).toEqual(noId);
  });

  it("reads empty, damaged or tokenless values as no sign-in", () => {
    for (const text of [undefined, "", "nonsense", '{"username":"ann"}', '{"token":""}', "null"]) expect(decodeShared(text)).toBeNull();
  });

  it("sends this browser's sign-in, a disconnect, or nothing when it never had one", () => {
    expect(localShared(ann, undefined)).toBe(encodeShared(ann));
    expect(localShared(null, encodeShared(ann))).toBe("");
    expect(localShared(null, undefined)).toBeUndefined();
    expect(localShared(null, "")).toBe("");
  });

  it("connects this browser with another device's sign-in, and disconnects it with its disconnect", () => {
    expect(sharedAction(null, encodeShared(ann))).toEqual({ kind: "signIn", session: ann });
    expect(sharedAction(ann, encodeShared(annAgain))).toEqual({ kind: "signIn", session: annAgain });
    expect(sharedAction(ann, "")).toEqual({ kind: "signOut" });
  });

  it("does nothing when it is already the same, the file has none, or it can't be read", () => {
    expect(sharedAction(ann, encodeShared(ann))).toEqual({ kind: "none" });
    expect(sharedAction(ann, undefined)).toEqual({ kind: "none" });
    expect(sharedAction(null, "")).toEqual({ kind: "none" });
    expect(sharedAction(ann, "nonsense")).toEqual({ kind: "none" });
  });

  it("keeps what was synced for the same user's new token, not for another user", () => {
    expect(sameUser(ann, annAgain)).toBe(true);
    expect(sameUser(ann, bob)).toBe(false);
    expect(sameUser(null, ann)).toBe(false);
    expect(sameUser({ token: "x", userId: null, username: "" }, { token: "y", userId: null, username: "" })).toBe(false);
  });
});
