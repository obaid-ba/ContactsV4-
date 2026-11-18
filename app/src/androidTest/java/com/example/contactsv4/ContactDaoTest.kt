package com.example.contactsv4

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.contactsv4.data.Contact
import com.example.contactsv4.data.ContactDao
import com.example.contactsv4.data.ContactDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentés du DAO. La base est créée EN MÉMOIRE (nom null) : le fichier contacts.db
 * réel de l'appareil n'est pas touché.
 * Lancer : clic droit sur la classe > Run, ou  ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ContactDaoTest {

    private lateinit var helper: ContactDbHelper
    private lateinit var dao: ContactDao

    @Before
    fun setUp() {
        helper = ContactDbHelper(ApplicationProvider.getApplicationContext(), null)
        dao = ContactDao(helper)
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun contact(nom: String, prenom: String = "Test", tel: String = "123") =
        Contact(nom = nom, prenom = prenom, telephone = tel)

    @Test
    fun premierLancement_contactsDeDemonstrationPresents() {
        val tous = dao.getAll()
        assertTrue("au moins 5 contacts de démo", tous.size >= 5)
        assertTrue("au moins 2 contacts sans GPS", tous.count { it.latitude == null && it.longitude == null } >= 2)
    }

    @Test
    fun insert_renvoieUnIdEtLeContactEstRelisible() {
        val id = dao.insert(contact("Nouveau"))
        assertTrue(id > 0)
        val relu = dao.getById(id)
        assertNotNull(relu)
        assertEquals("Nouveau", relu!!.nom)
        assertEquals(id, relu.id)
    }

    @Test
    fun update_renvoie1PuisZeroPourUnIdInconnu() {
        val id = dao.insert(contact("Avant"))
        assertEquals(1, dao.update(contact("Apres").copy(id = id)))
        assertEquals("Apres", dao.getById(id)!!.nom)
        assertEquals(0, dao.update(contact("Fantome").copy(id = 999_999)))
    }

    @Test
    fun delete_renvoie1PuisZero() {
        val id = dao.insert(contact("ASupprimer"))
        assertEquals(1, dao.delete(id))
        assertNull(dao.getById(id))
        assertEquals(0, dao.delete(id))
    }

    @Test
    fun apostrophe_dansLeNomEtDansLeFiltre_neCassePasLeSql() {
        dao.insert(contact("L'Ariana", "O'Neil"))
        assertEquals(1, dao.search("L'A").count { it.nom == "L'Ariana" })
        assertTrue(dao.search("'").any { it.nom == "L'Ariana" })
        assertTrue(dao.search("'; DROP TABLE contacts; --").isEmpty()) // aucune erreur, aucun résultat
        assertTrue(dao.getAll().isNotEmpty()) // la table existe toujours
    }

    @Test
    fun jokersLikeSaisisParLUtilisateurSontEchappes() {
        assertTrue(dao.search("%").isEmpty())
        assertTrue(dao.search("_").isEmpty())
    }

    @Test
    fun tri_nomPuisPrenom_sansTenirCompteDeLaCasse() {
        dao.insert(contact("zzz_bob"))
        dao.insert(contact("ZZZ_alice"))
        dao.insert(contact("zzz_Carl"))
        val ordre = dao.getAll().map { it.nom }.filter { it.startsWith("zzz", ignoreCase = true) }
        assertEquals(listOf("ZZZ_alice", "zzz_bob", "zzz_Carl"), ordre)
    }

    @Test
    fun gpsAbsent_resteNullApresRelecture() {
        val id = dao.insert(contact("SansGps"))
        val relu = dao.getById(id)!!
        assertNull(relu.latitude)
        assertNull(relu.longitude)
        assertNull(relu.email)
        assertNull(relu.photoUri)
    }
}
