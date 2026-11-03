import { useEffect, useRef, useState, type RefObject } from "react";
import { armed, atEnd, drag, newWheelGesture, WHEEL_GAP } from "@/lib/pullToMark";

/**
 * Scrolling on past the end of `list` (wheel, trackpad or finger) to mark all as read, as on
 * Android (lib/pullToMark.ts). Returns how far it is pulled, in pixels.
 */
export function usePullToMark(list: RefObject<HTMLElement | null>, enabled: boolean, onMark: () => void): number {
  const [pull, setPull] = useState(0);
  const mark = useRef(onMark);
  useEffect(() => {
    mark.current = onMark;
  }, [onMark]);
  useEffect(() => {
    const el = list.current;
    if (!el || !enabled) return;
    let value = 0;
    const set = (v: number) => {
      value = v;
      setPull(v);
    };
    const letGo = () => {
      if (armed(value)) mark.current();
      set(0);
    };
    const end = () => atEnd(el.scrollTop, el.clientHeight, el.scrollHeight);
    // Wheel: a gesture counts only when it starts at the end; it lets go when the wheel rests.
    let lastWheel = 0;
    let counting = false;
    let rest = 0;
    const onWheel = (e: WheelEvent) => {
      const now = performance.now();
      if (newWheelGesture(now, lastWheel)) counting = end() && e.deltaY > 0;
      lastWheel = now;
      if (!counting) return;
      if (e.deltaY < 0 && value <= 0) {
        counting = false;
        return;
      }
      set(drag(value, e.deltaY));
      window.clearTimeout(rest);
      rest = window.setTimeout(letGo, WHEEL_GAP);
    };
    // Finger: pulling up at the end, letting go on lifting it.
    let lastY: number | null = null;
    const onTouchStart = (e: TouchEvent) => {
      lastY = end() ? e.touches[0].clientY : null;
    };
    const onTouchMove = (e: TouchEvent) => {
      if (lastY === null) return;
      const y = e.touches[0].clientY;
      const delta = lastY - y;
      lastY = y;
      if (value > 0 || (delta > 0 && end())) set(drag(value, delta));
    };
    const onTouchEnd = () => {
      lastY = null;
      if (value > 0) letGo();
    };
    el.addEventListener("wheel", onWheel, { passive: true });
    el.addEventListener("touchstart", onTouchStart, { passive: true });
    el.addEventListener("touchmove", onTouchMove, { passive: true });
    el.addEventListener("touchend", onTouchEnd);
    el.addEventListener("touchcancel", onTouchEnd);
    return () => {
      window.clearTimeout(rest);
      el.removeEventListener("wheel", onWheel);
      el.removeEventListener("touchstart", onTouchStart);
      el.removeEventListener("touchmove", onTouchMove);
      el.removeEventListener("touchend", onTouchEnd);
      el.removeEventListener("touchcancel", onTouchEnd);
      setPull(0);
    };
  }, [list, enabled]);
  return pull;
}
