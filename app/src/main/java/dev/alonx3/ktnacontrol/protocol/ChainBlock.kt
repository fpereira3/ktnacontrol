package dev.alonx3.ktnacontrol.protocol

/**
 * Los 20 bloques que pueden ocupar una posición de la cadena de efectos
 * (`60 00 06 00`–`06 13`, CLAUDE.md §5 "Cadena de efectos").
 *
 * La cadena del Mk2 es un **array de permutación**: veinte direcciones consecutivas, cada una
 * un selector de esta misma lista de veinte identificadores, y el orden en que aparecen es el
 * orden en que la señal los atraviesa. Escribir un identificador distinto en una posición
 * reordena la cadena de verdad.
 *
 * **Fuente única**: `reference/FxFloorboard/midi.xml:43074-43513`, los veinte bloques
 * `desc="Chain"`. ✅ Verificado mecánicamente que **las 20 posiciones ofrecen exactamente el
 * mismo catálogo de 20 valores** y que las direcciones van seguidas de `00` a `13`, en vez de
 * asumirlo desde la primera.
 *
 * ⚠️ **El MK1 no sirve de referencia aquí, y por una vez la diferencia es de fondo.**
 * `katana_sysex.txt:318-325` y `amplifier.json:129-140` dan la cadena del MK1 como *una sola*
 * dirección con *tres* valores (`One`/`Two`/`Three`). El Mk2 tiene siete cadenas predefinidas
 * (`60 00 06 20`) **y** este array de veinte. No transfiere ni la dirección ni la estructura.
 *
 * ⚠️ Nada de esto está confirmado con el amplificador. Es además el control con más potencial
 * de dejar un preset en un estado raro, así que conviene probarlo en PANEL.
 */
/**
 * El vocabulario reducido del **diagrama** de la cadena: diez bloques y nada más.
 *
 * Los veinte identificadores que el amplificador guarda no sirven para dibujar una cadena
 * legible — la mitad son puntos de ruteo interno (`CN_S`, `CN_M`, `CH_B`) o bloques que no
 * cambian el sonido de forma interesante (`CS`, `USB`, los dos noise gate). Este es el
 * vocabulario que se enseña.
 *
 * ✅ **`INPUT` y `SPEAKER` no salen del array**: son los extremos fijos.
 */
enum class ChainDiagramBlock(val displayName: String) {
    BOOSTER("BOOSTER"),
    FX("FX"),
    MOD("MOD"),
    AMP("AMP"),
    FXLOOP("FXLOOP"),
    DELAY("DELAY"),
    DELAY2("DELAY2"),
    REVERB("REVERB");

    companion object {
        /** El principio fijo del diagrama. */
        const val INPUT = "INPUT"

        /** El final fijo del diagrama. */
        const val SPEAKER = "SPEAKER"
    }
}

enum class ChainBlock(
    val value: Int,
    val displayName: String,
    val description: String,
    /**
     * Cómo se llama este bloque en el diagrama simplificado, o **null si no se dibuja**.
     *
     * ✅ **El mapeo está confirmado contra un editor de Mk2, no supuesto.** `FxFloorboard` hace
     * exactamente este trabajo —convertir las veinte ranuras en una cadena legible— en
     * `summaryDialog.cpp:89-105`, y ahí se leen `OD`→Booster, `FX1`→MOD, `FX2`→FX,
     * `DD1`→Delay 1, `DD2`→Delay 2, `RV`→Reverb, `LP`→Send/Return y `CH_A`→PreAmp. Los dos
     * últimos son los que resuelven `FXLOOP` y `AMP`, que no se pueden adivinar del nombre
     * crudo. `CH_A` está además confirmado por segunda vía en `stompBox.cpp:827`, que le pone
     * literalmente `fxName = tr("PreAmp")`.
     *
     * ⚠️ **Y las omisiones también salen de ahí**: ese mismo código sustituye por cadena vacía
     * `CH_B`, `CN_S`, `CN_M`, `NS_2`, `CS` y `USB` — o sea que un editor de Mk2 llegó por su
     * cuenta a la misma conclusión de que no pintan nada en un diagrama de señal.
     *
     * ⚠️ **`CAB` se omite aquí aunque FxFloorboard sí lo dibuje**, y es una decisión propia:
     * el diagrama termina siempre en un `SPEAKER` fijo, que es lo que `CAB` significa. Pintar
     * los dos sería decir lo mismo dos veces.
     */
    val diagramBlock: ChainDiagramBlock? = null,
) {
    CS(0x00, "CS", "Compresor"),
    LP(0x01, "LP", "Loop de send/return", ChainDiagramBlock.FXLOOP),
    CH_A(0x02, "CH_A", "PreAmp (el amplificador)", ChainDiagramBlock.AMP),
    CH_B(0x03, "CH_B", "Punto de ruteo interno; no se dibuja"),
    EQ1(0x04, "EQ1", "Ecualizador 1"),
    FX1(0x05, "FX1", "Mod", ChainDiagramBlock.MOD),
    FX2(0x06, "FX2", "FX", ChainDiagramBlock.FX),
    DD1(0x07, "DD1", "Delay 1", ChainDiagramBlock.DELAY),
    DD2(0x08, "DD2", "Delay 2", ChainDiagramBlock.DELAY2),
    RV(0x09, "RV", "Reverb", ChainDiagramBlock.REVERB),
    EQ2(0x0A, "EQ2", "Ecualizador 2"),
    PDL(0x0B, "PDL", "Pedal FX"),
    FV(0x0C, "FV", "Foot volume"),
    NS_1(0x0D, "NS_1", "Noise gate 1"),
    NS_2(0x0E, "NS_2", "Noise gate 2"),
    OD(0x0F, "OD", "Booster", ChainDiagramBlock.BOOSTER),
    USB(0x10, "USB", "Entrada/salida USB"),
    CN_S(0x11, "CN_S", "Cabinet stereo"),
    CAB(0x12, "CAB", "Cabinet"),
    CN_M(0x13, "CN_M", "Cabinet mono");

    companion object {
        /** Cuántas posiciones tiene la cadena: `60 00 06 00`–`06 13`. */
        const val SLOT_COUNT = 20

        fun fromValue(value: Int): ChainBlock? = entries.firstOrNull { it.value == value }

        /**
         * Convierte las veinte ranuras leídas en la secuencia del diagrama.
         *
         * Kotlin puro y sin `android.*` a propósito: así el filtro se puede probar en JVM, que
         * es lo único que se puede comprobar sin amplificador.
         *
         * @param slots los valores crudos de `60 00 06 00`–`06 13`; `null` donde no se leyó.
         * @return solo los bloques del vocabulario, en el orden del amplificador, **sin** los
         *   extremos fijos `INPUT` y `SPEAKER`, que los pone quien dibuja.
         */
        fun diagramSequence(slots: List<Int?>): List<ChainDiagramBlock> =
            slots.mapNotNull { raw -> raw?.let(::fromValue)?.diagramBlock }

        /** Los 20 valores crudos, para un [dev.alonx3.ktnacontrol.device.KatanaEnumParameter]. */
        val VALUES: List<Int> = entries.map { it.value }

        /** Etiqueta corta más descripción, como lo quiere un desplegable. */
        val OPTIONS: List<Pair<Int, String>> =
            entries.map { it.value to "${it.displayName} — ${it.description}" }
    }
}
