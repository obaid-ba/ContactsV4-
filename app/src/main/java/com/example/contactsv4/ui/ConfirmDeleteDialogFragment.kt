package com.example.contactsv4.ui

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.contactsv4.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Confirmation de suppression (AlertDialog).
 * C'est un DialogFragment, et non un AlertDialog lancé « à la main » : il survit à la rotation
 * de l'écran au lieu de disparaître (ou de provoquer une fuite de fenêtre).
 * Le résultat est renvoyé au fragment parent par l'API Fragment Result.
 */
class ConfirmDeleteDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_supprimer_titre)
            .setMessage(R.string.dialog_supprimer_message)
            .setNegativeButton(R.string.btn_annuler, null)
            .setPositiveButton(R.string.btn_supprimer) { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(KEY_CONFIRMED to true))
            }
            .create()

    companion object {
        const val TAG = "confirm_delete_dialog"
        const val REQUEST_KEY = "confirm_delete_request"
        const val KEY_CONFIRMED = "confirmed"
    }
}
