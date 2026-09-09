package dev.alonx3.ktnacontrol.protocol

/**
 * Los dos bloques de ecualizador **por preset** del Katana Mk2: EQ1 (`60 00 00 40`–`00 57`) y
 * EQ2 (`60 00 00 60`–`00 77`), 24 direcciones cada uno (CLAUDE.md §5, "Controles sin perilla
 * física").
 *
 * Cada bloque es en realidad **dos ecualizadores con un selector**: un paramétrico de 11
 * parámetros y un gráfico de 10 bandas + Level, y `Selection` (`00 41` / `00 61`) dice cuál
 * está sonando. Las 24 direcciones existen siempre; lo que cambia es cuál de los dos juegos
 * tiene efecto.
 *
 * **Fuente única**: `reference/FxFloorboard/midi.xml:37562-37735` (EQ1, bloques `desc="PEQ1:"`
 * y `"GEQ1:"`) y `:37739-37910` (EQ2, `"PEQ2:"` / `"GEQ2:"`). Ninguna otra fuente de Mk2 los
 * cubre; `presets_addrs.yaml:7-9` corrobora la extensión de EQ2 (`addr '60 00 00 60'`,
 * `size 24`) pero no su contenido.
 *
 * ✅ **`EQ2 = EQ1 + 0x20` verificado mecánicamente**, no supuesto: comparando los 24 nodos de
 * cada bloque por dirección relativa, nombre de parámetro y lista de `<PARAM>`, salen
 * **0 diferencias** de las tres clases. Es la misma comprobación que se hizo para
 * `FX = Mod + 0x0200` ([ModFxInternalParams.FX_OFFSET]).
 *
 * ⚠️ **Nada de esto está confirmado con el amplificador.**
 *
 * **No confundir con el EQ global**, que existe y vive en otro espacio de direcciones
 * (`00 00 00 10`–`00 00 01 07`, bloque `<System>`): ese es global y no por preset, tiene una
 * Position de cuatro valores en vez de dos, y no está investigado más allá de constatar que
 * existe (CLAUDE.md §5).
 */
object EqParams {

    /**
     * `EQ2 = EQ1 + 0x20`, y aquí el literal hexadecimal **sí** es el entero a sumar, al
     * contrario que en [ModFxInternalParams.FX_OFFSET].
     *
     * La diferencia está en qué byte de la dirección se mueve: FX cambia el **tercero**
     * (`60 00 01 xx` → `60 00 03 xx`), y como [Address.value] es base 128, mover ese byte en 2
     * son `2 × 128 = 256`. EQ2 cambia el **cuarto** (`60 00 00 40` → `60 00 00 60`), que es el
     * de menor peso, así que sumarle `0x20` es literalmente sumar 32. Las dos reglas se
     * escriben igual en la documentación y significan cosas distintas — de ahí que cada una
     * lleve su constante y su test.
     */
    const val EQ2_OFFSET = 0x20

    /** Dirección base de EQ1; la de EQ2 es esta `+ EQ2_OFFSET`. */
    val EQ1_BASE = Address(0x60, 0x00, 0x00, 0x40)

    /**
     * Corte de graves del paramétrico, 18 posiciones — `midi.xml:37570`.
     *
     * ⚠️ Es **byte a byte la misma lista** que `Lo Cut Off` del Parametric EQ de Mod/FX
     * (`60 00 01 2C`) y que [ReverbLowCutFrequency]; hay un test que lo comprueba contra la
     * primera, para que el día que alguna diverja se entere alguien. Se define aquí igualmente
     * en vez de importar la del reverb: son bloques distintos del amplificador, y compartir la
     * constante escondería precisamente esa divergencia (es lo que ya pasa entre
     * [DelayHighCutFrequency] y [ReverbHighCutFrequency], que parecen la misma y no lo son).
     */
    val LOW_CUT_LABELS = listOf(
        "FLAT", "20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz",
        "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz",
    )

    /** Frecuencia central de las dos bandas medias, 28 posiciones — `midi.xml:37593` y `:37634`. */
    val MID_FREQ_LABELS = listOf(
        "20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz", "125Hz",
        "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz", "1.00k",
        "1.25k", "1.60k", "2.00k", "2.50k", "3.15k", "4.00k", "5.00k", "6.30k", "8.00k", "10.0k",
    )

    /** Ancho de banda de las dos medias, 6 posiciones — `midi.xml:37623` y `:37664`. */
    val Q_LABELS = listOf("0.5", "1", "2", "4", "8", "16")

    /**
     * Corte de agudos del paramétrico, 15 posiciones — `midi.xml:37678`.
     *
     * ⚠️ En `0x0A` dice **`6.00k`**, igual que [ReverbHighCutFrequency] y que el Parametric EQ
     * de Mod/FX, y distinto de [DelayHighCutFrequency], que ahí pone `6.30K`. Es el tercer y
     * cuarto sitio del mismo fichero que dicen `6.00k` frente al único que dice `6.30K`
     * (CLAUDE.md §5.2): no cambia la decisión de mantener los enums de Delay y Reverb
     * separados, pero sí de qué lado está el peso de la evidencia.
     */
    val HIGH_CUT_LABELS = listOf(
        "630Hz", "800Hz", "1.00k", "1.25k", "1.60k", "2.00k", "2.50k", "3.15k", "4.00k",
        "5.00k", "6.00k", "8.00k", "10.0k", "12.5k", "FLAT",
    )

    /** Las 10 bandas fijas del gráfico, en el orden en que ocupan `00 4D`–`00 56`. */
    val GRAPHIC_BAND_LABELS = listOf(
        "31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1KHz", "2KHz", "4KHz", "8KHz", "16KHz",
    )

    /**
     * La escala del EQ gráfico: crudo `0x00`..`0x30` (49 valores) mostrado como `-12.0`..`+12.0`
     * dB, o sea **pasos de 0,5 dB** — `midi.xml:37698` en adelante, `range 00/30/-12.0/+12.0`.
     *
     * ⚠️ **No es [LevelScale.centered]**, aunque el rango también esté centrado en cero: 49
     * valores crudos sobre 24,0 dB no son "un byte = una unidad", y `LevelScale` solo sabe
     * sumar un desplazamiento entero. Tampoco es la misma escala que el Graphic EQ **interno de
     * Mod/FX**, que sí es entero (`00/28/-20/+20`, `LevelScale.centered(20)`): coinciden en
     * llamarse igual y en nada más.
     */
    val GRAPHIC_SCALE = FractionalLevelScale(
        rawRange = 0x00..0x30,
        displayRange = -12.0..12.0,
        step = 0.5,
    )

    /** Escala de los cinco Gain y el Level del paramétrico: `00/28/-20/+20` dB, entera. */
    const val PARAMETRIC_GAIN_RADIUS = 20

    private fun enumOf(labels: List<String>) =
        ParamKind.Enum(values = labels.indices.toList(), labels = labels)

    /**
     * Los 24 parámetros de un bloque de EQ, con las direcciones de **EQ1**; para EQ2,
     * `+ EQ2_OFFSET`. En el mismo orden en que ocupan la memoria, que es también el orden en
     * que conviene mostrarlos.
     */
    val SPECS: List<ParamSpec> = listOf(
        ParamSpec("On/Off", Address(0x60, 0x00, 0x00, 0x40), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Off", "On"),
        )),
        // ⚠️ `midi.xml` escribe "Parameteric" (con una `e` de más) en los dos bloques; es un
        // error de la fuente, no un nombre distinto.
        ParamSpec("Selection", Address(0x60, 0x00, 0x00, 0x41), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Parametric", "Graphic"),
        )),
        ParamSpec("Low Cut", Address(0x60, 0x00, 0x00, 0x42), enumOf(LOW_CUT_LABELS)),
        ParamSpec("Low Gain", Address(0x60, 0x00, 0x00, 0x43), ParamKind.Centered(PARAMETRIC_GAIN_RADIUS)),
        ParamSpec("Lo Mid Freq", Address(0x60, 0x00, 0x00, 0x44), enumOf(MID_FREQ_LABELS)),
        ParamSpec("Lo Mid Q", Address(0x60, 0x00, 0x00, 0x45), enumOf(Q_LABELS)),
        ParamSpec("Lo Mid Gain", Address(0x60, 0x00, 0x00, 0x46), ParamKind.Centered(PARAMETRIC_GAIN_RADIUS)),
        ParamSpec("Hi Mid Freq", Address(0x60, 0x00, 0x00, 0x47), enumOf(MID_FREQ_LABELS)),
        ParamSpec("Hi Mid Q", Address(0x60, 0x00, 0x00, 0x48), enumOf(Q_LABELS)),
        ParamSpec("Hi Mid Gain", Address(0x60, 0x00, 0x00, 0x49), ParamKind.Centered(PARAMETRIC_GAIN_RADIUS)),
        ParamSpec("Hi Gain", Address(0x60, 0x00, 0x00, 0x4A), ParamKind.Centered(PARAMETRIC_GAIN_RADIUS)),
        ParamSpec("Hi Cut", Address(0x60, 0x00, 0x00, 0x4B), enumOf(HIGH_CUT_LABELS)),
        ParamSpec("Level", Address(0x60, 0x00, 0x00, 0x4C), ParamKind.Centered(PARAMETRIC_GAIN_RADIUS)),
    ) + GRAPHIC_BAND_LABELS.mapIndexed { index, band ->
        ParamSpec(
            label = band,
            address = Address(0x60, 0x00, 0x00, 0x4D + index),
            kind = ParamKind.Fractional(
                rawRange = GRAPHIC_SCALE.rawRange,
                displayRange = GRAPHIC_SCALE.displayRange,
                step = GRAPHIC_SCALE.step,
            ),
        )
    } + ParamSpec(
        // El Level del gráfico, que **no** es el mismo que el `Level` del paramétrico (`00 4C`):
        // distinta dirección, distinta escala. Se etiqueta aparte para que no se confundan.
        label = "Level (gráfico)",
        address = Address(0x60, 0x00, 0x00, 0x57),
        kind = ParamKind.Fractional(
            rawRange = GRAPHIC_SCALE.rawRange,
            displayRange = GRAPHIC_SCALE.displayRange,
            step = GRAPHIC_SCALE.step,
        ),
    )

    /** Cuántas direcciones ocupa un bloque, para comprobar que los dos no se solapan. */
    const val BLOCK_SIZE = 24
}

/**
 * Cuál de los dos ecualizadores de un bloque está activo — el valor de `Selection`
 * (`60 00 00 41` en EQ1, `00 61` en EQ2).
 *
 * Existe como enum, y no como un `Int` suelto, porque la UI lo necesita para decidir **qué
 * mitad de los 24 parámetros tiene sentido mostrar**: con el paramétrico activo, las 11 bandas
 * del gráfico no hacen nada, y al revés.
 */
enum class EqSelection(val value: Int, val displayName: String) {
    PARAMETRIC(0x00, "Parametric"),
    GRAPHIC(0x01, "Graphic");

    companion object {
        fun fromValue(value: Int): EqSelection? = entries.firstOrNull { it.value == value }
    }
}
