package com.example.contactsv4.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.contactsv4.data.Contact
import com.example.contactsv4.databinding.ItemContactBinding
import com.example.contactsv4.prefs.PreferencesManager.DisplayPrefs
import com.example.contactsv4.util.PhotoLoader
import kotlinx.coroutines.CoroutineScope

/**
 * Adapter de la liste. Il reçoit des préférences DÉJÀ converties (taille en sp, couleur en Int,
 * booléen) : il ne lit jamais les SharedPreferences lui-même.
 *
 * ListAdapter + DiffUtil : quand la liste change (ajout, suppression, filtre), seules les lignes
 * réellement modifiées sont redessinées. Les lignes sont identifiées par l'_id de la base.
 */
class ContactAdapter(
    private val scope: CoroutineScope,
    private var prefs: DisplayPrefs,
    private val onContactClick: (Contact) -> Unit,
    private val onCallClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ViewHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    /** Applique de nouvelles préférences : redessine les lignes visibles sans recréer l'adapter. */
    fun updateDisplay(nouvelles: DisplayPrefs) {
        if (nouvelles == prefs) return
        prefs = nouvelles
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(contact: Contact) {
            // Nom + prénom : taille ET couleur des préférences
            b.textNom.text = "${contact.nom} ${contact.prenom}"
            b.textNom.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.tailleSp)
            b.textNom.setTextColor(prefs.couleur)

            // Téléphone : taille des préférences (couleur par défaut du thème)
            b.textTelephone.text = contact.telephone
            b.textTelephone.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.tailleSp)

            val taillePhoto = (48 * b.root.resources.displayMetrics.density).toInt()
            PhotoLoader.load(scope, b.imagePhoto, contact.photoUri, taillePhoto)

            // Bouton Appeler : VISIBLE ou GONE selon la préférence.
            // Il a son propre OnClickListener : le clic ne « remonte » donc pas à la ligne
            // et n'ouvre pas la fiche détaillée.
            b.btnAppeler.visibility = if (prefs.appelDepuisListe) View.VISIBLE else View.GONE
            b.btnAppeler.setOnClickListener { onCallClick(contact) }

            b.root.setOnClickListener { onContactClick(contact) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(old: Contact, new: Contact) = old.id == new.id
            override fun areContentsTheSame(old: Contact, new: Contact) = old == new
        }
    }
}
