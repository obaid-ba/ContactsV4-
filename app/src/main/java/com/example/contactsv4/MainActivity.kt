package com.example.contactsv4

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.contactsv4.databinding.ActivityMainBinding
import com.example.contactsv4.ui.ListeContactsFragment
import com.example.contactsv4.ui.ParametresFragment

/**
 * Unique Activity : elle héberge la barre d'application et un FragmentContainerView dans lequel
 * les fragments sont échangés dynamiquement (replace + addToBackStack).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // savedInstanceState != null = l'activité est recréée (rotation) : Android a déjà restauré
        // les fragments. Ajouter la liste une seconde fois empilerait des fragments en double.
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container, ListeContactsFragment(), TAG_LISTE)
            }
        }

        // Flèche « Retour » dans la barre dès qu'on n'est plus sur la liste.
        supportFragmentManager.addOnBackStackChangedListener {
            updateUpButton()
            invalidateOptionsMenu()
        }
        updateUpButton()
    }

    private fun updateUpButton() {
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // On masque « Paramètres » quand on y est déjà.
        val courant = supportFragmentManager.findFragmentById(R.id.fragment_container)
        menu.findItem(R.id.action_parametres)?.isVisible = courant !is ParametresFragment
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_parametres -> {
            openSettings()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openSettings() {
        val courant = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (courant is ParametresFragment) return
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, ParametresFragment(), TAG_PARAMETRES)
            addToBackStack(null)
        }
    }

    private companion object {
        const val TAG_LISTE = "liste"
        const val TAG_PARAMETRES = "parametres"
    }
}
