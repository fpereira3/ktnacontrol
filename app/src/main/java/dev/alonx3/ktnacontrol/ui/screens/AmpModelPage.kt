package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType

/**
 * **En qué página del selector de modelo cae cada uno de los treinta amplificadores**
 * (QA 2026-09-09, bloque C).
 *
 * El contenedor de AMP TYPE pasa a tener dos páginas deslizables, y esto decide qué va en cada
 * una. **Kotlin puro, sin Compose y con tests JVM**, por el mismo criterio que [AmpDomain] y
 * [ShellState]: el reparto de controles entre pantallas es precisamente lo que se puede olvidar
 * al añadir uno nuevo, y quedaría invisible sin que nada fallara.
 *
 * El corte no es una lista escrita a mano: sale de [AmpType.category], que ya distingue los diez
 * modelos emparejados de los otros veinte. Así, si algún día apareciera un modelo nuevo, cae solo
 * del lado correcto en vez de quedarse fuera de las dos páginas.
 */
enum class AmpModelPage {
    /**
     * Los **cinco canales base** —los que tiene la perilla física— más el switch de variación.
     *
     * ⚠️ Los cinco `Var [...]` **no** se listan aquí como opciones sueltas: son el mismo canal
     * con el switch puesto, y ofrecerlos por separado sería enseñar diez botones para cinco
     * canales y dejar el switch diciendo lo mismo por segunda vez.
     */
    AMP_TYPE,

    /**
     * Los **veinte modelos individuales**, lo que la comunidad llama *sneaky amps*.
     *
     * ⚠️ Aquí el switch de variación **no existe** —ni gris ni oculto—: ninguno de estos veinte
     * tiene gemelo `Var [...]`, así que un interruptor apagado para siempre solo invitaría a
     * preguntarse qué le pasa.
     */
    SNEAKY_AMPS,
    ;

    companion object {

        /**
         * Los cinco canales base, en el orden de la perilla física.
         *
         * Se derivan de [AmpCategory] y no de una lista propia: la perilla y esta página tienen
         * que decir lo mismo, y con dos listas eso dura hasta el primer despiste.
         */
        val BASE_MODELS: List<AmpType> =
            AmpCategory.entries.mapNotNull { AmpType.fromValue(it.baseValue) }

        /** Los cinco `Var [...]`, que no se ofrecen sueltos pero sí hay que saber cuáles son. */
        val VARIATION_MODELS: List<AmpType> =
            AmpCategory.entries.mapNotNull { AmpType.fromValue(it.variationValue) }

        /**
         * Los veinte individuales: **todo lo que no es ninguno de los cinco base ni de los cinco
         * `Var`**, en el orden en que `midi.xml` los lista.
         *
         * Es una resta, igual que `AmpDomain.LEVELS`: así un modelo nuevo aparece aquí solo, en
         * vez de perderse por no acordarse de añadirlo.
         */
        val SNEAKY_MODELS: List<AmpType> = AmpType.entries.filter { it.category == null }

        /** La página a la que pertenece un modelo. */
        fun of(type: AmpType): AmpModelPage =
            if (type.category == null) SNEAKY_AMPS else AMP_TYPE

        /** La página a la que pertenece un valor crudo, o null si no está en la tabla. */
        fun ofValue(value: Int?): AmpModelPage? = value?.let(AmpType::fromValue)?.let(::of)

        /**
         * Qué opciones ofrece cada página, ya como `valor → etiqueta`.
         *
         * [AMP_TYPE] ofrece **los cinco base**, no los diez de la pareja: el `Var` se elige con
         * el switch, que es como funciona el panel del amplificador.
         */
        fun optionsOf(page: AmpModelPage): List<Pair<Int, String>> = when (page) {
            AMP_TYPE -> BASE_MODELS.map { it.value to it.displayName }
            SNEAKY_AMPS -> SNEAKY_MODELS.map { it.value to it.displayName }
        }

        /**
         * En qué página hay que estar para que el modelo activo se vea seleccionado.
         *
         * ⚠️ **Un `Var [...]` manda a [AMP_TYPE]**, no a ninguna otra parte: es uno de los cinco
         * canales con el switch puesto, y su chip seleccionado es el del canal base.
         */
        fun pageFor(model: Int?): AmpModelPage = ofValue(model) ?: AMP_TYPE

        /**
         * Qué chip aparece marcado en la página [AMP_TYPE] con este modelo activo.
         *
         * Con `Var [Crunch]` activo hay que marcar **Crunch**, porque el `Var` no es una opción
         * de la lista sino el estado del switch de al lado.
         */
        fun selectedBaseModel(model: Int?): Int? =
            model?.let(AmpType::fromValue)?.category?.baseValue

        /** Si el switch de variación se pinta en esta página. Solo en [AMP_TYPE]. */
        fun showsVariationSwitch(page: AmpModelPage): Boolean = page == AMP_TYPE
    }
}
