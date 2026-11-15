import { describe, expect, it } from "vitest";
import { cleanEmail, isPixelStyle } from "../src/lib/emailCleanup";
import { sanitizeArticle } from "../src/lib/sanitize";

// The same cases as the Android app's EmailCleanupTest.
describe("cleanEmail", () => {
  it("removes hidden preheader text", () => {
    expect(cleanEmail('<div style="display:none;max-height:0px;overflow:hidden">Preheader text &zwnj;&nbsp;</div><p>Real</p>')).toBe("<p>Real</p>");
    expect(cleanEmail('<div style="display: none !important"><div>inner</div>x</div><p>Real</p>')).toBe("<p>Real</p>");
    expect(cleanEmail('<span style="opacity:0;color:#fff">Hidden</span><p>Real</p>')).toBe("<p>Real</p>");
    expect(cleanEmail("<span style='mso-hide:all'>Hidden</span><p>Real</p>")).toBe("<p>Real</p>");
    expect(cleanEmail('<div style="font-size:0px;max-height:0">Hidden</div><p>Real</p>')).toBe("<p>Real</p>");
  });

  it("never removes visible content", () => {
    const kept = [
      '<p style="opacity:0.5">Faded</p>',
      '<p style="max-height:0.5em">Small</p>',
      '<div style="font-size:0"><img src="https://a.example/x.jpg"></div>',
      '<div style="max-height:0"><a href="https://a.example/">A link</a></div>',
      '<table style="display:none"><tr><td>Table</td></tr></table>',
      '<div style="display:none">Never closed<p>Real</p>',
      `<div style="max-height:0">${"word ".repeat(200)}</div>`,
      '<div style="color:red">Red</div>',
    ];
    for (const html of kept) expect(cleanEmail(html)).toBe(html);
  });

  it("leaves the rest when a hidden element sits inside a visible one", () => {
    expect(cleanEmail('<div><p>One</p><span style="display:none">x<span>y</span></span><p>Two</p></div>')).toBe("<div><p>One</p><p>Two</p></div>");
  });

  it("removes a view in browser link near the top, in several languages", () => {
    const labels = [
      "View in browser",
      "View in your browser",
      "View online",
      "Read online",
      "View this email in your browser",
      "View in browser →",
      "Open in browser",
      "Web version",
      "Bekijk in je browser",
      "Bekijk deze e-mail in je browser",
      "Online bekijken",
      "Bekijk online",
      "Voir en ligne",
      "Voir dans le navigateur",
      "Im Browser ansehen",
      "Ver en el navegador",
      "Visualizza nel browser",
    ];
    for (const label of labels) expect(cleanEmail(`<p><a href="https://x.example/v">${label}</a></p><p>Hello</p>`), label).toBe("<p></p><p>Hello</p>");
  });

  it("takes the words before the link along", () => {
    expect(cleanEmail('<p>Trouble viewing this email? <a href="https://x.example/v"><span>View in your browser</span></a></p><p>Hello</p>')).toBe("<p></p><p>Hello</p>");
    expect(cleanEmail('<p>Hello there. <a href="https://x.example/v">View online</a></p>')).toBe("<p>Hello there. </p>");
  });

  it("keeps other links and links further down", () => {
    const kept = [
      '<p><a href="https://x.example/">Read more</a></p>',
      '<p><a href="https://x.example/">Read online store reviews</a></p>',
      '<p><a href="https://x.example/">View in browser tabs</a></p>',
      `<p>${"word ".repeat(120)}</p><p><a href="https://x.example/v">View in browser</a></p>`,
      '<!-- <a href="https://x.example/v">View in browser</a> -->',
    ];
    for (const html of kept) expect(cleanEmail(html)).toBe(html);
  });

  it("removes spacer pictures", () => {
    expect(cleanEmail('<p>a<img src="https://a.example/spacer.gif" width="20"></p>')).toBe("<p>a</p>");
    expect(cleanEmail('<p>a<img src="https://a.example/img/blank.gif?v=2"></p>')).toBe("<p>a</p>");
    expect(cleanEmail('<p>a<img src="https://a.example/p.png" style="width:1px;height:1px"></p>')).toBe("<p>a</p>");
    expect(cleanEmail("<p>a<IMG style='height: 0 !important' src=\"https://a.example/p.png\"></p>")).toBe("<p>a</p>");
    const photo = '<p><img src="https://a.example/photo.jpg" style="width:100%"><img src="https://a.example/spacers/photo.jpg"></p>';
    expect(cleanEmail(photo)).toBe(photo);
  });

  it("knows a pixel by its style", () => {
    expect(isPixelStyle("width:1px")).toBe(true);
    expect(isPixelStyle("border:0; HEIGHT: 0")).toBe(true);
    expect(isPixelStyle("width:1PX !important;display:block")).toBe(true);
    expect(isPixelStyle("width:100px")).toBe(false);
    expect(isPixelStyle("width:10px")).toBe(false);
    expect(isPixelStyle("width:0.5em")).toBe(false);
    expect(isPixelStyle("max-width:1px")).toBe(false);
    expect(isPixelStyle(null)).toBe(false);
  });

  it("takes the chrome off a whole email and keeps its text", () => {
    const email =
      "<html><head><style>a{color:red}</style></head><body>" +
      '<div style="display:none">Preview text</div>' +
      '<p><a href="https://x.example/v">View in browser</a></p>' +
      '<img src="https://a.example/spacer.gif" width="600" height="20">' +
      "<h1>Title</h1><p>Real <b>content</b></p></body></html>";
    expect(cleanEmail(email)).toBe("<html><head><style>a{color:red}</style></head><body><p></p><h1>Title</h1><p>Real <b>content</b></p></body></html>");
  });
});

describe("sanitizeArticle for newsletters and pixels", () => {
  const base = "https://a.example/post";
  const email = '<div style="display:none">Preview text</div><p><a href="https://x.example/v">View in browser</a></p><p>Real text</p>';

  it("cleans the email only for a newsletter", () => {
    expect(sanitizeArticle(email, base, true, true)).not.toMatch(/Preview text|View in browser/);
    expect(sanitizeArticle(email, base, true, true)).toContain("Real text");
    expect(sanitizeArticle(email, base)).toContain("View in browser");
  });

  it("drops pictures that are 0 or 1 pixel by their style, in every article", () => {
    const html = sanitizeArticle('<img src="https://a.example/p.gif" style="width:1px;height:1px"><img src="https://a.example/ok.jpg" style="width:100%">', base);
    expect(html).not.toContain("p.gif");
    expect(html).toContain("ok.jpg");
  });

  it("drops open tracking pixels of mail platforms", () => {
    const pixels = [
      "https://x.us1.list-manage.com/track/open.php?u=1&id=2",
      "https://a.example/track/open?id=1",
      "https://mandrillapp.com/track/open.php?u=1",
      "https://u1.ct.sendgrid.net/wf/open?upn=abc",
      "https://eotrx.substackcdn.com/open?token=abc",
      "https://link.mail.beehiiv.com/ss/o/abc/def.gif",
      "https://open.convertkit-mail2.com/abc",
      "https://pstmrk.it/open/abc",
      "https://e.customeriomail.com/e/o/abc",
      "https://x.hubspotemail.net/e2t/o/abc",
      "https://a.example/o.gif?id=1",
    ];
    for (const url of pixels) expect(sanitizeArticle(`<p>Hi</p><img src="${url}">`, base), url).not.toContain("<img");
    expect(sanitizeArticle('<img src="https://a.example/photo.jpg">', base)).toContain("<img");
  });
});
