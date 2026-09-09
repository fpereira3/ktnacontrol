package dev.alonx3.ktnacontrol.protocol.tsl

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mandar un `.tsl` al amplificador, sobre el fichero real del repo.
 *
 * Lo que se fija aquí es la forma del envío: qué bloques se trocean, cuáles no y —lo más
 * importante— **cuáles no se mandan en absoluto**. Escribir un bloque de dirección dudosa no
 * enseñaría un número raro, escribiría encima de otro parámetro del amplificador.
 */
class TslTransferTest {

    private val referenceFile = File("../reference/FxFloorboard/default_mk2.tsl")

    private fun reference(): TslPreset {
        val result = TslParser.parse(referenceFile.readText())
        assertTrue("el fichero de referencia debería parsear", result is TslParseResult.Parsed)
        return (result as TslParseResult.Parsed).presets.single()
    }

    private fun planOfReference() = TslTransfer.plan(MemoryImage.from(reference().memory))

    private fun payloadOf(message: ByteArray): ByteArray =
        (RolandSysEx.parse(message) as RolandMessage.Data).data

    private fun addressOf(message: ByteArray): Address =
        (RolandSysEx.parse(message) as RolandMessage.Data).address

    @Test
    fun `las dos claves de 221 bytes se trocean y las demas no`() {
        val plan = planOfReference()

        assertEquals(
            "solo Fx(1) y Fx(2) pasan de 128 bytes",
            listOf("UserPatch%Fx(1)", "UserPatch%Fx(2)"),
            plan.writes.filter { it.chunked }.map { it.key },
        )
        plan.writes.filterNot { it.chunked }.forEach { write ->
            assertEquals("${write.key} debería caber en un solo SET", 1, write.messages.size)
            assertTrue("${write.key} no debería pasar de 128 bytes", write.dataBytes <= 128)
        }
    }

    @Test
    fun `los trozos de Fx(1) van a 60 00 01 00 y 60 00 02 00`() {
        val write = planOfReference().writes.single { it.key == "UserPatch%Fx(1)" }

        assertEquals(2, write.messages.size)
        assertEquals(Address(0x60, 0x00, 0x01, 0x00), addressOf(write.messages[0]))
        assertEquals(Address(0x60, 0x00, 0x02, 0x00), addressOf(write.messages[1]))
        assertEquals(128, payloadOf(write.messages[0]).size)
        assertEquals(93, payloadOf(write.messages[1]).size)
    }

    @Test
    fun `los bloques en disputa no generan ningun mensaje`() {
        val plan = planOfReference()
        val disputed = TslBlockMap.blocks.filter { it.confidence == TslConfidence.DISPUTED }

        assertEquals("el mapa tiene cuatro bloques en disputa", 4, disputed.size)
        disputed.forEach { block ->
            assertFalse(
                "${block.key} no debería mandarse",
                plan.writes.any { it.key == block.key },
            )
            assertTrue(
                "${block.key} debería salir en skipped, no desaparecer en silencio",
                plan.skipped.any { it.key == block.key },
            )
        }
        // Y lo que sí se manda son las otras 18 claves del mapa, ni una menos.
        assertEquals(TslBlockMap.BLOCK_COUNT - disputed.size, plan.writes.size)
    }

    @Test
    fun `los bytes de cada bloque llegan enteros y en su direccion`() {
        val image = MemoryImage.from(reference().memory)

        TslTransfer.plan(image).writes.forEach { write ->
            val rebuilt = write.messages.fold(ByteArray(0)) { acc, m -> acc + payloadOf(m) }
            assertArrayEquals(
                "${write.key} debería reconstruirse byte a byte",
                image.bytesAt(write.address, write.block.size),
                rebuilt,
            )
        }
    }

    @Test
    fun `un preset con un bloque incompleto no manda ese bloque a medias`() {
        val image = MemoryImage.empty()
        // Solo 10 de los 18 bytes de UserPatch%Status.
        image.write(Address(0x60, 0x00, 0x06, 0x50), ByteArray(10))

        val plan = TslTransfer.plan(image)

        assertTrue("no hay ningún bloque completo que mandar", plan.writes.isEmpty())
        assertTrue(
            "y Status debería aparecer como omitido",
            plan.skipped.any { it.key == "UserPatch%Status" },
        )
    }

    @Test
    fun `un preset en blanco se manda entero`() {
        val plan = TslTransfer.plan(TslWriter.blank("VACIO"))

        assertEquals(TslBlockMap.BLOCK_COUNT - 4, plan.writes.size)
        assertEquals("18 bloques, dos de ellos en dos trozos", 20, plan.messageCount)
    }
}
