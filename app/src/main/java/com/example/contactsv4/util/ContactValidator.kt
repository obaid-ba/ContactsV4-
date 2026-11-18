package com.example.contactsv4.util

import android.util.Patterns
import com.example.contactsv4.R
import com.example.contactsv4.data.Contact

/** Champs du formulaire pouvant porter une erreur. */
enum class Field { NOM, PRENOM, TELEPHONE, EMAIL, LATITUDE, LONGITUDE }

/**
 * Résultat d'une validation : soit [contact] est non nul (tout est valide),
 * soit [errors] indique, champ par champ, le message à afficher.
 */
class ValidationResult(
    val contact: Contact?,
    /** Champ -> identifiant de la ressource texte (strings.xml) du message d'erreur. */
    val errors: Map<Field, Int>
)

/**
 * Validation du formulaire AVANT toute écriture en base : si une erreur est détectée,
 * aucun Contact n'est produit, donc rien ne peut être enregistré.
 */
object ContactValidator {

    fun validate(
        id: Long,
        nom: String,
        prenom: String,
        telephone: String,
        email: String,
        latitude: String,
        longitude: String,
        photoPath: String?
    ): ValidationResult {
        val errors = LinkedHashMap<Field, Int>()

        val nomNet = nom.trim()
        val prenomNet = prenom.trim()
        val telNet = telephone.trim()
        val emailNet = email.trim()

        if (nomNet.isEmpty()) errors[Field.NOM] = R.string.err_nom_obligatoire
        if (prenomNet.isEmpty()) errors[Field.PRENOM] = R.string.err_prenom_obligatoire
        if (telNet.isEmpty()) errors[Field.TELEPHONE] = R.string.err_telephone_obligatoire
        if (emailNet.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(emailNet).matches()) {
            errors[Field.EMAIL] = R.string.err_email_invalide
        }

        // GPS : les deux vides = pas de position (NULL, NULL). Sinon les deux doivent être valides.
        val latTxt = latitude.trim()
        val lonTxt = longitude.trim()
        var lat: Double? = null
        var lon: Double? = null

        if (latTxt.isNotEmpty() || lonTxt.isNotEmpty()) {
            if (latTxt.isEmpty()) {
                errors[Field.LATITUDE] = R.string.err_gps_incomplet
            } else {
                lat = parseCoordonnee(latTxt)
                when {
                    lat == null -> errors[Field.LATITUDE] = R.string.err_gps_non_numerique
                    lat !in -90.0..90.0 -> errors[Field.LATITUDE] = R.string.err_latitude_limites
                }
            }
            if (lonTxt.isEmpty()) {
                errors[Field.LONGITUDE] = R.string.err_gps_incomplet
            } else {
                lon = parseCoordonnee(lonTxt)
                when {
                    lon == null -> errors[Field.LONGITUDE] = R.string.err_gps_non_numerique
                    lon !in -180.0..180.0 -> errors[Field.LONGITUDE] = R.string.err_longitude_limites
                }
            }
        }

        if (errors.isNotEmpty()) return ValidationResult(null, errors)

        return ValidationResult(
            Contact(
                id = id,
                nom = nomNet,
                prenom = prenomNet,
                telephone = telNet,
                email = emailNet.ifEmpty { null },
                photoUri = photoPath,
                latitude = lat,
                longitude = lon
            ),
            errors
        )
    }

    /**
     * Accepte « 36.8 » et « 36,8 » (clavier français). Refuse « abc », « NaN » et « Infinity »
     * (toDoubleOrNull() accepte ces deux derniers, d'où le test isFinite()).
     */
    private fun parseCoordonnee(texte: String): Double? =
        texte.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
