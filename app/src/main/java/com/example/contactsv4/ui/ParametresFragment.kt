package com.example.contactsv4.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.contactsv4.R

/**
 * Écran des trois préférences, généré à partir de res/xml/preferences.xml.
 *
 * PreferenceFragmentCompat écrit AUTOMATIQUEMENT chaque changement dans les SharedPreferences
 * par défaut : il n'y a pas de bouton « Enregistrer » ni de code d'écriture à écrire.
 * Le résumé de chaque liste affiche la valeur choisie (app:useSimpleSummaryProvider="true").
 * La liste des contacts relit les préférences dans onResume() : application immédiate au retour.
 */
class ParametresFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.titre_parametres)
    }
}
