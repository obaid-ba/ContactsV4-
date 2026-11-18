package com.example.contactsv4.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.contactsv4.R
import com.example.contactsv4.data.Contact
import com.example.contactsv4.data.ContactDao
import com.example.contactsv4.data.ContactDbHelper
import com.example.contactsv4.util.PhotoStorage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel PARTAGÉ (rattaché à MainActivity, donc commun à tous les fragments).
 *
 *  - Il survit aux rotations : la liste n'est pas rechargée inutilement.
 *  - TOUS les accès à la base passent par ici : coroutine lancée dans viewModelScope,
 *    requête exécutée dans withContext(Dispatchers.IO) -> jamais sur le thread principal.
 *  - Après chaque modification (ajout / modif / suppression) il recharge la liste :
 *    les fragments qui observent [contacts] sont notifiés automatiquement.
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val dao = ContactDao(ContactDbHelper.getInstance(appContext))

    // ---------------------------------------------------------------- liste

    // Sans valeur initiale : tant que la 1re lecture SQLite n'est pas terminée, la liste n'affiche
    // pas faussement « Aucun contact à afficher ».
    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts

    /** Texte du filtre en cours (conservé pendant la rotation). */
    var query: String = ""
        private set

    private var listJob: Job? = null

    fun setQuery(texte: String) {
        if (texte == query) return
        query = texte
        loadList()
    }

    /** Recharge la liste depuis la base (appelée à chaque affichage de la liste et après chaque écriture). */
    fun refresh() = loadList()

    private fun loadList() {
        listJob?.cancel() // une frappe plus récente remplace une requête devenue obsolète
        val filtre = query
        listJob = viewModelScope.launch {
            try {
                val liste = withContext(Dispatchers.IO) { dao.search(filtre) }
                _contacts.value = liste
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(UiEvent.Error(str(R.string.err_chargement)))
            }
        }
    }

    // ---------------------------------------------------------------- contact sélectionné

    private val _selected = MutableLiveData<ContactLoad?>()
    val selected: LiveData<ContactLoad?> = _selected

    /**
     * Charge UN contact par son _id (jamais par sa position dans la liste).
     * On remet d'abord [selected] à null : sinon un fragment qui observe recevrait
     * tout de suite l'ancienne valeur (fiche précédente) en cache.
     */
    fun loadContact(id: Long) {
        _selected.value = null
        viewModelScope.launch {
            try {
                val contact = withContext(Dispatchers.IO) { dao.getById(id) }
                _selected.value = ContactLoad(id, contact)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _selected.value = ContactLoad(id, null)
                send(UiEvent.Error(str(R.string.err_sql)))
            }
        }
    }

    // ---------------------------------------------------------------- écritures

    private class Outcome(val success: Boolean, @StringRes val message: Int)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Create si contact.id == 0, Update sinon.
     * [previousPhoto] : ancien chemin de photo, supprimé du disque si la photo a changé.
     */
    fun save(contact: Contact, previousPhoto: String?) {
        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    val result = if (contact.id == 0L) {
                        // insert() renvoie le nouvel _id, ou -1 en cas d'échec
                        if (dao.insert(contact) == -1L) Outcome(false, R.string.err_insertion)
                        else Outcome(true, R.string.msg_contact_ajoute)
                    } else {
                        // update() renvoie le nombre de lignes modifiées : 0 = _id inexistant
                        if (dao.update(contact) == 0) Outcome(false, R.string.err_introuvable)
                        else Outcome(true, R.string.msg_contact_modifie)
                    }
                    if (result.success && previousPhoto != null && previousPhoto != contact.photoUri) {
                        PhotoStorage.deleteIfInternal(appContext, previousPhoto)
                    }
                    result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // SQLiteException (disque plein, base verrouillée...) ou autre
                Outcome(false, R.string.err_sql)
            }

            if (outcome.success) {
                loadList()
                send(UiEvent.Saved(str(outcome.message)))
            } else {
                send(UiEvent.Error(str(outcome.message)))
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    val photo = dao.getById(id)?.photoUri
                    // delete() renvoie le nombre de lignes supprimées : 0 = déjà supprimé / inexistant
                    if (dao.delete(id) == 0) {
                        Outcome(false, R.string.err_introuvable)
                    } else {
                        PhotoStorage.deleteIfInternal(appContext, photo)
                        Outcome(true, R.string.msg_contact_supprime)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome(false, R.string.err_suppression)
            }

            if (outcome.success) {
                loadList()
                send(UiEvent.Deleted(str(outcome.message)))
            } else {
                send(UiEvent.Error(str(outcome.message)))
            }
        }
    }

    // ---------------------------------------------------------------- photos

    /** Copie une image de la galerie vers filesDir (hors thread principal). */
    fun importPhotoFromGallery(ownerId: Long, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { PhotoStorage.importFromUri(appContext, uri) }
            sendPhotoResult(ownerId, path)
        }
    }

    /** Importe la photo prise par la caméra depuis son fichier temporaire. */
    fun importPhotoFromCamera(ownerId: Long, tempFilePath: String) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                PhotoStorage.importFromFile(appContext, File(tempFilePath))
            }
            sendPhotoResult(ownerId, path)
        }
    }

    private fun sendPhotoResult(ownerId: Long, path: String?) {
        if (path != null) send(UiEvent.PhotoReady(ownerId, path))
        else send(UiEvent.Error(str(R.string.err_photo_import)))
    }

    // ---------------------------------------------------------------- utilitaires

    private fun send(event: UiEvent) {
        _events.trySend(event)
    }

    private fun str(@StringRes id: Int): String = appContext.getString(id)
}
