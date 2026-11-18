package com.example.contactsv4.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.contactsv4.R

fun Context.toast(@StringRes message: Int) =
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()

fun Context.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()

/**
 * Ouvre le composeur avec le numéro (ACTION_DIAL).
 * ACTION_DIAL ne demande AUCUNE permission : l'utilisateur appuie lui-même sur « Appeler ».
 * (ACTION_CALL, lui, lance l'appel directement et exige la permission CALL_PHONE.)
 */
fun Context.dialNumber(number: String?) {
    val numero = number?.trim().orEmpty()
    if (numero.isEmpty()) {
        toast(R.string.err_numero_vide)
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)))
    } catch (e: ActivityNotFoundException) {
        toast(R.string.err_pas_app_telephone)
    }
}

/** Ouvre l'application e-mail (ACTION_SENDTO + mailto: : seules les applis de courrier répondent). */
fun Context.composeEmail(address: String?) {
    val adresse = address?.trim().orEmpty()
    if (adresse.isEmpty()) {
        toast(R.string.err_email_vide)
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", adresse, null)))
    } catch (e: ActivityNotFoundException) {
        toast(R.string.err_pas_app_email)
    }
}

/** Remplace le fragment affiché et l'empile pour que « Retour » revienne à l'écran précédent. */
fun Fragment.navigateTo(target: Fragment, tag: String) {
    parentFragmentManager.commit {
        setReorderingAllowed(true)
        replace(R.id.fragment_container, target, tag)
        addToBackStack(null)
    }
}
