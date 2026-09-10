package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
import dev.alonx3.ktnacontrol.ui.theme.Spacing
import dev.alonx3.ktnacontrol.usb.UsbConnectionState

/**
 * **Lo que la barra superior dice de la conexión, en todo momento y en las cuatro pantallas.**
 *
 * Antes el estado de la conexión solo se sabía yendo a Logs y leyendo la consola — o sea que la
 * respuesta a "¿por qué no se mueve nada?" estaba en otra pantalla. Ahora está siempre a la
 * vista.
 *
 * El caso lo decide [ShellState.indicatorOf], que es Kotlin puro y tiene tests; aquí solo se
 * eligen las palabras, que van en `strings.xml` (CLAUDE.md §6).
 */
@Composable
internal fun ConnectionBadge(state: UsbConnectionState, modifier: Modifier = Modifier) {
    val indicator = ShellState.indicatorOf(state)
    val label = when (indicator) {
        ConnectionIndicator.CONNECTED -> stringResource(
            R.string.shell_connection_connected,
            ShellState.deviceNameOf(state).orEmpty(),
        )
        ConnectionIndicator.CONNECTING -> stringResource(R.string.shell_connection_connecting)
        ConnectionIndicator.ABSENT -> stringResource(R.string.shell_connection_absent)
        ConnectionIndicator.FAILED -> stringResource(R.string.shell_connection_failed)
    }
    Text(
        text = label,
        modifier = modifier.padding(end = Spacing.sm),
        style = MaterialTheme.typography.labelMedium,
        // El color es la parte que se lee de reojo: solo "conectado" merece el color normal.
        color = when (indicator) {
            ConnectionIndicator.CONNECTED -> MaterialTheme.colorScheme.onSurface
            ConnectionIndicator.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/**
 * La barra inferior con **las tres pantallas de dominio**. Logs no está aquí a propósito: es una
 * entrada secundaria de la barra superior (CLAUDE.md §4.2, "La navegación").
 *
 * Recorre [DebugSection.PRIMARY], así que añadir una pantalla de dominio la pone en la barra sin
 * tocar esto — y olvidarse de clasificarla la deja inalcanzable, que es lo que fija
 * `ShellStateTest`.
 */
@Composable
internal fun ShellBottomBar(current: DebugSection, onSelect: (DebugSection) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        DebugSection.PRIMARY.forEach { section ->
            val selected = section == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(section) },
                // La pestaña activa se marca con el contenedor del acento, no con el acento a
                // pleno: una pastilla ámbar saturada detrás del texto compite con los sliders,
                // que son lo que de verdad hay que mirar.
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                // ⚠️ **Sin iconos, y es una decisión, no un olvido**: los `Icons.Filled` de
                // Material viven en `material-icons-core`, que **no está en el classpath** de
                // este proyecto. Añadir una dependencia por tres pictogramas contradice la regla
                // de §6 ("mantener la lista corta, cada añadido con justificación"), y una barra
                // de tres entradas con el nombre escrito se entiende igual — mejor, de hecho,
                // que un icono que haya que adivinar. El nombre va en la ranura del icono porque
                // es lo único que hay: poner el mismo texto dos veces sería peor.
                //
                // ⚠️ **Las tres entradas miden lo mismo, y antes no** (QA 2026-09-09, bloque C):
                // solo la activa llevaba la pastilla del indicador, así que la seleccionada se
                // veía más grande que las otras dos y la barra "saltaba" al cambiar de pestaña.
                // Ahora **todas** llevan la misma caja —mismo ancho mínimo, mismo alto, mismo
                // borde—, y lo único que cambia con la selección son el color del borde y el del
                // texto. Un borde visible en las tres es además lo que hace que se lean como
                // botones de un panel y no como tres palabras sueltas.
                icon = {
                    Box(
                        modifier = Modifier
                            .widthIn(min = NAV_ITEM_MIN_WIDTH)
                            .height(NAV_ITEM_HEIGHT)
                            .border(
                                width = NAV_ITEM_BORDER,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(section.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                },
            )
        }
    }
}

/** Lo que hace que las tres entradas de la barra midan igual. Ver [ShellBottomBar]. */
private val NAV_ITEM_MIN_WIDTH = 88.dp
private val NAV_ITEM_HEIGHT = 36.dp
private val NAV_ITEM_BORDER = 1.dp

/**
 * **El estado de "aquí no se puede hacer nada, y esto es lo que falta".**
 *
 * ⚠️ Sustituye a lo que había antes —los controles en gris, sin explicación—, que se lee como un
 * bug. Una pantalla que dice *qué* falta y ofrece el botón que lo arregla convierte lo mismo en
 * una regla. Cada estado de [UsbConnectionState] tiene su frase: no es igual "todavía no se ha
 * buscado" que "se buscó y no estaba" o "falta que aceptes el permiso".
 *
 * ⚠️ **La Biblioteca no usa esto y es deliberado** (§4.5): funciona desenchufada. Por eso el
 * texto remata señalando precisamente eso — sin cable no todo está perdido, hay media app que
 * sigue sirviendo.
 *
 * @param onScan reintenta la búsqueda. Es la acción que arregla el caso más común (el cable no
 *   estaba puesto al abrir la app), así que va aquí y no escondida en Logs.
 * @param mentionLibrary si añadir el puntero a la Biblioteca. False dentro de `PresetsScreen`,
 *   donde la Biblioteca está justo debajo y decirlo sería redundante.
 */
@Composable
internal fun NoAmpNotice(
    state: UsbConnectionState,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    mentionLibrary: Boolean = true,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.no_amp_title),
                style = MaterialTheme.typography.titleMedium,
            )
            // Las palabras las elige Compose; el que esto bloquee los controles lo decide
            // `ShellState.availabilityOf`, que está probado.
            Text(
                text = when (state) {
                    UsbConnectionState.Idle -> stringResource(R.string.no_amp_body_idle)
                    UsbConnectionState.Searching -> stringResource(R.string.no_amp_body_searching)
                    UsbConnectionState.KatanaNotFound ->
                        stringResource(R.string.no_amp_body_not_found)
                    UsbConnectionState.AwaitingPermission ->
                        stringResource(R.string.no_amp_body_permission)
                    is UsbConnectionState.Failed ->
                        stringResource(R.string.no_amp_body_failed, state.reason)
                    // Inalcanzable: con `Connected` no se pinta este aviso. El `when` es
                    // exhaustivo para que añadir un estado nuevo no compile sin decidir su texto.
                    is UsbConnectionState.Connected -> stringResource(R.string.no_amp_body_idle)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (mentionLibrary) {
                Text(
                    text = stringResource(R.string.no_amp_library_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onScan) {
                Text(stringResource(R.string.no_amp_scan))
            }
        }
    }
}

/** El acceso a Logs desde la barra superior: presente pero sin competir con las tres de abajo. */
@Composable
internal fun AdvancedAction(current: DebugSection, onSelect: (DebugSection) -> Unit) {
    TextButton(
        onClick = { onSelect(DebugSection.LOGS) },
        // Ya estando en Logs el botón no haría nada; apagarlo lo dice sin un texto extra.
        enabled = current != DebugSection.LOGS,
    ) {
        Text(stringResource(R.string.shell_advanced))
    }
}

/**
 * ⚠️ **Un `@Preview` no comprueba nada por sí solo** — que quepa y se lea hay que mirarlo en el
 * dispositivo (BACKLOG.md, "Pendiente por probar"). Lo que sí deja ver sin amplificador es lo
 * único que la Fase 2 añade: cómo se ve cada estado de conexión y el aviso que sustituye al panel
 * gris.
 */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ShellPiecesPreview() {
    KTNAControlTheme {
        Column(modifier = Modifier.padding(Spacing.md)) {
            listOf(
                UsbConnectionState.Connected("KATANA"),
                UsbConnectionState.Searching,
                UsbConnectionState.KatanaNotFound,
                UsbConnectionState.Failed("no se pudo reclamar la interfaz 3"),
            ).forEach { state -> ConnectionBadge(state) }

            NoAmpNotice(state = UsbConnectionState.KatanaNotFound, onScan = {})
            ShellBottomBar(current = DebugSection.AMP, onSelect = {})
        }
    }
}
