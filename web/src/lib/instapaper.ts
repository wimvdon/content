/**
 * Saves a link to the user's Instapaper account with Instapaper's Simple API, straight from the
 * browser (not through the feed loader). Instapaper doesn't allow web pages to read its answer,
 * so the request is sent "no-cors": it arrives, but the app can't tell whether it worked.
 */
export async function saveToInstapaper(username: string, password: string, url: string, title: string): Promise<void> {
  const body = new URLSearchParams({ username, password, url });
  if (title.trim()) body.set("title", title.trim());
  await fetch("https://www.instapaper.com/api/add", { method: "POST", mode: "no-cors", body, referrerPolicy: "no-referrer" });
}
