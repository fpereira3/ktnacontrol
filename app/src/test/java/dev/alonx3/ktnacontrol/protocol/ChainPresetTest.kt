package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Las siete cadenas predefinidas contra el oráculo de Boss Tone Studio** (QA 2026-09-09,
 * bloque A.2).
 *
 * Las siete secuencias de abajo están **transcritas del propio Boss Tone Studio** por el usuario
 * en el reporte de QA, y son el criterio de aceptación: si [ChainBlock.diagramSequence] no da
 * exactamente esto para el valor crudo de cada cadena, el diagrama miente.
 *
 * Es la primera vez que este diagrama tiene un oráculo externo. Hasta ahora se comprobaba que el
 * filtro no reordenara y que el fichero de referencia diera "algo con sentido musical" — útil,
 * pero incapaz de detectar que faltara o sobrara un bloque, que es precisamente lo que pasaba
 * (sobraba `FXLOOP`).
 */
class ChainPresetTest {

    private val IN_ = ChainDiagramBlock.INPUT
    private val SPK = ChainDiagramBlock.SPEAKER

    private val B = ChainDiagramBlock.BOOSTER
    private val M = ChainDiagramBlock.MOD
    private val F = ChainDiagramBlock.FX
    private val A = ChainDiagramBlock.AMP
    private val D1 = ChainDiagramBlock.DELAY
    private val D2 = ChainDiagramBlock.DELAY2
    private val R = ChainDiagramBlock.REVERB

    /**
     * El oráculo, literal del reporte de QA.
     *
     * Nótese el patrón que lo hace verificable de un vistazo: **AMP es lo único que se desplaza**
     * de una fila a la siguiente, **Booster y Mod intercambian** cuál va primero entre las
     * familias `-1` y `-2`, y `DELAY → DELAY2 → REVERB` cierra siempre igual.
     */
    private val esperado: Map<ChainPreset, List<ChainDiagramBlock>> = mapOf(
        ChainPreset.CHAIN_1 to listOf(B, A, M, F, D1, D2, R),
        ChainPreset.CHAIN_2_1 to listOf(B, M, A, F, D1, D2, R),
        ChainPreset.CHAIN_3_1 to listOf(B, M, F, A, D1, D2, R),
        ChainPreset.CHAIN_4_1 to listOf(B, M, F, D1, A, D2, R),
        ChainPreset.CHAIN_2_2 to listOf(M, B, A, F, D1, D2, R),
        ChainPreset.CHAIN_3_2 to listOf(M, B, F, A, D1, D2, R),
        ChainPreset.CHAIN_4_2 to listOf(M, B, F, D1, A, D2, R),
    )

    @Test
    fun `las siete cadenas dan exactamente el diagrama de Boss Tone Studio`() {
        assertEquals("son siete y solo siete", 7, ChainPreset.entries.size)
        ChainPreset.entries.forEach { preset ->
            assertEquals(
                "${preset.displayName} (crudo ${"%02X".format(preset.value)})",
                esperado.getValue(preset),
                ChainBlock.diagramSequence(preset.rawSlots),
            )
        }
    }

    @Test
    fun `el diagrama completo se lee como lo escribe Boss, con IN y SPEAKER`() {
        // La forma en que el usuario lo transcribió, salvo que la app dice `INPUT` donde el
        // reporte dice `IN` — el mismo extremo con otra palabra, no otro bloque.
        assertEquals(
            "INPUT → BOOSTER → AMP → MOD → FX → DELAY → DELAY2 → REVERB → SPEAKER",
            (listOf(IN_) + ChainPreset.CHAIN_1.diagram.map { it.displayName } + SPK)
                .joinToString(" → "),
        )
        assertEquals(
            "INPUT → MOD → BOOSTER → FX → DELAY → AMP → DELAY2 → REVERB → SPEAKER",
            (listOf(IN_) + ChainPreset.CHAIN_4_2.diagram.map { it.displayName } + SPK)
                .joinToString(" → "),
        )
    }

    @Test
    fun `los valores crudos son 00 a 06 en el orden de los botones de Boss`() {
        // ⚠️ El orden **no** es el que sugieren los nombres: las dos familias van seguidas
        // (`2-1 3-1 4-1` y luego `2-2 3-2 4-2`), no intercaladas. Sale de
        // `floorBoard.cpp:490-599` cruzado con `floorBoardDisplay.cpp:158-177`.
        assertEquals(
            listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06),
            ChainPreset.entries.map { it.value },
        )
        assertEquals(
            listOf("CHAIN 1", "CHAIN 2-1", "CHAIN 3-1", "CHAIN 4-1", "CHAIN 2-2", "CHAIN 3-2", "CHAIN 4-2"),
            ChainPreset.entries.map { it.displayName },
        )
        assertEquals(KatanaAddresses.CHAIN_TYPE_VALUES, ChainPreset.entries.map { it.value })
    }

    @Test
    fun `cada cadena es una permutacion completa de los veinte bloques`() {
        // La comprobación que caza una transcripción mal copiada: veinte ranuras, los veinte
        // identificadores, ninguno repetido y ninguno ausente.
        ChainPreset.entries.forEach { preset ->
            assertEquals(
                "${preset.displayName} debe tener las 20 ranuras",
                ChainBlock.SLOT_COUNT,
                preset.slots.size,
            )
            assertEquals(
                "${preset.displayName} debe usar los 20 identificadores",
                ChainBlock.entries.toSet(),
                preset.slots.toSet(),
            )
        }
    }

    @Test
    fun `las siete son distintas entre si`() {
        val ordenes = ChainPreset.entries.map { it.slots }
        assertEquals("ninguna cadena repite a otra", ordenes.size, ordenes.toSet().size)
    }

    @Test
    fun `fromValue cubre los siete y rechaza lo demas`() {
        ChainPreset.entries.forEach { assertNotNull(ChainPreset.fromValue(it.value)) }
        assertNull(ChainPreset.fromValue(0x07))
        assertNull(ChainPreset.fromValue(-1))
    }

    @Test
    fun `en las siete el amplificador va despues del booster y del mod, o entre ellos`() {
        // Una invariante física del Katana que ninguna cadena rompe: el previo nunca es el
        // primer bloque, y la reverb siempre es el último antes del altavoz.
        ChainPreset.entries.forEach { preset ->
            val d = preset.diagram
            assertEquals("${preset.displayName}: la reverb cierra", ChainDiagramBlock.REVERB, d.last())
            assertEquals("${preset.displayName}: siete bloques", 7, d.size)
            assert(d.indexOf(ChainDiagramBlock.AMP) > 0) {
                "${preset.displayName}: el previo nunca es el primer bloque"
            }
        }
    }
}
