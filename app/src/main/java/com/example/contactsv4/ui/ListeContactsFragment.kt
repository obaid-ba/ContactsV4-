package com.example.contactsv4.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.contactsv4.R
import com.example.contactsv4.databinding.FragmentListeContactsBinding
import com.example.contactsv4.prefs.PreferencesManager
import com.example.contactsv4.util.dialNumber
import com.example.contactsv4.util.navigateTo
import com.example.contactsv4.util.toast
import kotlinx.coroutines.launch

/** Liste des contacts : filtre, bouton « + », bouton Appeler par ligne (selon la préférence). */
class ListeContactsFragment : Fragment(R.layout.fragment_liste_contacts) {

    private var _binding: FragmentListeContactsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()
    private lateinit var preferences: PreferencesManager
    private lateinit var adapter: ContactAdapter

    private var derniereNavigation = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListeContactsBinding.bind(view)
        preferences = PreferencesManager(requireContext())

        adapter = ContactAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            prefs = preferences.read(),
            // On transmet l'_id du contact, JAMAIS sa position dans la liste.
            onContactClick = { contact -> ouvrirDetail(contact.id) },
            onCallClick = { contact -> requireContext().dialNumber(contact.telephone) }
        )
        binding.recyclerContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerContacts.adapter = adapter

        // Filtrage au fil de la saisie (requête SQL paramétrée dans ContactDao.search)
        binding.editRecherche.doOnTextChanged { texte, _, _, _ ->
            viewModel.setQuery(texte?.toString().orEmpty())
        }

        binding.fabAjouter.setOnClickListener { ouvrirDetail(DetailContactFragment.NEW_CONTACT) }

        viewModel.contacts.observe(viewLifecycleOwner) { liste ->
            adapter.submitList(liste)
            binding.textVide.isVisible = liste.isEmpty()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event is UiEvent.Error) requireContext().toast(event.message)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.titre_liste)
        // Au retour des Paramètres : relecture des préférences + rafraîchissement immédiat de l'adapter.
        adapter.updateDisplay(preferences.read())
        // La liste est rechargée depuis la base à chaque affichage.
        viewModel.refresh()
    }

    /** Anti double-clic : deux clics rapides n'empilent pas deux fiches. */
    private fun ouvrirDetail(contactId: Long) {
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - derniereNavigation < 600) return
        derniereNavigation = maintenant
        navigateTo(DetailContactFragment.newInstance(contactId), "detail")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerContacts.adapter = null
        _binding = null
    }
}
