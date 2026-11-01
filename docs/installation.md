# Installation

For developers who build the app, and for the one-time setup of releases. To just install the
app, download the APK from [Releases](https://github.com/wimvdon/rss-reader/releases).

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17 or newer | CI uses Temurin 21. Android Studio's bundled JBR works. |
| Android SDK | Platform 37 | `compileSdk` and `targetSdk` are 37. |
| Device or emulator | Android 12+ (API 31) | Per-app language setting needs Android 13+. |
| Git | any | Debug builds are versioned with `git describe`. |
| Node.js | 22 (with npm) | The build bundles the article extractor from `web/` (`npm ci`, then `npm run build:android`). |

Gradle is downloaded by the wrapper. There is no server, account or API key to set up.

## Build and run

```bash
git clone https://github.com/wimvdon/rss-reader.git
cd rss-reader
./gradlew testDebugUnitTest lintDebug                 # what every change must pass (CI builds the APKs)
./gradlew installDebug                                 # install on a connected phone/emulator
./gradlew assembleRelease                              # minified release build (unsigned without keystore.properties)
python3 -m unittest discover -s scripts/tests          # tests for the release scripts
```

Without Android Studio, point Gradle at the SDK with `sdk.dir=/path/to/sdk` in `local.properties`
(never committed).

## Verify it works

1. Open **Reader**. It shows **No feeds yet**.
2. Tap **Add feed**, type `theverge.com` and tap **Add**. The feed's articles appear.
3. Tap an article. It opens in the reader; **Aa** changes font, size, spacing and background.

## Testing a pull request

Open the pull request › **Checks** › **CI** › **Artifacts** and install
`content-<version>-debug.apk`. Debug builds have the application ID
`to.wim.content.debug` and the name **Content Dev**, so they install next to the
released app with their own feeds and settings. Uninstall it when you're done testing.

## Signing key

Releases are signed with your own key, stored as four **repository secrets**. You need a
computer with Java once (any JDK, or Android Studio, which includes one).

**Create the keystore.** Pick a strong password and use it for both prompts (with a PKCS12
keystore, the key password *is* the keystore password).

macOS / Linux (Terminal):
```sh
keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
Windows (PowerShell). If `keytool` is not found, use Android Studio's copy:
`& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"` instead of `keytool`.
```powershell
keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 `
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
It asks for your name etc. Any values are fine; they are visible in the APK's certificate.

**Encode it as base64** (this copies it to the clipboard):

| System | Command |
|---|---|
| Windows (PowerShell) | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\upload.jks")) \| Set-Clipboard` |
| macOS | `base64 -i upload.jks \| pbcopy` |
| Linux | `base64 -w 0 upload.jks \| xclip -selection clipboard` (or `base64 -w 0 upload.jks > upload.b64` and copy the file's content) |

**Add the secrets.** In the repository: **Settings → Secrets and variables → Actions →
New repository secret**, four times:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the base64 text from the clipboard |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `upload` (the `-alias` you used) |
| `ANDROID_KEY_PASSWORD` | the key password (same as the keystore password for PKCS12) |

Or with the GitHub CLI: `gh secret set ANDROID_KEYSTORE_BASE64 < upload.b64`, and
`gh secret set ANDROID_KEYSTORE_PASSWORD` (it prompts for the value), and so on.

The release workflow is forgiving about copy/paste mistakes in the base64 secret (line breaks,
quotes, missing `=` and so on). If something is still wrong, the run explains what without showing
the secret. It also checks the passwords and alias before building.

## Backing up the key

Store **`upload.jks` and both passwords** in at least two safe places, e.g. a password manager
(as an attachment) and an encrypted USB stick. Do **not** put them in the repository, email or
chat. GitHub secrets cannot be read back, so they are not a backup. Then delete `upload.b64` and
any other copies on your computer that aren't backups.

## Making a release

1. Make sure `main` contains what you want to release and CI is green.
2. **Actions → Release → Run workflow**, branch `main`, choose:
   - `patch` (1.2.**3**): fixes, small changes
   - `minor` (1.**3**.0): new features
   - `major` (**2**.0.0): big changes
   - `none`: only build a downloadable APK (no version, no release)
3. When it finishes, the new release (with the APK) is on the **Releases** page.

The first `patch`/`minor`/`major` release becomes `0.0.1` / `0.1.0` / `1.0.0`.
Pushing a tag (`git tag v1.2.3 && git push origin v1.2.3`) also releases.

The workflow **refuses** to publish when you're not on `main`, when the tag already exists, when the
version would not be higher than the last one, or when nothing changed since the last release.

Each release also includes `content-<version>-mapping.txt`. You need it to read crash stack traces
from release builds (see [testing.md](testing.md)).

**Version numbers:** a release `X.Y.Z` gets `versionCode = X*10000 + Y*100 + Z` (so `Y` and `Z`
must stay below 100). All other builds are named after `git describe`, e.g. `1.2.0-3-gabc1234`
(3 commits after 1.2.0), or `0.0.0-dev` without tags, so they never look like a release.

## Google Play (optional)

`release.yml` contains a commented-out `play-internal` job that builds an AAB and uploads it to the
**internal testing** track. To use it:
1. Create the app in the [Play Console](https://play.google.com/console) with the same
   application ID, and upload the first AAB by hand (`./gradlew bundleRelease` with the signing
   setup), as Play requires. Opt in to Play App Signing, using your key as the *upload key*.
2. In Google Cloud, create a service account, create a JSON key for it, and in the Play Console
   (**Users and permissions**) invite its e-mail with the "Release to testing tracks" permission.
3. Add the JSON as the repository secret `PLAY_SERVICE_ACCOUNT_JSON`.
4. Uncomment the job (`PACKAGE_NAME` is already set to `to.wim.content`). It runs after every published release.

## Notifications (Firebase)

Android notifications when something is shared (#235) use Firebase Cloud Messaging. Two secrets,
neither in the repository:

1. **The app's Firebase settings**, `google-services.json` from the Firebase console (Project
   settings › General, with Android apps `to.wim.content` and `to.wim.content.debug`),
   as the repository secret `GOOGLE_SERVICES_JSON` (Settings › Secrets and variables › Actions). CI
   and the release workflow write it to `app/google-services.json` before building; locally, put the
   file there yourself (it is in `.gitignore`). Without it the app builds and works, without
   notifications (the setting isn't shown).
2. **The service account** the feed loader sends with: Firebase console › Project settings › Service
   accounts › Generate new private key, the whole JSON file as the Worker secret `FCM_SERVICE_ACCOUNT`
   (Cloudflare › Workers & Pages › `rss-reader-worker` › Settings › Variables and Secrets).

## GitHub settings

Apply these once per repository (**Settings**):

1. **General → Template repository**: leave it unticked (only the template itself has it).
2. **General → Pull Requests**: allow **squash merging** (you may untick merge commits and
   rebase merging) and tick **Automatically delete head branches**.
3. **Branches → Add branch ruleset** (or classic *branch protection rule*) for `main`:
   - Require a pull request before merging.
   - Require status checks to pass → add **`Test, lint and build`** (the CI job; it appears in the
     list after CI has run once).
   - Block force pushes.

   ⚠️ Branch protection/rulesets on **private** repositories need **GitHub Pro** (or Team).
   On a free account they work only for **public** repositories. Without it, CI still runs and
   shows ✅/❌ on every pull request, but GitHub won't *stop* you merging a red one, so only
   merge when the checks are green.

The same settings with the [GitHub CLI](https://cli.github.com/):
```sh
gh repo edit wimvdon/rss-reader --enable-squash-merge --delete-branch-on-merge
gh api -X PUT repos/wimvdon/rss-reader/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["Test, lint and build"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```
