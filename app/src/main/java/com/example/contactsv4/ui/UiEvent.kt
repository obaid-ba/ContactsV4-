package com.example.contactsv4.ui

import com.example.contactsv4.data.Contact

/** Événements ponctuels (« une seule fois ») envoyés par le ViewModel à l'écran visible. */
sealed interface UiEvent {
    /** Enregistrement réussi : l'écran peut revenir à la liste. */
    data class Saved(val message: String) : UiEvent

    /** Suppression réussie : l'écran peut revenir à la liste. */
    data class Deleted(val message: String) : UiEvent

    /** Une photo a été copiée dans le stockage interne ; [ownerId] = fiche (0 = création) qui l'a demandée. */
    data class PhotoReady(val ownerId: Long, val path: String) : UiEvent

    /** Message d'erreur compréhensible à afficher. */
    data class Error(val message: String) : UiEvent
}

/** Résultat du chargement d'un contact par son _id (contact = null si introuvable). */
data class ContactLoad(val id: Long, val contact: Contact?)
