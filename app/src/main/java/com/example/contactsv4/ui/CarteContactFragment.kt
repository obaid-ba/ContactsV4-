package com.example.contactsv4.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.contactsv4.R
import com.example.contactsv4.data.Contact
import com.example.contactsv4.databinding.FragmentCarteContactBinding
import com.example.contactsv4.util.toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Affiche la position ENREGISTRÉE du contact sur Google Maps (marqueur).
 * Le contact est relu en base à partir de son _id passé dans le Bundle.
 * Sans position valide, un message explicite remplace la carte.
 */
class CarteContactFragment : Fragment(R.layout.fragment_carte_contact) {

    private var _binding: FragmentCarteContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCarteContactBinding.bind(view)
        requireActivity().setTitle(R.string.titre_carte)

        val contactId = requireArguments().getLong(ARG_CONTACT_ID)
        viewModel.loadContact(contactId) // avant observe() : voir DetailContactFragment
        viewModel.selected.observe(viewLifecycleOwner) { load ->
            if (load == null || load.id != contactId) return@observe
            val contact = load.contact
            when {
                contact == null -> {
                    requireContext().toast(R.string.err_introuvable)
                    parentFragmentManager.popBackStack()
                }
                contact.hasValidPosition -> showMap(contact)
                else -> showNoPositionMessage()
            }
        }
    }

    private fun showNoPositionMessage() {
        binding.map.isVisible = false
        binding.textMessageCarte.isVisible = true
    }

    private fun showMap(contact: Contact) {
        binding.textMessageCarte.isVisible = false
        binding.map.isVisible = true

        val latitude = contact.latitude ?: return
        val longitude = contact.longitude ?: return
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            ?: return

        mapFragment.getMapAsync { googleMap ->
            val position = LatLng(latitude, longitude)
            googleMap.addMarker(
                MarkerOptions().position(position).title("${contact.prenom} ${contact.nom}")
            )
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 14f))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: Long) = CarteContactFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}
