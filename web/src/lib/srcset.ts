/**
 * A picture's `srcset` ("a.jpg 400w, b.jpg 800w") as its candidates, read the way browsers do: an
 * address runs until a space, so the commas inside it stay (Substack's and other image servers'
 * addresses hold them, as in `…/w_424,c_limit,f_auto/…`); a comma after an address or its
 * descriptor ends the candidate. The same as the Android app's `ArticleBlocks.srcsetCandidates`.
 */
export interface SrcsetCandidate {
  url: string;
  /** "400w", "2x", or "" without one. */
  descriptor: string;
}

export function parseSrcset(srcset: string | null | undefined): SrcsetCandidate[] {
  const out: SrcsetCandidate[] = [];
  if (!srcset) return out;
  let i = 0;
  const n = srcset.length;
  const space = (c: string) => c === " " || c === "\t" || c === "\n" || c === "\r" || c === "\f";
  while (i < n) {
    while (i < n && (space(srcset[i]) || srcset[i] === ",")) i++;
    if (i >= n) break;
    let end = i;
    while (end < n && !space(srcset[end])) end++;
    let url = srcset.slice(i, end);
    i = end;
    let descriptor = "";
    if (url.endsWith(",")) {
      url = url.replace(/,+$/, "");
    } else {
      // The descriptors, up to the comma that ends the candidate (none inside parentheses).
      let depth = 0;
      const start = i;
      while (i < n && (srcset[i] !== "," || depth > 0)) {
        if (srcset[i] === "(") depth++;
        else if (srcset[i] === ")") depth = Math.max(0, depth - 1);
        i++;
      }
      descriptor = srcset.slice(start, i).trim();
    }
    if (url) out.push({ url, descriptor });
  }
  return out;
}

/** The candidate's size for picking the largest: the number of "800w" or "2x", 1 without one. */
export function candidateSize(c: SrcsetCandidate): number {
  return Number.parseFloat(c.descriptor) || 1;
}
