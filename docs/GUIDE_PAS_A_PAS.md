# Guide pas à pas — TP Contacts V4

Ce guide suit l'ordre dans lequel on **construit** l'application. À chaque étape : l'objectif, les
fichiers concernés, **pourquoi** on fait ainsi (c'est ce que l'enseignant attend dans le compte rendu),
et un test rapide pour valider avant de passer à la suite.

> Règle d'or : ne passez à l'étape suivante que quand le test rapide de l'étape courante passe.

---

## Étape 0 — Préparer le projet

**Objectif** : un projet qui compile, avec les bonnes dépendances.

- `app/build.gradle.kts` : `viewBinding = true`, et les dépendances AndroidX (fragment, recyclerview,
  **preference-ktx**, lifecycle/viewmodel), coroutines, `play-services-maps`.
- **Clé Maps** : lue dans `local.properties` (ignoré par Git) et injectée dans le manifeste par
  `manifestPlaceholders`. Le dépôt ne contient donc **jamais** la clé.
- `AndroidManifest.xml` : `INTERNET` (pour Maps), `FileProvider` (pour la caméra), **pas** de permission
  `CALL_PHONE` ni `CAMERA`.
- Thème **clair uniquement** (`Theme.Material3.Light.NoActionBar`, pas de `values-night`) : ainsi les 5 couleurs
  de texte restent lisibles (le sujet demande de vérifier la lisibilité).

**Test rapide** : *Sync Project* puis *Run* → écran vide avec une barre d'application.

> Vous avez déjà votre V3 ? Gardez vos fichiers de la V3 pour `MainActivity`, `CarteContactFragment`
> et les actions Appeler / E-mail, et reprenez de ce dépôt tout le dossier `data/`, `prefs/`,
> `ContactViewModel` et les parties « base de données » des fragments. Le sujet impose que le contact
> soit identifié par son `_id` partout : remplacez tout usage de la *position* dans la liste.

---

## Étape 1 — Le modèle et le contrat (`data/Contact.kt`, `data/ContactContract.kt`)

**Objectif** : décrire une ligne de la table en Kotlin, et centraliser les noms.

- `Contact` : `id: Long = 0` (0 = pas encore enregistré), `email`, `photoUri`, `latitude`, `longitude` nullables.
- `ContactContract` : `TABLE_NAME`, `COL_NOM`, … en constantes. **Aucun nom de colonne écrit en dur ailleurs**
  (une faute de frappe dans une chaîne ne se voit qu'à l'exécution ; avec une constante, le compilateur la voit).
- Le téléphone est du **TEXT** : il peut commencer par 0 ou « +216 » et contenir des espaces ; en INTEGER, le 0 initial disparaîtrait.
- Position absente = **NULL dans les deux colonnes** → `Double?`.

**Test rapide** : le projet compile.

---

## Étape 2 — Le helper (`data/ContactDbHelper.kt`)

**Objectif** : créer le fichier `contacts.db`, et le faire évoluer.

- `onCreate` : `CREATE TABLE …` puis insertion des **6 contacts de démo** (dont 2 sans GPS, 1 avec apostrophe « O'Neil »).
  Elle ne s'exécute **que si le fichier n'existe pas** → les contacts de démo ne sont donc jamais insérés deux fois.
- `onUpgrade` : appelée quand `DATABASE_VERSION` augmente. Elle contient un exemple commenté de migration (bonus).
- **Singleton** (`getInstance`) construit avec `applicationContext` : une seule connexion à la base pour tout le
  processus, et aucune `Activity` retenue en mémoire (fuite).

**Piège classique** : vous modifiez le schéma, relancez… et rien ne change, ou « no such column ».
`onCreate` n'est plus appelée car le fichier existe déjà. Solution : désinstaller l'app, *ou* incrémenter
`DATABASE_VERSION` et écrire `onUpgrade`.

**Test rapide** : lancer l'app, ouvrir *App Inspection → Database Inspector* : la table `contacts` contient 6 lignes.

---

## Étape 3 — Le DAO (`data/ContactDao.kt`)

**Objectif** : la seule classe qui parle SQL.

| Méthode | Ce qu'elle fait | Valeur renvoyée |
|---|---|---|
| `insert(c)` | `db.insert(...)` | nouvel `_id`, ou **-1** si échec |
| `update(c)` | `db.update(..., "_id = ?", arrayOf(id))` | nombre de lignes modifiées (**0** = `_id` inconnu) |
| `delete(id)` | `db.delete(..., "_id = ?", ...)` | nombre de lignes supprimées |
| `getAll()` | `ORDER BY nom COLLATE NOCASE, prenom COLLATE NOCASE` | liste triée |
| `getById(id)` | `WHERE _id = ?` | `Contact?` |
| `search(texte)` | `LIKE ?` sur nom, prénom, téléphone | liste triée |

Points d'attention (chacun est dans le barème « requêtes paramétrées ») :

1. **Jamais de concaténation** : `"... WHERE nom = '" + saisie + "'"` casse avec « O'Neil » (et permet l'injection SQL).
   On écrit `"nom LIKE ?"` + `arrayOf(motif)`.
2. Chaque `Cursor` est refermé par **`use { }`** ; les index viennent de **`getColumnIndexOrThrow()`**
   (une faute de nom lève une exception claire au lieu de renvoyer -1 silencieusement).
3. `getString()` sur une colonne NULL ne renvoie pas `null` : on teste `isNull()` pour conserver les `null` (email, photo, GPS).
4. Dans `search`, les `%` et `_` tapés par l'utilisateur sont **échappés** (`ESCAPE '\'`), sinon taper « % » afficherait tout.

**Test rapide** : lancer `ContactDaoTest` (clic droit → Run) : 8 tests verts.

---

## Étape 4 — Le ViewModel et les threads (`ui/ContactViewModel.kt`)

**Objectif** : aucun accès disque sur le thread principal.

Le schéma est toujours le même :

```kotlin
viewModelScope.launch {                       // démarre sur le thread principal
    val liste = withContext(Dispatchers.IO) { // la requête tourne sur un thread d'arrière-plan
        dao.search(filtre)
    }
    _contacts.value = liste                   // retour sur le thread principal pour mettre à jour l'UI
}
```

- **Pourquoi ?** Une requête SQLite est une lecture/écriture disque : sur le thread principal elle fige l'écran
  (écran qui saccade, voire « L'application ne répond pas » après 5 s).
- Le ViewModel est **partagé** (`activityViewModels()`), donc commun aux fragments, et il **survit à la rotation**.
- Après chaque écriture, il recharge la liste : la liste observe `contacts` (LiveData) et se met à jour toute seule.
- Une frappe plus récente **annule** la requête de filtrage précédente (`listJob?.cancel()`).
- Les erreurs (insertion échouée, contact introuvable…) arrivent à l'écran par un canal d'événements (`UiEvent`) sous forme de messages lisibles.

**Test rapide** : ajouter temporairement `StrictMode.enableDefaults()` dans une `Application` : aucun avertissement « disk read/write on main thread ».

---

## Étape 5 — La liste (`ListeContactsFragment`, `ContactAdapter`, `item_contact.xml`)

**Objectif** : afficher la liste triée depuis SQLite, avec filtre et bouton « + ».

- `ListAdapter` + `DiffUtil` (`areItemsTheSame` compare les **`_id`**) : seules les lignes modifiées sont redessinées.
- Au clic : on passe **`contact.id`** à `DetailContactFragment.newInstance(id)` (Bundle). Jamais la position.
- Le champ de recherche appelle `viewModel.setQuery(texte)` → `dao.search(texte)`.
- `onResume()` : relit les préférences **et** recharge la liste → « chargée depuis la base à chaque affichage ».
- Anti double-clic (600 ms) pour ne pas empiler deux fiches.

**Choix de filtrage à justifier** : ici filtrage **en SQL** (`LIKE`). Raisons et limite : voir `COMPTE_RENDU.md`.

**Test rapide** : taper « ne » filtre ; taper `'` ne produit aucune erreur ; vider le champ réaffiche tout.

---

## Étape 6 — Le détail : Create / Update / Delete (`DetailContactFragment`)

**Objectif** : un seul écran pour créer, consulter, modifier, supprimer.

- `contactId == 0` ⇒ **création** (champs vides, boutons Supprimer et Carte masqués). Sinon on charge le contact par son `_id`.
- **Validation avant écriture** (`util/ContactValidator.kt`) : nom, prénom, téléphone obligatoires ; e-mail bien formé ;
  GPS = deux champs vides **ou** deux nombres dans les limites (latitude −90…90, longitude −180…180).
  Si une erreur existe, **aucun `Contact` n'est produit**, donc rien n'est écrit. Les erreurs s'affichent sous chaque champ.
  (`"NaN".toDoubleOrNull()` renvoie une valeur ! d'où le test `isFinite()`.)
- **Annuler** : `popBackStack()` sans rien enregistrer.
- **Supprimer** : `ConfirmDeleteDialogFragment` → si « Supprimer » : `viewModel.delete(id)` → retour à la liste.
  Un `DialogFragment` (et non un `AlertDialog` lancé à la main) survit à la rotation.
- Les valeurs renvoyées par le DAO sont exploitées : `-1` → « Échec de l'enregistrement » ; `0` → « Contact introuvable ».
- Bouton désactivé pendant l'écriture : un double clic ne crée pas deux contacts.

**Test rapide** : créer « L'Ariana » → apparaît trié dans la liste ; modifier ; supprimer avec confirmation ;
**fermer complètement l'app** (glisser hors des récents) et relancer : tout est conservé.

---

## Étape 7 — Les photos (`util/PhotoStorage.kt`, `util/PhotoLoader.kt`)

**Objectif** : une photo qui survit au redémarrage.

- Une URI de galerie (`content://…`) est **temporaire**. On **copie** l'image dans `filesDir/photos/` et on stocke le **chemin** de la copie.
- La copie est réduite (≤ 1024 px), l'orientation EXIF est appliquée (sinon les photos de la caméra apparaissent couchées), puis écrite en JPEG.
- Galerie : sélecteur de photos du système (`PickVisualMedia`, **aucune permission**). Caméra : `TakePicture` + `FileProvider`.
  Annulation et application absente sont gérées (`ActivityNotFoundException`, résultat `false` / `null`).
- L'affichage décode la miniature sur `Dispatchers.IO` avec un petit cache (défilement fluide).
- Nettoyage : l'ancienne photo est supprimée au remplacement ; la photo en attente est supprimée si on quitte la fiche sans enregistrer.

**Test rapide** : choisir une photo, enregistrer, redémarrer l'appareil/émulateur : la photo est toujours là.

---

## Étape 8 — Les préférences (`res/xml/preferences.xml`, `ParametresFragment`, `PreferencesManager`)

**Objectif** : trois réglages clé/valeur, stockés dans les **SharedPreferences** (pas dans SQLite).

1. `res/values/arrays.xml` : libellés et valeurs (`14/16/20` ; `#000000`, `#1565C0`…).
2. `res/xml/preferences.xml` : 2 `ListPreference` (clés `taille_police`, `couleur_preferee`) + 1 `SwitchPreferenceCompat`
   (`appel_depuis_liste`), avec `app:useSimpleSummaryProvider="true"` → le résumé affiche la valeur choisie.
3. `ParametresFragment : PreferenceFragmentCompat` : une seule ligne utile, `setPreferencesFromResource`.
   **Il écrit automatiquement** chaque changement dans les SharedPreferences : pas de bouton « Enregistrer ».
4. `PreferencesManager.read()` : **seul endroit** qui connaît les clés ; il convertit en `Float` (sp), `Int` (couleur), `Boolean`.
   L'adapter ne reçoit que ces valeurs converties.
5. Accès : menu « Paramètres » de la barre d'application (`MainActivity`), `replace()` + `addToBackStack()`.

**Application immédiate** : au retour à la liste, `onResume()` appelle `adapter.updateDisplay(preferences.read())`
qui fait `notifyItemRangeChanged` → les lignes se redessinent **sans redémarrer**.
(Alternative acceptée par le sujet : `OnSharedPreferenceChangeListener`.)

**Test rapide** : Paramètres → Grande + Rouge → Retour : nom et prénom grands et rouges ; relancer l'app : réglages conservés.

---

## Étape 9 — Le bouton Appeler dans la liste

- `item_contact.xml` : `ImageButton` `btn_appeler`, `visibility="gone"` par défaut.
- Dans `onBindViewHolder` : `VISIBLE` si la préférence est active, sinon `GONE`.
- Il a **son propre** `OnClickListener` : le clic ne remonte pas à la ligne → la fiche ne s'ouvre pas.
- `Intent.ACTION_DIAL` + `Uri.fromParts("tel", numero, null)` : le composeur s'ouvre, **sans permission**.
  Numéro vide → message ; pas d'application téléphone → `ActivityNotFoundException` attrapée → message.

**Test rapide** : activer l'appel, bouton visible sur chaque ligne ; le désactiver, il disparaît ; clic → composeur avec le bon numéro.

---

## Étape 10 — La carte (`CarteContactFragment`)

- Le contact est relu **par son `_id`**. Si `hasValidPosition` est faux (NULL, ou hors limites), un **message explicite**
  remplace la carte ; sinon marqueur + caméra centrée (zoom 14).
- `SupportMapFragment` est gonflé comme fragment enfant (`FragmentContainerView`) : géré correctement à la rotation.
- La carte montre la position **enregistrée** : après avoir modifié les coordonnées, enregistrez d'abord.

---

## Étape 11 — Rotation et recréation

| Risque | Parade dans le code |
|---|---|
| Fragments empilés en double | `MainActivity` n'ajoute la liste que si `savedInstanceState == null` |
| Saisie perdue | tous les champs ont un `id` (état restauré automatiquement) ; `populated` empêche de ré-écraser avec la base |
| Photo en attente perdue | chemin sauvegardé dans `onSaveInstanceState` |
| Boîte de dialogue disparue | `DialogFragment` |
| Résultat tardif d'un import photo | événement porteur de l'`id` de la fiche destinataire |

---

## Étape 12 — Tests, captures, compte rendu, GitHub

1. Dérouler la checklist de `docs/COMPTE_RENDU.md` (section 8 du sujet) et noter le résultat **réel**.
2. Prendre les **captures réelles** listées dans le compte rendu (12 captures).
3. Rédiger le compte rendu PDF **avec vos mots** (voir la remarque en tête de `COMPTE_RENDU.md`).
4. Vérifier que `local.properties` n'est pas versionné (`git status` ne doit pas le lister), pousser sur GitHub,
   **donner l'accès à l'enseignant**, déposer l'URL dans Google Classroom.

```bash
git init
git add .
git status            # vérifier : pas de local.properties, pas de clé
git commit -m "TP Contacts V4"
git branch -M main
git remote add origin https://github.com/<vous>/<depot>.git
git push -u origin main
```

---

## Annexe — Bonus (facultatif, +2 pts)

- **Migration v2 (favori)** : `DATABASE_VERSION = 2`, dans `onUpgrade` :
  `if (oldVersion < 2) db.execSQL("ALTER TABLE contacts ADD COLUMN favori INTEGER NOT NULL DEFAULT 0")`.
  Ajouter `favori` au contrat, au modèle, au `toContentValues()` et à `readContacts()`, puis
  `ORDER BY favori DESC, nom COLLATE NOCASE, prenom COLLATE NOCASE`. Test : installer la V4 (v1), créer des contacts, puis installer la v2 **par-dessus** (sans désinstaller) : rien n'est perdu.
- **4e préférence « Ordre de tri »** : une `ListPreference` de plus ; `PreferencesManager` renvoie la clause `ORDER BY`
  à partir d'une **liste blanche** (jamais la chaîne brute des préférences dans le SQL).
- **Import de 100 contacts** : `db.beginTransaction()` … `db.setTransactionSuccessful()` … `db.endTransaction()` ;
  mesurer avec `SystemClock.elapsedRealtime()` avec et sans transaction (sans transaction, chaque insertion est un commit disque).
