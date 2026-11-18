package com.example.contactsv4.util

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import com.example.contactsv4.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Affiche une photo SANS bloquer le thread principal : le décodage se fait sur Dispatchers.IO,
 * avec un petit cache mémoire pour que le défilement de la liste reste fluide.
 */
object PhotoLoader {

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt() // 1/8 de la mémoire, en Ko
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(scope: CoroutineScope, view: ImageView, path: String?, targetPx: Int) {
        // Le tag permet d'ignorer un résultat tardif si la vue (recyclée) affiche déjà autre chose.
        view.tag = path
        if (path == null) {
            view.setImageResource(R.drawable.ic_person)
            return
        }

        val cle = "$path@$targetPx"
        cache.get(cle)?.let {
            view.setImageBitmap(it)
            return
        }

        view.setImageResource(R.drawable.ic_person)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { PhotoStorage.decodeScaled(path, targetPx) }
            if (bitmap != null) {
                cache.put(cle, bitmap)
                if (view.tag == path) view.setImageBitmap(bitmap)
            }
            // bitmap == null (fichier absent) : on garde l'image de remplacement.
        }
    }
}
