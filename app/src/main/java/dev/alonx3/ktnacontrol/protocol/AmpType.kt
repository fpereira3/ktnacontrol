package dev.alonx3.ktnacontrol.protocol

/**
 * The amp models the Katana Mk2 exposes at [KatanaAddresses.AMP_TYPE_FULL] (`60 00 00 21`).
 *
 * **Source: `reference/FxFloorboard/midi.xml:37311-37341`**, the `<DATA value="21" ...
 * desc="PREAMP:" customdesc="Type">` block — the table that belongs to that exact address.
 * It is the most complete of the three sources and the only one that is unambiguously about
 * the Mk2: `reference/TuxKatana/params/amplifier.yaml:16-45` and
 * `reference/TuxKatana/doc/Adresses.txt:3-33` list the same values but **miss `BG Lead`
 * (`0x10`)**, so this table has 30 entries where those have 29.
 *
 * Declared in the order `midi.xml` lists them: the five base categories, then their
 * variations, then the individual models (what the community calls the "sneaky amps").
 *
 * ⚠️ **Nothing here is confirmed against the amplifier yet.** The values come from
 * documentation only; writing them has never been tried (CLAUDE.md §5).
 *
 * Not included: `0x19` "Custom", which appears only in a *different* block of the same file
 * (`midi.xml:51292`, "Preamp convert mk2") and **not** in the table for address `00 21`. If
 * a Custom amp turns out to be selectable, that is where to look first.
 */
enum class AmpType(val value: Int, val displayName: String) {
    ACOUSTIC(0x01, "Acoustic"),
    CLEAN(0x08, "Clean"),
    CRUNCH(0x0B, "Crunch"),
    LEAD(0x18, "Lead"),
    BROWN(0x17, "Brown"),
    ACOUSTIC_VARIATION(0x1C, "Var Acoustic"),
    CLEAN_VARIATION(0x1D, "Var Clean"),
    CRUNCH_VARIATION(0x1E, "Var Crunch"),
    LEAD_VARIATION(0x1F, "Var Lead"),
    BROWN_VARIATION(0x20, "Var Brown"),
    NATURAL_CLEAN(0x00, "Natural Clean"),
    CLEAN_TWIN(0x09, "Clean Twin"),
    COMBO_CRUNCH(0x02, "Combo Crunch"),
    PRO_CRUNCH(0x0A, "Pro Crunch"),
    DELUXE_CRUNCH(0x0C, "Deluxe Crunch"),
    STACK_CRUNCH(0x03, "Stack Crunch"),
    VO_DRIVE(0x0D, "VO Drive"),
    BG_DRIVE(0x11, "BG Drive"),
    MATCH_DRIVE(0x0F, "Match Drive"),
    POWER_DRIVE(0x05, "Power Drive"),
    VO_LEAD(0x0E, "VO Lead"),

    /**
     * ⚠️ Only `midi.xml` lists this one. Both TuxKatana sources skip `0x10` entirely, so if
     * one amp model in the list turns out not to exist on the Mk2, this is the first suspect.
     */
    BG_LEAD(0x10, "BG Lead"),
    EXTREME_LEAD(0x06, "Extreme Lead"),
    T_AMP_LEAD(0x16, "T-Amp Lead"),
    MS_1959_I(0x12, "MS-1959 I"),
    MS_1959_I_II(0x13, "MS-1959 I+II"),
    HIGAIN_STACK(0x04, "HiGain Stack"),
    R_FIER_VINTAGE(0x14, "R-Fier Vintage"),
    R_FIER_MODERN(0x15, "R-Fier Modern"),
    CORE_METAL(0x07, "Core Metal");

    /**
     * The base / variation pair this type belongs to, or null for the individual models.
     *
     * Only the ten types that map onto the five knob positions have a variation: the rest —
     * the "sneaky amps" — are selected outright and the VARIATION button does not apply to
     * them. ⚠️ What the amp reports at [KatanaAddresses.AMP_VARIATION] while one of those is
     * active is **unknown**, never observed.
     */
    val category: AmpCategory?
        get() = AmpCategory.entries.firstOrNull { it.baseValue == value || it.variationValue == value }

    /** Whether this is the "Var [...]" half of a pair. */
    val isVariation: Boolean
        get() = AmpCategory.entries.any { it.variationValue == value }

    companion object {
        /** The raw byte values, for [KatanaAddresses] and for building a control. */
        val VALUES: List<Int> = entries.map { it.value }

        /** The type with this raw value, or null if the amp reports something unlisted. */
        fun fromValue(value: Int): AmpType? = entries.firstOrNull { it.value == value }
    }
}

/**
 * The five positions of the physical AMP TYPE knob, at
 * [KatanaAddresses.AMP_TYPE_PANEL] (`60 00 06 50`).
 *
 * **This is a different value space from [AmpType]**, and confusing the two is the easiest
 * mistake to make here: the panel knob is `00`..`04` (five categories), while `60 00 00 21`
 * takes the full model ids (`0x00`..`0x20`, non-contiguous). `amplifier.yaml:3` names the
 * panel one `am_num` — a *number*, not a type.
 *
 * Source: `reference/FxFloorboard/midi.xml:44062-44068`, the `<DATA value="50" name="Panel:
 * 944" abbr="knob" desc="Amp" customdesc="Type">` block, corroborated by
 * `reference/TuxKatana/doc/Adresses.txt:35` (`60 00 06 50 -> [00|01|02|03|04]`) and by
 * `reference/katana-midi-bridge/parameters/amplifier.json:44-48`.
 *
 * ⚠️ Sin confirmar contra el amplificador.
 */
enum class AmpCategory(
    val value: Int,
    val displayName: String,
    /** What [KatanaAddresses.AMP_TYPE_FULL] takes for this category with variation **off**. */
    val baseValue: Int,
    /** …and with variation **on**. */
    val variationValue: Int,
) {
    ACOUSTIC(0x00, "Acoustic", baseValue = 0x01, variationValue = 0x1C),
    CLEAN(0x01, "Clean", baseValue = 0x08, variationValue = 0x1D),
    CRUNCH(0x02, "Crunch", baseValue = 0x0B, variationValue = 0x1E),
    LEAD(0x03, "Lead", baseValue = 0x18, variationValue = 0x1F),
    BROWN(0x04, "Brown", baseValue = 0x17, variationValue = 0x20);

    /**
     * The value to write to [KatanaAddresses.AMP_TYPE_FULL] to put this category in or out of
     * variation.
     *
     * This is how the app changes the variation at all: writing to
     * [KatanaAddresses.AMP_VARIATION] does nothing (confirmed 2026-09-03), but the pairs are
     * reachable through the model list, which does work.
     */
    fun typeValue(variation: Boolean): Int = if (variation) variationValue else baseValue

    companion object {
        val VALUES: List<Int> = entries.map { it.value }
        fun fromValue(value: Int): AmpCategory? = entries.firstOrNull { it.value == value }
    }
}

/**
 * **El mapeo bidireccional canal base ↔ `Var [canal]`, en un solo sitio y probado** (QA
 * 2026-09-09, bloque A.1).
 *
 * Existe porque la misma pregunta —"¿en qué canal estoy y está la variación puesta?"— se estaba
 * respondiendo de **tres formas distintas** repartidas por la UI, y las tres discrepaban:
 *
 * | Quién | Qué miraba | Consecuencia |
 * | --- | --- | --- |
 * | `AmpSection`, para pintar el switch | [KatanaAddresses.AMP_VARIATION] (`06 5C`) | el LED, que **es de solo lectura** |
 * | `ampVariationApplies`, para habilitarlo | el modelo (`00 21`) | se apagaba con cualquier "sneaky amp" activo |
 * | `onAmpVariationChanged`, para escribir | la perilla de panel (`06 50`) | podía escribir el canal de otra categoría |
 *
 * ⚠️ **De ahí salían los dos síntomas del reporte de QA**, y conviene entender el mecanismo
 * porque los dos parecían "el switch está roto" y no lo estaban:
 *
 *  1. **"En Acoustic/Clean/Lead/Brown el switch sale bloqueado."** No era de esos cuatro canales:
 *     era de que el modelo activo fuera un *sneaky amp* (`Pro Crunch`, `VO Lead`, …), que no
 *     tiene gemelo `Var [...]` y dejaba [AmpType.category] en null. Crunch "funcionaba" solo
 *     porque ahí el modelo era el `[CRUNCH]` pelado. Lo arregla [categoryOf], que cae a la
 *     **perilla del panel** cuando el modelo no basta: la perilla siempre está en una de las
 *     cinco posiciones, así que la variación siempre tiene a qué referirse.
 *  2. **"Una vez activada, no hay forma de volver al modelo base."** El camino de apagado
 *     existía; lo que no llegaba era el gesto. El switch se pintaba desde `06 5C`, que solo
 *     reporta el **botón físico**: al cambiar la variación escribiendo el modelo, ese LED no
 *     tiene por qué moverse, así que el switch se quedaba visualmente en OFF y el siguiente
 *     toque volvía a mandar `onCheckedChange(true)` — otra vez encender. Lo arregla [isOn],
 *     que deriva el estado **del modelo**, que es justo lo que la app acaba de escribir.
 *
 * ✅ **La tabla de los diez valores no cambió y no hacía falta**: `midi.xml:37311-37341` da los
 * cinco `[CANAL]` y los cinco `Var [Canal]` exactamente como ya estaban en [AmpCategory]. Lo que
 * estaba mal era quién preguntaba qué, no el dato.
 */
object AmpVariation {

    /**
     * En qué canal está el amplificador, para lo que a la variación respecta.
     *
     * Mira **primero el modelo** y solo si ese no lo dice cae a la perilla del panel:
     *
     *  - Con `[CRUNCH]` o `Var [Crunch]` activo, el modelo ya identifica el canal sin ambigüedad.
     *  - Con un *sneaky amp* activo (`Pro Crunch`, `MS-1959 I`, …) el modelo no pertenece a
     *    ninguna pareja, y entonces manda [KatanaAddresses.AMP_TYPE_PANEL] (`06 50`), que
     *    reporta la posición física de la perilla — confirmada contra el amplificador como parte
     *    del bloque `06 5x`, el que acertó once de once (CLAUDE.md §5).
     *
     * ⚠️ **El orden importa y es deliberado.** Al revés —panel primero— la app perdería el canal
     * correcto justo cuando el modelo sí lo sabe, porque `06 50` no tiene por qué haberse leído
     * todavía en el momento en que el dump ya trajo `00 21`.
     *
     * @return null solo si **ninguna** de las dos direcciones se ha leído aún.
     */
    fun categoryOf(model: Int?, panelCategory: Int?): AmpCategory? =
        model?.let(AmpType::fromValue)?.category
            ?: panelCategory?.let(AmpCategory::fromValue)

    /**
     * Si la variación está puesta ahora mismo, **leído del modelo** y no del LED `06 5C`.
     *
     * @return true con uno de los cinco `Var [...]`, false con uno de los cinco canales base, y
     *   **null cuando el modelo no es ninguno de los diez** — un *sneaky amp*, o nada leído aún.
     *   Null no es "apagado": es "esta pregunta no tiene respuesta con este modelo", y quien
     *   pinta el switch decide qué enseñar (hoy, apagado; ver [AmpVariationUi]).
     */
    fun isOn(model: Int?): Boolean? =
        model?.let(AmpType::fromValue)?.takeIf { it.category != null }?.isVariation

    /**
     * **El modelo que hay que escribir en [KatanaAddresses.AMP_TYPE_FULL]** para poner la
     * variación en [on], o null si no se sabe en qué canal está el amplificador.
     *
     * Es el camino completo en las dos direcciones —encender escribe el `Var [...]`, apagar
     * escribe el canal base—, que es la mitad que faltaba: antes solo existía el de encender.
     *
     * ⚠️ **Con un sneaky amp activo esto lo sustituye por el canal de su perilla**, y eso es un
     * cambio de sonido real: pasar de `Pro Crunch` a `[CRUNCH]` o a `Var [Crunch]`. Es lo mismo
     * que hace el botón VARIATION del panel —la variación es una propiedad de la posición de la
     * perilla, no del modelo suelto— pero conviene que esté escrito: la alternativa era dejar el
     * switch muerto en cuanto hubiera un sneaky amp, que es justo el bug que se está arreglando.
     */
    fun modelFor(model: Int?, panelCategory: Int?, on: Boolean): Int? =
        categoryOf(model, panelCategory)?.typeValue(on)
}

/**
 * Lo que la UI necesita saber del switch de variación, resuelto de una vez.
 *
 * Un solo tipo en vez de dos `StateFlow` sueltos porque las dos preguntas —"¿se puede tocar?" y
 * "¿está encendido?"— salen del mismo par de direcciones y separarlas fue precisamente lo que
 * dejó que se contestaran con fuentes distintas.
 */
data class AmpVariationUi(
    /** Si el switch tiene a qué referirse: hay canal conocido, por modelo o por perilla. */
    val applies: Boolean,
    /** Si se pinta encendido. Un modelo sin pareja se enseña apagado, no indeterminado. */
    val on: Boolean,
) {
    companion object {
        fun of(model: Int?, panelCategory: Int?): AmpVariationUi = AmpVariationUi(
            applies = AmpVariation.categoryOf(model, panelCategory) != null,
            on = AmpVariation.isOn(model) == true,
        )
    }
}

/**
 * The green / red / yellow bank each effect keeps, selected by the button under its knob.
 *
 * `midi.xml` calls these addresses "GRY color select" — GRY spelling out the three colours —
 * which is as explicit as the reference material ever gets. Three Mk2 sources agree on both
 * the addresses and on `00|01|02`:
 *  - `reference/FxFloorboard/midi.xml:3959-3963` (`Booster/MOD/FX/Delay1/Rev-Delay2 GRY
 *    color select`),
 *  - `reference/TuxKatana/doc/Adresses.txt:70, 83, 98, 112, 123` (`-> [00|01|02]`, and line
 *    83 annotates it `# [green|red|yellow]`),
 *  - los YAML de `reference/TuxKatana/params/` (`bo_bank_sel`, `mo_bank_sel`, `fx_bank_sel`,
 *    `de_bank_sel`, `re_bank_sel`).
 *
 * `reference/katana-midi-bridge/parameters/color_assign.json:2-6` defines the same
 * `colorEnum` `[0, 1, 2] = green, red, yellow`, but for the **MK1** address family — see
 * [KatanaAddresses.EFFECT_COLOR_MK1].
 *
 * ⚠️ Sin confirmar contra el amplificador.
 */
enum class EffectColor(val value: Int) {
    GREEN(0x00),
    RED(0x01),
    YELLOW(0x02);

    companion object {
        val VALUES: List<Int> = entries.map { it.value }
        fun fromValue(value: Int): EffectColor? = entries.firstOrNull { it.value == value }
    }
}
