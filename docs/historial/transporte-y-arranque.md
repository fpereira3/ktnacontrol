> Archivado desde CLAUDE.md — §4 "Arquitectura planeada" (cabecera) y §4.1 "El transporte: USB vendor-specific, NO USB MIDI" completa, más §6 "Librerías MIDI de terceros"
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

## 4. Arquitectura planeada

### 4.1 El transporte: USB vendor-specific, NO USB MIDI

**Hallazgo confirmado con `lsusb -v` sobre el amplificador real** (2026-09-01), después de
que la app no detectara nada con el framework MIDI:

- El dispositivo **entero** se enumera con `bDeviceClass 255` (Vendor Specific).
- **Ninguna** de sus 4 interfaces usa las clases estándar Audio / MIDIStreaming de la
  especificación USB.

Consecuencia dura: **`android.media.midi.MidiManager` nunca podrá detectar el Katana MK2**,
en ningún Android y con cualquier kernel. No es un problema de permisos, de OTG ni de
versión: el amplificador simplemente no se presenta como dispositivo MIDI.

El transporte real es:

| | |
| --- | --- |
| Interfaz | **número 3** — `bInterfaceClass 255`, `bInterfaceSubClass 3`, `bInterfaceProtocol 0` |
| Alternate setting | **0** (el activo por defecto al conectar) |
| Envío (app → amp) | endpoint **`0x03`**, Bulk OUT |
| Recepción (amp → app) | endpoint **`0x84`**, Bulk IN |
| `wMaxPacketSize` | **512 bytes** (USB High Speed) |

Las interfaces 1 y 2 llevan el audio (endpoints isócronos, 112 bytes) y no intervienen en el
protocolo de control; también son vendor-specific, no audio class. La interfaz 0 no tiene
endpoints de datos. La interfaz 3 tiene un **alternate setting 1** que expone los mismos
endpoints como *interrupt* y con IN en `0x85` — por eso hay que filtrar por alternate
setting 0 y no solo por número de interfaz.

**Trocear y reensamblar.** Con 512 bytes por paquete, los mensajes largos —el dump de
memoria son 1920 bytes— necesitan varios `bulkTransfer()` encadenados. Eso es *además* del
framing SysEx `F0…F7`, que es un nivel distinto: un `bulkTransfer` puede devolver un SysEx
partido, o más de uno. `SysExFramer` sigue siendo necesario tal cual.

#### Formato en el cable: paquetes USB-MIDI de 4 bytes

Resuelto leyendo [MrHaroldA/MS3](https://github.com/MrHaroldA/MS3) (Arduino + USB Host
Shield; controla el Boss MS-3 y el Katana), en `MS3.h`: el manejo de paquetes en `receive()`
y la función `setEditorMode()`.

Aunque la interfaz sea vendor-specific, **los datos viajan empaquetados en el formato de
paquete USB-MIDI Class estándar de 4 bytes**:

```
byte 0 : (número de cable << 4) | Code Index Number (CIN)
bytes 1-3 : hasta 3 bytes de datos MIDI reales, rellenados con 0x00 si sobran
```

Un SysEx de N bytes se trocea en paquetes de 3 bytes de payload cada uno. Los CIN relevantes:

| CIN | Significado | Bytes útiles |
| --- | --- | --- |
| `0x4` | SysEx empieza o continúa | 3 |
| `0x5` | SysEx termina con 1 byte | 1 |
| `0x6` | SysEx termina con 2 bytes | 2 |
| `0x7` | SysEx termina con 3 bytes | 3 |

Esto es **capa de transporte, no capa SysEx**: `protocol/` sigue trabajando con mensajes
`F0…F7` limpios, y empaquetar/desempaquetar es responsabilidad de `usb/`.

✅ **Verificado contra el amplificador real** (2026-09-02). Un Identity Request universal
empaquetado obtuvo respuesta bien formada:

```
envío    sysex: F0 7E 7F 06 01 F7
         wire:  04 F0 7E 7F 07 06 01 F7                    (8 bytes escritos)
respuesta wire: 20 bytes
    desempaquetado: F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7
```

Es un Identity Reply estándar, idéntico al que documenta
[HOW.md](reference/TuxKatana/HOW.md). Valida de una sola vez la interfaz y los endpoints
(3, `0x03`/`0x84`), el empaquetado `packUsbMidi`/`unpackUsbMidi`, y que el amplificador
atiende a broadcast (device ID `7F`) e identifica su model id como `33` — el mismo de §5.

#### Handshake obligatorio antes de cualquier comando

También de `MS3.h` / `setEditorMode()`: el amplificador **no responde a nada** hasta recibir
un mensaje de handshake fijo, enviado **dos veces seguidas** con un pequeño delay entre medio
(~4 ms en la librería de referencia):

```
F0 7E 00 06 02 41 3B 03 00 00 00 00 00 00 F7
```

Esos bytes tienen la forma de un Identity *Reply* (`F0 7E <dev> 06 02 <fabricante> <modelo>
…`), y `3B` es el model id del **MS-3**; el del Katana es `33`, así que en este proyecto se
envía con `33`.

⚠️ **Probado el 2026-09-02 con `33`: el amplificador no responde nada**, ni al primer envío,
ni al segundo, ni con una lectura posterior de un segundo. Esto **no** invalida el
transporte: en esa misma sesión el Identity Request sí obtuvo respuesta (más arriba).

**Descartada la hipótesis que lo ligaba al edit mode.** Se pensó que el handshake sería lo
que activaba el modo de edición sin confirmar; ya no: el edit mode se activa con su propio
SET a `7F 00 00 01` y eso está confirmado funcionando (§5).

**Investigación cerrada (2026-09-03): el handshake no responde y no se seguirá indagando.**

Se agotó la última hipótesis. La trama que enviábamos difería del Identity Reply real del
amplificador **en un solo byte**, el de la versión de firmware, así que se probó con los bytes
que el propio amp reporta —byte a byte idéntica a su respuesta—:

```
Identity Reply del amp:  F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7
handshake enviado:       F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7   ← idéntico
```

Escrito dos veces con ~4 ms, 20 bytes en el cable cada vez, **sin respuesta**. Igual que con
los ceros genéricos. El botón «Handshake v-real» de la pantalla de diagnóstico se conserva
para poder reproducirlo, pero **el tema queda cerrado, no en "baja prioridad"**: ningún flujo
implementado lo necesita —transporte, protocolo, los once niveles, los selectores y el dump
funcionan sin él— y el criterio de cierre se fijó antes de ver el resultado. No se retoma
salvo que aparezca una razón concreta para necesitarlo.

Qué queda sin explicar, para quien lo retome algún día: `MS3.h` describe el handshake como
obligatorio para el **MS-3**, con model id `3B`. Puede que el Katana simplemente no lo
necesite, que responda por un endpoint o interfaz que no miramos, o que la trama lleve algo
más que no está en la librería de referencia. Ninguna de esas ramas se ha explorado, y
tampoco hace falta.

**Por qué las apps de escritorio sí funcionan.** En Linux, `snd-usb-audio` trae quirks para
dispositivos Roland/Boss que exponen estas interfaces vendor-specific como puertos ALSA MIDI
—de ahí los `KATANA MIDI 1` / `KATANA DAW CTRL` / `KATANA CTRL` que ven TuxKatana y
katana-midi-bridge—. Android no hace esa traducción hacia su framework MIDI. Por eso las
referencias de `reference/` siguen siendo válidas **para el protocolo** (§5) pero **no** para
el transporte.

**Enfoque descartado.** La primera versión de esta app usó `MidiManager`, `MidiDevice`,
`MidiInputPort` y `MidiOutputPort`. Está descartado por lo anterior. El código sigue en
`midi/` como referencia histórica; no ampliarlo.


---

#### Librerías MIDI de terceros — decidido: no se usa ninguna

La regla original era "nada de librerías MIDI de terceros, porque `android.media.midi` cubre
el caso". Ese razonamiento dejó de valer con el hallazgo de §4.1 —el framework MIDI de
Android no cubre el caso—, así que la restricción se volvió a decidir desde cero en vez de
heredarla.

Se evaluó **[kshoji/USB-MIDI-Driver](https://github.com/kshoji/USB-MIDI-Driver)**, pensada
justamente para dispositivos MIDI no estándar sobre la USB Host API, con soporte explícito
para dispositivos vendor-specific de Roland/Boss.

**Decisión: no se usa** (2026-09-03). El transporte y el protocolo enteros están
implementados y probados contra el amplificador sin ella, así que la decisión ya no es una
predicción sino una constatación:

- Lo que la librería aportaría —enumeración, permisos, `claimInterface`, los bucles de
  `bulkTransfer` y el empaquetado USB-MIDI— son **unas pocas decenas de líneas** en
  `usb/UsbMidiPacket.kt` y `usb/KatanaUsbTransport.kt`, con tests JVM propios.
- **El handshake es específico de Boss** y no lo cubre ninguna librería genérica: habría que
  añadirlo por fuera igualmente.
- Añadiría su propia abstracción de "puertos MIDI" encima del modelo que ya existe
  (`KatanaLink`, `KatanaControl`), que es más pequeño y está hecho a la medida del caso.

No se descarta por principio: si algún día hiciera falta hablar con otros dispositivos MIDI
class-compliant, la evaluación se rehace. Para este amplificador, no aporta.

