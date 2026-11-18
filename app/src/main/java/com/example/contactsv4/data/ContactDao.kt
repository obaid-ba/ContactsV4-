package com.example.contactsv4.data

import android.database.Cursor
import com.example.contactsv4.data.ContactContract.ALL_COLUMNS
import com.example.contactsv4.data.ContactContract.COL_EMAIL
import com.example.contactsv4.data.ContactContract.COL_ID
import com.example.contactsv4.data.ContactContract.COL_LATITUDE
import com.example.contactsv4.data.ContactContract.COL_LONGITUDE
import com.example.contactsv4.data.ContactContract.COL_NOM
import com.example.contactsv4.data.ContactContract.COL_PHOTO_URI
import com.example.contactsv4.data.ContactContract.COL_PRENOM
import com.example.contactsv4.data.ContactContract.COL_TELEPHONE
import com.example.contactsv4.data.ContactContract.TABLE_NAME

/**
 * Data Access Object : SEULE classe du projet qui contient du SQL.
 *
 * Règles respectées partout ici :
 *  - toute valeur variable passe par un argument « ? » (selectionArgs / whereArgs) ;
 *  - chaque Cursor est fermé par use { } ;
 *  - les index de colonnes sont obtenus par getColumnIndexOrThrow() ;
 *  - ces méthodes BLOQUENT : l'appelant doit les lancer hors du thread principal
 *    (voir ContactViewModel : withContext(Dispatchers.IO)).
 */
class ContactDao(private val helper: ContactDbHelper) {

    /** Insère un contact. Renvoie le nouvel _id, ou -1 si l'insertion a échoué. */
    fun insert(contact: Contact): Long =
        helper.writableDatabase.insert(TABLE_NAME, null, contact.toContentValues())

    /** Met à jour la ligne dont _id = contact.id. Renvoie le nombre de lignes modifiées (0 ou 1). */
    fun update(contact: Contact): Int =
        helper.writableDatabase.update(
            TABLE_NAME,
            contact.toContentValues(),
            "$COL_ID = ?",
            arrayOf(contact.id.toString())
        )

    /** Supprime le contact. Renvoie le nombre de lignes supprimées (0 ou 1). */
    fun delete(id: Long): Int =
        helper.writableDatabase.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))

    /** Tous les contacts, triés par nom puis prénom sans tenir compte de la casse. */
    fun getAll(): List<Contact> =
        helper.readableDatabase.query(
            TABLE_NAME, ALL_COLUMNS, null, null, null, null, ORDER_BY
        ).use { readContacts(it) }

    /** Un contact par son _id, ou null s'il n'existe pas. */
    fun getById(id: Long): Contact? =
        helper.readableDatabase.query(
            TABLE_NAME, ALL_COLUMNS, "$COL_ID = ?", arrayOf(id.toString()),
            null, null, null, "1"
        ).use { readContacts(it).firstOrNull() }

    /**
     * Filtre sur nom, prénom OU téléphone (sous-chaîne).
     * La saisie est passée en argument « ? » : une apostrophe (« O'Neil ») ne casse rien.
     * Les caractères joker de LIKE saisis par l'utilisateur (% et _) sont échappés.
     *
     * Limite connue : LIKE et COLLATE NOCASE de SQLite ne comparent sans casse que les
     * lettres ASCII (« é » ≠ « É »).
     */
    fun search(texte: String): List<Contact> {
        val saisie = texte.trim()
        if (saisie.isEmpty()) return getAll()

        val motif = "%" + echapperLike(saisie) + "%"
        val selection = "($COL_NOM LIKE ? ESCAPE '\\' OR " +
            "$COL_PRENOM LIKE ? ESCAPE '\\' OR " +
            "$COL_TELEPHONE LIKE ? ESCAPE '\\')"

        return helper.readableDatabase.query(
            TABLE_NAME, ALL_COLUMNS, selection, arrayOf(motif, motif, motif),
            null, null, ORDER_BY
        ).use { readContacts(it) }
    }

    // ------------------------------------------------------------------ privé

    private fun echapperLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun readContacts(cursor: Cursor): List<Contact> {
        // Les index sont calculés UNE fois, avant la boucle.
        val iId = cursor.getColumnIndexOrThrow(COL_ID)
        val iNom = cursor.getColumnIndexOrThrow(COL_NOM)
        val iPrenom = cursor.getColumnIndexOrThrow(COL_PRENOM)
        val iTel = cursor.getColumnIndexOrThrow(COL_TELEPHONE)
        val iEmail = cursor.getColumnIndexOrThrow(COL_EMAIL)
        val iPhoto = cursor.getColumnIndexOrThrow(COL_PHOTO_URI)
        val iLat = cursor.getColumnIndexOrThrow(COL_LATITUDE)
        val iLon = cursor.getColumnIndexOrThrow(COL_LONGITUDE)

        val result = ArrayList<Contact>(cursor.count)
        while (cursor.moveToNext()) {
            result.add(
                Contact(
                    id = cursor.getLong(iId),
                    nom = cursor.getString(iNom),
                    prenom = cursor.getString(iPrenom),
                    telephone = cursor.getString(iTel),
                    email = cursor.stringOrNull(iEmail),
                    photoUri = cursor.stringOrNull(iPhoto),
                    latitude = cursor.doubleOrNull(iLat),
                    longitude = cursor.doubleOrNull(iLon)
                )
            )
        }
        return result
    }

    // getString() sur une colonne NULL renverrait "" / 0.0 : on teste isNull() pour garder le null.
    private fun Cursor.stringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.doubleOrNull(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    private companion object {
        // COLLATE NOCASE : tri insensible à la casse ; _id départage les homonymes (ordre stable).
        const val ORDER_BY =
            "$COL_NOM COLLATE NOCASE ASC, $COL_PRENOM COLLATE NOCASE ASC, $COL_ID ASC"
    }
}
