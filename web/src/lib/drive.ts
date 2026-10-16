/**
 * The sync file in Google Drive's hidden app folder, like the Android app's sync/GoogleDrive.kt.
 * Sign-in uses Google Identity Services in the browser: a short-lived access token for the
 * drive.appdata scope (only this app's own folder).
 */

export const SCOPE = "https://www.googleapis.com/auth/drive.appdata";
export const FILE_NAME = "rss-reader-sync.json";
const API = "https://www.googleapis.com";

export type SyncProblem = "offline" | "needsApproval" | "server" | "unreadableFile" | "notConfigured";

export class SyncError extends Error {
  readonly problem: SyncProblem;

  constructor(problem: SyncProblem) {
    super(problem);
    this.problem = problem;
  }
}

/** Where the sync file lives; Google Drive is the first. */
export interface SyncProvider {
  read(): Promise<string | null>;
  write(text: string): Promise<void>;
}

interface TokenClient {
  requestAccessToken(options?: { prompt?: string }): void;
}

interface TokenResponse {
  access_token?: string;
  expires_in?: number;
  error?: string;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        oauth2: {
          initTokenClient(config: {
            client_id: string;
            scope: string;
            callback: (r: TokenResponse) => void;
            error_callback?: (e: unknown) => void;
          }): TokenClient;
          revoke(token: string, done?: () => void): void;
        };
      };
    };
  }
}

let scriptLoading: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (window.google?.accounts) return Promise.resolve();
  scriptLoading ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      scriptLoading = null;
      reject(new SyncError("offline"));
    };
    document.head.appendChild(script);
  });
  return scriptLoading;
}

const TOKEN_KEY = "rss-reader-drive-token";

/** Google sign-in for Drive. Tokens last about an hour and are kept for this browser tab only. */
export class GoogleAuth {
  private readonly clientId: string;

  constructor(clientId: string) {
    this.clientId = clientId;
  }

  private cached(): string | null {
    try {
      const raw = sessionStorage.getItem(TOKEN_KEY);
      if (!raw) return null;
      const { token, expires } = JSON.parse(raw) as { token: string; expires: number };
      return Date.now() < expires - 60_000 ? token : null;
    } catch {
      return null;
    }
  }

  forget(): void {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      // Storage blocked; nothing to forget.
    }
  }

  /** A token without showing anything; throws needsApproval when the user must click Connect. */
  async token(): Promise<string> {
    const cached = this.cached();
    if (cached) return cached;
    return this.request("none");
  }

  /** Shows Google's screen (call from a click). */
  async connect(): Promise<string> {
    return this.request("");
  }

  private async request(prompt: "" | "none"): Promise<string> {
    if (!this.clientId) throw new SyncError("notConfigured");
    await loadScript();
    return new Promise<string>((resolve, reject) => {
      const client = window.google!.accounts.oauth2.initTokenClient({
        client_id: this.clientId,
        scope: SCOPE,
        callback: (r) => {
          if (!r.access_token) {
            reject(new SyncError("needsApproval"));
            return;
          }
          try {
            sessionStorage.setItem(TOKEN_KEY, JSON.stringify({ token: r.access_token, expires: Date.now() + (r.expires_in ?? 3600) * 1000 }));
          } catch {
            // Keep it in memory only.
          }
          resolve(r.access_token);
        },
        error_callback: () => reject(new SyncError("needsApproval")),
      });
      client.requestAccessToken({ prompt });
    });
  }
}

export class GoogleDrive implements SyncProvider {
  private readonly auth: GoogleAuth;

  constructor(auth: GoogleAuth) {
    this.auth = auth;
  }

  async read(): Promise<string | null> {
    const id = await this.findFile();
    if (!id) return null;
    return (await this.call(`${API}/drive/v3/files/${id}?alt=media`)).text();
  }

  async write(text: string): Promise<void> {
    const id = await this.findFile();
    if (id) {
      await this.call(`${API}/upload/drive/v3/files/${id}?uploadType=media&fields=id`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: text,
      });
    } else {
      const form = new FormData();
      form.append("metadata", new Blob([JSON.stringify({ name: FILE_NAME, parents: ["appDataFolder"], mimeType: "application/json" })], { type: "application/json" }));
      form.append("file", new Blob([text], { type: "application/json" }));
      await this.call(`${API}/upload/drive/v3/files?uploadType=multipart&fields=id`, { method: "POST", body: form });
    }
  }

  private async findFile(): Promise<string | null> {
    const q = encodeURIComponent(`name = '${FILE_NAME}' and trashed = false`);
    const response = await this.call(`${API}/drive/v3/files?spaces=appDataFolder&fields=files(id)&orderBy=modifiedTime%20desc&q=${q}`);
    const body = (await response.json().catch(() => null)) as { files?: { id?: string }[] } | null;
    if (!body) throw new SyncError("server");
    return body.files?.[0]?.id ?? null;
  }

  private async call(url: string, init: RequestInit = {}): Promise<Response> {
    for (let attempt = 0; attempt < 2; attempt++) {
      const token = await this.auth.token();
      let response: Response;
      try {
        response = await fetch(url, { ...init, headers: { ...(init.headers as Record<string, string>), Authorization: `Bearer ${token}` } });
      } catch {
        throw new SyncError("offline");
      }
      if (response.ok) return response;
      if (response.status === 401 && attempt === 0) {
        this.auth.forget();
        continue;
      }
      throw new SyncError(response.status === 401 || response.status === 403 ? "needsApproval" : "server");
    }
    throw new SyncError("needsApproval");
  }
}
