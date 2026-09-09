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
   de cadena de efectos (chain). ✅ **Los cinco están localizados documentalmente**
   (2026-09-05, §5 "Controles sin perilla física"), con una sola ambigüedad —Solo tiene dos
   direcciones candidatas— y ninguno probado todavía contra el amplificador.
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
  - **El contrato de la UI (2026-09-04): edit mode apagado deja cambiar de canal y nada más.**
    Sin edit mode el amplificador no reporta, así que la app **no puede confirmar** ningún
    parámetro que escriba; dejar los controles activos sería aceptar el gesto y no poder decir
    si sirvió de algo. `SlidersPane` calcula `canEdit = connected && editMode` y deshabilita
    con él todos los parámetros, con un aviso que explica por qué. **El selector de canal es la
    única excepción** —sigue con `enabled = connected`—: cambiar de canal es un comando
    básico, no un ajuste fino.
  - ⚠️ **Nunca ha habido, ni debe haber, un gate de edit mode en `device/` o `protocol/`.**
    Esas capas no saben qué es el edit mode y el SET sale al cable siempre; quién puede editar
    lo decide la UI. Hay un test JVM que lo fija. Queda **por confirmar con el amplificador**
    si el hardware acepta el SET del canal con edit mode apagado (BACKLOG.md, "Pendiente por
    probar" y "El canal depende de Edit Mode: no es una regresión").

#### Cómo se parte `SlidersPane` en pantallas de dominio (decidido el 2026-09-08)

El bloque 6 del BACKLOG —"UI real de control"— arranca por `AmpScreen`, y lo primero que había
que resolver no era la pantalla sino **cómo extraerla sin romper la edición offline**: hoy
`SlidersPane` sirve a la vez a la pantalla en vivo y al editor de la Biblioteca (`offline = true`,
§4.5), y esa reutilización es deliberada — un solo juego de tarjetas para las dos cosas, en vez
de una copia que se separa de la otra cada vez que se cablea un control nuevo.

✅ **Decisión: extraer los composables del dominio de amplificador y que los llamen los dos.**
Ni pantalla nueva duplicada, ni `SlidersPane` congelado como "solo offline".

**Lo que hizo fácil la decisión fue mirar cómo está partido ya el código**: `SlidersPane` es, de
arriba abajo, *acciones de preset* → *bloque de amplificador* → *cinco tarjetas de efecto* →
`NoPanelPane`. Y resulta que **todo lo que no es la sección de efectos es dominio de
amplificador**: el canal, el modelo, los seis niveles del panel, y `NoPanelPane` entero (Noise
Gate, Contour, EQ1, EQ2, cadena). O sea que no hubo que trocear nada transversal — solo sacar el
bloque contiguo del medio a `AmpSection` y **reutilizar `NoPanelPane`, que ya era un composable
aparte desde el 2026-09-06**.

```
AmpScreen (en vivo)         SlidersPane (en vivo y offline)
   AmpSection      ←──────────  AmpSection        ← el mismo composable
   NoPanelPane     ←──────────  EffectCard ×5
                                NoPanelPane       ← el mismo composable
```

**Por qué no las otras dos opciones:**

- **Duplicar el bloque de amplificador en una `AmpScreen` propia y dejar `SlidersPane` intacto**
  se descarta por lo de siempre con las copias: dos sitios que hay que acordarse de tocar cada
  vez que se cablea un parámetro, y el proyecto tiene por delante los cientos de direcciones de
  Mod/FX. La ventaja que ofrecía —no tocar nada de lo que ya funciona— resultó no hacer falta:
  ver abajo.
- **Congelar `SlidersPane` como pantalla solo-offline hasta tener todas las pantallas de dominio**
  deja al editor de la Biblioteca sin recibir ninguna mejora de las pantallas nuevas durante todo
  el bloque 6, que no es corto. Y al final habría que reescribir el editor entero de golpe.

⚠️ **El orden de `SlidersPane` NO cambia, y eso fue un criterio explícito.** `AmpSection` es solo
el bloque contiguo del medio; `NoPanelPane` se sigue llamando **donde estaba, al final**, después
de los efectos. Se podría haber juntado todo el dominio de amplificador de un tirón, pero eso
habría reordenado una pantalla que el usuario ya usa con el amplificador delante — y reordenar la
UI no era la tarea. La señal de que la reutilización no se partió es que **ningún test existente
cambió**.

**Dónde vive cada cosa:**

| | Qué es |
| --- | --- |
| `ui/screens/AmpDomain.kt` | **Kotlin puro, sin Compose**: qué `LevelId`/`SelectorId`/`NoPanelParamId` son del dominio de amplificador y cuáles de efectos. Con tests JVM. |
| `ui/screens/AmpScreen.kt` | `AmpSection` (el bloque reutilizado) y `AmpScreen` (la pantalla en vivo). |
| `ui/screens/EffectsScreen.kt` | `EffectsSection` (las cinco tarjetas) y `EffectsScreen` (la pantalla en vivo). Segunda aplicación del mismo patrón, 2026-09-08 — sin lista nueva: usa `EffectId.entries` y `AmpDomain.EFFECT_SELECTORS`, que ya existían. |
| `DebugConnectionScreen.kt` | `SlidersPane` sigue donde estaba, ahora llamando a `AmpSection` y `EffectsSection`. |

**`AmpDomain` no es una lista decorativa**: el reparto de controles entre pantallas es
precisamente lo que se puede olvidar al añadir un parámetro nuevo —y quedaría invisible en las
dos pantallas sin que nada falle—, así que se expresa como dato y **hay un test JVM que exige que
cada `SelectorId` esté clasificado en exactamente un sitio**. Es la misma idea del
`AMP_LEVELS = LevelId.entries - efectos` que ya existía, extendida a lo demás y comprobada.

⚠️ **La cadena de efectos se queda en `AmpScreen`, no espera a una pantalla propia.** El criterio:
una pantalla de dominio tiene que tener un dominio detrás, y "la cadena" hoy son **cuatro
selectores de enrutado** (tipo de cadena, posición del loop, de EQ1 y del Pedal/FX) más un
diagrama de solo lectura — y el reordenado manual, que era lo único que la habría hecho una
pantalla de verdad, **se retiró el 2026-09-06 por no funcionar contra el amplificador**. Son
ajustes de por dónde pasa la señal *del amplificador*, no de ningún efecto concreto, así que su
sitio natural mientras tanto es con el amplificador. Si algún día `EffectsScreen` necesita
reordenar la cadena, se mueve; hasta entonces, una pantalla con un selector y un dibujo sería una
entrada de menú que no se gana su sitio.

#### El cierre: `SlidersPane` se retira entera (decidido el 2026-09-09)

Con `PresetsScreen` —la tercera pantalla de dominio— `SlidersPane` se queda sin nada que hacer, y
la pregunta era si conservarla como UI del editor offline (`offline = true`) o borrarla del todo.

✅ **Decisión: borrarla del todo.** Y no fue una decisión de gusto: **el propio código ya la había
vaciado**. Después de extraer `AmpSection` y `EffectsSection`, el cuerpo de `SlidersPane` quedó así:

```kotlin
if (!offline) { EditModeToggle …; divider }
if (!offline) { Guardar preset · Exportar · Releer; diálogos; selector de canal }
AmpSection(…)        ← siempre
EffectsSection(…)    ← siempre
NoPanelPane(…)       ← siempre
```

O sea: **la rama offline es exactamente esos tres composables en fila**, y todo lo demás está tras
un `if (!offline)`. `SlidersPane` había dejado de ser una pantalla con contenido para ser *una
cáscara de chrome en vivo alrededor de tres piezas que ya se reutilizan por separado*. Lo que la
justificaba —compartir las tarjetas entre lo de en vivo y lo offline— hoy lo hacen `AmpSection`,
`EffectsSection` y `NoPanelPane` directamente.

**Por qué no conservarla solo para el editor offline** (la opción de menos churn):

- El parámetro `offline` es un booleano que hace que **un composable signifique dos cosas**, y con
  el modo en vivo retirado dejaría de tener un segundo valor: se quedaría permanentemente en
  `true`, con la mitad del cuerpo muerto y sin forma de notarlo.
- **El llamador offline paga un peaje que solo existe por la firma compartida.** Para editar un
  fichero hay que pasarle `state = UsbConnectionState.Idle`, `editMode = false`,
  `onEditModeChanged = {}`, `presetSaveInFlight = false`, `onSavePreset = { _, _ -> }`,
  `diagnostics = OFFLINE_DIAGNOSTICS` (un objeto entero de callbacks que nunca se llaman) y una
  docena de `onRead… = {}` — argumentos inertes cuyo único propósito era satisfacer a la mitad en
  vivo. Al llamar a los tres composables directamente, **todo eso desaparece**, incluido el
  `OFFLINE_DIAGNOSTICS` que existía solo para rellenar un hueco.

El editor offline pasa a tener su propio cuerpo, `PresetEditorBody`, **privado y junto a su único
llamador** (`PresetEditorScreen`, en `LibraryPane.kt`). No se pone en `ui/screens/` junto a
`AmpSection`: lo que merece vivir suelto es lo que se **reutiliza**, y esas tres piezas ya están
ahí; un composable con un solo llamador pertenece al lado de ese llamador.

⚠️ **Y esto sí borra código en vez de moverlo**: se van el `offline: Boolean`, las tres ramas
`if (!offline)`, `OFFLINE_DIAGNOSTICS` y ~60 parámetros de firma. La señal de siempre —**ningún
test hizo falta tocarlo**— se mantiene, y aquí vale doble: si algún test hubiera dependido de
`SlidersPane`, este borrado lo habría roto. Ninguno lo hacía, porque lo que se prueba en JVM es
`PresetEditor` (el estado) y `AmpDomain` (el reparto), no el árbol de Compose.

**El menú queda en cuatro entradas**: Logs · Amplificador · Efectos · Presets. "Sliders" y
"Biblioteca" desaparecen — la primera porque su contenido está repartido entre las dos pantallas
de dominio, la segunda porque es la mitad de abajo de Presets.

| | Qué es |
| --- | --- |
| `ui/screens/PresetsScreen.kt` | `PresetsScreen` (guardar en canal + exportar + la Biblioteca entera) y el diálogo de guardado, traído desde `DebugConnectionScreen`. |
| `ui/screens/LibraryPane.kt` | La mitad "Biblioteca" de esa pantalla, sin cambios de comportamiento; su editor ahora usa `PresetEditorBody` en vez de `SlidersPane`. |
| ~~`SlidersPane`~~ | **Borrada.** Su contenido vive en `AmpSection` + `EffectsSection` + `NoPanelPane`. |

##### Cómo se distingue "en vivo" de "Biblioteca" dentro de `PresetsScreen`

Juntar las dos cosas en una pantalla es exactamente donde se podían mezclar sin querer: arriba hay
botones que **escriben en el amplificador ahora mismo** y abajo una lista de **ficheros del
teléfono**. La separación es deliberada y tiene cuatro señales, no una:

1. **Dos secciones con encabezado y subtítulo propios**, y el subtítulo dice sobre qué actúa cada
   una ("el amplificador conectado ahora mismo" contra "ficheros `.tsl` de este teléfono").
2. **Un divisor entre ambas**, para que no se lean como una lista de botones continua.
3. ⚠️ **Reglas de habilitación distintas y visibles, que es la señal que no miente**: la mitad de
   arriba se apaga sin cable y —para guardar— sin Edit Mode, con su aviso; la de abajo **funciona
   con el cable desenchufado**, que es justo su razón de ser (§4.5). La asimetría no es estética:
   es la diferencia real entre escribir en hardware y leer ficheros.
4. **Abrir un preset ocupa la pantalla entera** y el bloque en vivo desaparece: dentro de un
   preset, la única acción hacia el amplificador es "Enviar al amplificador…", que trata de *ese*
   preset y ya tiene sus dos confirmaciones.

⚠️ **"Releer" no se lleva a `PresetsScreen`**, aunque estuviera en la misma fila de botones de
`SlidersPane`. Repuebla la caché de controles, o sea **lo que enseñan `AmpScreen` y
`EffectsScreen`** —y las dos ya lo tienen—; aquí no hay ningún control que refrescar. Y exportar no
lo necesita: `KatanaRepository.exportImage` lee su propio dump completo, no la caché.

#### La navegación: por qué NO hay `NavHost` (decidido el 2026-09-09)

Fase 2 de la UI: shell de verdad —barra superior con el estado de conexión, barra inferior con
las tres pantallas de dominio, Logs degradado a entrada secundaria—. La pregunta previa era si
adoptar Navigation Compose o seguir con el `when` sobre un enum de sección.

✅ **Decisión: seguir sin `NavHost`.** Scaffold + `NavigationBar` que setea el mismo enum, más un
`BackHandler` para el back. **Sin dependencia nueva.**

**El argumento que lo decidió no es "es más barato", es que el detalle de un preset no es estado
de ruta.** La ventaja principal de la opción con `NavHost` era plantar la vista de detalle/editor
de la Biblioteca como su propia ruta con back del sistema. Pero lo que hay que "navegar" ahí es
un `LibraryViewModel.EditingSession`, y dentro lleva un `PresetEditor` que **posee un
`KatanaRepository` vivo sobre un `OfflineKatanaLink`, creado con `viewModelScope`** (§4.5). Un
argumento de ruta es **dato serializable**; esto es un objeto vivo con su propio scope de
corrutinas. Convertirlo en ruta deja dos salidas, las dos peores que lo que ya hay:

1. **Dos fuentes de verdad** para "qué preset está abierto" —la ruta y el ViewModel—, que es
   justo la clase de duplicación que este proyecto evita en todas partes.
2. **Mantener el ViewModel igual** y usar la ruta solo como disparador: el mismo `when` de ahora
   con un `NavController` encima.

⚠️ **Y hay un pie de banco concreto**: `viewModel()` dentro de un `NavBackStackEntry` se scopea
**por ruta**, así que la lista y el editor recibirían `LibraryViewModel` **distintos** salvo que
se scopee explícitamente al padre. Fallaría en silencio exactamente donde compartir importa —el
editor abierto dejaría de verse desde la lista— y el síntoma sería "a veces se pierde lo que
estaba editando". Es un riesgo real a cambio de una ventaja que resultó no aplicar.

**Lo que sí se toma de la opción con `NavHost`, sin adoptarla**: el comportamiento del back, con
un `BackHandler` y ~10 líneas.

| Gesto | Qué hace |
| --- | --- |
| Back con un preset abierto | lo cierra y vuelve a la lista (con confirmación si hay cambios sin guardar) |
| Back en una pestaña que no es la de inicio | vuelve a **Amplificador**, la de inicio |
| Back en Amplificador | no se consume: cierra la app, como espera cualquiera |

Es el mismo comportamiento que un `NavHost` con `popUpTo(startDestination)`, escrito a mano
porque son tres ramas — y **vive en `ShellNavigation`, Kotlin puro con tests JVM**, en vez de
dentro de un composable donde no se podría probar.

**Cuándo reabrir esto**: si aparecen deep links de verdad (abrir un `.tsl` desde el gestor de
archivos es un `Intent`, no una ruta), si los destinos pasan de un puñado, o si hiciera falta
un back stack independiente por pestaña. Hoy son tres destinos y una entrada secundaria.

##### Qué ve cada pantalla según la conexión, en una función probada

Hasta ahora `canEdit = connected && editMode` estaba escrito **tres veces** —`AmpScreen`,
`EffectsScreen` y el bloque en vivo de `PresetsScreen`— y sin cable los controles se quedaban
grises sin decir por qué. Las dos cosas se arreglan con el mismo cambio: `ShellState`
(`ui/screens/ShellState.kt`, **Kotlin puro, sin Compose**) tiene la única definición:

```
availabilityOf(state, editMode) →  NoAmp(state)      no hay cable: se explica qué falta
                                   NeedsEditMode     hay cable, falta Edit Mode: controles grises + aviso
                                   Ready             se puede editar
```

⚠️ **`NoAmp` deja de ser "todo gris"**: la pantalla enseña **qué falta y qué hacer** —buscando,
esperando permiso, no encontrado, fallo— con el botón de buscar a mano. Un panel gris sin
explicación se lee como un bug; una frase lo convierte en una regla.

⚠️ **La Biblioteca no entra en esto y es deliberado** (§4.5): funciona desenchufada, que es su
razón de ser. Lo que sí recibe el mismo trato es el bloque **en vivo** de `PresetsScreen`
(guardar en canal, exportar), que sin cable no puede hacer nada.

##### Cambios sin guardar al cambiar de pestaña

⚠️ **No se pierden, y no por suerte.** La sesión de edición vive en `LibraryViewModel`, no en el
composable, porque tiene que sobrevivir a la recomposición para poder poseer su repositorio
(§4.5). Consecuencia: irse a Efectos y volver a Presets **reencuentra el editor tal cual**, con
su marca de "Cambios sin guardar". El criterio elegido es **no tocar eso**: preservar en silencio
es lo que menos sorprende, y avisar al cambiar de pestaña convertiría un gesto barato en un
diálogo.

**El único camino que descarta trabajo es "Volver" dentro del editor**, y por eso **ese sí
pregunta** cuando hay cambios sin guardar (el back del sistema hace lo mismo, que para el usuario
es el mismo gesto). Sin esa confirmación la app sería incoherente: preserva al navegar y borra al
volver, sin decirlo.

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

**El dump llega en varios mensajes, cada uno con su base.** Ni el número de mensajes ni el
total de bytes son fijos —HOW.md traza 6, el Mk2 real dio 8— así que `MemoryDump` es una
**búsqueda por dirección**, no un índice sobre un array plano. Una dirección que no aparece
es una respuesta normal ("el amp no mandó ese rango"), no un error: queda en `null` y su
control cae al GET de respaldo.

⚠️ **Corregido el 2026-09-04: los trozos SÍ son contiguos entre sí.** Aquí se decía que "el
amplificador se salta los huecos donde no hay parámetros", y no es lo que hace: manda **un
bloque contiguo troceado en mensajes de 241 bytes de datos**, y lo que hace con los huecos es
**parar antes** (1860 de 1920), no dejar agujeros en medio. Comprobado por cuatro vías que
cuadran a la vez —las tres bases que documenta HOW.md, la única base del Mk2 anotada
(`60 00 05 53` = trozo 3 = offset 723 = 3×241), el offset 125 de las perillas dentro de ese
trozo, y el reparto 7×241 + 173 = 1860—. Detalle y tabla en BACKLOG.md, "Propuesta de diseño".

Esto **no cambia el diseño de `MemoryDump`**, que sigue siendo una búsqueda por dirección y
sigue sin asumir número ni total. Importa para otra cosa: **filtrar la respuesta del dump por
contigüidad sería una mala idea igualmente** —si algún día el amp sí dejara un hueco, un filtro
así descartaría en silencio todo lo posterior—, así que el criterio de aceptación es el rango
pedido, y la contigüidad solo vale como comprobación de diagnóstico.

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

**También se relee al cambiar de canal, no solo al conectar.** Cada canal (1A–4A, 1B–4B,
PANEL) tiene sus propios valores para todo: niveles, modelo de amplificador, colores, on/off,
tipos de efecto. Sin releer al cambiar de canal la app seguiría mostrando los del canal
anterior, que es peor que mostrar nada — parecería que el amplificador dice una cosa cuando
dice otra.

⚠️ **La primera versión de esto (2026-09-03) tenía un bug de cuatro fallos encadenados,
reproducido en JVM el 2026-09-04 y ya arreglado — pendiente de confirmar con el amplificador
real** (BACKLOG.md, "Pendiente por probar", punto 6). Vale la pena documentar la cadena
porque es un recordatorio de lo mismo que §5 repite para las direcciones: **un argumento
estructural convincente no es una comprobación**. Se razonaba que "no hay bucle porque el
canal vive fuera del dump" y que "1A→2A→3A recarga una sola vez" — las dos frases sonaban
bien y las dos eran falsas, y bastaba una traza para verlo:

- **Sí había bucle, y sí se reescribía el canal.** El argumento era que `00 01 00 00` vive
  fuera del dump. Cierto para la *petición*, pero `sendAndCollectUntilQuiet` no filtraba por
  dirección: recogía todo lo que pasara por el stream mientras durara la ventana, así que un
  reporte espontáneo de cambio de canal que llegara durante un dump entraba en la lista y
  acababa como un `Chunk` de `MemoryDump`. Y `applyDumpValue` era de **un solo byte** y no
  conocía `byteWidth`, así que le daba al canal su byte alto, `0` — el canal se convertía en
  Panel.
- **Y por eso 1A→2A→3A no recargaba una vez: no recargaba ninguna.** Ese `0` espurio
  reiniciaba el `collectLatest` y, como `reload` ya había dejado `loadedChannel = 0`, el guard
  descartaba la recarga pendiente.
- Además la recarga de conexión era un `launch` aparte sin relación con el `collectLatest`
  del canal: se midieron **dos dumps simultáneos**, cada colector comiéndose los mensajes del
  otro, así que ninguno alcanzaba el silencio de 250 ms y ambos agotaban el tope de 3 s.

**La corrección, en `KatanaRepository` y en el ViewModel:**

- `sendAndCollectUntilQuiet` gana un predicado `accept: (RolandMessage.Data) -> Boolean`
  (`protocol/RolandExchange.kt`) y devuelve `QuietCollection(accepted, rejected,
  invalidCount)` en vez de la lista cruda. `loadFromDump` lo llama con
  `blockReplyIn(MEMORY_DUMP, MEMORY_DUMP_SIZE)`: dentro del rango pedido **y** con un payload
  mayor que `MAX_CONTROL_PAYLOAD` (2, el mismo umbral que ya usaba `onIncoming` al revés). Un
  mensaje rechazado **no reinicia el contador de silencio** — antes sí, y era la causa directa
  de que la ventana se estirara hasta el tope. Los rechazos se registran agrupados en
  `DumpLoad.rejectedDuringWindow`, no línea por mensaje.
- `MemoryDump` gana `bytesAt(address, width)`, y `KatanaControl.applyDumpValue` pasó de
  recibir un `Int` ya extraído a recibir el `MemoryDump` entero y leer sus propios
  `byteWidth` bytes — todo o nada, nunca un byte alto suelto.
- En el ViewModel, la recarga de conexión y el watcher de canal dejaron de ser dos caminos
  independientes: los dos alimentan el mismo `MutableStateFlow<ReloadRequest>` conflado,
  consumido por un único `collectLatest` (`startReloadCoordinator`), con un `Mutex` como red
  de seguridad alrededor de `loadFromDump()` y el guard de canal reevaluado **después** del
  margen de 300 ms, no solo antes.

✅ **Comprobado en JVM** (10 tests nuevos, 208 en total) reproduciendo el escenario exacto que
falló: 1A→2A→3A en 100 ms produce un solo dump adicional y dan canal correcto en caché; una
recarga de conexión deliberadamente lenta que se solapa con un cambio de canal nunca deja dos
dumps en vuelo a la vez; y un reporte espontáneo emitido durante la ventana del dump aparece
en `rejected`, no en `accepted`. **Sin probar contra el amplificador todavía.**

### 4.5 Editar sin amplificador: el backend de un control es el `KatanaLink`

Decidido el 2026-09-06, antes de escribir la edición offline de la Biblioteca. La pregunta era
cómo hacer que **los mismos sliders y selectores** que hoy escriben al amplificador puedan
editar un preset de un fichero, sin cable y sin hardware.

✅ **La decisión: una implementación de `KatanaLink` que en vez de un cable es una imagen de
memoria en RAM.** No hace falta ninguna abstracción nueva — `KatanaLink` **ya era** el backend
de un parámetro, solo que hasta ahora tenía una sola implementación.

```
en vivo:   KatanaControl → KatanaLink(USB)      → [amplificador]
offline:   KatanaControl → KatanaLink(MemoryImage) → [bytes en RAM]
                            ↑ mismos controles, mismo KatanaRepository, misma UI
```

`KatanaLink` tiene exactamente dos operaciones (`send(mensaje)` y `incoming: Flow<ByteArray>`),
y `KatanaControl` no usa nada más: un SET es `link.send(RolandSysEx.set(...))` y un GET es
`awaitRolandReply(link.incoming, ...)`. Así que una implementación que **guarde los bytes de un
SET en un mapa dirección→byte y conteste a un GET desde ese mismo mapa** hace funcionar todo el
juego de controles sin tocar `KatanaControl`, `KatanaRepository` ni un solo composable.

**Cuatro cosas del código ya existente hacen que esto encaje sin forzar nada:**

1. **`awaitRolandReply` y `sendAndCollectUntilQuiet` se suscriben con
   `CoroutineStart.UNDISPATCHED` *antes* de llamar a `send`** (§4.4). Estaba puesto para no
   perder una respuesta rápida del amplificador; resulta que es exactamente lo que hace falta
   para que una respuesta **síncrona**, emitida desde dentro de `send`, no se pierda.
2. **`KatanaRepository` ya se testea contra un `KatanaLink` falso** (§6). El link offline no es
   un tipo nuevo de cosa: es el mismo patrón que los tests llevan usando desde el principio,
   ascendido a código de producción.
3. **`MemoryDump` es una búsqueda por dirección**, no un índice sobre un array plano (§4.4), y
   la imagen mutable es su gemela escribible. Convertir una en otra es directo, así que
   `AmpState.from(...)` y el parseo de `.tsl` siguen funcionando sin cambios.
4. **Las escrituras ya son fire-and-forget optimistas** (§4.2). Offline, "el amplificador no
   confirma" deja de ser una limitación y pasa a ser trivialmente cierto.

#### Por qué no las otras dos opciones

**Opción A —abstraer un "backend de parámetro" dentro de `KatanaControl`, con dos
implementaciones— se descarta porque duplicaría un seam que ya existe.** `KatanaLink` está una
capa más abajo y ya separa exactamente lo mismo. Meter una segunda costura significaría que
`read`, `set`, `probeWrite` y `applyIncoming` crezcan cada uno una rama, en la clase que
concentra las reglas más delicadas del proyecto (la caché optimista, el debounce, la regla
anti-eco). Más código y más riesgo para el mismo resultado.

**Opción B —`AmpState` como fuente única de verdad, con los controles editándolo siempre y un
observador sincronizando hacia el amplificador— se descarta por un dato concreto y medible:
`AmpState` tiene 24 campos y la app controla hoy varios cientos de direcciones.** Solo
`ModFxInternalParams` son 192 `ParamSpec`, más 48 de EQ1/EQ2, 20 ranuras de cadena, los Contour,
el Noise Gate. Hacer de `AmpState` la fuente de verdad obliga a hacerlo crecer a cientos de
campos, uno por parámetro, y a mantener esa lista a mano cada vez que se cablea un tipo nuevo —
justo lo que §4.4 evita deliberadamente al aplicar el dump recorriendo la lista de controles en
vez de los campos de `AmpState`.

⚠️ **Y tiene un problema peor, que es el que la descarta del todo: un modelo de dominio pierde
lo que no modela.** Un `.tsl` son 1141 bytes; `AmpState` entiende 24 valores. Si la edición
pasara por `AmpState`, importar un preset, cambiarle el Gain y volver a exportarlo **borraría
todos los parámetros internos de Mod/FX, el EQ y la cadena**, sustituidos por lo que `AmpState`
no supo conservar. Con la imagen de bytes, lo que la app no entiende **se conserva intacto
porque nunca se toca**. Para un formato de intercambio esa propiedad no es un detalle, es el
requisito.

Además invertiría el flujo de datos que hoy está confirmado contra hardware real, y reabriría
la clase de bug que costó un día entero de depuración (§4.4, "los cuatro fallos encadenados":
eco, reentrada y dumps solapados).

#### Qué es cada cosa, ahora que hay dos orígenes

| | Qué es | Mutable | Fidelidad |
| --- | --- | --- | --- |
| `MemoryImage` | mapa dirección→byte, la memoria editable de un preset | **sí** | **total**: guarda bytes que nadie interpreta |
| `MemoryDump` | lo que el amplificador contestó, indexado por dirección | no | total, pero de solo lectura |
| `AmpState` | instantánea legible de 24 parámetros con nombre | no | **lossy a propósito**: es para leer, no para guardar |

**La regla que sale de aquí, y que conviene no olvidar: lo que se guarda y se transporta son
bytes; `AmpState` es para enseñar.** Cualquier camino de exportación que pase por `AmpState`
está perdiendo información aunque no lo parezca.

⚠️ **`device/` sigue sin saber si está en vivo o no**, igual que no sabe qué es el edit mode
(§4.2). Un control hace su SET siempre; quién es el destinatario lo decide quién construye el
`KatanaRepository`. Esa es la misma frontera de siempre, y no se mueve.

⚠️ **Actualización del 2026-09-09: el editor offline ya no pasa por `SlidersPane`.** Cuando se
escribió esto, la UI del editor era `SlidersPane(offline = true)` — la misma pantalla que la de en
vivo, con un booleano. Con las tres pantallas de dominio hechas, `SlidersPane` se borró y el editor
usa `PresetEditorBody` (`AmpSection` + `EffectsSection` + `NoPanelPane`), que es lo que esa rama
`offline` ya era en realidad. **Nada de lo de arriba cambia**: el backend sigue siendo un
`KatanaLink` sobre `MemoryImage`, los controles siguen siendo los mismos, y `device/` sigue sin
saber si está en vivo. Lo que cambió es qué composable los pinta. Ver §4.2, "El cierre".

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
| `7F 00 01 04` | Guardar estado actual en un canal (**2 bytes**, `00 xx`; `00` PANEL, `01`..`08` los ocho canales — la misma numeración de `00 01 00 00`). Ver §5 "Guardado de presets" |
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

✅ **Mod, FX, Delay y Reverb usan la misma dirección "gemela" y están los cuatro confirmados
con audio** (2026-09-04, con Edit Mode activado): Mod `60 00 01 01`, FX `60 00 03 01`,
Delay 1 `60 00 05 01`, Reverb `60 00 05 41`. **Los cinco efectos siguen el mismo patrón**, así
que la analogía que se implementó a propósito sin darla por buena resultó correcta — esta vez;
la regla de §5 sigue en pie, porque la vez del reverb no lo fue.

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
  | `60 00 00 12` | Drive | `00`..`78` (0..120) | ✅ confirmado |
  | `60 00 00 13` | Bottom | `00/64` mostrado `-50..+50` | ✅ confirmado |
  | `60 00 00 14` | Tone | `00/64` mostrado `-50..+50` | ✅ confirmado |
  | `60 00 00 15` | Solo Sw | `00`/`01` | ✅ confirmado |
  | `60 00 00 16` | Solo Level | `00/64` = 0..100 | ✅ confirmado |
  | `60 00 00 17` | Effect Level | `00/64` = 0..100 | ✅ confirmado |
  | `60 00 00 18` | Direct Mix | `00/64` = 0..100 | ✅ confirmado |
  | `60 00 00 19`–`1E` | Custom Type + Bottom/Top/Low/High/Character | pedal "custom" | ❌ sin implementar (a propósito) |

  ✅ **Confirmados con audio el 2026-09-04** (con Edit Mode activado):
  `KatanaAddresses.BOOST_DRIVE`/`BOOST_BOTTOM`/`BOOST_TONE`/`BOOST_SOLO_ENABLED`/
  `BOOST_SOLO_LEVEL`/`BOOST_EFFECT_LEVEL`/`BOOST_DIRECT_MIX`, todas con dos fuentes de Mk2 de
  acuerdo (`booster.yaml:4-9` y `midi.xml:37109-37304`).
  `60 00 00 12` estaba antes documentada como `BOOST_LEVEL_LOW`, "alternativa baja de
  [BOOST_LEVEL], sin probar" — con el bloque interno completo entendido, no era una
  alternativa a la perilla del panel, sino este mismo parámetro de Drive, y **la prueba de
  audio lo confirma**. Igual queda confirmada la escala centrada `-50..+50` de Bottom/Tone,
  que se modeló con `LevelScale.centered(50)` a partir de `midi.xml` y nada más.

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

#### El mapa de parámetros internos de Mod y FX (extracción documental, 2026-09-04 / 2026-09-05)

Extracción completa desde `midi.xml`, **sin implementar nada todavía**: el objetivo es que
cablear cada tipo después sea mecánico en vez de volver a leer 2.300 líneas de XML cada vez.
Nada de esto está probado contra el amplificador.

**Hecho en dos tandas.** El 2026-09-04 se fijó el método, el índice de los 31 bloques y la
regla FX = Mod + `0x0200`, y se extrajeron cuatro tipos como muestra. El **2026-09-05** se
completaron los 27 restantes: ahora los 31 están abajo parámetro a parámetro, en
"Extracción completa de los 31 tipos", con su catálogo de selectores. Esa segunda tanda
**corrigió dos afirmaciones** de la primera — ver la lista de anomalías, puntos 1 y 2.

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
| ⚠️ Pitch Shifter `0F` | `60 00 01 4A`–`01 58` | `60 00 03 4A`–`03 58` | 13 (15 dir.) | `MOD PS` |
| ⚠️ Harmonist `10` | `60 00 01 59`–`01 7B` | `60 00 03 59`–`03 7B` | 9 (11 dir.) + 24 escala | `MOD HR` |
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
| ⚠️ DC30 `26` | `60 00 02 51`–`02 59` | `60 00 04 51`–`04 59` | 8 (9 dir.) | `MOD DC30` |
| Heavy Octave `27` | `60 00 02 5A`–`02 5C` | `60 00 04 5A`–`04 5C` | 3 | `MOD HOC` |
| Pedal Bend `28` | `60 00 02 5D`–`02 60` | `60 00 04 5D`–`04 60` | 4 | `MOD PBEND` |

Las ⚠️ marcan los que **no encajan en el patrón "DSP simple"**; se detallan abajo. Los otros
25 son un juego plano de parámetros de un byte, exactamente como el Booster.

⚠️ **"Params" cuenta parámetros, no direcciones, y en tres tipos no coinciden**: Pitch
Shifter, Harmonist y DC30 tienen parámetros de 2 bytes, así que ocupan más direcciones que
parámetros (por eso el "(N dir.)"). **DC30 lleva ⚠️ desde el 2026-09-05**: la primera tanda
lo marcó solo por sus nombres repetidos, pero su `Repeat Rate` también es de 2 bytes — punto
2 de las anomalías.

##### Extracción completa de los 31 tipos (2026-09-05)

Los 31 bloques, parámetro a parámetro, con dirección, rango, escala y línea de `midi.xml`.
Sustituye a la muestra de cuatro tipos que había aquí antes (Tremolo, Phaser, Flanger,
2x2 Chorus, que siguen abajo en su sitio del catálogo): el objetivo declarado era que cablear
un tipo fuese mecánico **sin volver a abrir el XML**, y con solo cuatro extraídos no lo era.
**Nada de esto está probado contra el amplificador.**

**Método**, el mismo de la primera tanda, aplicado a los 27 restantes: agrupar los `<DATA>` por
su atributo `desc` (que lleva el prefijo por tipo), leer el `customdesc` como nombre del
parámetro y el `<PARAM>` hijo como rango. Los bloques de un tipo son consecutivos, así que el
agrupado los delimita solo.

✅ **La regla FX = Mod + `0x0200` se volvió a verificar, y esta vez el resultado es más fuerte
de lo que decía la nota anterior.** Comparando los 237 nodos de cada bloque por
`(LSB relativa, 4.º byte, profundidad)` contra `(desc, customdesc, lista de PARAM)`:

| Comprobación | Resultado |
| --- | --- |
| Nº de nodos | 237 vs 237 |
| Diferencias de **dirección** | **0** |
| Diferencias de **nombre de parámetro** (`customdesc`) | **0** |
| Diferencias de **rango / lista de valores** (`PARAM`) | **0** |
| Diferencias de etiqueta de bloque (`desc`) | 205, de las cuales 191 son el prefijo `MOD `→`FX ` |

Las 14 diferencias que no son el prefijo son las dos rarezas ya documentadas al final de esta
sección (`ACS:`/`AGS:`, 5 nodos; y los `Pre Delay` anidados mal etiquetados en FX, 9 nodos).
El recuento de 14 coincide exactamente con el de la extracción de 2026-09-04, obtenido por
separado — **dos extracciones independientes dan el mismo número**, que es la única razón por
la que la regla se puede usar sin volver a comprobarla tipo por tipo.

**Cómo leer las tablas.** «Rango» está en la notación de `midi.xml` traducida: crudo es lo que
viaja por SysEx, mostrado es lo que ve el usuario. «Escala» dice qué clase de
`dev.alonx3.ktnacontrol.protocol.LevelScale` corresponde, o `enum N` si es un selector
(`KatanaEnumParameter`, con la lista completa de valores en el catálogo de más abajo). Para la
dirección de FX, sumar `0x0200` a la de Mod. La columna `midi.xml` es la línea del `<DATA>`.

⚠️ **Todas estas direcciones solo significan lo que dice la tabla mientras el tipo activo del
slot sea el de la tabla.** Mod y FX son "DSP complejo" (§5.2): cada tipo reutiliza el mismo
espacio de direcciones. Es la misma salvedad que ya trae el Pre Delay de 2x2 Chorus, el único
parámetro interno de Mod cableado hasta ahora — con cualquier otro tipo activo, `60 00 02 3A`
no es un Pre Delay, es otra cosa.

###### Touch Wah — `ModFxType.TOUCH_WAH` (`0x00`) — `MOD TW:` — 7 params, 7 direcciones

Mod `60 00 01 02`–`01 08` · FX `60 00 03 02`–`03 08` · midi.xml 37957-37977

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 02` | Mode | `00` LPF · `01` BPF | enum 2 | 37957 |
| `60 00 01 03` | Polarity | `00` Down · `01` Up | enum 2 | 37961 |
| `60 00 01 04` | Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37965 |
| `60 00 01 05` | Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37968 |
| `60 00 01 06` | Peak | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37971 |
| `60 00 01 07` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37974 |
| `60 00 01 08` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37977 |

###### Auto Wah — `ModFxType.AUTO_WAH` (`0x01`) — `MOD AW:` — 7 params, 7 direcciones

Mod `60 00 01 09`–`01 0F` · FX `60 00 03 09`–`03 0F` · midi.xml 37980-37999

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 09` | Mode | `00` LPF · `01` BPF | enum 2 | 37980 |
| `60 00 01 0A` | Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37984 |
| `60 00 01 0B` | Peak | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37987 |
| `60 00 01 0C` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37990 |
| `60 00 01 0D` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37993 |
| `60 00 01 0E` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37996 |
| `60 00 01 0F` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37999 |

###### Pedal Wah — `ModFxType.PEDAL_WAH` (`0x02`) — `MOD SWAH:` — 6 params, 6 direcciones

Mod `60 00 01 10`–`01 15` · FX `60 00 03 10`–`03 15` · midi.xml 38002-38022

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 10` | Type | `00` CRY WAH … `05` Reso WAH | enum 6 | 38002 |
| `60 00 01 11` | Pedal Pos | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38010 |
| `60 00 01 12` | Pedal Min | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38013 |
| `60 00 01 13` | Pedal Max | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38016 |
| `60 00 01 14` | Effect Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38019 |
| `60 00 01 15` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38022 |

###### Compressor — `ModFxType.COMPRESSOR` (`0x03`) — `MOD ACS:` — 5 params, 5 direcciones

Mod `60 00 01 16`–`01 1A` · FX `60 00 03 16`–`03 1A` · midi.xml 38025-38043

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 16` | Type | `00` BOSS Comp … `06` Mild | enum 7 | 38025 |
| `60 00 01 17` | Sustain | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38034 |
| `60 00 01 18` | Attack | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38037 |
| `60 00 01 19` | Tone | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38040 |
| `60 00 01 1A` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38043 |

###### Limiter — `ModFxType.LIMITER` (`0x04`) — `MOD LM:` — 6 params, 6 direcciones

Mod `60 00 01 1B`–`01 20` · FX `60 00 03 1B`–`03 20` · midi.xml 38046-38080

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 1B` | Type | `00` BOSS Limiter · `01` Rack 160D · `02` Vtg Rack U | enum 3 | 38046 |
| `60 00 01 1C` | Attack | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38051 |
| `60 00 01 1D` | Thresh | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38054 |
| `60 00 01 1E` | Ratio | `00` 1:1 … `11` oo:1 | enum 18 | 38057 |
| `60 00 01 1F` | Release | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38077 |
| `60 00 01 20` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38080 |

###### Graphic EQ — `ModFxType.GRAPHIC_EQ` (`0x06`) — `MOD GEQ:` — 11 params, 11 direcciones

Mod `60 00 01 21`–`01 2B` · FX `60 00 03 21`–`03 2B` · midi.xml 38083-38113

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 21` | 31Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38083 |
| `60 00 01 22` | 62Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38086 |
| `60 00 01 23` | 125Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38089 |
| `60 00 01 24` | 250Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38092 |
| `60 00 01 25` | 500Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38095 |
| `60 00 01 26` | 1KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38098 |
| `60 00 01 27` | 2KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38101 |
| `60 00 01 28` | 4KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38104 |
| `60 00 01 29` | 8KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38107 |
| `60 00 01 2A` | 16KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38110 |
| `60 00 01 2B` | Level | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38113 |

###### Parametric EQ — `ModFxType.PARAMETRIC_EQ` (`0x07`) — `MOD PEQ:` — 11 params, 11 direcciones

Mod `60 00 01 2C`–`01 36` · FX `60 00 03 2C`–`03 36` · midi.xml 38116-38241

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 2C` | Lo Cut Off | `00` FLAT … `11` 800Hz | enum 18 | 38116 |
| `60 00 01 2D` | Lo Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38136 |
| `60 00 01 2E` | Lo Mid Freq | `00` 20.0Hz … `1B` 10.0k | enum 28 | 38139 |
| `60 00 01 2F` | Lo Mid Q | `00` 0.5 … `05` 16 | enum 6 | 38169 |
| `60 00 01 30` | Lo Mid Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38177 |
| `60 00 01 31` | Hi Mid Freq | `00` 20.0Hz … `1B` 10.0k | enum 28 | 38180 |
| `60 00 01 32` | Hi Mid Q | `00` 0.5 … `05` 16 | enum 6 | 38210 |
| `60 00 01 33` | Hi Mid Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38218 |
| `60 00 01 34` | Hi Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38221 |
| `60 00 01 35` | Hi Cut Off | `00` 630Hz … `0E` FLAT | enum 15 | 38224 |
| `60 00 01 36` | Level | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38241 |

###### Guitar Sim — `ModFxType.GUITAR_SIM` (`0x09`) — `MOD GS:` — 5 params, 5 direcciones

Mod `60 00 01 37`–`01 3B` · FX `60 00 03 37`–`03 3B` · midi.xml 38244-38263

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 37` | Type | `00` S->H … `07` P->AC | enum 8 | 38244 |
| `60 00 01 38` | Low | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38254 |
| `60 00 01 39` | High | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38257 |
| `60 00 01 3A` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38260 |
| `60 00 01 3B` | Body | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38263 |

###### Slow Gear — `ModFxType.SLOW_GEAR` (`0x0A`) — `MOD SG:` — 3 params, 3 direcciones

Mod `60 00 01 3C`–`01 3E` · FX `60 00 03 3C`–`03 3E` · midi.xml 38266-38272

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 3C` | Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38266 |
| `60 00 01 3D` | Rise Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38269 |
| `60 00 01 3E` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38272 |

###### Wave Synth — `ModFxType.WAVE_SYNTH` (`0x0C`) — `MOD WSY:` — 8 params, 8 direcciones

Mod `60 00 01 3F`–`01 46` · FX `60 00 03 3F`–`03 46` · midi.xml 38275-38297

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 3F` | Wave | `00` SAW · `01` SQUARE | enum 2 | 38275 |
| `60 00 01 40` | Cutoff Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38279 |
| `60 00 01 41` | Reson. | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38282 |
| `60 00 01 42` | FLT.Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38285 |
| `60 00 01 43` | FLT.Decay | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38288 |
| `60 00 01 44` | FLT.Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38291 |
| `60 00 01 45` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38294 |
| `60 00 01 46` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38297 |

###### Octave — `ModFxType.OCTAVE` (`0x0E`) — `MOD OC:` — 3 params, 3 direcciones

Mod `60 00 01 47`–`01 49` · FX `60 00 03 47`–`03 49` · midi.xml 38300-38309

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 47` | Range | `00` 1 · `01` 2 · `02` 3 · `03` 4 | enum 4 | 38300 |
| `60 00 01 48` | Octave | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38306 |
| `60 00 01 49` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38309 |

###### Pitch Shifter — `ModFxType.PITCH_SHIFTER` (`0x0F`) — `MOD PS:` — 13 params, 15 direcciones

Mod `60 00 01 4A`–`01 58` · FX `60 00 03 4A`–`03 58` · midi.xml 38312-38375

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 4A` | Voice | `00` 1-Voice · `01` 2-Mono | enum 2 | 38312 |
| `60 00 01 4B` | Mode | `00` Fast · `01` Medium · `02` Slow · `03` Mono | enum 4 | 38316 |
| `60 00 01 4C` | Pitch | crudo `00`..`30` = `-24`..`+24` | `LevelScale.centered(24)` | 38322 |
| `60 00 01 4D` | Fine | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38325 |
| `60 00 01 4E`+`4F` | Pre Delay (Voice 1) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38328-38337 |
| `60 00 01 50` | Voice 1 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38341 |
| `60 00 01 51` | Mode | `00` Fast · `01` Medium · `02` Slow · `03` Mono | enum 4 | 38344 |
| `60 00 01 52` | Pitch | crudo `00`..`30` = `-24`..`+24` | `LevelScale.centered(24)` | 38350 |
| `60 00 01 53` | Fine | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38353 |
| `60 00 01 54`+`55` | Pre Delay (Voice 2) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38356-38365 |
| `60 00 01 56` | Voice 2 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38369 |
| `60 00 01 57` | Feedback | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38372 |
| `60 00 01 58` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38375 |


###### Harmonist — `ModFxType.HARMONIST` (`0x10`) — `MOD HR:` — 9 params, 11 direcciones

Mod `60 00 01 59`–`01 63` · FX `60 00 03 59`–`03 63` · midi.xml 38378-38481

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 59` | Voice | `00` 1-Voice · `01` 2-Voice | enum 2 | 38378 |
| `60 00 01 5A` | Harmony | `00` -2oct … `1D` User | enum 30 | 38382 |
| `60 00 01 5B`+`5C` | Pre Delay (Voice 1) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38414-38423 |
| `60 00 01 5D` | Voice 1 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38427 |
| `60 00 01 5E` | Harmony | `00` -2oct … `1D` User | enum 30 | 38430 |
| `60 00 01 5F`+`60` | Pre Delay (Voice 2) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38462-38471 |
| `60 00 01 61` | Voice 2 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38475 |
| `60 00 01 62` | FeedBack | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38478 |
| `60 00 01 63` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38481 |


###### Acu Processor — `ModFxType.ACU_PROCESSOR` (`0x12`) — `MOD AC:` — 7 params, 7 direcciones

Mod `60 00 01 7C`–`02 02` · FX `60 00 03 7C`–`04 02` · midi.xml 39708-39760

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 7C` | Type | `00` Small · `01` Medium · `02` Bright · `03` Power | enum 4 | 39708 |
| `60 00 01 7D` | Bass | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39714 |
| `60 00 01 7E` | Middle | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39717 |
| `60 00 01 7F` | Mid.Freq | `00` 20.0Hz … `1B` 10.0kHz | enum 28 | 39720 |
| `60 00 02 00` | Treble | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39754 |
| `60 00 02 01` | Presence | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39757 |
| `60 00 02 02` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39760 |

###### Phaser — `ModFxType.PHASER` (`0x13`) — `MOD PH:` — 8 params, 8 direcciones

Mod `60 00 02 03`–`02 0A` · FX `60 00 04 03`–`04 0A` · midi.xml 39763-39789

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 03` | Type | `00` 4stage · `01` 8stage · `02` 12stage · `03` Bi-Phase | enum 4 | 39763 |
| `60 00 02 04` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39769 |
| `60 00 02 05` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39772 |
| `60 00 02 06` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39775 |
| `60 00 02 07` | Reson. | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39778 |
| `60 00 02 08` | Step Rate | `00` = Off, luego crudo `01`..`65` = `00`..`100` | `LevelScale.offThenOneBased()` | 39781 |
| `60 00 02 09` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39786 |
| `60 00 02 0A` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39789 |

###### Flanger — `ModFxType.FLANGER` (`0x14`) — `MOD FL:` — 8 params, 8 direcciones

Mod `60 00 02 0B`–`02 12` · FX `60 00 04 0B`–`04 12` · midi.xml 39792-39823

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 0B` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39792 |
| `60 00 02 0C` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39795 |
| `60 00 02 0D` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39798 |
| `60 00 02 0E` | Reson. | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39801 |
| `60 00 02 0F` | Separ | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39804 |
| `60 00 02 10` | Low Cut | `00` FLAT … `0A` 800Hz | enum 11 | 39807 |
| `60 00 02 11` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39820 |
| `60 00 02 12` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39823 |

###### Tremolo — `ModFxType.TREMOLO` (`0x15`) — `MOD TR:` — 4 params, 4 direcciones

Mod `60 00 02 13`–`02 16` · FX `60 00 04 13`–`04 16` · midi.xml 39826-39835

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 13` | Shape | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39826 |
| `60 00 02 14` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39829 |
| `60 00 02 15` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39832 |
| `60 00 02 16` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39835 |

###### Rotary — `ModFxType.ROTARY` (`0x16`) — `MOD RT:` — 7 params, 7 direcciones

Mod `60 00 02 17`–`02 1D` · FX `60 00 04 17`–`04 1D` · midi.xml 39838-39857

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 17` | Speed | `00` Slow · `01` Fast | enum 2 | 39838 |
| `60 00 02 18` | Rate (Slow) | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39842 |
| `60 00 02 19` | Rate (Fast) | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39845 |
| `60 00 02 1A` | Rise Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39848 |
| `60 00 02 1B` | Fall Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39851 |
| `60 00 02 1C` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39854 |
| `60 00 02 1D` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39857 |

###### Uni-V — `ModFxType.UNI_V` (`0x17`) — `MOD UV:` — 3 params, 3 direcciones

Mod `60 00 02 1E`–`02 20` · FX `60 00 04 1E`–`04 20` · midi.xml 39860-39866

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 1E` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39860 |
| `60 00 02 1F` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39863 |
| `60 00 02 20` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39866 |

###### Slicer — `ModFxType.SLICER` (`0x19`) — `MOD SL:` — 5 params, 5 direcciones

Mod `60 00 02 21`–`02 25` · FX `60 00 04 21`–`04 25` · midi.xml 39869-39900

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 21` | Pattern | `00` P1 … `13` P20 | enum 20 | 39869 |
| `60 00 02 22` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39891 |
| `60 00 02 23` | Trig.Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39894 |
| `60 00 02 24` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39897 |
| `60 00 02 25` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39900 |

###### Vibrato — `ModFxType.VIBRATO` (`0x1A`) — `MOD VB:` — 5 params, 5 direcciones

Mod `60 00 02 26`–`02 2A` · FX `60 00 04 26`–`04 2A` · midi.xml 39903-39916

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 26` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39903 |
| `60 00 02 27` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39906 |
| `60 00 02 28` | Off/On | `00` Off · `01` On | enum 2 | 39909 |
| `60 00 02 29` | Rise Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39913 |
| `60 00 02 2A` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39916 |

###### Ring Modulate — `ModFxType.RING_MODULATE` (`0x1B`) — `MOD RM:` — 4 params, 4 direcciones

Mod `60 00 02 2B`–`02 2E` · FX `60 00 04 2B`–`04 2E` · midi.xml 39919-39929

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 2B` | Mode | `00` Normal · `01` Intelligent | enum 2 | 39919 |
| `60 00 02 2C` | Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39923 |
| `60 00 02 2D` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39926 |
| `60 00 02 2E` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39929 |

###### Humanizer — `ModFxType.HUMANIZER` (`0x1C`) — `MOD HU:` — 8 params, 8 direcciones

Mod `60 00 02 2F`–`02 36` · FX `60 00 04 2F`–`04 36` · midi.xml 39932-39962

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 2F` | Mode | `00` Picking · `01` Auto | enum 2 | 39932 |
| `60 00 02 30` | Vowel 1 | `00` A … `04` U | enum 5 | 39936 |
| `60 00 02 31` | Vowel 2 | `00` A … `04` U | enum 5 | 39943 |
| `60 00 02 32` | Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39950 |
| `60 00 02 33` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39953 |
| `60 00 02 34` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39956 |
| `60 00 02 35` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39959 |
| `60 00 02 36` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39962 |

###### 2x2 Chorus — `ModFxType.CHORUS` (`0x1D`) — `MOD 2CE:` — 10 params, 10 direcciones

Mod `60 00 02 37`–`02 40` · FX `60 00 04 37`–`04 40` · midi.xml 39965-40008

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 37` | Xover Freq | `00` 100Hz … `10` 4.00kHz | enum 17 | 39965 |
| `60 00 02 38` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39984 |
| `60 00 02 39` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39987 |
| `60 00 02 3A` | Pre Delay | crudo `00`..`50` = `0.0`..`40.0` ms | **fraccionaria** | 39990 |
| `60 00 02 3B` | Low | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39993 |
| `60 00 02 3C` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39996 |
| `60 00 02 3D` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39999 |
| `60 00 02 3E` | Pre Delay | crudo `00`..`50` = `0.0`..`40.0` ms | **fraccionaria** | 40002 |
| `60 00 02 3F` | High | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40005 |
| `60 00 02 40` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40008 |

###### AC Guitar Sim — `ModFxType.AC_GUITAR_SIM` (`0x1F`) — `MOD ACS:` — 5 params, 5 direcciones

Mod `60 00 02 41`–`02 45` · FX `60 00 04 41`–`04 45` · midi.xml 40011-40023

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 41` | Top | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 40011 |
| `60 00 02 42` | Body | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40014 |
| `60 00 02 43` | Low | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 40017 |
| `60 00 02 44` | High | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 40020 |
| `60 00 02 45` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40023 |

###### Phaser 90E — `ModFxType.PHASER_90E` (`0x23`) — `MOD PH90:` — 2 params, 2 direcciones

Mod `60 00 02 46`–`02 47` · FX `60 00 04 46`–`04 47` · midi.xml 40026-40030

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 46` | Script | `00` Off · `01` On | enum 2 | 40026 |
| `60 00 02 47` | Speed | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40030 |

###### Flanger 117E — `ModFxType.FLANGER_117E` (`0x24`) — `MOD FL117:` — 4 params, 4 direcciones

Mod `60 00 02 48`–`02 4B` · FX `60 00 04 48`–`04 4B` · midi.xml 40033-40042

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 48` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40033 |
| `60 00 02 49` | Width | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40036 |
| `60 00 02 4A` | Speed | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40039 |
| `60 00 02 4B` | Regeneration | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40042 |

###### Wah 95E — `ModFxType.WAH_95E` (`0x25`) — `MOD WAH95:` — 5 params, 5 direcciones

Mod `60 00 02 4C`–`02 50` · FX `60 00 04 4C`–`04 50` · midi.xml 40045-40057

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 4C` | Pedal Pos | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40045 |
| `60 00 02 4D` | Pedal Min | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40048 |
| `60 00 02 4E` | Pedal Max | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40051 |
| `60 00 02 4F` | Effect Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40054 |
| `60 00 02 50` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40057 |

###### DC30 — `ModFxType.DC30` (`0x26`) — `MOD DC30:` — 8 params, 9 direcciones

Mod `60 00 02 51`–`02 59` · FX `60 00 04 51`–`04 59` · midi.xml 40060-40098

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 51` | Selector | `00` Chorus · `01` Echo | enum 2 | 40060 |
| `60 00 02 52` | Input | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40064 |
| `60 00 02 53` | Intensity | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40067 |
| `60 00 02 54`+`55` | Repeat Rate | crudo 2 bytes = `40`..`600` rpm | `direct(40..600)`, **`byteWidth = 2`** | 40070-40085 |
| `60 00 02 56` | Intensity | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40089 |
| `60 00 02 57` | Volume | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40092 |
| `60 00 02 58` | Tone | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40095 |
| `60 00 02 59` | Output Select | `00` D+E · `01` D/E | enum 2 | 40098 |


###### Heavy Octave — `ModFxType.HEAVY_OCTAVE` (`0x27`) — `MOD HOC:` — 3 params, 3 direcciones

Mod `60 00 02 5A`–`02 5C` · FX `60 00 04 5A`–`04 5C` · midi.xml 40102-40108

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 5A` | Octave -1 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40102 |
| `60 00 02 5B` | Octave -2 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40105 |
| `60 00 02 5C` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40108 |

###### Pedal Bend — `ModFxType.PEDAL_BEND` (`0x28`) — `MOD PBEND:` — 4 params, 4 direcciones

Mod `60 00 02 5D`–`02 60` · FX `60 00 04 5D`–`04 60` · midi.xml 40111-40120

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 5D` | Pitch | crudo `00`..`30` = `-24`..`+24` | `LevelScale.centered(24)` | 40111 |
| `60 00 02 5E` | Pedal Posn | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40114 |
| `60 00 02 5F` | Effect Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40117 |
| `60 00 02 60` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40120 |

##### Catálogo de valores de los selectores internos

Todos los `enum N` de las tablas de arriba, con su lista completa, para no tener que volver al
XML. Las direcciones son las de **Mod**; para FX, `+0x0200`.

✅ **Los 36 selectores internos corren sin huecos**, comprobado por programa sobre la lista de
valores de cada uno. Es una diferencia real con los catálogos de *tipo* del proyecto —
`ModFxType` (10 huecos), `BoostType` (`07`) y `AmpType` (`19`) —: ahí `KatanaEnumParameter`
rechaza valores ilegales porque los hay; aquí un rango contiguo `0..N-1` describe el selector
entero. Aun así conviene registrarlos igual como `KatanaEnumParameter`, no como niveles: lo que
muestran es texto (`FLAT`, `1.60k`, `oo:1`), no un número en una escala.

- **`60 00 01 02` MOD TW: — Mode** (2 valores, L37957): `00` LPF, `01` BPF
- **`60 00 01 03` MOD TW: — Polarity** (2 valores, L37961): `00` Down, `01` Up
- **`60 00 01 09` MOD AW: — Mode** (2 valores, L37980): `00` LPF, `01` BPF
- **`60 00 01 10` MOD SWAH: — Type** (6 valores, L38002): `00` CRY WAH, `01` VO WAH, `02` Fat WAH, `03` Light WAH, `04` 7string WAH, `05` Reso WAH
- **`60 00 01 16` MOD ACS: — Type** (7 valores, L38025): `00` BOSS Comp, `01` Hi-BAND, `02` Light, `03` D-Comp, `04` Orange, `05` Fat, `06` Mild
- **`60 00 01 1B` MOD LM: — Type** (3 valores, L38046): `00` BOSS Limiter, `01` Rack 160D, `02` Vtg Rack U
- **`60 00 01 1E` MOD LM: — Ratio** (18 valores, L38057): `00` 1:1, `01` 1.2:1, `02` 1.4:1, `03` 1.6:1, `04` 1.8:1, `05` 2:1, `06` 2.3:1, `07` 2.6:1, `08` 3:1, `09` 3.5:1, `0A` 4:1, `0B` 5:1, `0C` 6:1, `0D` 8:1, `0E` 10:1, `0F` 12:1, `10` 20:1, `11` oo:1
- **`60 00 01 2C` MOD PEQ: — Lo Cut Off** (18 valores, L38116): `00` FLAT, `01` 20.0Hz, `02` 25.0Hz, `03` 31.5Hz, `04` 40.0Hz, `05` 50.0Hz, `06` 63.0Hz, `07` 80.0Hz, `08` 100Hz, `09` 125Hz, `0A` 160Hz, `0B` 200Hz, `0C` 250Hz, `0D` 315Hz, `0E` 400Hz, `0F` 500Hz, `10` 630Hz, `11` 800Hz
- **`60 00 01 2E` MOD PEQ: — Lo Mid Freq** (28 valores, L38139): `00` 20.0Hz, `01` 25.0Hz, `02` 31.5Hz, `03` 40.0Hz, `04` 50.0Hz, `05` 63.0Hz, `06` 80.0Hz, `07` 100Hz, `08` 125Hz, `09` 160Hz, `0A` 200Hz, `0B` 250Hz, `0C` 315Hz, `0D` 400Hz, `0E` 500Hz, `0F` 630Hz, `10` 800Hz, `11` 1.00k, `12` 1.25k, `13` 1.60k, `14` 2.00k, `15` 2.50k, `16` 3.15k, `17` 4.00k, `18` 5.00k, `19` 6.30k, `1A` 8.00k, `1B` 10.0k
- **`60 00 01 2F` MOD PEQ: — Lo Mid Q** (6 valores, L38169): `00` 0.5, `01` 1, `02` 2, `03` 4, `04` 8, `05` 16
- **`60 00 01 31` MOD PEQ: — Hi Mid Freq** (28 valores, L38180): `00` 20.0Hz, `01` 25.0Hz, `02` 31.5Hz, `03` 40.0Hz, `04` 50.0Hz, `05` 63.0Hz, `06` 80.0Hz, `07` 100Hz, `08` 125Hz, `09` 160Hz, `0A` 200Hz, `0B` 250Hz, `0C` 315Hz, `0D` 400Hz, `0E` 500Hz, `0F` 630Hz, `10` 800Hz, `11` 1.00k, `12` 1.25k, `13` 1.60k, `14` 2.00k, `15` 2.50k, `16` 3.15k, `17` 4.00k, `18` 5.00k, `19` 6.30k, `1A` 8.00k, `1B` 10.0k
- **`60 00 01 32` MOD PEQ: — Hi Mid Q** (6 valores, L38210): `00` 0.5, `01` 1, `02` 2, `03` 4, `04` 8, `05` 16
- **`60 00 01 35` MOD PEQ: — Hi Cut Off** (15 valores, L38224): `00` 630Hz, `01` 800Hz, `02` 1.00k, `03` 1.25k, `04` 1.60k, `05` 2.00k, `06` 2.50k, `07` 3.15k, `08` 4.00k, `09` 5.00k, `0A` 6.00k, `0B` 8.00k, `0C` 10.0k, `0D` 12.5k, `0E` FLAT
- **`60 00 01 37` MOD GS: — Type** (8 valores, L38244): `00` S->H, `01` H->S, `02` H->HF, `03` S->Hollow, `04` H->Hollow, `05` S->AC, `06` H->AC, `07` P->AC
- **`60 00 01 3F` MOD WSY: — Wave** (2 valores, L38275): `00` SAW, `01` SQUARE
- **`60 00 01 47` MOD OC: — Range** (4 valores, L38300): `00` 1, `01` 2, `02` 3, `03` 4
- **`60 00 01 4A` MOD PS: — Voice** (2 valores, L38312): `00` 1-Voice, `01` 2-Mono
- **`60 00 01 4B` MOD PS: — Mode** (4 valores, L38316): `00` Fast, `01` Medium, `02` Slow, `03` Mono
- **`60 00 01 59` MOD HR: — Voice** (2 valores, L38378): `00` 1-Voice, `01` 2-Voice
- **`60 00 01 5A` MOD HR: — Harmony** (30 valores, L38382): `00` -2oct, `01` -14th, `02` -13th, `03` -12th, `04` -11th, `05` -10th, `06` -9th, `07` -1oct, `08` -7th, `09` -6th, `0A` -5th, `0B` -4th, `0C` -3rd, `0D` -2nd, `0E` Unison, `0F` +2nd, `10` +3rd, `11` +4th, `12` +5th, `13` +6th, `14` +7th, `15` +1oct, `16` +9th, `17` +10th, `18` +11th, `19` +12th, `1A` +13th, `1B` +14th, `1C` +2oct, `1D` User
- **`60 00 01 7C` MOD AC: — Type** (4 valores, L39708): `00` Small, `01` Medium, `02` Bright, `03` Power
- **`60 00 02 03` MOD PH: — Type** (4 valores, L39763): `00` 4stage, `01` 8stage, `02` 12stage, `03` Bi-Phase
- **`60 00 02 10` MOD FL: — Low Cut** (11 valores, L39807): `00` FLAT, `01` 55.0Hz, `02` 110Hz, `03` 165Hz, `04` 200Hz, `05` 280Hz, `06` 340Hz, `07` 400Hz, `08` 500Hz, `09` 630Hz, `0A` 800Hz
- **`60 00 02 17` MOD RT: — Speed** (2 valores, L39838): `00` Slow, `01` Fast
- **`60 00 02 21` MOD SL: — Pattern** (20 valores, L39869): `00` P1, `01` P2, `02` P3, `03` P4, `04` P5, `05` P6, `06` P7, `07` P8, `08` P9, `09` P10, `0A` P11, `0B` P12, `0C` P13, `0D` P14, `0E` P15, `0F` P16, `10` P17, `11` P18, `12` P19, `13` P20
- **`60 00 02 28` MOD VB: — Off/On** (2 valores, L39909): `00` Off, `01` On
- **`60 00 02 2B` MOD RM: — Mode** (2 valores, L39919): `00` Normal, `01` Intelligent
- **`60 00 02 2F` MOD HU: — Mode** (2 valores, L39932): `00` Picking, `01` Auto
- **`60 00 02 30` MOD HU: — Vowel 1** (5 valores, L39936): `00` A, `01` E, `02` I, `03` O, `04` U
- **`60 00 02 37` MOD 2CE: — Xover Freq** (17 valores, L39965): `00` 100Hz, `01` 125Hz, `02` 160Hz, `03` 200Hz, `04` 250Hz, `05` 315Hz, `06` 400Hz, `07` 500Hz, `08` 630Hz, `09` 800Hz, `0A` 1.00kHz, `0B` 1.25kHz, `0C` 1.60kHz, `0D` 2.00kHz, `0E` 2.50kHz, `0F` 3.15kHz, `10` 4.00kHz
- **`60 00 02 46` MOD PH90: — Script** (2 valores, L40026): `00` Off, `01` On
- **`60 00 02 51` MOD DC30: — Selector** (2 valores, L40060): `00` Chorus, `01` Echo
- **`60 00 02 59` MOD DC30: — Output Select** (2 valores, L40098): `00` D+E, `01` D/E

Son **33 listas distintas sobre 36 selectores**: los tres que faltan aquí repiten una de
arriba y no se duplican para no dar la impresión de que son otra cosa — `MOD PS: Mode` de la
segunda voz (`60 00 01 51`, misma lista que `01 4B`), `MOD HR: Harmony` de la segunda voz
(`60 00 01 5E`, misma lista que `01 5A`) y `MOD HU: Vowel 2` (`60 00 02 31`, misma lista que
Vowel 1). Son parámetros reales y hay que cablearlos; solo su catálogo está compartido.

⚠️ **`MOD PEQ: Hi Cut Off` (`60 00 01 35`) es la misma lista de 15 frecuencias que
`DelayHighCutFrequency` / `ReverbHighCutFrequency`, y desempata la contradicción documentada
entre ellas.** El proyecto mantiene esos dos enums separados a propósito porque `midi.xml` da
`0A` = `"6.30K"` en el bloque de Delay 1 y `"6.00k"` en el de Reverb (§5.2, "Parámetros
internos fijos de Delay 1 y Reverb"). Este tercer sitio, independiente de los dos, dice
**`0A` = `6.00k`** — dos fuentes contra una dentro del mismo fichero. No cambia la decisión de
tener dos enums (sigue sin haber prueba de hardware, y la fuente sigue contradiciéndose sola),
pero si algún día hay que apostar, `6.00k` es la que va ganando. Lo mismo con
`MOD PEQ: Lo Cut Off` (`60 00 01 2C`), que coincide byte a byte con `ReverbLowCutFrequency`.

##### Lo que NO encaja en el patrón "DSP simple", explícitamente

Reescrito con la extracción completa. **Dos entradas de la lista anterior eran incorrectas y se
corrigen aquí**; el resto se confirma y hay una nueva.

1. ⚠️ **`Pre Delay` de paso fraccionario: solo 2x2 Chorus.** `range 00/50/0.0/40.0` es crudo
   `0x00`..`0x50` mostrado como `0.0`..`40.0` ms, en pasos de 0,5 ms. Afecta a `60 00 02 3A` y
   `60 00 02 3E`, y **a nada más**. Ya resuelto con `FractionalLevelScale` /
   `KatanaFractionalParameter` (§5.2, "Escala de paso fraccionario"), que es justamente el
   parámetro que se cableó.
   ❌ **Corrección**: la versión anterior de esta lista añadía aquí "y a los Pre Delay de Pitch
   Shifter y Harmonist". **Es falso.** Los de PS y HR son `00/7F/00/127 ms` + `128/255` +
   `256/300`: milisegundos enteros, paso 1, sin parte fraccionaria. Son el punto 2, no este.
   El error venía de agrupar por el nombre del parámetro (`Pre Delay`) en vez de por su rango.
2. ⚠️ **Parámetros de 2 bytes: cinco, no cuatro.** El esquema es el MSB×128+LSB que ya usan
   `ACTIVE_CHANNEL` (§5.1) y `DELAY_TIME`, y está resuelto en código con el `byteWidth = 2` de
   `KatanaControl`. `midi.xml` los modela con `<DATA>` anidados, un hijo por tramo del MSB, y
   por eso los bloques tienen huecos en la numeración principal:

   | Parámetro | Dirección (MSB+LSB) | Hueco | Rango | Unidad | midi.xml |
   | --- | --- | --- | --- | --- | --- |
   | PS Pre Delay Voice 1 | `60 00 01 4E`+`4F` | `4E`→`50` | `0`..`300` | ms | 38328-38337 |
   | PS Pre Delay Voice 2 | `60 00 01 54`+`55` | `54`→`56` | `0`..`300` | ms | 38356-38365 |
   | HR Pre Delay Voice 1 | `60 00 01 5B`+`5C` | `5B`→`5D` | `0`..`300` | ms | 38414-38423 |
   | HR Pre Delay Voice 2 | `60 00 01 5F`+`60` | `5F`→`61` | `0`..`300` | ms | 38462-38471 |
   | **DC30 Repeat Rate** | `60 00 02 54`+`55` | `54`→`56` | **`40`..`600`** | **rpm** | 40070-40085 |

   🆕 **El de DC30 es nuevo: no estaba en la lista anterior, que daba cuatro.** Y es el más
   raro de los cinco, por dos motivos. Primero, **su mínimo crudo no es cero**: el tramo
   `MSB=00` arranca en `28` (`range 28/7F/40/127`), así que los valores `0`..`39` no existen y
   una escala que asuma que el recorrido empieza en `0` se sale por abajo. Segundo, **la unidad
   son rpm**, no ms — es la velocidad del altavoz rotatorio que emula el pedal, no un tiempo de
   retardo. Los cinco tramos: `00`→40-127, `01`→128-255, `02`→256-383, `03`→384-511,
   `04`→512-599, más el valor suelto `04`/`58`→600.
   ⚠️ Y su bloque anidado **está mal etiquetado en la fuente**: `midi.xml` lo llama
   `SDD:Delay Time(LSB)`, copiado del bloque de delay (SDD es una unidad de delay, no parte del
   DC30). La etiqueta miente; la dirección, el rango y la unidad `rpm` de los `<PARAM>` no.
3. ⚠️ **Harmonist arrastra 24 direcciones de escala de usuario**, `60 00 01 64`–`01 7B`: doce
   notas cromáticas × dos voces, 49 valores cada una, desde `midi.xml:38484`. Confirmado. Son
   una tabla de mapeo musical, no controles de panel — se pueden dejar fuera sin perder el
   efecto, igual que se dejó fuera Custom Type del Booster. Solo se activan cuando `Harmony`
   vale `1D` (`User`).
4. ⚠️ **Acu Processor cruza el límite de página**, de `60 00 01 7C` a `60 00 02 02`.
   Confirmado, y sigue siendo el único bloque que lo hace. La aritmética base 128 de `Address`
   lo cubre sola (`01 7F + 1 = 02 00`); solo rompería si alguien calculara direcciones sumando
   al último byte a mano.
5. ⚠️ **Nombres repetidos dentro de un bloque, que solo la posición desambigua.** Tres casos,
   uno más que antes:
   - **2x2 Chorus**: `Rate`/`Depth`/`Pre Delay` aparecen dos veces (`02 38`-`3A` y
     `02 3C`-`3E`). Los delimitan los niveles de banda que cierran cada grupo, `02 3B` Low y
     `02 3F` High. El "(banda Low/High)" de la tabla es interpretación nuestra, razonada pero no
     literal de la fuente — si alguna vez suena cruzado, es el primer sitio donde mirar.
   - **DC30**: `Intensity` aparece en `02 53` y otra vez en `02 56`, separadas por el
     `Repeat Rate` de 2 bytes. El pedal tiene dos mitades y `02 51` `Selector` (`00` Chorus /
     `01` Echo) dice cuál está sonando, así que lo más probable es que `53` sea la del chorus y
     `56` la del echo — otra vez posición, no fuente.
   - 🆕 **Pitch Shifter y Harmonist**: `Mode`, `Pitch`, `Fine`, `Pre Delay` y `Harmony` se
     repiten por voz. El reparto lo marcan los niveles `PS Voice 1` (`01 50`) y `PS Voice 2`
     (`01 56`), `HR Voice 1` (`01 5D`) y `HR Voice 2` (`01 61`), que cierran cada grupo igual
     que en 2x2 Chorus. Ojo con `60 00 01 4A`, que se llama `Voice` a secas y **no** es un
     nivel: es el selector de cuántas voces suenan (`00` 1-Voice, `01` 2-Mono).
6. ⚠️ **Los "sneaky" tienen 2–5 parámetros**, no el juego completo: Phaser 90E dos, Flanger
   117E cuatro, Wah 95E cinco. Confirmado. Son emulaciones de pedales concretos con un par de
   perillas.
7. 🆕 **Vibrato lleva su propio `Off/On` interno** en `60 00 02 28`, en mitad del bloque y
   distinto del on/off del slot de Mod (`60 00 01 00`). Es el switch del propio pedal emulado.
   No confundirlos: apagar uno no es apagar el otro.
8. 🆕 **Cuatro escalas centradas distintas conviven en estos bloques**, y la radio no se puede
   suponer por el nombre del parámetro: `-50..+50` (`LevelScale.centered(50)`, crudo `00`..`64`
   — Tone, Low, High, Top, Bass, Middle, Treble, Presence, Fine), `-20..+20` en dB
   (`centered(20)`, crudo `00`..`28` — todo Graphic EQ y las ganancias del Parametric EQ) y
   `-24..+24` (`centered(24)`, crudo `00`..`30` — Pitch de Pitch Shifter y de Pedal Bend). El
   ancho del crudo es lo que las distingue, no la etiqueta.
9. 🆕 **`Step Rate` del Phaser (`60 00 02 08`) es el único `offThenOneBased` del lote**, con la
   forma `00` = Off y luego crudo `01`..`65` = `0`..`100` — exactamente la escala que ya existe
   para los cinco niveles de efecto del panel. Ningún otro parámetro interno de Mod/FX la usa.

**Dos rarezas de la fuente**, ninguna de dirección, encontradas al comparar Mod contra FX y
confirmadas en la extracción completa — son las 14 diferencias de etiqueta del cuadro de
verificación:

- **`midi.xml` llama `ACS:` a dos tipos distintos en el bloque Mod**: Compressor (`01 16`) y
  AC Guitar Sim (`02 41`). El bloque **FX desambigua** y usa `AGS:` para el segundo
  (`03 16` vs `04 41`). Se resuelve por posición y por los nombres de parámetro, que no se
  parecen en nada (Sustain/Attack/Tone/Effect contra Top/Body/Low/High/Level); el catálogo de
  `ModFxType` ya los tiene como tipos separados. **5 nodos.**
- Las etiquetas de los `Pre Delay` anidados están **copiadas mal en FX**: donde Mod dice
  `PS :Voice2:Pre Delay(LSB)`, `HR :Voice1:Pre Delay` y `HR :Voice2:Pre Delay`, FX repite
  `PS :Voice1:Pre Delay` en los tres. Direcciones, rangos y estructura idénticas; solo el texto
  está mal. **9 nodos.** (Y en Mod, el de Voice 2 es el único de los cuatro que lleva `(LSB)`
  en el nombre — la fuente tampoco es consistente consigo misma.)

#### Parámetros internos fijos de Delay 1 y Reverb (implementado, 2026-09-05)

⚠️ **Implementado, pendiente de confirmar con audio** — ver BACKLOG.md, "Pendiente por
probar". Delay 1 y Reverb son "DSP simple" igual que Booster (§5.2 arriba): un único bloque
fijo de direcciones, el mismo para cualquier tipo activo, sin el desdoblamiento por tipo de
Mod/FX. **Fuente única**: `reference/FxFloorboard/midi.xml:42413-42488` (Delay 1, bloque
`desc="DD1:"`) y `:42860-42935` (Reverb, bloque `desc="REV:"`/`"REVERB:"`) — ninguno de estos
parámetros aparece en `reference/TuxKatana/params/delay.yaml` ni `reverb.yaml` más allá de lo
ya implementado (On/Off, Type, color, nivel de panel), ni en `Adresses.txt`.

**Delay 1** (`KatanaAddresses.DELAY_TIME`–`DELAY_DIRECT_MIX`):

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 05 02`–`03` | Time (2 bytes) | `1..2000` ms, directo | 42413-42461 |
| `60 00 05 04` | Feedback | `00/64` = 0..100 | 42465 |
| `60 00 05 05` | High Cut | enum 15: `00` 630Hz…`0E` FLAT | 42468-42482 |
| `60 00 05 06` | Effect | `00/78` = 0..120 | 42485 |
| `60 00 05 07` | Direct | `00/64` = 0..100 | 42488 |

**Reverb** (`KatanaAddresses.REVERB_PRE_DELAY`–`REVERB_DIRECT_MIX`):

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 05 42` | Time | ✅ cableado (2026-09-05), escala fraccionaria | 42873 |
| `60 00 05 43`–`44` | Pre Delay (2 bytes) | `0..500` ms, directo | 42876-42891 |
| `60 00 05 45` | Low Cut | enum 18: `00` FLAT…`11` 800Hz | 42892-42906 |
| `60 00 05 46` | High Cut | enum 15: `00` 630Hz…`0E` FLAT | 42912-42922 |
| `60 00 05 47` | Density | `00/0A` = 0..10 (no 0..100) | 42925 |
| `60 00 05 48` | Effect | ❌ **no cableado**, ver abajo | 42928 |
| `60 00 05 49` | Direct Mix | `00/64` = 0..100 | 42931 |

Tap Time y los parámetros específicos de tipo (X/Y-channel, Mod, SDE) quedan fuera de Delay 1
a propósito, igual que Spring Color queda fuera de Reverb: son sub-modos de un tipo concreto,
no parte del bloque fijo.

**Delay Time (`60 00 05 02`) tiene un tramo de la fuente que no cuadra, y no bloqueó la
implementación.** El esquema es el mismo MSB×128+LSB de 2 bytes que ya usa
`KatanaAddresses.ACTIVE_CHANNEL` (§5.1): 16 posiciones del MSB (`00`..`0F`), cada una con un
sub-rango de milisegundos del LSB. 15 de los 16 tramos son limpios —anchura del crudo igual a
la del mostrado—, pero `MSB=0x0E` documenta crudo `00`..`4F` (80 valores) mostrado como
`1792`-`1919` ms (128 valores): un ancho que no coincide con ningún otro tramo, ni con el de
al lado (`MSB=0x0F`: crudo `00`..`4F` → `1920`-`1999`, ese sí encaja). La hipótesis más
probable es un error de copia en esa fila de `midi.xml` (el hueco de 48 es justo lo que
sobraría si el crudo real fuera `0x7F`, no `0x4F`) y no una resolución real distinta — pero es
solo una lectura, no una prueba. Se cableó igual con `LevelScale.direct(1..2000)` porque
`MidiBytes` ya decodifica cualquier valor del rango entero de 14 bits sin escala nueva; lo
pendiente es confirmar con audio específicamente el tramo `1792`-`1999` ms. Ver el KDoc de
`KatanaAddresses.DELAY_TIME`.

`REVERB_PRE_DELAY`, con la misma estructura de 4 tramos, **no tiene esta irregularidad**: los
cuatro son limpios de punta a punta, el último incluido (`00`..`73` → `384`-`499`, más el
valor especial `74` → `500` que encaja exacto). Eso refuerza que lo de Delay Time es un fallo
puntual de la fuente y no una propiedad del esquema de 2 bytes en sí.

✅ **Reverb Time (`60 00 05 42`), cableado el 2026-09-05 tras extender la escala.**
`midi.xml:42873` da `range 00/63/0.1/10.0 sec`: crudo `0x00`..`0x63` mostrado como
`0.1`..`10.0` segundos, es decir `mostrado = (crudo + 1) / 10` — un paso de 0.1s, exactamente
el mismo tipo de anomalía que el Pre Delay de 0.5ms de Mod (más arriba en esta sección).
`LevelScale` solo sabe sumar un desplazamiento entero, no dividir, así que no podía
representar este paso; se quedó documentado y sin cablear una temporada, por instrucción
explícita del proyecto, hasta que se resolviera la escala en vez de forzarlo como si fuera
`0..100` directo. Ver "Escala de paso fraccionario" más abajo para cómo se resolvió.
⚠️ **Implementado, sin confirmar con audio.**

❌ **Reverb Effect Level (`60 00 05 48`) es la misma dirección que `REVERB_LEVEL_DERIVED`,
ya probada como no funcional (más arriba, "Intento descartado 2").** No es una dirección
nueva sin identificar: es el "Effect Level" del bloque interno de Reverb, entre Density
(`47`) y Direct Mix (`49`). Esto explica el porqué de aquel resultado — no es que la dirección
esté muerta, es que **no es la perilla del panel** (`REVERB_LEVEL`, `60 00 06 5B`), es un
parámetro interno de la reverb en sí, y el valor "derivado y retardado" que se observaba
entonces era, con esta lectura, probablemente el nivel interno tras aplicar el propio efecto.
No se reintroduce como control nuevo: sigue sin haber prueba de que aceptar escritura ahí
cambie el sonido.

#### Escala de paso fraccionario (implementado, 2026-09-05)

✅ **`FractionalLevelScale`, nueva clase en `protocol/`, junto a `KatanaFractionalParameter` en
`device/`.** Desbloquea los dos parámetros que se habían quedado documentados y sin cablear
por la misma razón: un paso que no es "un byte crudo = una unidad mostrada" (0.5 ms para el
Pre Delay de 2x2 Chorus, 0.1 s para Reverb Time), que `LevelScale` no puede representar
porque solo suma un desplazamiento entero (`rawOffset`), nunca multiplica.

**Deliberadamente un tipo aparte, no una ampliación de `LevelScale` a `Double`.** Los ~230
tests que ya existían cuando se escribió esto asumen que todo control muestra un `Int`, y
son la inmensa mayoría de los parámetros del proyecto — forzar `Double` en todos ellos habría
sido cambiar el contrato de lo que funciona por dos casos minoritarios. La fórmula en sí no
necesitó un campo de desplazamiento aparte: `mostrado = displayRange.start + (crudo -
rawRange.first) × step` ya captura el "+1" de Reverb Time con solo que `displayRange`
empiece en `0.1`, no en `0.0`.

**Redondeo sin arrastre de error de punto flotante**: `toRaw` siempre parte del valor de
pantalla ya fijado y redondea una sola vez a un entero — nunca acumula sobre una conversión
anterior — así que una ida y vuelta repetida (crudo → mostrado → crudo → …) no puede
degradarse: cada conversión arranca limpia desde un entero, y el ruido de multiplicar por
`step` en coma flotante queda muchos órdenes de magnitud por debajo del `0.5` que haría falta
para cruzar un límite de redondeo. Verificado con un test que repite el viaje de ida y vuelta
50 veces sobre el mismo valor y comprueba que nunca se mueve del crudo original.

`KatanaFractionalParameter` es el mellizo de `KatanaParameter` con `displayValue`/`setLevel`
en `Double` en vez de `Int`; comparte toda la maquinaria de `KatanaControl` (caché, GET, SET
optimista, regla anti-eco) sin cambiar nada de lo que ya existía.

**Mod's Pre Delay de 2x2 Chorus (`60 00 02 3A` banda Low, `60 00 02 3E` banda High)** es el
**primer parámetro interno de Mod cableado en código**, y por eso trae una salvedad que
Booster/Delay/Reverb no tenían: Mod es "DSP complejo" (cada tipo activo tiene su propio
bloque de direcciones), así que estas dos direcciones **solo significan "Pre Delay" mientras
el tipo activo de Mod sea 2x2 Chorus** (`ModFxType.CHORUS`, `0x1D`); con cualquier otro tipo
activo son parámetros de ese otro tipo. La UI condiciona la visibilidad de estos dos sliders
al tipo activo en vez de mostrarlos siempre — a diferencia de Booster/Delay/Reverb, donde el
control siempre significa lo mismo sin importar el tipo. El repositorio registra el control
igual que todos los demás: la decisión de quién puede verlo/editarlo vive en la UI, no en
`device/`.

⚠️ **Ambos, implementados, sin confirmar con audio.**

**Dos catálogos de frecuencia nuevos, `protocol/`, con tests JVM**: `DelayHighCutFrequency`
(15 valores) y `ReverbHighCutFrequency` (15 valores) — **parecen la misma lista y no lo son**:
`0x0A` es `"6.30K"` en el bloque de Delay 1 y `"6.00k"` en el de Reverb, una fila donde la
propia fuente se contradice consigo misma. Se mantienen como dos enums separados a propósito
en vez de compartir uno: reutilizar la misma lista para los dos habría escondido la
discrepancia el día que cualquiera de las dos resultara ser la incorrecta — ver "el ruido de
las fuentes no predice el resultado" más abajo. `ReverbLowCutFrequency` (18 valores) no tiene
gemela en Delay. Las tres corren sin huecos.

#### Guardado de presets (investigado 2026-09-05, cableado 2026-09-06)

✅ **`7F 00 01 04` es correcto y vale para el Mk2**, no solo para el MK1. Y hay **una segunda
vía completamente distinta**, por Control Change, que ninguna nota previa del proyecto
mencionaba.

⚠️ **La vía SysEx está cableada desde el 2026-09-06 y sin probar contra el amplificador**
(`protocol/PresetSave.kt`, `KatanaRepository.savePreset`, botón "Guardar preset…" en Sliders).
La vía por Control Change **no**: sigue necesitando una segunda ruta de empaquetado que el
proyecto no tiene.

**Tres decisiones al cablearlo, todas por la misma razón —es destructivo y no se confirma—:**

1. **Solo los ocho canales; PANEL (`00`) no se ofrece.** Sigue siendo TBD qué hace el
   amplificador con él, y un destino de efecto desconocido no es algo que convenga poner en un
   desplegable al lado de los que sí se entienden. `PresetSave.commitMessage` **rechaza**
   cualquier valor fuera de `01`..`08` en vez de clampearlo: clampear elegiría un canal por su
   cuenta en una operación sin deshacer.
2. **No se toca el edit mode**, aunque la secuencia del MK1 lo ponga como paso 1. Es un ajuste
   explícito del usuario (§4.2) y encenderlo de tapadillo sería moverle un interruptor por la
   espalda; en su lugar la UI **exige** que esté encendido (el botón cae bajo `canEdit`, como
   cualquier otro parámetro y a diferencia del selector de canal).
3. **La verificación es releer el nombre del canal destino** en `10 0N 00 00` y compararlo. Es
   lo más parecido a una confirmación que existe. ⚠️ Y se informa con cuidado: que el nombre
   **no** cuadre no prueba que el guardado fallara —puede que el amp tarde en actualizar esa
   tabla, cosa que ninguna fuente aclara— así que el log dice "o el guardado no entró, o el amp
   no actualizó todavía esa tabla", no "falló".

⚠️ **El margen entre el nombre y el commit (50 ms) es criterio propio, no un dato.** Ninguna
fuente documenta ni ese intervalo ni el mínimo entre dos guardados; se eligió por analogía con
el único margen documentado, que es el de alrededor del **edit mode**. Por lo mismo la app no
deja dos guardados solapados.

##### Tabla resumen

| | Vía SysEx | Vía Control Change |
| --- | --- | --- |
| Dirección / mensaje | `7F 00 01 04` (SET, `12`) | CC#8 y CC#9 |
| Dato | **2 bytes**: `00 xx` | CC#8 valor `127`; CC#9 valor `1`..`8` |
| Qué es `xx` | canal destino, `00` PANEL … `08` CH B4 | CC#9: canal `1`..`8` (sin PANEL) |
| Guarda en el canal actual | no, hay que decirlo | **sí, eso hace CC#8** |
| ¿Confirma? | sin documentar; todo apunta a fire-and-forget | sin documentar |
| Ámbito | commit del búfer de edición al canal destino | ídem |
| Fuente | `katana_sysex.txt` (MK1) + `patchWriteDialog.cpp` (**Mk2**) | PDF MIDX-20 + `midi.yaml` (comentado) |
| Confianza | **dos fuentes, una de ellas Mk2 y en código** | dos fuentes de Mk2, ninguna en código |

##### 1. Qué dato se envía

**No se envían los datos del preset.** El amplificador ya tiene el estado editado en su búfer
(es lo que la app viene modificando parámetro a parámetro); `7F 00 01 04` es un **commit**: le
dice "coge lo que tienes ahora y escríbelo en el canal *xx*". Son **2 bytes de dato**, igual
que el canal activo de §5.1.

✅ **Confirmado para el Mk2 en código**, no por analogía con el MK1:
[patchWriteDialog.cpp:346](reference/FxFloorboard/patchWriteDialog.cpp) construye literalmente

```cpp
sysxMsg = "F0410000000033127F00010400"+addr+"00F7";
```

que se descompone como `F0 41 00 00 00 00 33` (prefijo Roland con model id `33`, el del
Katana) · `12` SET · **`7F 00 01 04`** dirección · `00 <addr>` dato de 2 bytes · `00` checksum
· `F7`.

⚠️ **Ese `00` final es un marcador, no el checksum real.**
[midiIO.cpp:504-512](reference/FxFloorboard/midiIO.cpp) recalcula el checksum de todo mensaje
saliente antes de mandarlo, sumando desde el byte 8 (`checksumOffset = 8` en
`globalVariables.h:49`, que es exactamente el primer byte de la dirección) con la fórmula
`128 - suma % 128` de siempre. O sea: **el mismo checksum que ya calcula `RolandSysEx`**, sin
nada especial. Los mensajes reales quedan así:

| Destino | Dato | Mensaje completo |
| --- | --- | --- |
| PANEL | `00 00` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 00 7C F7` |
| CH A1 | `00 01` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 01 7B F7` |
| CH A2 | `00 02` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 02 7A F7` |
| CH A3 | `00 03` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 03 79 F7` |
| CH A4 | `00 04` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 04 78 F7` |
| CH B1 | `00 05` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 05 77 F7` |
| CH B2 | `00 06` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 06 76 F7` |
| CH B3 | `00 07` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 07 75 F7` |
| CH B4 | `00 08` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 08 74 F7` |

##### 2. ¿Confirma el amplificador?

**Ninguna fuente documenta una respuesta**, y la evidencia disponible apunta a que **no la
hay**: es fire-and-forget, con la misma semántica DT1 del edit mode (§4.2).

Lo más informativo es lo que FxFloorboard **dejó comentado** justo alrededor del envío
(`patchWriteDialog.cpp:348-350`):

```cpp
//QObject::disconnect(sysxIO, SIGNAL(sysxReply(QString)));
//QObject::connect(sysxIO, SIGNAL(sysxReply(QString)), sysxIO, SLOT(resetDevice(QString)));
sysxIO->sendSysx(sysxMsg);   // Send the data.
```

Manda y cierra el diálogo acto seguido, sin esperar nada. Que el enganche a `sysxReply` esté
ahí escrito y anulado sugiere que alguien intentó tratar una respuesta y acabó quitándolo —
pero eso es una lectura de la intención, no un dato. **TBD, probar con amplificador.**

##### 3. ¿Hay que decir a qué canal, y con qué numeración?

**Con la vía SysEx, sí: el canal destino es el dato.** No existe un "guardar donde estoy" por
SysEx; para eso está CC#8.

✅ **La numeración es la misma que la del canal activo `00 01 00 00` (§5.1)**: `00` PANEL,
`01`..`08` los ocho canales en orden A1-A4, B1-B4. Se deduce de FxFloorboard cruzando dos
sitios del mismo fichero:

- La lista de destinos (`patchWriteDialog.cpp:174-190`) etiqueta la fila `z`: `z==0` →
  `"PANEL: "`, `z==1` → `"CH A1: "`, … `z==8` → `"CH B4: "`.
- El envío (`:327` y `:341`) hace `patch = currentRow()+1` y luego `addr = num-1`, o sea
  **`addr` = el índice de fila `z` tal cual**.

Y lo corrobora el ajuste para el Katana 50 (`:336-339`), que tiene 5 canales:

```cpp
if(model<1 && patch>3) { num = num+2; }  // kat50 channels: panel==0, A1==1, A2==2, B1==5, B2==6
```

Es decir: incluso en el amplificador de 5 canales, los canales B siguen valiendo `05` y `06`,
no `03` y `04`. **La numeración 0..8 es del protocolo, no de la lista de la interfaz** — que es
justo lo que hacía falta saber.

⚠️ **Corrección a lo que decía §5 hasta ahora.** La tabla de direcciones clave describía
`7F 00 01 04` como "(`00 xx`, xx = 01..04)". Ese `01..04` es el rango del **MK1** copiado tal
cual de [katana_sysex.txt:150-155](reference/katana-midi-bridge/doc/katana_sysex.txt), donde el
amplificador solo tenía cuatro canales. En el Mk2 llega hasta `08`. Los dos coinciden en el
tramo que comparten (Ch1 = `01`), así que no era una contradicción, solo un rango incompleto.

⚠️ **Lo que sigue sin estar claro es si `00` (PANEL) es un destino legal o un artefacto de la
interfaz de FxFloorboard.** Guardar "en el panel" no tiene un significado obvio —el panel es
precisamente el estado no guardado—, pero su lista incluye la fila y el código no la excluye.
**TBD, probar con amplificador**, y si hay que apostar, empezar por los canales `01`..`08`.

##### 4. ¿Hace falta un tiempo mínimo entre guardados?

⚠️ **Ninguna fuente documenta un intervalo mínimo entre guardados sucesivos.** No hay nada, ni
en el MK1, ni en FxFloorboard, ni en el PDF. Lo que sí hay es un dato de temporización **de
otra cosa**, y conviene no confundirlos: el retardo documentado es alrededor del **edit mode**,
no del guardado.

- [katana_sysex.txt:142-143](reference/katana-midi-bridge/doc/katana_sysex.txt): *"The amp
  needs a short settling period after the control mode command. 50-100 msec. seems
  reasonable."*
- [bankTreeList.cpp:337-340](reference/FxFloorboard/bankTreeList.cpp) hace exactamente eso en
  el Mk2: `SLEEP(50)` → SET de edit mode → `SLEEP(50)`.

Dos fuentes independientes, una de Mk2, coinciden en ~50 ms alrededor del edit mode. Para el
guardado en sí, **TBD**. Lo prudente al implementarlo es dejar un margen y no permitir
guardados en ráfaga, pero eso es criterio propio, no un dato de las fuentes.

##### 5. El "Saving in progress..." del panel

❌ **Ninguna fuente lo documenta, en ningún sentido**: ni una dirección que lo reporte, ni un
GET que lo consulte, ni un flag de "ocupado". Se buscó explícitamente por `saving`,
`in progress`, `busy` y `write in progress` en las cuatro fuentes y no aparece nada.

Lo único adyacente es que FxFloorboard muestra su propio texto `"Writing Patch"` en la barra de
estado (`patchWriteDialog.cpp:326`), que es un mensaje **de su interfaz**, generado localmente
al pulsar el botón — no algo que el amplificador reporte.

**Hipótesis por defecto: es solo visual, del panel del amplificador, y no consultable.** Es
coherente con que el guardado sea fire-and-forget. Pero es una hipótesis: **TBD**, y la forma
de comprobarlo es mirar si llega algún mensaje espontáneo por el endpoint de entrada mientras
el panel muestra ese texto (con edit mode activo, que es lo que hace que el amplificador
reporte, §4.2).

##### La vía alternativa: Control Change #8 y #9

**Es la respuesta directa a "¿hace falta especificar el canal?": con CC#8, no.**

| Mensaje | Efecto | Valor |
| --- | --- | --- |
| CC#8 | guardar en el **canal actual** | `127` |
| CC#9 | guardar en un canal concreto | `1`..`8` |

Dos fuentes de Mk2 coinciden byte a byte:

- El PDF de implementación MIDI,
  [MIDX_20_KatanaMKIIV1.pdf](reference/TuxKatana/doc/MIDX_20_KatanaMKIIV1.pdf), sección
  "Miscellaneous CC's": `STORE TO CURRENT PRESET = CC# 8 (value=127)` y
  `STORE TO PRESET = CC# 9 (1-8)`.
- [reference/TuxKatana/params/midi.yaml:67-71](reference/TuxKatana/params/midi.yaml), que los
  tiene **comentados** (sin implementar) pero con los mismos valores:
  `Store_Current_Preset: # CC #8 / val: 0x7f` y `Store_To_Preset: # CC #9 / start: 0x01,
  end: 0x08`.

Que estén comentados no dice nada del hardware: es exactamente el caso de `60 00 06 57`, que
`booster.yaml` listaba bajo `Unimplemented:` y funcionó igual (§5, "el ruido de las fuentes no
predice el resultado").

⚠️ **Dos cautelas serias con esta vía**, las dos de peso:

1. **El PDF es del MIDX-20, un puente MIDI de terceros, no la implementación MIDI oficial de
   Boss.** Su portada dice "MIDX Boss Katana™ MKII Bridge — MIDI Implementation" y avisa de que
   "Text in RED indicate features not available with BOSS Tone Studio". Buena parte de su tabla
   coincide con los CC nativos que ya documenta `midi.yaml` (CC#16-20, CC#7), lo que sugiere
   que describe lo que el amplificador acepta y no lo que el puente inventa — pero **no está
   probado**, y podría ser que CC#8/#9 los implemente el puente traduciéndolos a SysEx.
2. **El proyecto no puede mandar CC hoy.** `packUsbMidi` solo emite los CIN `0x4`–`0x7` y da por
   hecho una trama `F0…F7` (§5, final). Mandar un CC exige una segunda ruta de empaquetado
   (CIN `0xB`, 3 bytes). Además PC y CC viajan por un canal MIDI y el amplificador solo atiende
   el que tenga en `00 02 00 00`, un ajuste global del usuario; el SysEx no depende de eso.

**Por eso la vía SysEx es la que conviene implementar primero**: no necesita nada nuevo del
transporte, y está confirmada en código de una app de Mk2.

##### El nombre del preset, que es parte del guardado

⚠️ **El MK1 dice que hay que escribir el nombre ANTES de guardar**, y el Mk2 lo hace de otra
manera. [katana_sysex.txt:145-147](reference/katana-midi-bridge/doc/katana_sysex.txt) lo pone
como paso explícito de la secuencia:

```
Send name of amp first:
60 00 00 00 --> 4B 41 54 41 4E 41 20 20 20 20 20 20 20 20 20 20      ("KATANA" + 10 espacios)
```

y lo repite en `:1989` (*"Amp Name (Write here before preset store)"*).

FxFloorboard **no manda el nombre dentro de la rutina de guardado**: su `writeToMemory()` usa
`getCurrentPatchName()` solo para refrescar su propia lista. El nombre viaja antes, como un
parámetro normal más: `renameWidget.cpp:68` escribe los 16 bytes ASCII en el origen
`"Structure"` con `nameAddress = "00"` (`globalVariables.h:76`), o sea `60 00 00 00`, y se
sincroniza por el camino habitual de cambio de parámetro.

Las dos formas acaban en lo mismo — **los 16 bytes del nombre están en `60 00 00 00` antes de
mandar el commit**— y coincide con `presets_addrs.yaml:1-3`
(`UserPatch%PatchName: addr '60 00 00 00', size 16`) y con `HOW.md:10`. Para esta app significa
que **el guardado son dos pasos**: escribir el nombre y después el commit, no solo el commit.

Esto además toca un cabo suelto conocido: §5 anota que los primeros 16 bytes de `60 00 00 00`
"serían el nombre del preset actual — pero lo que devolvió el amplificador ahí no se parece a
un nombre" (ver BACKLOG). Tres fuentes dicen que ahí va el nombre, así que la discrepancia está
en la lectura, no en la dirección.

##### Secuencia completa propuesta (sin probar)

Juntando el MK1 (que da el orden) y FxFloorboard (que da los bytes del Mk2):

```
1. Edit mode ON        SET 7F 00 00 01 -> 01        (ya implementado, §4.2)
2. esperar ~50 ms                                    (katana_sysex.txt + bankTreeList.cpp)
3. Nombre del preset   SET 60 00 00 00 -> 16 bytes ASCII
4. Commit              SET 7F 00 01 04 -> 00 xx      (xx = canal destino, 01..08)
5. (opcional) Edit mode OFF  SET 7F 00 00 01 -> 00
```

⚠️ El paso 5 lo hace el MK1 al final de la secuencia. **En esta app no conviene copiarlo tal
cual**: el edit mode es un ajuste explícito y visible del usuario (§4.2), y apagarlo como
efecto secundario de guardar dejaría la UI sin poder confirmar nada de lo que escriba después.

##### Qué no dice ninguna fuente

Resumido, para no volver a buscarlo:

- Si el guardado responde algo. **TBD.**
- Si `00` (PANEL) es un destino legal. **TBD.**
- Cuánto hay que esperar entre dos guardados. **TBD.**
- Qué dirección reporta "Saving in progress...". **Ninguna fuente lo documenta**; la hipótesis
  es que es solo del panel.
- Si CC#8/#9 los atiende el amplificador o el puente MIDX-20. **TBD.**
- Qué pasa si se guarda con el edit mode apagado. Ninguna fuente lo dice; el MK1 lo pone dentro
  de la secuencia de BTS mode, así que lo prudente es asumir que hace falta.

#### Formato `.tsl` (investigado 2026-09-05, importación y exportación cableadas 2026-09-06)

⚠️ **Importación, edición offline y exportación implementadas el 2026-09-06 y pendientes de
probar** (`protocol/tsl/`, `library/PresetLibrary`, sección "Biblioteca").

- **Importar y ver** un `.tsl` — §5 "Formato `.tsl`", abajo.
- **Editar sin amplificador** y **crear uno desde cero** — la arquitectura está en §4.5, y lo
  escribe `TslWriter` con la política de bloques poco fiables de más abajo.
- **Exportar** el estado del amplificador conectado a un `.tsl` nuevo
  (`KatanaRepository.exportImage`, botón en Sliders). ⚠️ **Esta sí necesita hardware para
  probarse**; las otras dos no.

##### La política de lo que no es de fiar, al escribir

Al serializar hay tres casos y **cada uno tiene una decisión explícita**, no un valor por
defecto silencioso:

| Caso | Qué se hace |
| --- | --- |
| La imagen tiene los bytes del bloque | **se escriben** |
| No los tiene, pero el fichero de origen sí traía la clave | **se copia verbatim** |
| Ni una cosa ni la otra | **se omite la clave** y se avisa |

⚠️ **La copia verbatim es lo que hace que editar no pierda nada.** Los cuatro bloques en
disputa (los tres `Contour` y `GafcExp1AsgnMinMax`, 82 bytes) no se cargan a propósito al
importar; sin este paso, abrir un preset y volver a guardarlo los **borraría**. Se copian sin
mirarlos: no hace falta saber a qué dirección van para saber que pertenecen a ese preset. Lo
mismo con cualquier clave que el mapa no conozca.

⚠️ **Omitir es deliberado, y la alternativa —escribir ceros— es peor.** Un bloque de Contour a
ceros es un Shape 1 con Freq Shift −50: un valor legal, indistinguible de uno elegido a mano,
que al cargarlo en el amplificador **cambiaría el sonido en silencio**. Una clave que falta,
como mucho, hace que el lector se queje. Entre un fallo ruidoso y uno callado, el ruidoso — es
la misma regla que ya rige la importación, aplicada en la otra dirección. ⚠️ **Sin probar
contra Boss Tone Studio**: ninguna fuente dice si BTS acepta un `.tsl` con claves ausentes. Lo
que sí es seguro es que esta app lo relee sin problema.

##### El preset en blanco

`TslWriter.blank(name)` construye un punto de partida **sin fichero y sin amplificador**.
⚠️ **No es un preset de fábrica de Boss y no lo pretende**: copiar uno de `reference/` metería
material GPL en la app (§7), y ninguna fuente dice cuál sería "el preset vacío" correcto.

Todo a cero, **con dos excepciones documentadas** donde el cero no es neutro sino
estructuralmente inválido:

1. **La cadena de efectos** (`60 00 06 00`–`06 13`) es un **array de permutación**: veinte
   ceros significarían "el compresor veinte veces". Se siembra con la identidad `00`..`13`.
2. **El nombre**, que se rellena con el elegido.

✅ Todo lo demás a cero es legal: los catálogos con huecos del proyecto (`AmpType` sin el `19`,
`BoostType` sin el `07`, `ModFxType`) **sí incluyen el `0x00`** — comprobado con un test que
monta el repositorio real sobre el preset en blanco, porque `KatanaEnumParameter` rechaza en
silencio (§4.3) y un selector inválido no daría error, solo dejaría el control vacío.



**La reutilización que hizo esto barato**: un `.tsl` y un dump de memoria terminan en la misma
forma —trozos de bytes, cada uno con su dirección base—, así que el parser produce un
[`MemoryDump`] y `AmpState.from(dump)` lee **sin cambiar una línea**. Ni `AmpState` ni
`MemoryDump` se tocaron; lo único nuevo es el mapa de claves→dirección y el JSON.

**Tres decisiones al cablearlo:**

1. **Las claves en disputa NO se cargan**, se listan como no disponibles: los tres `Contour`
   (`midi.xml` dice `0F 30`/`38`/`40`, la aritmética de FxFloorboard `0F 2E`/`36`/`3E`) y
   `GafcExp1AsgnMinMax` (`09 30` vs `09 34`). Un desfase de dos daría el Freq Shift donde va el
   Shape y lo enseñaría como si fuera bueno — peor que no enseñar nada. Son 82 de los 1141
   bytes; el resto sí se carga.
2. **`Patch_1` se lee con 91 bytes**, no con los 50 del yaml, que es lo que hace que la cadena
   de efectos, Solo, Contour general y la posición de EQ2 **sí** lleguen. Hay un test que
   comprueba que las 20 posiciones de la cadena vienen enteras.
3. **El tamaño del fichero manda sobre el del mapa.** Si un bloque trae otra longitud, se cargan
   los bytes del fichero y se avisa, en vez de truncar o rellenar — otra revisión del formato
   podría traer bloques distintos y ajustarlos a ciegas sería inventar.

⚠️ **`kotlinx-serialization-json` añadido** (§6 lo permitía "solo si el parseo de presets `.tsl`
lo justifica"). El motivo concreto es que el `org.json` de la plataforma **está apagado en los
tests JVM** —devuelve valores por defecto o lanza— y el parseo del `.tsl` es justo lo que hay
que poder probar sin amplificador.

⚠️ **Corrección de partida: un `.tsl` es un fichero JSON de texto, no un volcado binario.** No
hay "offset en bytes desde el inicio del fichero" que valga: los datos no están en posiciones
fijas del fichero, están en **claves de un objeto JSON**, y la posición de cada clave dentro del
texto cambia con el nombre del preset, los espacios y el orden. La unidad de direccionamiento
del `.tsl` es **el nombre de la clave**, no un offset.

Eso no rompe el plan: lo que hace de "mapa de offsets" es
[presets_addrs.yaml](reference/TuxKatana/params/presets_addrs.yaml), que **mapea cada clave del
JSON a una dirección SysEx y un tamaño**. Es exactamente el puente que hacía falta.

Comprobado sobre un fichero real: `reference/FxFloorboard/default_mk2.tsl` (6381 B, JSON).

##### La envoltura

```json
{ "name": "KATANA Mk2", "formatRev": "0002", "device": "KATANA MkII",
  "data": [ [ { "memo":    { "memo": "", "isToneCentralPatch": true },
                "paramSet": { "UserPatch%PatchName": ["4B","41","54",…],
                              "UserPatch%Patch_0":   ["00","0A","32",…], … } } ] ] }
```

| Campo | Tipo | Qué es |
| --- | --- | --- |
| `name` | string | Nombre del fichero/preset, legible. Redundante con `UserPatch%PatchName`. |
| `formatRev` | string | Revisión del formato. `"0002"` en Mk2. |
| `device` | string | **`"KATANA MkII"`** — es el discriminante del modelo. |
| `data` | array | `data[0]` es la **lista de presets**; un `.tsl` puede llevar varios. |
| `memo` | objeto | Nota libre del usuario + `isToneCentralPatch` (booleano). |
| `paramSet` | objeto | **Los datos del preset**: 22 claves → array de bytes. |

✅ **`device` y `formatRev` son discriminantes reales, no decorativos.**
[tsl.py:35](reference/TuxKatana/lib/tsl.py) rechaza el fichero si
`data['device'] != "KATANA MkII"`, y [tsl.py:26](reference/TuxKatana/lib/tsl.py) anota que la
revisión `"0002"` es lo que *"seem to tell the `UserPatch%Patch_Mk2V2` presence"* — o sea, el
bloque extra de firmware 2 solo está a partir de esa revisión.

⚠️ **No todo `.tsl` es de Katana.** El otro fichero del repo,
`reference/FxFloorboard/default.tsl`, tiene `device: "GT"` y un esquema **completamente
distinto** (`liveSetData` + `patchList`, con `params` en vez de `paramSet`). Es de la serie GT.
Comprobar `device` antes de parsear no es paranoia: son formatos que solo comparten la
extensión.

##### Codificación de los datos

Cada valor de `paramSet` es un **array JSON de strings de dos caracteres hexadecimales en
mayúsculas**, un string por byte:

```json
"UserPatch%PatchName": ["4B","41","54","41","4E","41","20","4D","6B","32","20","20","20","20","20","20"]
```

que es `KATANA Mk2` seguido de espacios (`20`). **Son los bytes SysEx crudos, sin transformar**:
el mismo `4B 41 …` que viajaría por el cable. No hay checksum, ni compresión, ni escapado, ni
reordenación — el checksum es de la trama SysEx, y en el fichero no hay tramas.

##### El mapa: clave → dirección SysEx

Esta es la tabla que convierte el `.tsl` en direcciones. **Los tamaños están verificados contra
el fichero real**, no solo leídos del yaml.

| Clave `paramSet` | Dirección SysEx | Bytes | Tipo | Qué contiene |
| --- | --- | --- | --- | --- |
| `UserPatch%PatchName` | `60 00 00 00`–`00 0F` | 16 | ASCII | Nombre del preset, rellenado con `20` |
| `UserPatch%Patch_0` | `60 00 00 10`–`00 57` | 72 | binario | Booster completo + PREAMP completo + EQ1 |
| `UserPatch%Eq(2)` | `60 00 00 60`–`00 77` | 24 | binario | EQ2 (paramétrico + gráfico) |
| `UserPatch%Fx(1)` | `60 00 01 00`–`02 5C` | 221 | binario | **MOD**: on/off, tipo y los bloques internos de los tipos |
| `UserPatch%Fx(2)` | `60 00 03 00`–`04 5C` | 221 | binario | **FX**: ídem, la imagen +`0x0200` de la anterior |
| `UserPatch%Delay(1)` | `60 00 05 00`–`05 19` | 26 | binario | Delay 1 completo |
| `UserPatch%Delay(2)` | `60 00 05 20`–`05 39` | 26 | binario | Delay 2 completo |
| `UserPatch%Patch_1` | `60 00 05 40`–`06 1A` | **91** | binario | Reverb + Noise Gate + Master + **cadena** + Solo + Contour + posición EQ2 |
| `UserPatch%Patch_2` | `60 00 06 20`–`06 43` | 36 | binario | Tipo de cadena, posiciones, **tipos por color** de los 5 efectos, colores activos |
| `UserPatch%Status` | `60 00 06 50`–`06 61` | 18 | binario | **Las 12 perillas del panel** + los 6 `led state` |
| `UserPatch%KnobAsgn` | `60 00 07 00`–`07 20` | 33 | binario | Asignación de perillas |
| `UserPatch%ExpPedalAsgn` | `60 00 08 00`–`08 20` | 33 | binario | Asignación del pedal de expresión |
| `UserPatch%ExpPedalAsgnMinMax` | `60 00 08 30`–`08 7B` | 76 | binario | Mín/máx de esa asignación |
| `UserPatch%GafcExp1Asgn` | `60 00 09 00`–`09 20` | 33 | binario | GA-FC pedal 1 |
| `UserPatch%GafcExp1AsgnMinMax` | `60 00 09 30`–`09 7B` ⚠️ | 76 | binario | Mín/máx GA-FC 1 |
| `UserPatch%GafcExp2Asgn` | `60 00 0A 00`–`0A 20` | 33 | binario | GA-FC pedal 2 |
| `UserPatch%GafcExp2AsgnMinMax` | `60 00 0A 30`–`0A 7B` | 76 | binario | Mín/máx GA-FC 2 |
| `UserPatch%FsAsgn` | `60 00 0F 08`–`0F 09` | 2 | binario | Asignación de footswitch |
| `UserPatch%Patch_Mk2V2` | `60 00 0F 10`–`0F 25` | 22 | binario | Añadidos del firmware 2 (solo con `formatRev` ≥ `"0002"`) |
| `UserPatch%Contour(1)` | `60 00 0F 30`–`0F 31` ⚠️ | 2 | binario | Contour 1: Shape + Freq Shift |
| `UserPatch%Contour(2)` | `60 00 0F 38`–`0F 39` ⚠️ | 2 | binario | Contour 2 |
| `UserPatch%Contour(3)` | `60 00 0F 40`–`0F 41` ⚠️ | 2 | binario | Contour 3 |

**Total: 1141 bytes en 22 bloques.**

##### ⚠️ Tres correcciones a `presets_addrs.yaml`

El yaml es la guía correcta, pero tiene fallos concretos que hay que arreglar antes de usarlo:

1. ❌ **`UserPatch%Patch_1` dice `size: 50` y son 91.** El fichero real trae 91 bytes en esa
   clave, y [sysxWriter.cpp:363-366](reference/FxFloorboard/sysxWriter.cpp) lo construye como
   `64 + 27 = 91` bytes. **Con 50 se perderían el bloque de cadena entero, Solo, Contour y la
   posición de EQ2** — o sea, casi todo lo que se documentó en "Controles sin perilla física".
   91 bytes desde `60 00 05 40` llegan exactamente a `60 00 06 1A`, que es el último control de
   ese tramo (Contour Freq Shift). Cuadra a la perfección.
2. ✅ **Nueve claves tienen `addr` vacío en el yaml y ahora tienen dirección.** TuxKatana sabía
   sus tamaños pero no dónde vivían. Se resolvieron con la aritmética de
   [sysxWriter.cpp:377-390](reference/FxFloorboard/sysxWriter.cpp) sobre el volcado de patch
   (`default.syx`), que es una tira de mensajes SysEx de 128 bytes de datos cada uno (12 de
   cabecera + 128 + checksum + `F7` = 142): `KnobAsgn` → `60 00 07 00`, `ExpPedalAsgn` →
   `08 00`, `GafcExp1Asgn` → `09 00`, `GafcExp2Asgn` → `0A 00`, `FsAsgn` → `0F 08`,
   `Patch_Mk2V2` → `0F 10`, y los tres `Contour` en `0F 30`/`0F 38`/`0F 40`.
3. ⚠️ **`GafcExp1AsgnMinMax`: el yaml dice `09 30` y la aritmética de FxFloorboard da `09 34`.**
   Sus dos hermanas caen limpiamente en `08 30` y `0A 30`, así que el `09 30` del yaml es el que
   encaja con el patrón y el `09 34` parece un desliz del escritor. El propio yaml marca esa
   línea, y solo esa, con el comentario `# ⚠️ Sequence not valuable +1` — su autor ya había
   visto que ahí había algo raro. **Se documenta `09 30` y queda como TBD.**

⚠️ **Y una discrepancia que NO se resuelve sola: los tres `Contour`.** La aritmética de
FxFloorboard da `0F 2E`, `0F 36`, `0F 3E`; `midi.xml` dice `0F 30`, `0F 38`, `0F 40` (§5,
"Controles sin perilla física"). Difieren en 2, con el mismo paso de 8. **Se documenta la de
`midi.xml`** por dos razones: es una afirmación directa (`<DATA value="30" … desc="Contour 1:"
customdesc="Contour Shape">`, `midi.xml:50135`) frente a una aritmética de offsets, y esa misma
aritmética ya falla en `GafcExp1AsgnMinMax` — un escritor con un desfase demostrado no es buen
árbitro para otro desfase. **TBD, y es de las primeras cosas a comprobar al leer un `.tsl` real
exportado desde Boss Tone Studio.**

##### ¿Volcado directo o envuelto? Ni una cosa ni la otra

La pregunta admite una respuesta precisa, y son tres afirmaciones distintas:

1. **Los bytes de dentro sí son 1:1 con SysEx.** Cada array es el contenido crudo de un rango de
   direcciones, sin transformar. Escribirlos al amplificador es "por cada clave, un SET a su
   dirección con esos bytes" — nada más.
2. **Pero el fichero NO es un volcado de memoria.** No hay una imagen contigua ni un offset
   global: hay 22 bloques con nombre, y **entre ellos hay huecos reales de direcciones** que el
   fichero simplemente no guarda (8 B entre `Patch_0` y `Eq(2)`, 35 B entre `Fx(2)` y
   `Delay(1)`, 12 B antes de `Status`, 206 B antes de `ExpPedalAsgnMinMax`…). Un parser que
   asuma continuidad se desalinea en el segundo bloque.
3. **Y guarda poco más de la mitad del preset**: 1141 bytes de los 1992 que pide el editor en un
   volcado de patch (`patchRequestDataSize = "00000F48"`,
   [globalVariables.h:65](reference/FxFloorboard/globalVariables.h)), o sea el **57 %**. Lo que
   falta son rangos sin parámetros o no guardables.

O sea: **envoltura JSON ligera + contenido crudo por bloques**. No hay checksums, ni longitudes,
ni compresión, ni cabeceras binarias que descifrar.

##### Qué parte del `.tsl` es cada cosa

- **Metadatos**: `name`, `formatRev`, `device` (envoltura) y `memo` (por preset). ⚠️ **No hay
  fecha ni versión de firmware** en ninguna parte del formato — si la app quiere fechar un
  export, tendrá que ponerlo en `memo`, que es el único campo libre.
- **Los 24+ controles ya implementados**: repartidos entre `Status` (las 12 perillas del panel,
  `60 00 06 50`–`06 61`), `Patch_0` (modelo de amplificador `00 21`, y el Booster entero
  `00 10`–`00 18`) y `Patch_2` (los cinco selectores de color). ✅ **Verificado byte a byte**
  sobre `default_mk2.tsl`: `60 00 06 51` (Gain) cae en `Status[1]` y vale `0x32` = 50;
  `60 00 00 21` cae en `Patch_0[17]` y vale `0x08` = Clean.
- **Efectos, tipos activos y parámetros internos**: `Fx(1)` es Mod completo, `Fx(2)` es FX,
  `Delay(1)`/`Delay(2)` los dos delays, y la reverb va dentro de `Patch_1`. Los **tipos por
  color** (`06 24`–`06 38`) están en `Patch_2`.
  ✅ **Un chequeo de consistencia que sale bien**: en el fichero, el color de Booster
  (`06 39` → `Patch_2[25]`) vale `00` = verde, el tipo del slot verde (`06 24` → `Patch_2[4]`)
  vale `0A` y el tipo activo (`00 11` → `Patch_0[1]`) vale también `0A` = Blues Drive. Es
  justo lo que §5.2 predice: **el tipo activo refleja el slot del color encendido.** Un fichero
  real confirma el modelo.
- **Cadena de efectos y posiciones de EQ**: sí están, y no en un sitio obvio — la cadena de 20
  ranuras (`60 00 06 00`–`06 13`) cae **dentro de `UserPatch%Patch_1`, en los índices 64–83**,
  que es precisamente la parte que se perdería con el `size: 50` del yaml. Las posiciones:
  EQ2 en `Patch_1[89]` (`06 19`) y EQ1 en `Patch_2[2]` (`06 22`).
  ✅ **La cadena del fichero por defecto es una permutación completa de los 20 identificadores**,
  y en un orden que tiene sentido musical:
  `PDL → OD → FX1 → FX2 → EQ1 → EQ2 → CH_A → CS → NS_1 → FV → LP → DD1 → CN_S → DD2 → RV → CAB
  → CH_B → NS_2 → USB → CN_M`
  (booster antes del previo, delays y reverb después). Confirma con un dato real el modelo de
  "array de permutación" que §5 dedujo de `midi.xml`.

##### Lo que el `.tsl` NO guarda

⚠️ **Pedal Bend de Mod y de FX se quedan fuera.** `Fx(1)` acaba en `60 00 02 5C` (Heavy Octave
Direct Mix) y el bloque de Mod sigue hasta `02 60` con los cuatro parámetros de Pedal Bend
(§5.2); igual en `Fx(2)` con `04 5D`–`04 60`. Son 8 direcciones documentadas que un
export/import por `.tsl` **perdería**. Puede que vivan en `Patch_Mk2V2` (22 bytes sin desglosar,
justo el bloque que el firmware 2 añadió), pero **eso es una conjetura sin comprobar** — el
contenido de `Patch_Mk2V2` no está documentado en ninguna fuente.

Tampoco está el canal activo (`00 01 00 00`), y es correcto que no esté: un preset describe un
sonido, no en qué ranura se carga. El destino se elige al guardar (§5, "Guardado de presets").

##### Cómo queda el trabajo de implementación

Con el mapa de arriba, leer y escribir `.tsl` es mecánico:

```
Importar:  JSON.parse → comprobar device == "KATANA MkII"
           → por cada clave de paramSet: dirección = MAPA[clave]
           → bytes = array.map { it.toInt(16) }
           → SET a esa dirección  (+ commit con 7F 00 01 04 si se quiere fijar en un canal)
Exportar:  por cada entrada del MAPA: GET dirección/tamaño (o leerlo del dump ya cacheado)
           → array de "%02X" → JSON.stringify con la envoltura
```

**El proyecto ya tiene todas las piezas**: `Address` hace la aritmética base 128, `RolandSysEx`
construye los SET, `MemoryDump` ya sostiene la mayor parte de estos rangos, y `KatanaRepository`
sabe escribir. Lo único nuevo es el mapa de 22 entradas y el JSON — y para el JSON, §6 dice que
`org.json` de la plataforma puede bastar y que `kotlinx-serialization-json` solo se justifica si
el parseo lo pide.

⚠️ **Una nota de tamaño**: dos bloques superan los 128 bytes (`Fx(1)` y `Fx(2)` con 221;
`ExpPedalAsgnMinMax` y sus dos hermanas se quedan en 76). Un SET de 221 bytes cruza el límite de
página de direcciones, así que trocearlo obliga a sumar en base 128 y no al último byte.

✅ **Troceado implementado el 2026-09-08** en `RolandSysEx.setChunked` + `protocol/tsl/TslTransfer`
+ `KatanaRepository.sendPreset`, con **128 bytes de datos por mensaje**. Ese 128 **no sale de
ninguna fuente que diga cuál es el máximo** —eso sigue siendo TBD— sino del único tamaño de SET
masivo que se observa en una fuente de Mk2: el volcado de patch de `sysxWriter.cpp:377-390` es una
tira de mensajes de 128 bytes de datos (12 + 128 + checksum + `F7` = 142).

❌ **Corrección: la frase que decía que 221 bytes "no caben en un solo paquete USB de 512 B" era
falsa, y la aritmética que la acompañaba también.** Un SET de 221 bytes de datos son `14 + 221 =
235` bytes de mensaje; empaquetados en tramas USB-MIDI de 4 bytes dan `ceil(235/3) × 4 = **316**`
bytes en el cable, no "300+ que no caben": **caben de sobra** en los 512 de `wMaxPacketSize`
(§4.1). Así que trocear **no lo obliga el transporte**; lo aconseja no ser el primero en probar si
el amplificador digiere un SET de 221 bytes de una sentada. Sigue sin haber fuente que lo diga:
**TBD, y es justo lo que la prueba con amplificador tiene que despejar** — si 221 de una vez
también funciona, el troceado sobra (pero no estorba).

#### Controles sin perilla física (investigado 2026-09-05, cableado 2026-09-06)

Los cinco grupos que la §"Visión de alcance" listaba como punto 3 —Noise Gate, Solo, Contour,
posición IN/OUT de EQ1 y EQ2, y cadena de efectos— existen todos y **todos tienen dirección
documentada**.

⚠️ **Cableados el 2026-09-06 salvo el Solo, pendientes de confirmar con audio** (BACKLOG.md,
"Pendiente por probar"). El **Solo se queda fuera a propósito**: sigue en instrumentación de
diagnóstico aparte, con sus dos candidatas sin desempatar, y cablearlo antes de saber cuál
responde sería elegir una al azar.

✅ **Lo que sí se verificó al cablearlo**, por programa contra `midi.xml` y no por relectura:
EQ2 es EQ1 `+ 0x20` byte a byte (24 nodos, 0 diferencias de dirección, nombre ni rango); las 20
posiciones de la cadena ofrecen el mismo catálogo de 20 identificadores y van seguidas de `00`
a `13`; y los seis catálogos de frecuencia/Q del EQ coinciden byte a byte con los del
Parametric EQ de Mod/FX.

**Todo sale de `midi.xml`**, que es la única fuente de Mk2 que los cubre. `Adresses.txt` no
menciona ninguno (su sección `EQ:` es en realidad el Bass/Middle/Treble del panel, no un
ecualizador); los `*.yaml` de TuxKatana solo aportan un dato indirecto de Contour y otro de
EQ2; y `katana-midi-bridge` es MK1, así que corrobora **estructura pero no direcciones**.

##### El truco que resolvió esto: la tabla de destinos de asignación

Ninguno de los cinco aparece en las secciones de parámetros que ya se habían recorrido. Todos
salen de un sitio distinto: la **tabla de destinos de asignación** (knob/pedal assign) en
`midi.xml:3700-3995`, donde cada destino codifica su dirección en dos atributos:

```
midi.xml:3957  <PARAM value="1E" name="Contour: Off/On"  desc="06" customdesc="16"/>
                                                          ↑ 3.er byte  ↑ 4.º byte
```

> `desc` = tercer byte de la dirección, `customdesc` = cuarto. Es decir `60 00 <desc> <customdesc>`.

✅ **La regla está verificada contra direcciones que este proyecto ya confirmó por audio**, no
supuesta: `"Panel Knob: Gain"` da `desc="06" customdesc="51"` → `60 00 06 51`;
`"Booster GRY color select"` da `06`/`39` → `60 00 06 39`; `"Panel Knob: Reverb/Delay2"` da
`06`/`5B` → `60 00 06 5B`. Las tres coinciden con lo medido (§5, bloque de perillas). Además,
cada dirección obtenida así **vuelve a aparecer como `<DATA>` en el bloque `<Structure>`**, con
su rango y sus valores — dos sitios independientes del mismo fichero que concuerdan.

##### Tabla resumen

Confianza: **`midi.xml` ×2** = la dirección aparece en la tabla de asignación *y* como `<DATA>`
en `<Structure>`; **`midi.xml` ×1** = solo como `<DATA>`; **+MK1** = `katana-midi-bridge` o
`katana_sysex.txt` corroboran la *estructura* con otra dirección; **+tsl** = los offsets de
`presets_addrs.yaml` corroboran el tamaño.

| Control | Dirección | Tipo | Rango / opciones | Ámbito | Confianza |
| --- | --- | --- | --- | --- | --- |
| **Noise Gate** On/Off | `60 00 05 66` | on/off | `00` Off · `01` On | preset | `midi.xml` ×2 +MK1 |
| Noise Gate Threshold | `60 00 05 67` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×2 +MK1 |
| Noise Gate Release | `60 00 05 68` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×2 +MK1 |
| **Solo** On/Off (panel) | `60 00 06 14` | on/off | `00` Off · `01` On | preset | `midi.xml` ×1 ⚠️ |
| Solo Level (panel) | `60 00 06 15` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×1 ⚠️ |
| **Solo** Sw (preamp) | `60 00 00 2B` | on/off | `00` Off · `01` On | preset | `midi.xml` ×1 ⚠️ |
| Solo Level (preamp) | `60 00 00 2C` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×1 ⚠️ |
| **Contour** Off/On | `60 00 06 16` | on/off | `00` Off · `01` On | preset | `midi.xml` ×2 |
| Contour Select | `60 00 06 17` | selector | `00` 1 · `01` 2 · `02` 3 | preset | `midi.xml` ×2 |
| Contour Freq Shift (activo) | `60 00 06 1A` | nivel centrado | crudo `00`..`64` = `-50`..`+50` | preset | `midi.xml` ×1 |
| Contour 1 Shape | `60 00 0F 30` ⚠️ | selector | `00` 1 · `01` 2 · `02` 3 · `03` 4 | preset | `midi.xml` ×2 +tsl |
| Contour 1 Freq Shift | `60 00 0F 31` ⚠️ | nivel centrado | crudo `00`..`64` = `-50`..`+50` | preset | `midi.xml` ×2 +tsl |
| Contour 2 Shape / Freq | `60 00 0F 38` / `0F 39` ⚠️ | ídem | ídem | preset | `midi.xml` ×2 +tsl |
| Contour 3 Shape / Freq | `60 00 0F 40` / `0F 41` ⚠️ | ídem | ídem | preset | `midi.xml` ×2 +tsl |
| **EQ1 posición** | `60 00 06 22` | selector 2 | `00` Amp In · `01` Amp Out | preset | `midi.xml` ×2 |
| **EQ2 posición** | `60 00 06 19` | selector 2 | `00` PreAmp In · `01` Pre Amp Out | preset | `midi.xml` ×2 |
| EQ1 bloque interno | `60 00 00 40`–`00 57` | 24 bytes | ver abajo | preset | `midi.xml` ×1 |
| EQ2 bloque interno | `60 00 00 60`–`00 77` | 24 bytes | ver abajo | preset | `midi.xml` ×1 +tsl |
| **Chain** (orden) | `60 00 06 00`–`06 13` | 20 selectores | 20 IDs de bloque, ver abajo | preset | `midi.xml` ×1 |
| Chain tipo 1~7 | `60 00 06 20` | selector 7 | crudo `00`..`06` | preset | `midi.xml` ×2 |
| Loop posición | `60 00 06 21` | selector 2 | `00` Post Amp · `01` Post Reverb | preset | `midi.xml` ×2 |
| Pedal/FX posición | `60 00 06 23` | selector 2 | `00` Input · `01` Post Amp | preset | `midi.xml` ×2 |

**Hallazgos sueltos del mismo barrido**, no pedidos pero del mismo bloque: `60 00 05 70`
Master Patch Level (`0`..`100` %), `60 00 05 71` Master Key (enum 12, `00` C(Am) … `0B` B(G#m)),
y `60 00 06 43` Cabinet Resonance.

##### 1. Noise Gate — `60 00 05 66`–`68`

Tres parámetros, el patrón "DSP simple" de siempre: un on/off y dos niveles `0..100`.
`midi.xml:43022-43031` los da como `<DATA>` con `desc="NS:"` (Noise **Suppressor**, que es como
lo llama Boss; "Noise Gate" es el nombre de la spec MK1). La tabla de asignación los repite en
`midi.xml:3928-3930` como `NS: On/Off` / `Threshold` / `Release` con `desc="05"`.

✅ **La estructura la corrobora el MK1 por partida doble**, con otra dirección:
`katana-midi-bridge/parameters/amplifier.json:106-127` define `noiseGate` con
`baseAddr [96,0,6,99]` (= `60 00 06 63`), `length: 3` y exactamente los mismos tres campos
(`gateActive` booleano, `threshold` y `release` `0..100`); y
`katana-midi-bridge/doc/katana_sysex.txt:328-337` da los mismos tres en `60 00 06 63`–`65`.
Es el caso de libro de §5.2: **la estructura transfiere del MK1, la dirección no**
(`06 63` → `05 66`).

##### 2. Solo — dos candidatas, y hay que probar cuál

⚠️ **Este es el único de los cinco con ambigüedad real**, y es exactamente el patrón que ya
costó tres intentos con el reverb: dos direcciones plausibles, ninguna fuente que desempate.

| Candidata | midi.xml | Etiqueta en la fuente | Bloque |
| --- | --- | --- | --- |
| `60 00 06 14` / `06 15` | 43514 / 43518 | `name="Solo"` `desc="Solo"` — On/Off y Level | `panel` (LSB `06`) |
| `60 00 00 2B` / `00 2C` | 37492 / 37496 | `desc="PREAMP:"` — Solo Sw y Solo Level | `PRE` (LSB `00`) |

Las dos tienen la misma forma (on/off + nivel `0..100`) y las dos son **distintas del Solo del
Booster** (`60 00 00 15`/`16`), que ya está implementado y confirmado por audio (§5.2).

**Ninguna de las dos aparece en la tabla de destinos de asignación** — se comprobó
explícitamente: en `midi.xml:3700-3995` no hay ni una entrada con "Solo" ni con "PREAMP". Así
que aquí falta el segundo testigo que sí tienen Contour y las posiciones de EQ, y **la regla
del proyecto se aplica entera: solo el audio decide**.

Si hubiera que apostar, `60 00 06 14`/`15` está en el bloque `panel`, que es donde viven las
once perillas confirmadas y los cinco selectores de color confirmados — pero eso es una
analogía, no un dato, y §5 tiene el precedente de `60 00 06 5C` (variación), que está en ese
mismo bloque y resultó de solo lectura. **TBD, probar con amplificador.**

##### 3. Contour — dos niveles, y el segundo cae fuera del dump

El Contour del Katana Mk2 son **tres slots** (como los colores de los efectos), con un
seleccionador de cuál está activo:

- `60 00 06 16` — Contour Off/On (`midi.xml:43521`, y la tabla de asignación en `:3957`).
- `60 00 06 17` — cuál de los tres está activo, `00`/`01`/`02` (`:43525`, asignación `:3958`).
- `60 00 06 1A` — "Contour: Freq Shift" del activo, escala centrada `-50..+50` (`:43544`).
- Por slot, en **otro bloque**: Shape (enum de 4) y Freq Shift (centrada `-50..+50`), con
  paso de 8 bytes entre slots:

  | Slot | Shape | Freq Shift | midi.xml | asignación |
  | --- | --- | --- | --- | --- |
  | Contour 1 | `60 00 0F 30` | `60 00 0F 31` | 50135 / 50141 | 3959 / 3960 |
  | Contour 2 | `60 00 0F 38` | `60 00 0F 39` | 50150 / 50156 | 3961 / 3962 |
  | Contour 3 | `60 00 0F 40` | `60 00 0F 41` | 50165 / 50171 | 3963 / 3964 |

✅ **El tamaño lo corrobora TuxKatana**: `presets_addrs.yaml:58-66` define
`UserPatch%Contour(1)`, `(2)` y `(3)` con `size: 2` cada uno — dos bytes por slot, que son
justo Shape + Freq Shift. Pero su campo `addr` está **vacío**: TuxKatana sabe que existen y
cuánto ocupan, y no sabe dónde están. Es la única cosa que aporta cualquier fuente que no sea
`midi.xml`, y aun así confirma la forma.

⚠️ **`60 00 0F 3x`/`4x` está FUERA del dump y esto sí cambia el diseño.** El dump pide
`60 00 00 00` con tamaño `00 00 0F 00` (1920 bytes), o sea hasta `60 00 0E 7F`; y el
amplificador real devolvió 1860, hasta `60 00 0E 43` (§4.4). Los Contour por slot están en el
offset 1968-1985: **fuera de lo pedido y fuera de lo devuelto**. Son los primeros controles
del proyecto que `loadFromDump` no puede poblar — caen al GET individual de respaldo, que ya
existe y funciona. Todo lo demás de esta sección (Noise Gate, Solo en las dos candidatas, EQ1,
EQ2, Chain) **sí cae dentro del dump** — comprobado offset por offset, y ahora también por un
test.

⚠️ **Y al cablearlo (2026-09-06) apareció el precio, que la nota anterior no anticipaba: son
seis GET de respaldo EN SERIE, en cada recarga.** `loadFromDump` recorre los controles que el
dump no cubrió con un `forEach { control.read() }` secuencial, y cada `read()` espera hasta
`DEFAULT_REPLY_TIMEOUT_MS` (800 ms). Seis controles (3 slots × Shape + Freq Shift) son, si esa
región **no** contesta, **hasta 4,8 s añadidos a cada conexión y a cada cambio de canal** —
porque el cambio de canal dispara la misma recarga (§4.4). Si contesta rápido el coste es
despreciable, así que **lo que decide entre "gratis" y "inaceptable" es justo lo que está sin
probar**: si `60 00 0F 3x` responde al GET.

Se descubrió porque un test de regresión de concurrencia que ya existía empezó a agotar su
margen de 800 ms — el invariante que probaba (nunca dos dumps a la vez) seguía cumpliéndose, lo
que cambió fue cuánto tarda una recarga. Hay ahora un test que **fija en seis** el número de
controles fuera del dump, para que nadie añada un séptimo sin enterarse de lo que cuesta.

**Si resulta que esa región no contesta**, las salidas son ampliar el rango del dump para
cubrir `60 00 0F xx`, o hacer los GET de respaldo en paralelo en vez de en serie. Ninguna de
las dos se ha hecho: las dos son cambios reales al camino de recarga, y hacerlos antes de saber
si hacen falta sería optimizar a ciegas.

##### 4. EQ1 y EQ2 — la posición son dos valores, no tres

**La posición IN/OUT es un selector de dos posiciones**, no de tres, y son dos direcciones
distintas y bastante separadas:

- `60 00 06 22` — EQ1: `00` Amp In · `01` Amp Out (`midi.xml:43559`; asignación `:3967`,
  literalmente `name="Signal chain position: EQ1"`).
- `60 00 06 19` — EQ2: `00` PreAmp In · `01` Pre Amp Out (`midi.xml:43540`; asignación `:3968`,
  `name="Signal chain position: EQ2"`).

⚠️ **Las dos etiquetas dicen lo mismo con palabras distintas** ("Amp In/Out" contra "PreAmp
In/Pre Amp Out") y `midi.xml` escribe `Postion` en los dos sitios. Es cosmético: el rango es
`00`/`01` en ambos y la tabla de asignación los llama a los dos "Signal chain position".

**Los dos EQ tienen bloque interno propio, de 24 bytes cada uno**, y cada uno es en realidad
**dos ecualizadores con un selector**: paramétrico o gráfico.

| | EQ1 | EQ2 |
| --- | --- | --- |
| On/Off | `60 00 00 40` | `60 00 00 60` |
| Selection (`00` Paramétrico · `01` Gráfico) | `00 41` | `00 61` |
| Paramétrico (11 params) | `00 42`–`00 4C` | `00 62`–`00 6C` |
| Gráfico (10 bandas + Level) | `00 4D`–`00 57` | `00 6D`–`00 77` |
| midi.xml | 37562-37728 | 37739-37905 |

Los 11 del paramétrico, en orden: Low Cut (enum 18, `FLAT`..`800Hz`), Low Gain, Lo Mid Freq
(enum 28), Lo Mid Q (enum 6), Lo Mid Gain, Hi Mid Freq (enum 28), Hi Mid Q (enum 6), Hi Mid
Gain, Hi Gain, Hi Cut (enum 15), Level.

⚠️ **Corregido el 2026-09-06: son CUATRO "Gain" más el "Level", cinco escalas centradas en
total, no seis.** Esta línea decía "los cinco Gain y el Level", que da seis; los Gain son Low,
Lo Mid, Hi Mid y Hi — cuatro. Lo cazó un test al cablearlo (`NoPanelControlsTest`, "los cuatro
Gain y el Level del paramétrico son centrados enteros de 20"), no una relectura: la cuenta mal
hecha estaba en la prosa, y el código, que sale de la extracción, siempre tuvo cinco. Las cinco
son `range 00/28/-20/+20 dB` → `LevelScale.centered(20)`
(`midi.xml:37590`/`37631`/`37672`/`37675`/`37695`), la misma escala que el Graphic EQ **interno
de Mod/FX** (§5.2). ✅ Los seis catálogos de frecuencia y Q son **byte a byte los mismos** que
los ya extraídos para el Parametric EQ de Mod/FX — verificado por programa, y hay un test que
lo fija para que una divergencia futura no pase inadvertida.

Las 11 del gráfico (31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1KHz, 2KHz, 4KHz, 8KHz, 16KHz, Level) son
todas `range 00/30/-12.0/+12.0 dB`.

⚠️ **Ojo: el EQ gráfico es de paso fraccionario, y no es el mismo que el de Mod/FX.** Crudo
`0x00`..`0x30` (49 valores) mostrado como `-12.0`..`+12.0` dB da **pasos de 0,5 dB**, no de 1.
El Graphic EQ interno de Mod/FX es `00/28/-20/+20` (entero). Así que estas 22 bandas necesitan
`FractionalLevelScale` (§5.2, "Escala de paso fraccionario"), no `LevelScale.centered`.

✅ **La extensión del bloque de EQ2 la corrobora TuxKatana**: `presets_addrs.yaml:7-9` define
`UserPatch%Eq(2)` con `addr: '60 00 00 60'` y `size: 24` — dirección de inicio y tamaño
idénticos a lo que da `midi.xml`. Es la única dirección de esta sección entera confirmada por
una fuente distinta. EQ1 no tiene entrada propia porque cae dentro de
`UserPatch%Patch_0` (`60 00 00 10`, `size: 72` → `00 10`..`00 57`), que sí lo cubre.

**No confundirlos con el EQ global**, que existe y vive en otro espacio de direcciones: el
bloque `<System>` de `midi.xml` (`00 <sistema> <página> <param>`, el mismo esquema del canal
activo `00 01 00 00` de §5.1) tiene en `00 00 00 10`–`00 00 00 28` un ecualizador de sistema, y
en `00 00 00 2E`–`00 00 01 07` tres slots EQ1/EQ2/EQ3 etiquetados verde/rojo/amarillo con Type
y Position propios (`midi.xml:87-95, 284-287, 300-304, 479-483, 658-662`). **Ese sí tiene
Position de cuatro valores** (`00` Input · `01` Output · `02` Line Out Only · `03` Speaker Out
Only) y es **global, no por preset**. Nada de eso está investigado más allá de constatar que
existe; si algún día se quiere el EQ global, empezar por ahí.

##### 5. Cadena de efectos — sí se puede reordenar, y de dos maneras

**No es una posición fija.** Hay dos mecanismos, y conviven:

- **`60 00 06 20`** — "Chain position" / "Signal Chain order: Type 1~7": un selector con rango
  `range 00/06/00/06`, o sea **siete cadenas predefinidas** (`midi.xml:43552`; asignación
  `:3971`). Es el control simple.
- **`60 00 06 00`–`60 00 06 13`** — **veinte direcciones consecutivas, cada una un selector de
  los mismos 20 identificadores de bloque** (`midi.xml:43074-43427`). Es un **array de
  permutación**: cada posición de la cadena dice qué bloque va ahí. Este es el control fino, y
  es lo que permite un orden arbitrario.

Los 20 identificadores, idénticos en las 20 direcciones: `00` CS · `01` LP · `02` CH_A ·
`03` CH_B · `04` EQ1 · `05` FX1 · `06` FX2 · `07` DD1 · `08` DD2 · `09` RV · `0A` EQ2 ·
`0B` PDL · `0C` FV · `0D` NS_1 · `0E` NS_2 · `0F` OD · `10` USB · `11` CN_S · `12` CAB ·
`13` CN_M.

En el vocabulario del proyecto: `OD` es el Booster, `FX1` es Mod, `FX2` es FX, `DD1`/`DD2` los
dos delays, `RV` la reverb, `NS_1`/`NS_2` el noise gate, `LP` el loop de send/return, `PDL` el
Pedal FX, `FV` el foot volume, `CAB` el cabinet y `CS` el compresor.

Además hay tres selectores de punto de inserción, que son parte del mismo asunto y ya salen en
la tabla resumen: `60 00 06 21` (Loop: Post Amp / Post Reverb), `60 00 06 22` (EQ1) y
`60 00 06 23` (Pedal/FX: Input / Post Amp).

⚠️ **El MK1 no sirve de referencia aquí, y por una vez la diferencia es de fondo, no de
dirección.** `katana_sysex.txt:318-325` y `amplifier.json:129-140` dan la cadena del MK1 como
**una sola dirección** (`60 00 12 00`) con **tres valores** (`One`/`Two`/`Three`). El Mk2 tiene
siete tipos *y* un array de 20. Es un caso donde ni la estructura transfiere.

##### Lo que esto resuelve de paso: `60 00 06 18`

`60 00 06 18` es **`FS2 Func: Function`**, un selector de 8 valores (`00`=1 … `07`=8) —
`midi.xml:43530`. Merece la pena anotarlo porque esa dirección ya aparece en §5 como la primera
candidata fallida del nivel de reverb, sacada de `katana_sysex.txt` (MK1): *"❌ nada: ni sonido
ni estado; el GET devuelve `07` fijo"*. Ahora se entiende el `07`: no era basura ni una
dirección muerta, era **el valor 8 de la función del footswitch 2**. La dirección siempre
estuvo viva; lo que estaba mal era suponer qué había en ella.

##### Qué queda por probar

Nada de esta sección está confirmado con el amplificador. En orden de riesgo:

1. **Solo: cuál de las dos candidatas responde** (`06 14`/`15` contra `00 2B`/`2C`). Es la
   única ambigüedad real, y no hay fuente que la resuelva.
2. **Que la regla `desc`/`customdesc` de la tabla de asignación valga también para direcciones
   que el proyecto no ha medido.** Está verificada contra tres direcciones confirmadas por
   audio, lo cual es un buen indicio, pero las tres son del bloque `06 5x` — no prueba que la
   codificación sea igual de fiable en `05 6x` o `0F 3x`.
3. **Los Contour por slot (`60 00 0F 3x`/`4x`), que además caen fuera del dump**: hay que
   comprobar que el GET individual los devuelve, antes de asumir que el respaldo los cubre.
4. **El paso de 0,5 dB del EQ gráfico**, que es una lectura del rango de `midi.xml` y ya falló
   una vez en un caso parecido (el tramo raro de Delay Time, §5.2).

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
| `60 00 06 18` | katana_sysex.txt (MK1) | ❌ nada: ni sonido ni estado; el GET devuelve `07` fijo — **resuelto el 2026-09-05: es `FS2 Func`**, ver §5 "Controles sin perilla física" |
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
