package dev.alonx3.ktnacontrol.protocol

/**
 * El mapa de parámetros internos de los 31 tipos de Mod/FX, extraído de `midi.xml`
 * (CLAUDE.md §5.2, "Extracción completa de los 31 tipos"). Las direcciones son las de **Mod**;
 * para FX, `+ FX_OFFSET`.
 *
 * ⚠️ **Nada de esto está confirmado con el amplificador.** Es la extracción documental
 * cableada; el mismo aviso de siempre aplica: una dirección plausible puede no hacer nada
 * (CLAUDE.md §5, "Cómo encontrar la dirección de un parámetro").
 *
 * **No incluye el Pre Delay de 2x2 Chorus** (`60 00 02 3A`/`3E`): ya está cableado aparte como
 * [KatanaAddresses.MOD_CHORUS_PRE_DELAY_LOW]/[KatanaAddresses.MOD_CHORUS_PRE_DELAY_HIGH], y
 * [dev.alonx3.ktnacontrol.device.KatanaRepository] lo mezcla con esta tabla al renderizar.
 *
 * **Tampoco incluye** las 24 direcciones de escala de usuario de Harmonist ni el bloque
 * "Custom" de ningún tipo — se dejan fuera a propósito, igual que el Custom Type de Booster.
 */
object ModFxInternalParams {

    /**
     * `FX = Mod + 0x0200` en la notación de direcciones — sumar 2 al tercer byte —, verificado
     * sobre los 237 nodos del bloque con 0 diferencias (CLAUDE.md §5.2). En términos de
     * [Address.value] (base 128) eso es **+256, no +512**: la notación nombra bytes de la
     * dirección, no es un entero a sumar tal cual.
     */
    const val FX_OFFSET = 2 * 128

    val byType: Map<ModFxType, List<ParamSpec>> = mapOf(
        ModFxType.TOUCH_WAH to listOf(
            ParamSpec("Mode", Address(0x60, 0x00, 0x01, 0x02), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("LPF", "BPF"),
        )),
            ParamSpec("Polarity", Address(0x60, 0x00, 0x01, 0x03), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Down", "Up"),
        )),
            ParamSpec("Sens", Address(0x60, 0x00, 0x01, 0x04), ParamKind.Direct(0..100)),
            ParamSpec("Freq", Address(0x60, 0x00, 0x01, 0x05), ParamKind.Direct(0..100)),
            ParamSpec("Peak", Address(0x60, 0x00, 0x01, 0x06), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x01, 0x07), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x01, 0x08), ParamKind.Direct(0..100)),
        ),
        ModFxType.AUTO_WAH to listOf(
            ParamSpec("Mode", Address(0x60, 0x00, 0x01, 0x09), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("LPF", "BPF"),
        )),
            ParamSpec("Freq", Address(0x60, 0x00, 0x01, 0x0A), ParamKind.Direct(0..100)),
            ParamSpec("Peak", Address(0x60, 0x00, 0x01, 0x0B), ParamKind.Direct(0..100)),
            ParamSpec("Rate", Address(0x60, 0x00, 0x01, 0x0C), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x01, 0x0D), ParamKind.Direct(0..100)),
            ParamSpec("Direct", Address(0x60, 0x00, 0x01, 0x0E), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x01, 0x0F), ParamKind.Direct(0..100)),
        ),
        ModFxType.PEDAL_WAH to listOf(
            ParamSpec("Type", Address(0x60, 0x00, 0x01, 0x10), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05),
            labels = listOf("CRY WAH", "VO WAH", "Fat WAH", "Light WAH", "7string WAH", "Reso WAH"),
        )),
            ParamSpec("Pedal Pos", Address(0x60, 0x00, 0x01, 0x11), ParamKind.Direct(0..100)),
            ParamSpec("Pedal Min", Address(0x60, 0x00, 0x01, 0x12), ParamKind.Direct(0..100)),
            ParamSpec("Pedal Max", Address(0x60, 0x00, 0x01, 0x13), ParamKind.Direct(0..100)),
            ParamSpec("Effect Level", Address(0x60, 0x00, 0x01, 0x14), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x01, 0x15), ParamKind.Direct(0..100)),
        ),
        ModFxType.COMPRESSOR to listOf(
            ParamSpec("Type", Address(0x60, 0x00, 0x01, 0x16), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06),
            labels = listOf("BOSS Comp", "Hi-BAND", "Light", "D-Comp", "Orange", "Fat", "Mild"),
        )),
            ParamSpec("Sustain", Address(0x60, 0x00, 0x01, 0x17), ParamKind.Direct(0..100)),
            ParamSpec("Attack", Address(0x60, 0x00, 0x01, 0x18), ParamKind.Direct(0..100)),
            ParamSpec("Tone", Address(0x60, 0x00, 0x01, 0x19), ParamKind.Centered(50)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x01, 0x1A), ParamKind.Direct(0..100)),
        ),
        ModFxType.LIMITER to listOf(
            ParamSpec("Type", Address(0x60, 0x00, 0x01, 0x1B), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02),
            labels = listOf("BOSS Limiter", "Rack 160D", "Vtg Rack U"),
        )),
            ParamSpec("Attack", Address(0x60, 0x00, 0x01, 0x1C), ParamKind.Direct(0..100)),
            ParamSpec("Thresh", Address(0x60, 0x00, 0x01, 0x1D), ParamKind.Direct(0..100)),
            ParamSpec("Ratio", Address(0x60, 0x00, 0x01, 0x1E), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11),
            labels = listOf("1:1", "1.2:1", "1.4:1", "1.6:1", "1.8:1", "2:1", "2.3:1", "2.6:1", "3:1", "3.5:1", "4:1", "5:1", "6:1", "8:1", "10:1", "12:1", "20:1", "oo:1"),
        )),
            ParamSpec("Release", Address(0x60, 0x00, 0x01, 0x1F), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x01, 0x20), ParamKind.Direct(0..100)),
        ),
        ModFxType.GRAPHIC_EQ to listOf(
            ParamSpec("31Hz", Address(0x60, 0x00, 0x01, 0x21), ParamKind.Centered(20)),
            ParamSpec("62Hz", Address(0x60, 0x00, 0x01, 0x22), ParamKind.Centered(20)),
            ParamSpec("125Hz", Address(0x60, 0x00, 0x01, 0x23), ParamKind.Centered(20)),
            ParamSpec("250Hz", Address(0x60, 0x00, 0x01, 0x24), ParamKind.Centered(20)),
            ParamSpec("500Hz", Address(0x60, 0x00, 0x01, 0x25), ParamKind.Centered(20)),
            ParamSpec("1KHz", Address(0x60, 0x00, 0x01, 0x26), ParamKind.Centered(20)),
            ParamSpec("2KHz", Address(0x60, 0x00, 0x01, 0x27), ParamKind.Centered(20)),
            ParamSpec("4KHz", Address(0x60, 0x00, 0x01, 0x28), ParamKind.Centered(20)),
            ParamSpec("8KHz", Address(0x60, 0x00, 0x01, 0x29), ParamKind.Centered(20)),
            ParamSpec("16KHz", Address(0x60, 0x00, 0x01, 0x2A), ParamKind.Centered(20)),
            ParamSpec("Level", Address(0x60, 0x00, 0x01, 0x2B), ParamKind.Centered(20)),
        ),
        ModFxType.PARAMETRIC_EQ to listOf(
            ParamSpec("Lo Cut Off", Address(0x60, 0x00, 0x01, 0x2C), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11),
            labels = listOf("FLAT", "20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz", "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz"),
        )),
            ParamSpec("Lo Gain", Address(0x60, 0x00, 0x01, 0x2D), ParamKind.Centered(20)),
            ParamSpec("Lo Mid Freq", Address(0x60, 0x00, 0x01, 0x2E), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B),
            labels = listOf("20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz", "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz", "1.00k", "1.25k", "1.60k", "2.00k", "2.50k", "3.15k", "4.00k", "5.00k", "6.30k", "8.00k", "10.0k"),
        )),
            ParamSpec("Lo Mid Q", Address(0x60, 0x00, 0x01, 0x2F), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05),
            labels = listOf("0.5", "1", "2", "4", "8", "16"),
        )),
            ParamSpec("Lo Mid Gain", Address(0x60, 0x00, 0x01, 0x30), ParamKind.Centered(20)),
            ParamSpec("Hi Mid Freq", Address(0x60, 0x00, 0x01, 0x31), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B),
            labels = listOf("20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz", "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz", "1.00k", "1.25k", "1.60k", "2.00k", "2.50k", "3.15k", "4.00k", "5.00k", "6.30k", "8.00k", "10.0k"),
        )),
            ParamSpec("Hi Mid Q", Address(0x60, 0x00, 0x01, 0x32), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05),
            labels = listOf("0.5", "1", "2", "4", "8", "16"),
        )),
            ParamSpec("Hi Mid Gain", Address(0x60, 0x00, 0x01, 0x33), ParamKind.Centered(20)),
            ParamSpec("Hi Gain", Address(0x60, 0x00, 0x01, 0x34), ParamKind.Centered(20)),
            ParamSpec("Hi Cut Off", Address(0x60, 0x00, 0x01, 0x35), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E),
            labels = listOf("630Hz", "800Hz", "1.00k", "1.25k", "1.60k", "2.00k", "2.50k", "3.15k", "4.00k", "5.00k", "6.00k", "8.00k", "10.0k", "12.5k", "FLAT"),
        )),
            ParamSpec("Level", Address(0x60, 0x00, 0x01, 0x36), ParamKind.Centered(20)),
        ),
        ModFxType.GUITAR_SIM to listOf(
            ParamSpec("Type", Address(0x60, 0x00, 0x01, 0x37), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07),
            labels = listOf("S->H", "H->S", "H->HF", "S->Hollow", "H->Hollow", "S->AC", "H->AC", "P->AC"),
        )),
            ParamSpec("Low", Address(0x60, 0x00, 0x01, 0x38), ParamKind.Centered(50)),
            ParamSpec("High", Address(0x60, 0x00, 0x01, 0x39), ParamKind.Centered(50)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x01, 0x3A), ParamKind.Direct(0..100)),
            ParamSpec("Body", Address(0x60, 0x00, 0x01, 0x3B), ParamKind.Direct(0..100)),
        ),
        ModFxType.SLOW_GEAR to listOf(
            ParamSpec("Sens", Address(0x60, 0x00, 0x01, 0x3C), ParamKind.Direct(0..100)),
            ParamSpec("Rise Time", Address(0x60, 0x00, 0x01, 0x3D), ParamKind.Direct(0..100)),
            ParamSpec("Level", Address(0x60, 0x00, 0x01, 0x3E), ParamKind.Direct(0..100)),
        ),
        ModFxType.WAVE_SYNTH to listOf(
            ParamSpec("Wave", Address(0x60, 0x00, 0x01, 0x3F), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("SAW", "SQUARE"),
        )),
            ParamSpec("Cutoff Freq", Address(0x60, 0x00, 0x01, 0x40), ParamKind.Direct(0..100)),
            ParamSpec("Reson.", Address(0x60, 0x00, 0x01, 0x41), ParamKind.Direct(0..100)),
            ParamSpec("FLT.Sens", Address(0x60, 0x00, 0x01, 0x42), ParamKind.Direct(0..100)),
            ParamSpec("FLT.Decay", Address(0x60, 0x00, 0x01, 0x43), ParamKind.Direct(0..100)),
            ParamSpec("FLT.Depth", Address(0x60, 0x00, 0x01, 0x44), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x01, 0x45), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x01, 0x46), ParamKind.Direct(0..100)),
        ),
        ModFxType.OCTAVE to listOf(
            ParamSpec("Range", Address(0x60, 0x00, 0x01, 0x47), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03),
            labels = listOf("1", "2", "3", "4"),
        )),
            ParamSpec("Octave", Address(0x60, 0x00, 0x01, 0x48), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x01, 0x49), ParamKind.Direct(0..100)),
        ),
        ModFxType.PITCH_SHIFTER to listOf(
            ParamSpec("Voice", Address(0x60, 0x00, 0x01, 0x4A), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("1-Voice", "2-Mono"),
        )),
            ParamSpec("Mode (voz 1)", Address(0x60, 0x00, 0x01, 0x4B), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03),
            labels = listOf("Fast", "Medium", "Slow", "Mono"),
        )),
            ParamSpec("Pitch (voz 1)", Address(0x60, 0x00, 0x01, 0x4C), ParamKind.Centered(24)),
            ParamSpec("Fine (voz 1)", Address(0x60, 0x00, 0x01, 0x4D), ParamKind.Centered(50)),
            ParamSpec("Pre Delay (voz 1)", Address(0x60, 0x00, 0x01, 0x4E), ParamKind.TwoByteDirect(0..300)),
            ParamSpec("Voice 1", Address(0x60, 0x00, 0x01, 0x50), ParamKind.Direct(0..100)),
            ParamSpec("Mode (voz 2)", Address(0x60, 0x00, 0x01, 0x51), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03),
            labels = listOf("Fast", "Medium", "Slow", "Mono"),
        )),
            ParamSpec("Pitch (voz 2)", Address(0x60, 0x00, 0x01, 0x52), ParamKind.Centered(24)),
            ParamSpec("Fine (voz 2)", Address(0x60, 0x00, 0x01, 0x53), ParamKind.Centered(50)),
            ParamSpec("Pre Delay (voz 2)", Address(0x60, 0x00, 0x01, 0x54), ParamKind.TwoByteDirect(0..300)),
            ParamSpec("Voice 2", Address(0x60, 0x00, 0x01, 0x56), ParamKind.Direct(0..100)),
            ParamSpec("Feedback", Address(0x60, 0x00, 0x01, 0x57), ParamKind.Direct(0..100)),
            ParamSpec("Direct", Address(0x60, 0x00, 0x01, 0x58), ParamKind.Direct(0..100)),
        ),
        ModFxType.HARMONIST to listOf(
            ParamSpec("Voice", Address(0x60, 0x00, 0x01, 0x59), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("1-Voice", "2-Voice"),
        )),
            ParamSpec("Harmony (voz 1)", Address(0x60, 0x00, 0x01, 0x5A), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D),
            labels = listOf("-2oct", "-14th", "-13th", "-12th", "-11th", "-10th", "-9th", "-1oct", "-7th", "-6th", "-5th", "-4th", "-3rd", "-2nd", "Unison", "+2nd", "+3rd", "+4th", "+5th", "+6th", "+7th", "+1oct", "+9th", "+10th", "+11th", "+12th", "+13th", "+14th", "+2oct", "User"),
        )),
            ParamSpec("Pre Delay (voz 1)", Address(0x60, 0x00, 0x01, 0x5B), ParamKind.TwoByteDirect(0..300)),
            ParamSpec("Voice 1", Address(0x60, 0x00, 0x01, 0x5D), ParamKind.Direct(0..100)),
            ParamSpec("Harmony (voz 2)", Address(0x60, 0x00, 0x01, 0x5E), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D),
            labels = listOf("-2oct", "-14th", "-13th", "-12th", "-11th", "-10th", "-9th", "-1oct", "-7th", "-6th", "-5th", "-4th", "-3rd", "-2nd", "Unison", "+2nd", "+3rd", "+4th", "+5th", "+6th", "+7th", "+1oct", "+9th", "+10th", "+11th", "+12th", "+13th", "+14th", "+2oct", "User"),
        )),
            ParamSpec("Pre Delay (voz 2)", Address(0x60, 0x00, 0x01, 0x5F), ParamKind.TwoByteDirect(0..300)),
            ParamSpec("Voice 2", Address(0x60, 0x00, 0x01, 0x61), ParamKind.Direct(0..100)),
            ParamSpec("FeedBack", Address(0x60, 0x00, 0x01, 0x62), ParamKind.Direct(0..100)),
            ParamSpec("Direct", Address(0x60, 0x00, 0x01, 0x63), ParamKind.Direct(0..100)),
        ),
        ModFxType.ACU_PROCESSOR to listOf(
            ParamSpec("Type", Address(0x60, 0x00, 0x01, 0x7C), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03),
            labels = listOf("Small", "Medium", "Bright", "Power"),
        )),
            ParamSpec("Bass", Address(0x60, 0x00, 0x01, 0x7D), ParamKind.Centered(50)),
            ParamSpec("Middle", Address(0x60, 0x00, 0x01, 0x7E), ParamKind.Centered(50)),
            ParamSpec("Mid.Freq", Address(0x60, 0x00, 0x01, 0x7F), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B),
            labels = listOf("20.0Hz", "25.0Hz", "31.5Hz", "40.0Hz", "50.0Hz", "63.0Hz", "80.0Hz", "100Hz", "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz", "1.00kHz", "1.25kHz", "1.60kHz", "2.00kHz", "2.50kHz", "3.15kHz", "4.00kHz", "5.00kHz", "6.30kHz", "8.00kHz", "10.0kHz"),
        )),
            ParamSpec("Treble", Address(0x60, 0x00, 0x02, 0x00), ParamKind.Centered(50)),
            ParamSpec("Presence", Address(0x60, 0x00, 0x02, 0x01), ParamKind.Centered(50)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x02, 0x02), ParamKind.Direct(0..100)),
        ),
        ModFxType.PHASER to listOf(
            ParamSpec("Type", Address(0x60, 0x00, 0x02, 0x03), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03),
            labels = listOf("4stage", "8stage", "12stage", "Bi-Phase"),
        )),
            ParamSpec("Rate", Address(0x60, 0x00, 0x02, 0x04), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x02, 0x05), ParamKind.Direct(0..100)),
            ParamSpec("Manual", Address(0x60, 0x00, 0x02, 0x06), ParamKind.Direct(0..100)),
            ParamSpec("Reson.", Address(0x60, 0x00, 0x02, 0x07), ParamKind.Direct(0..100)),
            ParamSpec("Step Rate", Address(0x60, 0x00, 0x02, 0x08), ParamKind.OffThenOneBased(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x02, 0x09), ParamKind.Direct(0..100)),
            ParamSpec("Direct", Address(0x60, 0x00, 0x02, 0x0A), ParamKind.Direct(0..100)),
        ),
        ModFxType.FLANGER to listOf(
            ParamSpec("Rate", Address(0x60, 0x00, 0x02, 0x0B), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x02, 0x0C), ParamKind.Direct(0..100)),
            ParamSpec("Manual", Address(0x60, 0x00, 0x02, 0x0D), ParamKind.Direct(0..100)),
            ParamSpec("Reson.", Address(0x60, 0x00, 0x02, 0x0E), ParamKind.Direct(0..100)),
            ParamSpec("Separ", Address(0x60, 0x00, 0x02, 0x0F), ParamKind.Direct(0..100)),
            ParamSpec("Low Cut", Address(0x60, 0x00, 0x02, 0x10), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A),
            labels = listOf("FLAT", "55.0Hz", "110Hz", "165Hz", "200Hz", "280Hz", "340Hz", "400Hz", "500Hz", "630Hz", "800Hz"),
        )),
            ParamSpec("Effect", Address(0x60, 0x00, 0x02, 0x11), ParamKind.Direct(0..100)),
            ParamSpec("Direct", Address(0x60, 0x00, 0x02, 0x12), ParamKind.Direct(0..100)),
        ),
        ModFxType.TREMOLO to listOf(
            ParamSpec("Shape", Address(0x60, 0x00, 0x02, 0x13), ParamKind.Direct(0..100)),
            ParamSpec("Rate", Address(0x60, 0x00, 0x02, 0x14), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x02, 0x15), ParamKind.Direct(0..100)),
            ParamSpec("Level", Address(0x60, 0x00, 0x02, 0x16), ParamKind.Direct(0..100)),
        ),
        ModFxType.ROTARY to listOf(
            ParamSpec("Speed", Address(0x60, 0x00, 0x02, 0x17), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Slow", "Fast"),
        )),
            ParamSpec("Rate (Slow)", Address(0x60, 0x00, 0x02, 0x18), ParamKind.Direct(0..100)),
            ParamSpec("Rate (Fast)", Address(0x60, 0x00, 0x02, 0x19), ParamKind.Direct(0..100)),
            ParamSpec("Rise Time", Address(0x60, 0x00, 0x02, 0x1A), ParamKind.Direct(0..100)),
            ParamSpec("Fall Time", Address(0x60, 0x00, 0x02, 0x1B), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x02, 0x1C), ParamKind.Direct(0..100)),
            ParamSpec("Level", Address(0x60, 0x00, 0x02, 0x1D), ParamKind.Direct(0..100)),
        ),
        ModFxType.UNI_V to listOf(
            ParamSpec("Rate", Address(0x60, 0x00, 0x02, 0x1E), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x02, 0x1F), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x02, 0x20), ParamKind.Direct(0..100)),
        ),
        ModFxType.SLICER to listOf(
            ParamSpec("Pattern", Address(0x60, 0x00, 0x02, 0x21), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13),
            labels = listOf("P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9", "P10", "P11", "P12", "P13", "P14", "P15", "P16", "P17", "P18", "P19", "P20"),
        )),
            ParamSpec("Rate", Address(0x60, 0x00, 0x02, 0x22), ParamKind.Direct(0..100)),
            ParamSpec("Trig.Sens", Address(0x60, 0x00, 0x02, 0x23), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x02, 0x24), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x02, 0x25), ParamKind.Direct(0..100)),
        ),
        ModFxType.VIBRATO to listOf(
            ParamSpec("Rate", Address(0x60, 0x00, 0x02, 0x26), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x02, 0x27), ParamKind.Direct(0..100)),
            ParamSpec("Off/On", Address(0x60, 0x00, 0x02, 0x28), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Off", "On"),
        )),
            ParamSpec("Rise Time", Address(0x60, 0x00, 0x02, 0x29), ParamKind.Direct(0..100)),
            ParamSpec("Level", Address(0x60, 0x00, 0x02, 0x2A), ParamKind.Direct(0..100)),
        ),
        ModFxType.RING_MODULATE to listOf(
            ParamSpec("Mode", Address(0x60, 0x00, 0x02, 0x2B), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Normal", "Intelligent"),
        )),
            ParamSpec("Freq", Address(0x60, 0x00, 0x02, 0x2C), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x02, 0x2D), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x02, 0x2E), ParamKind.Direct(0..100)),
        ),
        ModFxType.HUMANIZER to listOf(
            ParamSpec("Mode", Address(0x60, 0x00, 0x02, 0x2F), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Picking", "Auto"),
        )),
            ParamSpec("Vowel 1", Address(0x60, 0x00, 0x02, 0x30), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04),
            labels = listOf("A", "E", "I", "O", "U"),
        )),
            ParamSpec("Vowel 2", Address(0x60, 0x00, 0x02, 0x31), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04),
            labels = listOf("A", "E", "I", "O", "U"),
        )),
            ParamSpec("Sens", Address(0x60, 0x00, 0x02, 0x32), ParamKind.Direct(0..100)),
            ParamSpec("Rate", Address(0x60, 0x00, 0x02, 0x33), ParamKind.Direct(0..100)),
            ParamSpec("Depth", Address(0x60, 0x00, 0x02, 0x34), ParamKind.Direct(0..100)),
            ParamSpec("Manual", Address(0x60, 0x00, 0x02, 0x35), ParamKind.Direct(0..100)),
            ParamSpec("Effect", Address(0x60, 0x00, 0x02, 0x36), ParamKind.Direct(0..100)),
        ),
        ModFxType.CHORUS to listOf(
            ParamSpec("Xover Freq", Address(0x60, 0x00, 0x02, 0x37), ParamKind.Enum(
            values = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10),
            labels = listOf("100Hz", "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz", "800Hz", "1.00kHz", "1.25kHz", "1.60kHz", "2.00kHz", "2.50kHz", "3.15kHz", "4.00kHz"),
        )),
            ParamSpec("Rate (banda baja)", Address(0x60, 0x00, 0x02, 0x38), ParamKind.Direct(0..100)),
            ParamSpec("Depth (banda baja)", Address(0x60, 0x00, 0x02, 0x39), ParamKind.Direct(0..100)),
            ParamSpec("Low", Address(0x60, 0x00, 0x02, 0x3B), ParamKind.Direct(0..100)),
            ParamSpec("Rate (banda alta)", Address(0x60, 0x00, 0x02, 0x3C), ParamKind.Direct(0..100)),
            ParamSpec("Depth (banda alta)", Address(0x60, 0x00, 0x02, 0x3D), ParamKind.Direct(0..100)),
            ParamSpec("High", Address(0x60, 0x00, 0x02, 0x3F), ParamKind.Direct(0..100)),
            ParamSpec("Direct", Address(0x60, 0x00, 0x02, 0x40), ParamKind.Direct(0..100)),
        ),
        ModFxType.AC_GUITAR_SIM to listOf(
            ParamSpec("Top", Address(0x60, 0x00, 0x02, 0x41), ParamKind.Centered(50)),
            ParamSpec("Body", Address(0x60, 0x00, 0x02, 0x42), ParamKind.Direct(0..100)),
            ParamSpec("Low", Address(0x60, 0x00, 0x02, 0x43), ParamKind.Centered(50)),
            ParamSpec("High", Address(0x60, 0x00, 0x02, 0x44), ParamKind.Centered(50)),
            ParamSpec("Level", Address(0x60, 0x00, 0x02, 0x45), ParamKind.Direct(0..100)),
        ),
        ModFxType.PHASER_90E to listOf(
            ParamSpec("Script", Address(0x60, 0x00, 0x02, 0x46), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Off", "On"),
        )),
            ParamSpec("Speed", Address(0x60, 0x00, 0x02, 0x47), ParamKind.Direct(0..100)),
        ),
        ModFxType.FLANGER_117E to listOf(
            ParamSpec("Manual", Address(0x60, 0x00, 0x02, 0x48), ParamKind.Direct(0..100)),
            ParamSpec("Width", Address(0x60, 0x00, 0x02, 0x49), ParamKind.Direct(0..100)),
            ParamSpec("Speed", Address(0x60, 0x00, 0x02, 0x4A), ParamKind.Direct(0..100)),
            ParamSpec("Regeneration", Address(0x60, 0x00, 0x02, 0x4B), ParamKind.Direct(0..100)),
        ),
        ModFxType.WAH_95E to listOf(
            ParamSpec("Pedal Pos", Address(0x60, 0x00, 0x02, 0x4C), ParamKind.Direct(0..100)),
            ParamSpec("Pedal Min", Address(0x60, 0x00, 0x02, 0x4D), ParamKind.Direct(0..100)),
            ParamSpec("Pedal Max", Address(0x60, 0x00, 0x02, 0x4E), ParamKind.Direct(0..100)),
            ParamSpec("Effect Level", Address(0x60, 0x00, 0x02, 0x4F), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x02, 0x50), ParamKind.Direct(0..100)),
        ),
        ModFxType.DC30 to listOf(
            ParamSpec("Selector", Address(0x60, 0x00, 0x02, 0x51), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("Chorus", "Echo"),
        )),
            ParamSpec("Input", Address(0x60, 0x00, 0x02, 0x52), ParamKind.Direct(0..100)),
            ParamSpec("Intensity", Address(0x60, 0x00, 0x02, 0x53), ParamKind.Direct(0..100)),
            ParamSpec("Repeat Rate", Address(0x60, 0x00, 0x02, 0x54), ParamKind.TwoByteDirect(40..600)),
            ParamSpec("Intensity (echo)", Address(0x60, 0x00, 0x02, 0x56), ParamKind.Direct(0..100)),
            ParamSpec("Volume", Address(0x60, 0x00, 0x02, 0x57), ParamKind.Direct(0..100)),
            ParamSpec("Tone", Address(0x60, 0x00, 0x02, 0x58), ParamKind.Direct(0..100)),
            ParamSpec("Output Select", Address(0x60, 0x00, 0x02, 0x59), ParamKind.Enum(
            values = listOf(0x00, 0x01),
            labels = listOf("D+E", "D/E"),
        )),
        ),
        ModFxType.HEAVY_OCTAVE to listOf(
            ParamSpec("Octave -1", Address(0x60, 0x00, 0x02, 0x5A), ParamKind.Direct(0..100)),
            ParamSpec("Octave -2", Address(0x60, 0x00, 0x02, 0x5B), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x02, 0x5C), ParamKind.Direct(0..100)),
        ),
        ModFxType.PEDAL_BEND to listOf(
            ParamSpec("Pitch", Address(0x60, 0x00, 0x02, 0x5D), ParamKind.Centered(24)),
            ParamSpec("Pedal Posn", Address(0x60, 0x00, 0x02, 0x5E), ParamKind.Direct(0..100)),
            ParamSpec("Effect Level", Address(0x60, 0x00, 0x02, 0x5F), ParamKind.Direct(0..100)),
            ParamSpec("Direct Mix", Address(0x60, 0x00, 0x02, 0x60), ParamKind.Direct(0..100)),
        ),
    )
}
