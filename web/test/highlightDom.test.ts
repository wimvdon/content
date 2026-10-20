import { describe, expect, it } from "vitest";
import { createHighlight, resolve } from "../src/lib/anchors";
import { mark, offsetOf, pageText } from "../src/lib/highlightDom";

function page(html: string): HTMLElement {
  const root = document.createElement("div");
  root.innerHTML = html;
  return root;
}

describe("highlights in the page", () => {
  it("reads the page as paragraphs joined by a blank line, like the Android reader", () => {
    const root = page("<h2>Title</h2><p>One <em>two</em> three.</p><ul><li>Four</li><li>Five</li></ul>");
    expect(pageText(root).text).toBe("Title\n\nOne two three.\n\nFour\n\nFive");
  });

  it("finds a selection's place in that text and marks a highlight across inline elements", () => {
    const root = page("<p>One <em>two</em> three.</p><p>Four five.</p>");
    const text = pageText(root);
    const em = root.querySelector("em")!.firstChild!;
    const start = offsetOf(text, em, 1)!;
    const end = offsetOf(text, root.querySelector("p")!.lastChild!, 3)!;
    expect(text.text.slice(start, end)).toBe("wo th");
    const h = createHighlight(text.text, text.text.indexOf("two three"), text.text.indexOf("two three") + 9, "h1", 1);
    const anchor = resolve(h, text.text);
    if (anchor.kind === "notFound") throw new Error("not found");
    mark(root, anchor.start, anchor.end, "h1", false);
    const marks = [...root.querySelectorAll("mark")];
    expect(marks.map((m) => m.textContent).join("")).toBe("two three");
    expect(marks.every((m) => m.dataset.highlight === "h1")).toBe(true);
    // The text itself is unchanged.
    expect(pageText(root).text).toBe(text.text);
  });
});
