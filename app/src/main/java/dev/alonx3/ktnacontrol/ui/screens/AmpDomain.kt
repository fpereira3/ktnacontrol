package dev.alonx3.ktnacontrol.ui.screens

/**
 * A qué pantalla pertenece un control (CLAUDE.md §4.2, "Cómo se parte `SlidersPane`").
 *
 * Se modela como dato y no como "donde lo pusieron en el árbol de Compose" por una razón muy
 * concreta: **olvidarse de pintar un control no falla por ningún lado**. Queda invisible en las
 * dos pantallas, con su dirección registrada, su `StateFlow` poblándose y nadie enterándose. Con
 * el reparto escrito aquí, hay un test JVM que exige que cada control tenga dueño.
 */
enum class ControlDomain {
    /** El amplificador en sí: canal, modelo, niveles del panel, EQ, NG, Contour, cadena. */
    AMP,

    /** Uno de los cinco efectos, que se pintan en su tarjeta y no en la pantalla de amp. */
    EFFECT,

    /**
     * ❌ **Probado contra el amplificador y sin efecto: no se pinta en ninguna pantalla.**
     *
     * No es lo mismo que "sin clasificar". Bright y Gain SW se quitaron de la UI el 2026-09-06
     * tras comprobar con SET+GET que no cambian ni el sonido ni el estado interno (CLAUDE.md §5);
     * sus direcciones siguen registradas por si algún día aparece evidencia de que estaban mal
     * identificadas. Marcarlos aquí es lo que permite que el test exija dueño para todo lo demás
     * sin tener que hacer una excepción a mano.
     */
    RETIRED,
}

/**
 * Qué controles pertenecen al dominio de amplificador, y cuáles no.
 *
 * Es el reparto que usan [AmpScreen] y `SlidersPane`: la primera pinta **solo** lo de [AMP], la
 * segunda lo pinta todo. Kotlin puro, sin Compose ni `android.*`, para que se pueda probar.
 */
object AmpDomain {

    /**
     * Los niveles continuos del panel que **no** son de un efecto: Gain, Volume, Bass, Middle,
     * Treble y Presence.
     *
     * **Derivado, no escrito a mano** —es la misma expresión que ya vivía en `SlidersPane` como
     * `AMP_LEVELS`—: así, añadir un nivel a [LevelId] no puede dejarlo invisible, porque todo lo
     * que ningún efecto reclama cae aquí.
     */
    val LEVELS: List<LevelId> = LevelId.entries - EffectId.entries.map { it.level }.toSet()

    /**
     * **El orden en que se pintan los seis niveles del panel**, que a propósito **no** es el de
     * [LEVELS].
     *
     * [LEVELS] es una resta sobre `LevelId.entries` (§4.2), así que su orden es el del enum —el
     * del **mapa de direcciones**, `06 51`..`06 56`— y eso pone Gain y Volume delante de la
     * ecualización. Con la tira paginada de tres en tres, ese orden parte los controles por donde
     * no se usan: la primera página quedaría `Gain · Volume · Bass` y la segunda
     * `Middle · Treble · Presence`, con el **medio de la ecualización cortado entre dos páginas**.
     *
     * ✅ Con este orden cada página es un grupo que se toca junto:
     *
     * | Página | Controles | Qué es |
     * | --- | --- | --- |
     * | 1 | `BASS · MIDDLE · TREBLE` | la ecualización, que se ajusta **comparando las tres** |
     * | 2 | `GAIN · VOLUME · PRESENCE` | cuánto satura, cuánto suena y el brillo de arriba |
     *
     * ⚠️ **Y por eso esta tira fija sus columnas en 3** en vez de derivarlas del ancho como el
     * resto (`ControlPaging`): aquí el reparto **significa algo**, y en una pantalla ancha —donde
     * caben 5 o 6— las dos tríadas se fundirían en una fila sin agrupación. Es una excepción
     * deliberada a la decisión 2 del bloque C, no un descuido; ver [AmpSection].
     *
     * El `init` de abajo exige que sea una **permutación exacta** de [LEVELS]: añadir un nivel de
     * panel sin ponerlo aquí falla al cargar la clase, no en silencio en la pantalla.
     */
    val PANEL_LEVEL_ORDER: List<LevelId> = listOf(
        LevelId.BASS,
        LevelId.MIDDLE,
        LevelId.TREBLE,
        LevelId.GAIN,
        LevelId.VOLUME,
        LevelId.PRESENCE,
    )

    /** Los niveles de los cinco efectos, el complemento exacto de [LEVELS]. */
    val EFFECT_LEVELS: List<LevelId> = EffectId.entries.map { it.level }

    /**
     * Los selectores del amplificador, en el orden en que se pintan.
     *
     * ⚠️ **`ACTIVE_CHANNEL` está aquí pero es la excepción del contrato de Edit Mode** (§4.2):
     * es el único control que sigue habilitado con Edit Mode apagado. Pertenecer al dominio y
     * estar bajo `canEdit` son dos cosas distintas, y esta lista solo dice lo primero.
     */
    val SELECTORS: List<SelectorId> = listOf(
        SelectorId.ACTIVE_CHANNEL,
        SelectorId.AMP_CATEGORY,
        SelectorId.AMP_TYPE,
        SelectorId.AMP_VARIATION,
        SelectorId.NOISE_GATE,
        SelectorId.CONTOUR,
        SelectorId.CONTOUR_SELECT,
        SelectorId.EQ1_POSITION,
        SelectorId.EQ2_POSITION,
        // ⚠️ La cadena se queda en la pantalla de amplificador y no espera a una pantalla
        // propia: son cuatro selectores de **por dónde pasa la señal del amplificador**, no de
        // ningún efecto concreto, y el reordenado manual —lo único que habría justificado una
        // pantalla— se retiró el 2026-09-06 por no funcionar. Ver CLAUDE.md §4.2.
        SelectorId.CHAIN_TYPE,
        SelectorId.LOOP_POSITION,
        SelectorId.PEDAL_FX_POSITION,
    )

    /** Selectores que pertenecen a un efecto: viven en su tarjeta, nunca en la pantalla de amp. */
    val EFFECT_SELECTORS: List<SelectorId> = listOf(
        /**
         * ⚠️ **Solo cambió de dominio el 2026-09-09** (QA, bloque C): era del amplificador y
         * ahora es una tarjeta más de [EffectsSection], después de Reverb.
         *
         * El criterio es el mismo que reparte todo lo demás: **qué trata el control, no dónde
         * estaba**. Solo no ajusta el amplificador —no es una perilla del panel, ni el EQ, ni por
         * dónde pasa la señal—: es un realce conmutable con su propio nivel y (documentado, aún
         * sin cablear) su propio ecualizador. Eso es exactamente la forma de un efecto, y en el
         * amplificador era un huérfano en medio de Gain/Volume/Bass.
         *
         * Y hay un segundo argumento, de uso: Solo se pisa a la vez que Booster, no a la vez que
         * el modelo de amplificador.
         */
        SelectorId.AMP_SOLO,
        SelectorId.DELAY_HIGH_CUT,
        SelectorId.REVERB_LOW_CUT,
        SelectorId.REVERB_HIGH_CUT,
    )

    /** ❌ Los dos que se probaron y no hacen nada. Ver [ControlDomain.RETIRED]. */
    val RETIRED_SELECTORS: List<SelectorId> = listOf(
        SelectorId.AMP_BRIGHT,
        SelectorId.AMP_GAIN_SW,
    )

    /**
     * Los tres niveles sin perilla física, **todos del amplificador**: los dos del Noise Gate y
     * el Freq Shift del Contour activo. No hay ninguno de efecto, así que no hay complemento.
     */
    val NO_PANEL_PARAMS: List<NoPanelParamId> = NoPanelParamId.entries.toList()

    /** A qué pantalla pertenece [id]. */
    fun domainOf(id: SelectorId): ControlDomain = when (id) {
        in SELECTORS -> ControlDomain.AMP
        in EFFECT_SELECTORS -> ControlDomain.EFFECT
        in RETIRED_SELECTORS -> ControlDomain.RETIRED
        // Inalcanzable mientras el test de cobertura pase; existe porque `when` sobre una
        // colección no es exhaustivo para el compilador.
        else -> error("SelectorId sin dominio asignado: $id — ver AmpDomain")
    }

    /** A qué pantalla pertenece [id]. Un nivel es de efecto o del amplificador, sin más. */
    fun domainOf(id: LevelId): ControlDomain =
        if (id in EFFECT_LEVELS) ControlDomain.EFFECT else ControlDomain.AMP

    init {
        // Una reordenación a mano que se deje un nivel fuera —o que repita uno— haría desaparecer
        // un control del panel **sin ningún error**: la pantalla simplemente pintaría cinco. Que
        // reviente al cargar la clase es lo barato.
        require(PANEL_LEVEL_ORDER.toSet() == LEVELS.toSet() && PANEL_LEVEL_ORDER.size == LEVELS.size) {
            "PANEL_LEVEL_ORDER debe ser una permutación exacta de LEVELS: $PANEL_LEVEL_ORDER vs $LEVELS"
        }
    }
}
