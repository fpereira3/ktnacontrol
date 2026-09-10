package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El bloque SOLO EQ, `60 00 0F 10`–`0F 19` (QA 2026-09-09, bloque B.1).
 *
 * Mismos tests de rango y parseo que el resto del EQ del proyecto ([EqParams]): que las
 * direcciones son las que dicen las dos vías de `midi.xml`, que los catálogos corren sin huecos,
 * y que la escala de ganancia es la fraccionaria de 0,5 dB y no la entera del EQ paramétrico.
 *
 * ⚠️ **Nada de esto está probado contra el amplificador.** Lo que se comprueba aquí es que la
 * transcripción de la fuente es fiel y coherente, no que el amplificador responda.
 */
class SoloEqParamsTest {

    @Test
    fun `son diez parametros en diez direcciones consecutivas desde 60 00 0F 10`() {
        assertEquals(10, SoloEqParams.PARAMS.size)
        assertEquals(Address(0x60, 0x00, 0x0F, 0x10), SoloEqParams.BASE)
        SoloEqParams.PARAMS.forEachIndexed { index, spec ->
            assertEquals(
                "${spec.label} debería estar en la posición $index del bloque",
                SoloEqParams.BASE + index,
                spec.address,
            )
        }
    }

    @Test
    fun `las etiquetas y el orden son los de midi_xml`() {
        // El orden importa: es el que hace que la dirección se pueda deducir de la posición.
        assertEquals(
            listOf(
                "Position", "Off/On", "Low Cut", "Low Gain", "Mid Freq",
                "Mid Q", "Mid Gain", "Hi Gain", "Hi Cut", "Level",
            ),
            SoloEqParams.PARAMS.map { it.label },
        )
    }

    @Test
    fun `el bloque entero cabe en Patch_Mk2V2, que es de 22 bytes desde 0F 10`() {
        // La corroboración estructural: `presets_addrs.yaml` da `UserPatch%Patch_Mk2V2` en
        // `60 00 0F 10` con 22 bytes, y aquí caben el SOLO EQ (10) y el SOLO DELAY (12).
        val patchMk2V2Base = Address(0x60, 0x00, 0x0F, 0x10)
        assertEquals(patchMk2V2Base, SoloEqParams.BASE)
        assertEquals(10, SoloEqParams.SOLO_DELAY_FIRST - patchMk2V2Base)
        assertEquals(22, (SoloEqParams.SOLO_DELAY_LAST - patchMk2V2Base) + 1)
    }

    @Test
    fun `las cuatro ganancias y el Level usan la escala fraccionaria de medio dB`() {
        val fraccionarios = SoloEqParams.PARAMS.filter { it.kind is ParamKind.Fractional }
        assertEquals(
            listOf("Low Gain", "Mid Gain", "Hi Gain", "Level"),
            fraccionarios.map { it.label },
        )
        fraccionarios.forEach { spec ->
            val kind = spec.kind as ParamKind.Fractional
            assertEquals("${spec.label}: crudo", 0x00..0x30, kind.rawRange)
            assertEquals("${spec.label}: paso", 0.5, kind.step, 0.0)
            assertEquals("${spec.label}: mínimo", -12.0, kind.displayRange.start, 0.0)
            assertEquals("${spec.label}: máximo", 12.0, kind.displayRange.endInclusive, 0.0)
        }
    }

    @Test
    fun `la ganancia convierte crudo a dB y vuelve sin desviarse`() {
        val kind = SoloEqParams.GAIN_KIND
        assertEquals(-12.0, kind.rawToDisplay(0x00), 0.0)
        assertEquals(0.0, kind.rawToDisplay(0x18), 0.0)
        assertEquals(12.0, kind.rawToDisplay(0x30), 0.0)
        // Ida y vuelta en los 49 valores crudos: ninguno se mueve.
        (0x00..0x30).forEach { raw ->
            assertEquals("crudo $raw", raw, kind.displayToRaw(kind.rawToDisplay(raw)))
        }
    }

    @Test
    fun `es la escala del EQ grafico, no la del parametrico`() {
        // ⚠️ La distinción que este bloque comparte con EQ1/EQ2: el gráfico es 0,5 dB sobre
        // `00/30`, el paramétrico es 1 dB sobre `00/28`. Confundirlas da el doble de recorrido
        // con la mitad de resolución.
        val kind = SoloEqParams.GAIN_KIND
        assertEquals(49, kind.rawRange.count())
        assertTrue("no es la escala entera de ±20 dB", kind.displayRange.endInclusive != 20.0)
    }

    @Test
    fun `los cuatro selectores corren sin huecos`() {
        val selectores = SoloEqParams.PARAMS.mapNotNull { it.kind as? ParamKind.Enum }
        assertEquals(6, selectores.size) // Position, Off/On, Low Cut, Mid Freq, Mid Q, Hi Cut
        selectores.forEach { kind ->
            assertEquals(
                "cada valor debe tener su etiqueta",
                kind.values.size,
                kind.labels.size,
            )
            assertEquals(
                "y la lista debe correr de 0 a N-1 sin saltarse nada",
                kind.values.indices.toList(),
                kind.values,
            )
        }
    }

    @Test
    fun `los catalogos tienen el tamano que dice midi_xml`() {
        assertEquals(18, SoloEqParams.LOW_CUT_LABELS.size)
        assertEquals(28, SoloEqParams.MID_FREQ_LABELS.size)
        assertEquals(6, SoloEqParams.Q_LABELS.size)
        assertEquals(15, SoloEqParams.HIGH_CUT_LABELS.size)
        assertEquals(2, SoloEqParams.POSITION_LABELS.size)
    }

    @Test
    fun `los catalogos coinciden con los del EQ del preset`() {
        // Son los mismos y se declaran por separado a propósito (ver el KDoc): este test es lo
        // que hace que una divergencia futura se note en vez de pasar callada.
        assertEquals(EqParams.LOW_CUT_LABELS, SoloEqParams.LOW_CUT_LABELS)
        assertEquals(EqParams.MID_FREQ_LABELS, SoloEqParams.MID_FREQ_LABELS)
        assertEquals(EqParams.Q_LABELS, SoloEqParams.Q_LABELS)
        assertEquals(EqParams.HIGH_CUT_LABELS, SoloEqParams.HIGH_CUT_LABELS)
    }

    @Test
    fun `en 0x0A dice 6_00k, que es el cuarto testigo contra el 6_30K del delay`() {
        assertEquals("6.00k", SoloEqParams.HIGH_CUT_LABELS[0x0A])
        assertEquals("6.00k", EqParams.HIGH_CUT_LABELS[0x0A])
        assertEquals("6.00k", ReverbHighCutFrequency.entries[0x0A].displayName)
        // El único que discrepa, y sigue discrepando: no se toca sin prueba de hardware.
        assertEquals("6.30K", DelayHighCutFrequency.entries[0x0A].displayName)
    }

    @Test
    fun `el interruptor propio del bloque no se confunde con los otros dos Solo`() {
        // Tres cosas distintas que se llaman "Solo" en este amplificador.
        assertEquals(Address(0x60, 0x00, 0x0F, 0x11), SoloEqParams.OFF_ON)
        assertTrue(SoloEqParams.OFF_ON != KatanaAddresses.BOOST_SOLO_ENABLED)
        assertTrue(SoloEqParams.OFF_ON != KatanaAddresses.AMP_SOLO_ENABLED)
        assertTrue(SoloEqParams.OFF_ON != KatanaAddresses.AMP_SOLO_ENABLED_PANEL)
    }

    @Test
    fun `el bloque cae fuera del dump y esta marcado como tal`() {
        // ⚠️ Es lo que impide registrarlo como control hoy: serían diez GET de respaldo en
        // serie más, sobre los seis que ya hay. Ver el KDoc de `OUTSIDE_DUMP`.
        assertTrue(SoloEqParams.OUTSIDE_DUMP)
        val finDelDump = KatanaAddresses.MEMORY_DUMP + KatanaAddresses.MEMORY_DUMP_SIZE
        SoloEqParams.PARAMS.forEach { spec ->
            assertTrue(
                "${spec.label} debería estar fuera del rango del dump",
                spec.address.value >= finDelDump.value,
            )
        }
    }
}
