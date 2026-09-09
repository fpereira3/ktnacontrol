package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.device.KatanaFractionalParameter
import dev.alonx3.ktnacontrol.device.KatanaParameter
import dev.alonx3.ktnacontrol.device.KatanaRepository
import dev.alonx3.ktnacontrol.device.OfflineKatanaLink
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.ModFxType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Todo lo que [SlidersPane] necesita saber para pintarse, en una foto.
 *
 * Offline no hace falta un `StateFlow` por parámetro como en la pantalla en vivo: allí cada
 * valor puede cambiar por su cuenta —el amplificador reporta cuando alguien toca una perilla
 * física— y hay que estar escuchando. Aquí **solo cambia lo que el dedo cambia**, así que
 * rehacer la foto entera después de cada edición es más simple y sale gratis: son unos cientos
 * de lecturas de un `StateFlow` ya resuelto.
 */
data class EditorState(
    val levels: Map<LevelId, Int?> = emptyMap(),
    val selectors: Map<SelectorId, Int?> = emptyMap(),
    val effectColors: Map<EffectId, Int?> = emptyMap(),
    val effectEnabled: Map<EffectId, Boolean?> = emptyMap(),
    val effectTypes: Map<EffectId, Int?> = emptyMap(),
    val boosterParams: Map<BoosterParamId, Int?> = emptyMap(),
    val boosterSoloEnabled: Boolean? = null,
    val ampSoloLevel: Int? = null,
    val delayParams: Map<DelayParamId, Int?> = emptyMap(),
    val reverbParams: Map<ReverbParamId, Int?> = emptyMap(),
    val reverbTime: Double? = null,
    val modChorusPreDelayLow: Double? = null,
    val modChorusPreDelayHigh: Double? = null,
    val modInternalRaw: Map<ModFxType, Map<String, Int?>> = emptyMap(),
    val fxInternalRaw: Map<ModFxType, Map<String, Int?>> = emptyMap(),
    val noPanelParams: Map<NoPanelParamId, Int?> = emptyMap(),
    val contourSlots: List<DebugConnectionViewModel.ContourSlotValues> = emptyList(),
    val eq1Raw: Map<String, Int?> = emptyMap(),
    val eq2Raw: Map<String, Int?> = emptyMap(),
    val chainSlots: List<Int?> = emptyList(),
    /** Si el switch de variación tiene a qué referirse — ver `KatanaAddresses.AMP_VARIATION`. */
    val variationApplies: Boolean = false,
    /** Hay cambios sin guardar. */
    val dirty: Boolean = false,
)

/**
 * Una sesión de edición de un preset **sin amplificador** (CLAUDE.md §4.5).
 *
 * Monta un [KatanaRepository] entero sobre un [OfflineKatanaLink], así que los controles son
 * literalmente los mismos que usa la pantalla en vivo —con su caché, su debounce y su
 * validación de selectores— solo que sus SET acaban en [image] en vez de en el cable. La UI
 * que los pinta también es la misma: [SlidersPane] con `offline = true`.
 *
 * ⚠️ **[image] es la fuente de verdad, no [EditorState].** La foto es para pintar; lo que se
 * guarda en el `.tsl` son los bytes, incluidos los que la app no interpreta (§4.5).
 */
class PresetEditor(
    /** La memoria que se edita. Se modifica en el sitio. */
    val image: MemoryImage,
    scope: CoroutineScope,
) {

    /**
     * ⚠️ **Sin debounce, y esto es corrección, no rendimiento.**
     *
     * El debounce de ~100 ms existe para no inundar el cable USB al arrastrar un slider
     * (CLAUDE.md §4.2). Offline el destino es un `HashMap`: no hay nada que inundar, y el
     * retardo sí tendría una consecuencia real y mala — **mover un slider y tocar "Guardar"
     * acto seguido escribiría el `.tsl` sin ese último cambio**, porque el SET aún estaría
     * esperando su turno. Un preset guardado al que le falta justo lo último que tocaste es
     * el peor fallo posible aquí, y además silencioso.
     */
    private val repository =
        KatanaRepository(OfflineKatanaLink(image), scope, debounceMillis = 0L)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var dirty = false

    init {
        // Sin GET de respaldo: offline preguntar dos veces a la misma imagen no puede dar una
        // respuesta nueva, y con cientos de controles saldría carísimo. Ver `loadFromImage`.
        repository.loadFromImage(image)
        refresh()
    }

    /** Rehace la foto. Se llama después de cada edición. */
    private fun refresh() {
        val ampType = repository.ampType.state.value
        _state.value = EditorState(
            levels = LevelId.entries.associateWith {
                ControlBinding.level(repository, it).displayValue
            },
            selectors = SelectorId.entries.associateWith {
                ControlBinding.selector(repository, it).state.value
            },
            effectColors = EffectId.entries.associateWith {
                ControlBinding.color(repository, it).state.value
            },
            effectEnabled = EffectId.entries.associateWith {
                ControlBinding.enabled(repository, it).state.value?.let { raw ->
                    raw == KatanaAddresses.SWITCH_ON
                }
            },
            effectTypes = EffectId.entries.associateWith {
                ControlBinding.typeControl(repository, it).state.value
            },
            boosterParams = BoosterParamId.entries.associateWith {
                ControlBinding.boosterParam(repository, it).displayValue
            },
            boosterSoloEnabled = repository.boostSoloEnabled.state.value
                ?.let { it == KatanaAddresses.SWITCH_ON },
            ampSoloLevel = repository.ampSoloLevel.displayValue,
            delayParams = DelayParamId.entries.associateWith {
                ControlBinding.delayParam(repository, it).displayValue
            },
            reverbParams = ReverbParamId.entries.associateWith {
                ControlBinding.reverbParam(repository, it).displayValue
            },
            reverbTime = repository.reverbTime.displayValue,
            modChorusPreDelayLow = repository.modChorusPreDelayLow.displayValue,
            modChorusPreDelayHigh = repository.modChorusPreDelayHigh.displayValue,
            modInternalRaw = repository.modInternalParams.mapValues { (_, params) ->
                params.mapValues { (_, control) -> control.state.value }
            },
            fxInternalRaw = repository.fxInternalParams.mapValues { (_, params) ->
                params.mapValues { (_, control) -> control.state.value }
            },
            noPanelParams = NoPanelParamId.entries.associateWith {
                ControlBinding.noPanelParam(repository, it).displayValue
            },
            contourSlots = repository.contourSlots.map { slot ->
                DebugConnectionViewModel.ContourSlotValues(
                    shape = slot.shape.state.value,
                    freqShift = slot.freqShift.displayValue,
                )
            },
            eq1Raw = repository.eq1Params.mapValues { (_, control) -> control.state.value },
            eq2Raw = repository.eq2Params.mapValues { (_, control) -> control.state.value },
            chainSlots = repository.chainSlots.map { it.state.value },
            // El switch de variación solo tiene sentido sobre uno de los cinco canales base o
            // su gemelo `Var [...]`; con cualquier otro modelo no hay a qué referirse.
            variationApplies = ampType != null &&
                AmpCategory.entries.any { category ->
                    category.typeValue(false) == ampType || category.typeValue(true) == ampType
                },
            dirty = dirty,
        )
    }

    /** Marca la edición como sucia y rehace la foto. */
    private fun edited() {
        dirty = true
        refresh()
    }

    /** El nombre guardado en `60 00 00 00`, o null si el preset no lo trae. */
    fun currentName(): String? =
        image.bytesAt(KatanaAddresses.CURRENT_PRESET_NAME, KatanaAddresses.PRESET_NAME_SIZE)
            ?.map { (it.toInt() and 0x7F).toChar() }
            ?.joinToString("")
            ?.trimEnd()
            ?.takeIf { it.isNotBlank() }

    /** Se acaba de guardar: lo que hay en memoria y lo que hay en disco coinciden. */
    fun markSaved() {
        dirty = false
        refresh()
    }

    fun close() = repository.close()

    // --- Mutadores: uno por cada callback de SlidersPane -----------------------------------

    fun onLevelChanged(id: LevelId, value: Int) =
        edit { ControlBinding.level(repository, id).setLevel(value) }

    fun onSelectorChanged(id: SelectorId, value: Int) =
        edit { ControlBinding.selector(repository, id).set(value) }

    /**
     * La variación se **escribe por el modelo** (`00 21`), no por su propia dirección
     * (`06 5C`), que en el amplificador es de solo lectura (CLAUDE.md §5). Offline la
     * distinción daría igual, pero se respeta para que el byte que acaba en el `.tsl` sea
     * exactamente el mismo que escribiría el camino en vivo.
     */
    fun onAmpVariationChanged(enabled: Boolean) = edit {
        val category = AmpCategory.fromValue(repository.ampCategory.state.value ?: return@edit)
            ?: return@edit
        repository.ampType.set(category.typeValue(enabled))
    }

    fun onEffectColorChanged(effect: EffectId, value: Int) =
        edit { ControlBinding.color(repository, effect).set(value) }

    fun onEffectEnabledChanged(effect: EffectId, enabled: Boolean) = edit {
        ControlBinding.enabled(repository, effect).set(
            if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF
        )
    }

    fun onEffectTypeChanged(effect: EffectId, value: Int) =
        edit { ControlBinding.typeControl(repository, effect).set(value) }

    fun onBoosterParamChanged(id: BoosterParamId, value: Int) =
        edit { ControlBinding.boosterParam(repository, id).setLevel(value) }

    fun onBoosterSoloEnabledChanged(enabled: Boolean) = edit {
        repository.boostSoloEnabled.set(
            if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF
        )
    }

    fun onAmpSoloLevelChanged(value: Int) = edit { repository.ampSoloLevel.setLevel(value) }

    fun onDelayParamChanged(id: DelayParamId, value: Int) =
        edit { ControlBinding.delayParam(repository, id).setLevel(value) }

    fun onReverbParamChanged(id: ReverbParamId, value: Int) =
        edit { ControlBinding.reverbParam(repository, id).setLevel(value) }

    fun onReverbTimeChanged(value: Double) = edit { repository.reverbTime.setLevel(value) }

    fun onModChorusPreDelayLowChanged(value: Double) =
        edit { repository.modChorusPreDelayLow.setLevel(value) }

    fun onModChorusPreDelayHighChanged(value: Double) =
        edit { repository.modChorusPreDelayHigh.setLevel(value) }

    fun onNoPanelParamChanged(id: NoPanelParamId, value: Int) =
        edit { ControlBinding.noPanelParam(repository, id).setLevel(value) }

    fun onContourShapeChanged(slot: Int, value: Int) =
        edit { repository.contourSlots.getOrNull(slot)?.shape?.set(value) }

    fun onContourFreqShiftChanged(slot: Int, value: Int) =
        edit { repository.contourSlots.getOrNull(slot)?.freqShift?.setLevel(value) }

    fun onEqParamChanged(isEq2: Boolean, key: String, value: Double) = edit {
        val params = if (isEq2) repository.eq2Params else repository.eq1Params
        when (val control = params[key]) {
            is KatanaFractionalParameter -> control.setLevel(value)
            is KatanaParameter -> control.setLevel(value.toInt())
            else -> control?.set(value.toInt())
        }
    }

    fun onModParamChanged(type: ModFxType, key: String, value: Double) =
        edit { setInternal(repository.modInternalParams[type]?.get(key), value) }

    fun onFxParamChanged(type: ModFxType, key: String, value: Double) =
        edit { setInternal(repository.fxInternalParams[type]?.get(key), value) }

    private fun setInternal(control: dev.alonx3.ktnacontrol.device.KatanaControl?, value: Double) {
        when (control) {
            is KatanaFractionalParameter -> control.setLevel(value)
            is KatanaParameter -> control.setLevel(value.toInt())
            else -> control?.set(value.toInt())
        }
    }

    /**
     * Aplica una edición y rehace la foto.
     *
     * Con `debounceMillis = 0` el SET se lanza sin esperar, así que el byte llega a [image] en
     * cuanto la corrutina corre. La foto se rehace desde la caché optimista del control, que ya
     * está actualizada cuando esto vuelve — igual que en vivo, el slider sigue al dedo.
     */
    private inline fun edit(block: () -> Unit) {
        block()
        edited()
    }

    /** Para los tests: el tipo de amplificador tal cual, sin pasar por la foto. */
    internal fun ampTypeOrNull(): AmpType? =
        repository.ampType.state.value?.let(AmpType::fromValue)
}
