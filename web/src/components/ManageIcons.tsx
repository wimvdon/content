import type { ReactNode } from "react";

/**
 * The icons of Manage feeds and Manage Instapaper: what is managed (the feed symbol, a bookmark)
 * with a pen, in Lucide's style, as on Android.
 */

/** Lucide's pen at the bottom right (as in user-pen). */
const PEN = "M21.378 16.626a1 1 0 0 0-3.004-3.004l-4.01 4.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z";

function WithPen({ className, scale, x, y, children }: { className: string; scale: number; x: number; y: number; children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {/* Smaller, to leave room for the pen; the line keeps Lucide's width. */}
      <g transform={`translate(${x} ${y}) scale(${scale})`} strokeWidth={2 / scale}>
        {children}
      </g>
      <path d={PEN} />
    </svg>
  );
}

export function ManageFeedsIcon({ className = "size-5" }: { className?: string }) {
  return (
    <WithPen className={className} scale={0.7} x={-0.5} y={-0.5}>
      <path d="M4 11a9 9 0 0 1 9 9" />
      <path d="M4 4a16 16 0 0 1 16 16" />
      <circle cx="5" cy="19" r="1" />
    </WithPen>
  );
}

export function ManageInstapaperIcon({ className = "size-5" }: { className?: string }) {
  return (
    <WithPen className={className} scale={0.75} x={-0.5} y={-0.25}>
      <path d="M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z" />
    </WithPen>
  );
}
