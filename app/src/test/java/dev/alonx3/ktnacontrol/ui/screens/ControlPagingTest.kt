package dev.alonx3.ktnacontrol.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La matemática del paginado de las tiras de controles (QA 2026-09-09, bloque C).
 *
 * Es lo único de todo el rediseño que se puede comprobar sin dispositivo, y es justo la parte que
 * puede estar mal sin que se note mirando: una página de más al final, un control que no aparece
 * en ninguna, o un reparto que se desalinea cuando el total no es múltiplo del tamaño de página.
 */
class ControlPagingTest {

    // --- Cuántas columnas caben -------------------------------------------------------------

    @Test
    fun `el numero de columnas sale del ancho real`() {
        // 80 dp por celda, que es `CONTROL_SLOT_WIDTH`.
        assertEquals("móvil estrecho", 3, ControlPaging.columnsFor(250, 80))
        assertEquals("móvil normal en vertical", 4, ControlPaging.columnsFor(330, 80))
        assertEquals("un poco más ancho", 5, ControlPaging.columnsFor(420, 80))
        assertEquals("horizontal o tablet", 6, ControlPaging.columnsFor(600, 80))
    }

    @Test
    fun `nunca baja de tres ni sube de seis`() {
        // ⚠️ El mínimo se fuerza aunque no quepa: devolver 1 o 2 multiplicaría las páginas justo
        // en la pantalla donde más cuesta navegarlas. Las celdas encogen con `weight`.
        assertEquals(ControlPaging.MIN_COLUMNS, ControlPaging.columnsFor(0, 80))
        assertEquals(ControlPaging.MIN_COLUMNS, ControlPaging.columnsFor(80, 80))
        assertEquals(ControlPaging.MIN_COLUMNS, ControlPaging.columnsFor(-100, 80))
        // Y el techo: una pantalla enorme no convierte la tira en un muro.
        assertEquals(ControlPaging.MAX_COLUMNS, ControlPaging.columnsFor(2000, 80))
        assertEquals(3, ControlPaging.MIN_COLUMNS)
        assertEquals(6, ControlPaging.MAX_COLUMNS)
    }

    @Test
    fun `un ancho de celda invalido no revienta`() {
        assertEquals(ControlPaging.MIN_COLUMNS, ControlPaging.columnsFor(330, 0))
        assertEquals(ControlPaging.MIN_COLUMNS, ControlPaging.columnsFor(330, -80))
    }

    @Test
    fun `las columnas fijas ganan al ancho, pero no a los topes`() {
        // La excepción del panel de amplificador: tres por página aunque quepan seis, porque cada
        // página es una tríada que se ajusta junta (`AmpDomain.PANEL_LEVEL_ORDER`).
        assertEquals(3, ControlPaging.columnsFor(600, 80, fixedColumns = 3))
        assertEquals(3, ControlPaging.columnsFor(250, 80, fixedColumns = 3))
        // Y los topes siguen mandando: un fijo absurdo se recorta en vez de colarse.
        assertEquals(ControlPaging.MIN_COLUMNS, ControlPaging.columnsFor(600, 80, fixedColumns = 1))
        assertEquals(ControlPaging.MAX_COLUMNS, ControlPaging.columnsFor(600, 80, fixedColumns = 99))
    }

    @Test
    fun `sin columnas fijas nada cambia`() {
        // Que el parámetro nuevo sea opcional no basta: hay que fijar que null es exactamente el
        // comportamiento anterior, que es lo que usan las once tarjetas de efecto.
        listOf(0, 250, 330, 420, 600, 2000).forEach { width ->
            assertEquals(
                "ancho $width",
                ControlPaging.columnsFor(width, 80),
                ControlPaging.columnsFor(width, 80, fixedColumns = null),
            )
        }
    }

    @Test
    fun `los seis niveles del panel dan dos paginas de tres`() {
        // El caso real, escrito como caso: 6 controles, 3 por página, sin página coja.
        val perPage = ControlPaging.columnsFor(330, 80, fixedColumns = 3)
        assertEquals(2, ControlPaging.pageCount(6, perPage))
        assertEquals(0..2, ControlPaging.indicesOnPage(6, 0, perPage))
        assertEquals(3..5, ControlPaging.indicesOnPage(6, 1, perPage))
    }

    // --- Cuántas páginas --------------------------------------------------------------------

    @Test
    fun `las paginas son el techo de la division`() {
        assertEquals(1, ControlPaging.pageCount(1, 4))
        assertEquals(1, ControlPaging.pageCount(4, 4))
        assertEquals(2, ControlPaging.pageCount(5, 4))
        assertEquals(2, ControlPaging.pageCount(8, 4))
        assertEquals(3, ControlPaging.pageCount(9, 4))
        // El caso real que más se repite: los 11 parámetros de un EQ paramétrico.
        assertEquals(3, ControlPaging.pageCount(11, 4))
        assertEquals(4, ControlPaging.pageCount(11, 3))
        assertEquals(2, ControlPaging.pageCount(11, 6))
    }

    @Test
    fun `nunca hay cero paginas`() {
        // Un `HorizontalPager` con cero páginas no es un estado que la UI sepa pintar.
        assertEquals(1, ControlPaging.pageCount(0, 4))
        assertEquals(1, ControlPaging.pageCount(-3, 4))
        assertEquals(1, ControlPaging.pageCount(10, 0))
        assertEquals(1, ControlPaging.pageCount(10, -2))
    }

    // --- Qué controles caen en cada página ---------------------------------------------------

    @Test
    fun `cada control cae en exactamente una pagina`() {
        // La comprobación que de verdad importa: ninguno se pierde y ninguno sale dos veces.
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 20, 24, 33).forEach { count ->
            (3..6).forEach { perPage ->
                val vistos = mutableListOf<Int>()
                repeat(ControlPaging.pageCount(count, perPage)) { page ->
                    vistos += ControlPaging.indicesOnPage(count, page, perPage).toList()
                }
                assertEquals(
                    "con $count controles de $perPage en $perPage, todos y en orden",
                    (0 until count).toList(),
                    vistos,
                )
            }
        }
    }

    @Test
    fun `la ultima pagina puede venir corta`() {
        // 11 de 4 en 4 son 4 + 4 + 3. Esa página corta es correcta; lo que la UI hace es
        // rellenar el hueco para que sus tres no se estiren al ancho de cuatro.
        assertEquals(0..3, ControlPaging.indicesOnPage(11, 0, 4))
        assertEquals(4..7, ControlPaging.indicesOnPage(11, 1, 4))
        assertEquals(8..10, ControlPaging.indicesOnPage(11, 2, 4))
        assertEquals(3, ControlPaging.indicesOnPage(11, 2, 4).count())
    }

    @Test
    fun `una pagina fuera de rango sale vacia, no revienta`() {
        assertTrue(ControlPaging.indicesOnPage(11, 3, 4).isEmpty())
        assertTrue(ControlPaging.indicesOnPage(11, 99, 4).isEmpty())
        assertTrue(ControlPaging.indicesOnPage(11, -1, 4).isEmpty())
        assertTrue(ControlPaging.indicesOnPage(0, 0, 4).isEmpty())
        assertTrue(ControlPaging.indicesOnPage(11, 0, 0).isEmpty())
    }

    @Test
    fun `pageOfIndex es la inversa de indicesOnPage`() {
        val count = 23
        val perPage = 4
        (0 until count).forEach { index ->
            val page = ControlPaging.pageOfIndex(index, perPage)
            assertTrue(
                "el control $index debería estar en la página $page",
                index in ControlPaging.indicesOnPage(count, page, perPage),
            )
        }
    }

    @Test
    fun `pageOfIndex no se sale con entradas raras`() {
        assertEquals(0, ControlPaging.pageOfIndex(0, 4))
        assertEquals(0, ControlPaging.pageOfIndex(-5, 4))
        assertEquals(0, ControlPaging.pageOfIndex(7, 0))
    }

    // --- Los casos reales del proyecto -------------------------------------------------------

    @Test
    fun `los tamanos reales de las tarjetas dan un paginado razonable`() {
        // En un móvil normal (4 columnas), ninguna tarjeta del proyecto pasa de 3 páginas.
        val enUnMovil = 4
        val casos = mapOf(
            "Booster (6 params + nivel)" to 7,
            "Delay (4 + nivel)" to 5,
            "Reverb (4 + nivel)" to 5,
            "Solo (nivel)" to 1,
            "Noise Gate (2)" to 2,
            "EQ gráfico (11 bandas)" to 11,
            "EQ paramétrico (11)" to 11,
        )
        casos.forEach { (nombre, count) ->
            val pages = ControlPaging.pageCount(count, enUnMovil)
            assertTrue("$nombre: $pages páginas es demasiado", pages <= 3)
        }
    }
}
