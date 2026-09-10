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

    /**
     * ⚠️ **Deja de dibujarse desde el 2026-09-09 (QA, bloque A.2), y es una decisión.**
     *
     * El oráculo de este diagrama pasó a ser **Boss Tone Studio**, cuya vista de cadena de señal
     * enseña exactamente siete bloques —Booster, Mod, FX, Amp, Delay, Delay 2, Reverb— entre
     * `IN` y `SPEAKER`, y **no** el send/return. Dibujarlo aquí hacía que ninguna de las siete
     * cadenas de fábrica coincidiera con lo que el usuario ve en el editor oficial.
     *
     * No es que el loop no exista: es que **no es un bloque de tono, es un punto de inserción**,
     * y tiene su propio selector ([KatanaAddresses.LOOP_POSITION], `60 00 06 21`, Post Amp /
     * Post Reverb) justo al lado del diagrama. Se controla ahí, no se dibuja aquí.
     */
    LP(0x01, "LP", "Loop de send/return"),
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
         * ⚠️ **Corta en [CAB] y no sigue, arreglado el 2026-09-09** (QA, bloque A.2: "el orden no
         * tiene sentido físico — un bloque después de SPEAKER es imposible").
         *
         * El diagrama sustituye [CAB] por un `SPEAKER` fijo al final. Mientras la lista se
         * recorría entera, **todo lo que el array pusiera después del cabinet se dibujaba antes
         * de ese `SPEAKER`**, o sea en un sitio que afirma lo contrario de lo que dice el
         * amplificador: sonar antes del altavoz cuando en realidad va después. Con las siete
         * cadenas de fábrica no se notaba —después de `CAB` solo quedan bloques que no se
         * dibujan—, pero era cierto por casualidad, no por diseño, y bastaba una cadena editada
         * a mano para verlo. Ahora el corte es explícito y hay un test que lo fija.
         *
         * @param slots los valores crudos de `60 00 06 00`–`06 13`; `null` donde no se leyó.
         * @return solo los bloques del vocabulario, en el orden del amplificador, **sin** los
         *   extremos fijos `INPUT` y `SPEAKER`, que los pone quien dibuja.
         */
        fun diagramSequence(slots: List<Int?>): List<ChainDiagramBlock> =
            slots.takeWhile { raw -> raw?.let(::fromValue) != CAB }
                .mapNotNull { raw -> raw?.let(::fromValue)?.diagramBlock }

        /** Los 20 valores crudos, para un [dev.alonx3.ktnacontrol.device.KatanaEnumParameter]. */
        val VALUES: List<Int> = entries.map { it.value }

        /** Etiqueta corta más descripción, como lo quiere un desplegable. */
        val OPTIONS: List<Pair<Int, String>> =
            entries.map { it.value to "${it.displayName} — ${it.description}" }
    }
}


/**
 * Las veinte ranuras de una cadena, escritas como los hex que aparecen en la fuente.
 *
 * Se transcriben en hexadecimal y no como nombres de [ChainBlock] a propósito: así la línea de
 * aquí se puede **comparar carácter a carácter** con la de `floorBoard.cpp`, que es de donde
 * salen. La validación es estricta —veinte valores, todos conocidos, sin repetir— porque una
 * transcripción de veinte bytes es exactamente el sitio donde se cuela un error silencioso.
 */
private fun chainSlots(hex: String): List<ChainBlock> {
    val blocks = hex.trim().split(" ").map { byte ->
        val value = byte.toInt(16)
        requireNotNull(ChainBlock.fromValue(value)) { "valor de cadena desconocido: $byte" }
    }
    require(blocks.size == ChainBlock.SLOT_COUNT) {
        "una cadena son ${ChainBlock.SLOT_COUNT} ranuras, no ${blocks.size}"
    }
    require(blocks.toSet().size == blocks.size) { "la cadena repite un bloque: $hex" }
    return blocks
}

/**
 * **Las siete cadenas predefinidas de `60 00 06 20`**, con el nombre que les da Boss y el orden
 * de las veinte ranuras que cada una deja escrito (QA 2026-09-09, bloque A.2).
 *
 * ⚠️ **`midi.xml` NO sirve para esto y conviene decirlo**: su entrada para esta dirección es
 * `<DATA value="20" desc="Chain" customdesc="position">` con un escueto `range 00/06/00/06`
 * (`midi.xml:43552`) — siete valores sin un solo nombre. Ni `Adresses.txt`, ni los YAML de
 * TuxKatana, ni `katana-midi-bridge` mencionan la cadena del Mk2 (el MK1 tiene otra cosa: una
 * sola dirección con tres valores, §5 "Cadena de efectos"). O sea que el valor crudo de cada
 * cadena **no estaba documentado en ninguna fuente que el proyecto ya usara**.
 *
 * ✅ **Sale de `reference/FxFloorboard/floorBoard.cpp:490-599`**, que es una fuente de Mk2 **y en
 * código**: siete funciones `chain_N_Set()`, cada una escribiendo su valor en
 * `Structure 06 00 20` —o sea `60 00 06 20`— y acto seguido las veinte ranuras. Los nombres de
 * los botones que las disparan están en `floorBoardDisplay.cpp:158-177`, y dan el orden
 * `CHAIN 1 · 2-1 · 3-1 · 4-1 · 2-2 · 3-2 · 4-2` para los valores `00`..`06` — que **no** es el
 * orden que sugeriría el nombre (las dos familias van seguidas, no intercaladas).
 *
 * ✅ **Y el resultado cuadra con Boss Tone Studio a la primera**: filtradas por
 * [ChainBlock.diagramSequence], las siete dan exactamente las siete secuencias que enseña BTS
 * (transcritas por el usuario en el reporte de QA), incluido el detalle de que **Booster y Mod
 * se intercambian** entre las familias `-1` y `-2` y de que **AMP es lo único que se desplaza**
 * de una fila a la siguiente. Dos fuentes independientes —el código de un editor de PC y la
 * pantalla del editor oficial— que coinciden bloque a bloque en las siete.
 *
 * ⚠️ **[slots] es el orden "de fábrica", sin los cuatro reubicables.** `floorBoard.cpp` aplica
 * después cuatro correcciones según los selectores de posición —loop (`06 21`), EQ1 (`06 22`),
 * EQ2 (`06 19`) y Pedal/FX (`06 23`)—, que mueven `LP`, `EQ1`, `EQ2` y `PDL` dentro del array.
 * Ninguno de los cuatro se dibuja en el diagrama, así que **no cambian la secuencia visible**;
 * por eso no se replican aquí. Si algún día alguno entra en el vocabulario del diagrama, esto
 * hay que revisarlo.
 *
 * ⚠️ **Sin confirmar contra el amplificador**: que estos sean los valores crudos correctos sale
 * de código ajeno, no de una prueba. Lo que sí está comprobado en JVM es que el diagrama que
 * producen es el que espera el usuario.
 */
enum class ChainPreset(
    /** El valor crudo de [KatanaAddresses.CHAIN_TYPE]. */
    val value: Int,
    /** Como lo llama Boss — `floorBoardDisplay.cpp:158-177`. */
    val displayName: String,
    /** Las veinte ranuras que esta cadena deja en `60 00 06 00`–`06 13`. */
    val slots: List<ChainBlock>,
) {
    CHAIN_1(0x00, "CHAIN 1", chainSlots("0B 0F 04 0A 02 00 05 06 0D 0C 01 07 11 08 09 12 03 0E 10 13")),
    CHAIN_2_1(0x01, "CHAIN 2-1", chainSlots("0B 0F 05 04 0A 02 00 06 0D 0C 01 07 11 08 09 12 03 0E 10 13")),
    CHAIN_3_1(0x02, "CHAIN 3-1", chainSlots("0B 0F 05 06 04 0A 02 00 0D 0C 01 07 11 08 09 12 03 0E 10 13")),
    CHAIN_4_1(0x03, "CHAIN 4-1", chainSlots("0B 0F 05 06 07 04 0A 02 00 0D 0C 01 11 08 09 12 03 0E 10 13")),
    CHAIN_2_2(0x04, "CHAIN 2-2", chainSlots("0B 05 0F 04 0A 02 00 06 0D 0C 01 07 11 08 09 12 03 0E 10 13")),
    CHAIN_3_2(0x05, "CHAIN 3-2", chainSlots("0B 05 0F 06 04 0A 02 00 0D 0C 01 07 11 08 09 12 03 0E 10 13")),
    CHAIN_4_2(0x06, "CHAIN 4-2", chainSlots("0B 05 0F 06 07 04 0A 02 00 0D 0C 01 11 08 09 12 03 0E 10 13"));

    /** Las veinte ranuras como valores crudos, tal y como llegan del amplificador. */
    val rawSlots: List<Int?> get() = slots.map { it.value }

    /** Lo que el diagrama enseña para esta cadena, entre `INPUT` y `SPEAKER`. */
    val diagram: List<ChainDiagramBlock> get() = ChainBlock.diagramSequence(rawSlots)

    companion object {
        fun fromValue(value: Int): ChainPreset? = entries.firstOrNull { it.value == value }

        /** Etiqueta para el selector: el nombre de Boss, no un número de orden. */
        val OPTIONS: List<Pair<Int, String>> = entries.map { it.value to it.displayName }
    }
}
