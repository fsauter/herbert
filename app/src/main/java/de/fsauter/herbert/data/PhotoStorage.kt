package de.fsauter.herbert.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages device hero photos inside app-private storage (Scoped Storage safe, no storage permission needed).
 */
class PhotoStorage(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    private val cameraCacheDir: File
        get() = File(context.cacheDir, "camera").apply { mkdirs() }

    fun createCameraCaptureUri(): Pair<Uri, File> {
        val captureFile = File(cameraCacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            captureFile,
        )
        return uri to captureFile
    }

    suspend fun savePhotoFromUri(macAddress: String, sourceUri: Uri, oldPhotoPath: String?): String? =
        withContext(Dispatchers.IO) {
            val destFile = nextPhotoFile(macAddress)
            val copied = try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    true
                } == true
            } catch (_: Exception) {
                false
            }
            if (!copied) {
                destFile.delete()
                return@withContext null
            }
            deletePhoto(oldPhotoPath)
            destFile.absolutePath
        }

    suspend fun savePhotoFromCameraFile(
        macAddress: String,
        captureFile: File,
        oldPhotoPath: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (!captureFile.exists() || captureFile.length() == 0L) {
            captureFile.delete()
            return@withContext null
        }
        val destFile = nextPhotoFile(macAddress)
        return@withContext try {
            captureFile.copyTo(destFile, overwrite = true)
            captureFile.delete()
            deletePhoto(oldPhotoPath)
            destFile.absolutePath
        } catch (_: Exception) {
            destFile.delete()
            null
        }
    }

    fun deletePhoto(photoPath: String?) {
        if (photoPath.isNullOrBlank()) return
        val file = File(photoPath)
        if (file.parentFile?.canonicalPath == photosDir.canonicalPath && file.exists()) {
            file.delete()
        }
    }

    private fun nextPhotoFile(macAddress: String): File {
        val safeMac = macAddress.replace(Regex("[^A-Za-z0-9]"), "")
        return File(photosDir, "${safeMac}_${System.currentTimeMillis()}.jpg")
    }
}
