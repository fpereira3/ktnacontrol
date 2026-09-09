package dev.alonx3.ktnacontrol.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reparto de controles entre pantallas de dominio (CLAUDE.md §4.2).
 *
 * ⚠️ **Lo que estos tests protegen no es el aspecto de la pantalla, es un olvido silencioso**:
 * cablear un parámetro nuevo y no pintarlo en ninguna pantalla no falla por ningún lado — el
 * control existe, se puebla desde el dump y no se ve. Con el reparto escrito como dato, olvidarse
 * rompe un test.
 *
 * ⚠️ **Lo que NO se puede probar aquí**: que la pantalla se vea bien, que quepa, y que los
 * controles estén donde el ojo los espera. Eso necesita dispositivo; para eso están los
 * `@Preview` y la prueba a mano del BACKLOG.
 */
class AmpDomainTest {

    @Test
    fun `todo selector tiene dominio, y solo uno`() {
        SelectorId.entries.forEach { id ->
            val homes = listOf(
                id in AmpDomain.SELECTORS,
                id in AmpDomain.EFFECT_SELECTORS,
                id in AmpDomain.RETIRED_SELECTORS,
            ).count { it }
            assertEquals(
                "$id debería estar clasificado exactamente una vez (está $homes)",
                1,
                homes,
            )
            // Y `domainOf` no debería lanzar para ninguno.
            AmpDomain.domainOf(id)
        }
    }

    @Test
    fun `los niveles se reparten entre amplificador y efectos sin solaparse`() {
        assertEquals(
            "juntos deberían dar los once niveles del panel",
            LevelId.entries.toSet(),
            (AmpDomain.LEVELS + AmpDomain.EFFECT_LEVELS).toSet(),
        )
        assertTrue(
            "ningún nivel puede ser de las dos cosas",
            AmpDomain.LEVELS.none { it in AmpDomain.EFFECT_LEVELS },
        )
        assertEquals(LevelId.entries.size, AmpDomain.LEVELS.size + AmpDomain.EFFECT_LEVELS.size)
    }

    @Test
    fun `los seis niveles del amplificador son los del panel sin los cinco efectos`() {
        assertEquals(
            listOf(
                LevelId.GAIN,
                LevelId.VOLUME,
                LevelId.BASS,
                LevelId.MIDDLE,
                LevelId.TREBLE,
                LevelId.PRESENCE,
            ),
            AmpDomain.LEVELS,
        )
        assertEquals(ControlDomain.AMP, AmpDomain.domainOf(LevelId.GAIN))
        assertEquals(ControlDomain.EFFECT, AmpDomain.domainOf(LevelId.REVERB))
    }

    @Test
    fun `la pantalla de amplificador no reclama ningun control de efecto`() {
        EffectId.entries.forEach { effect ->
            assertTrue(
                "el nivel de $effect es de su tarjeta, no de la pantalla de amp",
                effect.level !in AmpDomain.LEVELS,
            )
        }
        // Los cortes de frecuencia de Delay y Reverb son internos de esos efectos, aunque estén
        // en el mismo enum que los del amplificador.
        listOf(
            SelectorId.DELAY_HIGH_CUT,
            SelectorId.REVERB_LOW_CUT,
            SelectorId.REVERB_HIGH_CUT,
        ).forEach { id ->
            assertEquals(ControlDomain.EFFECT, AmpDomain.domainOf(id))
        }
    }

    @Test
    fun `la cadena y las posiciones de EQ son del amplificador`() {
        // Decisión documentada (CLAUDE.md §4.2): la cadena no espera a una pantalla propia.
        listOf(
            SelectorId.CHAIN_TYPE,
            SelectorId.LOOP_POSITION,
            SelectorId.PEDAL_FX_POSITION,
            SelectorId.EQ1_POSITION,
            SelectorId.EQ2_POSITION,
        ).forEach { id ->
            assertEquals(ControlDomain.AMP, AmpDomain.domainOf(id))
        }
    }

    @Test
    fun `Bright y Gain SW estan retirados, no sin clasificar`() {
        assertEquals(ControlDomain.RETIRED, AmpDomain.domainOf(SelectorId.AMP_BRIGHT))
        assertEquals(ControlDomain.RETIRED, AmpDomain.domainOf(SelectorId.AMP_GAIN_SW))
    }

    @Test
    fun `los tres parametros sin perilla fisica son del amplificador`() {
        assertEquals(NoPanelParamId.entries.size, AmpDomain.NO_PANEL_PARAMS.size)
    }
}
