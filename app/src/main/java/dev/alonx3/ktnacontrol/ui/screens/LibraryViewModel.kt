package dev.alonx3.ktnacontrol.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.alonx3.ktnacontrol.device.model.AmpState
import dev.alonx3.ktnacontrol.library.ImportResult
import dev.alonx3.ktnacontrol.library.LibraryEntry
import dev.alonx3.ktnacontrol.library.PresetLibrary
import dev.alonx3.ktnacontrol.library.SaveResult
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.tsl.TslWriter
import dev.alonx3.ktnacontrol.protocol.tsl.TslPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Un preset abierto para mirarlo: lo que trae el fichero, ya en el modelo de dominio.
 *
 * **[state] sale de `AmpState.from(preset.memory)`, el mismo camino que usa el amplificador en
 * vivo.** Un `.tsl` y un dump acaban en la misma forma —bytes con una dirección— así que la
 * vista de detalle puede reutilizar las tarjetas de Sliders sin que ninguna sepa de dónde
 * vinieron los valores.
 */
data class OpenedPreset(
    val entry: LibraryEntry,
    val preset: TslPreset,
    val state: AmpState,
)

/**
 * La pantalla de Biblioteca: importar `.tsl`, listarlos y abrir uno en **solo lectura**.
 *
 * No habla con el amplificador ni lo necesita: todo sale de ficheros, así que funciona con el
 * cable desconectado. Es también lo que permite probar el import antes de la próxima sesión
 * con guitarra.
 *
 * ⚠️ **Esta mitad no edita ni exporta nada.** Escribir un `.tsl` de vuelta, o mandar un preset
 * importado al amplificador, es trabajo aparte — y mandar uno al amplificador sería además
 * destructivo, con todo lo que eso implica (ver el guardado de presets, CLAUDE.md §5).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val library = PresetLibrary(application)

    private val _entries = MutableStateFlow<List<LibraryEntry>>(emptyList())

    /** Los `.tsl` importados, del más reciente al más antiguo. */
    val entries: StateFlow<List<LibraryEntry>> = _entries.asStateFlow()

    /**
     * Sigue en `true` hasta que la primera lectura de disco termina.
     *
     * ⚠️ **Sin esto no había forma de distinguir** "todavía no leí nada" de "leí y no hay nada":
     * las dos dejaban `entries` vacío. Ver [libraryListStateOf] (CLAUDE.md §4.7, Fase 4), que es
     * quien decide qué enseñar a partir de esto y de [entries] — la Composable no mira `loading`
     * a pelo.
     */
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _opened = MutableStateFlow<OpenedPreset?>(null)

    /** El preset que se está mirando, o null si se está en la lista. */
    val opened: StateFlow<OpenedPreset?> = _opened.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** Hay una importación en curso: copiar y parsear tocan disco. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** El resultado del último import, para enseñarlo una vez. */
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _entries.value = library.list()
            _loading.value = false
        }
    }

    /**
     * Importa el documento que el usuario eligió en el selector del sistema.
     *
     * El fichero se **copia** a la carpeta de la app, así que el original puede borrarse
     * después sin romper nada — ver [PresetLibrary].
     */
    fun onFilePicked(uri: Uri?) {
        if (uri == null) return // el usuario canceló el selector
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val result = library.import(uri)) {
                    is ImportResult.Imported -> {
                        val entry = result.entry
                        _message.value = when {
                            entry.isReadable ->
                                "Importado «${entry.title}» (${entry.presets.size} preset(s))."
                            // Se importó igual: el fichero está, solo que no se entiende.
                            else -> "Importado «${entry.fileName}», pero no se pudo leer: ${entry.error}"
                        }
                    }

                    is ImportResult.Failed ->
                        _message.value = "No se pudo importar «${result.fileName}»: ${result.reason}"
                }
                _entries.value = library.list()
            } finally {
                _busy.value = false
            }
        }
    }

    /** Abre el preset [index] de [entry] en la vista de solo lectura. */
    fun onPresetOpened(entry: LibraryEntry, index: Int) {
        val preset = entry.presets.getOrNull(index) ?: return
        _opened.value = OpenedPreset(
            entry = entry,
            preset = preset,
            state = AmpState.from(preset.memory),
        )
    }

    /** Vuelve a la lista. */
    fun onDetailClosed() {
        _opened.value = null
    }

    fun onDeleteEntry(entry: LibraryEntry) {
        viewModelScope.launch {
            library.delete(entry)
            if (_opened.value?.entry?.file == entry.file) _opened.value = null
            _entries.value = library.list()
            _message.value = "Borrado «${entry.fileName}» de la biblioteca."
        }
    }

    // --- Edición offline (CLAUDE.md §4.5) --------------------------------------------------

    private val _editing = MutableStateFlow<EditingSession?>(null)

    /** La sesión de edición abierta, o null si no se está editando nada. */
    val editing: StateFlow<EditingSession?> = _editing.asStateFlow()

    /**
     * Un preset abierto **para editar**, con su editor y de dónde salió.
     *
     * [origin] es el fichero al que "Guardar" sobrescribe. Es null para un preset nuevo, y
     * entonces solo cabe "Guardar como" — no hay nada que sobrescribir todavía.
     */
    data class EditingSession(
        val editor: PresetEditor,
        val origin: LibraryEntry?,
        val source: TslPreset?,
        val initialName: String,
    )

    /** Abre un preset de la biblioteca para editarlo, sin amplificador. */
    fun onEditPreset(entry: LibraryEntry, index: Int = 0) {
        val preset = entry.presets.getOrNull(index) ?: return
        closeEditor()
        _editing.value = EditingSession(
            editor = PresetEditor(MemoryImage.from(preset.memory), viewModelScope),
            origin = entry,
            source = preset,
            initialName = preset.name,
        )
    }

    /**
     * Crea un preset **desde cero**, sin fichero de partida y sin amplificador.
     *
     * ⚠️ El punto de partida no es un preset de fábrica de Boss: es la imagen neutra que
     * construye [TslWriter.blank] con lo que la app sabe. Ver su KDoc para qué se siembra y por
     * qué (la cadena de efectos no puede ser veinte ceros).
     */
    fun onCreatePreset(name: String) {
        closeEditor()
        _editing.value = EditingSession(
            editor = PresetEditor(TslWriter.blank(name), viewModelScope),
            origin = null,
            source = null,
            initialName = name,
        )
    }

    /**
     * Guarda lo editado.
     *
     * @param asNewFile true para "Guardar como" (crea una copia nueva), false para sobrescribir
     *   el fichero del que se partió. Sin fichero de partida siempre crea uno nuevo.
     */
    fun onSaveEdit(name: String, asNewFile: Boolean) {
        val session = _editing.value ?: return
        _busy.value = true
        viewModelScope.launch {
            try {
                val target = session.origin?.file?.takeIf { !asNewFile }
                val result = library.save(
                    image = session.editor.image,
                    name = name,
                    target = target,
                    source = session.source,
                )
                _message.value = when (result) {
                    is SaveResult.Saved -> {
                        session.editor.markSaved()
                        buildString {
                            append("Guardado «$name» en ${result.entry.fileName}.")
                            // La política de bloques poco fiables, dicha en voz alta: es
                            // información que el usuario necesita para saber qué lleva el
                            // fichero, no ruido de diagnóstico.
                            if (result.passedThrough.isNotEmpty()) {
                                append(" Copiados sin interpretar: ")
                                append(result.passedThrough.size)
                                append(" bloque(s).")
                            }
                            if (result.omitted.isNotEmpty()) {
                                append(" No incluidos: ")
                                append(result.omitted.joinToString("; "))
                            }
                        }
                    }

                    is SaveResult.Failed -> result.reason
                }
                _entries.value = library.list()
                // Tras "Guardar como", lo editado pasa a vivir en el fichero nuevo.
                if (result is SaveResult.Saved) {
                    _editing.value = session.copy(origin = result.entry)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** Cierra la edición y descarta el editor. Los cambios sin guardar se pierden. */
    fun onEditClosed() {
        closeEditor()
    }

    private fun closeEditor() {
        _editing.value?.editor?.close()
        _editing.value = null
    }

    override fun onCleared() {
        closeEditor()
    }

    fun onMessageShown() {
        _message.value = null
    }
}
