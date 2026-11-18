package com.example.contactsv4.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.contactsv4.R
import com.example.contactsv4.databinding.FragmentDetailContactBinding
import com.example.contactsv4.data.Contact
import com.example.contactsv4.prefs.PreferencesManager
import com.example.contactsv4.util.ContactValidator
import com.example.contactsv4.util.Field
import com.example.contactsv4.util.PhotoLoader
import com.example.contactsv4.util.PhotoStorage
import com.example.contactsv4.util.composeEmail
import com.example.contactsv4.util.dialNumber
import com.example.contactsv4.util.navigateTo
import com.example.contactsv4.util.toast
import java.io.File
import kotlinx.coroutines.launch

/**
 * Création (contactId == 0), consultation, modification et suppression d'un contact.
 *
 * Gestion de la rotation / du retour depuis un autre écran :
 *  - les champs ont tous un id : Android restaure automatiquement leur texte ;
 *  - [populated] empêche de ré-écraser une saisie en cours avec les données de la base ;
 *  - le chemin de photo en attente est sauvegardé dans onSaveInstanceState.
 */
class DetailContactFragment : Fragment(R.layout.fragment_detail_contact) {

    private var _binding: FragmentDetailContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()

    private val contactId: Long get() = requireArguments().getLong(ARG_CONTACT_ID, NEW_CONTACT)
    private val isNew: Boolean get() = contactId == NEW_CONTACT

    /** Vrai une fois les champs remplis depuis la base (ou immédiatement en création). */
    private var populated = false

    /** Photo affichée / à enregistrer, et photo actuellement en base (pour nettoyer les fichiers). */
    private var currentPhoto: String? = null
    private var originalPhoto: String? = null

    /** Fichier temporaire donné à l'application Caméra. */
    private var cameraTempPath: String? = null

    /** Vrai quand l'enregistrement a réussi (inutile alors de nettoyer la photo en attente). */
    private var saved = false

    // Sélecteur de photos du système : aucune permission nécessaire
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // uri == null : l'utilisateur a annulé -> on ne fait rien (pas de plantage)
            if (uri != null) viewModel.importPhotoFromGallery(contactId, uri)
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            onCameraResult(success)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            populated = savedInstanceState.getBoolean(STATE_POPULATED)
            currentPhoto = savedInstanceState.getString(STATE_PHOTO)
            originalPhoto = savedInstanceState.getString(STATE_ORIGINAL_PHOTO)
            cameraTempPath = savedInstanceState.getString(STATE_CAMERA_TEMP)
        } else if (isNew) {
            populated = true // rien à charger : champs vides
        }

        // Réponse de la boîte de dialogue de confirmation
        childFragmentManager.setFragmentResultListener(
            ConfirmDeleteDialogFragment.REQUEST_KEY, this
        ) { _, result ->
            if (result.getBoolean(ConfirmDeleteDialogFragment.KEY_CONFIRMED)) {
                setBusy(true)
                viewModel.delete(contactId)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailContactBinding.bind(view)

        requireActivity().setTitle(if (isNew) R.string.titre_nouveau_contact else R.string.titre_detail)
        binding.btnSupprimer.isVisible = !isNew
        binding.btnCarte.isVisible = !isNew

        applyDisplayPrefs()
        showPhoto()
        setupClicks()
        clearErrorsWhileTyping()

        // ORDRE IMPORTANT : loadContact() remet `selected` à null AVANT qu'on l'observe,
        // sinon l'observateur recevrait la fiche précédente restée en cache.
        if (!populated) viewModel.loadContact(contactId)
        viewModel.selected.observe(viewLifecycleOwner) { load ->
            if (load == null || load.id != contactId || populated) return@observe
            val contact = load.contact
            if (contact == null) {
                requireContext().toast(R.string.err_introuvable)
                parentFragmentManager.popBackStack()
            } else {
                populated = true
                fill(contact)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
        }
    }

    // ------------------------------------------------------------------ affichage

    private fun fill(contact: Contact) {
        binding.editNom.setText(contact.nom)
        binding.editPrenom.setText(contact.prenom)
        binding.editTelephone.setText(contact.telephone)
        binding.editEmail.setText(contact.email.orEmpty())
        binding.editLatitude.setText(contact.latitude?.toString().orEmpty())
        binding.editLongitude.setText(contact.longitude?.toString().orEmpty())
        originalPhoto = contact.photoUri
        currentPhoto = contact.photoUri
        showPhoto()
    }

    private fun showPhoto() {
        val taille = (120 * resources.displayMetrics.density).toInt()
        PhotoLoader.load(viewLifecycleOwner.lifecycleScope, binding.imagePhoto, currentPhoto, taille)
    }

    /** Taille (nom, prénom, téléphone) et couleur (nom, prénom) choisies dans les Paramètres. */
    private fun applyDisplayPrefs() {
        val prefs = PreferencesManager(requireContext()).read()
        listOf(binding.editNom, binding.editPrenom, binding.editTelephone).forEach {
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.tailleSp)
        }
        binding.editNom.setTextColor(prefs.couleur)
        binding.editPrenom.setTextColor(prefs.couleur)
    }

    private fun clearErrorsWhileTyping() {
        binding.editNom.doOnTextChanged { _, _, _, _ -> binding.tilNom.error = null }
        binding.editPrenom.doOnTextChanged { _, _, _, _ -> binding.tilPrenom.error = null }
        binding.editTelephone.doOnTextChanged { _, _, _, _ -> binding.tilTelephone.error = null }
        binding.editEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.editLatitude.doOnTextChanged { _, _, _, _ -> binding.tilLatitude.error = null }
        binding.editLongitude.doOnTextChanged { _, _, _, _ -> binding.tilLongitude.error = null }
    }

    // ------------------------------------------------------------------ actions

    private fun setupClicks() {
        binding.btnEnregistrer.setOnClickListener { onSaveClicked() }
        binding.btnAnnuler.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSupprimer.setOnClickListener { askDeleteConfirmation() }

        binding.btnAppeler.setOnClickListener {
            requireContext().dialNumber(binding.editTelephone.text?.toString())
        }
        binding.btnEmail.setOnClickListener {
            requireContext().composeEmail(binding.editEmail.text?.toString())
        }
        binding.btnCarte.setOnClickListener {
            navigateTo(CarteContactFragment.newInstance(contactId), "carte")
        }
        binding.btnGalerie.setOnClickListener { launchGallery() }
        binding.btnCamera.setOnClickListener { launchCamera() }
    }

    private fun onSaveClicked() {
        val resultat = ContactValidator.validate(
            id = contactId,
            nom = binding.editNom.text?.toString().orEmpty(),
            prenom = binding.editPrenom.text?.toString().orEmpty(),
            telephone = binding.editTelephone.text?.toString().orEmpty(),
            email = binding.editEmail.text?.toString().orEmpty(),
            latitude = binding.editLatitude.text?.toString().orEmpty(),
            longitude = binding.editLongitude.text?.toString().orEmpty(),
            photoPath = currentPhoto
        )
        showErrors(resultat.errors)

        // Contact invalide : message d'erreur, et RIEN n'est écrit en base.
        val contact = resultat.contact
        if (contact == null) {
            requireContext().toast(R.string.err_corrigez_formulaire)
            return
        }

        setBusy(true) // évite un double clic = double insertion
        viewModel.save(contact, originalPhoto)
    }

    private fun showErrors(errors: Map<Field, Int>) {
        fun message(champ: Field): String? = errors[champ]?.let { getString(it) }
        binding.tilNom.error = message(Field.NOM)
        binding.tilPrenom.error = message(Field.PRENOM)
        binding.tilTelephone.error = message(Field.TELEPHONE)
        binding.tilEmail.error = message(Field.EMAIL)
        binding.tilLatitude.error = message(Field.LATITUDE)
        binding.tilLongitude.error = message(Field.LONGITUDE)
    }

    private fun askDeleteConfirmation() {
        if (childFragmentManager.findFragmentByTag(ConfirmDeleteDialogFragment.TAG) == null) {
            ConfirmDeleteDialogFragment().show(childFragmentManager, ConfirmDeleteDialogFragment.TAG)
        }
    }

    private fun setBusy(busy: Boolean) {
        _binding?.let {
            it.btnEnregistrer.isEnabled = !busy
            it.btnSupprimer.isEnabled = !busy
        }
    }

    // ------------------------------------------------------------------ photos

    private fun launchGallery() {
        try {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: ActivityNotFoundException) {
            requireContext().toast(R.string.err_pas_galerie)
        }
    }

    private fun launchCamera() {
        val context = requireContext()
        val temp = PhotoStorage.newCameraTempFile(context)
        cameraTempPath = temp.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", temp)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            temp.delete()
            cameraTempPath = null
            context.toast(R.string.err_pas_camera)
        }
    }

    private fun onCameraResult(success: Boolean) {
        val path = cameraTempPath ?: return
        cameraTempPath = null
        if (success) {
            viewModel.importPhotoFromCamera(contactId, path)
        } else {
            // Prise de vue annulée : on supprime le fichier temporaire, rien ne change.
            File(path).delete()
            requireContext().toast(R.string.msg_photo_annulee)
        }
    }

    private fun onPhotoReady(event: UiEvent.PhotoReady) {
        if (event.ownerId != contactId) {
            // Photo demandée par une autre fiche (déjà fermée) : on la jette.
            PhotoStorage.deleteIfInternal(requireContext(), event.path)
            return
        }
        // Une photo choisie plus tôt mais jamais enregistrée devient inutile.
        val ancienne = currentPhoto
        if (ancienne != null && ancienne != originalPhoto) {
            PhotoStorage.deleteIfInternal(requireContext(), ancienne)
        }
        currentPhoto = event.path
        showPhoto()
    }

    // ------------------------------------------------------------------ événements du ViewModel

    private fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Saved -> {
                saved = true
                requireContext().toast(event.message)
                parentFragmentManager.popBackStack() // retour à la liste (déjà rechargée par le ViewModel)
            }
            is UiEvent.Deleted -> {
                requireContext().toast(event.message)
                parentFragmentManager.popBackStack()
            }
            is UiEvent.Error -> {
                requireContext().toast(event.message)
                setBusy(false)
            }
            is UiEvent.PhotoReady -> onPhotoReady(event)
        }
    }

    // ------------------------------------------------------------------ cycle de vie

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_POPULATED, populated)
        outState.putString(STATE_PHOTO, currentPhoto)
        outState.putString(STATE_ORIGINAL_PHOTO, originalPhoto)
        outState.putString(STATE_CAMERA_TEMP, cameraTempPath)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fiche quittée sans enregistrer (Annuler / Retour) : on supprime la photo en attente.
        // Pas de nettoyage pendant une rotation : la fiche est recréée et en a encore besoin.
        if (!requireActivity().isChangingConfigurations && !saved) {
            val enAttente = currentPhoto
            if (enAttente != null && enAttente != originalPhoto) {
                PhotoStorage.deleteIfInternal(requireContext(), enAttente)
            }
            cameraTempPath?.let { File(it).delete() }
        }
    }

    companion object {
        const val NEW_CONTACT = 0L
        private const val ARG_CONTACT_ID = "contact_id"
        private const val STATE_POPULATED = "state_populated"
        private const val STATE_PHOTO = "state_photo"
        private const val STATE_ORIGINAL_PHOTO = "state_original_photo"
        private const val STATE_CAMERA_TEMP = "state_camera_temp"

        /** L'identifiant du contact voyage dans un Bundle (0 = création). */
        fun newInstance(contactId: Long) = DetailContactFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}
