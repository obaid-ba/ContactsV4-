package com.example.contactsv4.data

import android.content.ContentValues

/**
 * Modèle d'un contact.
 *
 * [id] vaut 0 tant que le contact n'est pas enregistré ; après insertion il prend la valeur
 * de la colonne _id attribuée par SQLite (AUTOINCREMENT).
 * Une position GPS absente est représentée par null dans les DEUX champs.
 */
data class Contact(
    val id: Long = 0,
    val nom: String,
    val prenom: String,
    val telephone: String,
    val email: String? = null,
    val photoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    /** Vrai si les deux coordonnées existent et sont dans les limites géographiques. */
    val hasValidPosition: Boolean
        get() = latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * Convertit le contact en ContentValues pour insert()/update().
 * Les valeurs null sont écrites explicitement (colonne mise à NULL).
 * La colonne _id n'est jamais écrite : elle est gérée par SQLite.
 */
fun Contact.toContentValues(): ContentValues = ContentValues().apply {
    put(ContactContract.COL_NOM, nom)
    put(ContactContract.COL_PRENOM, prenom)
    put(ContactContract.COL_TELEPHONE, telephone)
    put(ContactContract.COL_EMAIL, email)
    put(ContactContract.COL_PHOTO_URI, photoUri)
    put(ContactContract.COL_LATITUDE, latitude)
    put(ContactContract.COL_LONGITUDE, longitude)
}
