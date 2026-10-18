# Design

Reader is built from the android-app template and follows the design language of its sibling
apps (Tracker for Simkl, Time Tracker for Notion, Snoozer and Noter for Notion).

The shared rules (every section except "This app") have their master copy in `DESIGN.md` of
the android-app template (`wimvdon/android-app`). Change them there first, then copy them
unchanged to every app. Only the "This app" section belongs to this repository.

## Icons

- White adaptive-icon background.
- One bold shape with a diagonal gradient through neighbouring hues (top left to bottom
  right), filling most of the 66dp safe zone.
- One white detail inside that shape.
- A themed (monochrome) layer: the same shape with the detail cut out.

| App | Shape | Gradient |
| --- | --- | --- |
| Tracker for Simkl | TV screen with a checkmark | `#3D7BFF` → `#7A5CFF` → `#D15CE6` |
| Time Tracker for Notion | Stopwatch dial with a hand | `#FF8A3D` → `#F04438` → `#C81E5A` |
| Snoozer | Nine-lobed "cookie" (the home screen toggle) with a bell | `#22C3B4` → `#3E7BDA` → `#4E5BB0` |
| RSS Reader | The feed symbol on its own (owner's choice, see its "This app") | `#4FC3F7` → `#0091EA` → `#0D47A1` |
| Noter for Notion | Sticky note on a 3D box with a folded corner | `#FFC94D` → `#F5A623` → `#E8710A` |

## Colour

- Each app's fallback palette is a full Material 3 tonal scheme generated from its icon's
  middle gradient stop, so every colour role is set (none fall back to Material's purple).
- Settings › Theme offers System / Light / Dark and, on Android 12+, a Dynamic color switch.
  Dynamic color is off by default so the app's own colours show.
- The status and navigation bar icons follow the app's theme, not the system's
  (`SystemBarsFollowTheme`).
- `android:windowBackground` (and the splash background) matches the theme's background.

## Material 3

- Top app bars are Material's `TopAppBar` family with the default title style
  (Title Large, regular weight). Weight is not used to make app bar titles stand out.
- Emphasis through weight is kept for hero content (a title on a detail page, a big number).
- Button labels are in sentence case and short: the action in one to three words ("Watched",
  "Open settings", "Retry"). The screen around the button gives the context, so a label never
  repeats it ("Watched", not "Mark as watched"); explanations go in the text above the button.
  Longer wording is fine in menus and for screen readers (`contentDescription`).
- Icons come from Material's Rounded set; direction-dependent icons use `AutoMirrored`.
- Cards and grouped surfaces use the `surfaceContainer*` roles.
- Components keep Material's shape scale (`MaterialTheme.shapes`). App-level containers use
  `AppShapes`: `Group` (24dp) for banners and grouped content, `Item` (20dp) for tiles and
  highlighted rows.

## Navigation and structure

Follows Material 3's split between destinations and actions:

- Top-level destinations (two to five) sit in Material's `NavigationBar`, docked at the bottom
  edge. On wide windows an app may switch to a `NavigationRail` with the same destinations in
  the same order. A selected destination shows its filled (Rounded) icon, the others their
  Outlined icon, as Material's navigation guidelines describe. Floating bars (M3 Expressive's floating toolbar) are for actions on the
  current screen, never for switching sections.
- Tabs are only for peer views of the same content. Chips filter or sort a collection; they are
  not navigation and not general buttons.
- Every other page is a child page: a top app bar with a back arrow, no navigation bar.
- One FAB at most, for the screen's main action (add, start). Destructive actions never sit in
  a FAB or next to routine actions without separation, and ask for confirmation or offer Undo.
- Lists are lists: `ListItem` rows (or rows with the same paddings), separated by spacing or
  dividers. A card is for a unit that stands on its own (a poster, a banner, a running timer),
  not for every row or section.
- Rows that scroll sideways (posters, trailers, chips) keep one height while they scroll: text
  under a tile has a fixed number of lines (two for a title, one for a caption, ending in "…"),
  and a missing line still takes its space. Otherwise the row grows and shrinks as tiles with
  longer titles scroll into view, and everything below it jumps.
- Content keeps a readable width on wide windows: lists, forms, settings and text stay at most
  640dp wide and centred (`Modifier.readableWidth()`). Grids of posters or cards and pictures
  may use the whole width.

## Reordering

Every list the user can put in their own order works the same way (`ui/theme/Reorder.kt`):

- A drag handle (the six-dot grip, `DragHandle`) at the end of each row. Dragging it moves the
  row at once; the dragged row lifts with a shadow on `surfaceContainerHigh`, with a haptic tick
  when the drag starts. The new order is saved when the row is dropped.
- No up and down arrows. Screen readers get Move up and Move down as actions on the handle (or
  on the row in a list with its own drag code).
- A row that can't move (for example a destination locked last) shows what it is instead of a
  handle, such as a lock, and other rows can't pass it.
- Short lists use `ReorderableColumn`. A long or lazy list, or one with sections, may build
  its own drag on the same look and handle.
- Cards in a grid (a staggered grid of notes) are moved with a long press instead, as on a
  home screen: a grid has no row end for a handle.

## Settings screen

Built from `ui/theme/SettingsKit.kt`, so every app's settings look and behave the same. It
follows the Android and Material 3 settings pattern: one scrolling list, grouped by headers.

- `SettingsScaffold`: a large top app bar ("Settings") that collapses on scroll, with a back
  button, over one list, at most 640dp wide.
- `SettingsSection`: a section header (Title Small in the primary colour) above its rows. No
  cards and no collapsing: every setting is visible and one tap away.
- Rows inside a section:
  - `SwitchSetting`: a `ListItem` with a switch; the whole row is the switch and the change
    applies at once (no Save button).
  - `SettingItem`: a `ListItem` that opens something (a page, a dialog, another app), with a
    chevron or "opens elsewhere" icon when it leaves the screen.
  - `ExpandableSetting`: a row that shows longer instructions below it (setup steps most people
    read once).
  - `SettingsBlock`: anything else (a text field, segmented buttons for a single choice among
    two to four options, an explanation, a button), lined up with the rows' text.
- A choice among more than four options, or with long labels, opens a dialog with radio buttons
  from a `SettingItem` that shows the current value.
- Sections in this order, leaving out what an app does not have:
  1. Connection (account, API key, sync)
  2. The app's own features
  3. Shortcuts and integrations
  4. Appearance (`AppearanceSettings`: Theme, then Dynamic color)
  5. Language and formats
  6. `SettingsFooter`: app name and version, plus links such as data sources

## Motion

- Material 3 easing and duration tokens in `Motion`.
- Screens change with the shared-axis X transition (`Motion.sharedAxisX`, 30dp).
- State changes fade through (`Motion.fadeSpec`).
- Decorative animation is skipped when the user turned animations off
  (`rememberReducedMotion()`).

## Loading

- Content that loads over the network shows placeholders while it loads for the first time
  (`ui/theme/Skeleton.kt`), shaped like what will appear: poster tiles, list rows, form fields,
  lines of text. A soft highlight sweeps across them; with animations off they stay still.
- Screen readers read the placeholders out as one "Loading…" element.
- A refresh keeps the content on screen and shows pull-to-refresh or a small progress
  indicator instead; a busy button shows a spinner in place of its icon.
- An app with nothing to load (Snoozer) leaves `Skeleton.kt` out.

## Empty and setup states

Every screen that can be empty uses `EmptyState` (`ui/theme/EmptyState.kt`), so an empty
screen looks the same in every app:

- Centred in the space below the app bar, at most 360dp wide, and scrollable so pull-to-refresh
  keeps working.
- An icon in a 72dp `secondaryContainer` circle that says what is missing (a bookmark for an
  empty watchlist, a key for a missing API key), a title in Title Large that says what the state
  means ("No feeds yet"), and, when it helps, one or two sentences in Body Medium on how to fill
  the screen or what will appear here.
- At most two buttons, for the next useful step: a filled `Button` for the main one, an
  `OutlinedButton` or `TextButton` for an alternative. No button when there is nothing useful
  to do (a list that fills by itself).

The same component covers four situations:

| Situation | Example | Button |
| --- | --- | --- |
| Nothing here yet | "No watched movies" | The way to add something, if there is one |
| Nothing matches | A search or filter found nothing | "Clear filters" when filters are on |
| Couldn't load | The network failed and there is nothing to show | "Retry" |
| Setup needed | Not signed in, no API key, a permission missing | Straight to the place that fixes it ("Open settings") |

- A setup-needed state replaces the screen's content: a screen that can't work shows what to
  do, not a disabled screen with a small banner above it. Apps that need the same setup use the
  same words (Time Tracker for Notion and Noter for Notion both say "Connect to Notion").
- A banner (a `Group`-shaped card at the top) is only for something worth knowing while the
  screen still works. An error from an action on a screen that has content is a snackbar.

## Wording

Shared settings and actions use the same words in every app and language (strings are kept in
English, Dutch, German, French, Spanish and Italian).

| Meaning | en | nl | de | fr | es | it |
| --- | --- | --- | --- | --- | --- | --- |
| Theme: follow the device | System | Systeem | System | Système | Sistema | Sistema |
| Theme: light / dark | Light / Dark | Licht / Donker | Hell / Dunkel | Clair / Sombre | Claro / Oscuro | Chiaro / Scuro |
| Settings section | Appearance | Weergave | Darstellung | Apparence | Apariencia | Aspetto |
| Wallpaper colours | Dynamic color | Dynamische kleuren | Dynamische Farben | Couleurs dynamiques | Color dinámico | Colori dinamici |
| Back | Back | Terug | Zurück | Retour | Atrás | Indietro |
| Cancel / Save | Cancel / Save | Annuleren / Opslaan | Abbrechen / Speichern | Annuler / Enregistrer | Cancelar / Guardar | Annulla / Salva |
| Clear | Clear | Wissen | Löschen | Effacer | Borrar | Cancella |
| Retry | Retry | Opnieuw proberen | Wiederholen | Réessayer | Reintentar | Riprova |
| Dynamic color, supporting text | Use colors from your wallpaper | Kleuren van je achtergrond gebruiken | Farben deines Hintergrundbilds verwenden | Utiliser les couleurs du fond d’écran | Usar los colores del fondo de pantalla | Usa i colori dello sfondo |
| Go to settings | Open settings | Naar instellingen | Zu den Einstellungen | Ouvrir les paramètres | Abrir ajustes | Apri impostazioni |
| Setup needed (Notion apps) | Connect to Notion | Verbind met Notion | Mit Notion verbinden | Connexion à Notion | Conéctate a Notion | Collegati a Notion |
| Close a dialog that only shows something | Close | Sluiten | Schließen | Fermer | Cerrar | Chiudi |
| Finish an editing mode | Done | Klaar | Fertig | OK | Listo | Fine |
| Leave an account | Sign out | Uitloggen | Abmelden | Se déconnecter | Cerrar sesión | Esci |
| Screen reader: reorder handle | Reorder | Volgorde wijzigen | Neu anordnen | Réorganiser | Reordenar | Riordina |
| Screen reader: move a row | Move up / Move down | Omhoog / Omlaag | Nach oben / Nach unten | Monter / Descendre | Subir / Bajar | Sposta su / Sposta giù |

- Prefer the shortest clear wording ("System", not "Follow device (Auto)").
- American spelling in English ("color").
- Address the user informally in Dutch, German, Spanish and Italian (je, du, tú, tu) and with
  "vous" in French.
- Labels and buttons in sentence case.
- Help text only says what the screen doesn't: a consequence ("Your SIMKL lists stay as they
  are"), where data goes, or a term people may not know. It never restates a label ("Hide
  specials" needs no "Leave out special episodes"), describes a standard control (drag handles,
  switches, tapping a row) or explains why the app was built this way. One sentence where
  possible; longer setup steps go in an `ExpandableSetting` or the user guide.
- Buttons name the action as briefly as the language allows; drop "mark as", "markeren als",
  "als … markieren" and the like when the screen already makes it clear ("Gezien", not
  "Markeren als gezien").

## Shared design kit

These files live in each app (the template does not include them). They are identical in every
app apart from their package (and `R` import) lines. Copy a change to the other repositories:

- `ui/theme/Motion.kt`: motion tokens, the shared-axis transition and `rememberReducedMotion()`.
- `ui/theme/Shapes.kt`: `AppShapes`.
- `ui/theme/SystemBars.kt`: `SystemBarsFollowTheme()`.
- `ui/theme/Skeleton.kt`: loading placeholders (`Modifier.skeleton()`, `SkeletonLine`,
  `SkeletonBlock`).
- `ui/theme/EmptyState.kt`: `EmptyState` for empty, no-result, failed and setup-needed screens
  (see "Empty and setup states").
- `ui/theme/SettingsKit.kt`: the settings screen components above. It also needs
  `drawable/ic_arrow_back`, `drawable/ic_expand_more` and the strings `section_expanded` and
  `section_collapsed`.
- `ui/theme/Layout.kt`: `ReadableWidth` (640dp) and `Modifier.readableWidth()`.
- `ui/theme/Reorder.kt`: `DragHandle` and `ReorderableColumn` (see "Reordering"). It also
  needs `drawable/ic_drag_handle` and the strings `action_reorder`, `action_move_up` and
  `action_move_down`. An app without lists to reorder (Snoozer) leaves it out.

## This app

- Icon: the feed symbol on its own (the classic proportions: a dot and two thick, round-ended waves), gradient
  `#4FC3F7` → `#0091EA` → `#0D47A1` (sky to navy) on the white background; chosen by the owner
  over the shared "shape with a white detail" rule. Themed layer: the same symbol.
- Seed `#0091EA` (azure), tertiary from the icon's sky blue.
- Signature element: the reader page. Its font (Sans or Serif), text size, spacing, background
  (Theme, Sepia or Dark) and Auto dark (dark page whenever the app is dark) are set in one Text sheet that is also Settings › Reader; the whole
  screen, top bar and system bar icons included, takes the page colour.
- Article titles in the reader are the hero text (bold, in the reader's font). Unread rows in the
  list use SemiBold titles and a primary dot; read rows use `onSurfaceVariant`.
- Manage feeds ("Feeds and folders") is its own page (☰): a large top app
  bar over folder cards (`surfaceContainerHigh`, folder icon on a `secondaryContainer` circle) with
  their feeds indented below (`surfaceContainer`), a pen button and a drag handle at the end of each row, an outlined note under
  "No folder" when it is empty, and the
  dragged row lifted with a shadow. Tapping a row opens its edit dialog; Remove sits in the
  dialog, in the error colour. Manage Instapaper ("Instapaper folders and tags") is a page of its own
  too (☰, when signed in). Settings sections: Account (Sign in with Instapaper; signed in, who, the
  last sync or what went wrong, Sync now, Sign out and Delete synced data in the error colour, which
  asks first), Article list, Reader, Save and share (integration), Appearance, footer.
- Folder icons come from the whole Material Symbols set (outlined, rounded), picked with a search
  field and a grid in the folder dialog.
- Navigation: the ☰ drawer is the app's one top-level navigation, the same wherever the app is
  (no navigation bar or rail). Feeds and, signed in, Instapaper are sections in it; each header
  (Title Small in `primary`, a fold arrow, the unread count while folded) folds its section open
  and shut. The section being shown is open and the other folded shut whenever you move between
  them, so the drawer stays short. Back from an Instapaper list or a feed section goes to All articles.
- Feeds and Instapaper each have scrollable primary tabs under the top app bar for their peer views. Feeds:
  All (feed symbol), then each folder with its icon and name. Instapaper: Unread, Liked,
  Archive, each Instapaper folder, then Tags; under Tags a row of filter chips picks the tag
  (tags come and go too often for a tab each). The Mark all read FAB and the Unread chip are on
  Feeds only; the FAB only marks the section shown.
- The ☰ drawer: the Feeds section (All articles, the folders with their feeds, the other feeds),
  the Instapaper section (Unread, Liked, Archive, its folders and tags), then Manage feeds, Manage
  Instapaper and Settings. The web app's sidebar is the same, with the same folding sections.
- Site icons sit on a fixed light tile (`#F4F4F4`, rounded, 10% padding) in light and dark mode,
  so logos drawn for light browser tabs keep their contrast; the letter fallback uses
  `tertiaryContainer`.
- Article list: three views (Small, Medium, Large card with a 16:9 picture). Unread rows sit on
  `surfaceContainer`, read rows on `surfaceContainerLow`; the feed name is in `primary`. Swiping
  either way past 30% of the width toggles read (haptic tick at the threshold, row springs
  back); the drawer opens from the ☰ button only. The top bar shows the section and its unread count, with no ⋮ menu (refresh is pull-down; adding
  feeds lives on Manage feeds). Unread | All is one Material 3 filter chip, **Unread**, at the end
  of the top bar: selected (with a check) shows unread articles only, unselected all of them.
  Settings lives in the drawer only.
  The header (a small `TopAppBar` with the Unread chip, then the folder tabs attached to it) is one block that slides away
  while scrolling down and comes back as soon as you scroll up (Material's "enter always"),
  turning `surfaceContainer` once articles pass under it; articles sit under sticky day headers (Today,
  Yesterday, date) in `primary` Title Small; Mark all as read is an icon-only FAB. The drawer opens with the app's logo (the
  launcher icon: its feed symbol on white, in a circle with an `outlineVariant` edge) and name; the
  web app's sidebar and favicon use the same logo.
- Settings starts with Account; managing feeds and Instapaper lives in the drawer, not in Settings.

### Other platforms (planned)

Reader may get a web app and later an iOS app. The brand is shared; the design language is
each platform's own, so no app feels like a port.

- Shared on every platform: the icon, the seed colour and tertiary sky blue, the wording (Unread |
  All, folders, Mark all read, Save, Share), the flows (folders as peer views of one article list,
  swipe or a quick action to toggle read, the reader page with its Text settings) and the tone.
- Android: Material 3, everything above.
- Web: [shadcn/ui](https://ui.shadcn.com) (Radix primitives with Tailwind), themed with the seed
  colour; a neutral look that copies neither Android nor iOS. Wide screens use three panes:
  folders and feeds in a sidebar, the article list, the open article.
- iOS: Apple's Human Interface Guidelines with standard SwiftUI parts (navigation bar, tab bar or
  sidebar, swipe actions, sheets).
- Shared code is the logic (feeds, library, read state, sync), not the screens.
