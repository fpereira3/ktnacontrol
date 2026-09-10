package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reparto de los treinta modelos entre las dos páginas del selector (QA 2026-09-09, bloque C).
 *
 * Es lo único del rediseño visual que se puede comprobar sin dispositivo, y es justo la parte
 * que se puede equivocar en silencio: un modelo que no cae en ninguna página **no daría error**,
 * simplemente no se podría elegir nunca.
 */
class AmpModelPageTest {

    @Test
    fun `cada uno de los treinta modelos cae en exactamente una pagina`() {
        assertEquals("la tabla de midi.xml tiene 30 modelos", 30, AmpType.entries.size)

        val enAlguna = AmpType.entries.map { AmpModelPage.of(it) }
        assertEquals(30, enAlguna.size)

        // Y la suma de lo que ofrece cada página más los cinco `Var` cubre los treinta, sin
        // repetir. Los `Var` no se ofrecen sueltos: son el switch, no un chip.
        val ofrecidos = AmpModelPage.BASE_MODELS + AmpModelPage.SNEAKY_MODELS
        val cubiertos = ofrecidos + AmpModelPage.VARIATION_MODELS
        assertEquals("sin repetidos", cubiertos.size, cubiertos.toSet().size)
        assertEquals("los treinta cubiertos", AmpType.entries.toSet(), cubiertos.toSet())
    }

    @Test
    fun `la pagina AMP TYPE son los cinco canales de la perilla`() {
        assertEquals(5, AmpModelPage.BASE_MODELS.size)
        assertEquals(
            listOf("Acoustic", "Clean", "Crunch", "Lead", "Brown"),
            AmpModelPage.BASE_MODELS.map { it.displayName },
        )
        // Y son exactamente los `baseValue` de las cinco categorías, no una lista aparte.
        assertEquals(
            AmpCategory.entries.map { it.baseValue },
            AmpModelPage.BASE_MODELS.map { it.value },
        )
    }

    @Test
    fun `la pagina SNEAKY AMPS son los veinte restantes`() {
        assertEquals(20, AmpModelPage.SNEAKY_MODELS.size)
        // Ninguno de los veinte tiene pareja: por eso ahí no hay switch de variación.
        AmpModelPage.SNEAKY_MODELS.forEach { modelo ->
            assertNull("${modelo.displayName} no debería tener pareja", modelo.category)
            assertEquals(AmpModelPage.SNEAKY_AMPS, AmpModelPage.of(modelo))
        }
        // Una muestra reconocible, para que el test diga algo si la tabla se reordena.
        val nombres = AmpModelPage.SNEAKY_MODELS.map { it.displayName }
        assertTrue(nombres.contains("Natural Clean"))
        assertTrue(nombres.contains("Pro Crunch"))
        assertTrue(nombres.contains("MS-1959 I+II"))
        assertTrue(nombres.contains("Core Metal"))
        assertTrue("BG Lead solo lo da midi.xml, y tiene que estar", nombres.contains("BG Lead"))
    }

    @Test
    fun `los cinco Var no se ofrecen como opcion suelta en ninguna pagina`() {
        val ofrecidos = (
            AmpModelPage.optionsOf(AmpModelPage.AMP_TYPE) +
                AmpModelPage.optionsOf(AmpModelPage.SNEAKY_AMPS)
            ).map { it.first }

        AmpModelPage.VARIATION_MODELS.forEach { variacion ->
            assertTrue(
                "${variacion.displayName} no debería ser un chip: es el switch",
                variacion.value !in ofrecidos,
            )
        }
        assertEquals("cinco base + veinte sneaky", 25, ofrecidos.size)
    }

    @Test
    fun `un Var manda a la pagina AMP TYPE y marca su canal base`() {
        AmpCategory.entries.forEach { categoria ->
            assertEquals(
                AmpModelPage.AMP_TYPE,
                AmpModelPage.pageFor(categoria.variationValue),
            )
            assertEquals(
                "con Var puesto se marca el chip del canal base",
                categoria.baseValue,
                AmpModelPage.selectedBaseModel(categoria.variationValue),
            )
            assertEquals(
                categoria.baseValue,
                AmpModelPage.selectedBaseModel(categoria.baseValue),
            )
        }
    }

    @Test
    fun `un sneaky manda a su pagina y no marca ningun chip de la primera`() {
        assertEquals(
            AmpModelPage.SNEAKY_AMPS,
            AmpModelPage.pageFor(AmpType.PRO_CRUNCH.value),
        )
        assertNull(AmpModelPage.selectedBaseModel(AmpType.PRO_CRUNCH.value))
    }

    @Test
    fun `el switch de variacion solo se pinta en la primera pagina`() {
        assertTrue(AmpModelPage.showsVariationSwitch(AmpModelPage.AMP_TYPE))
        assertTrue(!AmpModelPage.showsVariationSwitch(AmpModelPage.SNEAKY_AMPS))
    }

    @Test
    fun `sin modelo leido se arranca en AMP TYPE`() {
        // El caso de arranque: mejor la página de los cinco canales que una lista de veinte.
        assertEquals(AmpModelPage.AMP_TYPE, AmpModelPage.pageFor(null))
        assertNull(AmpModelPage.ofValue(null))
        assertNull(AmpModelPage.selectedBaseModel(null))
    }

    @Test
    fun `un valor fuera de la tabla no rompe nada`() {
        assertNull(AmpModelPage.ofValue(0x7F))
        assertEquals(AmpModelPage.AMP_TYPE, AmpModelPage.pageFor(0x7F))
        assertNull(AmpModelPage.selectedBaseModel(0x7F))
    }

    @Test
    fun `las dos paginas no comparten ni una opcion`() {
        val primera = AmpModelPage.optionsOf(AmpModelPage.AMP_TYPE).map { it.first }.toSet()
        val segunda = AmpModelPage.optionsOf(AmpModelPage.SNEAKY_AMPS).map { it.first }.toSet()
        assertEquals(emptySet<Int>(), primera intersect segunda)
    }
}
