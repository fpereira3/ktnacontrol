package dev.alonx3.ktnacontrol.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.tsl.TslParseResult
import dev.alonx3.ktnacontrol.protocol.tsl.TslParser
import dev.alonx3.ktnacontrol.protocol.tsl.TslPreset
import dev.alonx3.ktnacontrol.protocol.tsl.TslWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Un `.tsl` ya importado: el fichero en la carpeta de la app y lo que se pudo leer de él.
 *
 * El parseo se guarda junto al fichero **porque puede fallar y aun así el fichero sigue ahí**.
 * Un import cuyo contenido no se entiende no se borra ni se esconde: se lista con su motivo,
 * que es lo que permite entender por qué un fichero concreto no sirve.
 */
data class LibraryEntry(
    /** El fichero dentro de la carpeta de la app; el original ya no hace falta. */
    val file: File,
    /** El nombre del fichero tal y como lo eligió el usuario. */
    val fileName: String,
    /** Los presets que trae, o vacío si no se pudo leer. */
    val presets: List<TslPreset>,
    /** Por qué no se pudo leer, o null si sí se pudo. */
    val error: String?,
) {
    val isReadable: Boolean get() = error == null && presets.isNotEmpty()

    /** Qué enseñar en la lista: el nombre del primer preset, o el del fichero si no hay. */
    val title: String get() = presets.firstOrNull()?.name ?: fileName
}

/** Cómo fue un intento de guardar. Los fallos son valores, no excepciones (CLAUDE.md §6). */
sealed interface SaveResult {
    /**
     * Se escribió. [omitted] y [passedThrough] no son errores: son la política de bloques poco
     * fiables de [TslWriter] hecha visible, para que el usuario sepa qué lleva y qué no el
     * fichero que acaba de guardar.
     */
    data class Saved(
        val entry: LibraryEntry,
        /** Claves que **no** están en el fichero, con el motivo. */
        val omitted: List<String>,
        /** Claves copiadas del original sin interpretarlas. */
        val passedThrough: List<String>,
    ) : SaveResult

    data class Failed(val reason: String) : SaveResult
}

/** Cómo fue un intento de importar. Los fallos son valores, no excepciones (CLAUDE.md §6). */
sealed interface ImportResult {
    data class Imported(val entry: LibraryEntry) : ImportResult
    data class Failed(val fileName: String, val reason: String) : ImportResult
}

/**
 * La biblioteca de presets en disco: importar `.tsl`, listarlos y leerlos.
 *
 * **Copia el fichero elegido a la carpeta privada de la app** en vez de guardar su `Uri`. Un
 * `Uri` del Storage Access Framework es un préstamo: el permiso puede caducar al reiniciar, y
 * el usuario puede borrar o mover el original desde Descargas sin saber que la app dependía de
 * él. Copiar cuesta unos kilobytes —un `.tsl` son ~6 KB— y a cambio la biblioteca sigue
 * funcionando pase lo que pase con el original, que es justo lo que se pidió.
 *
 * Se usa [Context.getFilesDir] y no `getExternalFilesDir`: es almacenamiento **privado
 * interno**, no necesita permisos, y encaja con que estos ficheros son datos de la app y no
 * algo que el usuario vaya a buscar con un explorador. Si algún día se quiere que sean
 * visibles desde el PC, `getExternalFilesDir` sería el cambio — de una línea.
 */
class PresetLibrary(private val context: Context) {

    /** Dónde viven las copias. Se crea sola la primera vez. */
    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    /**
     * Copia el `.tsl` de [uri] a la carpeta de la app y lo parsea.
     *
     * **Copia primero y parsea después**, a propósito: así un fichero que no se entiende queda
     * igualmente guardado y listado con su motivo, en vez de desaparecer sin dejar rastro de
     * qué se intentó importar.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: DEFAULT_FILE_NAME
        val target = uniqueTarget(displayName)

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("no se pudo abrir el fichero elegido")
        }
        copied.exceptionOrNull()?.let { failure ->
            target.delete()
            return@withContext ImportResult.Failed(
                displayName,
                "no se pudo copiar: ${failure.message ?: failure::class.simpleName}",
            )
        }

        ImportResult.Imported(read(target))
    }

    /** Los `.tsl` ya importados, del más reciente al más antiguo. */
    suspend fun list(): List<LibraryEntry> = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map(::read)
            .orEmpty()
    }

    /**
     * Escribe [image] como `.tsl` en la biblioteca.
     *
     * @param target el fichero a sobrescribir, o null para crear uno nuevo a partir de [name].
     * @param source el preset del que se partió, si lo hubo: sus claves sirven para copiar
     *   verbatim lo que la app no interpreta (los bloques en disputa). Ver [TslWriter].
     */
    suspend fun save(
        image: MemoryImage,
        name: String,
        memo: String = "",
        target: File? = null,
        source: TslPreset? = null,
    ): SaveResult = withContext(Dispatchers.IO) {
        val written = TslWriter.write(image = image, name = name, memo = memo, source = source)
        val file = target ?: uniqueTarget(sanitizeFileName("$name$TSL_EXTENSION"))
        val saved = runCatching { file.writeText(written.json) }
        saved.exceptionOrNull()?.let { failure ->
            return@withContext SaveResult.Failed(
                "no se pudo escribir «${file.name}»: ${failure.message ?: failure::class.simpleName}"
            )
        }
        SaveResult.Saved(
            entry = read(file),
            omitted = written.omitted.map { "${it.key}: ${it.reason}" },
            passedThrough = written.passedThrough,
        )
    }

    /** Borra una copia de la biblioteca. El fichero original del usuario no se toca. */
    suspend fun delete(entry: LibraryEntry): Boolean = withContext(Dispatchers.IO) {
        entry.file.delete()
    }

    private fun read(file: File): LibraryEntry {
        val text = runCatching { file.readText() }.getOrElse {
            return LibraryEntry(
                file = file,
                fileName = file.name,
                presets = emptyList(),
                error = "no se pudo leer del disco: ${it.message}",
            )
        }
        return when (val result = TslParser.parse(text)) {
            is TslParseResult.Parsed ->
                LibraryEntry(file, file.name, result.presets, error = null)

            is TslParseResult.Invalid ->
                LibraryEntry(file, file.name, emptyList(), error = result.reason)
        }
    }

    /**
     * El nombre que el sistema le da al documento elegido.
     *
     * Un `Uri` del SAF no es una ruta y no tiene por qué tener un nombre legible, así que esto
     * puede devolver null y el llamador cae a [DEFAULT_FILE_NAME].
     */
    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::sanitizeFileName)

    /**
     * Deja el nombre en algo que se pueda usar como fichero.
     *
     * El nombre viene del sistema y podría traer separadores de ruta: sin limpiarlos, un
     * `../algo` escribiría fuera de la carpeta de la biblioteca.
     */
    private fun sanitizeFileName(name: String): String =
        name.map { if (it.isLetterOrDigit() || it in ALLOWED_NAME_CHARS) it else '_' }
            .joinToString("")
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { DEFAULT_FILE_NAME }

    /** Evita pisar un import anterior con el mismo nombre añadiendo `(2)`, `(3)`… */
    private fun uniqueTarget(displayName: String): File {
        val base = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        fun candidate(suffix: String) =
            File(directory, base + suffix + (extension?.let { ".$it" } ?: ""))

        if (!candidate("").exists()) return candidate("")
        var index = 2
        while (candidate(" ($index)").exists()) index++
        return candidate(" ($index)")
    }

    companion object {
        /** Subcarpeta dentro de `filesDir`. */
        const val DIRECTORY_NAME = "presets"

        private const val DEFAULT_FILE_NAME = "preset.tsl"
        /** La extensión que se le pone a un preset guardado desde la app. */
        const val TSL_EXTENSION = ".tsl"

        private const val MAX_FILE_NAME_LENGTH = 96
        private val ALLOWED_NAME_CHARS = setOf(' ', '.', '-', '_', '(', ')')

        /**
         * Tipos MIME que ofrecer en el selector del sistema.
         *
         * ⚠️ Un `.tsl` **no tiene un MIME registrado**, así que Android lo suele reportar como
         * `application/octet-stream` o incluso como `application/json` según el proveedor. Se
         * abre con el comodín de "cualquier tipo" y se filtra por contenido al parsear: acotar
         * el MIME dejaría fuera ficheros perfectamente válidos según de dónde vengan.
         */
        const val OPEN_DOCUMENT_MIME = "*/*"
    }
}
