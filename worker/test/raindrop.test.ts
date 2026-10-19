import { describe, expect, it } from "vitest";
import { handle, type Env } from "../src/index";
import { TOKEN_URL } from "../src/raindrop";

const env: Env = {
  ALLOWED_ORIGINS: "https://app.example",
  RAINDROP_CLIENT_ID: "rd-client",
  RAINDROP_CLIENT_SECRET: "rd-secret",
  RAINDROP_REDIRECT_URI: "https://app.example/raindrop/callback",
};

function upstream(answer: () => Response) {
  const calls: { input: string; init?: RequestInit }[] = [];
  const fetcher = async (input: string, init?: RequestInit) => {
    calls.push({ input, init });
    return answer();
  };
  return { fetcher, calls };
}

const post = (path: string, body: unknown) =>
  new Request("https://worker.example" + path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

const tokens = { access_token: "acc-1", refresh_token: "ref-1", expires: 1209599768, expires_in: 1209599, token_type: "Bearer" };

describe("/raindrop/authorize", () => {
  it("sends the browser to Raindrop with the client ID, the fixed callback and the state", async () => {
    const { fetcher, calls } = upstream(() => new Response("never"));
    const response = await handle(new Request("https://worker.example/raindrop/authorize?state=abcdef123456"), env, fetcher);
    expect(response.status).toBe(302);
    const target = new URL(response.headers.get("Location")!);
    expect(target.origin + target.pathname).toBe("https://raindrop.io/oauth/authorize");
    expect(target.searchParams.get("client_id")).toBe("rd-client");
    expect(target.searchParams.get("redirect_uri")).toBe("https://app.example/raindrop/callback");
    expect(target.searchParams.get("state")).toBe("abcdef123456");
    expect(calls).toHaveLength(0);
    // No secret in the address.
    expect(response.headers.get("Location")).not.toContain("rd-secret");
  });

  it("needs a state", async () => {
    const response = await handle(new Request("https://worker.example/raindrop/authorize?state=x"), env, upstream(() => new Response("")).fetcher);
    expect(response.status).toBe(400);
  });
});

describe("/raindrop/token and /raindrop/refresh", () => {
  it("swaps the code for tokens with the secret, and hands back only the tokens", async () => {
    const { fetcher, calls } = upstream(() => new Response(JSON.stringify(tokens), { status: 200 }));
    const response = await handle(post("/raindrop/token", { code: "c8983220-1cca" }), env, fetcher);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ access_token: "acc-1", refresh_token: "ref-1", expires_in: 1209599 });
    expect(calls[0].input).toBe(TOKEN_URL);
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({
      grant_type: "authorization_code",
      code: "c8983220-1cca",
      redirect_uri: "https://app.example/raindrop/callback",
      client_id: "rd-client",
      client_secret: "rd-secret",
    });
  });

  it("refreshes with the refresh token", async () => {
    const { fetcher, calls } = upstream(() => new Response(JSON.stringify({ ...tokens, access_token: "acc-2" }), { status: 200 }));
    const response = await handle(post("/raindrop/refresh", { refresh_token: "ref-1" }), env, fetcher);
    expect(((await response.json()) as { access_token: string }).access_token).toBe("acc-2");
    expect(JSON.parse(String(calls[0].init?.body))).toMatchObject({ grant_type: "refresh_token", refresh_token: "ref-1", client_secret: "rd-secret" });
  });

  it("refuses bad input, and says when Raindrop refuses", async () => {
    const ok = upstream(() => new Response(JSON.stringify(tokens)));
    expect((await handle(post("/raindrop/token", { code: "bad code!" }), env, ok.fetcher)).status).toBe(400);
    expect((await handle(new Request("https://worker.example/raindrop/token"), env, ok.fetcher)).status).toBe(405);
    expect(ok.calls).toHaveLength(0);
    const refused = upstream(() => new Response(JSON.stringify({ error: "bad_authorization_code" }), { status: 400 }));
    expect((await handle(post("/raindrop/token", { code: "abc" }), env, refused.fetcher)).status).toBe(400);
    const down = upstream(() => new Response("oops", { status: 500 }));
    expect((await handle(post("/raindrop/token", { code: "abc" }), env, down.fetcher)).status).toBe(502);
  });

  it("isn't available until the secrets are set", async () => {
    const response = await handle(post("/raindrop/token", { code: "abc" }), { ALLOWED_ORIGINS: "" }, upstream(() => new Response("")).fetcher);
    expect(response.status).toBe(503);
  });
});
