import { Highlighter, NotebookPen, Trash2 } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createHighlight, resolve, type Anchor } from "@/lib/anchors";
import { playEmbed } from "@/lib/embedPlayer";
import { mark, offsetOf, pageText } from "@/lib/highlightDom";
import { t } from "@/lib/i18n";
import type { Highlight } from "@/lib/items";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";

/**
 * The article's text with its highlights (#102), the same as the Android reader: select text and
 * pick Highlight or Note in the bar that appears over it; a highlight is a yellow mark (lighter and
 * underlined when only found roughly); click one to change its note or remove it.
 */
export function HighlightedText({
  html,
  className,
  style,
  highlights,
  onAdd,
  onOpen,
}: {
  html: string;
  className: string;
  style: React.CSSProperties;
  /** Null where the article can't be highlighted (no link). */
  highlights: Highlight[] | null;
  onAdd: (highlight: Highlight, withNote: boolean) => void;
  onOpen: (id: string) => void;
}) {
  const root = useRef<HTMLDivElement>(null);
  const [bar, setBar] = useState<{ x: number; y: number; start: number; end: number } | null>(null);

  // The page, then the marks over it, again whenever either changes.
  useLayoutEffect(() => {
    const el = root.current;
    if (!el) return;
    el.innerHTML = html;
    if (!highlights) return;
    const text = pageText(el).text;
    for (const h of highlights) {
      const anchor = resolve(h, text);
      if (anchor.kind !== "notFound") mark(el, anchor.start, anchor.end, h.id, anchor.kind === "approximate");
    }
  }, [html, highlights]);

  // The bar over a selection in the text.
  useEffect(() => {
    if (!highlights) return;
    const onSelection = () => {
      const el = root.current;
      const selection = document.getSelection();
      if (!el || !selection || selection.isCollapsed || selection.rangeCount === 0) return setBar(null);
      const range = selection.getRangeAt(0);
      if (!el.contains(range.commonAncestorContainer)) return setBar(null);
      const page = pageText(el);
      const start = offsetOf(page, range.startContainer, range.startOffset);
      const end = offsetOf(page, range.endContainer, range.endOffset);
      if (start === null || end === null || !page.text.slice(start, end).trim()) return setBar(null);
      const box = range.getBoundingClientRect();
      setBar({ x: box.left + box.width / 2, y: box.top, start, end });
    };
    document.addEventListener("selectionchange", onSelection);
    return () => document.removeEventListener("selectionchange", onSelection);
  }, [highlights]);

  const make = (withNote: boolean) => {
    const el = root.current;
    if (!el || !bar) return;
    const text = pageText(el).text;
    onAdd(createHighlight(text, bar.start, bar.end, crypto.randomUUID(), Date.now()), withNote);
    document.getSelection()?.removeAllRanges();
    setBar(null);
  };

  return (
    <>
      <div
        ref={root}
        className={className}
        style={style}
        onClick={(e) => {
          // An embed's card plays its player in its place; a card without one opens as a link.
          const card = (e.target as HTMLElement).closest<HTMLAnchorElement>("a.embed[data-player]");
          if (card && playEmbed(card)) {
            e.preventDefault();
            return;
          }
          const id = (e.target as HTMLElement).closest<HTMLElement>("mark[data-highlight]")?.dataset.highlight;
          if (id && document.getSelection()?.isCollapsed !== false) onOpen(id);
        }}
      />
      {bar ? (
        <div
          role="toolbar"
          aria-label={t("highlightsTitle")}
          className="fixed z-30 flex -translate-x-1/2 -translate-y-full gap-1 rounded-xl border bg-card p-1 shadow-lg"
          style={{ left: bar.x, top: bar.y - 8 }}
          // Keeps the selection while a button is pressed.
          onMouseDown={(e) => e.preventDefault()}
        >
          <Button variant="ghost" size="sm" onClick={() => make(false)}>
            <Highlighter />
            {t("highlightAction")}
          </Button>
          <Button variant="ghost" size="sm" onClick={() => make(true)}>
            <NotebookPen />
            {t("note")}
          </Button>
        </div>
      ) : null}
    </>
  );
}

function Status({ anchor }: { anchor: Anchor | undefined }) {
  if (anchor?.kind === "approximate") return <p className="text-xs text-muted-foreground">{t("highlightApproximate")}</p>;
  if (anchor?.kind === "notFound") return <p className="text-xs text-muted-foreground">{t("highlightNotFound")}</p>;
  return null;
}

function Quote({ text, lines }: { text: string; lines: number }) {
  return (
    <blockquote className="border-l-[3px] border-primary pl-3 text-sm italic" style={{ display: "-webkit-box", WebkitLineClamp: lines, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
      {text}
    </blockquote>
  );
}

/** One highlight: its passage, how it was found, its note to edit, and Remove (not for a new one). */
export function HighlightDialog({
  highlight,
  anchor,
  onSave,
  onRemove,
  onClose,
}: {
  highlight: Highlight;
  anchor: Anchor | undefined;
  onSave: (note: string) => void;
  onRemove: (() => void) | null;
  onClose: () => void;
}) {
  const [note, setNote] = useState(highlight.note);
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={t("highlightTitle")}
      closeLabel={t("close")}
      footer={
        <>
          {onRemove ? (
            <Button
              variant="destructive"
              className="mr-auto"
              onClick={() => {
                onRemove();
                onClose();
              }}
            >
              <Trash2 />
              {t("highlightRemove")}
            </Button>
          ) : null}
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button
            onClick={() => {
              onSave(note);
              onClose();
            }}
          >
            {t("save")}
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Quote text={highlight.quote} lines={8} />
        <Status anchor={anchor} />
        <textarea
          autoFocus
          value={note}
          onChange={(e) => setNote(e.target.value)}
          aria-label={t("note")}
          placeholder={t("note")}
          rows={3}
          className="w-full resize-y rounded-lg border bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
      </div>
    </Dialog>
  );
}

/** Every highlight of the article, in the order they were made; click one to see or change it. */
export function HighlightsDialog({
  highlights,
  anchors,
  notCopied,
  onOpen,
  onClose,
}: {
  highlights: Highlight[];
  anchors: Map<string, Anchor>;
  /** The ones Instapaper refused (#164): "Not in Instapaper", tried again a day later. */
  notCopied?: string[];
  onOpen: (id: string) => void;
  onClose: () => void;
}) {
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`${t("highlightsTitle")} · ${highlights.length}`} closeLabel={t("close")}>
      {highlights.length === 0 ? <p className="text-sm text-muted-foreground">{t("highlightsNone")}</p> : null}
      <ul className="space-y-1">
        {[...highlights]
          .sort((a, b) => a.createdAt - b.createdAt)
          .map((h) => (
            <li key={h.id}>
              <button type="button" onClick={() => onOpen(h.id)} className="w-full cursor-pointer space-y-1 rounded-lg p-2 text-left hover:bg-muted">
                <Quote text={h.quote} lines={4} />
                {h.note ? <p className="pl-[15px] text-xs text-muted-foreground">{h.note}</p> : null}
                <div className="pl-[15px]">
                  <Status anchor={anchors.get(h.id)} />
                </div>
                {notCopied?.includes(h.id) ? <p className="pl-[15px] text-xs font-medium text-muted-foreground">{t("highlightNotInInstapaper")}</p> : null}
              </button>
            </li>
          ))}
      </ul>
    </Dialog>
  );
}
