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
        SelectorId.AMP_SOLO,
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
}
