package com.cadence.speech.transcription

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class VoiceModel(
    val code: String,
    val label: String,
    val id: String
)

val VOICE_MODELS = listOf(
    VoiceModel("en", "English", "vosk-model-small-en-us-0.15"),
    VoiceModel("tr", "Türkçe", "vosk-model-small-tr-0.3"),
    VoiceModel("de", "Deutsch", "vosk-model-small-de-0.15"),
    VoiceModel("fr", "Français", "vosk-model-small-fr-0.22"),
    VoiceModel("es", "Español", "vosk-model-small-es-0.42"),
    VoiceModel("ru", "Русский", "vosk-model-small-ru-0.22")
)

fun voiceModelFor(code: String): VoiceModel =
    VOICE_MODELS.firstOrNull { it.code == code } ?: VOICE_MODELS.first()

object VoskModels {

    fun dir(context: Context, model: VoiceModel): File = File(context.filesDir, model.id)

    fun isReady(dir: File): Boolean =
        File(dir, "final.mdl").isFile ||
            File(dir, "am/final.mdl").isFile ||
            File(dir, "conf/model.conf").isFile

    fun isReady(context: Context, model: VoiceModel): Boolean = isReady(dir(context, model))

    suspend fun ensure(
        context: Context,
        model: VoiceModel,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val target = dir(context, model)
        if (isReady(target)) return@withContext target

        val archive = File(context.cacheDir, "${model.id}.zip")
        if (!archive.exists()) {
            download(context, model, archive, onProgress)
        }

        val temp = File(context.cacheDir, "${model.id}-tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(temp, entry.name)
                if (!outFile.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                    error("Gecersiz model arsivi")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val root = temp.listFiles()?.firstOrNull { it.isDirectory }
            ?: error("Model arsivi bos")
        target.deleteRecursively()
        if (!root.renameTo(target)) {
            root.copyRecursively(target, overwrite = true)
        }
        temp.deleteRecursively()
        archive.delete()
        target
    }

    private fun download(
        context: Context,
        model: VoiceModel,
        archive: File,
        onProgress: (Int) -> Unit
    ) {
        val partial = File(context.cacheDir, "${model.id}.zip.part")
        partial.delete()
        val connection =
            URL("https://alphacephei.com/vosk/models/${model.id}.zip").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Model indirilemedi (HTTP $code)")
            val total = connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val progress = (copied * 100 / total).toInt().coerceIn(0, 100)
                            onProgress(progress)
                        }
                    }
                }
            }
            if (!partial.renameTo(archive)) {
                partial.copyTo(archive, overwrite = true)
                partial.delete()
            }
        } finally {
            connection.disconnect()
        }
    }
}
