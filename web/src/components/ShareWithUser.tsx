import { Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { gravatarUrl } from "@/lib/account";
import { app, useApp } from "@/lib/app";
import { t } from "@/lib/i18n";
import { has, isEmail, people, problemKey, toggle } from "@/lib/sendTo";
import type { SharedArticle } from "@/lib/sharing";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { Input, Label } from "./ui/input";
import { cn } from "./ui/cn";


/**
 * Send with Content (#167, #246): their email address, or one click on someone shared with before
 * (#235, with their Gravatar picture and name); Send says first when it has no Content account. The
 * article goes to their inbox. With `onOtherApps` it is the article's share sheet: Other apps (or
 * Copy link) hands it to the browser's own share menu instead.
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
  const { settings } = useApp();
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const valid = isEmail(email);
  const send = () => {
    if (!valid || sending) return;
    setSending(true);
    setProblem(null);
    void app.shareWithUser(email.trim(), items).then((result) => {
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
              {t(problemKey(problem))}
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

/**
 * Send with Content in the Save and share sheet (#256), as on Android: the people shared with before as
 * chips to pick, several at once, then Add address for someone new, who is checked for a Content account
 * before they join the chips. Sending happens with the sheet's button.
 */
export function SendToSection({ picked, onChange }: { picked: string[]; onChange: (picked: string[]) => void }) {
  const { settings } = useApp();
  const [adding, setAdding] = useState(false);
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const add = () => {
    const address = email.trim();
    if (!isEmail(address) || busy) return;
    setBusy(true);
    void app.hasAccount(address).then((found) => {
      setBusy(false);
      if (found === true) {
        onChange(has(picked, address) ? picked : [...picked, address]);
        setEmail("");
      } else setProblem(found === false ? "noAccount" : "offline");
    });
  };
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">
        {people(settings.recipients, picked).map((address) => (
          <RecipientChip key={address.toLowerCase()} email={address} images={settings.showImages} selected={has(picked, address)} onPick={() => onChange(toggle(picked, address))} />
        ))}
        {adding ? null : (
          <button type="button" onClick={() => setAdding(true)} className="inline-flex h-8 cursor-pointer items-center gap-1 rounded-lg border px-3 text-sm hover:bg-muted">
            <Plus className="size-4" aria-hidden="true" />
            {t("shareAddAddress")}
          </button>
        )}
      </div>
      {adding ? (
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            add();
          }}
        >
          <Input
            type="email"
            autoComplete="email"
            aria-label={t("accountEmail")}
            placeholder={t("accountEmail")}
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setProblem(null);
            }}
          />
          <Button type="submit" variant="outline" disabled={!isEmail(email) || busy}>
            {t("shareAddAddress")}
          </Button>
        </form>
      ) : null}
      {problem ? (
        <p role="alert" className="text-sm text-destructive">
          {t(problemKey(problem))}
        </p>
      ) : null}
    </div>
  );
}
