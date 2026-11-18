package com.example.contactsv4.data

/**
 * Contrat de la base : nom de la base, de la table et des colonnes.
 * Aucun nom de colonne n'est écrit en dur ailleurs dans le projet.
 */
object ContactContract {
    const val DATABASE_NAME = "contacts.db"

    const val TABLE_NAME = "contacts"

    const val COL_ID = "_id"
    const val COL_NOM = "nom"
    const val COL_PRENOM = "prenom"
    const val COL_TELEPHONE = "telephone"
    const val COL_EMAIL = "email"
    const val COL_PHOTO_URI = "photo_uri"
    const val COL_LATITUDE = "latitude"
    const val COL_LONGITUDE = "longitude"

    /** Colonnes lues par les requêtes SELECT (ordre libre : on lit par nom, pas par position). */
    val ALL_COLUMNS = arrayOf(
        COL_ID, COL_NOM, COL_PRENOM, COL_TELEPHONE,
        COL_EMAIL, COL_PHOTO_URI, COL_LATITUDE, COL_LONGITUDE
    )
}
