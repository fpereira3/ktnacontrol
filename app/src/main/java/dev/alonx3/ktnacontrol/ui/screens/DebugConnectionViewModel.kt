package dev.alonx3.ktnacontrol.ui.screens

import android.app.Application
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.alonx3.ktnacontrol.device.KatanaLink
import dev.alonx3.ktnacontrol.device.KatanaControl
import dev.alonx3.ktnacontrol.device.KatanaEnumParameter
import dev.alonx3.ktnacontrol.device.KatanaParameter
import dev.alonx3.ktnacontrol.device.KatanaRepository
import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.BoostType
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.ChainBlock
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.EqParams
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.ModFxInternalParams
import dev.alonx3.ktnacontrol.protocol.ParamKind
import dev.alonx3.ktnacontrol.protocol.ParamSpec
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.PresetSave
import dev.alonx3.ktnacontrol.protocol.displayToRaw
import dev.alonx3.ktnacontrol.protocol.rawToDisplay
import dev.alonx3.ktnacontrol.protocol.ReverbType
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import dev.alonx3.ktnacontrol.protocol.awaitRolandReply
import dev.alonx3.ktnacontrol.protocol.sendAndCollect
import dev.alonx3.ktnacontrol.usb.KatanaHandshake
import dev.alonx3.ktnacontrol.usb.KatanaUsbScanner
import dev.alonx3.ktnacontrol.usb.KatanaUsbTransport
import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import dev.alonx3.ktnacontrol.usb.UsbDeviceEvent
import dev.alonx3.ktnacontrol.usb.UsbLogLine
import dev.alonx3.ktnacontrol.usb.UsbOpenResult
import dev.alonx3.ktnacontrol.usb.describe
import dev.alonx3.ktnacontrol.usb.packUsbMidi
import dev.alonx3.ktnacontrol.usb.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dev.alonx3.ktnacontrol.library.PresetLibrary
import dev.alonx3.ktnacontrol.library.SaveResult

/**
 * Drives the diagnostics screen, which works as a **live monitor**: once the transport is
 * open a read loop runs continuously and every message the amp sends is logged, whether or
 * not the screen asked for it. The buttons only ever send; their answers arrive through the
 * same stream as everything else.
 *
 * Holds the transport across configuration changes so rotating does not re-claim the
 * interface.
 */
class DebugConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = KatanaUsbScanner(application)
    private var transport: KatanaUsbTransport? = null
    private var readJob: Job? = null

    private val _log = MutableStateFlow<List<UsbLogLine>>(emptyList())
    val log: StateFlow<List<UsbLogLine>> = _log.asStateFlow()

    private val _state = MutableStateFlow<UsbConnectionState>(UsbConnectionState.Idle)
    val state: StateFlow<UsbConnectionState> = _state.asStateFlow()

    /**
     * Every message the read loop produces, fanned out to whoever is interested: the log,
     * and any query waiting for its own answer.
     *
     * There is exactly **one** reader of the endpoint — the [readJob] below. Nothing else
     * may call `incomingMessages()`, or two loops would steal each other's messages.
     */
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val incomingMessages: SharedFlow<ByteArray> = incoming.asSharedFlow()

    private var repository: KatanaRepository? = null

    /**
     * Cached value of every level, keyed by [LevelId]; null until it is read or the amp
     * reports it.
     *
     * One map instead of six `StateFlow`s: adding a parameter no longer means adding a field,
     * a mirror job, two handlers and a slider.
     */
    private val _levels = MutableStateFlow(LevelId.entries.associateWith { null as Int? })
    val levels: StateFlow<Map<LevelId, Int?>> = _levels.asStateFlow()

    /**
     * Cached value of the amp-type selectors, keyed by [SelectorId].
     *
     * Keyed by the UI's own enum, never by [Address]: the screen must not learn addresses
     * (CLAUDE.md §4.2). Three maps rather than one because the three families are addressed
     * by three different things and merging them would need a wrapper key for no gain.
     */
    private val _selectors = MutableStateFlow(SelectorId.entries.associateWith { null as Int? })
    val selectors: StateFlow<Map<SelectorId, Int?>> = _selectors.asStateFlow()

    private val _effectColors =
        MutableStateFlow(EffectId.entries.associateWith { null as Int? })

    /** Green / red / yellow bank of each effect, or null until read. */
    val effectColors: StateFlow<Map<EffectId, Int?>> = _effectColors.asStateFlow()

    private val _effectEnabled =
        MutableStateFlow(EffectId.entries.associateWith { null as Boolean? })

    /**
     * Whether each effect is on, or null until read.
     *
     * ⚠️ `01` is taken to mean "on" — see the warning in
     * [KatanaAddresses.BOOST_ENABLED]: a source can be read as saying the opposite.
     */
    val effectEnabled: StateFlow<Map<EffectId, Boolean?>> = _effectEnabled.asStateFlow()

    private val _effectTypes = MutableStateFlow(EffectId.entries.associateWith { null as Int? })

    /**
     * Tipo de efecto activo de cada uno de los cinco efectos, o null mientras no se sepa.
     *
     * ✅ Los cinco están confirmados con audio (Booster el 2026-09-03; Mod, FX, Delay y
     * Reverb el 2026-09-04).
     */
    val effectTypes: StateFlow<Map<EffectId, Int?>> = _effectTypes.asStateFlow()

    private val _boosterParams =
        MutableStateFlow(BoosterParamId.entries.associateWith { null as Int? })

    /**
     * Booster's five internal continuous parameters, in **display** units (CLAUDE.md §5.2).
     *
     * ✅ Confirmed against the amplifier with audio (2026-09-04).
     */
    val boosterParams: StateFlow<Map<BoosterParamId, Int?>> = _boosterParams.asStateFlow()

    private val _boosterSoloEnabled = MutableStateFlow<Boolean?>(null)

    /** ✅ Booster's Solo switch (`60 00 00 15`). Confirmed with audio (2026-09-04). */
    val boosterSoloEnabled: StateFlow<Boolean?> = _boosterSoloEnabled.asStateFlow()

    private val _delayParams =
        MutableStateFlow(DelayParamId.entries.associateWith { null as Int? })

    /**
     * Delay 1's internal continuous parameters (Time, Feedback, Effect Level, Direct Mix), in
     * **display** units. High Cut is not here: it is a frequency selector, part of
     * [selectors]. ⚠️ Implemented but unconfirmed against the amplifier (CLAUDE.md §5.2).
     */
    val delayParams: StateFlow<Map<DelayParamId, Int?>> = _delayParams.asStateFlow()

    private val _reverbParams =
        MutableStateFlow(ReverbParamId.entries.associateWith { null as Int? })

    /**
     * Reverb's internal continuous parameters (Pre Delay, Density, Direct Mix), in **display**
     * units. Low Cut and High Cut are frequency selectors, part of [selectors]; Time is its own
     * `Double` state ([reverbTime]) and Effect Level is deliberately not implemented — see
     * [ReverbParamId]. ⚠️ Implemented but unconfirmed against the amplifier (CLAUDE.md §5.2).
     */
    val reverbParams: StateFlow<Map<ReverbParamId, Int?>> = _reverbParams.asStateFlow()

    private val _reverbTime = MutableStateFlow<Double?>(null)

    /**
     * ⚠️ Reverb Time, `60 00 05 42`, in display units `0.1..10.0` seconds. Unconfirmed
     * (CLAUDE.md §5.2). Its own `Double?` instead of living in [reverbParams]: it needed
     * [FractionalLevelScale][dev.alonx3.ktnacontrol.protocol.FractionalLevelScale], which the
     * rest of that map's `Int` values do not.
     */
    val reverbTime: StateFlow<Double?> = _reverbTime.asStateFlow()

    private val _modChorusPreDelayLow = MutableStateFlow<Double?>(null)
    private val _modChorusPreDelayHigh = MutableStateFlow<Double?>(null)

    /**
     * ⚠️ Pre Delay of Mod's 2x2 Chorus type, `60 00 02 3A`, in display units `0.0..40.0` ms.
     * Unconfirmed. **Only meaningful while Mod's active type is 2x2 Chorus** — see the KDoc of
     * `KatanaAddresses.MOD_CHORUS_PRE_DELAY_LOW`; the UI hides the control otherwise instead of
     * showing a slider that would silently mean something else.
     */
    val modChorusPreDelayLow: StateFlow<Double?> = _modChorusPreDelayLow.asStateFlow()

    /** ⚠️ Same as [modChorusPreDelayLow], the High band (`60 00 02 3E`). */
    val modChorusPreDelayHigh: StateFlow<Double?> = _modChorusPreDelayHigh.asStateFlow()

    private val _modFxInternalRaw =
        MutableStateFlow<Map<ModFxType, Map<String, Int?>>>(emptyMap())

    /**
     * Valores crudos de los parámetros internos de los 31 tipos de Mod (CLAUDE.md §5.2,
     * `ModFxInternalParams`). Clave externa: el tipo; interna, la etiqueta del parámetro
     * dentro de ese tipo. **Crudo, no mostrado** — a diferencia de [reverbParams] y compañía,
     * aquí la conversión vive en `ParamKind.rawToDisplay`/`displayToRaw` (protocol/,
     * puro): son 192 parámetros con 6 formas de escala distintas, y una tabla los convierte
     * todos igual de bien que 192 propiedades con nombre, sin repetir la conversión 192 veces.
     *
     * Solo tienen sentido los del tipo activo en el slot ([EffectId.MOD]'s `type`, en
     * [selectors]); el resto son bytes de otro tipo que comparte la misma dirección
     * (CLAUDE.md §5.2, "DSP complejo").
     *
     * ⚠️ Nada de esto está confirmado con el amplificador.
     */
    val modFxInternalRaw: StateFlow<Map<ModFxType, Map<String, Int?>>> =
        _modFxInternalRaw.asStateFlow()

    private val _fxInternalRaw =
        MutableStateFlow<Map<ModFxType, Map<String, Int?>>>(emptyMap())

    /** Igual que [modFxInternalRaw] pero para **FX** — mismas direcciones `+ FX_OFFSET`. */
    val fxInternalRaw: StateFlow<Map<ModFxType, Map<String, Int?>>> =
        _fxInternalRaw.asStateFlow()

    // --- Controles sin perilla física (CLAUDE.md §5) ---------------------------------------
    //
    // ⚠️ Todo lo de aquí está implementado y sin confirmar con audio. Los on/off y selectores
    // van por el camino de [selectors], que ya existía; esto es lo que necesita estado propio.

    private val _noPanelParams =
        MutableStateFlow(NoPanelParamId.entries.associateWith { null as Int? })

    /** ⚠️ Los tres niveles continuos sin perilla, en unidades de presentación. Sin confirmar. */
    val noPanelParams: StateFlow<Map<NoPanelParamId, Int?>> = _noPanelParams.asStateFlow()

    /** Los dos valores de un slot de Contour, en unidades de presentación. */
    data class ContourSlotValues(val shape: Int? = null, val freqShift: Int? = null)

    private val _contourSlots =
        MutableStateFlow(List(KatanaAddresses.CONTOUR_SLOT_COUNT) { ContourSlotValues() })

    /**
     * ⚠️ Los tres slots de Contour (`60 00 0F 30`/`38`/`40`). Sin confirmar.
     *
     * ⚠️ **Caen fuera del dump**, así que al conectar llegan por el GET individual de respaldo
     * y no de golpe con el resto — pueden tardar un poco más en poblarse que todo lo demás.
     */
    val contourSlots: StateFlow<List<ContourSlotValues>> = _contourSlots.asStateFlow()

    private val _eq1Raw = MutableStateFlow<Map<String, Int?>>(emptyMap())
    private val _eq2Raw = MutableStateFlow<Map<String, Int?>>(emptyMap())

    /**
     * ⚠️ Los 24 parámetros de EQ1, **en crudo**, por etiqueta de [EqParams.SPECS]. Sin
     * confirmar. Misma forma que [modFxInternalRaw] y por la misma razón: la conversión a lo
     * que se muestra vive en `ParamKind.rawToDisplay`, que es puro y está en `protocol/`.
     */
    val eq1Raw: StateFlow<Map<String, Int?>> = _eq1Raw.asStateFlow()

    /** ⚠️ Igual que [eq1Raw] para EQ2 — mismas direcciones `+ 0x20`. Sin confirmar. */
    val eq2Raw: StateFlow<Map<String, Int?>> = _eq2Raw.asStateFlow()

    // --- Mandar un preset de la Biblioteca al amplificador (CLAUDE.md §5, bloque 5) --------

    /**
     * ⚠️ **La secuencia de mandar un preset entero al amplificador. Destructiva.**
     *
     * Toda la lógica vive en [PresetSendFlow], que no sabe de Compose ni de Android y tiene sus
     * propios tests JVM; aquí solo se le da **con qué mandar**. La regla de siempre: el
     * ViewModel no decide si se puede editar —eso es de la UI, §4.2— y esta capa tampoco mira
     * el edit mode.
     *
     * El envío devuelve **null cuando no hay transporte**, que el flujo distingue de un fallo:
     * sin cable no salió nada y el amplificador está intacto, mientras que un fallo a mitad lo
     * deja con el preset a medias.
     */
    val presetSend = PresetSendFlow(
        scope = viewModelScope,
        send = { image -> sendImageToAmp(image) },
    )

    private suspend fun sendImageToAmp(image: MemoryImage): KatanaRepository.PresetSendResult? {
        val active = repository ?: run {
            appendLog(NO_TRANSPORT)
            return null
        }
        appendLog("→ ENVIAR preset al amplificador (${image.size} B) — destructivo:")
        val result = active.sendPreset(image)
        appendLog("  ${result.verdict}")
        return result
    }

    private val _presetSaveInFlight = MutableStateFlow(false)

    /**
     * ⚠️ Hay un guardado en curso. La UI deshabilita el botón mientras dure.
     *
     * No es cosmético: guardar es destructivo, no se confirma, y **ninguna fuente dice cuánto
     * hay que esperar entre dos guardados** (CLAUDE.md §5 → TBD). Dejar pulsar dos veces
     * seguidas sería justo el escenario del que nadie sabe qué pasa.
     */
    val presetSaveInFlight: StateFlow<Boolean> = _presetSaveInFlight.asStateFlow()

    private val _chainSlots = MutableStateFlow(List<Int?>(ChainBlock.SLOT_COUNT) { null })

    /**
     * ⚠️ Las 20 posiciones de la cadena, en orden (`60 00 06 00`–`06 13`). Sin confirmar.
     *
     * Cada valor es un [ChainBlock]; el conjunto debería ser una permutación de los veinte.
     */
    val chainSlots: StateFlow<List<Int?>> = _chainSlots.asStateFlow()

    private val _ampSoloLevel = MutableStateFlow<Int?>(null)

    /**
     * ⚠️ Amp Solo Level, `60 00 00 2C`, in display units `0..100`. Unconfirmed — part of the
     * PREAMP block that's still missing controls (BACKLOG.md, "Cambiar el tipo de
     * amplificador no recarga nada").
     */
    val ampSoloLevel: StateFlow<Int?> = _ampSoloLevel.asStateFlow()

    private val _ampSoloEnabledPanel = MutableStateFlow<Int?>(null)
    private val _ampSoloLevelPanel = MutableStateFlow<Int?>(null)

    /**
     * ⚠️ Diagnóstico: Solo Sw del amplificador por la **segunda candidata**, `60 00 06 14`.
     *
     * Convive con [SelectorId.AMP_SOLO] (`60 00 00 2B`), que ya se probó y no hizo nada. Ver
     * [KatanaAddresses.AMP_SOLO_ENABLED_PANEL].
     */
    val ampSoloEnabledPanel: StateFlow<Int?> = _ampSoloEnabledPanel.asStateFlow()

    /** ⚠️ Diagnóstico: Solo Level por la segunda candidata, `60 00 06 15`, en `0..100`. */
    val ampSoloLevelPanel: StateFlow<Int?> = _ampSoloLevelPanel.asStateFlow()

    private val _editMode = MutableStateFlow(false)

    /**
     * Whether edit mode was **last switched on by this app** — not what the amp confirms.
     *
     * A Roland write is fire-and-forget (DT1) and never acknowledged, so there is nothing to
     * read back. If the amp is power-cycled or another editor talks to it, this can drift.
     */
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private var repositoryMirror: Job? = null

    /**
     * Único disparador de recarga: la conexión inicial y cada cambio de canal detectado
     * emiten aquí, en vez de lanzar cada uno su propio `loadFromDump()` por su lado.
     *
     * Antes había dos caminos independientes —un `launch` suelto para la conexión y un
     * `collectLatest` aparte para el canal— y podían pisarse: se midieron **dos dumps
     * simultáneos** en JVM (BACKLOG.md, "El estado se desincroniza al cambiar de canal
     * rápido"). Con un solo `StateFlow` y un solo `collectLatest`, una recarga en vuelo
     * siempre cancela a la anterior, así que solo puede haber una a la vez — el [reloadMutex]
     * de abajo es la red de seguridad, no el mecanismo principal.
     */
    private var reloadRequests: MutableStateFlow<ReloadRequest>? = null

    /** El `collectLatest` de [reloadRequests], más el `collect` que alimenta sus cambios de canal. */
    private var reloadJob: Job? = null

    /**
     * Impide que dos `loadFromDump()` corran a la vez aunque algo (un tercer disparador
     * futuro, un error de diseño) se cuele por fuera de [reloadRequests]. Con un solo dueño
     * de la recarga no debería hacer falta nunca, pero es la garantía de que la invariante
     * se cumple pase lo que pase — antes de esto **no existía ningún guard de concurrencia
     * en el proyecto**.
     */
    private val reloadMutex = Mutex()

    /**
     * Qué pidió la última recarga: la apertura de la conexión, o un cambio de canal — y en
     * este segundo caso, a cuál.
     */
    private sealed interface ReloadRequest {
        val reason: String

        object Connection : ReloadRequest {
            override val reason: String = "la conexión"
        }

        data class ChannelChanged(val channel: Int) : ReloadRequest {
            override val reason: String = "el cambio de canal"
        }

        /**
         * El usuario pidió releer, con el botón de refresco.
         *
         * ⚠️ **[nonce] no es decoración: sin él el botón funcionaría una sola vez.**
         * [reloadRequests] es un `MutableStateFlow`, que **descarta un valor igual al que ya
         * tiene**; dos refrescos seguidos son la misma petición y el segundo no dispararía
         * nada. Un contador que sube en cada pulsación los hace distintos.
         */
        data class Manual(val nonce: Long) : ReloadRequest {
            override val reason: String = "el refresco manual"
        }
    }

    /** Lo que hace distinta a cada pulsación del botón de refresco. Ver [ReloadRequest.Manual]. */
    private var manualReloadNonce = 0L

    private val _reloadInFlight = MutableStateFlow(false)

    /** Hay una recarga en curso: leer el dump entero tarda, y no conviene pedir dos a la vez. */
    val reloadInFlight: StateFlow<Boolean> = _reloadInFlight.asStateFlow()

    /**
     * Vuelve a leer el dump completo del canal actual y repuebla todo el estado.
     *
     * ✅ **Reutiliza el coordinador de recargas tal cual**, en vez de llamar a `loadFromDump`
     * por su cuenta: pasa por el mismo `MutableStateFlow` conflado y el mismo `Mutex` que la
     * recarga de conexión y la de cambio de canal. Eso es lo que impide que un refresco a mano
     * se solape con una recarga automática — exactamente el bug de los dumps simultáneos que
     * costó un día de depuración (CLAUDE.md §4.4).
     */
    fun onRefreshClicked() {
        val requests = reloadRequests ?: return appendLog(NO_TRANSPORT)
        requests.value = ReloadRequest.Manual(++manualReloadNonce)
    }

    /**
     * Canal para el que vale el estado que hay cargado ahora mismo.
     *
     * Es lo que evita releer cuando el canal "cambia" al mismo valor que ya teníamos —el
     * caso típico es el GET de respaldo del propio dump, que lo vuelve a leer.
     */
    private var loadedChannel: Int? = null

    /**
     * Último canal que pidió la app, hasta que una recarga confirme si el amplificador lo
     * aceptó. Ver [reportIgnoredChannelWrite].
     */
    private var requestedChannel: Int? = null

    /** Adapts the USB transport to the narrow port the repository depends on. */
    private inner class TransportLink(
        private val activeTransport: KatanaUsbTransport,
    ) : KatanaLink {
        override val incoming: Flow<ByteArray> = incomingMessages
        override suspend fun send(message: ByteArray): Boolean =
            withContext(Dispatchers.IO) { activeTransport.sendRaw(message) >= 0 }
    }

    init {
        if (scanner.isSupported) {
            scanner.start()
            appendLog("Escuchando conexiones USB.")
        } else {
            appendLog("Este dispositivo no expone USB host (UsbManager no disponible).")
            _state.value = UsbConnectionState.Failed("USB host no disponible")
        }
        viewModelScope.launch {
            scanner.permissionResults.collect { result -> onPermissionResult(result) }
        }
        viewModelScope.launch {
            scanner.deviceEvents.collect { event -> onDeviceEvent(event) }
        }
        // Caso 3: el amp ya estaba enchufado antes de abrir la app, así que no llega ningún
        // intent ni broadcast — hay que mirar activamente.
        viewModelScope.launch { autoConnect() }
    }

    /**
     * Se conecta al Katana si ya está enchufado, sin esperar a ningún evento.
     *
     * Cubre el caso de abrir la app con el amplificador puesto de antes: ahí no hay
     * `USB_DEVICE_ATTACHED` (llegó antes de que existiéramos) ni intent de arranque.
     */
    private suspend fun autoConnect() {
        if (!scanner.isSupported || transport != null) return
        val katana = withContext(Dispatchers.IO) { scanner.findKatana() }
        if (katana == null) {
            appendLog("Sin Katana enchufado. Conéctalo, o pulsa «Buscar dispositivo».")
            return
        }
        appendLog("Katana ya enchufado: ${katana.describe().toLogText()}")
        connect(katana)
    }

    /**
     * Caso 1: Android abrió la app al conectar el amplificador y nos pasó el dispositivo en
     * el intent.
     *
     * Ese camino además concede el permiso USB de forma implícita, así que normalmente no
     * aparece el diálogo del sistema.
     */
    fun onDeviceAttachedByIntent(device: UsbDevice) {
        val description = device.describe()
        if (!description.isKatana) {
            appendLog("La app se abrió por un USB que no es el Katana: ${description.toLogText()}")
            return
        }
        appendLog("Abierta al conectar el amplificador: ${description.toLogText()}")
        viewModelScope.launch { connect(device) }
    }

    fun onScanClicked() {
        viewModelScope.launch { scan() }
    }

    /**
     * Sends the mandatory handshake twice. Any reply shows up in the log on its own, through
     * the read loop.
     */
    fun onHandshakeClicked() = sendHandshake(KatanaHandshake.VERSION_GENERIC, "versión genérica")

    /**
     * The one experiment left on the silent handshake (CLAUDE.md §4.1).
     *
     * Sends the same frame with the firmware-version bytes **the amplifier itself reported**
     * in its Identity Reply (`06 00 00 00`) instead of the reference library's zeros. That
     * single byte is the only difference between what the app emits and what the amp emits,
     * and it is the last hypothesis standing.
     *
     * ⚠️ It is also the **last** one that will be tried: nothing implemented needs the
     * handshake, so if this gets no answer the question is closed rather than kept open.
     */
    fun onHandshakeRealVersionClicked() =
        sendHandshake(KatanaHandshake.VERSION_REPORTED, "versión real del amp")

    private fun sendHandshake(version: ByteArray, label: String) {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            val message = KatanaHandshake.message(HANDSHAKE_MODEL_ID, version)
            appendLog("→ Handshake ($label, model 0x%02X), x2 con ~4 ms:".format(HANDSHAKE_MODEL_ID))
            appendLog("  sysex: ${message.toHexString()}")

            val written = withContext(Dispatchers.IO) {
                activeTransport.sendHandshake(HANDSHAKE_MODEL_ID, version)
            }
            written.forEachIndexed { index, bytes ->
                appendLog("  #${index + 1}: ${describeWrite(bytes)}")
            }
            appendLog("  · Si el amp contesta, la respuesta aparecerá sola en el log.")
        }
    }

    /** Sends a universal MIDI Identity Request. */
    fun onIdentityRequestClicked() = send("Identity Request", IDENTITY_REQUEST)

    /**
     * Reads the whole "effective" block at `60 00 00 00`.
     *
     * **The reply is not a single message.** The amp answers a dump with several Roland
     * messages, each carrying its own address — `reference/TuxKatana/HOW.md` traces 5 of 241
     * data bytes plus a final one of 139. So this cannot use [awaitRolandReply], which
     * matches exactly one message by address and would silently return only the first chunk:
     * it collects everything for a generous window and reports the lot.
     */
    fun onReadMemoryDumpClicked() {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            val query = RolandSysEx.get(
                address = KatanaAddresses.MEMORY_DUMP,
                size = KatanaAddresses.MEMORY_DUMP_SIZE,
            )
            appendLog(
                "→ GET dump de memoria (${KatanaAddresses.MEMORY_DUMP}, rango " +
                    "${KatanaAddresses.MEMORY_DUMP_SIZE} B):"
            )
            appendLog("  sysex: ${query.toHexString()}")

            var written = -1
            val seen = sendAndCollect(incomingMessages, DUMP_WINDOW_MS) {
                written = withContext(Dispatchers.IO) { activeTransport.sendRaw(query) }
            }
            appendLog("  ${describeWrite(written)}")
            logMemoryDump(seen)
        }
    }

    private fun logMemoryDump(messages: List<ByteArray>) {
        if (messages.isEmpty()) {
            appendLog("  · Sin respuesta al dump (timeout de ${DUMP_WINDOW_MS} ms).")
            return
        }

        val parsed = messages.map { message -> RolandSysEx.parse(message) }
        val chunks = parsed.filterIsInstance<RolandMessage.Data>()
        val invalid = parsed.filterIsInstance<RolandMessage.Invalid>()

        appendLog("← ${messages.size} mensaje(s) recibidos en la ventana:")
        appendLog(
            "  ${chunks.size} con checksum válido, ${invalid.size} inválido(s); " +
                "${messages.sumOf { it.size }} B en total, " +
                "${chunks.sumOf { it.data.size }} B de datos."
        )
        chunks.forEach { chunk ->
            appendLog("     ${chunk.address}: ${chunk.data.size} B")
        }
        invalid.forEach { bad -> appendLog("     ✗ ${bad.reason}") }

        // The first bytes of the block are the name of the preset in use, so they double as
        // a sanity check: they should match what the amp is showing right now.
        val head = chunks.firstOrNull { chunk -> chunk.address == KatanaAddresses.MEMORY_DUMP }
        if (head == null) {
            appendLog("  ! No llegó el trozo inicial (${KatanaAddresses.MEMORY_DUMP}).")
            return
        }
        val nameBytes = head.data.take(KatanaAddresses.PRESET_NAME_IN_DUMP).toByteArray()
        val name = asAscii(nameBytes) ?: nameBytes.toHexString()
        appendLog("  Preset actual (primeros ${nameBytes.size} B): \"$name\"")
    }

    /**
     * Turns edit mode on or off. On is what makes the amp report its own changes — a
     * front-panel knob, say — so it is what keeps the sliders in sync with the hardware.
     *
     * It **changes the state of the amplifier**, which is why CLAUDE.md §4.2 asks for it to be
     * an explicit, visible control with a way back off, not something switched silently.
     */
    fun onEditModeChanged(enabled: Boolean) = setEditMode(enabled)

    /**
     * Writes edit mode and then watches the stream for a moment.
     *
     * A Roland write is not acknowledged (see `sendAndCollect`), so silence here is the
     * expected outcome, not a failure — the interesting part happens afterwards: with edit
     * mode on, moving a front-panel knob should start producing spontaneous messages in the
     * log, which is the hypothesis being tested.
     */
    private fun setEditMode(enabled: Boolean) {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        val label = if (enabled) "ON" else "OFF"
        val value = if (enabled) KatanaAddresses.EDIT_MODE_ON else KatanaAddresses.EDIT_MODE_OFF
        val message = RolandSysEx.set(KatanaAddresses.EDIT_MODE, byteArrayOf(value))

        // Optimista: un SET no se confirma, así que no hay nada que esperar antes de mover el
        // interruptor.
        _editMode.value = enabled
        viewModelScope.launch {
            appendLog("→ SET Edit Mode $label (${KatanaAddresses.EDIT_MODE}):")
            appendLog("  sysex: ${message.toHexString()}")

            var written = -1
            val seen = sendAndCollect(incomingMessages) {
                written = withContext(Dispatchers.IO) { activeTransport.sendRaw(message) }
            }
            appendLog("  ${describeWrite(written)}")

            if (seen.isEmpty()) {
                appendLog("  · Sin respuesta al poner Edit Mode $label (un SET no se confirma).")
            } else {
                appendLog("  ← ${seen.size} mensaje(s) durante la ventana de espera:")
                seen.forEach { message -> appendLog("     ${message.toHexString()}") }
            }
            if (enabled) {
                appendLog("  Ahora mueve una perilla del amplificador y mira si aparece algo.")
            }
        }
    }

    /** Reads the 16-byte device name at `10 00 00 00`, waiting for its own answer. */
    fun onReadDeviceNameClicked() {
        if (transport == null) return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ GET nombre del dispositivo:")
            val reply = query(KatanaAddresses.DEVICE_NAME, KatanaAddresses.DEVICE_NAME_SIZE)
            appendLog("  ${KatanaAddresses.DEVICE_NAME}: ${describeReply(reply)}")
        }
    }

    /**
     * Queries the names of the 8 user presets **one at a time**, each waiting for its own
     * reply before the next goes out.
     *
     * Firing all eight and reading whatever came back would be a race: the answers arrive on
     * a shared asynchronous stream and nothing guarantees they keep the order of the
     * requests.
     */
    fun onReadPresetNamesClicked() {
        if (transport == null) return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ GET nombres de los ${KatanaAddresses.PRESET_COUNT} presets:")
            for (number in 1..KatanaAddresses.PRESET_COUNT) {
                val address = KatanaAddresses.presetName(number)
                val reply = query(address, KatanaAddresses.PRESET_NAME_SIZE)
                appendLog("  #$number ($address): ${describeReply(reply)}")
            }
        }
    }

    /**
     * Sends a GET for [address] and waits for the reply carrying that same address.
     *
     * A timeout is logged by the caller and does not abort the sequence.
     */
    private suspend fun query(address: Address, size: Int): RolandMessage.Data? {
        val activeTransport = transport ?: return null
        val message = RolandSysEx.get(address, size)
        return awaitRolandReply(incomingMessages, address) {
            withContext(Dispatchers.IO) { activeTransport.sendRaw(message) }
        }
    }

    private fun describeReply(reply: RolandMessage.Data?): String = when {
        reply == null -> "· sin respuesta (timeout)."
        else -> {
            val text = asAscii(reply.data)
            if (text != null) "✓ \"$text\"" else "✓ ${reply.data.size} B: ${reply.data.toHexString()}"
        }
    }

    /** Sends [message], logging what went out. The answer, if any, arrives on the read loop. */
    private fun send(label: String, message: ByteArray) {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ $label:")
            appendLog("  sysex: ${message.toHexString()}")
            appendLog("  wire:  ${packUsbMidi(message).toHexString()}")
            val written = withContext(Dispatchers.IO) { activeTransport.sendRaw(message) }
            appendLog("  ${describeWrite(written)}")
        }
    }

    private fun describeWrite(written: Int): String =
        if (written < 0) "✗ bulkTransfer devolvió -1." else "✓ escritos $written bytes en el cable."

    // --- Connection lifecycle -------------------------------------------------------------

    private suspend fun scan() {
        if (!scanner.isSupported) return
        _state.value = UsbConnectionState.Searching
        closeTransport()

        val devices = withContext(Dispatchers.IO) { scanner.attachedDevices() }
        if (devices.isEmpty()) {
            appendLog("No hay dispositivos USB conectados.")
        } else {
            appendLog("Dispositivos USB detectados: ${devices.size}")
            devices.forEach { device -> appendLog("  • ${device.describe().toLogText()}") }
        }

        val katana = devices.firstOrNull { device -> device.describe().isKatana }
        if (katana == null) {
            appendLog("No se encontró el Katana MK2 (vendorId 1410 / productId 472).")
            _state.value = UsbConnectionState.KatanaNotFound
            return
        }
        appendLog("Katana encontrado: ${katana.describe().toLogText()}")
        connect(katana)
    }

    /**
     * Punto único de conexión para los tres caminos: intent de arranque, broadcast de
     * conexión en caliente, y el botón manual.
     *
     * El guard importa: los tres pueden dispararse casi a la vez —abrir por intent y el
     * escaneo inicial, por ejemplo— y sin él se reclamaría la interfaz dos veces o saldrían
     * dos diálogos de permiso.
     */
    private suspend fun connect(device: UsbDevice) {
        if (transport != null) return
        if (_state.value is UsbConnectionState.AwaitingPermission) return

        if (scanner.hasPermission(device)) {
            openTransport(device)
        } else {
            appendLog("Sin permiso todavía; pidiéndolo al sistema...")
            _state.value = UsbConnectionState.AwaitingPermission
            scanner.requestPermission(device)
        }
    }

    private fun onDeviceEvent(event: UsbDeviceEvent) {
        when (event) {
            is UsbDeviceEvent.Attached -> {
                val description = event.device.describe()
                appendLog("+ USB conectado: ${description.toLogText()}")
                if (description.isKatana) {
                    viewModelScope.launch { connect(event.device) }
                }
            }

            is UsbDeviceEvent.Detached -> {
                val description = event.device.describe()
                appendLog("- USB desconectado: ${description.toLogText()}")
                if (description.isKatana) {
                    closeTransport()
                    _state.value = UsbConnectionState.Idle
                }
            }
        }
    }

    private fun onPermissionResult(result: KatanaUsbScanner.PermissionResult) {
        val device = result.device
        if (!result.granted || device == null) {
            appendLog("  ✗ Permiso denegado.")
            _state.value = UsbConnectionState.Failed("Permiso USB denegado")
            return
        }
        appendLog("  ✓ Permiso concedido.")
        viewModelScope.launch { openTransport(device) }
    }

    private suspend fun openTransport(device: UsbDevice) {
        appendLog("Reclamando interfaz 3 y resolviendo endpoints 0x03 / 0x84...")
        when (val result = withContext(Dispatchers.IO) { scanner.openTransport(device) }) {
            is UsbOpenResult.Success -> {
                transport = result.transport
                appendLog("  ✓ Transporte abierto. Escuchando en continuo.")
                _state.value = UsbConnectionState.Connected(
                    device.describe().product ?: device.deviceName
                )
                startReading(result.transport)
                startRepository(result.transport)
            }

            is UsbOpenResult.Failure -> {
                appendLog("  ✗ ${result.reason}")
                _state.value = UsbConnectionState.Failed(result.reason)
            }
        }
    }

    /**
     * Subscribes to the read loop. Every complete message the amp sends lands in the log,
     * including anything it volunteers on its own — a front-panel knob, say.
     */
    private fun startReading(activeTransport: KatanaUsbTransport) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            activeTransport.incomingMessages().collect { message ->
                incoming.emit(message)
                logIncoming(message)
            }
        }
    }

    private fun logIncoming(message: ByteArray) {
        appendLog("← ${message.size} B: ${message.toHexString()}")
        appendLog("   ${describeIncoming(message)}")
    }

    private fun describeIncoming(message: ByteArray): String {
        // Universal messages (Identity Reply) carry no Roland header or checksum.
        if (message.size >= 2 && (message[1].toInt() and 0xFF) == UNIVERSAL_NON_REALTIME) {
            return "SysEx universal (no Roland), p. ej. Identity Reply."
        }
        return when (val parsed = RolandSysEx.parse(message)) {
            is RolandMessage.Data -> buildString {
                append("Roland @ ").append(parsed.address)
                append(", ").append(parsed.data.size).append(" B de datos")
                asAscii(parsed.data)?.let { text -> append(" → \"").append(text).append('"') }
            }

            is RolandMessage.Invalid -> "No se pudo parsear: ${parsed.reason}"
        }
    }

    /** The payload as text, or null when it does not look like printable ASCII. */
    private fun asAscii(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val printable = data.all { byte -> (byte.toInt() and 0xFF) in 0x20..0x7E }
        return if (printable) String(data, Charsets.US_ASCII).trim() else null
    }

    /**
     * Brings up the repository over the freshly opened transport and reads the one parameter
     * it tracks, so the slider starts from the amp's real value instead of zero.
     */
    private fun startRepository(activeTransport: KatanaUsbTransport) {
        repository?.close()
        repositoryMirror?.cancel()

        val newRepository = KatanaRepository(
            link = TransportLink(activeTransport),
            scope = viewModelScope,
            onDiagnostic = { line -> appendLog("  [diag] $line") },
        )
        repository = newRepository
        repositoryMirror = viewModelScope.launch {
            LevelId.entries.forEach { id ->
                launch {
                    val parameter = parameterIn(newRepository, id)
                    parameter.state.collect { raw ->
                        val shown = raw?.let(parameter.scale::toDisplay)
                        _levels.update { levels -> levels + (id to shown) }
                    }
                }
            }
            SelectorId.entries.forEach { id ->
                launch {
                    selectorIn(newRepository, id).state.collect { value ->
                        _selectors.update { current -> current + (id to value) }
                    }
                }
            }
            EffectId.entries.forEach { effect ->
                val typeControl = typeControlIn(newRepository, effect)
                launch {
                    typeControl.state.collect { value ->
                        _effectTypes.update { current -> current + (effect to value) }
                    }
                }
            }
            BoosterParamId.entries.forEach { id ->
                launch {
                    val parameter = boosterParamIn(newRepository, id)
                    parameter.state.collect { raw ->
                        val shown = raw?.let(parameter.scale::toDisplay)
                        _boosterParams.update { current -> current + (id to shown) }
                    }
                }
            }
            launch {
                newRepository.boostSoloEnabled.state.collect { value ->
                    _boosterSoloEnabled.value = value?.let { it == KatanaAddresses.SWITCH_ON }
                }
            }
            launch {
                newRepository.ampSoloLevel.state.collect { raw ->
                    _ampSoloLevel.value = raw?.let(KatanaAddresses.PANEL_LEVEL_SCALE::toDisplay)
                }
            }
            // ⚠️ Diagnóstico: la segunda candidata del Solo. Se refleja igual que el resto
            // para que un reporte espontáneo del amplificador se vea en la UI — que es
            // justo lo que delata una dirección de solo lectura (§5, `60 00 06 5C`).
            launch {
                newRepository.ampSoloEnabledPanel.state.collect { raw ->
                    _ampSoloEnabledPanel.value = raw
                }
            }
            launch {
                newRepository.ampSoloLevelPanel.state.collect { raw ->
                    _ampSoloLevelPanel.value = raw?.let(KatanaAddresses.PANEL_LEVEL_SCALE::toDisplay)
                }
            }
            DelayParamId.entries.forEach { id ->
                launch {
                    val parameter = delayParamIn(newRepository, id)
                    parameter.state.collect { raw ->
                        val shown = raw?.let(parameter.scale::toDisplay)
                        _delayParams.update { current -> current + (id to shown) }
                    }
                }
            }
            ReverbParamId.entries.forEach { id ->
                launch {
                    val parameter = reverbParamIn(newRepository, id)
                    parameter.state.collect { raw ->
                        val shown = raw?.let(parameter.scale::toDisplay)
                        _reverbParams.update { current -> current + (id to shown) }
                    }
                }
            }
            launch {
                newRepository.reverbTime.state.collect { raw ->
                    _reverbTime.value = raw?.let(newRepository.reverbTime.scale::toDisplay)
                }
            }
            launch {
                newRepository.modChorusPreDelayLow.state.collect { raw ->
                    _modChorusPreDelayLow.value =
                        raw?.let(newRepository.modChorusPreDelayLow.scale::toDisplay)
                }
            }
            launch {
                newRepository.modChorusPreDelayHigh.state.collect { raw ->
                    _modChorusPreDelayHigh.value =
                        raw?.let(newRepository.modChorusPreDelayHigh.scale::toDisplay)
                }
            }
            // Los 192 parámetros internos de los 31 tipos de Mod/FX (CLAUDE.md §5.2): un
            // colector por control, igual que arriba, pero generado desde la tabla en vez de
            // nombrado uno a uno — con 31 tipos, repetir el patrón de `DelayParamId.entries`
            // 192 veces sería más código sin ayudar a nadie a encontrar nada.
            newRepository.modInternalParams.forEach { (type, params) ->
                params.forEach { (label, control) ->
                    launch {
                        control.state.collect { raw ->
                            _modFxInternalRaw.update { current ->
                                current + (type to ((current[type] ?: emptyMap()) + (label to raw)))
                            }
                        }
                    }
                }
            }
            newRepository.fxInternalParams.forEach { (type, params) ->
                params.forEach { (label, control) ->
                    launch {
                        control.state.collect { raw ->
                            _fxInternalRaw.update { current ->
                                current + (type to ((current[type] ?: emptyMap()) + (label to raw)))
                            }
                        }
                    }
                }
            }
            // Controles sin perilla física (CLAUDE.md §5).
            NoPanelParamId.entries.forEach { id ->
                launch {
                    val parameter = noPanelParamIn(newRepository, id)
                    parameter.state.collect { raw ->
                        val shown = raw?.let(parameter.scale::toDisplay)
                        _noPanelParams.update { current -> current + (id to shown) }
                    }
                }
            }
            newRepository.contourSlots.forEachIndexed { index, slot ->
                launch {
                    slot.shape.state.collect { raw ->
                        _contourSlots.update { current ->
                            current.mapIndexed { i, values ->
                                if (i == index) values.copy(shape = raw) else values
                            }
                        }
                    }
                }
                launch {
                    slot.freqShift.state.collect { raw ->
                        val shown = raw?.let(slot.freqShift.scale::toDisplay)
                        _contourSlots.update { current ->
                            current.mapIndexed { i, values ->
                                if (i == index) values.copy(freqShift = shown) else values
                            }
                        }
                    }
                }
            }
            listOf(
                newRepository.eq1Params to _eq1Raw,
                newRepository.eq2Params to _eq2Raw,
            ).forEach { (params, state) ->
                params.forEach { (label, control) ->
                    launch {
                        control.state.collect { raw ->
                            state.update { current -> current + (label to raw) }
                        }
                    }
                }
            }
            newRepository.chainSlots.forEachIndexed { index, control ->
                launch {
                    control.state.collect { raw ->
                        _chainSlots.update { current ->
                            current.mapIndexed { i, value -> if (i == index) raw else value }
                        }
                    }
                }
            }
            EffectId.entries.forEach { effect ->
                launch {
                    colorIn(newRepository, effect).state.collect { value ->
                        _effectColors.update { current -> current + (effect to value) }
                    }
                }
                launch {
                    enabledIn(newRepository, effect).state.collect { value ->
                        _effectEnabled.update { current ->
                            current + (effect to value?.let { it == KatanaAddresses.SWITCH_ON })
                        }
                    }
                }
            }
        }
        // **Una** petición en vez de 24 GET en serie: el amplificador contesta el dump con
        // varios mensajes de golpe y todas las direcciones que la app controla viven dentro
        // de `60 00 00 00`. Lo que el dump no cubra se recupera con su GET individual.
        startReloadCoordinator(newRepository)
    }

    /**
     * Único punto de disparo de recargas: la conexión inicial y cada cambio de canal activo
     * pasan por aquí, nunca por su cuenta.
     *
     * Cada canal (1A–4A, 1B–4B, PANEL) tiene sus propios valores: niveles, modelo de
     * amplificador, colores, on/off y tipos de efecto son todos distintos. Sin releer al
     * cambiar de canal la app seguiría mostrando los del canal anterior, que es peor que no
     * mostrar nada — parecería que el amplificador dice una cosa cuando dice otra.
     *
     * ⚠️ **Antes esto eran dos caminos separados** —un `launch` suelto para la conexión y un
     * `collectLatest` aparte observando el canal— **y se medían dos dumps simultáneos**
     * (BACKLOG.md, "El estado se desincroniza al cambiar de canal rápido"). Ahora los dos
     * disparadores emiten al mismo [ReloadRequest] conflado, así que solo hay un
     * `collectLatest` y por tanto una sola recarga en vuelo en todo momento — el
     * [reloadMutex] de [reload] es la red de seguridad, no lo que hace el trabajo.
     *
     * Detalles que hacen que esto no se muerda la cola:
     *  - **Da igual quién cambió el canal.** Se observa el estado del control, así que entra
     *    tanto el cambio hecho desde la app como el del footswitch físico.
     *  - **No hay bucle**: el canal vive en `00 01 00 00`, fuera del dump, así que recargar no
     *    lo reescribe. El GET de respaldo lo relee, pero un `StateFlow` no reemite un valor
     *    igual, así que ahí se para. Y ahora que el dump filtra por [blockReplyIn][dev.alonx3.ktnacontrol.protocol.blockReplyIn]
     *    (CLAUDE.md §4.4), un reporte de canal que llegue mientras el dump está en vuelo ya
     *    no puede colarse como si fuera un trozo de memoria.
     *  - **`collectLatest` + un margen** cancelan la recarga en vuelo si el canal vuelve a
     *    cambiar: pasar 1A→2A→3A rápido hace **una** recarga, la del canal donde te quedaste.
     *    El margen además le da tiempo al amplificador a cambiar de canal de verdad antes de
     *    preguntarle en qué estado quedó.
     *  - **El guard se vuelve a comprobar después del margen, no solo antes.** Antes solo se
     *    miraba antes de esperar; ahora también al final, que es donde de verdad importa que
     *    la condición siga siendo cierta.
     */
    private fun startReloadCoordinator(repository: KatanaRepository) {
        reloadJob?.cancel()
        val requests = MutableStateFlow<ReloadRequest>(ReloadRequest.Connection)
        reloadRequests = requests
        reloadJob = viewModelScope.launch {
            launch {
                repository.channel.state.filterNotNull().collect { channel ->
                    requests.value = ReloadRequest.ChannelChanged(channel)
                }
            }
            requests.collectLatest { request ->
                when (request) {
                    is ReloadRequest.ChannelChanged -> {
                        // El guard se reevalúa **después** del margen, no solo antes: durante
                        // esos 300 ms puede haber llegado ya el valor que esperábamos.
                        if (request.channel == loadedChannel) return@collectLatest
                        delay(CHANNEL_RELOAD_SETTLE_MS)
                        if (request.channel == loadedChannel) return@collectLatest
                        appendLog(
                            "↻ Canal ${describeChannel(request.channel)}: releyendo todo el estado..."
                        )
                    }

                    // El refresco manual **no** pasa por el guard de canal: el usuario lo pide
                    // precisamente cuando sospecha que el estado no cuadra, y saltárselo por
                    // "ya estamos en ese canal" sería no hacer nada justo cuando hace falta.
                    is ReloadRequest.Manual ->
                        appendLog("↻ Refresco manual: releyendo todo el estado...")

                    ReloadRequest.Connection ->
                        appendLog("→ Poblando el estado desde el dump de memoria...")
                }
                reloadMutex.withLock { reload(repository, request.reason) }
            }
        }
    }

    /** Lee el dump y deja anotado para qué canal vale lo que se acaba de cargar. */
    private suspend fun reload(repository: KatanaRepository, reason: String) {
        _reloadInFlight.value = true
        val load = try {
            repository.loadFromDump()
        } finally {
            _reloadInFlight.value = false
        }
        loadedChannel = repository.channel.state.value
        logDumpLoad(load)
        if (load.messages == 0) {
            appendLog("  ! Tras $reason el dump no contestó; el estado puede estar viejo.")
        }
        reportIgnoredChannelWrite()
    }

    /**
     * Avisa cuando el amplificador **no aceptó** un cambio de canal que pidió la app.
     *
     * El síntoma sin esto es desconcertante: el selector se mueve y vuelve solo un momento
     * después, sin que el log diga por qué. Y no es un fallo de la app — es la app
     * funcionando bien: la escritura es optimista, el amplificador la ignora, y la relectura
     * del canal (`00 01 00 00`, que vive fuera del dump y siempre cae al GET de respaldo)
     * trae el valor real y lo pisa. Es exactamente la misma firma que delató que
     * `60 00 06 5C` era de solo lectura (CLAUDE.md §5).
     *
     * Se menciona Edit Mode porque es la causa observada (BACKLOG.md, "Pendiente por
     * probar"), pero el aviso se da igual con Edit Mode encendido: si la escritura no cuaja
     * con edit mode activo, eso es otra cosa y también hay que verla.
     */
    private fun reportIgnoredChannelWrite() {
        val requested = requestedChannel ?: return
        requestedChannel = null
        val actual = loadedChannel ?: return
        if (actual == requested) return
        appendLog(
            "  ! Se pidió el canal ${describeChannel(requested)} pero el amplificador sigue " +
                "en ${describeChannel(actual)}." +
                if (!_editMode.value) " Edit Mode está apagado." else ""
        )
    }

    private fun describeChannel(value: Int): String = when (value) {
        0 -> "Panel"
        in 1..4 -> "A$value"
        in 5..8 -> "B${value - 4}"
        else -> "desconocido ($value)"
    }

    private fun logDumpLoad(load: KatanaRepository.DumpLoad) {
        if (load.messages == 0) {
            appendLog("  ! El dump no contestó; los controles quedan en desconocido.")
            return
        }
        appendLog(
            "← dump: ${load.messages} mensaje(s), ${load.dataBytes} B de datos" +
                if (load.invalidMessages > 0) ", ${load.invalidMessages} inválido(s)" else ""
        )
        appendLog(
            "  ${load.fromDump} control(es) poblados del dump" +
                ", ${load.fromFallbackGet} con GET de respaldo" +
                if (load.stillUnknown > 0) ", ${load.stillUnknown} sin conocer" else ""
        )
        if (load.rejectedDuringWindow.isNotEmpty()) {
            val total = load.rejectedDuringWindow.values.sum()
            val summary = load.rejectedDuringWindow.entries.joinToString(", ") { (address, count) ->
                if (count > 1) "$address ×$count" else "$address"
            }
            appendLog("  $total reporte(s) ignorado(s) durante la ventana: $summary")
        }
        appendLog("  ${load.state.summary()}")
    }

    private val library = PresetLibrary(application)

    private val _exportInFlight = MutableStateFlow(false)

    /** Hay una exportación en curso: leer el dump entero tarda. */
    val exportInFlight: StateFlow<Boolean> = _exportInFlight.asStateFlow()

    /**
     * Exporta el estado actual del amplificador a un `.tsl` de la biblioteca.
     *
     * **No es destructivo**: solo lee del amplificador y escribe un fichero nuevo en la carpeta
     * de la app. Nada que ver con el guardado a un canal, que sí sobrescribe el amp.
     *
     * ⚠️ **Lo que el dump no cubra no va en el fichero**, y se dice en el log en vez de
     * rellenarse con ceros — ver [KatanaRepository.exportImage] y `TslWriter`.
     */
    fun onExportPreset(name: String) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        if (_exportInFlight.value) return
        _exportInFlight.value = true
        viewModelScope.launch {
            try {
                appendLog("→ Exportando el estado actual a «$name»…")
                val image = active.exportImage()
                when (val result = library.save(image = image, name = name)) {
                    is SaveResult.Saved -> {
                        appendLog("  ← guardado en ${result.entry.fileName} (${image.size} B leídos).")
                        if (result.omitted.isNotEmpty()) {
                            appendLog("  · no incluidos: ${result.omitted.joinToString("; ")}")
                        }
                    }

                    is SaveResult.Failed -> appendLog("  ! ${result.reason}")
                }
            } finally {
                _exportInFlight.value = false
            }
        }
    }

    private fun selectorIn(repository: KatanaRepository, id: SelectorId): KatanaEnumParameter =
        ControlBinding.selector(repository, id)

    private fun colorIn(repository: KatanaRepository, effect: EffectId): KatanaEnumParameter =
        ControlBinding.color(repository, effect)

    private fun enabledIn(repository: KatanaRepository, effect: EffectId): KatanaEnumParameter =
        ControlBinding.enabled(repository, effect)

    private fun parameterIn(repository: KatanaRepository, id: LevelId): KatanaParameter =
        ControlBinding.level(repository, id)

    /**
     * Reads one level straight from the amp: the GET half of an address test (CLAUDE.md §5).
     *
     * A write that never landed and a write the amp accepts but ignores look identical from
     * the app; this plus the ear is what tells them apart.
     */
    fun onReadLevelClicked(id: LevelId) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = parameterIn(active, id)
        viewModelScope.launch {
            appendLog("→ GET ${id.logName} (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de ${id.logName}."
            )
        }
    }

    /**
     * Moves one level, in what the UI shows. Optimistic and debounced, so the slider never
     * lags the finger.
     *
     * The byte that travels is not necessarily this number: the five effect levels are offset
     * by one because their `0` means Off. See `LevelScale`.
     */
    fun onLevelChanged(id: LevelId, value: Int) {
        val active = repository ?: return
        parameterIn(active, id).setLevel(value)
    }

    /**
     * Picks an amp category or model, or the active channel. ✅ Ambas direcciones confirmadas.
     *
     * **El SET sale siempre, sin mirar Edit Mode** — no hay ni ha habido nunca un gate de edit
     * mode en el camino de escritura (`device/` y `protocol/` no saben qué es el edit mode).
     * Lo que decide qué se puede tocar es la UI, que deshabilita los parámetros con edit mode
     * apagado y deja el canal siempre disponible. Ver el contrato en CLAUDE.md §4.2.
     */
    fun onSelectorChanged(id: SelectorId, value: Int) {
        val active = repository ?: return
        if (id == SelectorId.ACTIVE_CHANNEL) requestedChannel = value
        selectorIn(active, id).set(value)
    }

    /**
     * Turns the amp's VARIATION on or off — **by writing the model, not the variation flag**.
     *
     * `60 00 06 5C` only reports (confirmed 2026-09-03): a SET there is ignored and the amp
     * keeps reporting its real state, so the switch used to flip back on its own. The five
     * base channels each have a `Var [...]` twin in the model list at `60 00 00 21`, which
     * does accept writes, so switching the variation means picking the twin.
     *
     * Needs to know which channel it is on, and takes that from the **category**
     * (`60 00 06 50`) because that one is confirmed to report the physical knob. Does nothing
     * when the category is unknown or when the current model is one of the individual amps —
     * see [ampVariationApplies].
     */
    fun onAmpVariationChanged(enabled: Boolean) {
        val active = repository ?: return
        val category = AmpCategory.fromValue(_selectors.value[SelectorId.AMP_CATEGORY] ?: return)
        if (category == null) {
            appendLog("  ! Variación: no se sabe en qué canal está el amp, no se envía nada.")
            return
        }
        if (!ampVariationApplies.value) {
            appendLog("  ! Variación: el modelo actual no es uno de los cinco canales base.")
            return
        }
        val target = category.typeValue(enabled)
        appendLog(
            "→ Variación ${if (enabled) "ON" else "OFF"} vía modelo: " +
                "${AmpType.fromValue(target)?.displayName ?: target}"
        )
        selectorIn(active, SelectorId.AMP_TYPE).set(target)
    }

    /**
     * Whether the VARIATION switch means anything right now.
     *
     * Only the ten types that pair up with the five knob positions have a variation. With one
     * of the individual models active, toggling would have to guess a channel to jump to, and
     * that would silently change the amp — so the switch is disabled instead.
     *
     * True while the model is unknown, so the control is not dead on arrival before the first
     * read comes back.
     */
    val ampVariationApplies: StateFlow<Boolean> = _selectors
        .map { current ->
            val type = current[SelectorId.AMP_TYPE]?.let { AmpType.fromValue(it) }
            type == null || type.category != null
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Picks the green / red / yellow bank of one effect. ✅ Confirmado. */
    fun onEffectColorChanged(effect: EffectId, value: Int) {
        val active = repository ?: return
        colorIn(active, effect).set(value)
    }

    // --- Tipo de efecto (CLAUDE.md §5.2) --------------------------------------------------

    /**
     * El control de "tipo activo" de un efecto.
     *
     * Los cinco efectos ya tienen catálogo y dirección; solo Booster está confirmado con
     * audio (2026-09-03). Mod, FX, Delay y Reverb usan la misma dirección "gemela" y se
     * espera que se comporten igual, pero eso lo dice el amplificador, no la analogía — ver
     * BACKLOG.md, "Pendiente por probar".
     */
    private fun typeControlIn(
        repository: KatanaRepository,
        effect: EffectId,
    ): KatanaEnumParameter = ControlBinding.typeControl(repository, effect)

    /**
     * Cambia el tipo de efecto escribiendo en su dirección de **tipo activo**.
     *
     * ✅ Confirmado en Booster (2026-09-03): el SET cambia el sonido y se corresponde con el
     * color encendido en el panel, en las dos direcciones. Mod y FX usan la dirección gemela,
     * así que se espera lo mismo — pero no está probado.
     */
    fun onEffectTypeChanged(effect: EffectId, value: Int) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val control = typeControlIn(active, effect)
        appendLog("→ SET tipo de ${effect.logName} = ${describeEffectType(effect, value)} (${control.address})")
        control.set(value)
    }

    private fun describeEffectType(effect: EffectId, value: Int?): String {
        if (value == null) return "desconocido"
        val name = when (effect) {
            EffectId.BOOST -> BoostType.fromValue(value)?.displayName
            EffectId.MOD, EffectId.FX -> ModFxType.fromValue(value)?.displayName
            EffectId.DELAY -> DelayType.fromValue(value)?.displayName
            EffectId.REVERB -> ReverbType.fromValue(value)?.displayName
        }
        return "%s (0x%02X)".format(name ?: "?", value)
    }

    // --- Parámetros internos de Booster (CLAUDE.md §5.2) -----------------------------------
    //
    // ⚠️ Implementados, pendientes de confirmar con audio (2026-09-04). Custom Type y sus
    // cinco parámetros quedan fuera a propósito, ver KatanaAddresses.

    private fun boosterParamIn(repository: KatanaRepository, id: BoosterParamId): KatanaParameter =
        ControlBinding.boosterParam(repository, id)

    /** Moves one of Booster's internal parameters, in what the UI shows. Optimistic, debounced. */
    fun onBoosterParamChanged(id: BoosterParamId, value: Int) {
        val active = repository ?: return
        boosterParamIn(active, id).setLevel(value)
    }

    /** GET half of the audio test for one Booster parameter (CLAUDE.md §5). */
    fun onReadBoosterParamClicked(id: BoosterParamId) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = boosterParamIn(active, id)
        viewModelScope.launch {
            appendLog("→ GET booster ${id.logName} (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de booster ${id.logName}."
            )
        }
    }

    /** Turns Booster's Solo mode on or off (`60 00 00 15`). ✅ Confirmado con audio. */
    fun onBoosterSoloEnabledChanged(enabled: Boolean) {
        val active = repository ?: return
        val value = if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF
        active.boostSoloEnabled.set(value)
    }

    /** Moves the amp's Solo Level (`60 00 00 2C`), in what the UI shows. ⚠️ Sin confirmar. */
    fun onAmpSoloLevelChanged(value: Int) {
        val active = repository ?: return
        active.ampSoloLevel.setLevel(value)
    }

    /** GET half of the audio test for the amp's Solo Level (CLAUDE.md §5). */
    fun onReadAmpSoloLevelClicked() {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = active.ampSoloLevel
        viewModelScope.launch {
            appendLog("→ GET amp solo level (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de amp solo level."
            )
        }
    }

    // --- Diagnóstico: la segunda candidata del Solo del amplificador ----------------------
    //
    // Aparte del control ya cableado (`60 00 00 2B`/`2C`), que se probó y no hizo nada. Ver
    // KatanaAddresses.AMP_SOLO_ENABLED_PANEL.

    /** ⚠️ Diagnóstico: enciende/apaga el Solo por `60 00 06 14`. */
    fun onAmpSoloPanelEnabledChanged(enabled: Boolean) {
        val active = repository ?: return
        val value = if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF
        active.ampSoloEnabledPanel.set(value)
    }

    /** ⚠️ Diagnóstico: mueve el Solo Level por `60 00 06 15`, en lo que muestra la UI. */
    fun onAmpSoloPanelLevelChanged(value: Int) {
        val active = repository ?: return
        active.ampSoloLevelPanel.setLevel(value)
    }

    /** ⚠️ Diagnóstico: GET a las dos direcciones de la segunda candidata. */
    fun onReadAmpSoloPanelClicked() {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            val switch = active.ampSoloEnabledPanel
            appendLog("→ GET solo sw candidata 2 (${switch.address}):")
            val raw = switch.read()
            appendLog(if (raw != null) "  ← el amp responde: $raw" else "  · sin respuesta.")

            val level = active.ampSoloLevelPanel
            appendLog("→ GET solo level candidata 2 (${level.address}):")
            val rawLevel = level.read()
            appendLog(
                if (rawLevel != null) "  ← el amp responde: ${level.scale.toDisplay(rawLevel)} (crudo $rawLevel)"
                else "  · sin respuesta."
            )
        }
    }

    // --- Diagnóstico: SET seguido de GET inmediato ----------------------------------------

    /**
     * Escribe y vuelve a leer, para separar "no suena" de "no acepta la escritura".
     *
     * Es el chequeo que resolvió `60 00 05 48` con el nivel de reverb (CLAUDE.md §5): un
     * control que no produce efecto audible puede ser un parámetro inerte en la dirección
     * correcta, o una dirección mal identificada, y desde fuera se ven igual. El GET
     * inmediato después del SET es lo único que los distingue.
     */
    private fun probe(label: String, control: KatanaControl?, value: Int) {
        val target = control ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ PRUEBA $label (${target.address}) = $value:")
            val result = target.probeWrite(value)
            if (result == null) {
                appendLog("  ! valor no válido para este control.")
                return@launch
            }
            appendLog("  antes=${result.before ?: "sin respuesta"} · pedido=${result.requested} · después=${result.after ?: "sin respuesta"}")
            appendLog("  ⇒ ${result.verdict}")
        }
    }

    /** ⚠️ Diagnóstico: SET + GET de Bright (`60 00 00 29`). */
    fun onProbeAmpBrightClicked(enabled: Boolean) = probe(
        label = "bright",
        control = repository?.ampBright,
        value = if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF,
    )

    /** ⚠️ Diagnóstico: SET + GET de Gain SW (`60 00 00 2A`), `00` Low / `01` Middle / `02` High. */
    fun onProbeAmpGainSwClicked(value: Int) = probe(
        label = "gain sw",
        control = repository?.ampGainSw,
        value = value,
    )

    /** ⚠️ Diagnóstico: SET + GET del Solo Sw ya cableado, la candidata del PREAMP. */
    fun onProbeAmpSoloPreampClicked(enabled: Boolean) = probe(
        label = "solo sw (candidata 1, PREAMP)",
        control = repository?.ampSoloEnabled,
        value = if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF,
    )

    /** ⚠️ Diagnóstico: SET + GET del Solo Sw por la segunda candidata, la del bloque panel. */
    fun onProbeAmpSoloPanelClicked(enabled: Boolean) = probe(
        label = "solo sw (candidata 2, panel)",
        control = repository?.ampSoloEnabledPanel,
        value = if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF,
    )

    // --- Parámetros internos fijos de Delay 1 y Reverb (CLAUDE.md §5.2) --------------------
    //
    // ⚠️ Implementados, pendientes de confirmar con audio. High Cut (Delay y Reverb) y Low Cut
    // (Reverb) no están aquí: son selectores de frecuencia, van por `selectorIn`/`onSelectorChanged`.

    private fun delayParamIn(repository: KatanaRepository, id: DelayParamId): KatanaParameter =
        ControlBinding.delayParam(repository, id)

    /** Moves one of Delay 1's internal parameters, in what the UI shows. Optimistic, debounced. */
    fun onDelayParamChanged(id: DelayParamId, value: Int) {
        val active = repository ?: return
        delayParamIn(active, id).setLevel(value)
    }

    /** GET half of the audio test for one Delay 1 parameter (CLAUDE.md §5). */
    fun onReadDelayParamClicked(id: DelayParamId) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = delayParamIn(active, id)
        viewModelScope.launch {
            appendLog("→ GET delay ${id.logName} (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de delay ${id.logName}."
            )
        }
    }

    private fun reverbParamIn(repository: KatanaRepository, id: ReverbParamId): KatanaParameter =
        ControlBinding.reverbParam(repository, id)

    /** Moves one of Reverb's internal parameters, in what the UI shows. Optimistic, debounced. */
    fun onReverbParamChanged(id: ReverbParamId, value: Int) {
        val active = repository ?: return
        reverbParamIn(active, id).setLevel(value)
    }

    /** GET half of the audio test for one Reverb parameter (CLAUDE.md §5). */
    fun onReadReverbParamClicked(id: ReverbParamId) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = reverbParamIn(active, id)
        viewModelScope.launch {
            appendLog("→ GET reverb ${id.logName} (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de reverb ${id.logName}."
            )
        }
    }

    // --- Parámetros con paso fraccionario (CLAUDE.md §5.2) ---------------------------------
    //
    // ⚠️ Implementados, pendientes de confirmar con audio. `setLevel`/`read` trabajan en
    // `Double`, no en `Int` — la diferencia de KatanaFractionalParameter frente al resto.

    /** Moves Reverb Time (`60 00 05 42`), in display seconds. Optimistic, debounced. */
    fun onReverbTimeChanged(value: Double) {
        val active = repository ?: return
        active.reverbTime.setLevel(value)
    }

    /** GET half of the audio test for Reverb Time (CLAUDE.md §5). */
    fun onReadReverbTimeClicked() {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = active.reverbTime
        viewModelScope.launch {
            appendLog("→ GET reverb time (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de reverb time."
            )
        }
    }

    /**
     * Moves the Low band of Mod's 2x2 Chorus Pre Delay (`60 00 02 3A`), in display ms.
     * Optimistic, debounced. Only meaningful while Mod's active type is 2x2 Chorus — see
     * `KatanaAddresses.MOD_CHORUS_PRE_DELAY_LOW`.
     */
    fun onModChorusPreDelayLowChanged(value: Double) {
        val active = repository ?: return
        active.modChorusPreDelayLow.setLevel(value)
    }

    /** GET half of the audio test for the Low band (CLAUDE.md §5). */
    fun onReadModChorusPreDelayLowClicked() {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = active.modChorusPreDelayLow
        viewModelScope.launch {
            appendLog("→ GET mod chorus pre delay low (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de mod chorus pre delay low."
            )
        }
    }

    /** Same as [onModChorusPreDelayLowChanged], the High band (`60 00 02 3E`). */
    fun onModChorusPreDelayHighChanged(value: Double) {
        val active = repository ?: return
        active.modChorusPreDelayHigh.setLevel(value)
    }

    /** GET half of the audio test for the High band (CLAUDE.md §5). */
    fun onReadModChorusPreDelayHighClicked() {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = active.modChorusPreDelayHigh
        viewModelScope.launch {
            appendLog("→ GET mod chorus pre delay high (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de mod chorus pre delay high."
            )
        }
    }

    // --- Parámetros internos de los 31 tipos de Mod/FX (CLAUDE.md §5.2) --------------------
    //
    // Genérico por (tipo, etiqueta) en vez de una función por parámetro: con 192 parámetros,
    // una función por cada uno sería ~600 líneas repitiendo la misma forma que
    // `onDelayParamChanged`/`onReverbParamChanged` de arriba. `ParamKind.displayToRaw`/
    // `rawToDisplay` (protocol/, puro) hacen la conversión; aquí solo se busca el control y se
    // llama — misma disciplina de optimista + debounce, heredada de `KatanaControl.set`.

    private fun modFxControlFor(isFx: Boolean, type: ModFxType, label: String): KatanaControl? {
        val active = repository ?: return null
        val byType = if (isFx) active.fxInternalParams else active.modInternalParams
        return byType[type]?.get(label)
    }

    private fun modFxSpecFor(type: ModFxType, label: String): ParamSpec? =
        ModFxInternalParams.byType[type]?.firstOrNull { it.label == label }

    /**
     * Moves one internal parameter of one Mod/FX type, in what the UI shows. Optimistic; the
     * debounce (or lack of it) is whatever [KatanaControl.set] applies for that control's own
     * kind — a continuous level coalesces, an [ParamKind.Enum] sends right away.
     */
    fun onModFxParamChanged(isFx: Boolean, type: ModFxType, label: String, display: Double) {
        val control = modFxControlFor(isFx, type, label) ?: return
        val spec = modFxSpecFor(type, label) ?: return
        control.set(spec.kind.displayToRaw(display))
    }

    /** GET half of the audio test for one internal Mod/FX parameter (CLAUDE.md §5). */
    fun onReadModFxParamClicked(isFx: Boolean, type: ModFxType, label: String) {
        val control = modFxControlFor(isFx, type, label) ?: return appendLog(NO_TRANSPORT)
        val spec = modFxSpecFor(type, label) ?: return
        val effectName = if (isFx) "fx" else "mod"
        viewModelScope.launch {
            appendLog("→ GET $effectName ${type.displayName}/$label (${control.address}):")
            val raw = control.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${spec.kind.rawToDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de $effectName ${type.displayName}/$label."
            )
        }
    }

    // --- Controles sin perilla física (CLAUDE.md §5) ---------------------------------------

    private fun noPanelParamIn(
        repository: KatanaRepository,
        id: NoPanelParamId,
    ): KatanaParameter = ControlBinding.noPanelParam(repository, id)

    /** ⚠️ Mueve uno de los tres niveles sin perilla, en lo que muestra la UI. Sin confirmar. */
    fun onNoPanelParamChanged(id: NoPanelParamId, value: Int) {
        val active = repository ?: return
        noPanelParamIn(active, id).setLevel(value)
    }

    /** GET de uno de los tres niveles sin perilla (CLAUDE.md §5). */
    fun onReadNoPanelParamClicked(id: NoPanelParamId) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val parameter = noPanelParamIn(active, id)
        viewModelScope.launch {
            appendLog("→ GET ${id.logName} (${parameter.address}):")
            val raw = parameter.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${parameter.scale.toDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de ${id.logName}."
            )
        }
    }

    /** ⚠️ Cambia la forma del slot de Contour [slot] (0-based). Sin confirmar. */
    fun onContourShapeChanged(slot: Int, value: Int) {
        val active = repository ?: return
        active.contourSlots.getOrNull(slot)?.shape?.set(value)
    }

    /** ⚠️ Mueve el Freq Shift del slot de Contour [slot] (0-based). Sin confirmar. */
    fun onContourFreqShiftChanged(slot: Int, value: Int) {
        val active = repository ?: return
        active.contourSlots.getOrNull(slot)?.freqShift?.setLevel(value)
    }

    /**
     * GET de los dos controles de un slot de Contour.
     *
     * ⚠️ Es **el único camino** por el que estos seis valores pueden llegar: caen fuera del
     * rango del dump (CLAUDE.md §5), así que el botón no es solo para diagnóstico como en el
     * resto de controles — es la comprobación de que el GET de respaldo funciona ahí.
     */
    fun onReadContourSlotClicked(slot: Int) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        val controls = active.contourSlots.getOrNull(slot) ?: return
        viewModelScope.launch {
            appendLog("→ GET contour ${slot + 1} (${controls.shape.address}, ${controls.freqShift.address}):")
            val shape = controls.shape.read()
            val freq = controls.freqShift.read()
            appendLog(
                if (shape != null || freq != null)
                    "  ← el amp responde: shape=${shape ?: "—"}, freq shift=" +
                        (freq?.let { controls.freqShift.scale.toDisplay(it) } ?: "—")
                else "  · sin respuesta al GET de contour ${slot + 1} — está fuera del dump, así que sin esto no hay valor."
            )
        }
    }

    private fun eqControlFor(isEq2: Boolean, label: String): KatanaControl? {
        val active = repository ?: return null
        return (if (isEq2) active.eq2Params else active.eq1Params)[label]
    }

    private fun eqSpecFor(label: String): ParamSpec? =
        EqParams.SPECS.firstOrNull { it.label == label }

    /** ⚠️ Mueve un parámetro de EQ1 o EQ2, en lo que muestra la UI. Sin confirmar. */
    fun onEqParamChanged(isEq2: Boolean, label: String, display: Double) {
        val control = eqControlFor(isEq2, label) ?: return
        val spec = eqSpecFor(label) ?: return
        control.set(spec.kind.displayToRaw(display))
    }

    /** GET de un parámetro de EQ1 o EQ2 (CLAUDE.md §5). */
    fun onReadEqParamClicked(isEq2: Boolean, label: String) {
        val control = eqControlFor(isEq2, label) ?: return appendLog(NO_TRANSPORT)
        val spec = eqSpecFor(label) ?: return
        val name = if (isEq2) "eq2" else "eq1"
        viewModelScope.launch {
            appendLog("→ GET $name/$label (${control.address}):")
            val raw = control.read()
            appendLog(
                if (raw != null) "  ← el amp responde: ${spec.kind.rawToDisplay(raw)} (crudo $raw)"
                else "  · sin respuesta al GET de $name/$label."
            )
        }
    }

    // ❌ `onChainSlotChanged` se eliminó el 2026-09-06: reordenar la cadena a mano no funciona
    // bien contra el amplificador y la UI ya no lo ofrece. Los veinte controles siguen
    // registrados en `KatanaRepository.chainSlots` **para leerlos** — es lo que alimenta el
    // diagrama del orden real. Ver BACKLOG.md.

    // --- Guardado de presets (CLAUDE.md §5) -------------------------------------------------

    /**
     * ⚠️ **Guarda el estado editado en [channel] (`01`..`08`). Destructivo e irreversible sobre
     * el amplificador**, y sin confirmación por SysEx — ver [KatanaRepository.savePreset].
     *
     * La UI ya pidió confirmación explícita antes de llegar aquí; esto no vuelve a preguntar.
     * Lo que sí hace es **no dejar dos guardados a la vez** ([presetSaveInFlight]): el commit no
     * se confirma y nadie ha documentado el intervalo mínimo entre dos, así que solaparlos sería
     * meterse en territorio desconocido con una operación que no tiene deshacer.
     *
     * Todo el detalle sale por el log, que es donde el usuario puede ver qué se mandó y qué
     * contestó el amplificador al leer de vuelta el nombre.
     */
    fun onSavePresetClicked(name: String, channel: Int) {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        if (channel !in PresetSave.CHANNELS) {
            return appendLog("! Canal destino inválido: $channel. No se guardó nada.")
        }
        if (!_presetSaveInFlight.compareAndSet(expect = false, update = true)) {
            return appendLog("! Ya hay un guardado en curso; se ignora este.")
        }
        viewModelScope.launch {
            try {
                val label = PresetSave.channelLabel(channel)
                appendLog("→ GUARDAR preset en $label (canal $channel) — destructivo:")
                appendLog("  1. nombre → ${KatanaAddresses.CURRENT_PRESET_NAME}: \"$name\"")
                appendLog("  2. commit → ${KatanaAddresses.PRESET_SAVE}: 00 %02X".format(channel))

                val result = active.savePreset(name = name, channel = channel)

                appendLog("  ${result.verdict}")
                if (!result.nameMatches && result.storedName != null) {
                    // El nombre es lo único observable; que no cuadre no prueba que el sonido
                    // no se guardara, y decir "falló" a secas sería afirmar de más.
                    appendLog(
                        "  ⚠️ El nombre no confirma el guardado. Comprobar en el propio " +
                            "amplificador si $label suena como se esperaba."
                    )
                }
            } finally {
                _presetSaveInFlight.value = false
            }
        }
    }

    /** Turns one effect on or off. ✅ Confirmado, incluido `00` = off / `01` = on. */
    fun onEffectEnabledChanged(effect: EffectId, enabled: Boolean) {
        val active = repository ?: return
        val value = if (enabled) KatanaAddresses.SWITCH_ON else KatanaAddresses.SWITCH_OFF
        enabledIn(active, effect).set(value)
    }


    private fun closeTransport() {
        readJob?.cancel()
        readJob = null
        repositoryMirror?.cancel()
        repositoryMirror = null
        reloadJob?.cancel()
        reloadJob = null
        reloadRequests = null
        loadedChannel = null
        requestedChannel = null
        repository?.close()
        repository = null
        _levels.value = LevelId.entries.associateWith { null }
        _selectors.value = SelectorId.entries.associateWith { null }
        _effectColors.value = EffectId.entries.associateWith { null }
        _effectEnabled.value = EffectId.entries.associateWith { null }
        _effectTypes.value = EffectId.entries.associateWith { null }
        _boosterParams.value = BoosterParamId.entries.associateWith { null }
        _boosterSoloEnabled.value = null
        _ampSoloLevel.value = null
        _ampSoloEnabledPanel.value = null
        _ampSoloLevelPanel.value = null
        _delayParams.value = DelayParamId.entries.associateWith { null }
        _reverbParams.value = ReverbParamId.entries.associateWith { null }
        _reverbTime.value = null
        _modChorusPreDelayLow.value = null
        _modChorusPreDelayHigh.value = null
        _modFxInternalRaw.value = emptyMap()
        _fxInternalRaw.value = emptyMap()
        _noPanelParams.value = NoPanelParamId.entries.associateWith { null }
        _contourSlots.value = List(KatanaAddresses.CONTOUR_SLOT_COUNT) { ContourSlotValues() }
        _eq1Raw.value = emptyMap()
        _eq2Raw.value = emptyMap()
        _chainSlots.value = List(ChainBlock.SLOT_COUNT) { null }
        _presetSaveInFlight.value = false
        _editMode.value = false
        transport?.close()
        transport = null
    }

    private fun appendLog(text: String) {
        _log.update { lines ->
            (lines + UsbLogLine(System.currentTimeMillis(), text)).takeLast(MAX_LOG_LINES)
        }
    }

    override fun onCleared() {
        closeTransport()
        scanner.close()
    }

    private companion object {
        /** Universal MIDI Identity Request, broadcast device id (`7F`). No Roland prefix. */
        val IDENTITY_REQUEST = byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())

        /** Second byte of a universal non-realtime SysEx. */
        const val UNIVERSAL_NON_REALTIME = 0x7E

        /**
         * Model id used in the handshake. Swap to [KatanaHandshake.MODEL_ID_MS3] (`0x3B`,
         * the value in the reference library) if `0x33` gets no answer from the amp.
         */
        const val HANDSHAKE_MODEL_ID = KatanaHandshake.MODEL_ID_KATANA

        /**
         * Window for the dump: much longer than a short GET, because the answer is several
         * messages spread over multiple `bulkTransfer` reads.
         */
        const val DUMP_WINDOW_MS = 3_000L

        /**
         * Margen tras un SET de tipo de Booster antes de fotografiar las cuatro direcciones.
         *
         * Un SET no se confirma, así que lo que se espera aquí no es una respuesta sino los
         * **reportes espontáneos** que el amp manda al cambiar de estado — incluido el rebote
         * que delataría una dirección de solo lectura.
         */
        const val BOOST_TYPE_SETTLE_MS = 400L

        /**
         * Margen entre detectar el cambio de canal y releer el estado.
         *
         * Cumple dos funciones a la vez: darle al amplificador tiempo a cambiar de canal de
         * verdad antes de preguntarle, y coalescer los cambios rápidos en una sola recarga.
         */
        const val CHANNEL_RELOAD_SETTLE_MS = 300L

        const val NO_TRANSPORT = "No hay transporte abierto; busca el dispositivo primero."
        const val MAX_LOG_LINES = 500
    }
}
