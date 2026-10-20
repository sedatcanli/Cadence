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
    VoiceModel("en-IN", "English (India)", "vosk-model-small-en-in-0.4"),
    VoiceModel("tr", "Türkçe", "vosk-model-small-tr-0.3"),
    VoiceModel("de", "Deutsch", "vosk-model-small-de-0.15"),
    VoiceModel("fr", "Français", "vosk-model-small-fr-0.22"),
    VoiceModel("es", "Español", "vosk-model-small-es-0.42"),
    VoiceModel("pt", "Português", "vosk-model-small-pt-0.3"),
    VoiceModel("it", "Italiano", "vosk-model-small-it-0.22"),
    VoiceModel("nl", "Nederlands", "vosk-model-small-nl-0.22"),
    VoiceModel("ca", "Català", "vosk-model-small-ca-0.4"),
    VoiceModel("ru", "Русский", "vosk-model-small-ru-0.22"),
    VoiceModel("uk", "Українська", "vosk-model-small-uk-v3-nano"),
    VoiceModel("pl", "Polski", "vosk-model-small-pl-0.22"),
    VoiceModel("cs", "Čeština", "vosk-model-small-cs-0.4-rhasspy"),
    VoiceModel("sv", "Svenska", "vosk-model-small-sv-rhasspy-0.15"),
    VoiceModel("hi", "हिन्दी", "vosk-model-small-hi-0.22"),
    VoiceModel("gu", "ગુજરાતી", "vosk-model-small-gu-0.42"),
    VoiceModel("te", "తెలుగు", "vosk-model-small-te-0.42"),
    VoiceModel("ar", "العربية", "vosk-model-ar-mgb2-0.4"),
    VoiceModel("fa", "فارسی", "vosk-model-small-fa-0.42"),
    VoiceModel("tg", "Тоҷикӣ", "vosk-model-small-tg-0.22"),
    VoiceModel("uz", "Oʻzbekcha", "vosk-model-small-uz-0.22"),
    VoiceModel("kk", "Қазақша", "vosk-model-small-kz-0.42"),
    VoiceModel("ky", "Кыргызча", "vosk-model-small-ky-0.42"),
    VoiceModel("ka", "ქართული", "vosk-model-small-ka-0.42"),
    VoiceModel("zh", "中文", "vosk-model-small-cn-0.22"),
    VoiceModel("ja", "日本語", "vosk-model-small-ja-0.22"),
    VoiceModel("ko", "한국어", "vosk-model-small-ko-0.22"),
    VoiceModel("vi", "Tiếng Việt", "vosk-model-small-vn-0.4"),
    VoiceModel("tl", "Filipino", "vosk-model-tl-ph-generic-0.6"),
    VoiceModel("eo", "Esperanto", "vosk-model-small-eo-0.42"),
    VoiceModel("br", "Brezhoneg", "vosk-model-br-0.8")
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
