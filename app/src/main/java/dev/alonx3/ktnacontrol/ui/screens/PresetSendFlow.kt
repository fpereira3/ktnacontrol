package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.device.KatanaRepository
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.tsl.TslTransfer
import dev.alonx3.ktnacontrol.protocol.tsl.TslTransferPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** El preset que se está mandando: sus bytes y el nombre con el que la UI lo llama. */
data class PresetSendRequest(val name: String, val image: MemoryImage)

/**
 * Cómo acabó un envío. **Ninguno de estos casos afirma que el amplificador aceptara nada**: un
 * SET no se confirma (CLAUDE.md §5), así que lo más que se sabe es si los mensajes salieron.
 */
sealed interface PresetSendOutcome {

    /** Salieron todos los mensajes. Sigue sin ser una confirmación del amplificador. */
    data class Completed(val result: KatanaRepository.PresetSendResult) : PresetSendOutcome

    /**
     * El cable falló a mitad y el envío se cortó: se sabe cuántos mensajes salieron y en qué
     * bloque y trozo se paró, porque `sendPreset` corta en seco y lo nombra.
     *
     * ⚠️ **El amplificador queda con una mezcla**, y eso es lo que hay que decirle al usuario.
     */
    data class Cut(val result: KatanaRepository.PresetSendResult) : PresetSendOutcome

    /**
     * El envío lanzó —típicamente porque el USB desapareció a media escritura—.
     *
     * ⚠️ Es **peor** que [Cut]: aquí ni siquiera se sabe cuántos mensajes llegaron, porque la
     * excepción se lleva por delante la cuenta. El mensaje al usuario lo dice tal cual.
     */
    data class Failed(val detail: String?) : PresetSendOutcome

    /** No había amplificador al que mandar. No salió nada; el amplificador está intacto. */
    data object NoTransport : PresetSendOutcome

    /**
     * El usuario canceló en alguno de los dos pasos de confirmación. **No salió nada.**
     *
     * Existe como resultado y no como un simple "volver a Idle" a propósito: cancelar una
     * operación destructiva merece un "no se mandó nada" explícito, no un silencio que deja al
     * usuario preguntándose si le dio al botón antes de arrepentirse.
     */
    data object Cancelled : PresetSendOutcome
}

/** En qué punto de la secuencia está el envío. La UI se pinta a partir de esto y de nada más. */
sealed interface PresetSendState {

    /** Nada en curso. */
    data object Idle : PresetSendState

    /**
     * Paso 1: **qué se va a mandar y qué NO**, antes de tocar nada.
     *
     * ⚠️ La lista de [TslTransferPlan.skipped] se enseña **aquí, antes**, no en el resultado:
     * esas direcciones se quedan con lo que ya hubiera en el amplificador, así que el preset que
     * acabe sonando es una mezcla. Enterarse de eso después de mandar sería enterarse tarde.
     */
    data class Review(val request: PresetSendRequest, val plan: TslTransferPlan) : PresetSendState

    /** Paso 2: la confirmación final, que no pide datos nuevos, solo un segundo sí. */
    data class Confirm(val request: PresetSendRequest, val plan: TslTransferPlan) : PresetSendState

    /** Mandando. Mientras dure, [PresetSendFlow.request] no admite otro envío. */
    data class Sending(val request: PresetSendRequest, val plan: TslTransferPlan) : PresetSendState

    /** Terminó (o se canceló): [outcome] dice cómo. */
    data class Finished(
        val request: PresetSendRequest,
        val plan: TslTransferPlan?,
        val outcome: PresetSendOutcome,
    ) : PresetSendState
}

/**
 * La secuencia de mandar un preset de la Biblioteca al amplificador, **sin Compose y sin
 * `android.*`**, para que se pueda probar entera en JVM.
 *
 * Vive aquí y no dentro del composable por lo de siempre: lo único que un test JVM no puede
 * ejercitar es el dedo sobre la pantalla, así que todo lo demás —qué diálogo toca, qué bloques
 * se avisan, si se admite un segundo envío, cómo se cuenta un corte a mitad— es lógica y se
 * saca fuera. Lo que queda en Compose es pintar el estado y llamar a estos métodos.
 *
 * ## Las cautelas, que son las mismas del guardado en canal (CLAUDE.md §5)
 *
 * No es un diseño nuevo: es la misma disciplina de `PresetSaveDialog`, aplicada al segundo botón
 * destructivo de la app.
 *
 * 1. **Dos confirmaciones, no una** ([PresetSendState.Review] → [PresetSendState.Confirm]).
 *    Mandar sobrescribe el estado en edición del amplificador y **no hay deshacer**; lo que se
 *    quiere evitar no es un error de datos, es un descuido.
 * 2. **Lo que no se va a escribir se dice antes de escribir**, en el paso 1.
 * 3. **Nunca dos envíos a la vez** ([request] devuelve false mientras hay uno en curso). El
 *    amplificador no confirma un SET y nadie ha documentado a qué ritmo los acepta; solapar dos
 *    ráfagas sería meterse en territorio desconocido con una operación sin deshacer.
 * 4. **El resultado se enseña tal cual lo devuelve `sendPreset`**, sin adornarlo de éxito.
 *
 * @param scope dónde corre el envío. En la app, el del ViewModel.
 * @param send el envío de verdad. **Devuelve null si no hay amplificador**, que es distinto de
 *   fallar: no salió nada y el amp está intacto.
 * @param planner cómo se calcula el plan. Inyectable solo para los tests; en producción es
 *   [TslTransfer.plan].
 */
class PresetSendFlow(
    private val scope: CoroutineScope,
    private val send: suspend (MemoryImage) -> KatanaRepository.PresetSendResult?,
    private val planner: (MemoryImage) -> TslTransferPlan = { TslTransfer.plan(it) },
) {

    private val _state = MutableStateFlow<PresetSendState>(PresetSendState.Idle)
    val state: StateFlow<PresetSendState> = _state.asStateFlow()

    /** Hay un envío en el cable ahora mismo. */
    val inFlight: Boolean get() = _state.value is PresetSendState.Sending

    /**
     * Empieza la secuencia: calcula el plan y enseña el paso 1. **No manda nada.**
     *
     * @return false si ya hay un envío en curso, y entonces no cambia nada. Se rechaza en vez de
     *   encolar: encolar un segundo envío destructivo que el usuario ya no ve venir sería peor
     *   que ignorarlo.
     */
    fun request(name: String, image: MemoryImage): Boolean {
        if (inFlight) return false
        val request = PresetSendRequest(name = name, image = image)
        _state.value = PresetSendState.Review(request, planner(image))
        return true
    }

    /** Paso 1 → paso 2. Desde cualquier otro estado no hace nada. */
    fun onContinue() {
        val current = _state.value as? PresetSendState.Review ?: return
        _state.value = PresetSendState.Confirm(current.request, current.plan)
    }

    /** Paso 2 → paso 1, para poder releer la lista de omitidos sin cancelar del todo. */
    fun onBack() {
        val current = _state.value as? PresetSendState.Confirm ?: return
        _state.value = PresetSendState.Review(current.request, current.plan)
    }

    /**
     * Cancela desde cualquiera de los dos pasos de confirmación. **No sale nada por el cable.**
     *
     * Un envío ya en curso **no se cancela**: los mensajes que ya salieron no se pueden
     * devolver, y parar a la mitad dejaría al amplificador con un preset incompleto sin que
     * nadie se lo haya pedido. Cancelar antes de mandar es gratis; a mitad, no existe.
     */
    fun onCancel() {
        val current = _state.value
        val (request, plan) = when (current) {
            is PresetSendState.Review -> current.request to current.plan
            is PresetSendState.Confirm -> current.request to current.plan
            else -> return
        }
        _state.value = PresetSendState.Finished(request, plan, PresetSendOutcome.Cancelled)
    }

    /**
     * Segundo sí: **manda**. Solo desde [PresetSendState.Confirm], para que no haya forma de
     * llegar aquí saltándose el paso 1.
     */
    fun onConfirmed() {
        val current = _state.value as? PresetSendState.Confirm ?: return
        _state.value = PresetSendState.Sending(current.request, current.plan)
        scope.launch {
            val outcome = try {
                when (val result = send(current.request.image)) {
                    null -> PresetSendOutcome.NoTransport
                    else ->
                        if (result.complete) PresetSendOutcome.Completed(result)
                        else PresetSendOutcome.Cut(result)
                }
            } catch (cancellation: CancellationException) {
                // La corrutina se cancela cuando muere el ViewModel; no es un fallo del envío y
                // no debe convertirse en un mensaje que el usuario ya no va a ver.
                throw cancellation
            } catch (error: Throwable) {
                // El caso real: el USB desaparece a media escritura. Ni siquiera se sabe cuántos
                // mensajes llegaron, y el mensaje al usuario lo dice.
                PresetSendOutcome.Failed(error.message)
            }
            _state.value = PresetSendState.Finished(current.request, current.plan, outcome)
        }
    }

    /** El usuario ya leyó el resultado. Vuelve a [PresetSendState.Idle]. */
    fun onResultShown() {
        if (_state.value is PresetSendState.Finished) _state.value = PresetSendState.Idle
    }
}

/**
 * Lo que la Biblioteca necesita para ofrecer el botón de enviar: el estado y los gestos.
 *
 * Es un puñado de lambdas y un estado, sin `androidx.*`, para que el composable no tenga que
 * conocer al ViewModel del amplificador ni a [PresetSendFlow]. **Null en la Biblioteca significa
 * "aquí no hay amplificador"** —una preview, o la pantalla montada sin conexión— y entonces el
 * botón no se pinta: es más honesto que un botón deshabilitado sin explicación.
 */
data class PresetSendControls(
    val state: PresetSendState,
    /**
     * Se puede escribir: conectado **y** con Edit Mode. Es el mismo `canEdit` que gobierna todo
     * lo destructivo (CLAUDE.md §4.2); sin edit mode el amplificador no reporta y la app no
     * podría confirmar nada de lo que escriba.
     */
    val canSend: Boolean,
    val onRequest: (String, MemoryImage) -> Unit,
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
    val onCancel: () -> Unit,
    val onConfirmed: () -> Unit,
    val onResultShown: () -> Unit,
)
