# TP Android — Gestion des contacts (V4)

Application Kotlin / XML : contacts persistants dans **SQLite** (API native, sans Room), écran de
**préférences** (taille de police, couleur, appel depuis la liste), **fragments** et **Google Maps**.

## Lancer le projet

1. Ouvrir le dossier dans **Android Studio** (Koala ou plus récent) et laisser la synchronisation Gradle se terminer.
   Si Android Studio propose de mettre à jour le plugin Gradle / le SDK, acceptez.
2. **Clé Google Maps** (jamais publiée sur GitHub) : créez une clé dans la console Google Cloud
   (API « Maps SDK for Android »), puis ajoutez UNE ligne dans le fichier `local.properties` à la racine
   (ce fichier est dans `.gitignore`) :

   ```
   MAPS_API_KEY=votre_cle_ici
   ```

   Sans clé, tout fonctionne sauf l'affichage des tuiles de la carte (fond gris).
3. Lancer sur un émulateur (avec Google Play pour la carte) ou un téléphone : **Run ▶**.

## Structure

```
app/src/main/java/com/example/contactsv4/
├── MainActivity.kt                 Hôte des fragments + barre d'application (menu Paramètres)
├── data/
│   ├── Contact.kt                  data class du modèle (+ conversion en ContentValues)
│   ├── ContactContract.kt          noms de base / table / colonnes (constantes)
│   ├── ContactDbHelper.kt          SQLiteOpenHelper singleton, onCreate (+ 6 contacts de démo), onUpgrade
│   └── ContactDao.kt               SEULE classe contenant du SQL
├── prefs/PreferencesManager.kt     lecture + conversion des préférences
├── ui/
│   ├── ContactViewModel.kt         ViewModel partagé : coroutines + Dispatchers.IO
│   ├── ListeContactsFragment.kt    liste, filtre, bouton « + », bouton Appeler
│   ├── ContactAdapter.kt           RecyclerView (ListAdapter + DiffUtil, ids = _id)
│   ├── DetailContactFragment.kt    créer / modifier / supprimer, photos, actions V3
│   ├── ConfirmDeleteDialogFragment.kt  confirmation de suppression (survit à la rotation)
│   ├── CarteContactFragment.kt     Google Maps + marqueur
│   └── ParametresFragment.kt       PreferenceFragmentCompat
└── util/                           validation, photos, intents, navigation
app/src/androidTest/.../ContactDaoTest.kt   tests instrumentés du DAO
docs/GUIDE_PAS_A_PAS.md             le guide pour refaire le TP étape par étape
docs/COMPTE_RENDU.md                trame du compte rendu + réponses aux questions
```

## Tests

- Manuels : liste de la section 8 du TP, reprise dans `docs/COMPTE_RENDU.md`.
- Automatiques (DAO) : `./gradlew connectedDebugAndroidTest` avec un appareil/émulateur branché.
