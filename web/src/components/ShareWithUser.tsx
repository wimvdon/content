import { useEffect, useState } from "react";
import { gravatarUrl } from "@/lib/account";
import { app, useApp } from "@/lib/app";
import { t, type Key } from "@/lib/i18n";
import type { SharedArticle } from "@/lib/sharing";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { Input, Label } from "./ui/input";
import { cn } from "./ui/cn";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** The longest message for the receiver (the feed loader keeps as much). */
const MAX_MESSAGE = 1000;

const PROBLEMS: Record<string, Key> = {
  noAccount: "shareUserNoAccount",
  noEmail: "shareUserNoEmail",
  signedOut: "shareUserSignedOut",
  offline: "syncOffline",
  failed: "syncServer",
};

/**
 * Send to a Content user (#167): their email address, or one click on someone shared with before
 * (#235, with their Gravatar picture and name); Send says first when it has no Content account.
 * `what` says what goes (this article, or a tag's articles).
 */
export function ShareWithUserDialog({ what, items, tag, onClose }: { what: string; items: SharedArticle[]; tag: string | null; onClose: () => void }) {
  const { settings } = useApp();
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const valid = EMAIL.test(email.trim());
  const send = () => {
    if (!valid || sending) return;
    setSending(true);
    setProblem(null);
    void app.shareWithUser(email.trim(), items, tag, message.trim()).then((result) => {
      setSending(false);
      if (result === "sent") setSent(email.trim());
      else setProblem(result);
    });
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
            <Button variant="ghost" onClick={onClose}>
              {t("cancel")}
            </Button>
            <Button onClick={send} disabled={!valid || sending}>
              {t("shareUserSend")}
            </Button>
          </>
        )
      }
    >
      {sent ? (
        <p className="text-sm">{t("shareUserSent").replace("{email}", sent)}</p>
      ) : (
        <form
          className="space-y-2"
          onSubmit={(e) => {
            e.preventDefault();
            send();
          }}
        >
          <p className="text-sm">{what}</p>
          {settings.recipients.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {settings.recipients.map((address) => (
                <RecipientChip
                  key={address}
                  email={address}
                  images={settings.showImages}
                  selected={email.trim().toLowerCase() === address}
                  onPick={() => {
                    setEmail(address);
                    setProblem(null);
                  }}
                />
              ))}
            </div>
          ) : null}
          <div className="space-y-1">
            <Label htmlFor="share-user-email">{t("accountEmail")}</Label>
            <Input
              id="share-user-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setProblem(null);
              }}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="share-user-message">{t("shareUserMessage")}</Label>
            <textarea
              id="share-user-message"
              rows={2}
              maxLength={MAX_MESSAGE}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
          {problem ? (
            <p role="alert" className="text-sm text-destructive">
              {t(PROBLEMS[problem] ?? "syncServer")}
            </p>
          ) : null}
        </form>
      )}
    </Dialog>
  );
}

/** Someone shared with before (#235): their Gravatar picture (else their first letter) and name (else address). */
function RecipientChip({ email, images, selected, onPick }: { email: string; images: boolean; selected: boolean; onPick: () => void }) {
  const [name, setName] = useState<string | null>(null);
  const [src, setSrc] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);
  useEffect(() => {
    let current = true;
    void app.profileName(email).then((found) => current && setName(found || null));
    if (images) void gravatarUrl(email, 64).then((url) => current && setSrc(url));
    return () => {
      current = false;
    };
  }, [email, images]);
  const label = name ?? email;
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onPick}
      title={email}
      className={cn(
        "inline-flex h-8 max-w-full cursor-pointer items-center gap-1.5 rounded-lg border pr-3 pl-1 text-sm",
        selected ? "border-transparent bg-accent text-accent-foreground" : "hover:bg-muted",
      )}
    >
      {src && !missing ? (
        <img src={src} alt="" className="size-6 shrink-0 rounded-full" onError={() => setMissing(true)} />
      ) : (
        <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary/15 text-xs font-medium text-primary" aria-hidden="true">
          {label.slice(0, 1).toUpperCase()}
        </span>
      )}
      <span className="truncate">{label}</span>
    </button>
  );
}
