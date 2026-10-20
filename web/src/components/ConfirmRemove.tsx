import { Trash2 } from "lucide-react";
import { t } from "@/lib/i18n";
import { Button } from "./ui/button";
import { Dialog } from "./ui/dialog";

/** Asks before removing a feed, a folder or a tag, as the Android app does. */
export function ConfirmRemove({ open, title, body, onConfirm, onClose }: { open: boolean; title: string; body: string; onConfirm: () => void; onClose: () => void }) {
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={title}
      closeLabel={t("close")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("cancel")}
          </Button>
          <Button
            variant="destructive"
            onClick={() => {
              onClose();
              onConfirm();
            }}
          >
            <Trash2 />
            {t("remove")}
          </Button>
        </>
      }
    >
      <p className="text-sm">{body}</p>
    </Dialog>
  );
}
