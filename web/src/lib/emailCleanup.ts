/**
 * Takes the usual email chrome out of a newsletter's HTML before it is shown (2026-11-15): a "View
 * in browser" link near the top, hidden preheader text, and spacer pictures. Only for articles of a
 * Newsletters feed, never for ordinary RSS articles. Conservative: what is not clearly chrome stays.
 * The same rules as the Android app's `EmailCleanup.kt`.
 */

const TAG = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>/g;
const ANCHOR = /<a\b[^>]*>([\s\S]*?)<\/a\s*>/gi;
const IMAGE = /<img\b[^>]*>/gi;
const STYLE_ATTR = /\bstyle\s*=\s*(?:"([^"]*)"|'([^']*)')/i;
const SRC_ATTR = /\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')/i;
const SKIPPED = /<head\b[\s\S]*?<\/head\s*>|<style\b[\s\S]*?<\/style\s*>|<!--[\s\S]*?-->/gi;
const ANY_TAG = /<[^>]*>/g;
const RICH_INNER = /<(?:img|a|table)\b/i;
const SPACES = /\s+/g;
const INVISIBLE = /[\u200B-\u200D\u00A0\u2060]/g;

/** Elements whose hiding is judged; others (tables, lists, links) are left alone. */
const HIDEABLE = new Set(["div", "span", "p", "td", "center", "font"]);

/** How much text a weakly hidden element may hold and still count as a preheader. */
const PREHEADER_MAX = 500;

/** A "view in browser" link counts when at most this much text comes before it. */
const TOP_TEXT = 400;

/** Longest link text read as a "view in browser" link. */
const LINK_MAX = 80;

const WEAK_HIDDEN = /(?:^|;)(?:max-height:0(?:px)?|opacity:0|font-size:0(?:px)?|mso-hide:all)(?:;|$)/;
const PIXEL_STYLE = /(?:^|;)(?:width|height):[01](?:px)?(?:;|$)/;
const SPACER_NAME = /(?:^|\/)(?:spacer|space|blank|transparent|clear|shim|1x1|pixel|px)[a-z0-9_.-]*\.(?:gif|png)(?:\?|#|$)/;

/** Whole link texts (lower case, trimmed) that mean "view this email in your browser". */
const VIEW_ONLINE_PARTS = [
  String.raw`(?:view|read|open|see|show|display)(?: (?:this|the|our|my))?(?: (?:email|e-mail|newsletter|message|mail|version|post|issue))?` +
    String.raw` (?:online|on the web|as a web ?page|in (?:your |a |the |my )?(?:web )?browser|in browser)`,
  String.raw`(?:view|see|open) (?:the |our )?(?:web|online|browser) version`,
  String.raw`(?:web|online|browser) version`,
  String.raw`click here to (?:view|read|open|see) (?:this |the )?(?:email |e-mail |newsletter |message )?(?:online|in (?:your |a |the )?browser)`,
  String.raw`bekijk(?: (?:deze|de|dit))?(?: (?:e-?mail|nieuwsbrief|mail|bericht))? (?:online|in (?:je|uw|de) browser|in browser)`,
  String.raw`(?:online|in (?:je|uw|de) browser) (?:bekijken|lezen|openen)`,
  String.raw`(?:lees|open) (?:online|in (?:je|uw|de) browser)`,
  String.raw`(?:bekijk (?:de )?)?(?:online|web|browser) ?versie`,
  String.raw`(?:voir|lire|afficher|ouvrir)(?: (?:ce|cet|cette|le|la|l'))?(?: (?:mail|e-?mail|message|newsletter|courriel|version))? ` +
    String.raw`(?:en ligne|dans (?:votre |le |un )?navigateur|sur (?:le |votre )?(?:web|navigateur))`,
  String.raw`version (?:en ligne|web)`,
  String.raw`(?:im|in (?:ihrem|deinem|einem)) browser (?:ansehen|anzeigen|öffnen|lesen)`,
  String.raw`(?:diese )?(?:e-?mail|nachricht|newsletter) (?:im|in (?:ihrem|deinem)) browser (?:ansehen|anzeigen|öffnen)`,
  String.raw`online (?:ansehen|lesen|anzeigen)`,
  String.raw`(?:browser|web|online)-?version`,
  String.raw`(?:ver|leer|abrir)(?: (?:este|esta|el|la|nuestro))?(?: (?:correo|mensaje|email|e-mail|boletín|newsletter|versión))? ` +
    String.raw`(?:en línea|online|en (?:el |tu |su |un )?(?:navegador|explorador))`,
  String.raw`versión (?:web|en línea|online)`,
  String.raw`(?:visualizza|vedi|leggi|apri)(?: (?:questa|questo|la|il))?(?: (?:email|e-mail|mail|newsletter|messaggio|versione))? ` +
    String.raw`(?:online|in linea|(?:nel|sul|nel tuo|sul tuo) browser|sul web)`,
  String.raw`versione (?:online|web)`,
];
const VIEW_ONLINE = new RegExp("^(?:" + VIEW_ONLINE_PARTS.join("|") + ")$");

/** Words that lead into a "view in browser" link, taken away with it when they stand right before it. */
const LEAD_IN = new RegExp(
  String.raw`(?:having trouble (?:viewing|reading|seeing) this (?:email|e-mail|message|newsletter)|trouble (?:viewing|reading|seeing) this (?:email|e-mail|message|newsletter)|` +
    String.raw`can['’]?t see this (?:email|e-mail|message|newsletter)(?: properly| correctly)?|is this (?:email|e-mail|message) not displaying (?:properly|correctly)|` +
    String.raw`problemen met (?:het )?(?:bekijken|weergeven|lezen) van (?:deze|dit) (?:e-?mail|bericht|nieuwsbrief)|` +
    String.raw`wordt (?:deze|dit) (?:e-?mail|bericht|nieuwsbrief) niet (?:goed|correct) weergegeven)[?:.!,]?\s*(?:-|–|—)?\s*$`,
  "i",
);

const TRIMMED = "<>›»«→←-–—|.:!()[]•·";

function normalize(style: string): string {
  return style.toLowerCase().replace(/\s+/g, "").replace(/!important/g, "");
}

/** True when an inline style gives a picture a width or height of 0 or 1 pixel: a tracking pixel or spacer. */
export function isPixelStyle(style: string | null | undefined): boolean {
  return style != null && PIXEL_STYLE.test(normalize(style));
}

function styleOf(attrs: string): string | null {
  const m = STYLE_ATTR.exec(attrs);
  return m ? normalize(m[1] ?? m[2] ?? "") : null;
}

function textLength(html: string): number {
  return html.replace(ANY_TAG, "").replace(SPACES, " ").trim().length;
}

/** The newsletter's HTML without the email chrome. */
export function cleanEmail(html: string): string {
  return removeSpacers(removeViewInBrowser(removeHidden(html)));
}

/** Hidden preheader text: elements hidden by their inline style, with what is in them. */
export function removeHidden(html: string): string {
  const tokens = Array.from(html.matchAll(TAG));
  let out = "";
  let copied = 0;
  let i = 0;
  while (i < tokens.length) {
    const open = tokens[i];
    const name = open[2].toLowerCase();
    const style = open[1] === "" && HIDEABLE.has(name) && !open[0].endsWith("/>") ? styleOf(open[3]) : null;
    const strong = style !== null && style.includes("display:none");
    const weak = style !== null && WEAK_HIDDEN.test(style);
    if (!strong && !weak) {
      i++;
      continue;
    }
    // Its end: the closing tag of the same name that balances this one; none, and it stays.
    let depth = 1;
    let j = i + 1;
    while (j < tokens.length) {
      const t = tokens[j];
      if (t[2].toLowerCase() === name && !t[0].endsWith("/>")) {
        depth += t[1] === "" ? 1 : -1;
        if (depth === 0) break;
      }
      j++;
    }
    if (j >= tokens.length) {
      i++;
      continue;
    }
    const start = open.index! + open[0].length;
    const inner = html.substring(start, tokens[j].index!);
    // Display none is hidden for sure; the other signs also hide a wrapper around pictures, so they only count for plain text.
    if (!strong && (RICH_INNER.test(inner) || textLength(inner) > PREHEADER_MAX)) {
      i++;
      continue;
    }
    out += html.substring(copied, open.index!);
    copied = tokens[j].index! + tokens[j][0].length;
    i = j + 1;
  }
  return out + html.substring(copied);
}

function linkText(inner: string): string {
  const text = inner
    .replace(ANY_TAG, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&#160;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&#39;/g, "'")
    .replace(/&rsquo;/g, "’")
    .replace(INVISIBLE, " ")
    .replace(SPACES, " ")
    .trim();
  let a = 0;
  let b = text.length;
  const trimmed = (c: string) => TRIMMED.includes(c) || /\s/.test(c);
  while (a < b && trimmed(text[a])) a++;
  while (b > a && trimmed(text[b - 1])) b--;
  return text.substring(a, b).toLowerCase();
}

/** A "View in browser" link (and "Having trouble viewing this email?" right before it) near the top. */
export function removeViewInBrowser(html: string): string {
  // The same text with head, styles and comments blanked out, to measure how far down a link is.
  const scan = html.replace(SKIPPED, (m) => " ".repeat(m.length));
  const found = Array.from(scan.matchAll(ANCHOR)).filter((m) => {
    const label = linkText(m[1]);
    return label.length <= LINK_MAX && VIEW_ONLINE.test(label) && textLength(scan.substring(0, m.index!)) <= TOP_TEXT;
  });
  if (found.length === 0) return html;
  let out = "";
  let copied = 0;
  for (const m of found) {
    let start = m.index!;
    const before = html.substring(copied, start);
    const textStart = before.lastIndexOf(">") + 1;
    const lead = LEAD_IN.exec(before.substring(textStart));
    if (lead) start = copied + textStart + lead.index;
    out += html.substring(copied, start);
    copied = m.index! + m[0].length;
  }
  return out + html.substring(copied);
}

/** Spacer pictures: one named like spacer.gif, or sized 0 or 1 pixel in its style. */
export function removeSpacers(html: string): string {
  return html.replace(IMAGE, (text) => {
    const srcMatch = SRC_ATTR.exec(text);
    const src = (srcMatch ? (srcMatch[1] ?? srcMatch[2] ?? "") : "").toLowerCase();
    const styleMatch = STYLE_ATTR.exec(text);
    const style = styleMatch ? (styleMatch[1] ?? styleMatch[2] ?? "") : null;
    return isPixelStyle(style) || SPACER_NAME.test(src) ? "" : text;
  });
}
