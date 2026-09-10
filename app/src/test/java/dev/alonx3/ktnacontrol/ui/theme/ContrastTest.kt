package dev.alonx3.ktnacontrol.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [contrastRatio] (CLAUDE.md §4.9, Fase 5).
 *
 * La primera mitad fija la fórmula contra los dos casos de referencia del propio estándar
 * (blanco/negro = 21:1 exacto, un color contra sí mismo = 1:1 exacto): si algún día se
 * reescribe el cálculo, estos dos números no negocian. La segunda mitad **pin-ea el resultado
 * real de la paleta de `Color.kt`** contra los umbrales elegidos — es lo que hace que cambiar
 * un color sin volver a mirar el contraste rompa un test en vez de colarse.
 */
class ContrastTest {

    @Test
    fun `blanco contra negro da el maximo del estandar, 21 a 1`() {
        assertEquals(21.0, contrastRatio(255, 255, 255, 0, 0, 0), 0.01)
    }

    @Test
    fun `un color contra si mismo da el minimo, 1 a 1`() {
        assertEquals(1.0, contrastRatio(0xFF7A1A, 0xFF7A1A), 0.001)
    }

    @Test
    fun `el orden de los dos colores no importa`() {
        val a = contrastRatio(0x16181B, 0xE6E8EA)
        val b = contrastRatio(0xE6E8EA, 0x16181B)
        assertEquals(a, b, 0.0001)
    }

    @Test
    fun `la sobrecarga hex ignora el byte de alfa`() {
        val conAlfa = contrastRatio(0xFF3ECF5C, 0xFF16181B)
        val sinAlfa = contrastRatio(0x3ECF5C, 0x16181B)
        assertEquals(conAlfa, sinAlfa, 0.0001)
    }

    // --- La paleta real de Color.kt, contra los umbrales que documenta CLAUDE.md §4.9 --------

    @Test
    fun `texto principal sobre superficie cumple el umbral de texto`() {
        val ratio = contrastRatio(ChassisOnSurface.hex, ChassisSurface.hex)
        assertTrue("$ratio < ${ContrastThreshold.TEXT}", ratio >= ContrastThreshold.TEXT)
    }

    @Test
    fun `texto secundario sobre superficie cumple el umbral de texto`() {
        val ratio = contrastRatio(ChassisOnSurfaceVariant.hex, ChassisSurface.hex)
        assertTrue("$ratio < ${ContrastThreshold.TEXT}", ratio >= ContrastThreshold.TEXT)
    }

    @Test
    fun `texto sobre el boton relleno del acento cumple el umbral de texto`() {
        val ratio = contrastRatio(EmberOnPrimary.hex, EmberPrimary.hex)
        assertTrue("$ratio < ${ContrastThreshold.TEXT}", ratio >= ContrastThreshold.TEXT)
    }

    @Test
    fun `texto sobre el contenedor del acento cumple el umbral de texto`() {
        val ratio = contrastRatio(EmberOnContainer.hex, EmberContainer.hex)
        assertTrue("$ratio < ${ContrastThreshold.TEXT}", ratio >= ContrastThreshold.TEXT)
    }

    @Test
    fun `texto sobre el contenedor de acero cumple el umbral de texto`() {
        val ratio = contrastRatio(SteelOnContainer.hex, SteelContainer.hex)
        assertTrue("$ratio < ${ContrastThreshold.TEXT}", ratio >= ContrastThreshold.TEXT)
    }

    @Test
    fun `encabezados de seccion en acero sobre superficie cumplen el umbral de texto`() {
        val ratio = contrastRatio(SteelSecondary.hex, ChassisSurface.hex)
        assertTrue("$ratio < ${ContrastThreshold.TEXT}", ratio >= ContrastThreshold.TEXT)
    }

    @Test
    fun `texto de aviso en rojo sobre superficie cumple el umbral de texto`() {
        val ratio = contrastRatio(WarningRed.hex, ChassisSurface.hex)
        assertTrue("$ratio < ${ContrastThreshold.TEXT}", ratio >= ContrastThreshold.TEXT)
    }

    @Test
    fun `el relleno del acento sobre la pista de slider cumple el umbral de componente`() {
        // KnobControl y VerticalBarControl pintan el valor con `colorScheme.primary` sobre
        // `colorScheme.surfaceVariant` — ver Controls.kt.
        val ratio = contrastRatio(EmberPrimary.hex, ChassisSurfaceVariant.hex)
        assertTrue(
            "$ratio < ${ContrastThreshold.NON_TEXT_COMPONENT}",
            ratio >= ContrastThreshold.NON_TEXT_COMPONENT,
        )
    }

    @Test
    fun `los tres colores de slot contra la tarjeta cumplen el umbral de componente`() {
        val background = ChassisSurface.hex
        listOf(
            "verde" to EffectSlotColors.Green.hex,
            "rojo" to EffectSlotColors.Red.hex,
            "amarillo" to EffectSlotColors.Yellow.hex,
        ).forEach { (name, slot) ->
            val ratio = contrastRatio(slot, background)
            assertTrue(
                "$name: $ratio < ${ContrastThreshold.NON_TEXT_COMPONENT}",
                ratio >= ContrastThreshold.NON_TEXT_COMPONENT,
            )
        }
    }
}
