import { Loader2, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { gravatarUrl } from "@/lib/account";
import { app, useApp } from "@/lib/app";
import { plural, t } from "@/lib/i18n";
import { has, isEmail, outcomeOf, people, problemKey, toggle, type ShareStatus } from "@/lib/sendTo";
import type { SharedArticle } from "@/lib/sharing";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { cn } from "./ui/cn";
import { Input } from "./ui/input";
import { action, chip, chipOn } from "./TopicChips";


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

/** A Gravatar name for each address, loaded once per address. */
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
 * Send with Content (#256), the same way as tags, as on Android (2026-11-02, instead of an
 * email-style To field): the people shared with before as filter chips with their Gravatar picture
 * and name; click one or more to pick them. **Edit people** after the chips (as Edit tags) opens the
 * People page of the same dialog: add someone by their address (checked for a Content account first;
 * they are picked at once) or forget someone. Sending is the dialog's button.
 */
export function SendToSection({ picked, onChange }: { picked: string[]; onChange: (picked: string[]) => void }) {
  const { settings } = useApp();
  const [managing, setManaging] = useState(false);
  // Someone picked who isn't in the list still shows, after the others.
  const shown = people(settings.recipients, picked);
  const names = useNames(shown);
  return (
    <div className="flex flex-wrap items-center gap-2">
      {shown.map((email) => {
        const label = names[email] ?? email;
        const on = has(picked, email);
        return (
          <button key={email.toLowerCase()} type="button" aria-pressed={on} title={email} onClick={() => onChange(toggle(picked, email))} className={cn(chip, "max-w-full pl-1", on ? chipOn : "hover:bg-muted")}>
            <Avatar email={email} label={label} images={settings.showImages} />
            <span className="truncate">{label}</span>
          </button>
        );
      })}
      <button type="button" onClick={() => setManaging(true)} className={action}>
        {shown.length === 0 ? <Plus className="size-4" aria-hidden="true" /> : <Pencil className="size-4" aria-hidden="true" />}
        {shown.length === 0 ? t("peopleAdd") : t("peopleEdit")}
      </button>
      {managing ? (
        <ManagePeopleDialog
          onAdded={(email) => !has(picked, email) && onChange([...picked, email])}
          onForgot={(email) => onChange(picked.filter((e) => e.toLowerCase() !== email.toLowerCase()))}
          onClose={() => setManaging(false)}
        />
      ) : null}
    </div>
  );
}

/**
 * Edit people, as Manage tags: the people you can send to, each with their picture, name and address
 * and a trash can that forgets them (on every device); under them an address field and **Add**,
 * which checks for a Content account first. Opened from a dialog it is a page of it, with a back arrow.
 */
export function ManagePeopleDialog({ onAdded, onForgot, onClose }: { onAdded: (email: string) => void; onForgot: (email: string) => void; onClose: () => void }) {
  const { settings } = useApp();
  const names = useNames(settings.recipients);
  const [address, setAddress] = useState("");
  const [checking, setChecking] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const typed = address.trim();
  const canAdd = isEmail(typed) && !checking;
  const add = () => {
    if (!canAdd) return;
    const known = settings.recipients.find((e) => e.toLowerCase() === typed.toLowerCase());
    if (known) {
      onAdded(known);
      setAddress("");
      return;
    }
    setChecking(true);
    setProblem(null);
    void app.hasAccount(typed).then((found) => {
      setChecking(false);
      if (found === true) {
        app.addRecipient(typed);
        onAdded(typed.toLowerCase());
        setAddress("");
      } else setProblem(found === false ? "noAccount" : "offline");
    });
  };
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={t("peopleTitle")} closeLabel={t("close")}>
      <div className="space-y-3">
        {settings.recipients.length === 0 ? <p className="text-sm text-muted-foreground">{t("peopleNone")}</p> : null}
        <ul className="space-y-1">
          {settings.recipients.map((email) => {
            const name = names[email] ?? null;
            const label = name ?? email;
            const forgetLabel = t("recipientForget").replace("{name}", label);
            return (
              <li key={email} className="flex items-center gap-3 rounded-md px-2 py-1.5">
                <Avatar email={email} label={label} images={settings.showImages} className="size-8" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm">{label}</span>
                  {name ? <span className="block truncate text-xs text-muted-foreground">{email}</span> : null}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={forgetLabel}
                  title={forgetLabel}
                  onClick={() => {
                    app.forgetRecipient(email);
                    onForgot(email);
                  }}
                >
                  <Trash2 />
                </Button>
              </li>
            );
          })}
        </ul>
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1 space-y-1">
            <Input
              type="email"
              autoComplete="email"
              value={address}
              onChange={(e) => {
                setAddress(e.target.value);
                setProblem(null);
              }}
              onKeyDown={(e) => {
                if (e.key !== "Enter") return;
                e.preventDefault();
                add();
              }}
              placeholder={t("peopleAddress")}
              aria-label={t("peopleAddress")}
              aria-invalid={problem !== null}
              className={cn(problem && "border-destructive")}
            />
            {problem ? (
              <p role="alert" className="text-sm text-destructive">
                {t(problemKey(problem))}
              </p>
            ) : null}
          </div>
          {/* The page's action, tonal as New tag in Manage tags. */}
          <Button variant="tonal" onClick={add} disabled={!canAdd}>
            {checking ? <Loader2 className="animate-spin" /> : <Plus />}
            {t("peopleAddAction")}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
