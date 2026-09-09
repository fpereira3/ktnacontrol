package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardado de presets (CLAUDE.md §5): los dos mensajes que sobrescriben un canal.
 *
 * Es la primera operación **destructiva e irreversible** del proyecto, y además
 * **fire-and-forget**: si el commit sale mal formado, el amplificador no se queja — o no hace
 * nada, o hace algo que nadie pidió sobre un canal que igual tenía trabajo dentro. Así que aquí
 * los bytes se comprueban **enteros y a mano**, no solo "parsea sin error": el mensaje completo
 * de CH A1 está escrito byte a byte tal y como lo documenta `patchWriteDialog.cpp`, para que un
 * cambio en el checksum o en el orden de los campos salte antes de llegar al hardware.
 */
class PresetSaveTest {

    // --- El commit ------------------------------------------------------------------------

    @Test
    fun `el commit de CH A1 es exactamente el mensaje documentado`() {
        // `F0 41 00 00 00 00 33` prefijo Roland+Katana · `12` SET · `7F 00 01 04` dirección ·
        // `00 01` dato de 2 bytes · `7B` checksum · `F7`.
        // Checksum a mano: 0x7F+0x00+0x01+0x04+0x00+0x01 = 133; (128 − 133 % 128) % 128 = 123 = 0x7B.
        val expected = byteArrayOf(
            0xF0.toByte(), 0x41, 0x00, 0x00, 0x00, 0x00, 0x33,
            0x12,
            0x7F, 0x00, 0x01, 0x04,
            0x00, 0x01,
            0x7B,
            0xF7.toByte(),
        )
        assertArrayEquals(expected, PresetSave.commitMessage(1))
    }

    @Test
    fun `el commit de CH B3 tambien cuadra byte a byte`() {
        // B3 es el canal 7: 0x7F+0x01+0x04+0x07 = 139; (128 − 139 % 128) % 128 = 117 = 0x75.
        // Un segundo canal, y de un banco distinto, porque el checksum del primero saldría
        // igual aunque el dato estuviera en el byte equivocado.
        val expected = byteArrayOf(
            0xF0.toByte(), 0x41, 0x00, 0x00, 0x00, 0x00, 0x33,
            0x12,
            0x7F, 0x00, 0x01, 0x04,
            0x00, 0x07,
            0x75,
            0xF7.toByte(),
        )
        assertArrayEquals(expected, PresetSave.commitMessage(7))
        assertEquals("B3", PresetSave.channelLabel(7))
    }

    @Test
    fun `los ocho commits se reparsean con su direccion y su canal`() {
        PresetSave.CHANNELS.forEach { channel ->
            when (val parsed = RolandSysEx.parse(PresetSave.commitMessage(channel))) {
                is RolandMessage.Data -> {
                    assertEquals(KatanaAddresses.PRESET_SAVE, parsed.address)
                    assertEquals(
                        "el dato del commit son 2 bytes",
                        KatanaAddresses.PRESET_SAVE_SIZE,
                        parsed.data.size,
                    )
                    assertEquals(channel, MidiBytes.decode(parsed.data))
                }

                is RolandMessage.Invalid -> throw AssertionError("canal $channel: ${parsed.reason}")
            }
        }
    }

    @Test
    fun `cada canal produce un commit distinto`() {
        // Si `commitMessage` ignorara su argumento, todo lo de arriba menos el primer test
        // seguiría pasando y se guardaría siempre en el mismo sitio.
        val messages = PresetSave.CHANNELS.map { PresetSave.commitMessage(it).toList() }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `un canal fuera de 1-8 se rechaza en vez de mandarse`() {
        // El `0` es PANEL, cuyo efecto nadie ha comprobado (CLAUDE.md §5 → TBD), y el `9` no
        // existe. En una operación sin deshacer, rechazar es lo correcto: clampear elegiría un
        // canal por su cuenta.
        listOf(-1, 0, 9, 127).forEach { channel ->
            val failed = runCatching { PresetSave.commitMessage(channel) }.isFailure
            assertTrue("el canal $channel debería rechazarse", failed)
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), PresetSave.CHANNELS)
    }

    // --- El nombre ------------------------------------------------------------------------

    @Test
    fun `el nombre son 16 bytes ASCII rellenados con espacios`() {
        val bytes = PresetSave.encodeName("KATANA")
        assertEquals(PresetSave.NAME_LENGTH, bytes.size)
        assertArrayEquals(
            // "KATANA" + 10 espacios, exactamente el ejemplo de `katana_sysex.txt:145-147`.
            byteArrayOf(0x4B, 0x41, 0x54, 0x41, 0x4E, 0x41, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20),
            bytes,
        )
    }

    @Test
    fun `el mensaje del nombre va a 60 00 00 00 y se reparsea`() {
        val message = PresetSave.nameMessage("Mi Preset")
        when (val parsed = RolandSysEx.parse(message)) {
            is RolandMessage.Data -> {
                assertEquals(KatanaAddresses.CURRENT_PRESET_NAME, parsed.address)
                assertEquals(Address(0x60, 0x00, 0x00, 0x00), parsed.address)
                assertEquals(PresetSave.NAME_LENGTH, parsed.data.size)
                assertEquals("Mi Preset", PresetSave.decodeName(parsed.data))
            }

            is RolandMessage.Invalid -> throw AssertionError(parsed.reason)
        }
    }

    @Test
    fun `un nombre mas largo de 16 se recorta en vez de desbordar el mensaje`() {
        val bytes = PresetSave.encodeName("0123456789ABCDEFGHIJ")
        assertEquals(PresetSave.NAME_LENGTH, bytes.size)
        assertEquals("0123456789ABCDEF", PresetSave.decodeName(bytes))
    }

    @Test
    fun `un nombre con acentos o ene no rompe el mensaje`() {
        // Sin sanear, la eñe (0xF1) se sale de los 7 bits y `RolandSysEx.set` lanza: el
        // guardado entero fallaría por escribir un nombre en castellano, que es lo normal aquí.
        val bytes = PresetSave.encodeName("Distorsión Ñ")
        assertTrue("los datos SysEx son de 7 bits", MidiBytes.isSevenBit(bytes))
        assertEquals("Distorsi?n ?", PresetSave.decodeName(bytes))

        // Y el mensaje completo se construye sin excepción, que es lo que de verdad importa.
        val parsed = RolandSysEx.parse(PresetSave.nameMessage("Distorsión Ñ"))
        assertTrue(parsed is RolandMessage.Data)
    }

    @Test
    fun `los caracteres de control tambien se sustituyen`() {
        val bytes = PresetSave.encodeName("a\nb\tc")
        assertTrue(MidiBytes.isSevenBit(bytes))
        assertEquals("a?b?c", PresetSave.decodeName(bytes))
    }

    @Test
    fun `un nombre vacio da 16 espacios y sigue siendo un mensaje valido`() {
        val bytes = PresetSave.encodeName("")
        assertEquals(PresetSave.NAME_LENGTH, bytes.size)
        assertTrue(bytes.all { it == ' '.code.toByte() })
        assertEquals("", PresetSave.decodeName(bytes))
        assertTrue(RolandSysEx.parse(PresetSave.nameMessage("")) is RolandMessage.Data)
    }

    // --- Las etiquetas de canal ------------------------------------------------------------

    @Test
    fun `las etiquetas siguen los dos bancos del panel, no el numero crudo`() {
        // Decir "canal 7" cuando en el amplificador pone "B3" es justo lo que puede hacer que
        // alguien confirme un guardado sobre el canal equivocado.
        assertEquals("A1", PresetSave.channelLabel(1))
        assertEquals("A4", PresetSave.channelLabel(4))
        assertEquals("B1", PresetSave.channelLabel(5))
        assertEquals("B4", PresetSave.channelLabel(8))
        assertEquals(8, PresetSave.CHANNELS.map(PresetSave::channelLabel).toSet().size)
    }

    @Test
    fun `la numeracion del guardado es la del canal activo, no la de Program Change`() {
        // En SysEx los ocho canales van seguidos `01`..`08`; en Program Change el panel es el 4
        // y parte los bancos por la mitad (§5.1). Confundirlas guardaría en otro canal.
        PresetSave.CHANNELS.forEach { channel ->
            assertTrue(
                "el canal $channel debería ser un valor legal del canal activo",
                channel in KatanaAddresses.ACTIVE_CHANNEL_VALUES,
            )
        }
        // Y el `00` del canal activo (PANEL) NO es un destino de guardado.
        assertTrue(0 in KatanaAddresses.ACTIVE_CHANNEL_VALUES)
        assertTrue(0 !in PresetSave.CHANNELS)
    }

    @Test
    fun `el commit y el nombre van a direcciones distintas`() {
        // Parece obvio y es el error que dejaría el nombre escrito en el registro de commit.
        assertNotEquals(KatanaAddresses.PRESET_SAVE, KatanaAddresses.CURRENT_PRESET_NAME)
        assertEquals(Address(0x7F, 0x00, 0x01, 0x04), KatanaAddresses.PRESET_SAVE)
        assertEquals(KatanaAddresses.MEMORY_DUMP, KatanaAddresses.CURRENT_PRESET_NAME)
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(
            expected.joinToString(" ") { "%02X".format(it) },
            actual.joinToString(" ") { "%02X".format(it) },
        )
    }
}
