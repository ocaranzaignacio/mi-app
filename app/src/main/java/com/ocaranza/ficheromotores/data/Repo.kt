package com.ocaranza.ficheromotores.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import com.ocaranza.ficheromotores.sync.SyncState
import com.ocaranza.ficheromotores.util.newId
import java.io.File

/** Guarda todo el fichero en un archivo JSON dentro del almacenamiento privado de la app. */
class Repo(private val ctx: Context) {
    private val file = File(ctx.filesDir, "fichero.json")
    val fotosDir: File = File(ctx.filesDir, "fotos").apply { mkdirs() }

    fun load(): Fichero {
        if (!file.exists()) return Fichero(emptyList(), emptyList())
        return try {
            ficheroFromJson(file.readText())
        } catch (e: Exception) {
            // Si el archivo quedó dañado, se conserva una copia para no perder datos.
            runCatching { file.copyTo(File(ctx.filesDir, "fichero-danado-${System.currentTimeMillis()}.json"), overwrite = true) }
            Fichero(emptyList(), emptyList())
        }
    }

    @Synchronized
    fun save(f: Fichero) {
        val tmp = File(ctx.filesDir, "fichero.json.tmp")
        tmp.writeText(f.toJson().toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun fotoFile(name: String): File = File(fotosDir, name)

    /** Copia una imagen al almacenamiento de la app, reducida a 1600 px y en JPEG. Devuelve el nombre del archivo. */
    fun importarFoto(uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxSide <= 0) return null
            var sample = 1
            while (maxSide / (sample * 2) >= 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val rot = ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val k = 1600f / maxOf(bmp.width, bmp.height)
            if (k < 1f || rot != 0f) {
                val m = Matrix()
                if (k < 1f) m.postScale(k, k)
                if (rot != 0f) m.postRotate(rot)
                val nb = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (nb != bmp) bmp.recycle()
                bmp = nb
            }
            val name = "${newId()}.jpg"
            fotoFile(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            bmp.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    private val syncFile = File(ctx.filesDir, "sync.json")

    fun loadSync(): SyncState = try {
        if (syncFile.exists()) SyncState.fromJson(syncFile.readText()) else SyncState()
    } catch (e: Exception) {
        SyncState()
    }

    @Synchronized
    fun saveSync(s: SyncState) {
        val tmp = File(ctx.filesDir, "sync.json.tmp")
        tmp.writeText(s.toJson().toString())
        if (!tmp.renameTo(syncFile)) {
            syncFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun borrarFoto(name: String) {
        runCatching { fotoFile(name).delete() }
    }
}
