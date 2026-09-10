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

Qué existe hoy, verificado contra el árbol de código y no contra la versión anterior de este texto.
La lista viva de qué falta probar está en BACKLOG.md, sección "Pendiente por probar".

| Paquete | Qué hay | Estado |
| --- | --- | --- |
| `usb/` | `KatanaUsbScanner`, `KatanaUsbTransport`, `UsbMidiPacket`, `UsbDiagnostics` | ✅ transporte verificado contra el amplificador real (§4.1) |
| `protocol/` | Kotlin puro, cero `android.*`: `MidiBytes`, `Address`, `RolandSysEx`, `RolandExchange`, `SysExFramer`, `KatanaAddresses`, `MemoryDump`/`MemoryImage`, `LevelScale`/`FractionalLevelScale`, los catálogos de tipo, `ModFxInternalParams`, `EqParams`, `SoloEqParams`, `ChainBlock`, `PresetSave`, `tsl/` | ✅ con tests JVM; el núcleo, verificado contra el amplificador |
| `device/` | `KatanaLink` (puerto estrecho), `OfflineKatanaLink`, `KatanaControl` (sellada: `KatanaParameter`, `KatanaFractionalParameter`, `KatanaEnumParameter`), `KatanaRepository`, `model/AmpState` | ✅ los once niveles del panel y los selectores, confirmados con audio |
| `library/` | `PresetLibrary`: los `.tsl` del teléfono | ⚠️ importar y editar offline probados en JVM; exportar necesita el amplificador |
| `ui/` | `AppShell` con barra inferior, tres pantallas de dominio (`AmpScreen`, `EffectsScreen`, `PresetsScreen`) y **Logs** como entrada secundaria; tema propio en `ui/theme/` | ✅ las cinco fases de UI sin amplificador, cerradas |

- **La UI real de control ya existe.** `SlidersPane` y el menú hamburguesa con Logs/Sliders se
  retiraron: hoy la navegación es el enum `DebugSection` (`LOGS`, `AMP`, `EFFECTS`, `PRESETS`)
  resuelto con un `when`, sin `NavHost` (§4.6). El nombre `SlidersPane` solo sobrevive en KDoc.
- **El paquete `midi/` (enfoque `MidiManager`) está eliminado del repositorio**, no conservado como
  referencia histórica: el porqué está en `docs/historial/transporte-y-arranque.md`.
- **514 tests JVM** — `./gradlew :app:testDebugUnitTest`.
- **Lo que falta** es, casi todo, confirmación con hardware: ver BACKLOG.md, "Pendiente por probar"
  y "Por hacer".

## Visión de alcance

Meta de producto: una **alternativa completa a Boss Tone Studio** para el Katana Mk2 desde el
celular por USB, sin PC. El roadmap ejecutable está en BACKLOG.md, "Por hacer"; esto es el destino.

| Objetivo | Estado |
| --- | --- |
| 1. Tipo de efecto por slot de color — el color es un **slot que guarda un tipo**, no un efecto | ✅ confirmado con audio en los cinco efectos |
| 2. Parámetros internos de cada tipo (Gain, Level, Tone, Bottom…) | ✅ extraídos de la fuente y cableados; ⚠️ la mayoría sin confirmar con audio |
| 3. Controles sin perilla física: Noise Gate, Solo, Contour, posición de EQ1/EQ2, cadena | ✅ cableados **salvo Solo**, que tiene dos direcciones candidatas sin desempatar |
| 4. Guardar el estado editado en un canal | ⚠️ cableado por SysEx, sin probar contra el amplificador |
| 5. Import/export de `.tsl` | ⚠️ importar y editar offline probados en JVM; exportar sin probar |
| 6. UI real de control por dominio | ✅ cerrada sin amplificador; queda el pase con el dedo y el aparato delante |

⚠️ La regla que no cambia: **cada pieza se confirma con audio o con el amplificador antes de darla
por buena**, y nunca se asume una dirección por analogía (§5, "Lecciones del protocolo").

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

## 4. Arquitectura

### 4.1 El transporte: USB vendor-specific, NO USB MIDI

**Confirmado con `lsusb -v` sobre el amplificador real** (2026-09-01): el dispositivo **entero** se
enumera con `bDeviceClass 255` (Vendor Specific) y **ninguna** de sus 4 interfaces usa las clases
estándar Audio / MIDIStreaming. Consecuencia dura: **`android.media.midi.MidiManager` nunca podrá
detectar el Katana MK2**, en ningún Android y con cualquier kernel — no es permisos, ni OTG, ni
versión. Se accede con `android.hardware.usb.UsbManager` y transferencias bulk.

| | |
| --- | --- |
| Interfaz | **número 3** — `bInterfaceClass 255`, `bInterfaceSubClass 3`, `bInterfaceProtocol 0` |
| Alternate setting | **0** (el activo por defecto al conectar) |
| Envío (app → amp) | endpoint **`0x03`**, Bulk OUT |
| Recepción (amp → app) | endpoint **`0x84`**, Bulk IN |
| `wMaxPacketSize` | **512 bytes** (USB High Speed) |

⚠️ La interfaz 3 tiene un **alternate setting 1** con los mismos endpoints como *interrupt* y el IN
en `0x85`: hay que filtrar por alternate setting 0 **y** por número de interfaz. Las interfaces 1 y 2
llevan el audio (isócronas) y no intervienen; la 0 no tiene endpoints de datos.

**Trocear y reensamblar.** Con 512 B por paquete, un mensaje largo —el dump son 1920 B— necesita
varios `bulkTransfer()` encadenados. Eso es *además* del framing SysEx `F0…F7`, que es otro nivel:
un `bulkTransfer` puede devolver un SysEx partido, o más de uno. `SysExFramer` hace falta igual.

#### Formato en el cable: paquetes USB-MIDI de 4 bytes

Aunque la interfaz sea vendor-specific, **los datos viajan en el formato de paquete USB-MIDI Class
estándar de 4 bytes**: `byte 0 = (cable << 4) | CIN`, `bytes 1-3` hasta 3 bytes MIDI reales
rellenados con `0x00`. Un SysEx de N bytes se trocea en paquetes de 3 bytes de payload:

| CIN | Significado | Bytes útiles |
| --- | --- | --- |
| `0x4` | SysEx empieza o continúa | 3 |
| `0x5` | SysEx termina con 1 byte | 1 |
| `0x6` | SysEx termina con 2 bytes | 2 |
| `0x7` | SysEx termina con 3 bytes | 3 |

Es **capa de transporte, no capa SysEx**: `protocol/` trabaja con mensajes `F0…F7` limpios y
empaquetar/desempaquetar es cosa de `usb/`. ✅ Verificado (2026-09-02): un Identity Request
empaquetado obtuvo un Identity Reply bien formado, validando de una vez interfaz, endpoints,
empaquetado y el model id `33`.

⚠️ **`packUsbMidi` solo emite los CIN `0x4`–`0x7` y da por hecho una trama `F0…F7`.** Mandar un PC o
un CC exige una segunda ruta de empaquetado (CIN `0xC` con 2 bytes, `0xB` con 3). La dirección
contraria sí está resuelta: `payloadLengthOf` cubre la tabla entera, así que un PC/CC **entrante** se
desempaqueta bien.

⚠️ **Handshake: investigación cerrada, no se usa.** El mensaje que `MS3.h` documenta como obligatorio
para el MS-3 **no obtiene respuesta del Katana** — ni con model id `33`, ni con la trama byte a byte
idéntica al Identity Reply del propio amplificador. Ningún flujo lo necesita; el botón de diagnóstico
se conserva solo para reproducirlo. Ramas sin explorar: `docs/historial/transporte-y-arranque.md`.

**Por qué las apps de escritorio sí funcionan**: en Linux, `snd-usb-audio` trae quirks Roland/Boss
que exponen estas interfaces como puertos ALSA MIDI; Android no hace esa traducción. Por eso
`reference/` vale **para el protocolo** (§5) pero **no** para el transporte.

**Notas de `android.hardware.usb` que siguen mordiendo:**

- Requiere OTG/USB host: el manifest necesita `android.hardware.usb.host`, y
  `<uses-feature android:name="android.software.midi">` **ya no aplica**.
- Apertura: `deviceList` → `requestPermission()` si falta (**diálogo del sistema, asíncrono, llega
  por `PendingIntent`**) → `openDevice()` → `claimInterface(3, force = true)` → `bulkTransfer()`.
- `bulkTransfer()` **bloquea** (va en `Dispatchers.IO`, nunca en el hilo principal; la lectura es un
  bucle propio con timeout que alimenta un `Flow<ByteArray>`) y devuelve bytes transferidos o `-1` en
  error **o timeout** — un timeout sin datos es normal, **no** es una desconexión.
- Liberar siempre (`releaseInterface()` + `close()`) al desconectar o destruir el `ViewModel`, y
  atender `USB_DEVICE_DETACHED`.
- ⚠️ **`UsbDevice.getInterfaceCount()` no es `bNumInterfaces`**: Android crea un `UsbInterface` por
  cada par (interfaz, alternate setting), así que el Katana declara 4 y devuelve 7. Buscar la de
  control por `id == 3` **y** `alternateSetting == 0`.
- El `device_filter` de `USB_DEVICE_ATTACHED` sigue siendo la vía para lanzar la app al conectar;
  falta rellenarlo con el vendor-id / product-id reales (en **decimal**).

### 4.2 Capas

Tres capas, con una regla dura: **el protocolo es Kotlin puro y testeable en JVM**.

```
dev.alonx3.ktnacontrol/
├── MainActivity.kt
├── protocol/     ← Kotlin puro. CERO imports de android.*
│   MidiBytes · Address (base 128) · RolandSysEx (checksum, GET/SET, troceado)
│   RolandExchange (petición↔respuesta, recogida por silencio) · SysExFramer
│   KatanaAddresses · MemoryDump (lo que contestó el amp) · MemoryImage (escribible)
│   LevelScale · FractionalLevelScale · ParamSpec · ModFxInternalParams (31 bloques)
│   AmpType (+AmpCategory, AmpVariation, EffectColor) · {Boost,Delay,Reverb,ModFx}Type
│   EqParams · SoloEqParams · ChainBlock · PresetSave · tsl/ (Parser, Writer, Transfer)
├── usb/          ← único lugar que toca android.hardware.usb
│   KatanaUsbScanner · KatanaUsbTransport · UsbMidiPacket · UsbDiagnostics
├── device/       ← estado del amplificador (fuente única de verdad)
│   KatanaLink (send + incoming) · OfflineKatanaLink (§4.5) · KatanaRepository
│   KatanaControl: sellada → KatanaParameter, KatanaFractionalParameter,
│                  KatanaEnumParameter (§4.3)      ·      model/AmpState
├── library/PresetLibrary.kt   los .tsl de este teléfono
└── ui/
    theme/    Color · Type · Spacing · Contrast · Theme
    screens/  AppShell · AmpScreen · EffectsScreen · PresetsScreen · LibraryPane
              DebugConnectionScreen (Logs) · ShellState · AmpDomain · ControlPaging
              Controls · PresetEditor · PresetSendFlow · los dos ViewModel
```

```
UI (Compose) → ViewModel → KatanaRepository → KatanaLink → [Katana | MemoryImage]
                                ↑                bulk 0x03 ↑↓ 0x84    │
                          StateFlow<AmpState>  ←  parse ← SysExFramer ┘
```

**Reglas duras.** Ninguna es opcional y todas costaron un bug:

- La UI **nunca** construye bytes SysEx ni conoce direcciones: habla de parámetros de dominio por sus
  enums (`LevelId`, `EffectId`, `SelectorId`, `NoPanelParamId`).
- `KatanaRepository` mantiene la copia local y la expone como `StateFlow`; escribir un parámetro =
  actualizar caché + enviar SysEx (optimista, *fire-and-forget*).
- **Al conectar se lee el dump, no un GET por control** (§4.4).
- **Anti-eco**: el amplificador también reporta por su cuenta (perillas físicas, valores derivados);
  al aplicar un mensaje entrante **no** se reenvía al amp.
- **Anti-flood**: arrastrar un slider genera decenas de eventos; se coalescen por dirección con un
  debounce de ~100 ms antes de enviar.
- **Edit mode** (`7F 00 00 01` → `01`) ✅ confirmado: sin él, mover una perilla física **no** genera
  ningún mensaje. Es lo que hace que el amp reporte, así que se activa como parte del flujo normal de
  conexión — pero como **ajuste explícito y visible, nunca en silencio**, y siempre con forma de
  desactivarlo. Es DT1: no devuelve confirmación.
- ⚠️ **Nunca ha habido, ni debe haber, un gate de edit mode en `device/` ni en `protocol/`**: esas
  capas no saben qué es y el SET sale al cable siempre; quién puede editar lo decide la UI. Hay un
  test JVM que lo fija.
- **`ShellState.availabilityOf(state, editMode)` es la única definición de `canEdit` del proyecto**
  (puro, con tests): `NoAmp` explica **qué falta y qué hacer**, con el botón de buscar a mano —un
  panel gris sin explicación se lee como un bug—; `NeedsEditMode` deja los controles grises con su
  aviso; `Ready` permite editar.
- **El selector de canal es la única excepción a `canEdit`** (`enabled = connected`): cambiar de canal
  es un comando básico, no un ajuste fino. ⚠️ Falta confirmar con el amplificador si acepta ese SET
  con edit mode apagado (BACKLOG.md, "Pendiente por probar" y "El canal depende de Edit Mode").
- **La Biblioteca queda fuera de ese contrato, deliberadamente**: funciona desenchufada, que es su
  razón de ser (§4.5). Sí lo respeta el bloque **en vivo** de `PresetsScreen` (guardar, exportar).

### 4.3 Dos tipos de control: continuo y selector

Casi todo lo que la app controla es **un byte en una dirección** —salvo el canal activo y algún
parámetro de 2 bytes—, así que todo comparte la misma maquinaria (caché, GET, SET optimista, regla
anti-eco) con un `byteWidth` configurable. Vive en `device/KatanaControl.kt`, una clase sellada:

| | `KatanaParameter` | `KatanaEnumParameter` |
| --- | --- | --- |
| Valores | una `LevelScale` (crudo ↔ mostrado) | una `List<Int>` con huecos |
| Fuera de rango | **clampea** al extremo más cercano | **rechaza**: ni envía ni toca la caché |
| Debounce | ~100 ms, se arrastra | ninguno, es un toque |
| Ejemplos | los once niveles del panel | amp type, color, on/off |

(`KatanaFractionalParameter` es el mellizo de `KatanaParameter` con `displayValue`/`setLevel` en
`Double`, para las escalas de paso 0,5 dB / 0,1 s.)

Las dos diferencias salen de la misma raíz: **los valores de un selector no son contiguos**, así que
"el valor legal más cercano" no significa nada y adivinar sería peor que no hacer nada. Importa
sobre todo en la entrada: si el amp reporta un valor que no está en la tabla, se **deja la UI como
está** y el valor va al log, no se mueve el control a un vecino inventado.

⚠️ **Un nivel tiene dos caras y hay que usar la correcta.** `state` / `set` hablan en **bytes
crudos**; `displayValue` / `setLevel` hablan en **lo que se muestra**. No son el mismo número en los
cinco niveles de efecto, donde el crudo va desplazado en uno porque su `0` significa Off (ver
`LevelScale`). Cualquier cosa que mueva un slider quiere la segunda cara; el log y los tests razonan
en bytes, que es la única forma honesta de hablar de lo que el amplificador guarda.

Las tablas de valores y las escalas viven en `protocol/`, son Kotlin puro y tienen tests JVM. La UI
las consume por sus enums y **nunca ve una dirección**.

### 4.4 Poblar el estado: un dump, no un GET por control

Al conectar —**y al cambiar de canal**, porque cada canal tiene sus propios valores para todo— se
pide el dump y se aplica control por control:

```
KatanaRepository.loadFromDump()
  → GET 60 00 00 00, tamaño 00 00 0F 00        (1920 B pedidos; el Mk2 real devuelve 1860)
  ← varios mensajes Roland, cada uno con su dirección base y su longitud
  → MemoryDump.from(mensajes)                   protocol/, puro, con tests JVM
  → por cada control: dump.bytesAt(dirección, byteWidth) → applyDumpValue
  → lo que el dump no cubra: un GET individual, en serie, como respaldo
```

✅ Verificado (2026-09-03): 8 mensajes, 1860 B, 24 de 24 controles poblados, 0 GET de respaldo.
Reglas, todas salidas de fallos reales:

- **La recogida termina por silencio (~250 ms sin nada nuevo), no por ventana fija**, con tope de 3 s.
  Con ventana fija los controles tardaban **más** que los GET que sustituían.
- **`sendAndCollectUntilQuiet` filtra con un predicado `accept`** (`blockReplyIn(...)`: dentro del
  rango pedido **y** payload mayor que `MAX_CONTROL_PAYLOAD`), y **un rechazado no reinicia el
  contador de silencio**. Sin eso, un reporte espontáneo llegado durante el dump se cuela como
  `Chunk` y estira la ventana hasta el tope.
- **`applyDumpValue` recibe el `MemoryDump` entero y lee sus propios `byteWidth` bytes**: todo o nada.
  Un byte alto suelto convertía el canal en Panel.
- **Un único coordinador de recargas**: conexión, cambio de canal y cambio de cadena alimentan el
  mismo `MutableStateFlow<ReloadRequest>` conflado, con un solo `collectLatest` y `Mutex` alrededor de
  `loadFromDump()`. Dos caminos independientes daban dos dumps simultáneos comiéndose los mensajes.
- ⚠️ **Un trigger que se repuebla *dentro* del dump necesita guard**: el canal vive fuera, la cadena
  (`06 20`) vive dentro, y su repoblación cancelaba la recarga en vuelo y lanzaba otra. Se apaga
  mientras `_reloadInFlight` está en `true`.
- **`MemoryDump` es una búsqueda por dirección**, no un índice sobre un array plano, y no asume número
  de mensajes ni total; una dirección ausente es `null`, no un error. Los trozos **sí** son contiguos,
  pero **filtrar por contigüidad sería mala idea**: si algún día hubiera un hueco, descartaría en
  silencio todo lo posterior.
- **Aplicar el dump recorre la lista de controles, no los campos de `AmpState`**: así añadir un
  parámetro no puede olvidarse de actualizar el volcado, porque no hay nada que actualizar.

La cadena de cuatro fallos encadenados que llevó a estas reglas está en
`docs/historial/arquitectura-y-estado.md`, y vale como recordatorio de que **un argumento estructural
convincente no es una comprobación**.

### 4.5 Editar sin amplificador: el backend de un control es el `KatanaLink`

`KatanaLink` tiene dos operaciones (`send` e `incoming: Flow<ByteArray>`) y `KatanaControl` no usa
nada más. Una implementación que **guarde los bytes de un SET en un mapa dirección→byte y conteste a
un GET desde ese mismo mapa** hace funcionar los mismos controles, el mismo repositorio y los mismos
composables sin cable: `device/OfflineKatanaLink.kt` sobre `protocol/MemoryImage.kt`. No hizo falta
ninguna abstracción nueva.

| | Mutable | Fidelidad |
| --- | --- | --- |
| `MemoryImage` | **sí** | **total**: guarda bytes que nadie interpreta |
| `MemoryDump` | no | total, pero de solo lectura |
| `AmpState` | no | **lossy a propósito**: es para leer, no para guardar |

- ⚠️ **Lo que se guarda y se transporta son bytes; `AmpState` es para enseñar.** Un `.tsl` son 1141
  bytes y `AmpState` entiende 24 valores: cualquier exportación que pase por `AmpState` pierde
  información aunque no lo parezca. Con la imagen de bytes, **lo que la app no entiende se conserva
  intacto porque nunca se toca** — para un formato de intercambio eso es el requisito.
- ⚠️ **`device/` sigue sin saber si está en vivo**, igual que no sabe qué es el edit mode: hace su SET
  siempre, y el destinatario lo decide quien construye el `KatanaRepository`.
- ⚠️ **`awaitRolandReply` y `sendAndCollectUntilQuiet` se suscriben con `CoroutineStart.UNDISPATCHED`
  *antes* de llamar a `send`**: es lo que hace que una respuesta **síncrona**, emitida desde dentro de
  `send`, no se pierda. No tocar ese orden.
- La UI del editor offline es `PresetEditorBody` (`AmpSection` + `EffectsSection` + `NoPanelPane`),
  privado junto a su único llamador en `LibraryPane.kt`.

Por qué no se abstrajo un "backend de parámetro" ni se hizo de `AmpState` la fuente de verdad:
`docs/historial/arquitectura-y-estado.md`.

### 4.6 Reglas de UI vigentes

Las cinco fases de UI están cerradas. Aquí solo la regla; el razonamiento y las alternativas
descartadas, en `docs/historial/decisiones-ui.md`.

- **Sin `NavHost`**: lo que habría que "navegar" es una `EditingSession` con un `KatanaRepository`
  vivo, y un argumento de ruta es dato serializable (además `viewModel()` se scopea por ruta y lista
  y editor recibirían instancias distintas). Es el enum `DebugSection` con un `when`.
- **El back vive en `ShellState`**, puro y con tests: preset abierto → lo cierra preguntando si hay
  cambios; otra pestaña → vuelve a Amplificador; Amplificador → cierra la app.
- **Un composable de dominio se comparte, no se copia** (`AmpSection`, `EffectsSection`,
  `NoPanelPane`, usados por la pantalla en vivo y por el editor offline); lo que tiene un solo
  llamador vive junto a él.
- **`AmpDomain` reparte los controles entre pantallas como dato**, con un test que exige cada
  `SelectorId` clasificado en exactamente un sitio: sin él, un control quedaría invisible en las dos
  pantallas sin que nada falle. `PANEL_LEVEL_ORDER` lleva `require` de permutación exacta por lo mismo.
- **`ui/theme/Color.kt` es la única lista de colores**: ningún composable escribe un `Color(0x…)`.
- **`ui/theme/Spacing.kt` es la escala de espaciado y solo de espaciado**; el diámetro de una perilla
  es `.dp` de componente, y ⚠️ ningún `.dp` de tamaño debe pasar a `.sp`.
- **La app es siempre oscura**: sin `darkTheme` y con `dynamicColor` **retirado, no en `false`** — el
  fondo de pantalla podría poner una superficie amarillenta bajo un slot amarillo.
- ⚠️ **Acento y colores de slot nunca comparten rol**: el acento **rellena** controles, el slot es
  franja de 4 dp + punto, `error` solo tiñe texto — y el slot nunca es la única vía, el
  `ChipSelector` lo dice con palabras.
- **Contraste calculado, no estimado** (`ui/theme/Contrast.kt`, puro y con tests): texto ≥ 4.5:1,
  objeto gráfico ≥ 3:1; los pares están en el KDoc de `Color.kt`. `Unknown` queda fuera a propósito.
- **`ControlPaging` deriva las columnas del ancho** (3–6). ⚠️ `fixedColumns` vale **solo cuando el
  reparto en páginas significa algo** — hoy solo los niveles del panel, dos tríadas.
- **A la tira vertical van los continuos, no los selectores**; excepción deliberada: frecuencias y Q
  del EQ paramétrico siguen en perilla, porque ahí el barrido es el gesto correcto.
- ⚠️ **Paginado y arrastre conviven por dos mecanismos**: separación de eje (y de tiempo, la perilla
  exige mantener pulsado) **y** `userScrollEnabled = !adjusting`. Todo control con arrastre avisa a
  `KnobInteraction`, con su `DisposableEffect` de seguro.
- **Un solo GET por pantalla** («Releer del amplificador»), no uno por parámetro: dispara
  `loadFromDump()`, superconjunto del GET individual. **No cae bajo `canEdit`**: leer no es escribir.
- **`DestructiveConfirmDialog`** es el segundo paso de los dos flujos que escriben sin deshacer y sin
  confirmación del amp (guardar en canal, enviar al amplificador). ⚠️ Guardar/Guardar-como **no**
  entra: es de un solo paso y pisa un fichero, no hardware; solo comparte el patrón «Sí, `<verbo>`».
- **La Biblioteca funciona desenchufada** y el bloque en vivo de `PresetsScreen` no: esa asimetría
  de habilitación es la señal que separa "escribe en hardware" de "son ficheros".
- **Los cambios sin guardar sobreviven al cambio de pestaña** (la sesión vive en `LibraryViewModel`)
  y **"Volver" dentro del editor sí pregunta**.
- ⚠️ **Un hijo con `weight(1f)` en una `Column` puede medir cero** si los hermanos sin peso se quedan
  el alto: es el bug por el que la Biblioteca "no aparecía". Un contenedor con scroll y una ranura
  `header`, nunca dos scrolls anidados en el mismo eje.

## 5. Dónde está documentado el protocolo SysEx

Esta sección describe **qué bytes** se intercambian; cómo llegan al amplificador es §4.1, y el
transporte vendor-specific no invalida nada de lo que sigue. Las fuentes viven en `reference/`
(§7), por orden de utilidad:

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
| `60 00 00 00` | Inicio del bloque "efectivo" (estado actual). Los primeros 16 bytes son el nombre del preset actual — ⚠️ lo que devolvió el amplificador ahí no se parecía a un nombre; tres fuentes dicen que el nombre va ahí, así que la discrepancia está en la lectura. Dump completo pidiendo tamaño `00 00 0F 00` (1920 B), que llega en varios mensajes |
| `7F 00 00 01` | Edit mode / "BTS control mode" (`00` off, `01` on) |
| `7F 00 01 04` | Guardar estado actual en un canal (**2 bytes**, `00 xx`; `00` PANEL, `01`..`08` los ocho canales — la misma numeración de `00 01 00 00`). Ver §5 "Guardado de presets" |
| `00 01 00 00` | Canal/preset activo (valor de **2 bytes**; `00` panel, `01`..`08` canales). Ver §5.1 |
| `00 02 00 00` | Canal MIDI de recepción (`00`..`0F` = MIDI 1..16) |
| `10 00`–`10 04 xx xx` | Bloques Panel / Ch1–Ch4 |


### Lecciones del protocolo

Reglas destiladas de errores ya cometidos; el caso completo, en el archivo de `docs/referencia/` que
corresponda.

- **Solo el audio confirma una dirección.** Checksum correcto, bytes bien formados y respuesta al GET
  no demuestran nada: `60 00 06 18` cumplía las tres y era `FS2 Func`, no el nivel de reverb. Prueba
  mínima: SET a los extremos → ¿cambia el sonido? → GET → ¿cambió el estado? → mover la perilla
  física → ¿reporta por esa dirección?
- **Confirmar por audio confirma que la dirección responde, no *qué* es.** `60 00 06 5A` se probó con
  el delay 2 apagado, así que "nivel de delay 1" y "mix global de los dos delays" daban lo mismo.
- **Un valor que rebota solo delata una dirección de solo lectura**: la escritura optimista lo pone,
  el amp ignora el SET y sigue reportando el suyo. Así se vio que `60 00 06 5C` es un LED.
- **Las direcciones por parámetro del MK1 no valen para el Mk2.** Sí valen su formato de mensaje, el
  checksum y las de sistema (`10 xx`, `60 00 00 00`, `7F 00 00 01`). La **estructura** de un bloque
  transfiere; los offsets no, y no son uniformes (booster `-0x20`, mod `-0x40`).
- **El ruido de las fuentes no predice el resultado**: `60 00 06 57` estaba bajo `Unimplemented:` y
  funcionó; `60 00 05 48` estaba limpiamente en `SEND` y no hace nada. Y una fuente puede afirmar lo
  contrario con flechas explícitas y seguir equivocada (`Adresses.txt` sobre `60 00 06 51`).
- **Dos fuentes que se contradicen no se promedian ni se fusionan**: se mantienen separadas hasta que
  el hardware desempate (`DelayHighCutFrequency` contra `ReverbHighCutFrequency`).
- **FX = Mod + `0x0200`**, verificado sobre los 237 nodos de cada bloque: **0** diferencias de
  dirección, de nombre y de rango; las 14 diferencias son solo de etiqueta.
- **`00` = off, `01` = on**, en todo el aparato.
- **Un selector rechaza, un nivel clampea**: los catálogos de *tipo* tienen huecos reales (`AmpType`
  sin el `0x19`, `BoostType` sin el `07`), así que "el valor legal más cercano" no significa nada.
  Los selectores **internos** de Mod/FX, en cambio, corren `0..N-1` sin huecos — los 36 — pero se
  registran igual como `KatanaEnumParameter`: muestran texto, no un número en una escala.
- **Un parámetro de 2 bytes se lee y escribe entero, o no se toca** (`byteWidth`, MSB×128+LSB).
- **En Mod y FX, una dirección solo significa lo que dice la tabla mientras el tipo activo del slot
  sea ese**; con otro tipo, `60 00 02 3A` no es un Pre Delay. La visibilidad se condiciona en la UI.
- **Lo que cae fuera del dump cuesta un GET en serie de hasta 800 ms**, en cada conexión y cada
  cambio de canal. Hoy son **seis** controles (los Contour por slot) y **un test fija ese número**.
- **Un argumento estructural convincente no es una comprobación.** "No hay bucle porque el canal vive
  fuera del dump" sonaba impecable y era falso (§4.4).

### Incógnitas abiertas del protocolo

Ninguna resuelta. Nada de esto se decide por analogía: o lo dice el amplificador, o sigue abierto.

| Incógnita | Estado | Detalle en `docs/referencia/` |
| --- | --- | --- |
| Solo: `60 00 06 14`/`15` (panel) contra `60 00 00 2B`/`2C` (preamp) | dos candidatas, ninguna fuente desempata; **sin cablear a propósito** | `controles-sin-perilla.md` |
| Contour por slot: `0F 30`/`38`/`40` (`midi.xml`) contra `0F 2E`/`36`/`3E` (aritmética de FxFloorboard) | se documenta la de `midi.xml`; difieren en 2 con el mismo paso de 8 | `controles-sin-perilla.md`, `formato-tsl.md` |
| ¿Responde la región `60 00 0F xx` a un GET? | decide si los seis GET de respaldo son gratis o inaceptables | `controles-sin-perilla.md` |
| SOLO EQ (`60 00 0F 10`–`0F 19`) | localizado con dos testigos; **deliberadamente sin cablear** por el coste de los GET de respaldo | `controles-sin-perilla.md` |
| `UserPatch%Patch_Mk2V2` (`0F 10`–`0F 25`, 22 B) | a medias: SOLO EQ (10 B) + SOLO DELAY (12 B, localizado sin extraer) | `formato-tsl.md` |
| `GafcExp1AsgnMinMax`: `09 30` (yaml) contra `09 34` (aritmética) | se documenta `09 30`, que encaja con sus dos hermanas | `formato-tsl.md` |
| Tamaño máximo de un SET | sin fuente; se trocea a 128 B de datos por analogía con el volcado de patch | `formato-tsl.md` |
| `00` (PANEL) como destino de guardado | ¿legal, o artefacto de la interfaz de FxFloorboard? | `guardado-presets.md` |
| ¿El guardado confirma? ¿intervalo mínimo? ¿qué reporta "Saving in progress..."? | TBD; hipótesis: fire-and-forget y aviso solo del panel | `guardado-presets.md` |
| CC#8 / CC#9: ¿los atiende el amplificador o el puente MIDX-20? | TBD; además la app aún no sabe empaquetar CC | `guardado-presets.md` |
| Delay Time, tramo `MSB=0x0E` (1792–1919 ms) | el ancho del crudo no cuadra con ningún otro tramo; probable error de la fuente | `delay-reverb-y-escalas.md` |
| `6.00k` contra `6.30K` en `0x0A` | 4 sitios de `midi.xml` contra 1; dos enums separados hasta que haya hardware | `delay-reverb-y-escalas.md` |
| Orden verde/rojo/amarillo de `EffectColor` | 3 testigos contra una tabla que se contradice sola; falta mirar el LED del panel | `docs/historial/decisiones-ui.md` |
| Si cambiar el tipo de un color **resetea** los parámetros de ese slot | sin respuesta en ninguna fuente | `canal-y-tipos-de-efecto.md` |

## Mapa de `docs/`

**`docs/referencia/` se consulta a demanda**, cuando la tarea toca ese tema. Un archivo por tema,
para que leer uno no obligue a leer los demás:

- `protocolo-fuentes-y-formato.md` — al buscar una dirección nueva: qué fuente consultar y con qué
  prioridad, más el formato del mensaje Roland.
- `direcciones-mk2.md` — al descubrir o verificar una dirección: el proceso, el bloque de perillas `06 50`–`06 5B`, amp type / color / on-off, las escalas del panel.
- `canal-y-tipos-de-efecto.md` — al tocar el canal activo (`00 01 00 00`) o el tipo de efecto por slot de color.
- `modfx-parametros.md` — al cablear parámetros internos de Mod o FX: los 31 bloques, sus selectores y lo que no encaja en el patrón.
- `delay-reverb-y-escalas.md` — al tocar Delay 1, Reverb o una escala de paso fraccionario.
- `controles-sin-perilla.md` — al tocar Noise Gate, Solo, Contour, EQ1/EQ2, la cadena o SOLO EQ.
- `guardado-presets.md` — al trabajar en guardar el estado en un canal (`7F 00 01 04` o CC#8/#9).
- `formato-tsl.md` — al trabajar en importación o exportación de `.tsl`.

⚠️ **`docs/historial/` no se lee salvo para buscar un precedente concreto**, y preferentemente con
`grep`: es la narrativa de cómo se llegó a lo que hoy son reglas, no documentación de uso. Leerlo
por costumbre reintroduce exactamente el coste que este archivo acaba de quitarse.

- `transporte-y-arranque.md` — handshake, el enfoque `MidiManager` descartado, la evaluación de kshoji/USB-MIDI-Driver y la evidencia de verificación del transporte.
- `arquitectura-y-estado.md` — el §2 y la visión anteriores, las capas, los dos tipos de control, la cadena de cuatro fallos del dump y por qué el offline es un `KatanaLink`.
- `decisiones-ui.md` — partición y cierre de `SlidersPane`, navegación sin `NavHost`, sistema de diseño, estados de la Biblioteca, accesibilidad y el QA del 2026-09-09.

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

Se evaluó **kshoji/USB-MIDI-Driver** (pensada para dispositivos no estándar sobre la USB Host API,
con soporte para vendor-specific de Roland/Boss) y **se descartó** (2026-09-03). No es una
predicción: el transporte y el protocolo enteros ya funcionan contra el amplificador sin ella, lo
que aportaría son unas pocas decenas de líneas que ya existen en `usb/` con tests JVM propios, el
handshake de Boss habría que añadirlo por fuera igualmente, y traería su propia abstracción de
"puertos MIDI" encima de `KatanaLink`/`KatanaControl`. Se rehace la evaluación si algún día hay que
hablar con otros dispositivos MIDI class-compliant. Detalle:
`docs/historial/transporte-y-arranque.md`.

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

- [BACKLOG.md](BACKLOG.md) es el registro de avance del proyecto, con cinco secciones: **Hecho**,
  **En progreso**, **Por hacer**, **Pendiente por probar** y **Notas y decisiones técnicas**.
- **Cada vez que completes una tarea de desarrollo**, actualiza `BACKLOG.md` de inmediato: mueve el
  ítem a "Hecho" (y quítalo de "En progreso"/"Por hacer") y, si hubo una decisión técnica relevante
  (elección de dirección SysEx, trade-off de arquitectura, por qué se descartó una alternativa),
  agrégala en "Notas y decisiones técnicas". Si la tarea incluye alguna prueba con el amplificador
  físico, añádela a "Pendiente por probar" junto al resultado esperado si sale bien.
- Esto se hace **sin que se te tenga que pedir explícitamente en cada sesión** — es parte de terminar
  la tarea, igual que compilar o testear.
- ⚠️ **Las referencias entre documentos van por nombre de sección o de ítem, nunca por número de
  punto ni de línea**: esas numeraciones se mueven y dejan punteros que mienten.

### Presupuesto de este archivo

CLAUDE.md se carga **entero al inicio de cada sesión**, así que su tamaño es un coste fijo que pagan
todas las tareas, incluidas las que no tocan nada de lo que aquí se cuenta.

- **Tope duro: 600 líneas.** Si una tarea lo excede, esa misma tarea mueve detalle a `docs/`.
- **CLAUDE.md guarda reglas y decisiones vigentes, no narrativa ni evidencia.** La investigación nueva
  y su evidencia (citas de fuente, trazas, tablas de extracción) van **directas** al archivo temático
  de `docs/referencia/`, o a uno nuevo; aquí, a lo sumo 3–5 líneas de resumen y el puntero.
- **Cuando algo queda obsoleto, se corrige o se mueve a `docs/historial/` en la misma tarea que lo
  vuelve obsoleto.** Un CLAUDE.md que contradice al código es peor que uno largo: induce a error en
  vez de solo costar tokens.
- **Las tablas de datos ya implementadas en código no se duplican aquí**: el código es la fuente de
  verdad — los 31 bloques de Mod/FX en `protocol/ModFxInternalParams.kt`, los catálogos de tipo en
  `protocol/{Amp,Boost,Delay,Reverb,ModFx}Type.kt`, las direcciones en `protocol/KatanaAddresses.kt`,
  el EQ y la cadena en `protocol/EqParams.kt`, `SoloEqParams.kt` y `ChainBlock.kt`.
- ⚠️ **Nunca usar la sintaxis `@ruta` para apuntar a un archivo desde aquí**: eso lo importaría al
  contexto en cada sesión y anularía todo lo anterior. Ruta en texto plano o enlace markdown normal.
- ⚠️ **Ningún archivo dentro de `docs/` puede llamarse `CLAUDE.md`**: Claude Code carga
  automáticamente los `CLAUDE.md` de subdirectorios cuando lee archivos ahí.
