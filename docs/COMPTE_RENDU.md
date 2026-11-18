# Compte rendu — TP Android Contacts V4

> **À lire avant de rendre** : ce document est une **trame et un brouillon de réponses**. Le compte rendu
> est un travail individuel : relisez chaque réponse, vérifiez-la **dans votre code et sur votre appareil**,
> et reformulez-la avec vos mots (l'enseignant peut vous interroger dessus). Les sections marquées
> **[À COMPLÉTER]** ne peuvent venir que de vous : résultats de tests, captures, difficultés réelles.

Nom : ………………  Groupe : ………………  Dépôt GitHub : ………………

---

## 1. Schéma de la base et choix des types

Base `contacts.db`, table `contacts` :

| Colonne | Type | Contrainte | Pourquoi ce type |
|---|---|---|---|
| `_id` | INTEGER | PRIMARY KEY AUTOINCREMENT | identifiant unique attribué par SQLite, jamais réutilisé ; nom `_id` = convention Android |
| `nom`, `prenom` | TEXT | NOT NULL | champs obligatoires |
| `telephone` | TEXT | NOT NULL | peut commencer par `0` ou `+216` et contenir des espaces : un INTEGER perdrait le 0 initial |
| `email` | TEXT | — | optionnel → NULL |
| `photo_uri` | TEXT | — | chemin du fichier copié dans `filesDir/photos` |
| `latitude`, `longitude` | REAL | NULL autorisé | nombres décimaux ; position absente = NULL **dans les deux** colonnes |

## 2. Rôle du helper et du DAO

- **`ContactDbHelper`** (`SQLiteOpenHelper`) : crée le fichier et gère les versions. `onCreate` (fichier inexistant)
  crée la table et insère 6 contacts de démonstration ; `onUpgrade` (version qui augmente) migre le schéma.
  C'est un **singleton** construit avec `applicationContext`.
- **`ContactDao`** : seule classe qui contient du SQL. Elle expose `insert`, `update`, `delete`, `getAll`,
  `getById`, `search`. Requêtes paramétrées (`?`), `Cursor` fermés par `use { }`, index par `getColumnIndexOrThrow()`.
- **`ContactContract`** : constantes des noms de table et de colonnes.

## 3. Stratégie de threads

Aucun accès à la base sur le thread principal. Les fragments appellent le `ContactViewModel` partagé, qui lance
`viewModelScope.launch { withContext(Dispatchers.IO) { dao… } }` puis publie le résultat dans un `LiveData`
(mise à jour de l'interface sur le thread principal). Une nouvelle frappe dans le filtre annule la requête précédente.
Le décodage des photos et leur copie se font aussi sur `Dispatchers.IO`.

## 4. Choix de filtrage : SQL ou mémoire ?

*(brouillon — adaptez-le si vous êtes en désaccord ; ce choix doit être le vôtre et justifié)*

Choix : **filtrage en SQL** (`LIKE ?` sur nom, prénom, téléphone, dans `ContactDao.search`).

- Une seule source de vérité : le tri (`COLLATE NOCASE`) et le filtre sont faits par le même moteur.
- Passe bien à l'échelle : on ne charge que les lignes utiles si la liste devient grande.
- Illustre la requête paramétrée : l'apostrophe saisie dans le filtre ne provoque aucune erreur.
- Les `%` et `_` saisis sont échappés (`ESCAPE`).

Limites assumées : une requête par frappe (atténué : la requête précédente est annulée) ; `LIKE` et `NOCASE`
de SQLite ne sont insensibles à la casse que pour les lettres **ASCII** (« é » ≠ « É »). Pour quelques centaines de
contacts, un filtre en mémoire (`contains(ignoreCase = true)`) serait plus simple et gérerait mieux les accents :
c'est l'alternative que j'ai écartée.

## 5. Gestion des photos

L'URI d'une galerie est temporaire (la permission de lecture disparaît au redémarrage). L'image est donc **copiée**
dans `filesDir/photos/` (réduite à 1024 px max, orientation EXIF appliquée, JPEG) et c'est le **chemin de la copie**
qui est stocké dans `photo_uri`. Caméra : fichier temporaire dans le cache via `FileProvider`, puis même import.
L'ancienne photo est supprimée au remplacement et à la suppression du contact ; la photo choisie mais non
enregistrée est supprimée quand on quitte la fiche. L'affichage décode la miniature hors du thread principal.

## 6. Préférences : stockage, lecture, application immédiate

- **Stockage** : SharedPreferences par défaut (clés `taille_police`, `couleur_preferee`, `appel_depuis_liste`),
  écrites automatiquement par `PreferenceFragmentCompat`.
- **Lecture** : centralisée dans `PreferencesManager.read()`, qui renvoie une taille en sp (Float), une couleur (Int)
  et un Boolean ; l'adapter ne reçoit que des valeurs converties.
- **Application immédiate** : `onResume()` de la liste relit les préférences et appelle `adapter.updateDisplay(...)`
  (`notifyItemRangeChanged`) ; pas de redémarrage. Le détail applique taille et couleur à sa création.
- **Lisibilité** : thème clair uniquement ; les 5 couleurs sont foncées.

## 7. Questions de compréhension

**1. Quand `onCreate` et `onUpgrade` sont-elles appelées ? Et si on modifie `onCreate` sans changer la version ?**
`onCreate` est appelée une seule fois, à la première ouverture de la base (`readableDatabase`/`writableDatabase`)
quand le fichier `contacts.db` n'existe pas. `onUpgrade` est appelée à l'ouverture quand la version stockée dans le
fichier est inférieure à `DATABASE_VERSION`. Si on modifie `onCreate` sans changer la version, **rien ne se passe** pour
un appareil qui a déjà le fichier : l'ancien schéma reste, et on obtient par exemple « no such column ». Il faut
désinstaller l'application (développement) ou incrémenter la version et écrire `onUpgrade` (production).

**2. Pourquoi des arguments `?` plutôt que concaténer ? Exemple d'entrée qui casse.**
Avec `?`, la valeur est transmise séparément du texte SQL : elle n'est jamais interprétée comme du SQL. La concaténation
mélange données et code : une apostrophe ferme la chaîne et casse la requête, et une saisie malveillante peut modifier
la requête (injection SQL). Exemple : `"… WHERE nom = '" + saisie + "'"` avec la saisie `O'Neil` donne
`WHERE nom = 'O'Neil'` → erreur de syntaxe près de « Neil » ; avec `'; DROP TABLE contacts; --` on tente d'exécuter
une commande de l'attaquant.

**3. Que renvoient `insert()`, `update()`, `delete()` ? Comment les exploitez-vous ?**
`insert()` renvoie l'`_id` de la ligne créée, ou **-1** en cas d'échec ; `update()` et `delete()` renvoient le **nombre
de lignes affectées** (0 si l'`_id` n'existe pas). Dans l'interface : `insert == -1` → message « Échec de
l'enregistrement » et on reste sur la fiche ; `update == 0` ou `delete == 0` → « Contact introuvable » ; sinon message de
succès, rechargement de la liste et retour à la liste.

**4. Pourquoi pas de base sur le thread principal ? Comment l'avez-vous évité ?**
Une requête est une opération disque, lente et de durée imprévisible : sur le thread principal, elle bloque le dessin de
l'interface (saccades) et peut provoquer un ANR (« L'application ne répond pas », après ~5 s). Je l'évite avec
`viewModelScope.launch { withContext(Dispatchers.IO) { … } }` dans le `ContactViewModel`.

**5. Pourquoi un helper unique construit avec `applicationContext` ?**
Plusieurs instances ouvriraient plusieurs connexions vers le même fichier : risques de verrous (« database is locked »)
et d'incohérences ; une instance unique partage une seule connexion, gérée de façon thread-safe. `applicationContext`
vit aussi longtemps que le processus : conserver un `Context` d'Activity dans un singleton empêcherait sa libération
(fuite de mémoire).

**6. SharedPreferences ou SQLite : quels critères ?**
SharedPreferences : peu de valeurs simples clé/valeur, lues d'un bloc, sans relation ni requête (réglages).
SQLite : données structurées en grand nombre, besoin de tri / filtre / jointures / contraintes d'intégrité /
transactions. Choix : **contacts → SQLite** (nombreux enregistrements, tri, recherche, CRUD, `NOT NULL`) ;
**préférences → SharedPreferences** (trois réglages indépendants, lus à chaque affichage).

**7. Comment les préférences modifiées sont-elles appliquées sans redémarrer ?**
`PreferenceFragmentCompat` écrit chaque changement dans les SharedPreferences. Au retour à la liste, `onResume()` relit
les valeurs via `PreferencesManager.read()` et appelle `adapter.updateDisplay(...)`, qui redessine les lignes
(`notifyItemRangeChanged`). (Variante : un `OnSharedPreferenceChangeListener`.)

**8. Différence entre `ACTION_DIAL` et `ACTION_CALL` ?**
`ACTION_DIAL` ouvre le composeur avec le numéro prérempli : l'utilisateur doit appuyer lui-même sur « Appeler » ;
**aucune permission** n'est requise. `ACTION_CALL` lance l'appel immédiatement et exige la permission dangereuse
`CALL_PHONE` (demande à l'exécution), avec un risque d'appel involontaire (donc coût possible).

**9. Que deviennent la base et les préférences à la désinstallation ? Et à l'effacement des données ?**
Dans les deux cas, le stockage interne de l'application est supprimé : `contacts.db`, les SharedPreferences **et**
les photos de `filesDir`. À la relance, l'application repart comme au premier lancement : `onCreate` s'exécute de nouveau,
les contacts de démonstration sont réinsérés et les préférences reviennent aux valeurs par défaut. (« Vider le cache »
ne supprime que le cache, pas la base.) Ici `allowBackup="false"` : pas de restauration automatique après réinstallation.

## 8. Tests réalisés — **[À COMPLÉTER avec vos résultats réels]**

Cochez seulement ce que vous avez réellement exécuté, et notez ce que vous avez observé.

| # | Test (section 8 du sujet) | Résultat attendu | OK ? | Observation |
|---|---|---|---|---|
| 1 | 1er lancement puis 2e lancement | 6 contacts de démo, **pas réinsérés** au 2e lancement | ☐ | |
| 2 | Ajouter, modifier, supprimer, fermer complètement l'app | chaque opération persiste | ☐ | |
| 3 | Nom « O'Neil » / « L'Ariana » et filtre `'` | aucune erreur SQL | ☐ | |
| 4 | Enregistrer sans nom / prénom / téléphone | message d'erreur, rien en base | ☐ | |
| 5 | Changer la photo puis **redémarrer l'appareil** | photo toujours affichée | ☐ | |
| 6 | GPS absent / non numérique / hors limites | rien d'invalide enregistré ; carte : message explicite | ☐ | |
| 7 | Chaque taille et chaque couleur (liste **et** détail) | affichage conforme | ☐ | |
| 8 | Activer / désactiver l'appel depuis la liste | bouton visible/masqué ; composeur avec le bon numéro ; fiche non ouverte | ☐ | |
| 9 | Relancer l'application | 3 préférences conservées | ☐ | |
| 10 | Rotation : liste, détail en saisie, paramètres ; Retour | pas de doublon, saisie conservée | ☐ | |
| 11 | Database Inspector | contenu de `contacts` = affichage | ☐ | |

Tests automatiques : `ContactDaoTest` (8 tests, base en mémoire) — résultat : …………

## 9. Difficultés rencontrées — **[À COMPLÉTER]**

Décrivez 2 ou 3 vraies difficultés (message d'erreur, cause, solution). Pistes si elles vous sont arrivées :
« no such column » après un changement de schéma ; carte grise (clé API) ; photo couchée (EXIF) ; saisie perdue à la rotation ;
liste non actualisée après enregistrement.

## 10. Captures réelles — **[À COMPLÉTER]**

1. ☐ Liste au premier lancement
2. ☐ Création d'un contact
3. ☐ Modification d'un contact
4. ☐ Confirmation de suppression
5. ☐ Liste après fermeture complète et relance
6. ☐ Database Inspector montrant la table `contacts`
7. ☐ Écran Paramètres
8. ☐ Liste avec deux tailles différentes (2 captures)
9. ☐ Liste avec deux couleurs différentes (2 captures)
10. ☐ Liste avec boutons Appeler activés
11. ☐ Composeur ouvert depuis la liste
12. ☐ Carte avec marqueur
