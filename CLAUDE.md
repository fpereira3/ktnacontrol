# CLAUDE.md

Guía de trabajo para este repositorio. Léela antes de escribir código.

## 1. Qué es este proyecto

**KTNA Control** — app Android para controlar un amplificador **Boss Katana MK2** por USB.

- Package / applicationId: `dev.alonx3.ktnacontrol`
- minSdk 26, targetSdk/compileSdk 37, Kotlin + Jetpack Compose (Material 3)
- Proyecto personal / hobby. **Sin fines de lucro, sin publicación en Play Store.**
- Referente funcional: *Katana Librarian* / *Boss Tone Studio*, pero reimplementado desde cero.

El *contenido* del control son mensajes **MIDI SysEx** (más algunos PC/CC), documentados en §5.
Pero el *transporte* **no** es USB MIDI estándar: el Katana MK2 es un dispositivo USB
**vendor-specific**, no class-compliant, así que se accede con
`android.hardware.usb.UsbManager` y transferencias bulk. Ver §4.1 — es el hallazgo que más
condiciona la arquitectura de esta app.

## 2. Estado actual

- [usb/](app/src/main/java/dev/alonx3/ktnacontrol/usb/) — **transporte funcionando y
  verificado contra el amplificador real** (§4.1): `KatanaUsbScanner` (enumerar, permiso),
  `KatanaUsbTransport` (`claimInterface(3)`, bulk sobre `0x03`/`0x84`, handshake) y
  `UsbMidiPacket` (empaquetado de 4 bytes, con tests JVM).
- [protocol/](app/src/main/java/dev/alonx3/ktnacontrol/protocol/) — Kotlin puro, con tests
  JVM y **verificado contra el amplificador real**: `MidiBytes` (7 bits), `Address`,
  `RolandSysEx` (GET/SET, checksum, parseo), `SysExFramer`, `RolandExchange`
  (petición↔respuesta correlada por dirección) y `KatanaAddresses`.
- [ui/screens/DebugConnectionScreen.kt](app/src/main/java/dev/alonx3/ktnacontrol/ui/screens/DebugConnectionScreen.kt)
  — pantalla de diagnóstico, que funciona como **monitor en vivo**: se conecta sola al
  enchufar el amp, registra todo lo que llega, y tiene botones para handshake, Identity
  Request, nombre del dispositivo, nombres de preset y edit mode.
- [device/](app/src/main/java/dev/alonx3/ktnacontrol/device/) — `KatanaLink` (puerto estrecho
  para poder testear con un falso) y `KatanaRepository`, de momento con **un solo parámetro**:
  el nivel de reverb, con lectura, escritura optimista y actualización desde los mensajes
  espontáneos. Confirmado con audio real.
- El paquete `midi/` (enfoque `MidiManager`) está **eliminado**; ver §4.1 para el porqué.
- **Falta**: los otros parámetros (ver el proceso en §5), el anti-flood con debounce de
  ~100 ms, el parseo del dump en parámetros, y la UI real de control.

## 3. Build

- AGP 9.3.2, Kotlin 2.2.10, Compose BOM 2026.02.01, Gradle wrapper.
- AGP 9 trae **soporte Kotlin integrado**: por eso solo hay dos plugins
  (`com.android.application` y `org.jetbrains.kotlin.plugin.compose`) y **no** existe alias
  `kotlin-android`. No lo añadas.
- Version catalog en [gradle/libs.versions.toml](gradle/libs.versions.toml).
  **Toda dependencia nueva se declara ahí**, nunca con versión hardcodeada en `build.gradle.kts`.

```bash
./gradlew :app:assembleDebug     # compilar
./gradlew :app:installDebug      # instalar en dispositivo conectado
./gradlew :app:testDebugUnitTest # tests JVM (los importantes: protocolo)
./gradlew :app:lintDebug
```

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
SET a `7F 00 00 01` y eso está confirmado funcionando (§5). Así que el silencio del
handshake es una **duda aparte y sin relación**, de baja prioridad y no bloqueante — hasta
ahora nada de lo implementado lo ha necesitado.

Un detalle que puede ser la pista: la trama que enviamos difiere del Identity Reply real del
amplificador **en un solo byte**, el de la versión de firmware:

```
respuesta real del amp: F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7
handshake que enviamos: F0 7E 00 06 02 41 33 03 00 00 00 00 00 00 F7
                                                    ↑ 06 vs 00
```

Según [HOW.md](reference/TuxKatana/HOW.md) ese campo es la versión de firmware
(`00 06 00 00`). Si alguna vez hace falta que el handshake provoque respuesta, probar con
los bytes de versión reales es lo primero.

**Por qué las apps de escritorio sí funcionan.** En Linux, `snd-usb-audio` trae quirks para
dispositivos Roland/Boss que exponen estas interfaces vendor-specific como puertos ALSA MIDI
—de ahí los `KATANA MIDI 1` / `KATANA DAW CTRL` / `KATANA CTRL` que ven TuxKatana y
katana-midi-bridge—. Android no hace esa traducción hacia su framework MIDI. Por eso las
referencias de `reference/` siguen siendo válidas **para el protocolo** (§5) pero **no** para
el transporte.

**Enfoque descartado.** La primera versión de esta app usó `MidiManager`, `MidiDevice`,
`MidiInputPort` y `MidiOutputPort`. Está descartado por lo anterior. El código sigue en
`midi/` como referencia histórica; no ampliarlo.

### 4.2 Capas

Tres capas, con una regla dura: **el protocolo es Kotlin puro y testeable en JVM**.

```
dev.alonx3.ktnacontrol/
├── MainActivity.kt
├── protocol/          ← Kotlin puro. CERO imports de android.*
│   ├── MidiBytes.kt        conversiones int <-> bytes de 7 bits
│   ├── Address.kt          dirección de 4 bytes + aritmética (suma de offsets)
│   ├── RolandSysEx.kt      header, checksum, construcción de mensajes GET/SET, parseo
│   ├── KatanaAddresses.kt  constantes de direcciones (ver §5)
│   ├── SysExFramer.kt      reensambla F0..F7 desde trozos arbitrarios de bytes
│   └── params/             tablas de parámetros: dirección, tamaño, tipo, rango, etiquetas
├── usb/               ← único lugar que toca android.hardware.usb
│   ├── KatanaUsbScanner.kt     UsbManager.deviceList + permiso + USB_DEVICE_ATTACHED
│   └── KatanaUsbTransport.kt   claimInterface(3), bulkTransfer sobre 0x03 / 0x84,
│                               envío y Flow<ByteArray> de entrada
├── device/            ← estado del amplificador (fuente única de verdad)
│   ├── KatanaRepository.kt   dump de memoria, caché por dirección, coalescing de escrituras
│   └── model/                AmpState, EffectState, Preset, ...
└── ui/
    ├── theme/
    └── screens/              DebugConnectionScreen, AmpScreen, EffectsScreen, PresetsScreen...
```

`SysExFramer` se mueve a `protocol/`: ya no depende de la API de MIDI de Android, solo
convierte un flujo de bytes en mensajes completos, así que gana tests JVM.

**Flujo de datos (unidireccional):**

```
UI (Compose) → ViewModel → KatanaRepository → KatanaUsbTransport → [Katana]
                                ↑                bulk 0x03 ↑↓ 0x84    │
                          StateFlow<AmpState>  ←  parse ← SysExFramer ┘
```

Reglas:

- La UI **nunca** construye bytes SysEx ni conoce direcciones. Habla de parámetros de dominio.
- `KatanaRepository` mantiene la copia local de la memoria del amp (el dump de `60 00 00 00`)
  y la expone como `StateFlow`. Escribir un parámetro = actualizar caché + enviar SysEx.
- El amplificador **también responde con cambios** (perillas físicas, parámetros derivados),
  así que la caché se actualiza tanto desde la UI como desde el endpoint de entrada. Hay que
  evitar bucles de eco: al aplicar un mensaje entrante no se reenvía al amp.
- **Anti-flood**: arrastrar un slider genera decenas de eventos. Coalescer por dirección con un
  debounce de ~100 ms antes de enviar (TuxKatana hace exactamente esto en `lib/anti_flood.py`).
- **Edit mode** (`7F 00 00 01 → 01`): ✅ **confirmado contra el amplificador real**
  (2026-09-02). Sin él, mover una perilla física del Katana **no** genera ningún mensaje;
  con él activo, **sí** los genera y el bucle de lectura los recibe. Es lo que hace que el
  amp reporte cambios derivados, así que **debe activarse como parte del flujo normal de
  conexión**, no solo desde el botón de diagnóstico: sin eso la UI nunca vería lo que se
  toca en el propio amplificador.
  - Es *fire-and-forget* (semántica DT1 de Roland): no devuelve confirmación por SysEx.
  - Altera el estado del amp y TuxKatana advierte de guardar los presets antes, así que
    tratarlo como un ajuste explícito y visible, nunca como algo silencioso — y ofrecer
    siempre la forma de desactivarlo.

**Notas de `android.hardware.usb` concretas para este proyecto:**

- Requiere OTG/USB host. En el manifest hace falta `android.hardware.usb.host`; el
  `<uses-feature android:name="android.software.midi">` **ya no aplica** y hay que quitarlo.
- El `device_filter` de `USB_DEVICE_ATTACHED` sigue siendo válido y ahora es la vía natural
  para lanzar la app al conectar el amplificador. Falta rellenarlo con el vendor-id /
  product-id reales (en **decimal**).
- Secuencia de apertura: `UsbManager.deviceList` para encontrarlo →
  `requestPermission()` si aún no se tiene (**diálogo del sistema, es asíncrono, llega por
  `PendingIntent`**; es un paso que con MIDI no existía) → `openDevice()` →
  `claimInterface(interfaz 3, force = true)` → `bulkTransfer()` sobre `0x03` y `0x84`.
- `bulkTransfer()` es **bloqueante**: va en `Dispatchers.IO`, nunca en el hilo principal. La
  lectura es un bucle propio con timeout que alimenta un `Flow<ByteArray>`; no hay callback
  como el que daba `MidiReceiver`.
- `bulkTransfer()` devuelve el número de bytes transferidos, o `-1` en error o timeout. Un
  timeout sin datos es normal en el bucle de lectura y **no** es una desconexión: solo hay
  que tratarlo como "nada que leer".
- Liberar siempre: `releaseInterface()` y `close()` al desconectar o al destruir el
  `ViewModel`, y atender `USB_DEVICE_DETACHED`.
- **`UsbDevice.getInterfaceCount()` no es `bNumInterfaces`.** Android crea un `UsbInterface`
  por cada par (interfaz, alternate setting): el Katana declara 4 interfaces pero devuelve 7.
  Para contar interfaces reales hay que quedarse con los `id` distintos. Por lo mismo, buscar
  la interfaz de control por `id == 3` **y** `alternateSetting == 0`; filtrar solo por `id`
  puede devolver el alt 1, que es interrupt y ni siquiera tiene el endpoint `0x84`.

## 5. Dónde está documentado el protocolo SysEx

Esta sección describe **qué bytes** se intercambian. Cómo llegan al amplificador es otro
asunto y está en §4.1: el hallazgo del transporte vendor-specific no invalida nada de lo que
sigue, porque el contenido SysEx es el mismo.

Todo vive en `reference/` (ver §7 para las reglas de uso). Por orden de utilidad:

| Fuente | Qué aporta |
| --- | --- |
| [reference/katana-midi-bridge/doc/katana_sysex.txt](reference/katana-midi-bridge/doc/katana_sysex.txt) | **La referencia principal.** 2295 líneas, spec reverse-engineered por Steven Hirsch (v1.7). Formato del mensaje, algoritmo de checksum y el mapa de direcciones por bloques: System, Amplifier Common, Boost/Mod, Delay/FX, Reverb, Color Button Management y "Range Mapping" (los rangos que hay que pedir en un dump). |
| [reference/TuxKatana/HOW.md](reference/TuxKatana/HOW.md) | **La mejor guía de la secuencia de arranque**: handshake de identificación, consulta del nombre del device, lectura de los 8 nombres de preset, edit mode y dump de memoria, con los bytes exactos de ida y vuelta anotados. |
| [reference/TuxKatana/doc/Adresses.txt](reference/TuxKatana/doc/Adresses.txt) | Direcciones de amp/EQ y la tabla de tipos de amplificador (Acoustic/Clean/Crunch/Lead/Brown + variaciones + "sneaky amps"). |
| [reference/TuxKatana/params/*.yaml](reference/TuxKatana/params/) | Tablas de parámetros ya organizadas por efecto (`amplifier.yaml`, `booster.yaml`, `mod.yaml`, `fx.yaml`, `delay.yaml`, `reverb.yaml`), más `midi.yaml` (direcciones SysEx + mapa PC/CC) y `presets_addrs.yaml` (offsets del formato `.tsl`). |
| [reference/katana-midi-bridge/parameters/*.json](reference/katana-midi-bridge/parameters/) | Las mismas tablas en JSON y **con metadatos de tipo y rango** (`dataType`: boolean/enum/byteRange/centeredByteRange, `values`, `display`). Es el mejor modelo para nuestras tablas de parámetros. `ranges.json` lista los bloques a leer en un dump. |
| [reference/TuxKatana/lib/](reference/TuxKatana/lib/) | Implementación de referencia: `midi_bytes.py` (aritmética de 7 bits), `sysex.py` (checksum, construcción/parseo), `memory.py` (caché por dirección), `controller.py` (secuencia completa), `anti_flood.py`, `tsl.py` (presets). |
| [reference/TuxKatana/doc/scheme.txt](reference/TuxKatana/doc/scheme.txt) | Diagrama de capas de TuxKatana; inspiró la arquitectura de §4. |
| [reference/FxFloorboard/midi.xml](reference/FxFloorboard/midi.xml) | Tabla exhaustiva del Katana MK2 en XML (51k líneas, UTF-16) usada por la app de PC. Útil para **verificar rangos y nombres de parámetros** cuando las otras fuentes discrepan. `globalVariables.h` tiene tamaños de patch y `patchRequestDataSize`. |
| [reference/TuxKatana/doc/MIDX_20_KatanaMKIIV1.pdf](reference/TuxKatana/doc/MIDX_20_KatanaMKIIV1.pdf) | Implementación MIDI (PDF). |
| [reference/android-katana-editor/](reference/android-katana-editor/) | ⚠️ **Solo es un mirror de releases `.apk`: no contiene código fuente**, únicamente `README.md` y capturas. No sirve para resolver el transporte USB. Vale como referencia de UX y de notas de usuario (OTG, permisos) — y su README confirma indirectamente §4.1: describe que Android pregunta con qué app abrir el **dispositivo USB**, no que aparezca como dispositivo MIDI. |

### Resumen operativo del protocolo

Suficiente para empezar; los detalles y el mapa completo de direcciones, en las fuentes de arriba.

- Todos los bytes de datos y direcciones son de **7 bits** (`0x00`–`0x7F`). `0x7F + 1 = 0x00`
  en el siguiente byte. Un valor de N bytes es `Σ byte[i] << 7*(n-1-i)`.
- Prefijo Roland: `F0 41 00 00 00 00 33` — Roland `41`, device ID `00`, model ID Katana `00 00 00 33`.
- Comando: `11` = query (GET), `12` = set (SET).
- Luego **dirección de 4 bytes**. En un GET siguen 4 bytes de tamaño; en un SET, los datos.
- Penúltimo byte = **checksum**: `(128 - (suma(dirección + datos) % 128)) % 128`.
- Terminador `F7`.

Direcciones clave:

| Dirección | Significado |
| --- | --- |
| `7E 00 06 01` | Identity Request (mensaje universal, sin prefijo Roland) |
| `10 00 00 00` | Nombre del dispositivo (16 bytes ASCII: `KATANA Mk2`) |
| `10 01 00 00` … `10 08 00 00` | Nombres de los presets 1–8 (16 bytes cada uno) |
| `60 00 00 00` | Inicio del bloque "efectivo" (estado actual). Los primeros 16 bytes son el nombre del preset actual. Dump completo pidiendo tamaño `00 00 0F 00` (1920 bytes), que llega en varios mensajes |
| `7F 00 00 01` | Edit mode / "BTS control mode" (`00` off, `01` on) |
| `7F 00 01 04` | Guardar estado actual en preset (`00 xx`, xx = 01..04) |
| `00 01 00 00` | Preset actual / recall (`00` panel, `01`..`04` canales) |
| `00 02 00 00` | Canal MIDI (`00`..`0F`) |
| `10 00`–`10 04 xx xx` | Bloques Panel / Ch1–Ch4 |

### ⚠️ Las direcciones por parámetro del MK1 NO valen para el Mk2

`katana_sysex.txt` dice en su primera línea **"Boss Katana 100 Combo — v1.7 - 2017-03-23"**:
documenta el **MK1**. Su formato de mensaje, checksum y las direcciones "de sistema"
(`10 xx`, `60 00 00 00`, `7F 00 00 01`) sí valen y están verificadas contra el Mk2. Pero
**el mapa de parámetros por efecto es distinto** y no se puede copiar.

Para direcciones de parámetros, las fuentes de Mk2 son `reference/TuxKatana/params/*.yaml`,
`reference/TuxKatana/doc/Adresses.txt` y `reference/FxFloorboard/midi.xml`.

#### Cómo encontrar la dirección de un parámetro (proceso, no atajo)

El nivel de reverb costó **tres candidatas** y solo el oído las distinguió:

| Candidata | Fuente | Resultado real |
| --- | --- | --- |
| `60 00 06 18` | katana_sysex.txt (MK1) | ❌ nada: ni sonido ni estado; el GET devuelve `07` fijo |
| `60 00 05 48` | reverb.yaml:17, sección `SEND` | ❌ escribir no hace nada; sí **reporta** un valor derivado y retardado |
| **`60 00 06 5B`** | reverb.yaml:19, sección `SEND` | ✅ **lectura y escritura**, cambio audible |

Lecciones, que aplican a los cinco efectos que quedan:

- **Que una fuente de Mk2 liste una dirección bajo `SEND` no basta.** `60 00 05 48` lo está
  y no funciona. En `set_mapping.py:39-43` TuxKatana fusiona `SEND` y `RECV` en el mismo
  mapa, así que esa separación es organizativa, no semántica.
- **La única prueba que vale es el audio.** Checksum correcto, bytes bien formados y una
  respuesta al GET no demuestran nada: `60 00 06 18` cumplía las tres cosas.
- **Prueba mínima**: SET a los extremos (0 y 100) → ¿cambia el sonido? Luego GET a la misma
  dirección → ¿cambió el estado? Y mover la perilla física → ¿reporta por esa dirección?
- **No hay un patrón fiable "escritura baja / reporte alto".** `Adresses.txt` empareja cada
  efecto con una dirección baja y una del bloque `60 00 06 5x`, y para reverb resultó ser la
  **alta** la de control. Las bajas siguen siendo candidatas legítimas a probar primero
  —están citadas como `SEND`— pero **cada parámetro se prueba por separado**:
  Presence `60 00 00 27` / `60 00 06 56`, Boost `60 00 00 12` / `60 00 06 57`,
  Mod `60 00 02 38` / `60 00 06 58`, FX `60 00 04 14` / `60 00 06 59`,
  Delay `60 00 05 06` / `60 00 06 5A`.

También responde a **Program Change** (0–8: BANK_A CH1-4, PANEL, BANK_B CH1-4) y a
**Control Change** (CC16 booster, CC17 mod, CC18 fx, CC19 delay, CC20 reverb, CC7 volumen global),
tabulados en [reference/TuxKatana/params/midi.yaml](reference/TuxKatana/params/midi.yaml).

Al arrancar, la secuencia que usa TuxKatana y que conviene replicar:
Identity Request → nombre del device → nombres de los 8 presets → edit mode ON → dump de memoria.

## 6. Convenciones

### Kotlin

- Kotlin idiomático: `data class` para modelos, `sealed interface` para estados y resultados,
  inmutabilidad por defecto, `val` antes que `var`, funciones de extensión donde aporten,
  argumentos con nombre en llamadas con varios parámetros del mismo tipo.
- Corrutinas y `Flow` para todo lo asíncrono. Nada de callbacks propagándose hacia arriba,
  nada de `GlobalScope`, nada de RxJava. Concurrencia estructurada; el I/O USB en `Dispatchers.IO`
  (`bulkTransfer` bloquea, ver §4.1).
- Estado observable con `StateFlow`; en Compose se consume con `collectAsStateWithLifecycle`.
  Sin `LiveData`.
- En el dominio los valores MIDI se manejan como `Int` en rango 0..127; `ByteArray` solo en el
  borde del transporte. Nada de aritmética con `Byte` con signo — es la fuente clásica de bugs aquí.
- Identificadores y KDoc en inglés; textos de UI siempre en `strings.xml`, nunca hardcodeados.
- Nada de `!!`. Errores de protocolo/conexión modelados como tipos (`sealed`), no como excepciones
  que cruzan capas.

### Compose

- Material 3, tema en `ui/theme/`, sin colores ni dimensiones hardcodeadas en los composables.
- Composables **stateless** con *state hoisting*; el `ViewModel` no baja más allá de la pantalla.
- `modifier: Modifier = Modifier` como primer parámetro opcional.
- Sin efectos secundarios en la composición: `LaunchedEffect` / `DisposableEffect` / `rememberCoroutineScope`.
- Cuidado con la recomposición en controles continuos (perillas, sliders): pasar lambdas estables
  y estado acotado, no el `AmpState` entero.
- `@Preview` en los composables de UI que puedan renderizarse sin amplificador conectado.

### Dependencias — mantener la lista corta

El proyecto vive con Compose BOM + `core-ktx` + `lifecycle-runtime-ktx` + `activity-compose`.
Añadir algo requiere justificación explícita. Aceptable si hace falta:

- `androidx.lifecycle:lifecycle-viewmodel-compose` y `lifecycle-runtime-compose` (para ViewModels y
  `collectAsStateWithLifecycle`).
- `kotlinx-serialization-json` **solo** si el parseo de presets `.tsl` lo justifica; el `org.json`
  de la plataforma puede bastar.

Explícitamente **no**: DI framework (Hilt/Koin — construcción manual basta para este tamaño),
Retrofit/OkHttp/Gson (no hay red), Room (empezar guardando presets como ficheros).

#### Librerías MIDI de terceros — decisión pendiente

La regla anterior era "nada de librerías MIDI de terceros, porque `android.media.midi` cubre
el caso". **Ese razonamiento ya no vale**: el framework MIDI de Android no cubre el caso
(§4.1), así que la restricción hay que volver a decidirla en lugar de heredarla.

Opción sobre la mesa: **[kshoji/USB-MIDI-Driver](https://github.com/kshoji/USB-MIDI-Driver)**,
pensada justamente para dispositivos MIDI no estándar sobre la USB Host API.

- A favor: ya resuelve enumeración, permisos, `claimInterface`, los bucles de `bulkTransfer`
  y el empaquetado/desempaquetado USB-MIDI; incluye soporte explícito para dispositivos
  vendor-specific de Roland/Boss, que es exactamente nuestro caso.
- En contra: es una dependencia grande para lo poco que necesitamos —una interfaz, dos
  endpoints—; añade su propia abstracción de "puertos MIDI" encima de lo que ya modelamos; y
  si el amplificador resulta hablar SysEx crudo en vez de paquetes USB-MIDI, gran parte de
  la librería sobra.

**Decisión: pendiente, y hay que tomarla explícitamente.** El dato que la desbloqueaba
—el formato en el cable— ya está resuelto (§4.1): son paquetes USB-MIDI de 4 bytes.

Con eso sobre la mesa, la **recomendación es no usarla**: empaquetar y desempaquetar esos
4 bytes son unas decenas de líneas, y el handshake previo es específico de Boss, así que
ninguna librería genérica lo cubre — habría que añadirlo por fuera igualmente. Falta
confirmarlo de forma explícita y registrar aquí el porqué.

### Tests

- `protocol/` es Kotlin puro y **debe tener tests JVM**: checksum, aritmética de direcciones,
  conversiones de 7 bits, construcción/parseo de mensajes, reensamblado de SysEx fragmentado.
- `KatanaRepository` se testea contra una interfaz de transporte falsa; el repositorio no depende
  de `android.hardware.usb` directamente sino de una abstracción propia. Esa indirección es
  justo lo que abarata cambios de transporte como el de §4.1.
- Los ejemplos de bytes de [HOW.md](reference/TuxKatana/HOW.md) sirven como vectores de test listos.

## 7. La carpeta `reference/`

Cuatro repos clonados **solo para consulta**:

- `TuxKatana` — app de escritorio Python/GTK4 con el control completo. **AGPL-3.0**.
- `katana-midi-bridge` — puente MIDI en Python; contiene la spec SysEx. **GPL-2.0**.
- `FxFloorboard` — editor de PC en Qt/C++ (`Katana-MK2-FxFloorBoard`). **GPL-3.0**.
- `android-katana-editor` — mirror de APKs, sin fuentes.

Reglas:

- **No forma parte del build** (`settings.gradle.kts` solo incluye `:app`) y está en `.gitignore`.
- **No editar, no refactorizar, no arreglar nada dentro de `reference/`.** Es material de lectura.
- Las tres fuentes con código son **copyleft (GPL/AGPL)**. Usar la **documentación del protocolo**
  (direcciones, formatos, tablas de parámetros: hechos sobre un dispositivo, no código) y
  **reimplementar en Kotlin**. No traducir ficheros de código línea a línea al proyecto.
- Al implementar un bloque de parámetros, dejar en el KDoc la referencia a la fuente concreta
  (fichero y sección) de la que salieron las direcciones.

## 8. Instrucciones generales

- [BACKLOG.md](BACKLOG.md) es el registro de avance del proyecto, con cuatro secciones:
  **Hecho**, **En progreso**, **Por hacer** y **Notas y decisiones técnicas**.
- **Cada vez que completes una tarea de desarrollo**, actualiza `BACKLOG.md` de inmediato:
  mueve el ítem correspondiente a "Hecho" (y quítalo de "En progreso"/"Por hacer") y, si la
  tarea implicó alguna decisión técnica relevante (elección de dirección SysEx, trade-off de
  arquitectura, por qué se descartó una alternativa, etc.), agrégala en "Notas y decisiones
  técnicas".
- Esto se hace **sin que se te tenga que pedir explícitamente en cada sesión** — es parte de
  terminar la tarea, igual que compilar o testear.
