package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lo que la barra superior dice de la conexión, sin decidir todavía con qué palabras.
 *
 * Es un enum y no una cadena porque **los textos van en `strings.xml`** (CLAUDE.md §6): esta capa
 * es Kotlin puro y se prueba en JVM; traducir el caso a una frase es trabajo de Compose. La misma
 * separación que ya usan `PresetSendFlow` y su `sendOutcomeText`.
 */
enum class ConnectionIndicator {
    /** Interfaz reclamada y endpoints resueltos: se puede hablar con el amplificador. */
    CONNECTED,

    /** En camino — buscando dispositivos o esperando a que el usuario conceda el permiso. */
    CONNECTING,

    /** No hay amplificador: ni se ha intentado, o se buscó y no estaba. */
    ABSENT,

    /** Se intentó y falló, con un motivo que ya es legible. */
    FAILED,
}

/**
 * Qué puede hacer una pantalla de control con el amplificador **ahora mismo**.
 *
 * ⚠️ **Es la única definición de `canEdit` del proyecto.** Antes la misma expresión
 * —`connected && editMode`— estaba escrita tres veces (`AmpScreen`, `EffectsScreen` y el bloque
 * en vivo de `PresetsScreen`), que es tres sitios donde olvidarse de una condición nueva. Ahora
 * sale de [ShellState.availabilityOf], que tiene tests.
 */
sealed interface ControlAvailability {

    /** Se puede editar: hay cable y Edit Mode. */
    data object Ready : ControlAvailability

    /**
     * Hay amplificador pero **falta el Edit Mode**, así que los controles se enseñan
     * deshabilitados con su aviso (CLAUDE.md §4.2): sin él el amplificador no reporta y la app
     * no podría confirmar nada de lo que escriba.
     */
    data object NeedsEditMode : ControlAvailability

    /**
     * **No hay amplificador.** Lleva el estado exacto para poder explicar *qué* falta —buscando,
     * sin permiso, no encontrado, un fallo— en vez de dejar la pantalla gris sin motivo.
     */
    data class NoAmp(val state: UsbConnectionState) : ControlAvailability

    /** Atajo para los composables: solo [Ready] permite tocar un parámetro. */
    val canEdit: Boolean get() = this is Ready
}

/** Las traducciones de estado de conexión a lo que ve la UI. Kotlin puro, con tests JVM. */
object ShellState {

    /** Qué enseña la barra superior. Exhaustivo sobre [UsbConnectionState] a propósito. */
    fun indicatorOf(state: UsbConnectionState): ConnectionIndicator = when (state) {
        is UsbConnectionState.Connected -> ConnectionIndicator.CONNECTED
        UsbConnectionState.Searching, UsbConnectionState.AwaitingPermission ->
            ConnectionIndicator.CONNECTING
        UsbConnectionState.Idle, UsbConnectionState.KatanaNotFound -> ConnectionIndicator.ABSENT
        is UsbConnectionState.Failed -> ConnectionIndicator.FAILED
    }

    /** El nombre que reportó el amplificador, o null si no hay ninguno conectado. */
    fun deviceNameOf(state: UsbConnectionState): String? =
        (state as? UsbConnectionState.Connected)?.deviceName

    /**
     * Qué puede hacer una pantalla de control.
     *
     * ⚠️ **Sin cable, el Edit Mode da igual**: se devuelve [ControlAvailability.NoAmp] aunque
     * `editMode` esté en true, porque ese true no significa nada sin amplificador que lo haya
     * aceptado. Enseñar "falta Edit Mode" cuando lo que falta es el cable sería mandar al usuario
     * a arreglar lo que no está roto.
     */
    fun availabilityOf(state: UsbConnectionState, editMode: Boolean): ControlAvailability = when {
        state !is UsbConnectionState.Connected -> ControlAvailability.NoAmp(state)
        editMode -> ControlAvailability.Ready
        else -> ControlAvailability.NeedsEditMode
    }
}

/**
 * Qué pestaña está activa y qué hace el back. **Kotlin puro, sin Compose** (CLAUDE.md §4.2, "La
 * navegación").
 *
 * Vive fuera del composable por lo de siempre: dónde está el usuario y qué hace el back son
 * decisiones, y las decisiones se prueban. Lo que queda en Compose es pintar la barra y llamar a
 * [select].
 *
 * ⚠️ **El back replica lo que haría un `NavHost` con `popUpTo(startDestination)`**, que es el
 * patrón estándar de una barra inferior: desde cualquier pestaña se vuelve a la de inicio, y en
 * la de inicio el back **no se consume** para que cierre la app. Escrito a mano porque son tres
 * ramas; ver CLAUDE.md para por qué no se adoptó Navigation Compose.
 */
class ShellNavigation(
    /** Dónde arranca la app. **Ya no es Logs**: la pantalla de inicio es el amplificador. */
    val start: DebugSection = DebugSection.AMP,
) {
    private val _current = MutableStateFlow(start)
    val current: StateFlow<DebugSection> = _current.asStateFlow()

    /** Va a [section]. Idempotente: reelegir la actual no cambia nada. */
    fun select(section: DebugSection) {
        _current.value = section
    }

    /**
     * Atiende el back de sistema.
     *
     * @return true si se consumió (se volvió a [start]); false si ya se estaba en [start] y le
     *   toca al sistema cerrar la app.
     */
    fun onBack(): Boolean {
        if (_current.value == start) return false
        _current.value = start
        return true
    }
}
