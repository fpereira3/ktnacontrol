package dev.alonx3.ktnacontrol.ui.screens

/**
 * **Cuántos controles caben por página y cuáles van en cada una** (QA 2026-09-09, bloque C).
 *
 * Kotlin puro, sin Compose ni `android.*`, por el mismo motivo que [knobValueAt]: es la parte de
 * la paginación que **puede estar mal de una forma que no se ve mirando la pantalla** —una página
 * de más al final, un control que no aparece en ninguna, un reparto que se desalinea con 11
 * elementos— y es lo único de todo esto que no exige un dedo para comprobarse.
 *
 * ## Cuántos controles por página: **se deriva del ancho real**, no es un número fijo
 *
 * El encargo daba las dos opciones por válidas. Se elige derivarlo, y el argumento es que **un
 * número fijo son dos experiencias distintas según el dispositivo**:
 *
 * | Ancho útil de la tarjeta | Con 4 fijos | Derivado |
 * | --- | --- | --- |
 * | ~250 dp (móvil estrecho, letra grande) | 62 dp por control: apretado | 3 |
 * | ~330 dp (móvil normal en vertical) | 82 dp: bien | 4 |
 * | ~600 dp (horizontal o tablet) | 4 controles y media pantalla vacía, **con páginas que no
 *   hacían falta** | 6 |
 *
 * La última fila es la que decide: con un número fijo, un ecualizador de 11 bandas se pagina en 3
 * páginas incluso en una pantalla donde caben todas de sobra. Y derivarlo **no** cuesta
 * incertidumbre: el ancho ya lo mide el contenedor, así que la cuenta es determinista y se prueba
 * aquí.
 *
 * ⚠️ **Los topes también son una decisión.** [MIN_COLUMNS] `= 3` respeta el suelo que pedía el
 * encargo ("3-4 por vez") y evita que en un ancho muy pequeño quede una tira de 2 que ya no se
 * lee como tira. [MAX_COLUMNS] `= 6` existe para que una pantalla ancha no convierta la tira en
 * un muro de controles diminutos donde ya no se distingue cuál es cuál: pasado ese punto la
 * paginación deja de ser una comodidad y pasa a ser lo único que hace la tarjeta legible.
 */
internal object ControlPaging {

    /** El suelo: menos de tres deja de leerse como una tira de controles. */
    const val MIN_COLUMNS = 3

    /** El techo: más de seis es un muro, y la tira deja de escanearse de un vistazo. */
    const val MAX_COLUMNS = 6

    /**
     * Cuántos controles caben en [availableWidthDp] con celdas de [slotWidthDp].
     *
     * @param availableWidthDp el ancho útil **ya medido** por el contenedor, en dp.
     * @param slotWidthDp lo que ocupa un control, en dp. Debe ser > 0.
     * @param fixedColumns si la tira **agrupa** en vez de rellenar, cuántos por página; ver abajo.
     * @return un número entre [MIN_COLUMNS] y [MAX_COLUMNS], ambos incluidos.
     *
     * ⚠️ **Se fuerza el mínimo aunque no quepa.** Con menos de `3 × slot` de ancho la alternativa
     * sería devolver 1 o 2, y eso multiplicaría las páginas justo en la pantalla donde más cuesta
     * navegarlas. Que tres celdas se estrechen un poco es peor que tener seis páginas de un
     * control: el reparto se hace con `weight`, así que encogen en vez de desbordar.
     *
     * ⚠️ **[fixedColumns] es la excepción a la decisión de derivar del ancho, y tiene un criterio
     * concreto: solo vale cuando el reparto en páginas *significa algo*.** El caso real es la tira
     * del panel de amplificador ([AmpDomain.PANEL_LEVEL_ORDER]): sus dos páginas son
     * `Bass · Middle · Treble` y `Gain · Volume · Presence`, dos grupos que se ajustan por
     * separado. Derivar ahí del ancho los fundiría en una sola fila en cuanto la pantalla diera
     * para 6, perdiendo la agrupación — que es justo lo contrario de lo que la paginación busca.
     * Para una tira donde los controles solo van uno detrás de otro (los efectos, los EQ), pasar
     * un número fijo sería volver al problema que [MAX_COLUMNS] y esta función resuelven.
     */
    fun columnsFor(availableWidthDp: Int, slotWidthDp: Int, fixedColumns: Int? = null): Int {
        if (fixedColumns != null) return fixedColumns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        if (slotWidthDp <= 0) return MIN_COLUMNS
        val fits = availableWidthDp / slotWidthDp
        return fits.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }

    /**
     * Cuántas páginas hacen falta para [itemCount] controles de [perPage] en [perPage].
     *
     * ⚠️ **Nunca devuelve 0**, ni siquiera sin controles: un `HorizontalPager` con cero páginas
     * no es un estado que la UI sepa pintar, y una tarjeta sin parámetros ya se resuelve antes de
     * llegar aquí (no se pinta la tira). Devolver 1 mantiene el invariante de que
     * `pageCount ≥ 1` siempre.
     */
    fun pageCount(itemCount: Int, perPage: Int): Int {
        if (perPage <= 0) return 1
        if (itemCount <= 0) return 1
        return (itemCount + perPage - 1) / perPage
    }

    /** En qué página cae el control [index] (base 0). */
    fun pageOfIndex(index: Int, perPage: Int): Int {
        if (perPage <= 0) return 0
        return (index.coerceAtLeast(0)) / perPage
    }

    /**
     * Los índices de los controles que se pintan en [page].
     *
     * La última página puede venir corta —11 controles de 4 en 4 dan 4+4+3— y eso es correcto: el
     * hueco se rellena con celdas vacías al pintar, para que los tres de la última no se estiren
     * al triple del ancho de los de arriba.
     *
     * @return el rango de índices, **vacío** si la página está fuera de rango.
     */
    fun indicesOnPage(itemCount: Int, page: Int, perPage: Int): IntRange {
        if (perPage <= 0 || itemCount <= 0 || page < 0) return IntRange.EMPTY
        val first = page * perPage
        if (first >= itemCount) return IntRange.EMPTY
        val last = (first + perPage - 1).coerceAtMost(itemCount - 1)
        return first..last
    }
}
