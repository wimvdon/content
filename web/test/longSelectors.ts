/**
 * jsdom refuses selectors longer than 2048 characters; browsers (and Android's WebView) don't.
 * Defuddle joins its lists of clutter into one long selector, so in the tests a long list is
 * asked part by part, giving what a browser would.
 */

const LIMIT = 2048;

/** The selectors of a comma-separated list, leaving commas inside brackets and quotes alone. */
function parts(selector: string): string[] {
  const out: string[] = [];
  let depth = 0;
  let quote: string | null = null;
  let start = 0;
  for (let i = 0; i < selector.length; i++) {
    const c = selector[i];
    if (quote) {
      if (c === "\\") i++;
      else if (c === quote) quote = null;
    } else if (c === '"' || c === "'") quote = c;
    else if (c === "(" || c === "[") depth++;
    else if (c === ")" || c === "]") depth--;
    else if (c === "," && depth === 0) {
      out.push(selector.slice(start, i));
      start = i + 1;
    }
  }
  out.push(selector.slice(start));
  return out.map((s) => s.trim()).filter(Boolean);
}

/** The list in pieces short enough for jsdom. */
function chunks(selector: string): string[] {
  const out: string[] = [];
  let current = "";
  for (const part of parts(selector)) {
    if (current && current.length + part.length + 1 > LIMIT) {
      out.push(current);
      current = "";
    }
    current = current ? `${current},${part}` : part;
  }
  if (current) out.push(current);
  return out;
}

const byPosition = (a: Node, b: Node) => (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1);

for (const proto of [Document.prototype, DocumentFragment.prototype, Element.prototype] as const) {
  const all = proto.querySelectorAll as (this: ParentNode, s: string) => NodeListOf<Element>;
  const one = proto.querySelector as (this: ParentNode, s: string) => Element | null;
  Object.defineProperty(proto, "querySelectorAll", {
    configurable: true,
    value(this: ParentNode, selector: string) {
      if (selector.length <= LIMIT) return all.call(this, selector);
      const found = new Set<Element>();
      for (const chunk of chunks(selector)) for (const el of all.call(this, chunk)) found.add(el);
      return [...found].sort(byPosition);
    },
  });
  Object.defineProperty(proto, "querySelector", {
    configurable: true,
    value(this: ParentNode, selector: string) {
      if (selector.length <= LIMIT) return one.call(this, selector);
      return (this.querySelectorAll(selector) as unknown as Element[])[0] ?? null;
    },
  });
}

const matches = Element.prototype.matches;
const closest = Element.prototype.closest;
Object.defineProperty(Element.prototype, "matches", {
  configurable: true,
  value(this: Element, selector: string): boolean {
    return selector.length <= LIMIT ? matches.call(this, selector) : chunks(selector).some((chunk) => matches.call(this, chunk));
  },
});
Object.defineProperty(Element.prototype, "closest", {
  configurable: true,
  value(this: Element, selector: string): Element | null {
    if (selector.length <= LIMIT) return closest.call(this, selector);
    return this.matches(selector) ? this : (this.parentElement?.closest(selector) ?? null);
  },
});
