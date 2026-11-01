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
  (Theme, Sepia or Dark) and Auto dark (dark page whenever the app is dark) are set in one Text sheet that is also Settings › Article view; the whole
  screen, top bar and system bar icons included, takes the page colour.
- Article titles in the reader are the hero text (bold, in the reader's font). Unread rows in the
  list use SemiBold titles and a primary dot; read rows use `onSurfaceVariant`.
- Tags: one way to group feeds and saved articles (a feed in one tag, a saved article in any
  number), each with a name, an icon and its place in the user's order, which every chip row and
  the sheets follow. **Manage tags** (a sheet, from Settings' large button and from **Edit
  tags** in the Add feed, Edit feed and Save/Organise sheets, over them) shows them as a
  reorderable list (2026-11-01, instead of chips: rows don't reflow under the finger): each row the
  tag's icon, its name and the grip at the end (`ReorderableColumn`); **New tag** after them, with **Done** at the bottom right as its
  changes are kept at once. **Manage feeds** (a sheet from Settings' other large button) shows the
  feeds the same way: rows (site icon, name, grip at the end) under a heading per tag (Title Small,
  `primary`, with the tag's icon; then No tag; an empty tag says "Drag a feed here"); a row
  dragged under another heading moves into that tag (`ReorderableSections`, `SectionOrder`); a failing
  feed's name in the error colour; then Add feed; tapping a feed opens the Edit feed sheet (name,
  address, tag chips, Save; Remove is a trash can at the top right and asks in a dialog). Tag chips look the same everywhere (#314): a `FilterChip` with the
  tag's icon and name, no check mark, in Feeds' and Saved's chip rows and in the sheets. After the
  chips in a sheet, **Edit tags** is a text button with a pen (an action, so it doesn't look like a
  tag chip). In Manage tags drag a row by its grip to reorder; tap it for the tag sheet
  (name, icon, a trash can at the top right; a name already taken joins the two). Remove tag asks
  in a dialog over the sheet. The web: still chips with a grip (← → on a focused chip) in dialogs, for now.
- Settings (the owner's choice, 2026-11-01, instead of the shared "one scrolling list"): two large
  buttons at the top, **Manage feeds** and **Manage tags** (side by side on `surfaceContainerHigh`,
  the icon on a `secondaryContainer` circle, the name and how many), then a list of pages, each a
  `SettingItem` with a Material Symbol, its title, what it holds and a chevron: Account (sign in with
  an emailed link; signed in, who, the last sync or what went wrong, Sync now, Sign out and Delete
  synced data in the error colour, which asks first), Integrations (Move to or from another reader,
  Instapaper), Theme, Article list, Article view (with Text and Sites you're signed in to); then the
  footer. The web: the same in one dialog, a page replacing the list with ← Settings above it. Every page is a `SettingsScaffold` with the
  page's name in the large top app bar and its sections inside.
- Folder icons come from the whole Material Symbols set (outlined, rounded), picked with a search
  field and a grid in the folder sheet. The app's other icons (the reading list's To read (an open
  book), Liked, Archive (a box) and tags included) use the same outlined, rounded style; only an icon that
  shows something is on is filled (a selected destination, the Like heart once liked). Saved and
  the Save action use Material Symbols list (#142): Saved is more than a reading list, so the open
  book stays on To read only.
- Navigation (#113): a navigation bar at the bottom (a navigation rail on windows 600dp and wider)
  switches between the destinations, Feeds (feed symbol), Saved (a list) and, signed in, the Inbox
  (a bell, with Material's small badge while something in it is unread; it was a bell in the search
  bar before). Search is
  an action in the top bar of both destinations (a magnifier) that opens one search page for
  everything: the field in its top bar, one row of filter chips above the results (**Feeds**, a
  menu chip with All feeds and a check box per feed; **Saved**; a divider; the tag chips), and the results as one list of article rows ranked by relevance, each with a small label
  (Feed or Saved). It is not a navigation bar item: it acts on the content of both
  destinations rather than being a place of its own. **Android has no ☰ drawer** (the owner's
  choice): Feeds, Saved and the Inbox are the navigation bar, and every place inside them is a chip.
  The search bar starts with the magnifier and ends with Settings: the account's picture
  (Gravatar, 32dp circle) while signed in with Show images on, else a cogwheel. The web's sidebar is
  always on screen, so there both sections stay open until you fold one, and stay as you left them.
  Back from Saved, the Inbox or a feed section goes to All articles.
- Three levels, the same in Feeds and Saved: the navigation bar says where (Feeds or Saved); a
  **segmented switch** under the search bar (the same as To read / Archive in the save sheet, full
  width with 16dp sides; on the web's wide window in the sidebar) says which view (Feeds: **Unread**
  and **All**; Saved: **To read** and **All**); one row of chips under it says which group: the tags in Feeds, Liked (heart) and
  the tags in Saved, in the tags' order, each only when it has something in the tab shown (the
  chosen one stays). Group chips have no check mark and chosen ones keep their weight, so a chip
  never changes width and the row never moves (#208). There is no X (Material 3's filter chips: an X is an input chip's "remove"; the web dropped
  its X too, 2026-11-01; one group at a time: tapping
  the chosen one again lets go) and no manage icon at the end (#307). In Feeds the feeds without a
  tag follow the tags in the same row, after a divider, as filter chips with their site icon
  (16dp). While a tag (or one of its feeds) is chosen, the **same row** shows only that tag (lit),
  then after the divider its feeds the same way (#338, owner's choice: one chip row, never two): tap
  one to show it alone (its tag stays lit), tap it again for the whole tag; tap the tag to let
  go, and every tag comes back. The web does the same (its row has the feeds without a tag too). Feeds' row only shows when a chip would narrow the list: not with a single
  chip (it would show what the list already shows), unless something is chosen or that chip is a tag
  with two or more feeds in the view (it leads to them). The chips sit right under the switch (their
  touch area is the gap). So every place is one or two taps from the list. **+** is the FAB, the screen's main action, the
  same in Feeds, Saved and the Inbox (it was in the search bar before, owner's choice): the Add sheet, two
  `surfaceContainerHigh` rows with their icon on a `secondaryContainer` circle, **Subscribe to feed**
  and **Save article**, each with a line saying what it does. **Mark all as read** is an outlined
  button (✓✓ and the words) under the last article, in Feeds' Unread view only, while something in
  the section shown is unread, and only marks that section: you've reached the end, you're done.
- Saved's lists look like what they are for: To read and All are read through, so they use
  the feed list's rows and its view setting (Small, Medium, Large), under a line with how many and
  about how long; a folder is a collection to browse, so its items are notes in Noter's style:
  `OutlinedCard`s on `surface` in a staggered grid (cells at least 160dp: two columns on a phone),
  the picture on top (96dp, cropped), the title (Title Medium, Medium weight, the reader's font, up to
  three lines), the first highlight (a `primary` quote mark and the reader's font in `onSurface`) or
  else the description (Body Medium, `onSurfaceVariant`), up to three lines either way; then your
  note, small (Label Medium, italic, `onSurfaceVariant`, a note icon, up to two lines); the tags as
  small chips (Label Small on `secondaryContainer`, small shape); then the site's icon and name, a
  marker icon with the highlight count and a heart when liked (#208).
  The reader's actions follow one rule (#139): the top bar is this reading (Back, the
  full article as an `IconToggleButton`, on while it shows, but not for a saved article, which
  always shows the whole article (#208), then Aa and ⋮); a **floating toolbar** (Material 3's
  floating toolbar, built from a `Surface` and a `Row` as Material 3 1.4 has none: a pill on
  `surfaceContainer`, 64dp high, 16dp above the bottom, in the middle) is this article in your
  library, with **Share** beside it as its own round button (`secondaryContainer`, 64dp: Material 3's
  floating toolbar with a FAB): a tonal **Save** before it is saved, then (2026-11-01, owner's choice,
  instead of Archive and Organise) first what moves it, in words: a tonal **Archive** (the Archive's
  box, styled like Save: the next step; one tap archives it, the article stays open and shows
  Archived with Undo, #331) while it is to read, or a plain **To read** (the open book: puts it back)
  once archived; then **Like** (an icon toggle, a heart filled while liked; its name on a long
  press as a tooltip). What describes a saved article sits under its title and details instead
  (`SavedHeader`, 2026-11-01): one row of chips, its tags as input chips with their icons, an assist
  chip **+ Tag** (the Tags sheet, tag chips only), **+ Note** while it has none (the Note sheet), and
  with highlights an assist chip with the quote mark and how many (their list); a note shows below
  as a `surfaceContainerHigh` block, tapped to edit. There is no Organise sheet any more; **Remove from Saved** is in ⋮. All its buttons are in one neutral colour (`onSurfaceVariant`, as Material 3's
  toolbars have it; the text buttons were `primary` before, so Share looked different), only the tonal
  Save stands out; the web's ghost buttons were already alike. It slides away while reading down and comes back scrolling up, at the top and
  at the end of the article; the text keeps room under its end, and messages show above it. The text ends
  with Read on website and nothing else. ⋮ holds the rare ones: Open in browser, Mark as unread,
  Sign in to this site (only while not signed in to it), Report a problem, Remove from Saved.
- Highlights (#102): **Highlight** and **Note** come first in the menu over selected text, then
  Copy, Share, Select all and the apps that act on text (#251; the web: a small bar above the
  selection). A highlight is a marker yellow (`#FFD54F` at 45 %)
  behind the text, lighter and underlined when only found roughly; tapping it opens a
  `ModalBottomSheet` with the quote (a `primary` bar at its left, italic), its status, the note
  field, Remove highlight, Cancel and Save. With highlights, the floating toolbar has a quote mark and the
  count before Share, which opens the list.
- Save is the action's one name (#142), in the reader's floating toolbar, the long-press menus and the sheet's button;
  "Read later" names nothing. After the sheet, a snackbar says "Saved to …", with **Open** (to the
  article in Saved) when you weren't in it already: after a long press, Add link, or a share from another
  app (where the snackbar is all of Reader that shows).
- One **Save and share** sheet (#143, #256), wherever you meet an article: a `ModalBottomSheet` with,
  in this order, what is being saved (only before the save: the address field for Add link, or a
  shared link's title and site; no list to pick since #305: the Archive is a state, chosen by the
  buttons), **Tags** in one
  row (Like, then every tag in use as a `FilterChip` without a check mark, in a steady order so
  nothing moves when one is chosen, then a New tag `AssistChip` that opens a field to make a new
  one), **Note** with the room, then, signed in, **Send with Content**:
  an email-style To field (#284): an outlined box with "To", the people picked as `InputChip`s
  (avatar, name, × to take them out) and a borderless field for a name or address; under it, up to
  five suggestions as `ListItem`s (avatar, name, address; tap adds), filtered by what is typed, and
  "Add …" for a whole new address (checked for a Content account first). Long-press a suggestion
  for trash buttons that forget people; Done ends it. Section titles in Title Small, `primary`.
  **Cancel** sits at the bottom left (`SheetActions`), in this sheet and every other one (Add feed,
  the tag sheet, a highlight, Report a problem; Remove, in the error colour, next to it), and the
  main button at the bottom right; the heading is only the title (`SheetHeader`). Above the tags a
  segmented **To read** / **Archive** choice (To read to start with when saving); **Save** (filled)
  shows the same icon (To read's open book or the Archive's box), and says **Save and send** or **Send** when
  sending: closing the sheet is Cancel, so only the button changes or sends
  anything; the heading is **Save** while saving. For an article saved before the same sheet opens
  as **Tags** (only the tag chips: the reader's Tags button, a row's long press) or **Note**
  (only the note); To read / Archive and Like are the reader's toggles. Right after a save
  Cancel (or closing the sheet) saves and sends nothing, so it takes the save back. Remove from Saved
  asks first in a dialog, and is only in the reader's ⋮: not in its toolbar nor in a row's long press
  (Archive, Tags, Share), so it stays rare and deliberate (2026-11-01); no Undo. Edit feed and the
  tag sheet have Remove as a trash can at the right end of the title row, asking first in a dialog. A
  folder or tag still being typed is taken by the button. One snackbar says where it went and who
  got it ("Saved to To read · Sent to …"). Other apps' share menus have three targets: Save and share
  (the same sheet), Send with Content (the sheet with only Send with Content,
  headed so) and Add feed (the Add feed dialog with the link). A person picked in Android's share
  menu opens Send with Content with them picked. After a save there, a tap anywhere but Open closes
  the message and goes back to the other app.
- Sheets and dialogs (#256): a bottom sheet for anything done with an article or that needs room
  (Save and share, Add feed with its tag chips (#307), Content's share sheet, highlights, Aa,
  Report a problem, the folder sheet with its icon search); a dialog only to confirm something or
  type one name (remove, rename, Add feed from the share menu); editing a feed is a sheet, like Add feed. No dialog opens on top of a sheet,
  except to confirm a removal (Remove feed?, Remove tag?, Remove from Saved?: the owner's choice,
  2026-11-01, a question pops up instead of sitting in the sheet's text), and no sheet on top of another (#323): a sheet opened from within one (Edit tags in Add feed, a
  feed or Add feed in Manage feeds, New tag in Manage tags) becomes a **page of the same sheet**,
  its header a back arrow and the title (`ContentSheet`, `SheetHeader`); the arrow or Back shows the
  sheet under it as it was. The web: the same in one dialog (Escape is Back there), and Manage feeds
  and Manage tags open as pages of the Settings dialog.
- Content's share sheet (#251): a `ModalBottomSheet` with the article's title and site, **Send with
  Content** the same To field as in the Save and share sheet, then a full-width Send button, then the apps as 48dp icons with their names,
  four to a row, Copy link and More last.
- Site icons sit on a fixed light tile (`#F4F4F4`, rounded, 10% padding) in light and dark mode,
  so logos drawn for light browser tabs keep their contrast; the letter fallback uses
  `tertiaryContainer`.
- Article list: three views (Small, Medium, Large card with a 16:9 picture). Unread rows sit on
  `surfaceContainer`, read rows on `surfaceContainerLow`; the feed name is in `primary`. Rows and
  cards don't swipe (#305): a long press holds their actions (Mark as read, Save, Share; in Saved
  Archive or Back to read first). The top bar shows the section and its unread count, with no ⋮ menu (refresh is pull-down; adding
  feeds lives on Manage feeds). Unread and All are the switch above the chip row (the chips: 16dp
  sides, 8dp above and below, like the chips in Search); the list doesn't swipe sideways. The web app has the same switch. The top bar holds only icon buttons, as Material 3's top app bar does.
  In a narrow window the web app has the same Feeds | Saved navigation bar at the bottom.
  Settings is the last button of the search bar (the account's picture or a cogwheel).
  The header (the search bar, the switch, then the chip row) is one block that slides away
  while scrolling down and comes back as soon as you scroll up (Material's "enter always"; the web
  app too since #323),
  staying on `surface` while articles pass under it (no grey band behind the tabs and chips, owner's
  choice); articles sit under sticky day headers (Today,
  Yesterday, date) in `primary` Title Small; Mark all as read is a button at the end of the list. The web
  app's sidebar and favicon use the app's logo (the launcher icon's C on its own: no circle or
  white tile).
- Settings starts with Manage feeds and Manage tags, then the Account page; Integrations holds OPML and Instapaper. The first screen,
  signed out, is a welcome with the email sign-in first. See docs/architecture.md, "Navigation and
  settings: where everything lives".

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
