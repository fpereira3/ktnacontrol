package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cablear los 27 tipos restantes de Mod/FX (CLAUDE.md §5.2) fue, en la práctica, escribir 192
 * entradas de tabla a mano desde `midi.xml`. Lo único que puede fallar en una tabla así es una
 * dirección mal transcrita o repetida — el resto (el checksum, el framing) ya lo prueba
 * [RolandSysExTest]. Estos tests verifican justo eso, sobre las 192 entradas de una vez, en vez
 * de confiar en que el `grep` manual de la sesión de extracción siga siendo válido.
 */
class ModFxInternalParamsTest {

    private val allSpecs: List<Pair<ModFxType, ParamSpec>> =
        ModFxInternalParams.byType.flatMap { (type, specs) -> specs.map { type to it } }

    @Test
    fun `hay 31 tipos y 192 parametros en total`() {
        assertEquals(31, ModFxInternalParams.byType.size)
        assertEquals(192, allSpecs.size)
    }

    @Test
    fun `ninguna direccion de Mod se repite entre parametros, ni siquiera de tipos distintos`() {
        // Mod es "DSP complejo" (CLAUDE.md §5.2): cada tipo tiene su propio tramo del mismo
        // bloque `60 00 01/02 xx`, así que la única garantía real es que dentro de la tabla
        // entera no haya dos entradas pisando la misma dirección — venga del mismo tipo o de
        // uno vecino.
        val addresses = allSpecs.map { (_, spec) -> spec.address.value }
        assertEquals(
            "hay direcciones de Mod repetidas en la tabla",
            addresses.size,
            addresses.toSet().size,
        )
    }

    @Test
    fun `un parametro de 2 bytes no pisa la direccion del siguiente parametro`() {
        // TwoByteDirect ocupa su dirección base y la siguiente (MidiBytes.encode con
        // byteWidth=2, MSB primero). Si algún spec vecino usara ese segundo byte como su
        // propia dirección de 1 byte, el `grep` de direcciones únicas de arriba no lo vería
        // porque compara bases, no rangos ocupados.
        val occupied = mutableSetOf<Int>()
        allSpecs.forEach { (type, spec) ->
            val width = if (spec.kind is ParamKind.TwoByteDirect) 2 else 1
            val base = spec.address.value
            (0 until width).forEach { offset ->
                val cell = base + offset
                assertTrue(
                    "$type/${spec.label}: la dirección ${spec.address} + $offset ya la ocupa otro parámetro",
                    occupied.add(cell),
                )
            }
        }
    }

    @Test
    fun `FX_OFFSET no hace que ninguna direccion de FX choque con una de Mod`() {
        // La regla verificada en CLAUDE.md §5.2 es +256 (2 * 128), no +512 (el literal
        // hexadecimal "0x0200" tal cual): si alguna vez alguien la reescribe como
        // `0x0200 = 512`, este test la delata sin tener que releer la nota.
        assertEquals(256, ModFxInternalParams.FX_OFFSET)

        val modAddresses = allSpecs.map { (_, spec) -> spec.address.value }.toSet()
        val fxAddresses = allSpecs.map { (_, spec) -> spec.address.value + ModFxInternalParams.FX_OFFSET }

        assertEquals(fxAddresses.size, fxAddresses.toSet().size)
        fxAddresses.forEach { fx ->
            assertTrue("una dirección de FX ($fx) coincide con una de Mod", fx !in modAddresses)
        }
    }

    @Test
    fun `cada Enum tiene tantos labels como values, y sin valores fuera de 7 bits`() {
        allSpecs.forEach { (type, spec) ->
            val kind = spec.kind
            if (kind is ParamKind.Enum) {
                assertEquals(
                    "$type/${spec.label}: values y labels de tamaño distinto",
                    kind.values.size,
                    kind.labels.size,
                )
                assertTrue(
                    "$type/${spec.label}: un value de Enum se sale de 0..0x7F",
                    kind.values.all { it in 0..MidiBytes.MAX_BYTE_VALUE },
                )
            }
        }
    }

    /**
     * Un valor crudo representativo y legal para [kind] — el mínimo del rango que acepta, o el
     * primer valor de un [ParamKind.Enum]. No es "el valor por defecto del amplificador",
     * es solo algo que produzca un SET bien formado para probar el checksum.
     */
    private fun sampleRaw(kind: ParamKind): Int = when (kind) {
        is ParamKind.Direct -> kind.range.first
        is ParamKind.TwoByteDirect -> kind.range.first
        is ParamKind.Centered -> kind.radius // raw = display(0) + radius
        is ParamKind.OffThenOneBased -> 1 // raw = display(range.first) + 1
        is ParamKind.Fractional -> kind.rawRange.first
        is ParamKind.Enum -> kind.values.first()
    }

    @Test
    fun `el SET de cada parametro de Mod tiene checksum correcto y se reparsea igual`() {
        allSpecs.forEach { (type, spec) ->
            val width = if (spec.kind is ParamKind.TwoByteDirect) 2 else 1
            val raw = sampleRaw(spec.kind)
            val data = MidiBytes.encode(raw, width)

            val message = RolandSysEx.set(spec.address, data)
            when (val parsed = RolandSysEx.parse(message)) {
                is RolandMessage.Data -> {
                    assertEquals("$type/${spec.label}: dirección no cuadra tras el parseo", spec.address, parsed.address)
                    assertEquals(
                        "$type/${spec.label}: payload no cuadra tras el parseo",
                        raw,
                        MidiBytes.decode(parsed.data),
                    )
                }

                is RolandMessage.Invalid ->
                    throw AssertionError("$type/${spec.label}: ${parsed.reason}")
            }
        }
    }

    @Test
    fun `el SET de cada parametro de FX (direccion + FX_OFFSET) tiene checksum correcto`() {
        allSpecs.forEach { (type, spec) ->
            val fxAddress = spec.address + ModFxInternalParams.FX_OFFSET
            val width = if (spec.kind is ParamKind.TwoByteDirect) 2 else 1
            val raw = sampleRaw(spec.kind)
            val data = MidiBytes.encode(raw, width)

            val message = RolandSysEx.set(fxAddress, data)
            when (val parsed = RolandSysEx.parse(message)) {
                is RolandMessage.Data -> {
                    assertEquals("FX $type/${spec.label}: dirección no cuadra", fxAddress, parsed.address)
                    assertEquals(
                        "FX $type/${spec.label}: payload no cuadra",
                        raw,
                        MidiBytes.decode(parsed.data),
                    )
                }

                is RolandMessage.Invalid ->
                    throw AssertionError("FX $type/${spec.label}: ${parsed.reason}")
            }
        }
    }

    @Test
    fun `rawToDisplay y displayToRaw son inversas en los extremos de cada rango`() {
        allSpecs.forEach { (type, spec) ->
            val kind = spec.kind
            if (kind is ParamKind.Enum) return@forEach // su "display" es el id, no una escala
            val bounds = kind.displayBounds
            listOf(bounds.start, bounds.endInclusive).forEach { display ->
                val raw = kind.displayToRaw(display)
                val roundTripped = kind.rawToDisplay(raw)
                assertEquals(
                    "$type/${spec.label}: displayToRaw/rawToDisplay no son inversas en $display",
                    display,
                    roundTripped,
                    0.001,
                )
            }
        }
    }

    @Test
    fun `DC30 Repeat Rate no arranca en cero, es rpm no ms`() {
        val spec = ModFxInternalParams.byType.getValue(ModFxType.DC30)
            .first { it.label == "Repeat Rate" }
        val kind = spec.kind as ParamKind.TwoByteDirect
        assertEquals(40, kind.range.first)
        assertEquals(600, kind.range.last)
    }

    @Test
    fun `Step Rate del Phaser es el unico offThenOneBased de los 31 tipos`() {
        val offThenOneBased = allSpecs.filter { (_, spec) -> spec.kind is ParamKind.OffThenOneBased }
        assertEquals(1, offThenOneBased.size)
        val (type, spec) = offThenOneBased.single()
        assertEquals(ModFxType.PHASER, type)
        assertEquals("Step Rate", spec.label)
    }

    @Test
    fun `el Off On interno de Vibrato es distinto del on off del slot`() {
        val spec = ModFxInternalParams.byType.getValue(ModFxType.VIBRATO)
            .first { it.label == "Off/On" }
        assertEquals(Address(0x60, 0x00, 0x02, 0x28), spec.address)
    }

    @Test
    fun `los tres radios de escala centrada conviven sin confundirse`() {
        val radii = allSpecs
            .mapNotNull { (_, spec) -> (spec.kind as? ParamKind.Centered)?.radius }
            .toSet()
        assertEquals(setOf(20, 24, 50), radii)
    }
}
