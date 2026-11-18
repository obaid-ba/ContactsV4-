package com.example.contactsv4.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Stockage des photos dans le stockage INTERNE de l'application (filesDir/photos).
 *
 * Pourquoi une copie ? Une URI de galerie (content://...) est temporaire : l'autorisation de lecture
 * disparaît au redémarrage. Le fichier copié dans filesDir, lui, nous appartient pour toujours
 * (jusqu'à désinstallation / effacement des données). C'est son CHEMIN qui est écrit en base.
 *
 * Toutes les fonctions d'import BLOQUENT (décodage + écriture disque) : à appeler sur Dispatchers.IO.
 */
object PhotoStorage {
    private const val PHOTOS_DIR = "photos"
    private const val CAMERA_DIR = "camera" // doit correspondre à res/xml/file_paths.xml
    private const val MAX_SIDE_PX = 1024
    private const val JPEG_QUALITY = 85

    fun photosDir(context: Context): File = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }

    /** Fichier temporaire (cache) où l'application Caméra écrira la photo. */
    fun newCameraTempFile(context: Context): File {
        val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        return File.createTempFile("capture_", ".jpg", dir)
    }

    /** Copie une image de la galerie vers filesDir. Renvoie le chemin, ou null en cas d'échec. */
    fun importFromUri(context: Context, uri: Uri): String? = try {
        val resolver = context.contentResolver

        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bornes) }
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) {
            null
        } else {
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bornes.outWidth, bornes.outHeight, MAX_SIDE_PX)
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (bitmap == null) null else saveNormalized(context, bitmap, orientation)
        }
    } catch (e: Exception) {
        null
    }

    /** Importe la photo prise par la caméra (fichier temporaire), puis supprime le temporaire. */
    fun importFromFile(context: Context, file: File): String? = try {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) {
            null
        } else {
            val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bornes.outWidth, bornes.outHeight, MAX_SIDE_PX)
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap == null) null else saveNormalized(context, bitmap, orientation)
        }
    } catch (e: Exception) {
        null
    } finally {
        file.delete()
    }

    /** Décode une miniature (côté ≥ [targetPx]) pour l'affichage. Null si le fichier est illisible. */
    fun decodeScaled(path: String, targetPx: Int): Bitmap? = try {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) {
            null
        } else {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bornes.outWidth, bornes.outHeight, targetPx)
            })
        }
    } catch (e: Exception) {
        null
    }

    /** Supprime le fichier seulement s'il se trouve bien dans notre dossier photos (sécurité). */
    fun deleteIfInternal(context: Context, path: String?) {
        if (path == null) return
        try {
            val file = File(path)
            if (file.canonicalPath.startsWith(photosDir(context).canonicalPath + File.separator)) {
                file.delete()
            }
        } catch (e: Exception) {
            // Rien à faire : un fichier orphelin n'est pas critique.
        }
    }

    // ------------------------------------------------------------------ privé

    /** Plus grande puissance de 2 telle que le plus grand côté reste ≥ cible. */
    private fun sampleSizeFor(width: Int, height: Int, cible: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= cible) sample *= 2
        return sample
    }

    /** Applique l'orientation EXIF, réduit à MAX_SIDE_PX, écrit en JPEG dans filesDir/photos. */
    private fun saveNormalized(context: Context, source: Bitmap, orientation: Int): String? {
        var bitmap = rotate(source, orientation)

        val plusGrandCote = maxOf(bitmap.width, bitmap.height)
        if (plusGrandCote > MAX_SIDE_PX) {
            val echelle = MAX_SIDE_PX.toFloat() / plusGrandCote
            val reduit = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * echelle).toInt().coerceAtLeast(1),
                (bitmap.height * echelle).toInt().coerceAtLeast(1),
                true
            )
            if (reduit !== bitmap) bitmap.recycle()
            bitmap = reduit
        }

        val destination = File(photosDir(context), "contact_${UUID.randomUUID()}.jpg")
        return try {
            FileOutputStream(destination).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            destination.absolutePath
        } catch (e: Exception) {
            destination.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrice = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrice.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrice.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrice.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrice.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrice.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrice.postRotate(90f)
                matrice.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrice.postRotate(270f)
                matrice.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val tourne = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrice, true)
        if (tourne !== bitmap) bitmap.recycle()
        return tourne
    }
}
