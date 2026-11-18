package com.example.contactsv4.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
 * Gère la création et la mise à jour du fichier contacts.db.
 *
 * - onCreate  : appelée UNE SEULE FOIS, quand le fichier n'existe pas encore
 *               (premier lancement, ou après désinstallation / effacement des données).
 *               Elle crée la table puis insère les contacts de démonstration.
 * - onUpgrade : appelée quand DATABASE_VERSION augmente. C'est là qu'on migre le schéma
 *               SANS perdre les données.
 *
 * Singleton : une seule instance pour toute l'application (voir [getInstance]), construite
 * avec applicationContext pour ne jamais retenir une Activity en mémoire (fuite).
 */
class ContactDbHelper internal constructor(context: Context, name: String?) :
    SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
        // onCreate s'exécute déjà dans une transaction : si une insertion échoue, tout est annulé.
        DEMO_CONTACTS.forEach { db.insertOrThrow(TABLE_NAME, null, it.toContentValues()) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations incrémentales : on applique chaque étape manquante, dans l'ordre.
        // Aucune migration n'existe encore (version 1). Exemple du bonus (version 2) :
        //
        // if (oldVersion < 2) {
        //     db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN favori INTEGER NOT NULL DEFAULT 0")
        // }
    }

    companion object {
        const val DATABASE_VERSION = 1

        private val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NOM       TEXT NOT NULL,
                $COL_PRENOM    TEXT NOT NULL,
                $COL_TELEPHONE TEXT NOT NULL,
                $COL_EMAIL     TEXT,
                $COL_PHOTO_URI TEXT,
                $COL_LATITUDE  REAL,
                $COL_LONGITUDE REAL
            )
        """.trimIndent()

        @Volatile
        private var instance: ContactDbHelper? = null

        /** Instance unique (double-checked locking). */
        fun getInstance(context: Context): ContactDbHelper =
            instance ?: synchronized(this) {
                instance ?: ContactDbHelper(
                    context.applicationContext,
                    ContactContract.DATABASE_NAME
                ).also { instance = it }
            }

        /** 6 contacts de démonstration, dont 2 sans coordonnées GPS et 1 avec une apostrophe. */
        private val DEMO_CONTACTS = listOf(
            Contact(
                nom = "Ben Salah", prenom = "Ahmed", telephone = "+216 22 123 456",
                email = "ahmed.bensalah@example.com", latitude = 36.8065, longitude = 10.1815
            ),
            Contact(
                nom = "Trabelsi", prenom = "Sarra", telephone = "+216 98 765 432",
                email = "sarra.trabelsi@example.com", latitude = 34.7406, longitude = 10.7603
            ),
            Contact(
                nom = "Jlassi", prenom = "Mohamed", telephone = "55 444 333",
                latitude = 33.8758, longitude = 10.8575
            ),
            Contact(
                nom = "Mansouri", prenom = "Inès", telephone = "+216 20 555 666",
                email = "ines.mansouri@example.com"
            ), // sans GPS
            Contact(
                nom = "Gharbi", prenom = "Youssef", telephone = "71 800 900"
            ), // sans GPS
            Contact(
                nom = "O'Neil", prenom = "Sean", telephone = "+216 29 111 222",
                email = "sean.oneil@example.com", latitude = 37.2744, longitude = 9.8739
            )
        )
    }
}
