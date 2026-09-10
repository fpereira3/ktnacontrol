package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.protocol.PresetSave
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
import dev.alonx3.ktnacontrol.ui.theme.Spacing
import dev.alonx3.ktnacontrol.usb.UsbConnectionState

/**
 * **La pantalla de presets**: la tercera y última pantalla de dominio del bloque 6, y la que
 * permite retirar `SlidersPane` (CLAUDE.md §4.2, "El cierre").
 *
 * Junta dos cosas que hasta ahora vivían en menús distintos, y **el cuidado está en que no se
 * mezclen**:
 *
 * | | Sobre qué actúa | Necesita cable |
 * | --- | --- | --- |
 * | **Amplificador (en vivo)** | el estado editado que el amp tiene ahora mismo | **sí** |
 * | **Biblioteca** | ficheros `.tsl` de este teléfono | **no** |
 *
 * La separación tiene cuatro señales, no una — dos encabezados con su subtítulo, un divisor,
 * **reglas de habilitación distintas y visibles** (la mitad de arriba se apaga sin cable; la de
 * abajo funciona desenchufada, que es su razón de ser, §4.5), y que **abrir un preset ocupa la
 * pantalla entera**, dejando fuera el bloque en vivo. Dentro de un preset, la única acción hacia
 * el amplificador es "Enviar al amplificador…", que trata de *ese* preset. Ver CLAUDE.md §4.2.
 *
 * ⚠️ **Nada de esto es funcionalidad nueva**: guardar en canal, exportar y la Biblioteca entera
 * ya estaban implementados y probados en sus capas. Esto es reubicación de UI.
 *
 * ⚠️ **"Releer" no está aquí**, aunque estuviera en la misma fila de botones de `SlidersPane`:
 * repuebla la caché de controles, o sea lo que enseñan [AmpScreen] y [EffectsScreen] —que ya lo
 * tienen— y esta pantalla no muestra ningún control. Exportar tampoco lo necesita:
 * `KatanaRepository.exportImage` lee su propio dump completo, no la caché.
 *
 * @param currentChannel el canal activo, solo para que el diálogo de guardado pueda avisar si el
 *   destino elegido **no** es el que se está editando. Se pasa ya resuelto en vez del mapa entero
 *   de selectores: es lo único que esta pantalla necesita de él.
 * @param sendToAmp con qué mandar un preset de la Biblioteca al amplificador, o null si no hay
 *   amplificador. Se pasa tal cual a [LibraryPane].
 */
@Composable
internal fun PresetsScreen(
    state: UsbConnectionState,
    editMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    currentChannel: Int?,
    presetSaveInFlight: Boolean,
    onSavePreset: (String, Int) -> Unit,
    exportInFlight: Boolean,
    onExportPreset: (String) -> Unit,
    onScanClicked: () -> Unit,
    sendToAmp: PresetSendControls?,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = viewModel(),
) {
    // La Biblioteca entera, sin cambios: lista, importar, ver, editar offline, guardar y
    // "Enviar al amplificador…". **El mismo ViewModel** que se observa arriba para saber si
    // hay un preset abierto — `viewModel()` devuelve la misma instancia, pero se pasa
    // explícito para que no dependa de esa coincidencia.
    //
    // ⚠️ **El bloque en vivo va DENTRO, por la ranura `header`, y no apilado encima** (QA
    // 2026-09-09, A.3: "la Biblioteca no aparece / no se encuentra"). Antes esto era una
    // `Column` con el bloque en vivo arriba y `LibraryPane` abajo con `Modifier.weight(1f)`, y
    // ese `weight` reparte **lo que sobra**: como el bloque en vivo no tenía tope ni scroll, en
    // una pantalla corta o con la letra grande se quedaba con todo el alto y la Biblioteca se
    // medía a **cero**. Y al no hacer scroll el contenedor de fuera, no había forma de llegar a
    // ella — se veía una pantalla de "Presets" sin biblioteca por ninguna parte.
    //
    // Con la ranura hay **un solo scroll** para las dos mitades: el bloque en vivo empuja la
    // lista, no la borra. La separación entre "esto escribe en el amplificador" y "esto son
    // ficheros" no cambia: sigue siendo encabezado + subtítulo + divisor + reglas de
    // habilitación distintas (CLAUDE.md §4.2).
    //
    // ⚠️ Con un preset abierto, `LibraryPane` vuelve antes de pintar la ranura, así que el
    // bloque en vivo desaparece solo — que es lo que antes hacía el `browsingLibrary` de aquí.
    // Esa cuarta señal de separación se mantiene, ahora sin que este llamador tenga que
    // duplicar la condición.
    LibraryPane(
        modifier = modifier.fillMaxSize(),
        viewModel = libraryViewModel,
        sendToAmp = sendToAmp,
    ) {
        LiveAmpSection(
            state = state,
            editMode = editMode,
            onEditModeChanged = onEditModeChanged,
            currentChannel = currentChannel,
            presetSaveInFlight = presetSaveInFlight,
            onSavePreset = onSavePreset,
            exportInFlight = exportInFlight,
            onExportPreset = onExportPreset,
            onScanClicked = onScanClicked,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            SectionHeader(stringResource(R.string.presets_library_title))
            Text(
                text = stringResource(R.string.presets_library_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * La mitad **en vivo**: lo que actúa sobre el amplificador conectado ahora mismo.
 *
 * Los dos botones vienen tal cual de la cabecera de `SlidersPane`, con sus reglas intactas:
 *
 * - **Guardar preset** es destructivo, así que va bajo `canEdit` (conectado **y** Edit Mode),
 *   igual que cualquier otro parámetro. Sin Edit Mode la app no puede confirmar nada de lo que
 *   escribe, y "no puedo confirmar" es razón de sobra para no dejar sobrescribir un canal.
 * - **Exportar** solo lee del amplificador y escribe un fichero nuevo, así que **no** cae bajo
 *   `canEdit`: basta con estar conectado (§4.2 va de poder *confirmar* escrituras, y aquí no se
 *   escribe nada en el amp).
 *
 * El toggle de Edit Mode se repite aquí igual que en [AmpScreen] y [EffectsScreen]: es la
 * pantalla desde la que se guarda, y tener que irse a otra sección para poder pulsar el botón
 * sería su propio pequeño bug.
 */
@Composable
private fun LiveAmpSection(
    state: UsbConnectionState,
    editMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    currentChannel: Int?,
    presetSaveInFlight: Boolean,
    onSavePreset: (String, Int) -> Unit,
    exportInFlight: Boolean,
    onExportPreset: (String) -> Unit,
    onScanClicked: () -> Unit,
) {
    val availability = ShellState.availabilityOf(state, editMode)
    val canEdit = availability.canEdit

    var savingPreset by rememberSaveable { mutableStateOf(false) }
    var exportingPreset by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        SectionHeader(stringResource(R.string.presets_live_title))
        Text(
            text = stringResource(R.string.presets_live_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ⚠️ **Solo esta mitad depende del cable.** Sin amplificador se explica qué falta, igual
        // que en [AmpScreen] y [EffectsScreen] — pero **la Biblioteca de abajo sigue entera**,
        // que es justo su razón de ser (§4.5). Por eso el aviso no menciona la Biblioteca aquí:
        // está a dos dedos, decirlo sobraría.
        if (availability is ControlAvailability.NoAmp) {
            NoAmpNotice(
                state = availability.state,
                onScan = onScanClicked,
                mentionLibrary = false,
            )
            return@Column
        }

        EditModeToggle(
            editMode = editMode,
            enabled = true,
            onEditModeChanged = onEditModeChanged,
        )
        if (!editMode) EditModeNotice()

        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { savingPreset = true },
                enabled = canEdit && !presetSaveInFlight,
            ) {
                Text(
                    if (presetSaveInFlight) stringResource(R.string.preset_save_in_flight)
                    else stringResource(R.string.preset_save_button)
                )
            }
            // Exportar solo lee, así que **no** cae bajo `canEdit`: basta con el cable, y aquí
            // ya lo hay. Es la asimetría que distingue "escribe" de "lee".
            OutlinedButton(
                onClick = { exportingPreset = true },
                enabled = !exportInFlight,
            ) {
                Text(stringResource(R.string.library_export))
            }
        }
        // Por qué está apagado, dicho donde se ven los botones. (Sin cable no se llega aquí:
        // esa rama la corta el aviso de arriba.)
        if (!canEdit) {
            Text(
                text = stringResource(R.string.preset_save_needs_edit_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (exportingPreset) {
        PresetNameDialog(
            title = stringResource(R.string.library_export),
            initialName = "",
            confirmLabel = stringResource(R.string.library_export),
            onConfirm = { name ->
                exportingPreset = false
                onExportPreset(name)
            },
            onDismiss = { exportingPreset = false },
        )
    }
    if (savingPreset) {
        PresetSaveDialog(
            currentChannel = currentChannel,
            initialName = "",
            onConfirm = { name, channel ->
                savingPreset = false
                onSavePreset(name, channel)
            },
            onDismiss = { savingPreset = false },
        )
    }
}
/**
 * El diálogo de guardar preset: nombre, canal destino y **una confirmación explícita aparte**
 * antes de mandar nada (CLAUDE.md §5, "Guardado de presets").
 *
 * Son dos pasos y no uno a propósito. Guardar **sobrescribe un canal del amplificador y no hay
 * deshacer**: con un solo botón, un toque de más sobre el canal equivocado se lleva por delante
 * un preset que igual costó una tarde. El segundo paso no pide nada nuevo —solo repite en
 * palabras qué se va a hacer y dónde— porque lo que se quiere evitar no es un error de datos,
 * es un descuido.
 *
 * Devuelve el control al llamador con [onConfirm] solo cuando el usuario pasó por los dos.
 */
@Composable
private fun PresetSaveDialog(
    currentChannel: Int?,
    initialName: String,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var target by rememberSaveable {
        // Por defecto, el canal que se está editando: es el destino que menos sorprende, y el
        // único que no destruye trabajo ajeno al que el usuario tiene delante.
        mutableIntStateOf(
            currentChannel?.takeIf { it in PresetSave.CHANNELS } ?: PresetSave.CHANNELS.first()
        )
    }
    var confirming by rememberSaveable { mutableStateOf(false) }

    val targetLabel = PresetSave.channelLabel(target)
    // Lo que de verdad se va a mandar, no lo que se tecleó: el amp solo acepta 16 bytes ASCII.
    val sanitized = remember(name) { PresetSave.decodeName(PresetSave.encodeName(name)) }

    if (confirming) {
        // El aspecto de este paso final es el mismo que el de "Enviar al amplificador…": ver
        // DestructiveConfirmDialog en Controls.kt y CLAUDE.md §4.7 para el porqué.
        DestructiveConfirmDialog(
            title = stringResource(R.string.preset_save_confirm_title, targetLabel),
            body = stringResource(R.string.preset_save_confirm_body, sanitized, targetLabel),
            confirmLabel = stringResource(R.string.preset_save_confirm_action, targetLabel),
            onConfirm = { onConfirm(sanitized, target) },
            onBack = { confirming = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_save_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { typed -> name = typed.take(PresetSave.NAME_LENGTH) },
                    label = { Text(stringResource(R.string.preset_save_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.preset_save_name_counter,
                        name.length,
                        PresetSave.NAME_LENGTH,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Solo cuando el saneado cambia algo: avisar siempre sería ruido, y callarlo
                // cuando cambia dejaría al usuario creyendo que guardó otro nombre.
                if (sanitized != name.trimEnd()) {
                    Text(
                        text = stringResource(R.string.preset_save_name_sanitized, sanitized),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                ChipSelector(
                    label = stringResource(R.string.preset_save_target_label),
                    options = PresetSave.CHANNELS.map { it to PresetSave.channelLabel(it) },
                    selected = target,
                    enabled = true,
                    onSelected = { picked -> target = picked },
                )

                Text(
                    text = when {
                        currentChannel == null -> stringResource(R.string.preset_save_unknown_channel)
                        target == currentChannel ->
                            stringResource(R.string.preset_save_same_channel, targetLabel)
                        else -> stringResource(
                            R.string.preset_save_other_channel,
                            targetLabel,
                            currentChannel.takeIf { it in PresetSave.CHANNELS }
                                ?.let(PresetSave::channelLabel)
                                ?: stringResource(R.string.channel_panel),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    // El aviso de "vas a pisar otro canal" va en color de error; el de "es el
                    // mismo que editas" no, porque no es un riesgo, es lo normal.
                    color = if (currentChannel != null && target == currentChannel) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )

                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.preset_save_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirming = true }) {
                Text(stringResource(R.string.preset_save_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/**
 * ⚠️ **Cubre solo la mitad en vivo, y hay una razón concreta.** [PresetsScreen] entera necesita
 * un [LibraryViewModel], que es un `AndroidViewModel` con su `Application` y lee ficheros de
 * disco: no se puede instanciar en una `@Preview`. Lo que se puede ver aquí es lo único que esta
 * pantalla añade —los dos botones en vivo, sus encabezados y el estado deshabilitado— y es
 * justamente lo que se movió de sitio.
 *
 * Como siempre: un `@Preview` no comprueba nada por sí solo. Que quepa y se lea hay que mirarlo
 * en el dispositivo — BACKLOG.md, "Pendiente por probar".
 */
@Preview(showBackground = true)
@Composable
private fun PresetsLiveSectionPreview() {
    KTNAControlTheme {
        Column {
            // Conectado y con Edit Mode: los dos botones activos.
            LiveAmpSection(
                state = UsbConnectionState.Connected("KATANA"),
                editMode = true,
                onEditModeChanged = {},
                currentChannel = 1,
                presetSaveInFlight = false,
                onSavePreset = { _, _ -> },
                exportInFlight = false,
                onExportPreset = {},
                onScanClicked = {},
            )
            HorizontalDivider()
            // Conectado sin Edit Mode: guardar apagado con su aviso, exportar sigue activo
            // porque solo lee. Es la asimetría que distingue "escribe" de "lee".
            LiveAmpSection(
                state = UsbConnectionState.Connected("KATANA"),
                editMode = false,
                onEditModeChanged = {},
                currentChannel = null,
                presetSaveInFlight = false,
                onSavePreset = { _, _ -> },
                exportInFlight = false,
                onExportPreset = {},
                onScanClicked = {},
            )
        }
    }
}
