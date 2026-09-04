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
  para poder testear con un falso), `KatanaParameter` (un parámetro de un byte: dirección,
  rango, caché, lectura, escritura optimista con debounce de ~100 ms y actualización desde
  los mensajes espontáneos), `KatanaEnumParameter` (un selector: una opción de una lista de
  valores) y `KatanaRepository`, que los registra. **Los once niveles continuos del panel
  están confirmados con audio real** (`60 00 06 51`–`06 5B`), todos con lectura, escritura y
  actualización desde la perilla física. Los **selectores** —tipo de amplificador, color por
  efecto, on/off por efecto— también están **confirmados**, con una excepción: la variación
  del amplificador se lee en `60 00 06 5C` pero se escribe por el modelo (§4.3, §5).
- El paquete `midi/` (enfoque `MidiManager`) está **eliminado**; ver §4.1 para el porqué.
- **Falta**: el parseo del dump en parámetros y la UI real de control. La pantalla de diagnóstico tiene dos secciones tras un menú
  hamburguesa —**Logs** (acciones + consola) y **Sliders** (sección de amplificador + una
  tarjeta por efecto)—, con el toggle de Edit Mode visible en las dos.

## Visión de alcance

Esto es la meta de **producto final**, no una lista de tareas listas para ejecutar — el
roadmap de bloques de trabajo está en `BACKLOG.md`, "Por hacer".

El objetivo final del proyecto es una **alternativa completa a Boss Tone Studio**: controlar
el Katana Mk2 al 100% desde el celular por USB, sin necesitar nunca conectar el amplificador
a una PC. Más allá de lo ya implementado (niveles, on/off, color, canal activo), eso incluye:

1. **Selección de TIPO de efecto por slot de color**, no solo nivel/on-off/color. Cada efecto
   (Booster, Mod, FX, Delay, Reverb) tiene un catálogo fijo de modelos ya definidos por Boss
   (p. ej. Booster: Clean Boost, T-Screamer/TubeScreamer, Distortion tipo RAT, etc.) — el
   catálogo ya existe en el firmware / Boss Tone Studio, no hay que modelarlo desde cero, solo
   descubrir sus direcciones e IDs. **El color (verde/rojo/amarillo) es un slot que guarda qué
   tipo de efecto tiene asignado, no un efecto en sí mismo.** Cada slot de color tiene además
   sus propios parámetros específicos según el tipo asignado (un Booster tipo TubeScreamer
   tendrá Gain/Level/Tone; un tipo distinto puede tener parámetros distintos). Esto es **por
   preset**: el booster asignado al color rojo en el preset 1A puede ser distinto al asignado
   al color rojo en el preset 2A.
2. **Parámetros internos completos de cada tipo de efecto en cada slot** (Gain, Level, Tone,
   Bottom, etc. según corresponda al tipo).
3. **Controles sin perilla física en el panel** pero presentes en Boss Tone Studio: Noise
   Gate, Solo, Contour, posición IN/OUT de EQ1 y EQ2 (antes o después del preamp), selección
   de cadena de efectos (chain).
4. **Guardado de presets**: escribir el estado actual editado a un canal específico (p. ej.
   1A), equivalente a grabar un preset desde el panel del amplificador pero hecho desde la
   app.
5. **Import/export de archivos `.tsl`** (formato de preset de Boss Tone Studio), usando los
   offsets ya documentados en
   [reference/TuxKatana/params/presets_addrs.yaml](reference/TuxKatana/params/presets_addrs.yaml).
6. **UI real de control** reemplazando la pantalla de diagnóstico actual, con pantallas por
   dominio, una vez que la investigación de protocolo de los puntos anteriores esté
   suficientemente avanzada.

El desarrollo sigue siendo **incremental**, no un salto a por todo esto de golpe: cada pieza
se investiga y se confirma con audio o con el amplificador real antes de darla por buena,
exactamente con la misma disciplina ya aplicada a reverb y al resto de efectos (§5) — nunca
asumir una dirección por analogía sin probarla.

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
│   ├── AmpType.kt          tablas de valores: AmpType, AmpCategory, EffectColor
│   ├── LevelScale.kt       crudo ↔ mostrado de un nivel continuo
│   ├── MemoryDump.kt       los trozos del dump, indexados por dirección
│   └── params/             tablas de parámetros: dirección, tamaño, tipo, rango, etiquetas
├── usb/               ← único lugar que toca android.hardware.usb
│   ├── KatanaUsbScanner.kt     UsbManager.deviceList + permiso + USB_DEVICE_ATTACHED
│   └── KatanaUsbTransport.kt   claimInterface(3), bulkTransfer sobre 0x03 / 0x84,
│                               envío y Flow<ByteArray> de entrada
├── device/            ← estado del amplificador (fuente única de verdad)
│   ├── KatanaControl.kt      base sellada + KatanaParameter (continuo) y
│   │                         KatanaEnumParameter (selector). Ver §4.3
│   ├── KatanaRepository.kt   caché por dirección, coalescing, carga desde el dump
│   └── model/AmpState.kt     instantánea legible de los 24 parámetros
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
- **Al conectar se lee el dump, no 24 GET.** Ver §4.4.
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


### 4.3 Dos tipos de control: continuo y selector

Casi todo lo que la app controla es **un byte en una dirección** —salvo el canal activo, que
son 2 (§5.1)—, así que todo comparte la misma maquinaria: caché, GET, SET optimista, regla
anti-eco, con un `byteWidth` configurable (por defecto 1) para ese único caso. Eso vive en
`device/KatanaControl.kt`, una clase sellada de la que cuelgan exactamente dos formas:

| | `KatanaParameter` | `KatanaEnumParameter` |
| --- | --- | --- |
| Valores | una `LevelScale` (crudo ↔ mostrado) | una `List<Int>` con huecos |
| Fuera de rango | **clampea** al extremo más cercano | **rechaza**: ni envía ni toca la caché |
| Debounce | ~100 ms, se arrastra | ninguno, es un toque |
| Ejemplos | los once niveles del panel | amp type, color, on/off |

Las dos diferencias salen de la misma raíz: **los valores de un selector no son contiguos**.
`AmpType` va del `0x00` al `0x20` saltándose el `0x19`, así que "el valor legal más cercano"
no significa nada y adivinar sería peor que no hacer nada. Importa sobre todo en la dirección
de entrada: si el amplificador reporta un valor que no está en la tabla, lo correcto es
**dejar la UI como está** y que el valor aparezca en el log, no mover el control a un vecino
inventado. El debounce se cae solo: sin arrastre no hay avalancha que coalescer, y esperar
100 ms para un botón solo añadiría lag.

**Un nivel tiene dos caras y hay que usar la correcta.** `state` / `set` hablan en **bytes
crudos**, como todo control; `displayValue` / `setLevel` hablan en **lo que se muestra**. No
son el mismo número en los cinco niveles de efecto, donde el crudo va desplazado en uno
porque su `0` significa Off — ver `LevelScale`. Cualquier cosa que mueva un slider quiere la
segunda cara; el log y los tests razonan en bytes, que es la única forma honesta de hablar de
lo que el amplificador guarda.

Las tablas de valores (`AmpType`, `AmpCategory`, `EffectColor`) y `LevelScale` viven en
`protocol/`, son Kotlin puro y tienen tests JVM. La UI las consume por sus enums (`LevelId`, `EffectId`,
`SelectorId`) y **nunca ve una dirección**, igual que con los niveles.

### 4.4 Poblar el estado: un dump en vez de 24 GET

Al abrir la conexión hay que averiguar en qué estado está el amplificador. La primera versión
mandaba **un GET por control** —24 peticiones en serie, cada una esperando hasta 800 ms su
propia respuesta— porque era lo simple mientras había un solo parámetro. Con veinticuatro es
absurdo: el amp contesta el dump de `60 00 00 00` con **todos** los rangos ocupados de golpe,
y todas las direcciones que la app controla viven dentro de él.

```
KatanaRepository.loadFromDump()
  → GET 60 00 00 00, tamaño 00 00 0F 00
  ← varios mensajes Roland, cada uno con su dirección base y su longitud
    (se recogen hasta que el amp se calla ~250 ms, no una ventana fija)
  → MemoryDump.from(mensajes)          protocol/, puro, con tests JVM
  → por cada control: dump.byteAt(su dirección) → applyDumpValue
  → lo que el dump no cubra: un GET individual, en serie, como respaldo
  → AmpState.from(dump)                instantánea legible para el log y la UI futura
```

**El dump no es un bloque contiguo.** Son varios mensajes, cada uno con su base: el
amplificador se salta los huecos donde no hay parámetros. Ni el número de mensajes ni el
total de bytes son fijos —HOW.md traza 6, el Mk2 real dio 8— así que `MemoryDump` es una
**búsqueda por dirección**, no un índice sobre un array plano. Una dirección que no aparece
es una respuesta normal ("el amp no mandó ese rango"), no un error: queda en `null` y su
control cae al GET de respaldo.

Dos tipos, y cada uno tiene su razón de existir:

- **`MemoryDump`** (protocol/, puro) responde "¿qué byte hay en esta dirección?". La
  aritmética es base 128, que es exactamente como el amp coloca los bytes.
- **`AmpState`** (device/model/) es la instantánea de dominio: los 24 parámetros con nombre,
  ya en unidades de presentación —el desplazamiento de los niveles de efecto se aplica aquí—
  y con `null` donde el dump no llegó. Lo consume hoy el log de diagnóstico; lo consumirá la
  UI real.

**Aplicar el dump a los controles no pasa por `AmpState`**, sino que recorre la lista de
controles del repositorio preguntando por la dirección de cada uno. Es deliberado: así añadir
un parámetro no puede olvidarse de actualizar el volcado, porque no hay nada que actualizar.

**La recogida termina por silencio, no por ventana fija.** No se sabe de antemano cuántos
mensajes vendrán, así que `sendAndCollectUntilQuiet` corta tras ~250 ms sin nada nuevo (el
hueco observado entre mensajes es de ~30 ms) con un tope absoluto de 3 s por si no llega
nada. Con la ventana fija los datos estaban a los 275 ms pero los controles no se poblaban
hasta los 3 s — **más lento que los 24 GET a los que sustituye**, que tardaban ~540 ms.

✅ **Verificado contra el amplificador (2026-09-03):** 8 mensajes, 1860 B, **24 de 24
controles poblados del dump y 0 GET de respaldo**, con los mismos valores que daba la lectura
individual. El bloque de perillas `06 50`–`06 5B` cae en el trozo que empieza en
`60 00 05 53`, a partir del offset 125.

⚠️ **También se relee al cambiar de canal, no solo al conectar — sin confirmar todavía**
(ver BACKLOG.md, "Pendiente por probar"). Cada canal (1A–4A, 1B–4B, PANEL) tiene sus propios
valores para todo: niveles, modelo de amplificador, colores, on/off, tipos de efecto. Sin
releer al cambiar de canal la app seguiría mostrando los del canal anterior, que es peor que
mostrar nada — parecería que el amplificador dice una cosa cuando dice otra. La implementación
observa `KatanaRepository.channel.state` con `collectLatest` y un margen de 300 ms antes de
relanzar `loadFromDump()`: el margen coalesce los cambios rápidos (1A→2A→3A solo recarga una
vez, para 3A) y le da tiempo al amplificador a terminar el cambio de canal antes de
preguntarle en qué estado quedó. No hay bucle porque el canal vive en `00 01 00 00`, fuera del
dump, así que recargar no lo reescribe.

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
| `60 00 00 00` | Inicio del bloque "efectivo" (estado actual). Los primeros 16 bytes serían el nombre del preset actual — ⚠️ pero lo
  que devolvió el amplificador ahí no se parece a un nombre; ver BACKLOG. Dump completo pidiendo tamaño `00 00 0F 00` (1920 bytes), que llega en varios mensajes |
| `7F 00 00 01` | Edit mode / "BTS control mode" (`00` off, `01` on) |
| `7F 00 01 04` | Guardar estado actual en preset (`00 xx`, xx = 01..04) |
| `00 01 00 00` | Canal/preset activo (valor de **2 bytes**; `00` panel, `01`..`08` canales). Ver §5.1 |
| `00 02 00 00` | Canal MIDI de recepción (`00`..`0F` = MIDI 1..16) |
| `10 00`–`10 04 xx xx` | Bloques Panel / Ch1–Ch4 |

### 5.1 El canal/preset activo: `00 01 00 00` (✅ implementado y confirmado)

Investigación documental del 2026-09-03, implementada el mismo día como
`KatanaAddresses.ACTIVE_CHANNEL` / `KatanaRepository.channel`, un `KatanaEnumParameter` más de
9 valores — ver §4.3 y la nota de "generalizar `KatanaControl` a `byteWidth`" más abajo.

✅ **Confirmado contra el amplificador real en las dos direcciones** (2026-09-03): elegir un
canal en la app cambia el canal real del amplificador, en los 8 canales (1A–4A, 1B–4B) más
Panel; y cambiar de canal físicamente en el amplificador actualiza el selector de la app. Lo
que sigue documenta la investigación que llevó hasta ahí, con cita de archivo y línea.

**Un solo valor de 2 bytes, no un banco + un canal.** No existe dirección separada de banco
A/B: los ocho canales son valores consecutivos de un único campo en `00 01 00 00`.

| Valor SysEx | Canal físico | Program Change |
| --- | --- | --- |
| `00 00` | PANEL | 4 |
| `00 01`…`00 04` | BANK A, CH1–CH4 | 0, 1, 2, 3 |
| `00 05`…`00 08` | BANK B, CH1–CH4 | 5, 6, 7, 8 |

⚠️ **Las dos numeraciones no coinciden**: en SysEx el panel es el `00` y los canales van
`01`..`08` seguidos; en Program Change el panel es el **4**, en medio de los dos bancos. No se
puede reutilizar el mismo índice para las dos vías.

Evidencia, por fuente:

- [reference/TuxKatana/widgets/switcher.py:75-88](reference/TuxKatana/widgets/switcher.py) —
  al recibir `channel-changed` con `ch_num`, `ch_num <= 4` indexa los botones del banco A y el
  resto `ch_num - 5` los del banco B. Es la prueba de que un solo número recorre 1..8.
- [reference/TuxKatana/params/config.yaml:8-17](reference/TuxKatana/params/config.yaml) — los
  ocho valores (`00 01 7e` … `00 08 77`, o sea dato + checksum), y cada uno **comentado con su
  Program Change equivalente**: `CH_5` lleva `# "Program_Change:BANK_B:CH_1"`. De ahí sale la
  tabla de arriba.
- [reference/TuxKatana/params/midi.yaml:23-34](reference/TuxKatana/params/midi.yaml) — la tabla
  PC completa: BANK_A 0–3, PANEL 4, BANK_B 5–8. Confirmada tal cual.
- [reference/katana-midi-bridge/globals.py:14-15](reference/katana-midi-bridge/globals.py) —
  `CURRENT_PRESET_ADDR = (0x00,0x01,0x00,0x00)` con `CURRENT_PRESET_LEN = 0x02`.
- [reference/TuxKatana/HOW.md:83-85](reference/TuxKatana/HOW.md) — "Channel Change: send 3
  MIDIBytes at Address('00 01 00 00')", o sea 2 bytes de dato + checksum.
- [reference/FxFloorboard/midi.xml:943-951](reference/FxFloorboard/midi.xml) — fuente **de
  Mk2**: `LSB 01 "system 2" → LSB 00 "page 1" → PARAM 00 "patch number"`, con `desc="CH"`. Es
  decir, `00 01 00 00` se llama "patch number" también en el editor oficialoide.

**Es de 2 bytes, no de 1.** Tres fuentes independientes coinciden (`CURRENT_PRESET_LEN = 0x02`,
"3 MIDIBytes" de HOW.md, y el `00 0N` de config.yaml). Hasta esta implementación, los
controles de `device/` escribían y leían siempre **un** byte; `KatanaControl` se generalizó
con un `byteWidth: Int = 1` (por defecto 1, sin tocar los 24 controles existentes) en vez de
crear un tipo aparte solo para este caso — ver §4.3 y BACKLOG.md. ✅ **Confirmado**: el SET de
2 bytes funciona tal cual contra el amplificador real, así que la duda de si 1 byte también
habría bastado queda cerrada sin necesidad de probarlo — el checksum no desempataba —el byte
extra es `00` y no altera la suma— pero ya no hace falta, la implementación manda los 2.

**Se puede leer, por dos vías distintas.** No hacía falta adivinar el canal:

- **GET explícito.** `katana_sysex.txt:180-198` documenta "[Determine Current Preset]": QUERY a
  `00 01 00 00` con longitud `00 00 00 02` → respuesta `00 xx`. Es MK1, pero está en el bloque
  de direcciones de sistema que sí vale para el Mk2 (ver el aviso de abajo). Exige edit mode.
  Es la vía que usa el GET de respaldo de `KatanaRepository.loadFromDump`, porque el canal no
  vive en el dump — ver más abajo.
- **Reporte espontáneo.** ✅ **Confirmado**: cambiar de canal con los botones físicos del
  amplificador actualiza el selector de la app al instante, igual que con el resto de
  selectores. `reference/TuxKatana/lib/controller.py:95-98` documentaba esto mismo: cualquier
  mensaje entrante cuya dirección sea `00 01 00 00` se emite como `channel-changed`.

**El canal NO está en el dump.** El dump va de `60 00 00 00`; `00 01 00 00` es otra región.
Curiosamente TuxKatana **nunca hace el GET**: deduce el canal inicial comparando el nombre del
preset actual del dump contra la lista de los ocho nombres
([presets.py:59-62](reference/TuxKatana/widgets/presets.py) +
[memory.py:97-99](reference/TuxKatana/lib/memory.py)). No consta si es porque el GET les
falló o simplemente porque ya tenían el dump a mano.

⚠️ **`Adresses.txt:140-149` etiqueta este bloque como `MIDI_CHANNELS: # write` y eso es un
nombre equivocado, dos veces.** El canal MIDI de recepción es `00 02 00 00`
(`katana_sysex.txt:1984-1985`, y `midi.xml:953-971` con sus 16 valores); `00 01 00 00` es
"Preset Recall" (`katana_sysex.txt:1979-1980`). Y el `# write` describe lo que hace TuxKatana,
no lo que permite el amplificador: su propio `controller.py` recibe mensajes por esa dirección.
Es otro caso de lo ya visto con `60 00 06 51`: la anotación de una fuente no es un dato.

### 5.2 Tipo de efecto por slot de color (✅ confirmado en Booster)

Investigación documental del 2026-09-03, implementada y **confirmada el mismo día en Booster**.

✅ **De las dos candidatas, la que acepta la escritura es la de "tipo activo"** (la "baja":
`60 00 00 11`). Un SET ahí cambia el sonido, se corresponde con el color encendido en el
panel, y la sincronización va en las dos direcciones: cambiar el color o el tipo en el
amplificador actualiza la app, y al revés. Las direcciones `06 24`–`06 26` por color **no**
hicieron falta para escribir; se conservan registradas porque vienen en el dump y reportan,
así que dan gratis el contenido de los tres slots.

⚠️ **Mod, FX, Delay y Reverb usan la misma dirección "gemela" y están todos implementados,
pero sin confirmar** (ver BACKLOG.md, "Pendiente por probar"): Mod `60 00 01 01`, FX
`60 00 03 01`, Delay 1 `60 00 05 01`, Reverb `60 00 05 41`.

**El color es un slot con su propio tipo, y hay una dirección por color.** No es una sola
dirección que cambie de significado: cada efecto tiene **tres** direcciones de tipo —una por
color— **más** una dirección de "tipo activo" que refleja el color seleccionado.

| Efecto | Tipo activo | Verde | Rojo | Amarillo | Color activo (ya implementado) |
| --- | --- | --- | --- | --- | --- |
| Booster | `60 00 00 11` | `60 00 06 24` | `06 25` | `06 26` | `60 00 06 39` |
| Mod | `60 00 01 01` | `60 00 06 27` | `06 28` | `06 29` | `60 00 06 3A` |
| FX | `60 00 03 01` | `60 00 06 2A` | `06 2B` | `06 2C` | `60 00 06 3B` |
| Delay 1 | `60 00 05 01` | `60 00 06 2D` | `06 2E` | `06 2F` | `60 00 06 3C` |
| Reverb | `60 00 05 41` | `60 00 06 30` | `06 31` | `06 32` | `60 00 06 3D` |
| Delay 2 | `60 00 05 21` | `60 00 06 33` | `06 34` | `06 35` | — |
| RV/DD2 layer | — | `60 00 06 36` | `06 37` | `06 38` | — |

El bloque `06 24`–`06 38` es contiguo y perfectamente regular: 3 colores × 7 familias.

**Dos fuentes de Mk2 coinciden** en las quince primeras:
[booster.yaml:11-13](reference/TuxKatana/params/booster.yaml) (`bo_type_G/R/Y`), y sus
equivalentes en `mod.yaml:4-6`, `fx.yaml:4-6`, `delay.yaml:25-27`, `reverb.yaml:4-6`; y
[midi.xml:43567-43959](reference/FxFloorboard/midi.xml), que nombra cada dirección con su
efecto y su color explícitos (`desc="Booster" customdesc="GREEN"`). **Delay 2 (`06 33`–`06 35`)
solo lo da `midi.xml`**: TuxKatana no implementa el segundo delay (su `reverb.yaml:38` lo deja
en `Unimplemented: # Delay_2 < !!!`). Las de modo RV/DD2 (`06 36`–`06 38`) son los
`re_mode_G/R/Y` de `reverb.yaml:9-11`.

**El "tipo activo" refleja el color, y hay evidencia observacional de ello.**
[Adresses.txt:72-74 y 85-87](reference/TuxKatana/doc/Adresses.txt) anota, para su propio
amplificador, qué valor toma la dirección de tipo activo con cada color:
`60 00 00 11: [0A|0B|0E]` (Boost verde/rojo/amarillo = Blues Drive / Over Drive / Distortion)
y `60 00 01 01 -> [1D|14|13]` (Mod = Chorus / Flanger / Phaser). Es decir: el autor cambió de
color y vio cambiar esa dirección. Lo que **ninguna fuente dice** es si escribir en el tipo
activo persiste en el slot del color, o si hay que escribir en `06 24`–`06 32`.

**Los parámetros internos se estructuran de dos maneras distintas**, y la diferencia importa
para el diseño:

- **"DSP simple" — Booster, Delay, Reverb: un único juego de parámetros fijo**, con el mismo
  significado para todos sus tipos. Cambiar de tipo cambia el sonido, no el mapa de memoria.
  Booster (`midi.xml:37109-37304`, y `booster.yaml:2-10`):

  | Dirección | Parámetro | Rango | Estado |
  | --- | --- | --- | --- |
  | `60 00 00 10` | On/Off | `00`/`01` | ✅ confirmado |
  | `60 00 00 11` | Type | 23 valores, ver abajo | ✅ confirmado |
  | `60 00 00 12` | Drive | `00`..`78` (0..120) | ⚠️ implementado, sin confirmar |
  | `60 00 00 13` | Bottom | `00/64` mostrado `-50..+50` | ⚠️ implementado, sin confirmar |
  | `60 00 00 14` | Tone | `00/64` mostrado `-50..+50` | ⚠️ implementado, sin confirmar |
  | `60 00 00 15` | Solo Sw | `00`/`01` | ⚠️ implementado, sin confirmar |
  | `60 00 00 16` | Solo Level | `00/64` = 0..100 | ⚠️ implementado, sin confirmar |
  | `60 00 00 17` | Effect Level | `00/64` = 0..100 | ⚠️ implementado, sin confirmar |
  | `60 00 00 18` | Direct Mix | `00/64` = 0..100 | ⚠️ implementado, sin confirmar |
  | `60 00 00 19`–`1E` | Custom Type + Bottom/Top/Low/High/Character | pedal "custom" | ❌ sin implementar (a propósito) |

  ⚠️ **Implementado el 2026-09-04, pendiente de confirmar con audio** (ver BACKLOG.md,
  "Pendiente por probar"): `KatanaAddresses.BOOST_DRIVE`/`BOOST_BOTTOM`/`BOOST_TONE`/
  `BOOST_SOLO_ENABLED`/`BOOST_SOLO_LEVEL`/`BOOST_EFFECT_LEVEL`/`BOOST_DIRECT_MIX`, todas con
  dos fuentes de Mk2 de acuerdo (`booster.yaml:4-9` y `midi.xml:37109-37304`).
  `60 00 00 12` estaba antes documentada como `BOOST_LEVEL_LOW`, "alternativa baja de
  [BOOST_LEVEL], sin probar" — con el bloque interno completo entendido, no era una
  alternativa a la perilla del panel, sino este mismo parámetro de Drive.

  **Bottom y Tone usan una escala centrada que resultó no necesitar tocar `LevelScale`**: el
  `rawOffset` que ya existía para las escalas `direct`/`offThenOneBased` es exactamente lo que
  hace falta para `raw = display + 50` — solo hizo falta una tercera factoría,
  `LevelScale.centered(radius)`, sin cambiar la clase.

  Custom Type y sus cinco parámetros (`60 00 00 1A`–`1E`) quedan **sin implementar a
  propósito**: es el modo "pedal custom", con su propio sub-catálogo, y menos prioritario.

- **"DSP complejo" — Mod y FX: cada tipo tiene su propio bloque contiguo de direcciones.**
  `midi.xml:37920-40155` los enumera: tras la cabecera común (`01 00` On/Off, `01 01` Type),
  vienen Touch Wah (`01 02`–`01 08`), Auto Wah (`01 09`–`01 0F`), Pedal Wah (`01 10`–`01 15`),
  Compressor (`01 16`–`01 1A`), Limiter (`01 1B`–`01 20`), Graphic EQ (`01 21`…), etc. El
  bloque **desborda** `60 00 01 xx` y sigue en `60 00 02 xx` (por eso `midi.xml` etiqueta las
  dos LSB como "FX1"); FX hace lo mismo en `60 00 03 xx`–`60 00 04 xx`.

Esa división no es una interpretación propia: `katana-midi-bridge` la tiene explícita en
[color_assign.json](reference/katana-midi-bridge/parameters/color_assign.json), con
`"dspSimple": ["boost","delay","reverb"]` y `"dspComplex": ["mod","fx"]`, y sus dos ficheros
`simple_dsp.json` / `complex_dsp.json` reflejan las dos formas: el primero da un juego plano
de parámetros por efecto, el segundo un `baseAddr` **por tipo**.

⚠️ **Ese JSON es del MK1 y sus direcciones no valen** (mismo aviso de siempre), pero **la
estructura sí transfiere y eso está triplemente confirmado**: `simple_dsp.json` describe el
booster con `length: 9` y los offsets `onOff, select, drive, bottom, tone, solo, …`, con
`bottom` y `tone` como `centeredByteRange` de `-50..+50` — exactamente los 9 parámetros y los
mismos rangos centrados que `midi.xml` da para el Mk2, solo que en `60 00 00 30` en vez de
`60 00 00 10`. Los desplazamientos entre mapas **no son uniformes** (booster `-0x20`, mod
`-0x40`), así que la estructura se copia y las direcciones no.

**Catálogo de tipos de Booster — 23 valores, dos fuentes de Mk2 de acuerdo byte a byte**
([booster.yaml:20-43](reference/TuxKatana/params/booster.yaml) y
[midi.xml:43568-43590](reference/FxFloorboard/midi.xml)):

`00` Mid Boost · `01` Clean Boost · `02` Treble Boost · `03` Crunch OD · `04` Natural OD ·
`05` Warm OD · `06` Fat DS · `08` Metal DS · `09` Oct Fuzz · `0A` Blues Drive · `0B` Over
Drive · `0C` TubeScreamer · `0D` Turbo OD · `0E` Distortion · `0F` Rat · `10` GuV DS ·
`11` DST+ · `12` Metal Zone · `13` '60s Fuzz · `14` Muff Fuzz · `15` HM-2 · `16` Metal Core ·
`17` Centa OD

⚠️ **El `07` no existe**, y falta en las **dos** fuentes — no es un error de transcripción.
Es el mismo caso que el `0x19` de `AmpType`: un selector con huecos, que es justo lo que
`KatanaEnumParameter` está hecho para rechazar en vez de adivinar.

Los otros catálogos, también con dos fuentes de acuerdo: **Delay** 11 tipos `00`..`0A`
(`delay.yaml:36-47`, `midi.xml:43841-43851`; única discrepancia, cosmética: el `08` es "Tap
Echo" en uno y "Tape" en el otro); **Reverb** 7 tipos `00`..`06` (`reverb.yaml:24-31`,
`midi.xml:43880-43886`); **Mod y FX** comparten el mismo catálogo de 30 tipos con huecos
(`mod.yaml:11-42` = `fx.yaml:11-42`); **RV/DD2 layer** 3 modos `00` Delay / `01` Dly+Rev /
`02` Reverb (`midi.xml:43946-43949`, `reverb.yaml:33-36`).

**Es por preset, y ya lo estamos leyendo sin saberlo.** Todo el bloque vive en
`60 00 xx xx`, el mismo "bloque efectivo" del dump (§4.4). La aritmética lo confirma: el trozo
que empieza en `60 00 05 53` y del que ya sacamos las perillas `06 50`–`06 5B` (offsets
125–136) cubre también `06 24`–`06 38` (offsets 81–101). O sea que **el dump que la app ya
pide contiene los tipos por color de los cinco efectos**; solo falta interpretarlos.

**Lo que las fuentes no responden:**

1. ✅ **Resuelto: se escribe en el tipo activo, no en el slot de color.** Era la duda
   principal y ninguna fuente la documentaba. El precedente que obligaba a probarlo —`06 5C`,
   del mismo bloque `06 xx`, que resultó de solo lectura— no se repitió: aquí la que manda es
   la "baja", al revés que en la variación. **Dos direcciones plausibles, y solo el hardware
   dice cuál**: es el mismo patrón que con el reverb y con Gain.
2. ✅ **Resuelto: extraído entero** (2026-09-04). Eran 237 direcciones por efecto, no ~256.
   Sigue siendo cierto que **solo hay una fuente de Mk2** —`mod.yaml` y `fx.yaml` no listan
   ningún parámetro interno—, así que todo lo de abajo sale de `midi.xml` y de nada más. Ver
   el apartado siguiente.
3. Si cambiar el tipo de un color **resetea** los parámetros de ese slot a los de fábrica del
   nuevo tipo, o los conserva.

#### El mapa de parámetros internos de Mod y FX (extracción documental, 2026-09-04)

Extracción completa desde `midi.xml`, **sin implementar nada todavía**: el objetivo es que
cablear cada tipo después sea mecánico en vez de volver a leer 2.300 líneas de XML cada vez.
Nada de esto está probado contra el amplificador.

**Cómo se extrae (el truco que lo vuelve mecánico).** Cada `<DATA>` del bloque lleva un
atributo `desc` con un **prefijo por tipo**, y los nodos de un mismo tipo son consecutivos:

```
39763: <DATA value="03" name="MOD" desc="MOD PH:"  customdesc="Type">
39792: <DATA value="0B" name="MOD" desc="MOD FL:"  customdesc="Rate">
```

Así que agrupar por `desc` da los bloques ya delimitados, sin adivinar dónde acaba uno y
empieza el siguiente. El `customdesc` es el nombre del parámetro y el `<PARAM>` hijo su rango.

**Regla FX = Mod con el tercer byte +2.** No hace falta extraer FX por separado:

> `dirección FX = dirección Mod + 0x0200` — es decir, `60 00 01 xx`→`60 00 03 xx` y
> `60 00 02 xx`→`60 00 04 xx`.

✅ **Verificado mecánicamente sobre los 237 nodos de cada bloque**, no inferido de unas
muestras: comparando `(LSB relativa, 4.º byte, tipo, nombre del parámetro)` los dos bloques
salen **idénticos**, con 14 diferencias que son **solo de etiqueta** y ninguna de dirección
(ver las dos rarezas de la fuente al final). Mod ocupa `midi.xml:37918-40155`, FX
`midi.xml:40156-42393`.

##### Índice de los 31 bloques

En el **mismo orden que el catálogo de `ModFxType`** — comprobado entrada por entrada contra
`ModFxType.kt`, no supuesto. Tras la cabecera común (`01 00` On/Off, `01 01` Type):

| Tipo (`ModFxType`) | Bloque Mod | Bloque FX | Params | `desc` |
| --- | --- | --- | --- | --- |
| Touch Wah `00` | `60 00 01 02`–`01 08` | `60 00 03 02`–`03 08` | 7 | `MOD TW` |
| Auto Wah `01` | `60 00 01 09`–`01 0F` | `60 00 03 09`–`03 0F` | 7 | `MOD AW` |
| Pedal Wah `02` | `60 00 01 10`–`01 15` | `60 00 03 10`–`03 15` | 6 | `MOD SWAH` |
| Compressor `03` | `60 00 01 16`–`01 1A` | `60 00 03 16`–`03 1A` | 5 | `MOD ACS` |
| Limiter `04` | `60 00 01 1B`–`01 20` | `60 00 03 1B`–`03 20` | 6 | `MOD LM` |
| Graphic EQ `06` | `60 00 01 21`–`01 2B` | `60 00 03 21`–`03 2B` | 11 | `MOD GEQ` |
| Parametric EQ `07` | `60 00 01 2C`–`01 36` | `60 00 03 2C`–`03 36` | 11 | `MOD PEQ` |
| Guitar Sim `09` | `60 00 01 37`–`01 3B` | `60 00 03 37`–`03 3B` | 5 | `MOD GS` |
| Slow Gear `0A` | `60 00 01 3C`–`01 3E` | `60 00 03 3C`–`03 3E` | 3 | `MOD SG` |
| Wave Synth `0C` | `60 00 01 3F`–`01 46` | `60 00 03 3F`–`03 46` | 8 | `MOD WSY` |
| Octave `0E` | `60 00 01 47`–`01 49` | `60 00 03 47`–`03 49` | 3 | `MOD OC` |
| ⚠️ Pitch Shifter `0F` | `60 00 01 4A`–`01 58` | `60 00 03 4A`–`03 58` | 13 | `MOD PS` |
| ⚠️ Harmonist `10` | `60 00 01 59`–`01 7B` | `60 00 03 59`–`03 7B` | 9 + 24 escala | `MOD HR` |
| ⚠️ Acu Processor `12` | `60 00 01 7C`–`02 02` | `60 00 03 7C`–`04 02` | 7 | `MOD AC` |
| Phaser `13` | `60 00 02 03`–`02 0A` | `60 00 04 03`–`04 0A` | 8 | `MOD PH` |
| Flanger `14` | `60 00 02 0B`–`02 12` | `60 00 04 0B`–`04 12` | 8 | `MOD FL` |
| Tremolo `15` | `60 00 02 13`–`02 16` | `60 00 04 13`–`04 16` | 4 | `MOD TR` |
| Rotary `16` | `60 00 02 17`–`02 1D` | `60 00 04 17`–`04 1D` | 7 | `MOD RT` |
| Uni-V `17` | `60 00 02 1E`–`02 20` | `60 00 04 1E`–`04 20` | 3 | `MOD UV` |
| Slicer `19` | `60 00 02 21`–`02 25` | `60 00 04 21`–`04 25` | 5 | `MOD SL` |
| Vibrato `1A` | `60 00 02 26`–`02 2A` | `60 00 04 26`–`04 2A` | 5 | `MOD VB` |
| Ring Modulate `1B` | `60 00 02 2B`–`02 2E` | `60 00 04 2B`–`04 2E` | 4 | `MOD RM` |
| Humanizer `1C` | `60 00 02 2F`–`02 36` | `60 00 04 2F`–`04 36` | 8 | `MOD HU` |
| ⚠️ 2x2 Chorus `1D` | `60 00 02 37`–`02 40` | `60 00 04 37`–`04 40` | 10 | `MOD 2CE` |
| AC Guitar Sim `1F` | `60 00 02 41`–`02 45` | `60 00 04 41`–`04 45` | 5 | `MOD ACS`/`AGS` |
| Phaser 90E `23` | `60 00 02 46`–`02 47` | `60 00 04 46`–`04 47` | 2 | `MOD PH90` |
| Flanger 117E `24` | `60 00 02 48`–`02 4B` | `60 00 04 48`–`04 4B` | 4 | `MOD FL117` |
| Wah 95E `25` | `60 00 02 4C`–`02 50` | `60 00 04 4C`–`04 50` | 5 | `MOD WAH95` |
| ⚠️ DC30 `26` | `60 00 02 51`–`02 59` | `60 00 04 51`–`04 59` | 9 | `MOD DC30` |
| Heavy Octave `27` | `60 00 02 5A`–`02 5C` | `60 00 04 5A`–`04 5C` | 3 | `MOD HOC` |
| Pedal Bend `28` | `60 00 02 5D`–`02 60` | `60 00 04 5D`–`04 60` | 4 | `MOD PBEND` |

Las ⚠️ marcan los que **no encajan en el patrón "DSP simple"**; se detallan abajo. Los otros
25 son un juego plano de parámetros de un byte, exactamente como el Booster.

##### Los cuatro tipos extraídos en detalle

Los tres que pidió el patrón (Phaser, Flanger, 2x2 Chorus) más Tremolo, que por ser el más
corto sirve de caso mínimo. Rango en la notación de `midi.xml`: `range crudoMin/crudoMax/
mostradoMin/mostradoMax`. Para FX, sumar `0x0200` a cada dirección.

**Tremolo** — cuatro niveles directos, el caso más simple posible:

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 02 13` | Shape | `range 00/64/00/100` | 39826 |
| `60 00 02 14` | Rate | `range 00/64/00/100` | 39829 |
| `60 00 02 15` | Depth | `range 00/64/00/100` | 39832 |
| `60 00 02 16` | Level | `range 00/64/00/100` | 39835 |

**Phaser** — un selector, seis niveles y un nivel con Off:

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 02 03` | Type | enum 4: `00` 4stage · `01` 8stage · `02` 12stage · `03` BiPhase | 39763 |
| `60 00 02 04` | Rate | `range 00/64/00/100` | 39769 |
| `60 00 02 05` | Depth | `range 00/64/00/100` | 39772 |
| `60 00 02 06` | Manual | `range 00/64/00/100` | 39775 |
| `60 00 02 07` | Reson. | `range 00/64/00/100` | 39778 |
| `60 00 02 08` | Step Rate | `00` = Off, luego `range 01/65/00/100` | 39781 |
| `60 00 02 09` | Effect | `range 00/64/00/100` | 39786 |
| `60 00 02 0A` | Direct | `range 00/64/00/100` | 39789 |

`Step Rate` es **exactamente `LevelScale.offThenOneBased()`**, la escala que ya existe para
los cinco niveles de efecto del panel (§5, "El bloque `06 57`–`06 5B`"). No hace falta nada
nuevo.

**Flanger** — como Phaser pero con un enum de frecuencia en vez de un tipo:

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 02 0B` | Rate | `range 00/64/00/100` | 39792 |
| `60 00 02 0C` | Depth | `range 00/64/00/100` | 39795 |
| `60 00 02 0D` | Manual | `range 00/64/00/100` | 39798 |
| `60 00 02 0E` | Reson. | `range 00/64/00/100` | 39801 |
| `60 00 02 0F` | Separ | `range 00/64/00/100` | 39804 |
| `60 00 02 10` | Low Cut | enum 11: `00` FLAT · `01` 55.0Hz · `02` 110Hz · … | 39807 |
| `60 00 02 11` | Effect | `range 00/64/00/100` | 39820 |
| `60 00 02 12` | Direct | `range 00/64/00/100` | 39823 |

`Low Cut` es un **enum de frecuencias contiguo** (`00`..`0A`), no un nivel: los valores se
muestran como texto ("FLAT", "55.0Hz"), así que va por `KatanaEnumParameter`, no por slider.

**2x2 Chorus** — el más irregular de los cuatro, y por eso el más instructivo:

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 02 37` | Xover Freq | enum 17: `00` 100Hz · `01` 125Hz · `02` 160Hz · … | 39965 |
| `60 00 02 38` | Rate **(banda Low)** | `range 00/64/00/100` | 39984 |
| `60 00 02 39` | Depth **(banda Low)** | `range 00/64/00/100` | 39987 |
| `60 00 02 3A` | Pre Delay **(banda Low)** | ⚠️ `range 00/50/0.0/40.0` | 39990 |
| `60 00 02 3B` | Low (nivel de la banda) | `range 00/64/00/100` | 39993 |
| `60 00 02 3C` | Rate **(banda High)** | `range 00/64/00/100` | 39996 |
| `60 00 02 3D` | Depth **(banda High)** | `range 00/64/00/100` | 39999 |
| `60 00 02 3E` | Pre Delay **(banda High)** | ⚠️ `range 00/50/0.0/40.0` | 40002 |
| `60 00 02 3F` | High (nivel de la banda) | `range 00/64/00/100` | 40005 |
| `60 00 02 40` | Direct | `range 00/64/00/100` | 40008 |

⚠️ **`midi.xml` repite los nombres `Rate`/`Depth`/`Pre Delay`** para las dos bandas sin
distinguirlas: lo único que dice cuál es cuál es **la posición** respecto a los niveles `3B`
Low y `3F` High que cierran cada grupo. El "(banda …)" de la tabla es interpretación nuestra,
razonada pero no literal de la fuente — si alguna vez suena cruzado, es el primer sitio donde
mirar.

##### Lo que NO encaja en el patrón "DSP simple", explícitamente

Seis cosas que rompen el molde de "un byte, un slider `0..100`" y que hay que resolver **antes**
de cablear los tipos afectados:

1. ⚠️ **`Pre Delay` no es representable con la `LevelScale` actual.** `range 00/50/0.0/40.0`
   significa crudo `0x00..0x50` (0..80) mostrado como **`0.0..40.0` en milisegundos**, o sea
   pasos de 0,5 ms. `LevelScale` mapea crudo↔mostrado con un desplazamiento entero
   (`rawOffset`) y no sabe dividir; **haría falta extenderla con un factor de escala** o
   modelar el parámetro aparte. Afecta a 2x2 Chorus (`02 3A`, `02 3E`) y a los Pre Delay de
   Pitch Shifter y Harmonist.
2. ⚠️ **Pitch Shifter y Harmonist tienen parámetros de 2 bytes.** Sus `Pre Delay` ocupan **dos
   direcciones**, y por eso sus bloques tienen huecos en la numeración principal: PS salta
   `4E`→`50` (el `4F` es el segundo byte) y `54`→`56`; HR salta `5B`→`5D` y `5F`→`61`.
   `midi.xml` lo modela con `<DATA>` anidados etiquetados `Pre Delay(LSB)`
   (`midi.xml:38329-38335`, `38357-38363`). **Esto ya está resuelto en el código**: es el
   mismo `byteWidth = 2` de `KatanaControl` que usa el canal activo (§5.1).
3. ⚠️ **Harmonist arrastra 24 direcciones de escala de usuario**, `60 00 01 64`–`01 7B`: doce
   notas cromáticas (`Voice 1 User Scale C`, `D flat`, …) × dos voces, desde
   `midi.xml:38484`. Son una tabla de mapeo musical, no controles de un panel — se pueden
   dejar fuera sin perder el efecto, igual que se dejó fuera Custom Type del Booster.
4. ⚠️ **Acu Processor cruza el límite de página**: va de `60 00 01 7C` a `60 00 02 02`. No es
   un problema real —la aritmética base 128 de `Address` (§4.2, §4.4) lo cubre sola, porque
   `01 7F + 1 = 02 00`—, pero **sí lo sería si alguien calculara direcciones sumando al último
   byte a mano**. Es el único bloque que lo hace.
5. ⚠️ **DC30 tiene dos secciones con nombres repetidos**, igual que 2x2 Chorus: `Intensity`
   aparece en `02 53` y otra vez en `02 56` (`midi.xml:40067` y `40089`), separadas por un
   `Repeat Rate`. Es un pedal con dos mitades (chorus + echo) y la fuente no las etiqueta.
6. ⚠️ **Los "sneaky" (Phaser 90E, Flanger 117E, Wah 95E) tienen 2–5 parámetros**, no el juego
   completo: son emulaciones de pedales concretos con un par de perillas. No es una anomalía
   de formato, pero sí rompe la expectativa de que todo tipo trae ~8 controles.

**Dos rarezas de la fuente**, ninguna de dirección, encontradas al comparar Mod contra FX:

- **`midi.xml` llama `ACS:` a dos tipos distintos en el bloque Mod**: Compressor (`01 16`) y
  AC Guitar Sim (`02 41`). El bloque **FX desambigua** y usa `AGS:` para el segundo
  (`03 16` vs `04 41`). Se resuelve por posición y por los nombres de parámetro; el catálogo
  de `ModFxType` ya los tiene como tipos separados.
- Las etiquetas de los `Pre Delay` anidados están **copiadas mal en FX**: donde Mod dice
  `PS :Voice2:Pre Delay(LSB)` y `HR :Voice1/2:Pre Delay`, FX repite `PS :Voice1:Pre Delay`
  en los tres. Direcciones y estructura idénticas; solo el texto está mal.

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

Presence, en cambio, salió a la primera con la candidata alta **`60 00 06 56`** (2026-09-02):
cambio audible de brillo, y la perilla física reporta por esa misma dirección. La baja
(`60 00 00 27`) quedó **sin probar** y se conserva documentada solo por si Presence resultara
tener el mismo problema que el reverb más adelante.

##### El bloque `60 00 06 50`–`60 00 06 5B` es la lista de perillas del panel

Esto es lo que explica por qué la dirección "alta" es la de control, y sale de
[reference/FxFloorboard/midi.xml:3981-3992](reference/FxFloorboard/midi.xml), donde el
bloque aparece nombrado uno a uno y **en el orden físico del panel**:

| Dirección | Nombre en `midi.xml` | Estado |
| --- | --- | --- |
| `60 00 06 50` | Panel Knob: Amp Type | sin probar |
| `60 00 06 51` | Panel Knob: Gain | ✅ confirmado por audio |
| `60 00 06 52` | Panel Knob: Volume | ✅ confirmado por audio |
| `60 00 06 53` | Panel Knob: Bass | ✅ confirmado por audio |
| `60 00 06 54` | Panel Knob: Middle | ✅ confirmado por audio |
| `60 00 06 55` | Panel Knob: Treble | ✅ confirmado por audio |
| `60 00 06 56` | Panel Knob: Presence | ✅ confirmado por audio |
| `60 00 06 57` | Panel Knob: Booster | ✅ confirmado por audio |
| `60 00 06 58` | Panel Knob: MOD | ✅ confirmado por audio |
| `60 00 06 59` | Panel Knob: FX | ✅ confirmado por audio |
| `60 00 06 5A` | Panel Knob: Delay 1 | ✅ confirmado por audio |
| `60 00 06 5B` | Panel Knob: Reverb/Delay2 | ✅ confirmado por audio |

**Once de las doce entradas del bloque están confirmadas por oído** (`06 51`–`06 5B`), cada
una en la posición que la tabla predice. Solo queda `06 50` (Amp Type) — que además no es un
nivel continuo, así que ni siquiera el rango `0..100` se le puede suponer. Documentado en
`KatanaAddresses.AMP_TYPE`, deliberadamente fuera del modelo de niveles de `device/`.

Lecciones del proceso, útiles para lo que quede por descubrir (selectores de color, Amp Type):

- **Que una fuente de Mk2 liste una dirección bajo `SEND` no basta.** `60 00 05 48` lo está
  y no funciona. En `set_mapping.py:39-43` TuxKatana fusiona `SEND` y `RECV` en el mismo
  mapa, así que esa separación es organizativa, no semántica.
- **La única prueba que vale es el audio.** Checksum correcto, bytes bien formados y una
  respuesta al GET no demuestran nada: `60 00 06 18` cumplía las tres cosas.
- **Prueba mínima**: SET a los extremos (0 y 100) → ¿cambia el sonido? Luego GET a la misma
  dirección → ¿cambió el estado? Y mover la perilla física → ¿reporta por esa dirección?
- **Que el patrón lleve once aciertos de once no lo convierte en regla universal**, aunque sí
  es la mejor apuesta posible para cualquier dirección nueva del mismo bloque. Ninguna de las
  "bajas" documentadas llegó a hacer falta —todas siguen sin probar—, y `60 00 05 48` sigue
  ahí para recordar que una dirección plausible puede aceptar el mensaje y no hacer nada. La
  única sorpresa real fue Gain/Volume (ver más abajo): las fuentes se contradecían y la alta
  ganó igual.
- **Una fuente puede afirmar justo lo contrario y seguir estando equivocada.**
  `Adresses.txt:36-41` decía, con flechas explícitas — el único sitio del fichero anotado
  así —, que `60 00 06 51` era *read status* y `60 00 00 22` la de escritura: exactamente el
  patrón "escritura baja / reporte alto" que ya había fallado con el reverb. El audio dijo lo
  contrario. Ni siquiera una anotación inequívoca sustituye la prueba.
- **El ruido de las fuentes no predice el resultado.** `booster.yaml` repite `60 00 06 57`
  bajo `Unimplemented:`, y funcionó igual. Al revés que `60 00 05 48`, que estaba limpiamente
  en `SEND` y no funcionó. Las anotaciones de las fuentes de Mk2 no ordenan nada: solo el
  audio.
- **Los rangos casi nunca están documentados para estas direcciones.** Ninguna fuente de Mk2
  da rango explícito para las seis; se usa `0..100` por analogía con el mapa MK1
  (`amplifier.json`) y con el formato `normal` de `slider_formats.yaml`. Es una **suposición
  razonada**, no un dato — pero **probada y aceptada** (2026-09-03): ver abajo.
##### Sobre los rangos `0..100` y sobre qué es realmente el slider de Delay

Dos cosas que salieron de usar la app contra el amplificador y que ninguna fuente decía:

- **El tramo que sobraba al final del recorrido eran dos cosas sumadas.** Se observó que el
  slider llegaba a 100 con la perilla física *a punto* del tope, y se anotó sin resolver: no
  se sabía si el 100 real estaba en el tope o si ese resto era holgura mecánica, y de oído no
  se distingue. **Resuelto el 2026-09-03**: era **las dos cosas**. Los cinco niveles de efecto
  tenían además un desfase de uno (ver más abajo); al corregirlo el hueco se redujo pero no
  desapareció. Lo que queda es holgura mecánica, y lo demuestra Presence, que nunca tuvo
  desfase —escala directa— y mostraba el mismo hueco desde el principio.
- **`60 00 06 5A` no es "el nivel del delay 1": es el mix global de los dos delays.** El
  Katana Mk2 tiene **dos** delays y **una sola perilla DELAY**; esa perilla ajusta el mix
  entre ambos. O sea que la línea comentada `# "60 00 06 5A": glob_mix_lvl` de `delay.yaml`
  probablemente sea el nombre correcto, y el `Panel Knob: Delay 1` de `midi.xml` sea el nombre
  de la perilla, no de lo que hay detrás. La prueba de audio **no distinguió** las dos cosas:
  se hizo con el delay 2 apagado, y así el mix global se comporta igual que el nivel del
  delay 1. Importa el día que se quiera controlar cada delay por separado — entonces esta no
  es la dirección.

- **La perilla no es lo único físico.** Cada efecto tiene además un botón de color
  (verde/rojo/amarillo) que vive en **otra** dirección —`60 00 06 39` para Boost,
  `06 3A` Mod, `06 3B` FX, `06 3C` Delay, `06 3D` Reverb—, y un on/off propio
  (`60 00 00 10` para Boost). Al pulsar el botón de color **no** deben llegar mensajes por la
  dirección de la perilla; eso es lo esperado, no un fallo.

#### Controles que no son niveles: amp type, color y on/off

Investigado e implementado el 2026-09-03, y **confirmado contra el amplificador el mismo
día** salvo un caso: `60 00 06 5C` (variación) resultó ser de solo lectura.

**Amp Type tiene dos direcciones con dos espacios de valores distintos**, y confundirlas es
el error fácil:

| Dirección | Qué es | Valores |
| --- | --- | --- |
| `60 00 06 50` | posición de la perilla AMP TYPE | `00`..`04` = Acoustic/Clean/Crunch/Lead/Brown |
| `60 00 00 21` | modelo de amplificador | 30 valores, `0x00`..`0x20` con huecos |
| `60 00 06 5C` | LED de variación | `00` off, `01` on |

`amplifier.yaml:3` llama a la primera `am_num` —un *número*— y a la segunda `am_type`. La
tabla de los 30 modelos sale de [midi.xml:37311-37341](reference/FxFloorboard/midi.xml), el
bloque `<DATA value="21" desc="PREAMP:" customdesc="Type">`, que es **la fuente más completa**:
`amplifier.yaml` y `Adresses.txt` tienen 29 entradas porque **les falta `BG Lead` (`0x10`)**.
Un 31.º valor, `0x19` "Custom", aparece solo en el bloque de conversión de `midi.xml` y no en
la tabla de la dirección, así que queda fuera y documentado.

**Selector de color por efecto** — `midi.xml` los llama "GRY color select", GRY por
Green/Red/Yellow. Tres fuentes de Mk2 coinciden en direcciones y en `00|01|02`:

| Efecto | Color | On/off |
| --- | --- | --- |
| Boost | `60 00 06 39` | `60 00 00 10` |
| Mod | `60 00 06 3A` | `60 00 01 00` |
| FX | `60 00 06 3B` | `60 00 03 00` |
| Delay | `60 00 06 3C` | `60 00 05 00` |
| Reverb | `60 00 06 3D` | `60 00 05 40` |

Los on/off salen de [midi.xml:3959-3963 y la lista de assign](reference/FxFloorboard/midi.xml)
y de los `*_sw` de los YAML. **El de reverb no está en `Adresses.txt`**: solo lo dan
`reverb.yaml:2` y `midi.xml`.

✅ **Las dos dudas que había quedaron resueltas al probar:**
- **Las direcciones "bajas" también se escriben.** Las diez de color y on/off, más
  `60 00 00 21`, funcionan pese a estar fuera del bloque `06 5x`. Ese bloque no tenía nada de
  especial: es simplemente donde están las *perillas del panel*, no el único sitio escribible.
  Conviene borrar esa regla de trabajo implícita.
- **`00` = off, `01` = on**, como en todo lo demás. La lectura literal de
  `Adresses.txt:81` (`[00|01] # [ON|OFF]`) era engañosa.

##### `60 00 06 5C` (variación) es de solo lectura, y cómo se reconoce

La única del lote que no acepta escritura. **Reporta** bien —pulsar el botón físico de
variación actualiza la app al instante— pero un SET se ignora.

El síntoma vale la pena saber leerlo, porque volverá a aparecer: al mover el switch, la UI se
encendía y **volvía sola a apagado un instante después**. Eso no es un fallo del control, es
la app funcionando bien. La escritura optimista pone la caché en `01`, el amplificador ignora
el SET y sigue reportando su `00` real por esa misma dirección, y el camino de mensajes
espontáneos lo aplica. **Un valor que rebota solo es la firma de una dirección de solo
reporte**, y solo se ve porque el edit mode y la actualización desde el amp están cableados.

`midi.xml:44107-44110` la etiqueta `abbr="led state"` —el estado de un LED, no un control—,
que en retrospectiva ya lo decía. Lo mismo cabe esperar de `06 5D`–`06 61`, los otros cinco
`led state` del bloque.

**La solución no fue buscar otra dirección de variación, sino usar el modelo**: los cinco
canales base tienen su gemelo `Var [...]` (`0x1C`–`0x20`) en la lista de
[AMP_TYPE_FULL], que sí acepta escritura. Así que el switch **lee `06 5C` y escribe
`00 21`**. Es un caso real de "se lee en una dirección y se escribe en otra" — el patrón que
se descartó para el reverb por ser una suposición. La diferencia es que aquí está medido en
las dos direcciones, no supuesto.

##### El bloque `06 57`–`06 5B` no es `0..100`, es `Off` + `1..101`

De [midi.xml:44062-44112](reference/FxFloorboard/midi.xml), que trae los rangos del bloque de
perillas:

- `06 51`–`06 56` (Gain, Volume, Bass, Middle, Treble, Presence): `range 00/64/00/100` —
  crudo `0x00..0x64` mostrado como `0..100`. Crudo y mostrado son el mismo número.
- `06 57`–`06 5B` (Booster, MOD, FX, Delay1, Rev/Delay2): `00 = Off` y luego
  `range 01/65/00/100` — el `0..100` que se muestra vive en el crudo `1..101`.

✅ **Corroborado de forma independiente** (2026-09-03): la UI de PC de **Boss Tone Studio**
muestra esos cinco como "Off" y después 0..100. Implementado en `LevelScale`.

✅ **Verificado en el amplificador tras implementarlo (2026-09-03)**: el hueco entre el slider
al 100 y el tope de la perilla física **se hizo más pequeño**, que es justo lo que predice un
desfase de un paso. El crudo 100 que mandaba la app era el 99 del amplificador. Es también el
motivo de que el rango se documentara tanto tiempo como "suposición razonada": lo era, y
estaba desplazada en uno.

El hueco no desapareció del todo, y no hay por qué buscar un segundo desfase: **Presence
(`06 56`) tiene escala directa, nunca estuvo desplazado, y mostraba el mismo hueco**. Lo que
queda es holgura mecánica del propio potenciómetro.

⚠️ **Una ambigüedad que queda a propósito**: en una escala con Off, el crudo `0` y el crudo
`1` se muestran los dos como `0`. Con un slider de 0..100 no se puede hacer mejor, y no hace
falta: lo que distingue "apagado" de "al mínimo" es el switch on/off del efecto, que tiene su
propia dirección y está confirmado. Por lo mismo, **el slider no puede llegar a Off**: mover
al mínimo manda crudo `1`.

También responde a **Program Change** (0–8: BANK_A CH1-4, PANEL, BANK_B CH1-4) y a
**Control Change** (CC16 booster, CC17 mod, CC18 fx, CC19 delay, CC20 reverb, CC7 volumen global),
tabulados en [reference/TuxKatana/params/midi.yaml:23-34](reference/TuxKatana/params/midi.yaml).

**Nada de eso está implementado, y no es gratis.** Todo lo que la app envía hoy es SysEx, y
`packUsbMidi` lo refleja: solo emite los CIN `0x4`–`0x7` y da por hecho una trama `F0…F7`.
Mandar un PC o un CC exigiría una segunda ruta de empaquetado (CIN `0xC` con 2 bytes, `0xB`
con 3). Lo que sí está resuelto es la dirección contraria: `payloadLengthOf` ya cubre la tabla
CIN completa, así que un PC/CC **entrante** se desempaqueta bien. Además, PC y CC viajan por un
canal MIDI y el amplificador solo atiende el que tenga configurado en `00 02 00 00`, que es un
ajuste global del usuario; el SysEx no depende de eso. Ver §5.1.

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

- [BACKLOG.md](BACKLOG.md) es el registro de avance del proyecto, con cinco secciones:
  **Hecho**, **En progreso**, **Por hacer**, **Pendiente por probar** y **Notas y decisiones técnicas**.
- **Cada vez que completes una tarea de desarrollo**, actualiza `BACKLOG.md` de inmediato:
  mueve el ítem correspondiente a "Hecho" (y quítalo de "En progreso"/"Por hacer") y, si la
  tarea implicó alguna decisión técnica relevante (elección de dirección SysEx, trade-off de
  arquitectura, por qué se descartó una alternativa, etc.), agrégala en "Notas y decisiones
  técnicas". Si la tarea incluye alguna prueba con el amplificador físico, añade dicha prueba a "Pendiente por probar", junto al resultado esperado en caso de realizarse exitosamente.
- Esto se hace **sin que se te tenga que pedir explícitamente en cada sesión** — es parte de
  terminar la tarea, igual que compilar o testear.
