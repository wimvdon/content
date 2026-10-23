import { useEffect, useRef, useState } from "react";

/** One entry of a row's menu (#221). */
export interface RowMenuItem {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}

const LONG_PRESS_MS = 500;

/**
 * A row's menu (#221), as on Android's long press: right-click, or press and hold on a touch
 * screen, opens it where the pointer is. Escape or a click elsewhere closes it.
 */
export function WithRowMenu({ items, children }: { items: RowMenuItem[]; children: React.ReactNode }) {
  const [at, setAt] = useState<{ x: number; y: number } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const opened = useRef(false);
  useEffect(() => {
    if (!at) return;
    const close = (e: KeyboardEvent) => e.key === "Escape" && setAt(null);
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [at]);
  if (items.length === 0) return <>{children}</>;
  const cancel = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  };
  return (
    <div
      onContextMenu={(e) => {
        e.preventDefault();
        setAt({ x: e.clientX, y: e.clientY });
      }}
      onPointerDown={(e) => {
        if (e.pointerType !== "touch") return;
        opened.current = false;
        const { clientX: x, clientY: y } = e;
        timer.current = setTimeout(() => {
          opened.current = true;
          setAt({ x, y });
        }, LONG_PRESS_MS);
      }}
      onPointerUp={cancel}
      onPointerMove={cancel}
      onPointerCancel={cancel}
      onClickCapture={(e) => {
        // The click that ends a long press doesn't also open the article.
        if (opened.current) {
          e.preventDefault();
          e.stopPropagation();
          opened.current = false;
        }
      }}
    >
      {children}
      {at ? (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setAt(null)} onContextMenu={(e) => (e.preventDefault(), setAt(null))} aria-hidden="true" />
          <div
            role="menu"
            className="fixed z-50 min-w-48 rounded-xl border bg-card py-1 shadow-lg"
            style={{ left: Math.min(at.x, window.innerWidth - 208), top: Math.min(at.y, window.innerHeight - 48 * items.length - 16) }}
          >
            {items.map((item) => (
              <button
                key={item.label}
                type="button"
                role="menuitem"
                autoFocus={item === items[0]}
                onClick={() => {
                  setAt(null);
                  item.onClick();
                }}
                className="flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted focus-visible:bg-muted focus-visible:outline-none [&_svg]:size-4 [&_svg]:text-muted-foreground"
              >
                {item.icon}
                {item.label}
              </button>
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}
