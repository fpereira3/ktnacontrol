package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El shell de navegación y el mapeo de estado de conexión → lo que ve cada pantalla
 * (CLAUDE.md §4.2, "La navegación").
 *
 * ⚠️ **Lo que estos tests protegen**: que ninguna pantalla quede inalcanzable al añadirla, que el
 * back haga lo que un `NavHost` haría, y que `canEdit` —la condición que gobierna **toda** la
 * escritura destructiva— tenga una sola definición y no tres copias que se puedan desincronizar.
 *
 * ⚠️ **Lo que NO prueban**: que la barra se vea, que quepa, o que tocar una pestaña navegue de
 * verdad. Eso necesita dispositivo; para eso están los `@Preview` y la prueba a mano del BACKLOG.
 */
class ShellStateTest {

    /** Los seis estados posibles, para poder recorrerlos exhaustivamente. */
    private val allStates = listOf(
        UsbConnectionState.Idle,
        UsbConnectionState.Searching,
        UsbConnectionState.KatanaNotFound,
        UsbConnectionState.AwaitingPermission,
        UsbConnectionState.Connected("KATANA"),
        UsbConnectionState.Failed("se soltó el cable"),
    )

    // --- El reparto de destinos -----------------------------------------------------------

    @Test
    fun `las tres pantallas de dominio van en la barra y Logs no`() {
        assertEquals(
            listOf(DebugSection.AMP, DebugSection.EFFECTS, DebugSection.PRESETS),
            DebugSection.PRIMARY,
        )
        assertEquals(listOf(DebugSection.LOGS), DebugSection.SECONDARY)
    }

    @Test
    fun `toda seccion es alcanzable por algun sitio`() {
        // Si alguien añade una pantalla y se olvida de decidir dónde vive, queda inalcanzable
        // y no falla nada — salvo esto.
        assertEquals(
            DebugSection.entries.toSet(),
            (DebugSection.PRIMARY + DebugSection.SECONDARY).toSet(),
        )
        assertEquals(
            DebugSection.entries.size,
            DebugSection.PRIMARY.size + DebugSection.SECONDARY.size,
        )
    }

    // --- Navegación y back ----------------------------------------------------------------

    @Test
    fun `arranca en el amplificador, no en Logs`() {
        val nav = ShellNavigation()
        assertEquals(DebugSection.AMP, nav.start)
        assertEquals(DebugSection.AMP, nav.current.value)
    }

    @Test
    fun `select cambia de pestaña y es idempotente`() {
        val nav = ShellNavigation()
        nav.select(DebugSection.EFFECTS)
        assertEquals(DebugSection.EFFECTS, nav.current.value)
        nav.select(DebugSection.EFFECTS)
        assertEquals(DebugSection.EFFECTS, nav.current.value)
    }

    @Test
    fun `el back vuelve a la pestaña de inicio desde cualquier otra`() {
        listOf(DebugSection.EFFECTS, DebugSection.PRESETS, DebugSection.LOGS).forEach { section ->
            val nav = ShellNavigation()
            nav.select(section)

            assertTrue("el back debería consumirse desde $section", nav.onBack())
            assertEquals(DebugSection.AMP, nav.current.value)
        }
    }

    @Test
    fun `en la pestaña de inicio el back NO se consume`() {
        val nav = ShellNavigation()

        // Que devuelva false es lo que deja que el sistema cierre la app, que es lo que
        // cualquiera espera del back en la primera pantalla.
        assertFalse(nav.onBack())
        assertEquals(DebugSection.AMP, nav.current.value)
    }

    // --- Estado de conexión → barra superior ------------------------------------------------

    @Test
    fun `cada estado de conexion tiene indicador, y los seis estan cubiertos`() {
        val byIndicator = allStates.groupBy(ShellState::indicatorOf)
        assertEquals(
            "conectado solo cuando lo está",
            listOf(UsbConnectionState.Connected("KATANA")),
            byIndicator[ConnectionIndicator.CONNECTED],
        )
        assertEquals(
            "buscando y esperando permiso son 'en camino'",
            listOf(UsbConnectionState.Searching, UsbConnectionState.AwaitingPermission),
            byIndicator[ConnectionIndicator.CONNECTING],
        )
        assertEquals(
            "sin intentar y no encontrado son 'no hay amplificador'",
            listOf(UsbConnectionState.Idle, UsbConnectionState.KatanaNotFound),
            byIndicator[ConnectionIndicator.ABSENT],
        )
        assertEquals(1, byIndicator[ConnectionIndicator.FAILED]?.size)
        assertEquals(allStates.size, byIndicator.values.sumOf { it.size })
    }

    @Test
    fun `el nombre del dispositivo solo existe estando conectado`() {
        assertEquals("KATANA", ShellState.deviceNameOf(UsbConnectionState.Connected("KATANA")))
        (allStates - UsbConnectionState.Connected("KATANA")).forEach { state ->
            assertEquals("$state no debería dar nombre", null, ShellState.deviceNameOf(state))
        }
    }

    // --- Estado de conexión → qué ve una pantalla de control --------------------------------

    @Test
    fun `sin cable da NoAmp, y el edit mode no lo cambia`() {
        (allStates - UsbConnectionState.Connected("KATANA")).forEach { state ->
            listOf(true, false).forEach { editMode ->
                val availability = ShellState.availabilityOf(state, editMode)
                assertEquals(
                    "con $state y editMode=$editMode debería ser NoAmp",
                    ControlAvailability.NoAmp(state),
                    availability,
                )
                assertFalse(availability.canEdit)
            }
        }
    }

    @Test
    fun `con cable pero sin edit mode, los controles se ven y no se tocan`() {
        val availability = ShellState.availabilityOf(UsbConnectionState.Connected("KATANA"), false)
        assertEquals(ControlAvailability.NeedsEditMode, availability)
        assertFalse(availability.canEdit)
    }

    @Test
    fun `con cable y edit mode se puede editar`() {
        val availability = ShellState.availabilityOf(UsbConnectionState.Connected("KATANA"), true)
        assertEquals(ControlAvailability.Ready, availability)
        assertTrue(availability.canEdit)
    }

    @Test
    fun `canEdit coincide con la vieja formula connected y editMode`() {
        // La condición que gobierna toda la escritura destructiva no puede cambiar de
        // significado al centralizarla: estaba escrita tres veces como `connected && editMode`.
        allStates.forEach { state ->
            listOf(true, false).forEach { editMode ->
                val legacy = state is UsbConnectionState.Connected && editMode
                assertEquals(
                    "$state / editMode=$editMode",
                    legacy,
                    ShellState.availabilityOf(state, editMode).canEdit,
                )
            }
        }
    }
}
