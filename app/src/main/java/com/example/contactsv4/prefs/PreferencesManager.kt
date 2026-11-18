package com.example.contactsv4.prefs

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager

/**
 * Point d'accès UNIQUE aux préférences utilisateur (stockées dans les SharedPreferences par défaut,
 * le même fichier que celui qu'écrit PreferenceFragmentCompat).
 *
 * Il convertit les valeurs brutes (chaînes) en types directement utilisables par l'interface :
 * la taille en sp (Float), la couleur en Int ARGB, l'appel en Boolean.
 * L'adapter ne manipule donc jamais de chaîne « #RRGGBB » ni de clé de préférence.
 */
class PreferencesManager(context: Context) {

    /** Préférences d'affichage déjà converties. */
    data class DisplayPrefs(
        val tailleSp: Float,
        val couleur: Int,
        val appelDepuisListe: Boolean
    )

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun read(): DisplayPrefs = DisplayPrefs(
        tailleSp = readTailleSp(),
        couleur = readCouleur(),
        appelDepuisListe = prefs.getBoolean(KEY_APPEL_LISTE, DEFAULT_APPEL_LISTE)
    )

    private fun readTailleSp(): Float =
        prefs.getString(KEY_TAILLE, DEFAULT_TAILLE)?.toFloatOrNull() ?: DEFAULT_TAILLE.toFloat()

    private fun readCouleur(): Int {
        val brut = prefs.getString(KEY_COULEUR, DEFAULT_COULEUR) ?: DEFAULT_COULEUR
        return try {
            Color.parseColor(brut)
        } catch (e: IllegalArgumentException) {
            Color.parseColor(DEFAULT_COULEUR) // valeur corrompue : on retombe sur le noir
        }
    }

    companion object {
        // Clés : identiques à celles de res/xml/preferences.xml
        const val KEY_TAILLE = "taille_police"
        const val KEY_COULEUR = "couleur_preferee"
        const val KEY_APPEL_LISTE = "appel_depuis_liste"

        // Valeurs par défaut : identiques à app:defaultValue dans preferences.xml
        const val DEFAULT_TAILLE = "16"
        const val DEFAULT_COULEUR = "#000000"
        const val DEFAULT_APPEL_LISTE = false
    }
}
