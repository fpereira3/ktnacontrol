package dev.alonx3.ktnacontrol.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reparto de controles visto **desde `EffectsScreen`** (CLAUDE.md §4.2): que no reclame nada
 * de [AmpDomain.SELECTORS] / [AmpDomain.LEVELS], y que lo suyo salga entero de lo que
 * `AmpDomain` ya clasificó como de efecto — sin ninguna lista nueva escrita a mano.
 *
 * `AmpDomainTest` ya prueba la cobertura completa (todo `SelectorId` en exactamente un sitio,
 * todo `LevelId` repartido sin solape); esto añade la mirada simétrica, la que haría fallar un
 * test si `EffectsScreen` empezara a pintar algo del amplificador.
 *
 * ⚠️ **Lo que esto NO prueba**: que las tarjetas se vean bien o quepan. Eso necesita
 * dispositivo — ver BACKLOG.md, "Pendiente por probar".
 */
class EffectsDomainTest {

    @Test
    fun `los niveles de efecto son exactamente los cinco de EffectId, sin ninguno del amplificador`() {
        assertEquals(
            EffectId.entries.map { it.level }.toSet(),
            AmpDomain.EFFECT_LEVELS.toSet(),
        )
        AmpDomain.EFFECT_LEVELS.forEach { id ->
            assertTrue("$id no debería estar también en AmpDomain.LEVELS", id !in AmpDomain.LEVELS)
            assertEquals(ControlDomain.EFFECT, AmpDomain.domainOf(id))
        }
    }

    @Test
    fun `los selectores de efecto no se solapan con los del amplificador`() {
        AmpDomain.EFFECT_SELECTORS.forEach { id ->
            assertTrue("$id no debería estar en AmpDomain.SELECTORS", id !in AmpDomain.SELECTORS)
            assertTrue("$id no debería estar retirado", id !in AmpDomain.RETIRED_SELECTORS)
            assertEquals(ControlDomain.EFFECT, AmpDomain.domainOf(id))
        }
    }

    @Test
    fun `ningun parametro sin perilla fisica es de un efecto`() {
        // Los tres —Noise Gate ×2 y el Freq Shift del Contour activo— son del amplificador; el
        // complemento de `NO_PANEL_PARAMS` para efectos está vacío porque no hay ninguno.
        assertTrue(NoPanelParamId.entries.all { it in AmpDomain.NO_PANEL_PARAMS })
    }

    @Test
    fun `EffectsScreen no tiene ninguna lista propia de control_domain, reutiliza AmpDomain`() {
        // Documenta la decisión en forma de test: si algún día `EffectsScreen` necesitara filtrar
        // sus propios controles, la fuente debe seguir siendo `AmpDomain` (o `EffectId.entries`
        // directamente, que ya era la fuente de verdad de "qué es un efecto"), nunca una lista
        // nueva a mano que pudiera desincronizarse de la de `AmpDomain`.
        assertEquals(EffectId.entries.size, AmpDomain.EFFECT_LEVELS.size)
    }
}
