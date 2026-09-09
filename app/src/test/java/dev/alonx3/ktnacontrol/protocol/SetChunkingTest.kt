package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Troceado de un SET largo (CLAUDE.md §5, "Formato `.tsl`", nota de tamaño).
 *
 * Lo que se comprueba aquí no es la aritmética por la aritmética: los bloques `UserPatch%Fx(1)`
 * y `Fx(2)` de un `.tsl` miden 221 bytes y **cruzan el límite de página de direcciones**, así
 * que un troceado que sume al último byte de la dirección en vez de en base 128 escribiría en
 * el sitio equivocado sin fallar por ningún lado.
 */
class SetChunkingTest {

    private val fxOne = Address(0x60, 0x00, 0x01, 0x00)

    /** El payload de un mensaje ya formado, para no reimplementar el parseo en cada test. */
    private fun payloadOf(message: ByteArray): ByteArray =
        (RolandSysEx.parse(message) as RolandMessage.Data).data

    private fun addressOf(message: ByteArray): Address =
        (RolandSysEx.parse(message) as RolandMessage.Data).address

    @Test
    fun `los 221 bytes de Fx(1) salen en dos mensajes, con el acarreo de pagina`() {
        val data = ByteArray(221) { (it % 128).toByte() }

        val messages = RolandSysEx.setChunked(fxOne, data, chunkSize = 128)

        assertEquals("221 = 128 + 93, o sea dos mensajes", 2, messages.size)
        assertEquals(fxOne, addressOf(messages[0]))
        // 60 00 01 00 + 128 en base 128 es 60 00 02 00, NO 60 00 01 80 (que ni existe).
        assertEquals(Address(0x60, 0x00, 0x02, 0x00), addressOf(messages[1]))
        assertEquals(128, payloadOf(messages[0]).size)
        assertEquals("el último trozo lleva solo lo que queda, sin relleno", 93, payloadOf(messages[1]).size)
    }

    @Test
    fun `cada trozo es un SET valido con su propio checksum`() {
        val data = ByteArray(221) { ((it * 7) % 128).toByte() }

        RolandSysEx.setChunked(fxOne, data, chunkSize = 128).forEach { message ->
            val parsed = RolandSysEx.parse(message)
            assertTrue("cada trozo debería parsear: $parsed", parsed is RolandMessage.Data)
            assertEquals(RolandSysEx.COMMAND_SET, RolandSysEx.commandOf(message))
        }
    }

    @Test
    fun `un payload multiplo exacto del trozo no deja ningun mensaje vacio`() {
        val data = ByteArray(256) { 1 }

        val messages = RolandSysEx.setChunked(fxOne, data, chunkSize = 128)

        assertEquals(2, messages.size)
        messages.forEach { assertEquals(128, payloadOf(it).size) }
    }

    @Test
    fun `un payload mas corto que el trozo da un unico SET identico al de set()`() {
        // Los 18 bytes de UserPatch%Status: el caso de todos los bloques cortos, que no deben
        // cambiar de forma por existir el troceado.
        val status = Address(0x60, 0x00, 0x06, 0x50)
        val data = ByteArray(18) { (it + 3).toByte() }

        val messages = RolandSysEx.setChunked(status, data, chunkSize = 128)

        assertEquals(1, messages.size)
        assertArrayEquals(RolandSysEx.set(status, data), messages.single())
    }

    @Test
    fun `un payload vacio no produce ningun mensaje`() {
        assertEquals(emptyList<ByteArray>(), RolandSysEx.setChunked(fxOne, ByteArray(0)))
    }

    @Test
    fun `la concatenacion de los trozos reconstruye el payload original`() {
        val data = ByteArray(221) { ((it * 13 + 5) % 128).toByte() }

        val rebuilt = RolandSysEx.setChunked(fxOne, data, chunkSize = 128)
            .fold(ByteArray(0)) { acc, message -> acc + payloadOf(message) }

        assertArrayEquals(data, rebuilt)
    }

    @Test
    fun `los trozos son contiguos en direcciones para cualquier tamano`() {
        val data = ByteArray(221) { 0 }

        listOf(1, 7, 64, 128, 220, 221, 500).forEach { chunkSize ->
            val messages = RolandSysEx.setChunked(fxOne, data, chunkSize)
            var expected = fxOne
            messages.forEach { message ->
                assertEquals("con trozo de $chunkSize", expected, addressOf(message))
                expected += RolandSysEx.payloadSizeOf(message)
            }
            assertEquals(
                "el final debería caer justo tras los 221 bytes",
                fxOne + 221,
                expected,
            )
        }
    }
}
