package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mapeo bidireccional canal base ↔ `Var [canal]` (QA 2026-09-09, bloque A.1).
 *
 * El reporte pedía **los diez casos**: encender y apagar la variación en cada uno de los cinco
 * canales. Están abajo, y además los dos síntomas concretos que se reportaron, escritos como
 * regresiones para que no puedan volver sin que alguien se entere.
 */
class AmpVariationTest {

    /** Los cinco canales con su pareja, tal y como los da `midi.xml:37311-37341`. */
    private val pares = listOf(
        Triple(AmpCategory.ACOUSTIC, AmpType.ACOUSTIC, AmpType.ACOUSTIC_VARIATION),
        Triple(AmpCategory.CLEAN, AmpType.CLEAN, AmpType.CLEAN_VARIATION),
        Triple(AmpCategory.CRUNCH, AmpType.CRUNCH, AmpType.CRUNCH_VARIATION),
        Triple(AmpCategory.LEAD, AmpType.LEAD, AmpType.LEAD_VARIATION),
        Triple(AmpCategory.BROWN, AmpType.BROWN, AmpType.BROWN_VARIATION),
    )

    @Test
    fun `los cinco canales tienen su pareja y ninguna se repite`() {
        assertEquals(5, pares.size)
        assertEquals(5, AmpCategory.entries.size)
        val todos = pares.flatMap { (_, base, variacion) -> listOf(base.value, variacion.value) }
        assertEquals("las diez son distintas", 10, todos.toSet().size)
    }

    // --- Los diez casos que pedía el reporte ------------------------------------------------

    @Test
    fun `encender la variacion escribe el Var del canal, en los cinco`() {
        pares.forEach { (categoria, base, variacion) ->
            assertEquals(
                "encender desde ${base.displayName}",
                variacion.value,
                AmpVariation.modelFor(model = base.value, panelCategory = categoria.value, on = true),
            )
        }
    }

    @Test
    fun `apagar la variacion escribe el canal base, en los cinco`() {
        // ⚠️ **Esta es la mitad que no existía**: antes solo se sabía encender, así que una vez
        // puesta la variación no había forma documentada de volver.
        pares.forEach { (categoria, base, variacion) ->
            assertEquals(
                "apagar desde ${variacion.displayName}",
                base.value,
                AmpVariation.modelFor(
                    model = variacion.value,
                    panelCategory = categoria.value,
                    on = false,
                ),
            )
        }
    }

    @Test
    fun `encender y apagar deja el modelo donde estaba, en los cinco`() {
        pares.forEach { (categoria, base, _) ->
            val encendido = AmpVariation.modelFor(base.value, categoria.value, on = true)
            val apagado = AmpVariation.modelFor(encendido, categoria.value, on = false)
            assertEquals("ida y vuelta desde ${base.displayName}", base.value, apagado)
        }
    }

    // --- Los dos síntomas del reporte, como regresiones --------------------------------------

    @Test
    fun `con un sneaky amp activo el switch sigue aplicando y usa la perilla del panel`() {
        // El síntoma reportado: "en Acoustic/Clean/Lead/Brown el switch sale bloqueado". No era
        // de esos canales — era de que el modelo activo fuera un sneaky amp, que no tiene pareja.
        val sneaky = listOf(
            AmpType.PRO_CRUNCH to AmpCategory.CRUNCH,
            AmpType.NATURAL_CLEAN to AmpCategory.CLEAN,
            AmpType.VO_LEAD to AmpCategory.LEAD,
            AmpType.MS_1959_I to AmpCategory.BROWN,
        )
        sneaky.forEach { (modelo, perilla) ->
            assertNull("${modelo.displayName} no tiene pareja", modelo.category)
            assertTrue(
                "${modelo.displayName} debería seguir permitiendo la variación",
                AmpVariationUi.of(modelo.value, perilla.value).applies,
            )
            assertEquals(
                "y encenderla debe dar el Var de la perilla",
                perilla.variationValue,
                AmpVariation.modelFor(modelo.value, perilla.value, on = true),
            )
        }
    }

    @Test
    fun `el estado del switch sale del modelo, no del LED`() {
        // El otro síntoma: "una vez activada una variación, no hay forma de volver al modelo
        // base". El camino de apagado existía; lo que no llegaba era el gesto, porque el switch
        // se pintaba desde `06 5C` y ese LED no se mueve al escribir el modelo.
        pares.forEach { (categoria, base, variacion) ->
            assertEquals(false, AmpVariation.isOn(base.value))
            assertEquals(true, AmpVariation.isOn(variacion.value))
            assertTrue(AmpVariationUi.of(variacion.value, categoria.value).on)
            assertTrue(!AmpVariationUi.of(base.value, categoria.value).on)
        }
    }

    @Test
    fun `un sneaky amp no dice ni si o no a la variacion, y se pinta apagado`() {
        assertNull(AmpVariation.isOn(AmpType.PRO_CRUNCH.value))
        // `null` no es "apagado", pero la UI tiene que enseñar algo: enseña apagado.
        assertTrue(!AmpVariationUi.of(AmpType.PRO_CRUNCH.value, AmpCategory.CRUNCH.value).on)
    }

    // --- Los bordes -------------------------------------------------------------------------

    @Test
    fun `el modelo manda sobre la perilla cuando los dos dicen algo`() {
        // Si el modelo es `Var [Crunch]` y la perilla dice Clean —posible durante una recarga,
        // con las dos direcciones llegando por separado—, gana el modelo, que es el que de
        // verdad describe el sonido.
        assertEquals(
            AmpCategory.CRUNCH,
            AmpVariation.categoryOf(
                model = AmpType.CRUNCH_VARIATION.value,
                panelCategory = AmpCategory.CLEAN.value,
            ),
        )
    }

    @Test
    fun `sin nada leido no se inventa un canal`() {
        assertNull(AmpVariation.categoryOf(model = null, panelCategory = null))
        assertNull(AmpVariation.modelFor(model = null, panelCategory = null, on = true))
        assertNull(AmpVariation.isOn(null))
        assertTrue(!AmpVariationUi.of(null, null).applies)
    }

    @Test
    fun `un valor que no esta en ninguna tabla no rompe nada`() {
        assertNull(AmpVariation.categoryOf(model = 0x7F, panelCategory = 0x7F))
        assertNull(AmpVariation.modelFor(model = 0x7F, panelCategory = 0x7F, on = false))
    }

    @Test
    fun `solo con la perilla leida ya se puede decidir`() {
        // El caso de arranque: el dump trae `06 50` antes que `00 21`, o el modelo es un sneaky.
        assertEquals(
            AmpType.LEAD_VARIATION.value,
            AmpVariation.modelFor(model = null, panelCategory = AmpCategory.LEAD.value, on = true),
        )
    }
}
