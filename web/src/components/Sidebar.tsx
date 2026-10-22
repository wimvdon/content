import { Settings as SettingsIcon } from "lucide-react";
import { t } from "@/lib/i18n";
import { ManageFeedsIcon, ManageSavedIcon } from "./ManageIcons";
import { cn } from "./ui/cn";

/**
 * The ☰ menu (#189), as on Android: the app's name, then what you manage: feeds, tags and settings.
 * Feed and Saved are in the navigation bar, and their tabs and tag chips choose what the list shows.
 */
export function Sidebar({ onManage, onManageSaved, onSettings }: { onManage: () => void; onManageSaved: () => void; onSettings: () => void }) {
  return (
    <nav className="flex h-full flex-col" aria-label={t("navigation")}>
      <div className="flex items-center gap-3 px-4 py-4">
        {/* The app's logo, the same as the Android launcher icon and the favicon. */}
        <img src="/icon.svg" alt="" className="size-9 rounded-full border" />
        <span className="text-lg font-semibold">Content</span>
      </div>
      <div className="p-2">
        <Row onClick={onManage} icon={<ManageFeedsIcon />} label={t("manageFeeds")} />
        <Row onClick={onManageSaved} icon={<ManageSavedIcon />} label={t("manageTags")} />
        <Row onClick={onSettings} icon={<SettingsIcon className="size-5" />} label={t("settings")} />
      </div>
    </nav>
  );
}

function Row({ onClick, icon, label }: { onClick: () => void; icon: React.ReactNode; label: string }) {
  return (
    <button type="button" onClick={onClick} className={cn("flex w-full min-w-0 cursor-pointer items-center gap-3 rounded-full px-3 py-2 text-left text-sm hover:bg-muted")}>
      <span className="shrink-0 text-muted-foreground [&>*]:size-5">{icon}</span>
      <span className="min-w-0 flex-1 truncate">{label}</span>
    </button>
  );
}
