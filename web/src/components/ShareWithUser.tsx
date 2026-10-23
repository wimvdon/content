import { useState } from "react";
import { app } from "@/lib/app";
import { t, type Key } from "@/lib/i18n";
import type { SharedArticle } from "@/lib/sharing";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";
import { Input, Label } from "./ui/input";

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
 * Send to a Content user (#167): their email address; Send says first when it has no Content
 * account. `what` says what goes (this article, or a tag's articles).
 */
export function ShareWithUserDialog({ what, items, tag, onClose }: { what: string; items: SharedArticle[]; tag: string | null; onClose: () => void }) {
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
