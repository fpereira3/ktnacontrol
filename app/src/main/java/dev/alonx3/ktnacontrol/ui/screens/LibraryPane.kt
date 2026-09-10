package dev.alonx3.ktnacontrol.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.device.model.AmpState
import dev.alonx3.ktnacontrol.library.LibraryEntry
import dev.alonx3.ktnacontrol.library.PresetLibrary
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.tsl.TslUnavailable
import dev.alonx3.ktnacontrol.protocol.PresetSave
import dev.alonx3.ktnacontrol.ui.theme.Spacing
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog

/**
 * **La mitad "Biblioteca" de [PresetsScreen]**: importar `.tsl`, listarlos, mirarlos, editarlos
 * sin amplificador y mandarlos al amp.
 *
 * ⚠️ **No necesita el cable, y eso no es un detalle sino la mitad de su razón de ser**: lee
 * ficheros del teléfono (§4.5), así que funciona desenchufada y se puede probar entre sesiones
 * con la guitarra. Es justo la asimetría que la distingue del bloque en vivo que [PresetsScreen]
 * pinta encima — ver CLAUDE.md §4.2.
 *
 * ⚠️ La única acción de aquí que sale hacia el hardware es **"Enviar al amplificador…"**, dentro
 * de un preset abierto, y es destructiva: va con sus dos confirmaciones y bajo `canEdit`
 * ([PresetSendControls]).
 *
 * ⚠️ **Hasta el 2026-09-06 esto era de solo lectura** y la doc lo decía así; editar, guardar y
 * enviar llegaron después. Se deja anotado porque el KDoc anterior afirmaba lo contrario.
 */
@Composable
fun LibraryPane(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
    /**
     * Con qué mandar un preset al amplificador, o **null si no hay amplificador que valga**
     * (una preview, o la app montada sin la pantalla de conexión). Null oculta el botón entero
     * en vez de enseñarlo apagado: un botón que nunca se va a poder pulsar aquí no informa de
     * nada. Ver [PresetSendControls].
     */
    sendToAmp: PresetSendControls? = null,
    /**
     * Lo que se pinta **encima de la lista, dentro del mismo scroll** — hoy, el bloque en vivo de
     * [PresetsScreen] (guardar en canal, exportar) con su encabezado.
     *
     * ⚠️ **Es una ranura y no un `Column` en el llamador, y esa es justo la corrección**
     * (QA 2026-09-09, A.3: "la Biblioteca no aparece / no se encuentra"). Antes `PresetsScreen`
     * apilaba el bloque en vivo y luego llamaba a esta pantalla con `Modifier.weight(1f)`. En una
     * `Column` de Compose, un hijo con `weight` recibe **lo que sobre** después de medir a los que
     * no la tienen — y si no sobra nada, recibe **altura cero**. El bloque en vivo no tenía tope
     * ni scroll propio, así que en cuanto la pantalla era corta o la escala de fuente grande, se
     * comía el hueco entero y la Biblioteca desaparecía sin dejar rastro: nada que ver y nada que
     * desplazar, porque el contenedor de fuera tampoco hacía scroll.
     *
     * Metiéndolo aquí hay **un solo contenedor con scroll** para toda la pantalla, así que el
     * bloque en vivo empuja la lista hacia abajo en vez de borrarla, y siempre se llega.
     *
     * Se pinta solo en modo lista: con un preset abierto esta pantalla ocupa el ancho entero y
     * vuelve antes de llegar aquí, que es exactamente lo que el llamador quería decir con su
     * antiguo `browsingLibrary`.
     */
    header: @Composable ColumnScope.() -> Unit = {},
) {
    var creatingPreset by rememberSaveable { mutableStateOf(false) }
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // `OpenDocument` es el Storage Access Framework: el selector del sistema, con acceso a
    // Descargas y a cualquier proveedor. No hace falta pedir permisos de almacenamiento — el
    // usuario concede el acceso al elegir el fichero, y de todos modos lo copiamos enseguida.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::onFilePicked,
    )

    val editing by viewModel.editing.collectAsStateWithLifecycle()

    val session = editing
    if (session != null) {
        PresetEditorScreen(
            session = session,
            sendToAmp = sendToAmp,
            busy = busy,
            message = message,
            onMessageShown = viewModel::onMessageShown,
            onSave = viewModel::onSaveEdit,
            onBack = viewModel::onEditClosed,
            modifier = modifier,
        )
        return
    }

    val detail = opened
    if (detail != null) {
        PresetDetail(
            opened = detail,
            sendToAmp = sendToAmp,
            onBack = viewModel::onDetailClosed,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .knobAwareVerticalScroll(),
    ) {
        // Fuera del padding lateral de la lista: el bloque en vivo trae el suyo, y compartirlo
        // le pondría el doble.
        header()

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { picker.launch(arrayOf(PresetLibrary.OPEN_DOCUMENT_MIME)) },
                enabled = !busy,
            ) {
                Text(
                    if (busy) stringResource(R.string.library_importing)
                    else stringResource(R.string.library_import)
                )
            }
            // Empezar de cero no necesita ni fichero ni amplificador: es el caso que hace que
            // la Biblioteca sirva estando lejos del amp.
            OutlinedButton(onClick = { creatingPreset = true }, enabled = !busy) {
                Text(stringResource(R.string.library_new_preset))
            }
        }
        if (creatingPreset) {
            PresetNameDialog(
                title = stringResource(R.string.library_new_preset),
                initialName = "",
                confirmLabel = stringResource(R.string.library_create),
                onConfirm = { name ->
                    creatingPreset = false
                    viewModel.onCreatePreset(name)
                },
                onDismiss = { creatingPreset = false },
            )
        }
        Text(
            text = stringResource(R.string.library_offline_note),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        message?.let { text ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::onMessageShown) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        }

        // ⚠️ `entries.isEmpty()` no bastaba: era igual de cierto "todavía no leí nada" que "leí
        // y no hay nada". `libraryListStateOf` distingue las dos (CLAUDE.md §4.7, Fase 4).
        when (val listState = libraryListStateOf(loading, entries)) {
            LibraryListState.Loading -> Text(
                text = stringResource(R.string.library_loading),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LibraryListState.Empty -> Text(
                text = stringResource(R.string.library_empty),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            is LibraryListState.Loaded -> listState.entries.forEach { entry ->
                LibraryEntryCard(
                    entry = entry,
                    onOpen = { index -> viewModel.onPresetOpened(entry, index) },
                    onEdit = { index -> viewModel.onEditPreset(entry, index) },
                    onDelete = { viewModel.onDeleteEntry(entry) },
                )
            }
        }
        }
    }
}

@Composable
private fun LibraryEntryCard(
    entry: LibraryEntry,
    onOpen: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = entry.fileName, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onDelete) { Text(stringResource(R.string.library_delete)) }
            }

            if (!entry.isReadable) {
                // Un fichero que no se entiende se lista igual, con el motivo: esconderlo
                // dejaría al usuario sin saber por qué su import "no hizo nada".
                Text(
                    text = stringResource(R.string.library_entry_unreadable, entry.error.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            entry.presets.forEachIndexed { index, preset ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // ⚠️ Fase 5 (CLAUDE.md §4.9): es el único área táctil hecha a mano del
                        // proyecto —todo lo demás es `Button`/`TextButton`/`NavigationBarItem`,
                        // que Material 3 ya garantiza en 48 dp por su cuenta—. Sin este mínimo
                        // explícito, la altura de esta fila dependía de un efecto lateral (que
                        // el `TextButton` de "Editar" de dentro empujara la fila hasta los 48 dp
                        // él solo); ponerlo aquí la hace cierta por diseño, no por casualidad de
                        // qué haya al lado.
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .clickable { onOpen(index) }
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = preset.name, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { onEdit(index) }) {
                            Text(stringResource(R.string.library_edit))
                        }
                    }
                    Text(
                        text = pluralStringResource(
                            R.plurals.library_entry_presets,
                            entry.presets.size,
                            entry.presets.size,
                        ) + " · " + pluralStringResource(
                            R.plurals.library_detail_loaded,
                            preset.loadedBytes,
                            preset.loadedBytes,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Un preset del fichero, con **los mismos controles que la pantalla de Sliders** pero
 * deshabilitados y alimentados desde el `.tsl`.
 *
 * Reutiliza los widgets de `Controls.kt` —los mismos que usa el amplificador en vivo— en vez de
 * las tarjetas completas de Sliders: aquellas llevan botones GET que preguntan al amplificador,
 * y aquí no hay a quién preguntar. Lo que se comparte es lo que tiene sentido compartir.
 */
@Composable
private fun PresetDetail(
    opened: OpenedPreset,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sendToAmp: PresetSendControls? = null,
) {
    val state = opened.state
    val memory = opened.preset.memory

    // Mirar un preset no cambia nada, así que el back cierra sin preguntar.
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .knobAwareVerticalScroll()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.library_back)) }

        Text(text = opened.preset.name, style = MaterialTheme.typography.titleMedium)
        // Mirar un preset es de solo lectura; mandarlo al amplificador no, y es el único
        // botón de esta pantalla que sale de la app hacia el hardware.
        sendToAmp?.let { controls ->
            SendToAmpSection(
                controls = controls,
                name = opened.preset.name,
                image = { MemoryImage.from(opened.preset.memory) },
            )
        }
        Text(
            text = stringResource(R.string.library_detail_file, opened.entry.fileName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (opened.preset.memo.isNotBlank()) {
            Text(
                text = stringResource(R.string.library_detail_memo, opened.preset.memo),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = stringResource(R.string.library_detail_readonly),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader(stringResource(R.string.section_amp))
        ChipSelector(
            label = stringResource(R.string.amp_category),
            options = AmpCategory.entries.map { it.value to it.displayName },
            selected = state.ampCategory?.value,
            enabled = false,
            onSelected = {},
        )
        DropdownSelector(
            label = stringResource(R.string.amp_type),
            options = AmpType.entries.map { it.value to it.displayName },
            selected = state.ampType?.value,
            enabled = false,
            onSelected = {},
        )
        SwitchRow(
            label = stringResource(R.string.amp_variation),
            checked = state.variationOn == true,
            enabled = false,
            onCheckedChange = {},
        )

        ReadOnlyLevel(R.string.debug_connection_gain_level, state.gain)
        ReadOnlyLevel(R.string.debug_connection_volume_level, state.volume)
        ReadOnlyLevel(R.string.debug_connection_bass_level, state.bass)
        ReadOnlyLevel(R.string.debug_connection_middle_level, state.middle)
        ReadOnlyLevel(R.string.debug_connection_treble_level, state.treble)
        ReadOnlyLevel(R.string.debug_connection_presence_level, state.presence)

        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.section_effects))
        EffectId.entries.forEach { effect ->
            val effectState = when (effect) {
                EffectId.BOOST -> state.boost
                EffectId.MOD -> state.mod
                EffectId.FX -> state.fx
                EffectId.DELAY -> state.delay
                EffectId.REVERB -> state.reverb
            }
            ReadOnlyEffectCard(
                effect = effect,
                level = effectState.level,
                enabled = effectState.enabled,
                color = effectState.color,
                // El tipo activo no lo modela AmpState, pero sí está en el fichero: se lee
                // directo de su dirección, que es un byte más del mismo bloque.
                type = memory.byteAt(activeTypeAddress(effect)),
            )
        }

        if (opened.preset.unavailable.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            UnavailableSection(opened.preset.unavailable)
        }
    }
}

/** Dónde vive el "tipo activo" de cada efecto (CLAUDE.md §5.2). */
private fun activeTypeAddress(effect: EffectId) = when (effect) {
    EffectId.BOOST -> KatanaAddresses.BOOST_TYPE_ACTIVE
    EffectId.MOD -> KatanaAddresses.MOD_TYPE_ACTIVE
    EffectId.FX -> KatanaAddresses.FX_TYPE_ACTIVE
    EffectId.DELAY -> KatanaAddresses.DELAY_TYPE_ACTIVE
    EffectId.REVERB -> KatanaAddresses.REVERB_TYPE_ACTIVE
}

@Composable
private fun ReadOnlyLevel(labelRes: Int, value: Int?) {
    LevelControl(
        label = stringResource(
            labelRes,
            value?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = value,
        enabled = false,
        onLevelChanged = {},
    )
}

@Composable
private fun ReadOnlyEffectCard(
    effect: EffectId,
    level: Int?,
    enabled: Boolean?,
    color: EffectColor?,
    type: Int?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            SwitchRow(
                label = stringResource(effect.labelRes),
                checked = enabled == true,
                enabled = false,
                onCheckedChange = {},
            )
            ChipSelector(
                label = stringResource(R.string.effect_color),
                options = EffectColor.entries.map { it.value to it.name },
                selected = color?.value,
                enabled = false,
                onSelected = {},
            )
            DropdownSelector(
                label = stringResource(R.string.effect_type),
                options = effectTypeOptionsFor(effect),
                selected = type,
                enabled = false,
                onSelected = {},
            )
            ReadOnlyLevel(effect.level.labelRes, level)
        }
    }
}

/** El catálogo de tipos de cada efecto; Mod y FX comparten tabla (ver [ModFxType]). */
private fun effectTypeOptionsFor(effect: EffectId): List<Pair<Int, String>> = when (effect) {
    EffectId.BOOST -> dev.alonx3.ktnacontrol.protocol.BoostType.entries.map { it.value to it.displayName }
    EffectId.MOD, EffectId.FX -> ModFxType.entries.map { it.value to it.displayName }
    EffectId.DELAY -> dev.alonx3.ktnacontrol.protocol.DelayType.entries.map { it.value to it.displayName }
    EffectId.REVERB -> dev.alonx3.ktnacontrol.protocol.ReverbType.entries.map { it.value to it.displayName }
}

/**
 * Lo que el fichero no trae, o trae y no se puede colocar con confianza.
 *
 * Se enseña **en la propia pantalla y no solo en un log** porque es la respuesta a "¿por qué
 * este campo está vacío?", y esa pregunta se hace mirando el preset, no buscando en la consola.
 */
@Composable
private fun UnavailableSection(items: List<TslUnavailable>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.library_detail_unavailable_title, items.size),
                style = MaterialTheme.typography.titleSmall,
            )
            items.forEach { item ->
                Text(
                    text = stringResource(
                        R.string.library_detail_unavailable_item,
                        item.what,
                        item.reason,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Editar un preset **sin amplificador** (CLAUDE.md §4.5).
 *
 * ✅ **Es literalmente [SlidersPane]**, la misma de la pantalla en vivo, con `offline = true`.
 * Ese es el objetivo del diseño: un solo juego de tarjetas y sliders para las dos cosas, en vez
 * de una copia que se va separando de la otra cada vez que se cablea un control nuevo. Lo único
 * que cambia es de dónde salen los valores y a dónde van los SET, y eso lo decide el
 * `KatanaLink` que hay debajo, no la UI.
 */
@Composable
private fun PresetEditorScreen(
    session: LibraryViewModel.EditingSession,
    sendToAmp: PresetSendControls?,
    busy: Boolean,
    message: String?,
    onMessageShown: () -> Unit,
    onSave: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by session.editor.state.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val name = session.editor.currentName() ?: session.initialName

    /**
     * ⚠️ **El único camino que pierde trabajo, y por eso el único que pregunta.**
     *
     * Cambiar de pestaña **conserva** lo editado —la sesión vive en el ViewModel, no en el
     * composable (CLAUDE.md §4.2 y §4.5)— así que la app sería incoherente si volver lo borrara
     * en silencio. Sin cambios pendientes no pregunta nada: un diálogo que siempre sale deja de
     * leerse.
     */
    val requestBack = {
        if (state.dirty) confirmingDiscard = true else onBack()
    }

    // El back del sistema es el mismo gesto que el botón "Volver", así que hace lo mismo. Se
    // registra más adentro que el del shell, así que gana: primero se cierra el preset.
    BackHandler { requestBack() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = requestBack) { Text(stringResource(R.string.library_back)) }
            Text(
                text = if (state.dirty) stringResource(R.string.library_unsaved) else name,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        // ⚠️ Mismo trato que en la vista de solo lectura ([PresetDetail]), y no solo en el
        // diálogo previo al envío: los bloques en disputa (Contour, GafcExp1AsgnMinMax) siguen
        // sin cargar mientras se edita, así que quien edite tiene que verlo aquí también, no
        // enterarse recién al intentar mandarlo al amplificador.
        session.source?.unavailable?.takeIf { it.isNotEmpty() }?.let { items ->
            UnavailableSection(items, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "Guardar" solo aparece si hay un fichero al que volver: un preset nuevo todavía
            // no tiene dónde sobrescribir, y ofrecerlo sería un botón que miente.
            if (session.origin != null) {
                OutlinedButton(onClick = { saving = false }, enabled = !busy) {
                    Text(stringResource(R.string.library_save))
                }
            }
            OutlinedButton(onClick = { saving = true }, enabled = !busy) {
                Text(stringResource(R.string.library_save_as))
            }
        }
        // ⚠️ Manda **lo que hay editado ahora**, esté guardado en fichero o no: la imagen del
        // editor es la fuente, no el `.tsl` de partida. Es lo que el usuario está viendo.
        sendToAmp?.let { controls ->
            SendToAmpSection(
                controls = controls,
                name = name,
                image = { session.editor.image },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        message?.let { text ->
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onMessageShown) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        }

        if (confirmingDiscard) {
            AlertDialog(
                onDismissRequest = { confirmingDiscard = false },
                title = { Text(stringResource(R.string.library_discard_title)) },
                text = { Text(stringResource(R.string.library_discard_body, name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmingDiscard = false
                            onBack()
                        },
                    ) { Text(stringResource(R.string.library_discard_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingDiscard = false }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                },
            )
        }

        saving?.let { asNewFile ->
            // Sobrescribir un fichero de la biblioteca se avisa; no es destructivo sobre el
            // amplificador, pero sí pisa un preset que el usuario tenía guardado.
            val overwriteTarget = session.origin.takeIf { !asNewFile }
            PresetNameDialog(
                title = stringResource(
                    if (asNewFile) R.string.library_save_as else R.string.library_save
                ),
                initialName = name,
                // ⚠️ Este diálogo sigue siendo de un solo paso a propósito (CLAUDE.md §4.7): no
                // se le añade una segunda confirmación. Lo único que se homologa con "Guardar en
                // canal" y "Enviar al amplificador" es el **verbo** del botón cuando de verdad
                // se está pisando algo — "Sí, sobrescribir" en vez de "Guardar" a secas.
                confirmLabel = stringResource(
                    if (overwriteTarget != null) R.string.library_save_overwrite_action
                    else R.string.library_save
                ),
                warning = overwriteTarget?.let {
                    stringResource(R.string.library_overwrite_warning, it.fileName)
                },
                onConfirm = { chosen ->
                    saving = null
                    onSave(chosen, asNewFile)
                },
                onDismiss = { saving = null },
            )
        }

        // El cuerpo del editor: **los tres composables de dominio**, sin intermediario
        // (CLAUDE.md §4.2, "El cierre"). Antes esto era `SlidersPane(offline = true)`, que a
        // estas alturas no era más que una cáscara alrededor de estos mismos tres.
        PresetEditorBody(session = session)
    }
}

/**
 * **El cuerpo del editor offline**: amplificador, efectos y los controles sin perilla física,
 * los tres composables de dominio en fila (CLAUDE.md §4.2, "El cierre").
 *
 * ✅ **Es literalmente lo que `SlidersPane(offline = true)` pintaba**, sin la cáscara: aquella
 * función tenía todo su chrome de en vivo —el toggle de Edit Mode, los botones de preset, el
 * selector de canal— tras un `if (!offline)`, así que su rama offline eran exactamente estas tres
 * llamadas. Al escribirlas aquí desaparecen de paso una docena de argumentos inertes que solo
 * existían para satisfacer a la mitad en vivo (`state`, `editMode`, `onSavePreset`, el
 * `OFFLINE_DIAGNOSTICS` de relleno y los `onRead… = {}`, que ya no existen).
 *
 * ⚠️ **`canEdit = true` siempre, y no es un descuido**: offline no hay contrato de Edit Mode que
 * aplicar (§4.2) — no hay amplificador que confirme ni que deje de confirmar, y lo que se edita es
 * un fichero en RAM. El GET tampoco se ofrece —el botón vive solo en las pantallas en vivo
 * (2026-09-10)— por la misma razón: no hay a quién
 * preguntarle, y el valor ya está en la imagen.
 *
 * Vive aquí, privado, y no suelto en `ui/screens/`: lo que merece estar suelto es lo que se
 * reutiliza —`AmpSection`, `EffectsSection` y `NoPanelPane`, que ya lo están— y esto tiene un solo
 * llamador.
 */
@Composable
private fun PresetEditorBody(session: LibraryViewModel.EditingSession) {
    val state by session.editor.state.collectAsStateWithLifecycle()

    // ⚠️ El scroll y el padding los ponía `SlidersPane` en su Column exterior, y sin ellos el
    // editor se quedaría sin poder bajar. Se reproducen tal cual —`knobAwareVerticalScroll`
    // incluido, que es el que congela el scroll mientras un dedo arrastra una perilla.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .knobAwareVerticalScroll()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
    AmpSection(
        levels = state.levels,
        selectors = state.selectors,
        variation = state.variation,
        canEdit = true,
        onLevelChanged = session.editor::onLevelChanged,
        onSelectorChanged = session.editor::onSelectorChanged,
        onAmpVariationChanged = session.editor::onAmpVariationChanged,
    )

    Spacer(modifier = Modifier.height(8.dp))
    EffectsSection(
        levels = state.levels,
        effectColors = state.effectColors,
        effectEnabled = state.effectEnabled,
        effectTypes = state.effectTypes,
        canEdit = true,
        onLevelChanged = session.editor::onLevelChanged,
        onEffectColorChanged = session.editor::onEffectColorChanged,
        onEffectEnabledChanged = session.editor::onEffectEnabledChanged,
        onEffectTypeChanged = session.editor::onEffectTypeChanged,
        boosterParams = state.boosterParams,
        boosterSoloEnabled = state.boosterSoloEnabled,
        onBoosterParamChanged = session.editor::onBoosterParamChanged,
        onBoosterSoloEnabledChanged = session.editor::onBoosterSoloEnabledChanged,
        delayParams = state.delayParams,
        reverbParams = state.reverbParams,
        selectors = state.selectors,
        onSelectorChanged = session.editor::onSelectorChanged,
        onDelayParamChanged = session.editor::onDelayParamChanged,
        onReverbParamChanged = session.editor::onReverbParamChanged,
        reverbTime = state.reverbTime,
        onReverbTimeChanged = session.editor::onReverbTimeChanged,
        modChorusPreDelayLow = state.modChorusPreDelayLow,
        modChorusPreDelayHigh = state.modChorusPreDelayHigh,
        onModChorusPreDelayLowChanged = session.editor::onModChorusPreDelayLowChanged,
        onModChorusPreDelayHighChanged = session.editor::onModChorusPreDelayHighChanged,
        modInternalRaw = state.modInternalRaw,
        fxInternalRaw = state.fxInternalRaw,
        onModParamChanged = session.editor::onModParamChanged,
        onFxParamChanged = session.editor::onFxParamChanged,
        // Solo se mudó a la sección de efectos (QA 2026-09-09, bloque C). Offline sigue siendo
        // editable igual: es un parámetro más de la imagen.
        ampSoloLevel = state.ampSoloLevel,
        onAmpSoloLevelChanged = session.editor::onAmpSoloLevelChanged,
        // Sin amplificador no hay diagnóstico que valga: pregunta cosas que solo el hardware
        // puede contestar. `null` lo oculta — antes hacía falta un objeto entero de callbacks
        // vacíos para lo mismo.
        diagnostics = null,
    )

    Spacer(modifier = Modifier.height(8.dp))
    NoPanelPane(
        selectors = state.selectors,
        params = state.noPanelParams,
        contourSlots = state.contourSlots,
        eq1Raw = state.eq1Raw,
        eq2Raw = state.eq2Raw,
        chainSlots = state.chainSlots,
        canEdit = true,
        onSelectorChanged = session.editor::onSelectorChanged,
        onParamChanged = session.editor::onNoPanelParamChanged,
        onContourShapeChanged = session.editor::onContourShapeChanged,
        onContourFreqShiftChanged = session.editor::onContourFreqShiftChanged,
        onEqParamChanged = session.editor::onEqParamChanged,
    )
    }
}

/** Pide un nombre de preset, con un aviso opcional cuando lo que se va a hacer pisa algo. */
@Composable
internal fun PresetNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    warning: String? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.preset_save_name_label)) },
                )
                // El nombre viaja en 16 bytes ASCII de 7 bits, así que se enseña ya saneado:
                // que el usuario vea el cambio antes, no después (CLAUDE.md §5).
                Text(
                    text = stringResource(
                        R.string.library_name_preview,
                        PresetSave.decodeName(PresetSave.encodeName(name)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                warning?.let {
                    // Mismo tono de aviso que "Guardar en canal" y "Enviar al amplificador":
                    // color de error y un pequeño margen antes, no pegado al campo de texto
                    // (CLAUDE.md §4.7).
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/**
 * El botón **"Enviar al amplificador"** y sus dos diálogos de confirmación.
 *
 * ⚠️ Es el segundo botón destructivo de la app —el otro es guardar en un canal— y sigue sus
 * mismas cautelas (CLAUDE.md §5): **bajo `canEdit`** (conectado + Edit Mode), **dos
 * confirmaciones**, **nunca dos envíos a la vez** y **un resultado que no afirma éxito**.
 *
 * Aquí no hay lógica: qué diálogo toca, qué bloques se avisan y cómo acabó el envío lo decide
 * [PresetSendFlow], que se prueba entero en JVM. Esto solo pinta lo que diga el estado. ⚠️ **Lo
 * único que no cubre ningún test es esto de aquí**: que el botón se vea y que tocarlo llame a
 * quien debe. Eso necesita un dispositivo.
 *
 * @param image de dónde salen los bytes, como lambda: en el editor la imagen cambia con cada
 *   toque, y capturarla al componer mandaría una foto vieja.
 */
@Composable
private fun SendToAmpSection(
    controls: PresetSendControls,
    name: String,
    image: () -> MemoryImage,
    modifier: Modifier = Modifier,
) {
    val sending = controls.state is PresetSendState.Sending

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { controls.onRequest(name, image()) },
            enabled = controls.canSend && !sending,
        ) {
            Text(
                if (sending) stringResource(R.string.library_send_in_flight)
                else stringResource(R.string.library_send_button)
            )
        }
        // Por qué está apagado, dicho donde se ve el botón: es la misma explicación que da la
        // pantalla de Sliders cuando el Edit Mode está apagado.
        if (!controls.canSend) {
            Text(
                text = stringResource(R.string.library_send_needs_edit_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (val step = controls.state) {
        is PresetSendState.Review -> SendReviewDialog(
            name = step.request.name,
            plan = step.plan,
            onContinue = controls.onContinue,
            onCancel = controls.onCancel,
        )

        // Mismo aspecto que el paso final de "Guardar en canal" — ver DestructiveConfirmDialog
        // en Controls.kt y CLAUDE.md §4.7.
        is PresetSendState.Confirm -> DestructiveConfirmDialog(
            title = stringResource(R.string.library_send_confirm_title, step.request.name),
            body = stringResource(R.string.library_send_confirm_body, step.plan.messageCount),
            confirmLabel = stringResource(R.string.library_send_confirm_action),
            onConfirm = controls.onConfirmed,
            onBack = controls.onBack,
        )

        is PresetSendState.Finished -> AlertDialog(
            onDismissRequest = controls.onResultShown,
            title = { Text(stringResource(R.string.library_send_result_title)) },
            text = { Text(sendOutcomeText(step)) },
            confirmButton = {
                TextButton(onClick = controls.onResultShown) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
        )

        // Mientras manda, el botón ya dice "Enviando…" y está apagado: un diálogo más solo
        // taparía la pantalla sin añadir nada que el usuario pueda hacer.
        PresetSendState.Idle, is PresetSendState.Sending -> Unit
    }
}

/**
 * Paso 1: **qué se va a escribir y qué no**, antes de tocar el amplificador.
 *
 * La lista de omitidos es la razón de que este paso exista y no se vaya directo a la
 * confirmación: esas direcciones **se quedan con lo que ya haya en el amplificador**, así que
 * el sonido que salga es una mezcla. Enterarse después sería enterarse tarde.
 */
@Composable
private fun SendReviewDialog(
    name: String,
    plan: dev.alonx3.ktnacontrol.protocol.tsl.TslTransferPlan,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.library_send_review_title, name)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (plan.writes.isEmpty()) {
                        stringResource(R.string.library_send_review_empty)
                    } else {
                        stringResource(
                            R.string.library_send_review_body,
                            plan.writes.size,
                            plan.messageCount,
                            plan.dataBytes,
                        )
                    }
                )
                if (plan.skipped.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(
                            R.string.library_send_review_skipped_title,
                            plan.skipped.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    plan.skipped.forEach { omission ->
                        Text(
                            text = stringResource(
                                R.string.library_send_review_skipped_item,
                                omission.key,
                                omission.reason,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue, enabled = plan.writes.isNotEmpty()) {
                Text(stringResource(R.string.preset_save_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/**
 * El resultado, en palabras. **Ninguna rama dice que algo "se guardó bien"**, y las dos malas lo
 * dicen expresamente:
 *
 * - **Corte del cable**: se sabe cuántos mensajes salieron y dónde paró, y se avisa de que el
 *   amplificador queda con una mezcla del preset anterior y de este.
 * - **Excepción a mitad** (el USB desaparece): ni siquiera eso se sabe, y el texto lo admite en
 *   vez de inventar una cuenta.
 * - **Cancelado**: no salió nada, dicho explícitamente en vez de cerrar el diálogo en silencio.
 */
@Composable
private fun sendOutcomeText(finished: PresetSendState.Finished): String {
    val skipped = finished.plan?.skipped.orEmpty()
    val skippedNote = if (skipped.isEmpty()) "" else "\n\n" + stringResource(
        R.string.library_send_result_skipped,
        skipped.size,
        skipped.joinToString("; ") { it.key },
    )
    return when (val outcome = finished.outcome) {
        is PresetSendOutcome.Completed -> stringResource(
            R.string.library_send_result_ok,
            outcome.result.messagesSent,
            outcome.result.dataBytesSent,
        ) + skippedNote

        is PresetSendOutcome.Cut -> buildString {
            append(
                stringResource(
                    R.string.library_send_result_cut,
                    outcome.result.messagesSent,
                    outcome.result.plan.messageCount,
                )
            )
            outcome.result.failedAt?.let { where ->
                append("\n")
                append(stringResource(R.string.library_send_result_cut_where, where))
            }
        }

        is PresetSendOutcome.Failed -> stringResource(
            R.string.library_send_result_failed,
            outcome.detail ?: stringResource(R.string.library_send_result_failed_unknown),
        )

        PresetSendOutcome.NoTransport ->
            stringResource(R.string.library_send_result_no_transport)

        PresetSendOutcome.Cancelled ->
            stringResource(R.string.library_send_result_cancelled)
    }
}

/**
 * El mínimo de área táctil recomendado por Android (48×48 dp) — no viene de ningún cálculo, es
 * la cifra de siempre de las guías de accesibilidad de la plataforma. Fase 5, CLAUDE.md §4.9.
 */
private val MIN_TOUCH_TARGET = 48.dp
