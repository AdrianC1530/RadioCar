package com.radiocar.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import java.io.File

/**
 * Videos de la memoria USB (o de la radio si no hay USB).
 * Primero pregunta al índice de medios de Android; si la USB aún no está indexada,
 * recorre las carpetas directamente.
 */
class UsbVideos(private val context: Context) {

    data class Video(val title: String, val uri: Uri, val path: String?)

    data class Scan(
        val hasPermission: Boolean,
        val usbLabel: String?,
        val total: Int,
        val recent: List<Video>,
    ) {
        val usbMounted get() = usbLabel != null
    }

    val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission() = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Hace I/O: llamar desde un hilo de fondo. */
    fun scan(limit: Int = 12): Scan {
        val usb = usbVolumes()
        val label = usb.firstOrNull()?.first
        if (!hasPermission()) return Scan(false, label, 0, emptyList())

        val paths = usb.map { it.second }
        var (total, recent) = queryMediaStore(paths, limit)
        if (total == 0 && paths.isNotEmpty()) {
            val files = walk(paths)
            total = files.size
            recent = files.take(limit).map { Video(it.nameWithoutExtension, Uri.fromFile(it), it.path) }
        }
        return Scan(true, label, total, recent)
    }

    /** Intents para reproducir [video] en la app [pkg] (reproductor de fábrica), con respaldo. */
    fun playIntents(video: Video, pkg: String?): List<Intent> {
        // Los reproductores de Topway entienden mejor rutas file:// que content://.
        val data = if (Build.VERSION.SDK_INT < 29 && video.path != null) Uri.fromFile(File(video.path)) else video.uri
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        val base = { Intent(Intent.ACTION_VIEW).setDataAndType(data, "video/*").addFlags(flags) }
        return listOfNotNull(pkg?.let { base().setPackage(it) }, base())
    }

    /** (nombre, ruta) de cada memoria USB / SD montada. */
    private fun usbVolumes(): List<Pair<String, String>> = try {
        context.getSystemService(StorageManager::class.java).storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { v -> pathOf(v)?.let { (v.getDescription(context) ?: "USB") to it } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun pathOf(v: StorageVolume): String? = try {
        if (Build.VERSION.SDK_INT >= 30) {
            v.directory?.path
        } else {
            // getPath() está oculto pero disponible en Android 8.1.
            StorageVolume::class.java.getMethod("getPath").invoke(v) as String?
        }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION") // DATA sigue siendo la forma de filtrar por carpeta en Android 8.1
    private fun queryMediaStore(paths: List<String>, limit: Int): Pair<Int, List<Video>> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
        )
        val selection = paths.takeIf { it.isNotEmpty() }
            ?.joinToString(" OR ") { "${MediaStore.Video.Media.DATA} LIKE ?" }
        val args = paths.takeIf { it.isNotEmpty() }?.map { "$it/%" }?.toTypedArray()
        return try {
            context.contentResolver.query(
                uri, projection, selection, args, "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val out = ArrayList<Video>()
                while (out.size < limit && c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    out += Video(
                        title = name.substringBeforeLast('.'),
                        uri = ContentUris.withAppendedId(uri, c.getLong(idCol)),
                        path = c.getString(dataCol),
                    )
                }
                c.count to out
            } ?: (0 to emptyList())
        } catch (e: Exception) {
            0 to emptyList()
        }
    }

    /** Recorre la USB (hasta 4 niveles, máx. 2000 archivos) buscando videos, más recientes primero. */
    private fun walk(roots: List<String>): List<File> {
        val found = ArrayList<File>()
        var visited = 0
        fun visit(dir: File, depth: Int) {
            val children = dir.listFiles() ?: return
            for (f in children) {
                if (++visited > 2000) return
                if (f.isDirectory) {
                    if (depth < 4 && !f.name.startsWith(".")) visit(f, depth + 1)
                } else if (f.extension.lowercase() in VIDEO_EXT) {
                    found += f
                }
            }
        }
        roots.forEach { visit(File(it), 0) }
        return found.sortedByDescending { it.lastModified() }
    }

    private companion object {
        val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "3gp", "ts", "flv", "wmv", "m4v", "mpg", "mpeg", "vob", "rmvb")
    }
}
