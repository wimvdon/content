/** Plain text of an HTML fragment, with whitespace collapsed (like Html.toPlainText on Android). */
export function toPlainText(html: string): string {
  if (!/[<&]/.test(html)) return html.replace(/\s+/g, " ").trim();
  const spaced = html
    .replace(/<(script|style)[\s\S]*?<\/\1>/gi, " ")
    .replace(/<(br|\/?p|\/?div|\/?li|\/?h[1-6]|\/?blockquote|\/?tr)\b[^>]*>/gi, " ");
  const doc = new DOMParser().parseFromString(`<body>${spaced}</body>`, "text/html");
  return (doc.body.textContent ?? "").replace(/\u00a0/g, " ").replace(/\s+/g, " ").trim();
}

/** The first [length] characters of an HTML fragment's text, cut at a word. */
export function snippet(html: string, length: number): string {
  const text = toPlainText(html);
  if (text.length <= length) return text;
  const cut = text.slice(0, length);
  const space = cut.lastIndexOf(" ");
  return (space > length / 2 ? cut.slice(0, space) : cut) + "…";
}

export function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/** Java's String.hashCode, for article ids that must match the Android app's. */
export function javaHashCode(text: string): number {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0;
  return h >>> 0;
}
