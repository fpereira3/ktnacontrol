package dev.alonx3.ktnacontrol.protocol

import dev.alonx3.ktnacontrol.protocol.tsl.TslParseResult
import dev.alonx3.ktnacontrol.protocol.tsl.TslParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El filtro de las veinte ranuras al vocabulario de diez del diagrama (2026-09-06).
 *
 * Es lo único de este cambio que se puede comprobar sin amplificador: que el mapeo es el que
 * dicen las fuentes y que el orden se conserva. Que el orden **sea el correcto** es otra
 * pregunta y necesita compararlo contra Boss Tone Studio — ver BACKLOG.md, punto 13.
 */
class ChainDiagramTest {

    @Test
    fun `los ocho bloques del vocabulario mapean a los identificadores que dicen las fuentes`() {
        // ✅ Confirmado en `FxFloorboard/summaryDialog.cpp:89-105`, que hace este mismo trabajo,
        // y `stompBox.cpp:827` para CH_A.
        assertEquals(ChainDiagramBlock.BOOSTER, ChainBlock.OD.diagramBlock)
        assertEquals(ChainDiagramBlock.MOD, ChainBlock.FX1.diagramBlock)
        assertEquals(ChainDiagramBlock.FX, ChainBlock.FX2.diagramBlock)
        assertEquals(ChainDiagramBlock.AMP, ChainBlock.CH_A.diagramBlock)
        assertEquals(ChainDiagramBlock.FXLOOP, ChainBlock.LP.diagramBlock)
        assertEquals(ChainDiagramBlock.DELAY, ChainBlock.DD1.diagramBlock)
        assertEquals(ChainDiagramBlock.DELAY2, ChainBlock.DD2.diagramBlock)
        assertEquals(ChainDiagramBlock.REVERB, ChainBlock.RV.diagramBlock)
    }

    @Test
    fun `los doce identificadores restantes no se dibujan`() {
        val omitidos = listOf(
            ChainBlock.CS, ChainBlock.CH_B, ChainBlock.EQ1, ChainBlock.EQ2,
            ChainBlock.PDL, ChainBlock.FV, ChainBlock.NS_1, ChainBlock.NS_2,
            ChainBlock.USB, ChainBlock.CN_S, ChainBlock.CAB, ChainBlock.CN_M,
        )
        omitidos.forEach { block ->
            assertNull("${block.displayName} no debería dibujarse", block.diagramBlock)
        }
        // Los 20 son 8 dibujados + 12 omitidos: si alguien añade un identificador nuevo sin
        // decidir de qué lado cae, esto lo caza.
        assertEquals(ChainBlock.SLOT_COUNT, ChainBlock.entries.size)
        assertEquals(8, ChainBlock.entries.count { it.diagramBlock != null })
        assertEquals(12, omitidos.size)
    }

    @Test
    fun `cada bloque del vocabulario lo produce exactamente un identificador`() {
        val producidos = ChainBlock.entries.mapNotNull { it.diagramBlock }
        assertEquals(
            "ningún bloque del diagrama puede salir de dos identificadores",
            producidos.size,
            producidos.toSet().size,
        )
        assertEquals(
            "y todos los del vocabulario tienen quien los produzca",
            ChainDiagramBlock.entries.toSet(),
            producidos.toSet(),
        )
    }

    /**
     * La cadena por defecto del fichero real, filtrada.
     *
     * El orden crudo es
     * `PDL → OD → FX1 → FX2 → EQ1 → EQ2 → CH_A → CS → NS_1 → FV → LP → DD1 → CN_S → DD2 → RV →
     * CAB → CH_B → NS_2 → USB → CN_M`, y lo que queda al filtrar tiene sentido musical: booster
     * antes del previo, loop y delays después, reverb al final. Que salga algo coherente es la
     * mejor señal disponible de que el mapeo no está cruzado.
     */
    @Test
    fun `la cadena del fichero de referencia filtra a una secuencia con sentido`() {
        val text = File("../reference/FxFloorboard/default_mk2.tsl").readText()
        val preset = (TslParser.parse(text) as TslParseResult.Parsed).presets.single()
        val slots = List(ChainBlock.SLOT_COUNT) { slot ->
            preset.memory.byteAt(KatanaAddresses.chainSlot(slot))
        }

        assertTrue("las 20 ranuras deberían venir en el fichero", slots.all { it != null })
        assertEquals(
            listOf(
                ChainDiagramBlock.BOOSTER,
                ChainDiagramBlock.MOD,
                ChainDiagramBlock.FX,
                ChainDiagramBlock.AMP,
                ChainDiagramBlock.FXLOOP,
                ChainDiagramBlock.DELAY,
                ChainDiagramBlock.DELAY2,
                ChainDiagramBlock.REVERB,
            ),
            ChainBlock.diagramSequence(slots),
        )
    }

    @Test
    fun `el filtro conserva el orden y no lo reordena`() {
        // Los mismos ocho al revés deben salir al revés: el diagrama refleja el amplificador,
        // no un orden canónico inventado por la app.
        val alReves = listOf(
            ChainBlock.RV, ChainBlock.DD2, ChainBlock.DD1, ChainBlock.LP,
            ChainBlock.CH_A, ChainBlock.FX2, ChainBlock.FX1, ChainBlock.OD,
        ).map { it.value as Int? }

        assertEquals(
            listOf(
                ChainDiagramBlock.REVERB,
                ChainDiagramBlock.DELAY2,
                ChainDiagramBlock.DELAY,
                ChainDiagramBlock.FXLOOP,
                ChainDiagramBlock.AMP,
                ChainDiagramBlock.FX,
                ChainDiagramBlock.MOD,
                ChainDiagramBlock.BOOSTER,
            ),
            ChainBlock.diagramSequence(alReves),
        )
    }

    @Test
    fun `una ranura sin leer se salta, no rompe la secuencia`() {
        val conHuecos = listOf(ChainBlock.OD.value, null, ChainBlock.RV.value)
        assertEquals(
            listOf(ChainDiagramBlock.BOOSTER, ChainDiagramBlock.REVERB),
            ChainBlock.diagramSequence(conHuecos),
        )
        assertEquals(emptyList<ChainDiagramBlock>(), ChainBlock.diagramSequence(List(20) { null }))
    }

    @Test
    fun `un valor crudo fuera de la tabla no rompe nada`() {
        assertEquals(
            listOf(ChainDiagramBlock.BOOSTER),
            ChainBlock.diagramSequence(listOf(0x7F, ChainBlock.OD.value)),
        )
    }
}
