package dev.alonx3.ktnacontrol.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La matemática del gesto de la perilla (rediseño de 2026-09-06).
 *
 * ⚠️ **Esto no prueba que el gesto se sienta bien** — eso solo lo dice el dedo, y está en
 * BACKLOG.md como prueba manual. Lo que sí prueba es lo que se puede equivocar sin que se
 * note mirando: saltarse posiciones de una lista, o acumular error al arrastrar.
 */
class KnobGestureTest {

    private val enumRange = 0.0..27.0 // las 28 frecuencias del paramétrico

    @Test
    fun `arrastrar a la derecha sube y a la izquierda baja`() {
        assertTrue(knobValueAt(10.0, dragPx = 200f, step = 1.0, range = enumRange) > 10.0)
        assertTrue(knobValueAt(10.0, dragPx = -200f, step = 1.0, range = enumRange) < 10.0)
    }

    /**
     * La propiedad que pide BACKLOG.md, punto 12: **recorrer la lista sin saltarse ninguna**.
     *
     * Se comprueba barriendo el arrastre píxel a píxel y quedándose con los valores distintos
     * que salen: tienen que ser los 28, consecutivos y sin huecos.
     */
    @Test
    fun `un barrido lento recorre las 28 posiciones una a una`() {
        val vistos = LinkedHashSet<Int>()
        var px = 0f
        while (px <= 28 * 64f) {
            vistos += knobValueAt(0.0, px, step = 1.0, range = enumRange).toInt()
            px += 1f
        }
        assertEquals("deberían salir las 28 posiciones", 28, vistos.size)
        assertEquals("y en orden, sin saltarse ninguna", (0..27).toList(), vistos.toList())
    }

    @Test
    fun `ir y volver con el dedo devuelve al valor de partida`() {
        val anchor = 12.0
        var px = 0f
        repeat(40) { px += 7f }
        val ida = knobValueAt(anchor, px, step = 1.0, range = enumRange)
        repeat(40) { px -= 7f }
        val vuelta = knobValueAt(anchor, px, step = 1.0, range = enumRange)

        assertTrue("la ida debería haber movido el valor", ida != anchor)
        assertEquals("la vuelta debe cerrar exacta, sin arrastre acumulado", anchor, vuelta, 0.0)
    }

    @Test
    fun `respeta el paso fraccionario del EQ grafico`() {
        // 0,5 dB por paso: el mismo arrastre que mueve una posición de lista mueve medio dB.
        val subido = knobValueAt(0.0, dragPx = 320f, step = 0.5, range = -12.0..12.0)
        val enteros = knobValueAt(0.0, dragPx = 320f, step = 1.0, range = -20.0..20.0)
        assertEquals(enteros / 2.0, subido, 1e-9)
        assertEquals(0.0, (subido / 0.5) % 1.0, 1e-9)
    }

    @Test
    fun `no se sale del rango por mucho que se arrastre`() {
        assertEquals(27.0, knobValueAt(0.0, 99_999f, 1.0, enumRange), 0.0)
        assertEquals(0.0, knobValueAt(27.0, -99_999f, 1.0, enumRange), 0.0)
    }

    @Test
    fun `sin arrastre el valor no se mueve`() {
        assertEquals(7.0, knobValueAt(7.0, 0f, 1.0, enumRange), 0.0)
    }

    // --- El congelado del scroll durante el ajuste (2026-09-06) ------------------------------

    @Test
    fun `el scroll solo se congela mientras dura el ajuste`() {
        val interaction = KnobInteraction()
        assertTrue("en reposo el scroll debe funcionar", !interaction.adjusting)

        interaction.begin()
        assertTrue("durante el ajuste el scroll se congela", interaction.adjusting)

        interaction.end()
        assertTrue("al soltar se restaura de inmediato", !interaction.adjusting)
    }

    /**
     * ⚠️ El fallo que más molestaría: **el scroll trabado para siempre**.
     *
     * Pasa si una perilla empieza un gesto y desaparece de la composición antes de soltarlo —un
     * cambio de tipo de efecto a media pulsación, por ejemplo—. `KnobControl` lo suelta con un
     * `DisposableEffect`; esto comprueba que soltar de más es inofensivo, que es lo que ese
     * camino necesita.
     */
    @Test
    fun `soltar de mas no rompe nada`() {
        val interaction = KnobInteraction()
        interaction.begin()
        interaction.end()
        interaction.end()
        assertTrue(!interaction.adjusting)
    }

    /**
     * Dos perillas comparten el mismo objeto, así que la segunda que suelte deja el scroll
     * libre. Es aceptable: no hay forma de tener dos dedos ajustando dos perillas a la vez con
     * este gesto, y el `DisposableEffect` cubre el caso raro.
     */
    @Test
    fun `el estado es compartido, no uno por perilla`() {
        val compartido = KnobInteraction()
        compartido.begin()
        assertTrue("cualquier contenedor que lo mire ve el ajuste", compartido.adjusting)
        compartido.end()
        assertTrue(!compartido.adjusting)
    }
}
