package dev.alonx3.ktnacontrol.protocol.tsl

import dev.alonx3.ktnacontrol.protocol.Address

/**
 * Cuánto se puede confiar en la dirección de un bloque del `.tsl`.
 *
 * No es adorno: la diferencia entre [CONFIRMED] y [DISPUTED] decide si un valor se muestra o
 * se marca como desconocido. Leer un bloque en la dirección equivocada no falla ni avisa —
 * simplemente devuelve el byte de al lado y lo enseña como si fuera bueno, que es peor que no
 * enseñar nada.
 */
enum class TslConfidence {
    /**
     * La dirección viene de `presets_addrs.yaml` y **encaja con el fichero real**: el tamaño
     * declarado coincide con la longitud del array y el bloque no se solapa con ningún otro.
     */
    CONFIRMED,

    /**
     * El yaml deja `addr` vacío y la dirección se dedujo de la aritmética de offsets de
     * `reference/FxFloorboard/sysxWriter.cpp:377-390` sobre el volcado de patch (CLAUDE.md §5).
     * Encaja con los tamaños y no se solapa, pero **ninguna fuente la afirma directamente**.
     */
    DERIVED,

    /**
     * ⚠️ **Dos fuentes dan direcciones distintas y no hay forma de desempatarlas sin el
     * amplificador o un `.tsl` de prueba hecho a propósito** (CLAUDE.md §5, "Formato `.tsl`").
     *
     * Los bytes de estos bloques **no se cargan**: se listan como no disponibles. Es la
     * diferencia entre "este fichero no lo trae" y "lo trae y no sé dónde", y en las dos la
     * respuesta honesta es la misma — no enseñar un número inventado.
     */
    DISPUTED,
}

/**
 * Uno de los 22 bloques del `paramSet` de un `.tsl`, con su dirección SysEx y su tamaño.
 *
 * Es el puente que convierte un `.tsl` —un JSON de claves con nombre— en algo con direcciones,
 * que es como el resto del proyecto entiende la memoria del amplificador. Los bytes de dentro
 * son **los mismos que viajarían por el cable**, sin transformar (CLAUDE.md §5), así que una
 * vez colocados en su dirección se pueden leer con la misma maquinaria que un dump.
 *
 * **Fuente**: `reference/TuxKatana/params/presets_addrs.yaml` para las 12 claves con `addr`, y
 * la aritmética de `sysxWriter.cpp` para las 9 que lo tienen vacío (CLAUDE.md §5 las resolvió).
 */
data class TslBlock(
    /** La clave tal cual aparece en el `paramSet` del JSON. */
    val key: String,
    /** Dónde empieza el bloque en la memoria del amplificador. */
    val address: Address,
    /** Cuántos bytes trae, según el fichero real. Ver [TslBlockMap] para los desacuerdos. */
    val size: Int,
    val confidence: TslConfidence,
    /** Por qué se confía o no en esta dirección, para el aviso que ve el usuario. */
    val note: String? = null,
) {
    /** Los bytes de este bloque se cargan en el estado, o se ignoran por no ser de fiar. */
    val trusted: Boolean get() = confidence != TslConfidence.DISPUTED
}

/**
 * El mapa de las 22 claves de un `.tsl` a direcciones (CLAUDE.md §5, "Formato `.tsl`").
 *
 * ⚠️ **`presets_addrs.yaml` es la guía correcta pero tiene fallos concretos**, y este mapa
 * incorpora las tres correcciones que documenta CLAUDE.md:
 *
 * 1. **`UserPatch%Patch_1` mide 91 bytes, no los 50 que dice el yaml.** Con 50 se perderían el
 *    bloque de cadena entero, Solo, Contour y la posición de EQ2 — o sea casi todo lo que se
 *    documentó como "controles sin perilla física". Los 91 están verificados por dos vías: el
 *    fichero real los trae, y `sysxWriter.cpp:363-366` lo construye como `64 + 27 = 91`.
 * 2. **Nueve claves tienen `addr` vacío en el yaml** y aquí llevan la dirección que resolvió la
 *    aritmética de `sysxWriter.cpp` — marcadas [TslConfidence.DERIVED].
 * 3. **Dos desacuerdos entre fuentes se marcan [TslConfidence.DISPUTED] y no se cargan**: los
 *    tres `Contour` y `GafcExp1AsgnMinMax`. Ver abajo.
 *
 * ⚠️ **El tamaño de aquí es el esperado, no el que manda.** Quien manda es la longitud del
 * array en el fichero: si no coinciden, [TslParser] usa la del fichero y lo avisa, porque un
 * `.tsl` de otra revisión podría traer bloques de otro tamaño y truncarlo a ciegas sería
 * inventar.
 */
object TslBlockMap {

    /** La clave con el nombre del preset, que además es la que da el título en la lista. */
    const val NAME_KEY = "UserPatch%PatchName"

    val blocks: List<TslBlock> = listOf(
        TslBlock(NAME_KEY, Address(0x60, 0x00, 0x00, 0x00), 16, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%Patch_0", Address(0x60, 0x00, 0x00, 0x10), 72, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%Eq(2)", Address(0x60, 0x00, 0x00, 0x60), 24, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%Fx(1)", Address(0x60, 0x00, 0x01, 0x00), 221, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%Fx(2)", Address(0x60, 0x00, 0x03, 0x00), 221, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%Delay(1)", Address(0x60, 0x00, 0x05, 0x00), 26, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%Delay(2)", Address(0x60, 0x00, 0x05, 0x20), 26, TslConfidence.CONFIRMED),
        // ⚠️ 91, no los 50 del yaml. Aquí viven la cadena de efectos (índices 64-83), Solo,
        // Contour general y la posición de EQ2 — todo lo que el `size: 50` se dejaba fuera.
        TslBlock(
            "UserPatch%Patch_1", Address(0x60, 0x00, 0x05, 0x40), 91, TslConfidence.CONFIRMED,
            note = "el yaml dice 50 y son 91; con 50 se perdían la cadena, Solo, Contour y la posición de EQ2",
        ),
        TslBlock("UserPatch%Patch_2", Address(0x60, 0x00, 0x06, 0x20), 36, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%Status", Address(0x60, 0x00, 0x06, 0x50), 18, TslConfidence.CONFIRMED),

        // Las nueve que el yaml dejó sin dirección, resueltas con la aritmética de sysxWriter.
        TslBlock("UserPatch%KnobAsgn", Address(0x60, 0x00, 0x07, 0x00), 33, TslConfidence.DERIVED),
        TslBlock("UserPatch%ExpPedalAsgn", Address(0x60, 0x00, 0x08, 0x00), 33, TslConfidence.DERIVED),
        TslBlock("UserPatch%ExpPedalAsgnMinMax", Address(0x60, 0x00, 0x08, 0x30), 76, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%GafcExp1Asgn", Address(0x60, 0x00, 0x09, 0x00), 33, TslConfidence.DERIVED),
        // ⚠️ El yaml dice `09 30` y la aritmética de FxFloorboard da `09 34`. Sus dos hermanas
        // caen limpias en `08 30` y `0A 30`, así que `09 30` es la que encaja con el patrón —
        // pero el propio yaml marca esa línea, y solo esa, con "Sequence not valuable +1", o
        // sea que su autor ya había visto algo raro ahí. Sin desempate, no se carga.
        TslBlock(
            "UserPatch%GafcExp1AsgnMinMax", Address(0x60, 0x00, 0x09, 0x30), 76, TslConfidence.DISPUTED,
            note = "el yaml dice 09 30 y la aritmética de FxFloorboard da 09 34; el propio yaml marca esta línea como sospechosa",
        ),
        TslBlock("UserPatch%GafcExp2Asgn", Address(0x60, 0x00, 0x0A, 0x00), 33, TslConfidence.DERIVED),
        TslBlock("UserPatch%GafcExp2AsgnMinMax", Address(0x60, 0x00, 0x0A, 0x30), 76, TslConfidence.CONFIRMED),
        TslBlock("UserPatch%FsAsgn", Address(0x60, 0x00, 0x0F, 0x08), 2, TslConfidence.DERIVED),
        TslBlock("UserPatch%Patch_Mk2V2", Address(0x60, 0x00, 0x0F, 0x10), 22, TslConfidence.DERIVED),

        // ⚠️ Los tres Contour: `midi.xml` dice `0F 30`/`38`/`40` y la aritmética de FxFloorboard
        // da `0F 2E`/`36`/`3E`, dos menos, con el mismo paso de 8. CLAUDE.md documenta la de
        // `midi.xml` por ser una afirmación directa frente a una aritmética que además ya falla
        // en `GafcExp1AsgnMinMax` — pero "la más probable" no es "comprobada", y un desfase de
        // dos daría el Freq Shift donde va el Shape. No se cargan.
        TslBlock(
            "UserPatch%Contour(1)", Address(0x60, 0x00, 0x0F, 0x30), 2, TslConfidence.DISPUTED,
            note = "midi.xml dice 0F 30 y la aritmética de FxFloorboard da 0F 2E",
        ),
        TslBlock(
            "UserPatch%Contour(2)", Address(0x60, 0x00, 0x0F, 0x38), 2, TslConfidence.DISPUTED,
            note = "midi.xml dice 0F 38 y la aritmética de FxFloorboard da 0F 36",
        ),
        TslBlock(
            "UserPatch%Contour(3)", Address(0x60, 0x00, 0x0F, 0x40), 2, TslConfidence.DISPUTED,
            note = "midi.xml dice 0F 40 y la aritmética de FxFloorboard da 0F 3E",
        ),
    )

    /** Cuántos bloques trae un `.tsl` de Mk2 bien formado. */
    const val BLOCK_COUNT = 22

    private val byKey: Map<String, TslBlock> = blocks.associateBy { it.key }

    fun forKey(key: String): TslBlock? = byKey[key]

    /**
     * Lo que un `.tsl` **no guarda**, con independencia de cómo se parsee.
     *
     * ⚠️ Los cuatro parámetros de Pedal Bend de Mod y de FX se quedan fuera del formato:
     * `Fx(1)` acaba en `60 00 02 5C` y el bloque de Mod sigue hasta `02 60` (CLAUDE.md §5.2),
     * e igual en `Fx(2)`. Son 8 direcciones documentadas que un import/export por `.tsl`
     * **pierde**. Puede que vivan en `Patch_Mk2V2` (22 bytes sin desglosar en ninguna fuente),
     * pero eso es conjetura sin comprobar.
     */
    val KNOWN_GAPS: List<String> = listOf(
        "Pedal Bend de Mod y de FX (60 00 02 5D–60 y 04 5D–60): el formato no los guarda",
    )
}
