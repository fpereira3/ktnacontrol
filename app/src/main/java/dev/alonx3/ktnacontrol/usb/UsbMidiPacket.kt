package dev.alonx3.ktnacontrol.usb

/**
 * USB-MIDI Class 4-byte packet encoding, used verbatim by the Katana MK2 over its
 * vendor-specific bulk endpoints (CLAUDE.md §4.1).
 *
 * ```
 * byte 0    : (cable number << 4) | Code Index Number (CIN)
 * bytes 1-3 : up to 3 real MIDI bytes, zero-padded
 * ```
 *
 * Plain Kotlin, no `android.*`: this is the transport's wire format, and keeping it pure
 * makes it unit-testable on the JVM. Everything above this layer — `protocol/` included —
 * only ever sees clean `F0…F7` messages.
 */

/** Size of one USB-MIDI event packet. */
const val USB_MIDI_PACKET_SIZE = 4

/** The amp exposes a single virtual cable, so the high nibble of byte 0 is always 0. */
private const val CABLE_NUMBER = 0

private const val CIN_SYSEX_START_OR_CONTINUE = 0x4
private const val CIN_SYSEX_END_1 = 0x5
private const val CIN_SYSEX_END_2 = 0x6
private const val CIN_SYSEX_END_3 = 0x7

private const val PAYLOAD_PER_PACKET = 3

/**
 * Splits a complete SysEx message into 4-byte USB-MIDI packets.
 *
 * Every packet but the last carries 3 bytes with CIN `0x4` ("SysEx starts or continues").
 * The last one uses `0x5` / `0x6` / `0x7` depending on whether 1, 2 or 3 bytes are left, and
 * the unused payload slots stay `0x00`.
 *
 * A message whose length is an exact multiple of 3 ends with a full 3-byte packet tagged
 * `0x7`, not with an empty `0x4` packet.
 */
fun packUsbMidi(sysex: ByteArray): ByteArray {
    if (sysex.isEmpty()) return ByteArray(0)

    val packetCount = (sysex.size + PAYLOAD_PER_PACKET - 1) / PAYLOAD_PER_PACKET
    val packed = ByteArray(packetCount * USB_MIDI_PACKET_SIZE)

    var source = 0
    var target = 0
    while (source < sysex.size) {
        val remaining = sysex.size - source
        val isLastPacket = remaining <= PAYLOAD_PER_PACKET
        val payloadSize = if (isLastPacket) remaining else PAYLOAD_PER_PACKET
        val cin = when {
            !isLastPacket -> CIN_SYSEX_START_OR_CONTINUE
            payloadSize == 1 -> CIN_SYSEX_END_1
            payloadSize == 2 -> CIN_SYSEX_END_2
            else -> CIN_SYSEX_END_3
        }

        packed[target] = ((CABLE_NUMBER shl 4) or cin).toByte()
        sysex.copyInto(
            destination = packed,
            destinationOffset = target + 1,
            startIndex = source,
            endIndex = source + payloadSize,
        )
        // The leftover slots of the last packet keep the 0x00 they were created with.

        source += payloadSize
        target += USB_MIDI_PACKET_SIZE
    }
    return packed
}

/**
 * Reverses [packUsbMidi]: walks [raw] in 4-byte blocks and concatenates the real MIDI bytes
 * of each one, according to its CIN.
 *
 * The result is a plain byte stream, which may hold zero, one or several messages —
 * splitting it into `F0…F7` messages is `SysExFramer`'s job, not this one's. A trailing
 * chunk shorter than a full packet is ignored, and zero-filled padding falls out naturally
 * because CIN `0x0` carries no payload.
 */
fun unpackUsbMidi(raw: ByteArray): ByteArray {
    if (raw.size < USB_MIDI_PACKET_SIZE) return ByteArray(0)

    val unpacked = ArrayList<Byte>(raw.size)
    var offset = 0
    while (offset + USB_MIDI_PACKET_SIZE <= raw.size) {
        val cin = raw[offset].toInt() and 0x0F
        repeat(payloadLengthOf(cin)) { index ->
            unpacked.add(raw[offset + 1 + index])
        }
        offset += USB_MIDI_PACKET_SIZE
    }
    return unpacked.toByteArray()
}

/**
 * How many of the 3 payload bytes a packet actually carries, per the USB-MIDI 1.0 CIN table.
 *
 * Covers the non-SysEx messages too (the amp sends Program/Control Change), and returns 0
 * for the reserved CINs `0x0` and `0x1`, which is also what makes all-zero padding vanish.
 */
private fun payloadLengthOf(cin: Int): Int = when (cin) {
    0x5, 0xF -> 1                                    // SysEx end w/ 1 byte; single byte
    0x2, 0x6, 0xC, 0xD -> 2                          // 2-byte common; SysEx end w/ 2; PC; ChanPress
    0x3, 0x4, 0x7, 0x8, 0x9, 0xA, 0xB, 0xE -> 3      // 3-byte common; SysEx cont/end w/ 3; voice msgs
    else -> 0                                        // 0x0, 0x1: reserved / padding
}
