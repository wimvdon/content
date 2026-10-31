import { Check, Plus, Trash2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { gravatarUrl } from "@/lib/account";
import { app, useApp } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import { has, newAddress, outcomeOf, problemKey, suggestions, type ShareStatus } from "@/lib/sendTo";
import type { SharedArticle } from "@/lib/sharing";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { cn } from "./ui/cn";


/**
 * Send with Content (#167, #246), the article's share sheet: the same Send with Content as in the Save
 * and share sheet (#256, `SendToSection`): the people shared with before as chips to pick, several at
 * once, Add address for someone new, then Send to everyone picked. The article goes to their inbox. With
 * `onOtherApps`, Other apps (or Copy link) hands it to the browser's own share menu instead.
 */
export function ShareWithUserDialog({
  items,
  onClose,
  onOtherApps,
}: {
  items: SharedArticle[];
  onClose: () => void;
  onOtherApps?: () => void;
}) {
  const [picked, setPicked] = useState<string[]>([]);
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const send = async () => {
    if (picked.length === 0 || sending) return;
    setSending(true);
    setProblem(null);
    const results: [string, ShareStatus][] = [];
    for (const email of picked) results.push([email, await app.shareWithUser(email, items)]);
    setSending(false);
    const outcome = outcomeOf(results);
    if (!outcome) return;
    if ("sent" in outcome) setSent(outcome.sent.length === 1 ? t("shareUserSent").replace("{email}", outcome.sent[0]) : plural("sentPeople", outcome.sent.length));
    else setProblem(outcome.status);
  };
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={t("shareUserMenu")}
      closeLabel={t("close")}
      footer={
        sent ? (
          <Button onClick={onClose}>{t("close")}</Button>
        ) : (
          <>
            {onOtherApps ? (
              <Button
                variant="ghost"
                className="mr-auto"
                onClick={() => {
                  onClose();
                  onOtherApps();
                }}
              >
                {typeof navigator !== "undefined" && "share" in navigator ? t("shareOtherApps") : t("copyLink")}
              </Button>
            ) : null}
            <Button variant="ghost" onClick={onClose}>
              {t("cancel")}
            </Button>
            <Button onClick={() => void send()} disabled={picked.length === 0 || sending}>
              {t("shareUserSend")}
            </Button>
          </>
        )
      }
    >
      {sent ? (
        <p className="text-sm">{sent}</p>
      ) : (
        <div className="space-y-2">
          <SendToSection
            picked={picked}
            onChange={(next) => {
              setPicked(next);
              setProblem(null);
            }}
          />
          {problem ? (
            <p role="alert" className="text-sm text-destructive">
              {t(problemKey(problem))}
            </p>
          ) : null}
        </div>
      )}
    </Dialog>
  );
}

/** Their Gravatar picture (else their first letter of `label`) in a circle. */
export function Avatar({ email, label, images, className = "size-6" }: { email: string; label: string; images: boolean; className?: string }) {
  const [src, setSrc] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);
  useEffect(() => {
    let current = true;
    if (images) void gravatarUrl(email, 64).then((url) => current && setSrc(url));
    return () => {
      current = false;
    };
  }, [email, images]);
  return src && !missing ? (
    <img src={src} alt="" className={cn("shrink-0 rounded-full", className)} onError={() => setMissing(true)} />
  ) : (
    <span className={cn("flex shrink-0 items-center justify-center rounded-full bg-primary/15 text-xs font-medium text-primary", className)} aria-hidden="true">
      {label.slice(0, 1).toUpperCase()}
    </span>
  );
}

/** How long a press lasts before it is a hold (as Android's long-press). */
const HOLD_MS = 500;

/** A Gravatar name for each address, loaded once per address, so typing a name finds its person. */
function useNames(emails: string[]): Record<string, string | null> {
  const [names, setNames] = useState<Record<string, string | null>>({});
  // One string, so the effect runs again only when the list of addresses changes.
  const key = emails.join(",");
  useEffect(() => {
    let current = true;
    for (const email of key ? key.split(",") : []) {
      void app.profileName(email).then((found) => current && setNames((n) => (n[email] === undefined ? { ...n, [email]: found || null } : n)));
    }
    return () => {
      current = false;
    };
  }, [key]);
  return names;
}

/**
 * Send with Content (#256, #284), laid out like an email's To field, as on Android: the people picked as
 * chips in a To box (× takes one out), with a field after them for a name or address. Under it,
 * suggestions: people shared with before who aren't picked, filtered by what is typed; click one to add
 * them. A whole new address typed in gives "Add …", checked for a Content account first. Hold a
 * suggestion (or right-click it) to tidy the list: each gets a trash button that forgets them on every
 * device; Done ends it. Sending is the sheet's button.
 */
export function SendToSection({ picked, onChange }: { picked: string[]; onChange: (picked: string[]) => void }) {
  const { settings } = useApp();
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState(false);
  const [checking, setChecking] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const names = useNames([...settings.recipients, ...picked]);
  const shown = suggestions(settings.recipients, picked, query, (e) => names[e]);
  const typed = newAddress(settings.recipients, picked, query);
  const hold = useRef<{ timer: number; held: boolean } | null>(null);
  const add = (email: string) => {
    onChange(has(picked, email) ? picked : [...picked, email]);
    setQuery("");
    setProblem(null);
  };
  // Someone new is checked for a Content account before they join the To field.
  const addNew = (email: string) => {
    if (checking) return;
    setChecking(true);
    void app.hasAccount(email).then((found) => {
      setChecking(false);
      if (found === true) add(email);
      else setProblem(found === false ? "noAccount" : "offline");
    });
  };
  const endHold = () => {
    if (hold.current) window.clearTimeout(hold.current.timer);
  };
  return (
    <div className="space-y-1">
      <label className={cn("flex min-h-10 flex-wrap items-center gap-1.5 rounded-md border px-3 py-1.5 focus-within:ring-2 focus-within:ring-ring", problem ? "border-destructive" : "")}>
        <span className="text-sm text-muted-foreground">{t("shareTo")}</span>
        {picked.map((email) => {
          const label = names[email] ?? email;
          return (
            <span key={email.toLowerCase()} title={email} className="inline-flex h-7 max-w-full items-center gap-1.5 rounded-full bg-accent pr-1 pl-0.5 text-sm text-accent-foreground">
              <Avatar email={email} label={label} images={settings.showImages} />
              <span className="truncate">{label}</span>
              <button
                type="button"
                aria-label={t("shareRecipientRemove").replace("{name}", label)}
                onClick={() => onChange(picked.filter((e) => e.toLowerCase() !== email.toLowerCase()))}
                className="cursor-pointer rounded-full p-0.5 hover:bg-background/60"
              >
                <X className="size-3.5" aria-hidden="true" />
              </button>
            </span>
          );
        })}
        <input
          type="email"
          autoComplete="email"
          aria-label={t("shareToHint")}
          placeholder={t("shareToHint")}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setProblem(null);
          }}
          onKeyDown={(e) => {
            // Enter adds what was typed: a whole new address, or the one suggestion it matches.
            if (e.key !== "Enter") return;
            e.preventDefault();
            if (typed) addNew(typed);
            else if (query.trim() && shown.length === 1) add(shown[0]);
          }}
          className="min-w-40 flex-1 bg-transparent py-1 text-sm outline-none"
        />
      </label>
      {problem ? (
        <p role="alert" className="text-sm text-destructive">
          {t(problemKey(problem))}
        </p>
      ) : null}
      {typed ? (
        <button type="button" onClick={() => addNew(typed)} className="flex w-full cursor-pointer items-center gap-3 rounded-md px-2 py-2 text-left text-sm hover:bg-muted">
          <Plus className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="truncate">{t("shareAddPerson").replace("{email}", typed)}</span>
        </button>
      ) : null}
      {shown.map((email) => {
        const name = names[email] ?? null;
        const label = name ?? email;
        const forgetLabel = t("recipientForget").replace("{name}", label);
        return (
          <div key={email} className="flex items-center gap-1 rounded-md hover:bg-muted">
            <button
              type="button"
              title={email}
              onClick={() => {
                if (hold.current?.held) {
                  hold.current = null;
                  return;
                }
                if (!editing) add(email);
              }}
              onPointerDown={() => {
                if (editing) return;
                const state = { held: false, timer: 0 };
                state.timer = window.setTimeout(() => {
                  state.held = true;
                  setEditing(true);
                }, HOLD_MS);
                hold.current = state;
              }}
              onPointerUp={endHold}
              onPointerLeave={endHold}
              onContextMenu={(e) => {
                e.preventDefault();
                setEditing(true);
              }}
              className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 px-2 py-1.5 text-left text-sm select-none"
            >
              <Avatar email={email} label={label} images={settings.showImages} className="size-8" />
              <span className="min-w-0">
                <span className="block truncate">{label}</span>
                {name ? <span className="block truncate text-xs text-muted-foreground">{email}</span> : null}
              </span>
            </button>
            {editing ? (
              <button type="button" aria-label={forgetLabel} title={forgetLabel} onClick={() => app.forgetRecipient(email)} className="cursor-pointer rounded-md p-2 text-muted-foreground hover:text-destructive">
                <Trash2 className="size-4" aria-hidden="true" />
              </button>
            ) : null}
          </div>
        );
      })}
      {editing ? (
        <div className="flex justify-end">
          <Button variant="ghost" size="sm" onClick={() => setEditing(false)}>
            <Check className="size-4" aria-hidden="true" />
            {t("recipientsDone")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
