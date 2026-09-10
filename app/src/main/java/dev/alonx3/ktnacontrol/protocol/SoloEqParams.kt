package dev.alonx3.ktnacontrol.protocol

/**
 * **SOLO EQ — el ecualizador propio del Solo del amplificador, `60 00 0F 10`–`0F 19`**
 * (investigación documental del 2026-09-09, bloque B.1 del reporte de QA).
 *
 * Hasta ahora el proyecto solo conocía del Solo su on/off y su nivel, y con dos candidatas sin
 * desempatar (CLAUDE.md §5, "Controles sin perilla física"). Esto es otra cosa: un bloque de
 * **diez direcciones consecutivas** con su propio interruptor, un selector de posición y un
 * ecualizador de tres bandas completo.
 *
 * ## Por qué no aparecía en ninguna búsqueda anterior
 *
 * Porque vive en el bloque que el firmware 2 añadió. `60 00 0F 10`–`0F 25` es exactamente
 * `UserPatch%Patch_Mk2V2`, los 22 bytes que CLAUDE.md §5 ("Formato `.tsl`") tenía anotados como
 * *"Añadidos del firmware 2"* **sin desglosar** — y que solo existen a partir de
 * `formatRev "0002"`. De sus 22 bytes, los diez primeros son este SOLO EQ y los doce siguientes
 * un SOLO DELAY (`0F 1A`–`0F 25`, ver [SOLO_DELAY_RANGE]). Eso cierra de paso un cabo suelto del
 * `.tsl`: ese bloque ya no es opaco.
 *
 * También explica el silencio del resto de fuentes: TuxKatana y `katana-midi-bridge` no lo
 * mencionan —el segundo es MK1 y el primero no llegó a este bloque—, y `Adresses.txt` tampoco.
 *
 * ## Confianza: `midi.xml` ×2, el nivel más alto que da una sola fuente
 *
 * Es el mismo criterio de CLAUDE.md §5: la dirección aparece **dos veces, de forma
 * independiente**, dentro del mismo fichero.
 *
 *  1. **La tabla de destinos de asignación** (`midi.xml:3432-3441`), con el par
 *     `desc`/`customdesc` = 3.er/4.º byte que el proyecto ya verificó contra tres direcciones
 *     confirmadas por audio: `<PARAM value="1B" name="SOLO EQ: Low Cut" desc="0F"
 *     customdesc="12"/>` → `60 00 0F 12`.
 *  2. **El bloque `<Structure>`** (`midi.xml:49929-50024`), con `desc="SOLO EQ:"` y el
 *     `customdesc` del nombre de cada parámetro, más su rango o su lista de valores.
 *
 * Las dos coinciden en las diez direcciones y en el orden. **No hay `DISPUTED` en este bloque**:
 * no hay dos fuentes que se contradigan, hay una fuente que se confirma a sí misma por dos vías
 * y ninguna que la contradiga.
 *
 * ⚠️ **Nada de esto está probado contra el amplificador**, y hay un motivo concreto por el que
 * todavía no se registran como controles: ver [OUTSIDE_DUMP].
 */
object SoloEqParams {

    /** La primera dirección del bloque. Las diez van seguidas desde aquí. */
    val BASE = Address(0x60, 0x00, 0x0F, 0x10)

    /**
     * El SOLO DELAY que sigue al SOLO EQ dentro del mismo bloque de firmware 2.
     *
     * `60 00 0F 1A`–`0F 25`: Delay Sw, Delay Time (2 bytes, `0F 1C`+`1D`), Feedback, Effect,
     * Direct, Filter (Off/Analog/Tape Echo), High Cut (los mismos 15 de [HIGH_CUT_LABELS]),
     * Modulation, Rate y Depth — `midi.xml:50021-50048`. **No se extrae aquí**: el encargo era
     * el SOLO EQ, y anotarlo a medias sería peor que anotar dónde está. Queda como pista para
     * quien lo necesite (BACKLOG.md, "Por hacer").
     *
     * Juntos, `0F 10`–`0F 25` son los 22 bytes exactos de `UserPatch%Patch_Mk2V2`.
     */
    val SOLO_DELAY_FIRST = Address(0x60, 0x00, 0x0F, 0x1A)
    val SOLO_DELAY_LAST = Address(0x60, 0x00, 0x0F, 0x25)

    /**
     * ⚠️ **Las diez direcciones caen FUERA del dump, y por eso el bloque no se cablea todavía.**
     *
     * El dump pide `60 00 00 00` con tamaño `00 00 0F 00`, o sea hasta `60 00 0E 7F`, y el
     * amplificador real devolvió hasta `60 00 0E 43` (CLAUDE.md §4.4). `60 00 0F 1x` está fuera
     * de lo pedido **y** fuera de lo devuelto, igual que los Contour por slot.
     *
     * Eso importa mucho más de lo que parece. `loadFromDump` recupera lo que el dump no cubre
     * con **un GET individual en serie por control**, cada uno esperando hasta 800 ms. Hoy hay
     * **seis** controles así —los tres slots de Contour × Shape/Freq Shift— y CLAUDE.md ya
     * documenta que eso vale "hasta 4,8 s añadidos a cada conexión y a cada cambio de canal" si
     * esa región no contesta; hay incluso un test que **fija ese número en seis** para que nadie
     * añada un séptimo sin enterarse.
     *
     * Registrar estos diez lo llevaría a **dieciséis**, o sea hasta ~12,8 s por recarga en el
     * peor caso, y la recarga se dispara también al cambiar de canal. Es una regresión de
     * rendimiento peor que la ausencia del control.
     *
     * **Así que este fichero documenta y prueba la tabla, pero `KatanaRepository` no la registra
     * aún.** Desbloquearlo es una decisión aparte y ya está identificada en CLAUDE.md §5: o
     * ampliar el rango del dump para cubrir `60 00 0F xx`, o hacer los GET de respaldo en
     * paralelo en vez de en serie. Ninguna de las dos se hace a ciegas antes de saber si esa
     * región siquiera contesta a un GET (BACKLOG.md, "Pendiente por probar").
     */
    const val OUTSIDE_DUMP = true

    /**
     * Corte de graves, 18 posiciones — `midi.xml:49937`.
     *
     * ⚠️ Byte a byte la misma lista que [EqParams.LOW_CUT_LABELS], que [ReverbLowCutFrequency] y
     * que el `Lo Cut Off` del Parametric EQ de Mod/FX. Se repite aquí en vez de importarse por
     * la razón de siempre en este proyecto: son bloques distintos del amplificador, y compartir
     * la constante escondería el día que uno diverja. Hay un test que compara las dos.
     */
    val LOW_CUT_LABELS = listOf(
        "FLAT", "20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz",
        "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz",
    )

    /** Frecuencia central de la banda media, 28 posiciones — `midi.xml:49960`. */
    val MID_FREQ_LABELS = listOf(
        "20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz", "125Hz",
        "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz", "1.00k",
        "1.25k", "1.60k", "2.00k", "2.50k", "3.15k", "4.00k", "5.00k", "6.30k", "8.00k", "10.0k",
    )

    /** Ancho de banda de la media, 6 posiciones — `midi.xml:49990`. */
    val Q_LABELS = listOf("0.5", "1", "2", "4", "8", "16")

    /**
     * Corte de agudos, 15 posiciones — `midi.xml:50004`.
     *
     * ⚠️ En `0x0A` dice **`6.00k`**. Es el **cuarto** sitio independiente de `midi.xml` que dice
     * `6.00k` —con [EqParams.HIGH_CUT_LABELS], [ReverbHighCutFrequency] y el Parametric EQ de
     * Mod/FX— frente al único que dice `6.30K`, que es [DelayHighCutFrequency]. No cambia la
     * decisión de mantener los enums de Delay y Reverb separados (sigue sin haber prueba de
     * hardware), pero el desequilibrio de la evidencia ya es 4 a 1.
     */
    val HIGH_CUT_LABELS = listOf(
        "630Hz", "800Hz", "1.00k", "1.25k", "1.60k", "2.00k", "2.50k", "3.15k", "4.00k",
        "5.00k", "6.00k", "8.00k", "10.0k", "12.5k", "FLAT",
    )

    /** Dónde se inserta el SOLO EQ respecto al previo — `midi.xml:49929`. */
    val POSITION_LABELS = listOf("PreAmp In", "PreAmp Out")

    /**
     * Las cuatro ganancias del bloque, todas `range 00/30/-12.0/+12.0 dB`.
     *
     * ⚠️ **Es una escala de paso fraccionario, no entera**: crudo `0x00`..`0x30` (49 valores)
     * mostrado como `-12.0`..`+12.0` dB son pasos de **0,5 dB**. Es exactamente la misma forma
     * que el EQ **gráfico** de EQ1/EQ2 (CLAUDE.md §5) y **distinta** de la del EQ paramétrico,
     * que es `00/28/-20/+20` entera. Confundirlas daría el doble de recorrido con la mitad de
     * resolución, así que la distinción vale la pena tenerla escrita.
     */
    val GAIN_KIND = ParamKind.Fractional(
        rawRange = 0x00..0x30,
        displayRange = -12.0..12.0,
        step = 0.5,
    )

    /**
     * Los diez parámetros, en el orden en que ocupan `0F 10`–`0F 19`.
     *
     * Las etiquetas son las de `customdesc` en `midi.xml`, sin retocar: así se pueden buscar en
     * la fuente tal cual.
     */
    val PARAMS: List<ParamSpec> = listOf(
        spec(0x10, "Position", ParamKind.Enum(POSITION_LABELS.indices.toList(), POSITION_LABELS)),
        spec(0x11, "Off/On", ParamKind.Enum(listOf(0, 1), listOf("Off", "On"))),
        spec(0x12, "Low Cut", ParamKind.Enum(LOW_CUT_LABELS.indices.toList(), LOW_CUT_LABELS)),
        spec(0x13, "Low Gain", GAIN_KIND),
        spec(0x14, "Mid Freq", ParamKind.Enum(MID_FREQ_LABELS.indices.toList(), MID_FREQ_LABELS)),
        spec(0x15, "Mid Q", ParamKind.Enum(Q_LABELS.indices.toList(), Q_LABELS)),
        spec(0x16, "Mid Gain", GAIN_KIND),
        spec(0x17, "Hi Gain", GAIN_KIND),
        spec(0x18, "Hi Cut", ParamKind.Enum(HIGH_CUT_LABELS.indices.toList(), HIGH_CUT_LABELS)),
        spec(0x19, "Level", GAIN_KIND),
    )

    /** El interruptor propio del bloque, distinto del Solo del amplificador y del del Booster. */
    val OFF_ON = PARAMS.single { it.label == "Off/On" }.address

    private fun spec(lastByte: Int, label: String, kind: ParamKind) =
        ParamSpec(label, Address(0x60, 0x00, 0x0F, lastByte), kind)
}
