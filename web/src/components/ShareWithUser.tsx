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

const PROBLEMS: Record<string, Key> = {
  noAccount: "shareUserNoAccount",
  noEmail: "shareUserNoEmail",
  signedOut: "shareUserSignedOut",
  offline: "syncOffline",
  failed: "syncServer",
};

/**
 * Send with Content (#167, #246): their email address, or one click on someone shared with before
 * (#235, with their Gravatar picture and name); Send says first when it has no Content account.
 * `what` says what goes (this article, or a tag's articles). With `onOtherApps` it is the article's
 * share sheet: Other apps (or Copy link) hands it to the browser's own share menu instead.
 */
export function ShareWithUserDialog({
  what,
  items,
  tag,
  onClose,
  onOtherApps,
}: {
  what: string;
  items: SharedArticle[];
  tag: string | null;
  onClose: () => void;
  onOtherApps?: () => void;
}) {
  const { settings } = useApp();
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const valid = EMAIL.test(email.trim());
  const send = () => {
    if (!valid || sending) return;
    setSending(true);
    setProblem(null);
    void app.shareWithUser(email.trim(), items, tag).then((result) => {
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

/** Someone shared with before (#235): their Gravatar picture (else their first letter) and name (else address). */
function RecipientChip({ email, images, selected, onPick }: { email: string; images: boolean; selected: boolean; onPick: () => void }) {
  const [name, setName] = useState<string | null>(null);
  useEffect(() => {
    let current = true;
    void app.profileName(email).then((found) => current && setName(found || null));
    return () => {
      current = false;
    };
  }, [email]);
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
      <Avatar email={email} label={label} images={images} />
      <span className="truncate">{label}</span>
    </button>
  );
}
