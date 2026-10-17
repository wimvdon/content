/** The web app's text in the Android app's languages, with its shared wording (DESIGN.md). */

const en = {
  feeds: "Feeds",
  allArticles: "All articles",
  collapse: "Collapse",
  expand: "Expand",
  manageFeeds: "Manage feeds and folders",
  settings: "Settings",
  openFeeds: "Open feeds",
  refresh: "Refresh",
  markAllRead: "Mark all as read",
  filter: "Show",
  unread: "Unread",
  all: "All",
  noFeedsTitle: "No feeds yet",
  noFeedsText: "Add a website or feed address, or import your feeds from another reader (OPML).",
  addFeed: "Add feed",
  importOpml: "Import OPML",
  exportOpml: "Export OPML",
  caughtUpTitle: "All caught up",
  caughtUpText: "No unread articles here.",
  nothingHere: "No articles here yet.",
  noArticlesTitle: "No articles yet",
  showAll: "Show all",
  back: "Back",
  fullArticle: "Full article",
  markUnread: "Mark as unread",
  markRead: "Mark as read",
  share: "Share",
  openWebsite: "Open website",
  loadingFull: "Loading the full article…",
  fullFailed: "The full article couldn’t be loaded; this is the feed’s text.",
  readOnWebsite: "Read on website",
  linkCopied: "Link copied",
  addFeedHint: "A website or feed address, for example theverge.com.",
  close: "Close",
  cancel: "Cancel",
  add: "Add",
  adding: "Adding…",
  address: "Address",
  errorAlready: "You already follow this feed.",
  errorInvalid: "That isn’t a web address.",
  errorNoFeed: "No feed found on this website.",
  errorOffline: "No connection. Try again.",
  errorNotFound: "This page doesn’t exist.",
  errorServer: "The website didn’t answer properly. Try again later.",
  errorTooLarge: "This feed is too large.",
  errorNoProxy: "Set the feed loader address in Settings first.",
  newFolder: "New folder",
  editFolder: "Edit folder",
  noFolder: "No folder",
  moveUp: "Move up",
  moveDown: "Move down",
  edit: "Edit",
  folderEmpty: "No feeds in this folder yet.",
  noFolderEmpty: "Every feed is in a folder.",
  editFeed: "Edit feed",
  remove: "Remove",
  save: "Save",
  name: "Name",
  nameHint: "Clear it to use the feed’s own name.",
  folder: "Folder",
  title: "Title",
  icon: "Icon",
  searchIcons: "Search icons (in English)",
  sync: "Sync",
  syncSummary: "Same feeds and read articles on all your devices. Stored in a hidden folder in your Drive.",
  connect: "Connect",
  syncNow: "Sync now",
  disconnect: "Disconnect",
  syncing: "Syncing…",
  lastSynced: "Last synced {when}",
  notSyncedYet: "Not synced yet",
  syncOffline: "Couldn’t reach Google Drive. Check your connection.",
  syncNeedsApproval: "Google Drive needs your permission again. Click Connect.",
  syncServer: "Google Drive had a problem. Try again later.",
  syncUnreadable: "The sync file is from a newer version of RSS Reader. Reload the page.",
  syncNotConfigured: "Set the Google client ID below first.",
  reader: "Reader",
  fullArticles: "Load full articles",
  fullArticlesSummary: "When a feed only has a summary, load the article from its website.",
  appearance: "Appearance",
  theme: "Theme",
  themeSystem: "System",
  themeLight: "Light",
  themeDark: "Dark",
  connections: "Connections",
  proxyUrl: "Feed loader address",
  proxyHint: "Your Cloudflare Worker (see worker/README.md). Browsers can’t load most feeds directly.",
  googleClientId: "Google client ID",
  googleClientIdHint: "From your Google Cloud project, for Drive sync (see docs/sync.md).",
  privacy: "Privacy",
  importFailed: "This file couldn’t be read as OPML.",
  importNothingNew: "No new feeds in this file.",
  undo: "Undo",
  pickArticle: "Pick an article to read it here.",
  needsProxy: "To load feeds, set the feed loader address (your Cloudflare Worker) in Settings.",
  today: "Today",
  yesterday: "Yesterday",
};

export type Key = keyof typeof en;

const plurals_en = {
  unreadCount: ["{n} unread article", "{n} unread articles"],
  unreadCountAll: ["{n} unread", "{n} unread"],
  feedsFailed: ["{n} feed couldn’t be refreshed", "{n} feeds couldn’t be refreshed"],
  importAdded: ["{n} feed added", "{n} feeds added"],
  markedRead: ["{n} article marked as read", "{n} articles marked as read"],
};

type PluralKey = keyof typeof plurals_en;
type Pack = { text: Record<Key, string>; plurals: Record<PluralKey, [string, string]> };

const nl: Pack = {
  text: {
    feeds: "Feeds", allArticles: "Alle artikelen", collapse: "Inklappen", expand: "Uitklappen",
    manageFeeds: "Feeds en mappen beheren", settings: "Instellingen", openFeeds: "Feeds openen", refresh: "Vernieuwen",
    markAllRead: "Alles als gelezen markeren", filter: "Tonen", unread: "Ongelezen", all: "Alle",
    noFeedsTitle: "Nog geen feeds", noFeedsText: "Voeg een website of feedadres toe, of importeer je feeds uit een andere lezer (OPML).",
    addFeed: "Feed toevoegen", importOpml: "OPML importeren", exportOpml: "OPML exporteren",
    caughtUpTitle: "Helemaal bij", caughtUpText: "Geen ongelezen artikelen hier.", nothingHere: "Nog geen artikelen hier.", noArticlesTitle: "Nog geen artikelen", showAll: "Alles tonen",
    back: "Terug", fullArticle: "Volledig artikel", markUnread: "Als ongelezen markeren", markRead: "Als gelezen markeren",
    share: "Delen", openWebsite: "Website openen", loadingFull: "Volledig artikel laden…",
    fullFailed: "Het volledige artikel kon niet geladen worden; dit is de tekst uit de feed.", readOnWebsite: "Lezen op de website",
    linkCopied: "Link gekopieerd", addFeedHint: "Een website of feedadres, bijvoorbeeld nos.nl.", close: "Sluiten",
    cancel: "Annuleren", add: "Toevoegen", adding: "Toevoegen…", address: "Adres",
    errorAlready: "Je volgt deze feed al.", errorInvalid: "Dat is geen webadres.", errorNoFeed: "Geen feed gevonden op deze website.",
    errorOffline: "Geen verbinding. Probeer het opnieuw.", errorNotFound: "Deze pagina bestaat niet.",
    errorServer: "De website antwoordde niet goed. Probeer het later opnieuw.", errorTooLarge: "Deze feed is te groot.",
    errorNoProxy: "Stel eerst het adres van de feedlader in bij Instellingen.",
    newFolder: "Nieuwe map", editFolder: "Map bewerken", noFolder: "Geen map", moveUp: "Omhoog", moveDown: "Omlaag", edit: "Bewerken",
    folderEmpty: "Nog geen feeds in deze map.", noFolderEmpty: "Elke feed zit in een map.", editFeed: "Feed bewerken",
    remove: "Verwijderen", save: "Opslaan", name: "Naam", nameHint: "Maak leeg om de eigen naam van de feed te gebruiken.",
    folder: "Map", title: "Titel", icon: "Pictogram", searchIcons: "Pictogrammen zoeken (in het Engels)",
    sync: "Synchronisatie",
    syncSummary: "Dezelfde feeds en gelezen artikelen op al je apparaten. Bewaard in een verborgen map in je Drive.",
    connect: "Verbinden", syncNow: "Nu synchroniseren", disconnect: "Ontkoppelen", syncing: "Bezig met synchroniseren…",
    lastSynced: "Laatst gesynchroniseerd {when}", notSyncedYet: "Nog niet gesynchroniseerd",
    syncOffline: "Google Drive is niet bereikbaar. Controleer je verbinding.",
    syncNeedsApproval: "Google Drive heeft opnieuw je toestemming nodig. Klik op Verbinden.",
    syncServer: "Google Drive had een probleem. Probeer het later opnieuw.",
    syncUnreadable: "Het synchronisatiebestand komt van een nieuwere versie van RSS Reader. Laad de pagina opnieuw.",
    syncNotConfigured: "Vul eerst hieronder de Google-client-ID in.",
    reader: "Lezer", fullArticles: "Volledige artikelen laden",
    fullArticlesSummary: "Als een feed alleen een samenvatting heeft, het artikel van de website laden.",
    appearance: "Weergave", theme: "Thema", themeSystem: "Systeem", themeLight: "Licht", themeDark: "Donker",
    connections: "Verbindingen", proxyUrl: "Adres van de feedlader",
    proxyHint: "Je Cloudflare Worker (zie worker/README.md). Browsers kunnen de meeste feeds niet rechtstreeks laden.",
    googleClientId: "Google-client-ID", googleClientIdHint: "Uit je Google Cloud-project, voor synchronisatie met Drive (zie docs/sync.md).",
    privacy: "Privacy", importFailed: "Dit bestand kon niet als OPML gelezen worden.", importNothingNew: "Geen nieuwe feeds in dit bestand.",
    undo: "Ongedaan maken", pickArticle: "Kies een artikel om het hier te lezen.",
    needsProxy: "Stel het adres van de feedlader (je Cloudflare Worker) in bij Instellingen om feeds te laden.",
    today: "Vandaag", yesterday: "Gisteren",
  },
  plurals: {
    unreadCount: ["{n} ongelezen artikel", "{n} ongelezen artikelen"],
    unreadCountAll: ["{n} ongelezen", "{n} ongelezen"],
    feedsFailed: ["{n} feed kon niet vernieuwd worden", "{n} feeds konden niet vernieuwd worden"],
    importAdded: ["{n} feed toegevoegd", "{n} feeds toegevoegd"],
    markedRead: ["{n} artikel als gelezen gemarkeerd", "{n} artikelen als gelezen gemarkeerd"],
  },
};

const fr: Pack = {
  text: {
    feeds: "Flux", allArticles: "Tous les articles", collapse: "Réduire", expand: "Développer",
    manageFeeds: "Gérer les flux et dossiers", settings: "Paramètres", openFeeds: "Ouvrir les flux", refresh: "Actualiser",
    markAllRead: "Tout marquer comme lu", filter: "Afficher", unread: "Non lus", all: "Tous",
    noFeedsTitle: "Pas encore de flux", noFeedsText: "Ajoutez un site ou une adresse de flux, ou importez vos flux d’un autre lecteur (OPML).",
    addFeed: "Ajouter un flux", importOpml: "Importer OPML", exportOpml: "Exporter OPML",
    caughtUpTitle: "Vous êtes à jour", caughtUpText: "Aucun article non lu ici.", nothingHere: "Pas encore d’articles ici.", noArticlesTitle: "Pas encore d’articles", showAll: "Tout afficher",
    back: "Retour", fullArticle: "Article complet", markUnread: "Marquer comme non lu", markRead: "Marquer comme lu",
    share: "Partager", openWebsite: "Ouvrir le site", loadingFull: "Chargement de l’article complet…",
    fullFailed: "L’article complet n’a pas pu être chargé ; voici le texte du flux.", readOnWebsite: "Lire sur le site",
    linkCopied: "Lien copié", addFeedHint: "Un site ou une adresse de flux, par exemple lemonde.fr.", close: "Fermer",
    cancel: "Annuler", add: "Ajouter", adding: "Ajout…", address: "Adresse",
    errorAlready: "Vous suivez déjà ce flux.", errorInvalid: "Ce n’est pas une adresse web.", errorNoFeed: "Aucun flux trouvé sur ce site.",
    errorOffline: "Pas de connexion. Réessayez.", errorNotFound: "Cette page n’existe pas.",
    errorServer: "Le site n’a pas répondu correctement. Réessayez plus tard.", errorTooLarge: "Ce flux est trop volumineux.",
    errorNoProxy: "Indiquez d’abord l’adresse du chargeur de flux dans les Paramètres.",
    newFolder: "Nouveau dossier", editFolder: "Modifier le dossier", noFolder: "Aucun dossier", moveUp: "Monter", moveDown: "Descendre", edit: "Modifier",
    folderEmpty: "Pas encore de flux dans ce dossier.", noFolderEmpty: "Chaque flux est dans un dossier.", editFeed: "Modifier le flux",
    remove: "Supprimer", save: "Enregistrer", name: "Nom", nameHint: "Videz-le pour utiliser le nom du flux.",
    folder: "Dossier", title: "Titre", icon: "Icône", searchIcons: "Chercher une icône (en anglais)",
    sync: "Synchronisation",
    syncSummary: "Les mêmes flux et articles lus sur tous vos appareils. Stockés dans un dossier masqué de votre Drive.",
    connect: "Connecter", syncNow: "Synchroniser", disconnect: "Déconnecter", syncing: "Synchronisation…",
    lastSynced: "Dernière synchronisation {when}", notSyncedYet: "Pas encore synchronisé",
    syncOffline: "Impossible de joindre Google Drive. Vérifiez votre connexion.",
    syncNeedsApproval: "Google Drive a de nouveau besoin de votre autorisation. Cliquez sur Connecter.",
    syncServer: "Google Drive a rencontré un problème. Réessayez plus tard.",
    syncUnreadable: "Le fichier de synchronisation vient d’une version plus récente de RSS Reader. Rechargez la page.",
    syncNotConfigured: "Indiquez d’abord l’ID client Google ci-dessous.",
    reader: "Lecteur", fullArticles: "Charger les articles complets",
    fullArticlesSummary: "Quand un flux n’a qu’un résumé, charger l’article depuis son site.",
    appearance: "Apparence", theme: "Thème", themeSystem: "Système", themeLight: "Clair", themeDark: "Sombre",
    connections: "Connexions", proxyUrl: "Adresse du chargeur de flux",
    proxyHint: "Votre Cloudflare Worker (voir worker/README.md). Les navigateurs ne peuvent pas charger la plupart des flux directement.",
    googleClientId: "ID client Google", googleClientIdHint: "De votre projet Google Cloud, pour la synchronisation Drive (voir docs/sync.md).",
    privacy: "Confidentialité", importFailed: "Ce fichier n’a pas pu être lu comme OPML.", importNothingNew: "Aucun nouveau flux dans ce fichier.",
    undo: "Annuler", pickArticle: "Choisissez un article pour le lire ici.",
    needsProxy: "Pour charger les flux, indiquez l’adresse du chargeur de flux (votre Cloudflare Worker) dans les Paramètres.",
    today: "Aujourd’hui", yesterday: "Hier",
  },
  plurals: {
    unreadCount: ["{n} article non lu", "{n} articles non lus"],
    unreadCountAll: ["{n} non lu", "{n} non lus"],
    feedsFailed: ["{n} flux n’a pas pu être actualisé", "{n} flux n’ont pas pu être actualisés"],
    importAdded: ["{n} flux ajouté", "{n} flux ajoutés"],
    markedRead: ["{n} article marqué comme lu", "{n} articles marqués comme lus"],
  },
};

const de: Pack = {
  text: {
    feeds: "Feeds", allArticles: "Alle Artikel", collapse: "Einklappen", expand: "Ausklappen",
    manageFeeds: "Feeds und Ordner verwalten", settings: "Einstellungen", openFeeds: "Feeds öffnen", refresh: "Aktualisieren",
    markAllRead: "Alle als gelesen markieren", filter: "Anzeigen", unread: "Ungelesen", all: "Alle",
    noFeedsTitle: "Noch keine Feeds", noFeedsText: "Füge eine Website oder Feed-Adresse hinzu oder importiere deine Feeds aus einem anderen Reader (OPML).",
    addFeed: "Feed hinzufügen", importOpml: "OPML importieren", exportOpml: "OPML exportieren",
    caughtUpTitle: "Alles gelesen", caughtUpText: "Keine ungelesenen Artikel hier.", nothingHere: "Noch keine Artikel hier.", noArticlesTitle: "Noch keine Artikel", showAll: "Alle anzeigen",
    back: "Zurück", fullArticle: "Ganzer Artikel", markUnread: "Als ungelesen markieren", markRead: "Als gelesen markieren",
    share: "Teilen", openWebsite: "Website öffnen", loadingFull: "Ganzer Artikel wird geladen…",
    fullFailed: "Der ganze Artikel konnte nicht geladen werden; das ist der Text aus dem Feed.", readOnWebsite: "Auf der Website lesen",
    linkCopied: "Link kopiert", addFeedHint: "Eine Website oder Feed-Adresse, zum Beispiel heise.de.", close: "Schließen",
    cancel: "Abbrechen", add: "Hinzufügen", adding: "Wird hinzugefügt…", address: "Adresse",
    errorAlready: "Du folgst diesem Feed schon.", errorInvalid: "Das ist keine Webadresse.", errorNoFeed: "Auf dieser Website wurde kein Feed gefunden.",
    errorOffline: "Keine Verbindung. Versuche es noch einmal.", errorNotFound: "Diese Seite gibt es nicht.",
    errorServer: "Die Website hat nicht richtig geantwortet. Versuche es später noch einmal.", errorTooLarge: "Dieser Feed ist zu groß.",
    errorNoProxy: "Gib zuerst in den Einstellungen die Adresse des Feed-Laders ein.",
    newFolder: "Neuer Ordner", editFolder: "Ordner bearbeiten", noFolder: "Kein Ordner", moveUp: "Nach oben", moveDown: "Nach unten", edit: "Bearbeiten",
    folderEmpty: "Noch keine Feeds in diesem Ordner.", noFolderEmpty: "Jeder Feed ist in einem Ordner.", editFeed: "Feed bearbeiten",
    remove: "Entfernen", save: "Speichern", name: "Name", nameHint: "Leeren, um den eigenen Namen des Feeds zu verwenden.",
    folder: "Ordner", title: "Titel", icon: "Symbol", searchIcons: "Symbole suchen (auf Englisch)",
    sync: "Synchronisierung",
    syncSummary: "Gleiche Feeds und gelesene Artikel auf allen Geräten. Gespeichert in einem versteckten Ordner deines Drive.",
    connect: "Verbinden", syncNow: "Jetzt synchronisieren", disconnect: "Trennen", syncing: "Wird synchronisiert…",
    lastSynced: "Zuletzt synchronisiert {when}", notSyncedYet: "Noch nicht synchronisiert",
    syncOffline: "Google Drive ist nicht erreichbar. Prüfe deine Verbindung.",
    syncNeedsApproval: "Google Drive braucht erneut deine Erlaubnis. Klicke auf Verbinden.",
    syncServer: "Google Drive hatte ein Problem. Versuche es später noch einmal.",
    syncUnreadable: "Die Sync-Datei stammt von einer neueren Version von RSS Reader. Lade die Seite neu.",
    syncNotConfigured: "Gib zuerst unten die Google-Client-ID ein.",
    reader: "Reader", fullArticles: "Ganze Artikel laden",
    fullArticlesSummary: "Wenn ein Feed nur eine Zusammenfassung hat, den Artikel von der Website laden.",
    appearance: "Darstellung", theme: "Design", themeSystem: "System", themeLight: "Hell", themeDark: "Dunkel",
    connections: "Verbindungen", proxyUrl: "Adresse des Feed-Laders",
    proxyHint: "Dein Cloudflare Worker (siehe worker/README.md). Browser können die meisten Feeds nicht direkt laden.",
    googleClientId: "Google-Client-ID", googleClientIdHint: "Aus deinem Google-Cloud-Projekt, für die Drive-Synchronisierung (siehe docs/sync.md).",
    privacy: "Datenschutz", importFailed: "Diese Datei konnte nicht als OPML gelesen werden.", importNothingNew: "Keine neuen Feeds in dieser Datei.",
    undo: "Rückgängig", pickArticle: "Wähle einen Artikel, um ihn hier zu lesen.",
    needsProxy: "Um Feeds zu laden, gib in den Einstellungen die Adresse des Feed-Laders (deinen Cloudflare Worker) ein.",
    today: "Heute", yesterday: "Gestern",
  },
  plurals: {
    unreadCount: ["{n} ungelesener Artikel", "{n} ungelesene Artikel"],
    unreadCountAll: ["{n} ungelesen", "{n} ungelesen"],
    feedsFailed: ["{n} Feed konnte nicht aktualisiert werden", "{n} Feeds konnten nicht aktualisiert werden"],
    importAdded: ["{n} Feed hinzugefügt", "{n} Feeds hinzugefügt"],
    markedRead: ["{n} Artikel als gelesen markiert", "{n} Artikel als gelesen markiert"],
  },
};

const es: Pack = {
  text: {
    feeds: "Feeds", allArticles: "Todos los artículos", collapse: "Contraer", expand: "Expandir",
    manageFeeds: "Gestionar feeds y carpetas", settings: "Ajustes", openFeeds: "Abrir feeds", refresh: "Actualizar",
    markAllRead: "Marcar todo como leído", filter: "Mostrar", unread: "No leídos", all: "Todos",
    noFeedsTitle: "Aún no hay feeds", noFeedsText: "Añade un sitio web o una dirección de feed, o importa tus feeds de otro lector (OPML).",
    addFeed: "Añadir feed", importOpml: "Importar OPML", exportOpml: "Exportar OPML",
    caughtUpTitle: "Estás al día", caughtUpText: "No hay artículos sin leer aquí.", nothingHere: "Aún no hay artículos aquí.", noArticlesTitle: "Aún no hay artículos", showAll: "Mostrar todo",
    back: "Atrás", fullArticle: "Artículo completo", markUnread: "Marcar como no leído", markRead: "Marcar como leído",
    share: "Compartir", openWebsite: "Abrir sitio web", loadingFull: "Cargando el artículo completo…",
    fullFailed: "No se pudo cargar el artículo completo; este es el texto del feed.", readOnWebsite: "Leer en el sitio web",
    linkCopied: "Enlace copiado", addFeedHint: "Un sitio web o dirección de feed, por ejemplo elpais.com.", close: "Cerrar",
    cancel: "Cancelar", add: "Añadir", adding: "Añadiendo…", address: "Dirección",
    errorAlready: "Ya sigues este feed.", errorInvalid: "Eso no es una dirección web.", errorNoFeed: "No se encontró ningún feed en este sitio.",
    errorOffline: "Sin conexión. Inténtalo de nuevo.", errorNotFound: "Esta página no existe.",
    errorServer: "El sitio no respondió bien. Inténtalo más tarde.", errorTooLarge: "Este feed es demasiado grande.",
    errorNoProxy: "Primero indica la dirección del cargador de feeds en Ajustes.",
    newFolder: "Nueva carpeta", editFolder: "Editar carpeta", noFolder: "Sin carpeta", moveUp: "Subir", moveDown: "Bajar", edit: "Editar",
    folderEmpty: "Aún no hay feeds en esta carpeta.", noFolderEmpty: "Todos los feeds están en una carpeta.", editFeed: "Editar feed",
    remove: "Eliminar", save: "Guardar", name: "Nombre", nameHint: "Déjalo vacío para usar el nombre del feed.",
    folder: "Carpeta", title: "Título", icon: "Icono", searchIcons: "Buscar iconos (en inglés)",
    sync: "Sincronización",
    syncSummary: "Los mismos feeds y artículos leídos en todos tus dispositivos. Se guardan en una carpeta oculta de tu Drive.",
    connect: "Conectar", syncNow: "Sincronizar ahora", disconnect: "Desconectar", syncing: "Sincronizando…",
    lastSynced: "Última sincronización {when}", notSyncedYet: "Aún sin sincronizar",
    syncOffline: "No se pudo acceder a Google Drive. Revisa tu conexión.",
    syncNeedsApproval: "Google Drive necesita tu permiso de nuevo. Haz clic en Conectar.",
    syncServer: "Google Drive tuvo un problema. Inténtalo más tarde.",
    syncUnreadable: "El archivo de sincronización es de una versión más reciente de RSS Reader. Recarga la página.",
    syncNotConfigured: "Primero indica abajo el ID de cliente de Google.",
    reader: "Lector", fullArticles: "Cargar artículos completos",
    fullArticlesSummary: "Si un feed solo tiene un resumen, cargar el artículo desde su sitio web.",
    appearance: "Apariencia", theme: "Tema", themeSystem: "Sistema", themeLight: "Claro", themeDark: "Oscuro",
    connections: "Conexiones", proxyUrl: "Dirección del cargador de feeds",
    proxyHint: "Tu Cloudflare Worker (ver worker/README.md). Los navegadores no pueden cargar la mayoría de los feeds directamente.",
    googleClientId: "ID de cliente de Google", googleClientIdHint: "De tu proyecto de Google Cloud, para sincronizar con Drive (ver docs/sync.md).",
    privacy: "Privacidad", importFailed: "No se pudo leer este archivo como OPML.", importNothingNew: "No hay feeds nuevos en este archivo.",
    undo: "Deshacer", pickArticle: "Elige un artículo para leerlo aquí.",
    needsProxy: "Para cargar feeds, indica la dirección del cargador de feeds (tu Cloudflare Worker) en Ajustes.",
    today: "Hoy", yesterday: "Ayer",
  },
  plurals: {
    unreadCount: ["{n} artículo sin leer", "{n} artículos sin leer"],
    unreadCountAll: ["{n} sin leer", "{n} sin leer"],
    feedsFailed: ["No se pudo actualizar {n} feed", "No se pudieron actualizar {n} feeds"],
    importAdded: ["{n} feed añadido", "{n} feeds añadidos"],
    markedRead: ["{n} artículo marcado como leído", "{n} artículos marcados como leídos"],
  },
};

const it: Pack = {
  text: {
    feeds: "Feed", allArticles: "Tutti gli articoli", collapse: "Comprimi", expand: "Espandi",
    manageFeeds: "Gestisci feed e cartelle", settings: "Impostazioni", openFeeds: "Apri i feed", refresh: "Aggiorna",
    markAllRead: "Segna tutto come letto", filter: "Mostra", unread: "Non letti", all: "Tutti",
    noFeedsTitle: "Ancora nessun feed", noFeedsText: "Aggiungi un sito o un indirizzo di feed, oppure importa i tuoi feed da un altro lettore (OPML).",
    addFeed: "Aggiungi feed", importOpml: "Importa OPML", exportOpml: "Esporta OPML",
    caughtUpTitle: "Sei in pari", caughtUpText: "Nessun articolo non letto qui.", nothingHere: "Ancora nessun articolo qui.", noArticlesTitle: "Ancora nessun articolo", showAll: "Mostra tutto",
    back: "Indietro", fullArticle: "Articolo completo", markUnread: "Segna come non letto", markRead: "Segna come letto",
    share: "Condividi", openWebsite: "Apri il sito", loadingFull: "Caricamento dell’articolo completo…",
    fullFailed: "Impossibile caricare l’articolo completo; questo è il testo del feed.", readOnWebsite: "Leggi sul sito",
    linkCopied: "Link copiato", addFeedHint: "Un sito o un indirizzo di feed, per esempio ilpost.it.", close: "Chiudi",
    cancel: "Annulla", add: "Aggiungi", adding: "Aggiunta…", address: "Indirizzo",
    errorAlready: "Segui già questo feed.", errorInvalid: "Non è un indirizzo web.", errorNoFeed: "Nessun feed trovato su questo sito.",
    errorOffline: "Nessuna connessione. Riprova.", errorNotFound: "Questa pagina non esiste.",
    errorServer: "Il sito non ha risposto correttamente. Riprova più tardi.", errorTooLarge: "Questo feed è troppo grande.",
    errorNoProxy: "Imposta prima l’indirizzo del caricatore di feed nelle Impostazioni.",
    newFolder: "Nuova cartella", editFolder: "Modifica cartella", noFolder: "Nessuna cartella", moveUp: "Sposta su", moveDown: "Sposta giù", edit: "Modifica",
    folderEmpty: "Ancora nessun feed in questa cartella.", noFolderEmpty: "Ogni feed è in una cartella.", editFeed: "Modifica feed",
    remove: "Rimuovi", save: "Salva", name: "Nome", nameHint: "Svuotalo per usare il nome del feed.",
    folder: "Cartella", title: "Titolo", icon: "Icona", searchIcons: "Cerca icone (in inglese)",
    sync: "Sincronizzazione",
    syncSummary: "Gli stessi feed e articoli letti su tutti i tuoi dispositivi. Salvati in una cartella nascosta del tuo Drive.",
    connect: "Connetti", syncNow: "Sincronizza ora", disconnect: "Disconnetti", syncing: "Sincronizzazione…",
    lastSynced: "Ultima sincronizzazione {when}", notSyncedYet: "Non ancora sincronizzato",
    syncOffline: "Impossibile raggiungere Google Drive. Controlla la connessione.",
    syncNeedsApproval: "Google Drive ha di nuovo bisogno del tuo permesso. Fai clic su Connetti.",
    syncServer: "Google Drive ha avuto un problema. Riprova più tardi.",
    syncUnreadable: "Il file di sincronizzazione viene da una versione più recente di RSS Reader. Ricarica la pagina.",
    syncNotConfigured: "Inserisci prima qui sotto l’ID client di Google.",
    reader: "Lettore", fullArticles: "Carica articoli completi",
    fullArticlesSummary: "Se un feed ha solo un riassunto, carica l’articolo dal suo sito.",
    appearance: "Aspetto", theme: "Tema", themeSystem: "Sistema", themeLight: "Chiaro", themeDark: "Scuro",
    connections: "Connessioni", proxyUrl: "Indirizzo del caricatore di feed",
    proxyHint: "Il tuo Cloudflare Worker (vedi worker/README.md). I browser non possono caricare direttamente la maggior parte dei feed.",
    googleClientId: "ID client di Google", googleClientIdHint: "Dal tuo progetto Google Cloud, per la sincronizzazione con Drive (vedi docs/sync.md).",
    privacy: "Privacy", importFailed: "Impossibile leggere questo file come OPML.", importNothingNew: "Nessun nuovo feed in questo file.",
    undo: "Annulla", pickArticle: "Scegli un articolo per leggerlo qui.",
    needsProxy: "Per caricare i feed, imposta l’indirizzo del caricatore di feed (il tuo Cloudflare Worker) nelle Impostazioni.",
    today: "Oggi", yesterday: "Ieri",
  },
  plurals: {
    unreadCount: ["{n} articolo non letto", "{n} articoli non letti"],
    unreadCountAll: ["{n} non letto", "{n} non letti"],
    feedsFailed: ["Impossibile aggiornare {n} feed", "Impossibile aggiornare {n} feed"],
    importAdded: ["{n} feed aggiunto", "{n} feed aggiunti"],
    markedRead: ["{n} articolo segnato come letto", "{n} articoli segnati come letti"],
  },
};

const packs: Record<string, Pack> = { en: { text: en, plurals: plurals_en as Pack["plurals"] }, nl, fr, de, es, it };

function pickLanguage(): string {
  for (const lang of navigator.languages ?? [navigator.language]) {
    const code = lang.slice(0, 2).toLowerCase();
    if (packs[code]) return code;
  }
  return "en";
}

export const language = typeof navigator === "undefined" ? "en" : pickLanguage();
const pack = packs[language];

export function t(key: Key): string {
  return pack.text[key] ?? en[key];
}

export function plural(key: PluralKey, n: number): string {
  const [one, other] = pack.plurals[key];
  return (new Intl.PluralRules(language).select(n) === "one" ? one : other).replace("{n}", n.toLocaleString(language));
}

/** "Today", "Yesterday" or the date, for a yyyy-mm-dd day. */
export function dayLabel(day: string): string {
  const [y, m, d] = day.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = Math.round((today.getTime() - date.getTime()) / 86_400_000);
  if (diff === 0) return t("today");
  if (diff === 1) return t("yesterday");
  return date.toLocaleDateString(language, { weekday: "long", day: "numeric", month: "long", year: date.getFullYear() === today.getFullYear() ? undefined : "numeric" });
}

export function formatDateTime(ms: number): string {
  return new Date(ms).toLocaleString(language, { dateStyle: "medium", timeStyle: "short" });
}

export function formatRelative(ms: number): string {
  const minutes = Math.round((ms - Date.now()) / 60_000);
  const rtf = new Intl.RelativeTimeFormat(language, { numeric: "auto" });
  if (Math.abs(minutes) < 60) return rtf.format(minutes, "minute");
  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) return rtf.format(hours, "hour");
  return rtf.format(Math.round(hours / 24), "day");
}
