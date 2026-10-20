/**
 * Where Raindrop.io sends the browser back after connecting it in Reader's Android app (#112):
 * /raindrop/callback?code=…&state=…. A released app opens that address itself (an App Link); test
 * builds can't claim it, so this page offers to open either app with the same address, as an
 * Android intent link.
 */

export const CALLBACK_PATH = "/raindrop/callback";
export const APP_PACKAGE = "be.wimvandonck.reader";
export const DEV_PACKAGE = "be.wimvandonck.reader.debug";

/** The links that hand this callback to the app and to the test app, or null when it holds no code. */
export function appLinks(host: string, search: string): { app: string; dev: string } | null {
  const params = new URLSearchParams(search);
  if (!params.get("code") && !params.get("error")) return null;
  const target = `${host}${CALLBACK_PATH}${search}`;
  const link = (pkg: string) => `intent://${target}#Intent;scheme=https;package=${pkg};end`;
  return { app: link(APP_PACKAGE), dev: link(DEV_PACKAGE) };
}
