# BACKLOG

Registro de avance de KTNA Control, estilo backlog de sprint. Ver [CLAUDE.md](CLAUDE.md)
para arquitectura, mapeo del protocolo y convenciones — este archivo es histórico y de
seguimiento, no repite lo que ya está ahí.

## Hecho

- **2026-09-01** — [CLAUDE.md](CLAUDE.md): arquitectura planeada, mapeo del protocolo SysEx
  dentro de `reference/` y convenciones de Kotlin/Compose para el proyecto.
- **2026-09-01** — Configuración base de acceso USB MIDI en el manifest: `uses-feature` de
  `android.software.midi` (required) y `android.hardware.usb.host` (no required),
  intent-filter `USB_DEVICE_ATTACHED` en `MainActivity` con su `meta-data` apuntando a
  `@xml/device_filter`, y `res/xml/device_filter.xml` creado vacío. Verificado con
  `:app:assembleDebug` y revisando el manifest fusionado.
- **2026-09-02** — Transporte USB vendor-specific, reemplazando por completo el paquete
  `midi/` (borrado): `usb/UsbDiagnostics` (ids, endpoints y modelo puro),
  `usb/KatanaUsbScanner` (enumerar, filtrar por 1410/472, permiso vía `PendingIntent`) y
  `usb/KatanaUsbTransport` (`claimInterface(3)`, `sendRaw` / `receiveRaw` sobre `0x03` y
  `0x84`, `close()` que libera la interfaz). Manifest corregido —fuera
  `android.software.midi`, `usb.host` a `required="true"`— y `device_filter.xml` completado
  con 1410 / 472. La pantalla de diagnóstico ahora lista dispositivos USB y añade el botón
  «Enviar Identity Request». Compila, pasa lint e instalado en el celular.
- **2026-09-02** — Empaquetado USB-MIDI de 4 bytes y handshake, en `usb/UsbMidiPacket.kt`
  (`packUsbMidi` / `unpackUsbMidi`, Kotlin puro) y `usb/KatanaUsbTransport` (`sendRaw`
  empaqueta, `receiveRaw` desempaqueta, `receiveWire` devuelve el cable tal cual para
  diagnóstico, y `sendHandshake()` envía la trama dos veces con ~4 ms). Constantes del
  handshake en `KatanaHandshake`, con `MODEL_ID_MS3` al lado para intercambiar. Botón
  «Enviar Handshake» en la pantalla. **9 tests JVM** en `UsbMidiPacketTest` cubren los tres
  restos módulo 3, el round trip de 1 a 64 bytes, el relleno y los CIN de no-SysEx; los 10
  tests del proyecto pasan. Corregido de paso el conteo de interfaces (4 reales, 7 alt
  settings).
- **2026-09-02 — 🎉 HITO: primera comunicación end-to-end con el amplificador real.**
  Probado en el celular con el Katana por OTG. El dispositivo se enumera y se abre sin
  problema (`KATANA`, BOSS, vendorId 1410 / productId 472, clase 255, 4 interfaces / 7 alt
  settings — el conteo corregido salió bien), se reclama la interfaz 3 y **el Identity
  Request obtiene respuesta**:

  ```
  → sysex: F0 7E 7F 06 01 F7
    wire:  04 F0 7E 7F 07 06 01 F7          (8 bytes escritos)
  ← wire:  20 bytes
    desempaquetado: F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7
  ```

  Es un Identity Reply estándar y coincide con el que documenta
  [HOW.md](reference/TuxKatana/HOW.md). Queda validado de una sola vez: la interfaz y los
  endpoints (3, `0x03`/`0x84`), el empaquetado de 4 bytes en ambos sentidos, y que el amp
  atiende a broadcast (`7F`) respondiendo como device ID `00` con model id `33`.

- **2026-09-02** — Arranque de `protocol/`, Kotlin puro sin `android.*`: `MidiBytes`
  (aritmética de 7 bits), `Address` (4 bytes con acarreo en base 128), `RolandSysEx`
  (construcción GET/SET, checksum y parseo con validación) y `KatanaAddresses` (por ahora
  solo `DEVICE_NAME`). **22 tests JVM nuevos** contra los vectores reales de HOW.md y
  katana_sysex.txt: el GET del nombre del dispositivo, los de presets 1 y 2, el dump de
  memoria, el SET de edit mode, el ejemplo de checksum de la spec, y el parseo de la
  respuesta real con su checksum `76` → `"KATANA Mk2"`. 32 tests en total, todos pasan.
  Botón «Leer nombre del dispositivo» en la pantalla de diagnóstico.
- **2026-09-02 — ✅ Primer GET real confirmado contra el amplificador: `RolandSysEx` funciona
  de punta a punta.** Pulsando «Leer nombre del dispositivo» el amp devolvió **30 bytes**
  (el mensaje SysEx completo, ya desempaquetado), con **checksum válido**, dirección
  `10 00 00 00` y 16 bytes de datos que se leen como **`"KATANA Mk2"`**.
  - Los 30 bytes cuadran exactamente con lo previsto: 7 de cabecera + 1 de comando + 4 de
    dirección + 16 de datos + 1 de checksum + 1 de terminador.
  - Esto valida lo único que el Identity Request no ejercitaba —ese es un mensaje universal,
    sin prefijo Roland ni checksum—: **construcción de la dirección de 4 bytes, cálculo del
    checksum al enviar, y validación del checksum más extracción del payload al recibir.**
    Sumado al hito anterior, la pila entera (USB → empaquetado → SysEx Roland) está
    confirmada contra hardware real.

- **2026-09-02** — Direcciones de los nombres de los 8 presets en `KatanaAddresses`,
  **generadas con la aritmética de `Address`** (`FIRST_PRESET_NAME + i * stride`, donde el
  stride se escribe como `Address(0x00, 0x01, 0x00, 0x00).value` para no dejar un 16384
  mágico) en vez de escribir las 8 a mano, más `presetName(1..8)`. Botón «Leer nombres de
  presets» que las recorre en secuencia. 5 tests nuevos (37 en total, todos pasan).

- **2026-09-02 — ✅ Lectura de los 8 nombres de preset confirmada contra el amplificador.**
  Los 8 GET devolvieron respuesta con **checksum válido en todos**. Los 3 primeros presets
  traían nombres modificados —de botones pulsados directamente en el amplificador en pruebas
  anteriores— y los 5 restantes el nombre de fábrica `"KATANA Mk2"`.
  - Lo interesante es justo eso: **la app lee el estado real del hardware, no un valor
    cacheado ni por defecto**. Que los nombres editados a mano aparezcan y los intactos no,
    es la mejor confirmación posible de que las 8 direcciones generadas con la aritmética de
    `Address` apuntan cada una a su preset.

- **2026-09-02** — Bucle de lectura continuo: la pantalla de diagnóstico pasa a ser un
  **monitor en vivo**. `protocol/SysExFramer` (Kotlin puro, 9 tests) reensambla mensajes
  `F0…F7` desde trozos arbitrarios; `KatanaUsbTransport.incomingMessages()` expone un
  `Flow<ByteArray>` que corre en `Dispatchers.IO` con timeout de 100 ms y trata `-1` como
  "sin datos, seguir escuchando". Los botones **solo envían**; las respuestas llegan por el
  flow, igual que cualquier mensaje espontáneo del amplificador. `KatanaUsbScanner` atiende
  `USB_DEVICE_ATTACHED` / `DETACHED`, así que conectar el amp abre la conexión solo y
  desconectarlo la cierra. 46 tests en total, todos pasan; instalado en el celular.

- **2026-09-02** — Corregida la condición de carrera al leer varias direcciones seguidas.
  Con las respuestas llegando de forma asíncrona, enviar los 8 GET de presets sin esperar
  correlaba mal si el amp contestaba desordenado. Ahora `protocol/RolandExchange.kt`
  (`awaitRolandReply`, Kotlin puro) empareja cada petición con el primer mensaje Roland cuya
  dirección coincide, con timeout de 800 ms, y la secuencia sigue con la siguiente dirección
  aunque una no conteste. Lo usan «Leer nombre del dispositivo» y «Leer nombres de presets»;
  «Identity Request» y «Handshake» siguen igual, que son peticiones sueltas.
  6 tests nuevos (52 en total, todos pasan).

- **2026-09-02** — Edit Mode aislado: `KatanaAddresses.EDIT_MODE` (`7F 00 00 01`) con
  `EDIT_MODE_ON` / `OFF`, y botones «Edit Mode ON» / «OFF» en la pantalla. Como un SET no se
  confirma (ver nota abajo), no se correlaciona por dirección: se usa `sendAndCollect`, que
  envía y observa el stream 1,5 s registrando lo que llegue, o dice explícitamente que no
  llegó nada. El botón OFF existe para no dejar el amp en un estado distinto tras las
  pruebas. 4 tests nuevos (56 en total, todos pasan).

- **2026-09-02 — ✅ HIPÓTESIS CONFIRMADA: Edit Mode es lo que habilita los reportes
  espontáneos.** Probado en el amplificador real, en las dos direcciones:
  - Con Edit Mode **desactivado**, mover una perilla física del Katana no genera ningún
    mensaje en `incomingMessages()`.
  - Con Edit Mode **activado** (SET a `7F 00 00 01` con dato `0x01`), mover una perilla
    física **sí** genera mensajes espontáneos, y el monitor en vivo los registra bien.

  Queda cerrado el propósito de Edit Mode y **separado definitivamente del handshake**: son
  cosas distintas, y el silencio del handshake ya no tiene nada que ver con esto. También
  confirma lo que `HOW.md` y `controller.py` predecían antes de la prueba: el SET es
  fire-and-forget y no devuelve confirmación.

- **2026-09-02** — Lectura del dump de memoria: `KatanaAddresses.MEMORY_DUMP`
  (`60 00 00 00`, rango 1920) y botón «Leer dump de memoria», que envía el GET y recoge la
  respuesta durante 3 s, contando trozos, bytes de datos, checksums válidos e inválidos, y
  mostrando los primeros 16 bytes como el nombre del preset activo. Test nuevo del framer
  con un mensaje real de 1934 bytes troceado en lecturas de 512 (57 tests, todos pasan).

- **2026-09-02 — ✅ Dump de memoria confirmado contra el amplificador. Cierra el bloque de
  validación de transporte + protocolo.** Resultado: **8 mensajes, los 8 con checksum
  válido**, 1972 bytes en el cable y **1860 de datos** — cuadra exacto:
  1972 − 8×14 de overhead por mensaje = 1860. El nombre de preset del primer trozo
  **coincidió con el canal activo** en el amplificador en ese momento.
  - Con esto queda validada de punta a punta la pila completa contra hardware real:
    Identity Request → nombre del dispositivo → nombres de los 8 presets → edit mode →
    dump de memoria. Transporte, empaquetado, framing, direcciones y checksum, todo.

- **2026-09-02** — Primer control interactivo real y arranque de `device/`:
  `KatanaLink` (puerto estrecho para poder testear con un falso), `KatanaRepository`
  (`StateFlow<Int?>` del reverb level, lectura por GET, escritura optimista y suscripción a
  los mensajes espontáneos) y un `Slider` en la pantalla. **9 tests nuevos** contra un
  `FakeLink`, sin `UsbManager` (66 en total, todos pasan).

- **2026-09-02 — ✅ PRIMER CONTROL INTERACTIVO REAL, confirmado por OÍDO.** Mover el slider
  de la app **cambia el sonido del amplificador**. No es una confirmación por checksum ni por
  coincidencia de bytes: es audio real. Y los mensajes espontáneos al girar la perilla física
  llegan por esa misma dirección y mueven el slider solo, sin tocarlo.
  - **Dirección definitiva del nivel de reverb en el Mk2: `60 00 06 5B`, rango 0..100**, de
    **lectura y escritura a la vez** — no es "la de reporte" de un par.
  - Costó **tres candidatas** y solo el audio las distinguió. El proceso queda escrito en
    CLAUDE.md §5 para no repetirlo a ciegas con los cinco efectos que faltan.

## En progreso

- **Probar la auto-conexión del monitor** — lo demás del monitor ya está confirmado (el
  flow entrega respuestas y mensajes espontáneos). **Queda por comprobar: que al enchufar el
  amp la app se conecte sola, sin pulsar «Buscar dispositivo».**

## Por hacer

Orden de prioridad:

1. **Bucle de lectura continuo** — hoy `receiveRaw()` se llama una sola vez justo después de
   enviar. Falta el bucle en `Dispatchers.IO` que alimente un `Flow<ByteArray>`, y atender
   `USB_DEVICE_ATTACHED` / `USB_DEVICE_DETACHED` para conectar y soltar solo.
2. **`SysExFramer`** — reensamblar mensajes `F0…F7` desde trozos arbitrarios de bytes. Hoy
   cada lectura se asume un mensaje completo; con el dump de memoria (1920 bytes en varios
   mensajes) deja de ser cierto.
3. **Resto de mensajes SysEx del Katana** — implementar contra
   [reference/katana-midi-bridge/doc/katana_sysex.txt](reference/katana-midi-bridge/doc/katana_sysex.txt),
   en este orden (el nombre del dispositivo ya está hecho):
   - Nombres de los 8 presets (`10 01 00 00` … `10 08 00 00`).
   - Edit mode (`7F 00 00 01`).
   - Dump de memoria completo (`60 00 00 00`, tamaño `00 00 0F 00`) y reensamblado multi-mensaje.
   - Parámetros individuales por bloque (Amplifier Common, Boost/Mod, Delay/FX, Reverb,
     Color Button Management), según las tablas del mismo doc.

## Notas y decisiones técnicas

- **2026-09-01 — El Katana MK2 NO es un dispositivo USB MIDI class-compliant.** Confirmado
  con `lsusb -v` sobre el amplificador real, después de que la app no detectara nada: el
  dispositivo entero se enumera con `bDeviceClass 255` (Vendor Specific) y ninguna de sus 4
  interfaces usa las clases Audio / MIDIStreaming estándar. Por tanto **`MidiManager` nunca
  lo detectará**, en ningún Android ni con ningún kernel. Transporte real: **interfaz 3**
  (`bInterfaceClass 255`, `bInterfaceSubClass 3`, `bInterfaceProtocol 0`), **alternate
  setting 0**, endpoint **`0x03` Bulk OUT** para enviar y **`0x84` Bulk IN** para recibir,
  `wMaxPacketSize` **512 bytes**. Las interfaces 1 y 2 son de audio isócrono (44100 Hz) y no
  intervienen; la interfaz 0 no tiene endpoints de datos. Detalle completo en CLAUDE.md §4.1.
  - **Consecuencia**: la arquitectura pasa de `android.media.midi` a
    `android.hardware.usb.UsbManager` + `claimInterface(3)` + `bulkTransfer()`. El paquete
    `midi/` queda como implementación descartada; el protocolo SysEx de §5 no cambia.
  - **Por qué TuxKatana y katana-midi-bridge sí funcionan**: en Linux `snd-usb-audio` trae
    quirks para Roland/Boss que exponen esas interfaces vendor-specific como puertos ALSA
    MIDI. Android no hace esa traducción. Las referencias siguen valiendo para el protocolo,
    no para el transporte.
  - ~~Pendiente de confirmar: formato en el cable.~~ **RESUELTO el 2026-09-02**, ver abajo.
- **2026-09-02 — RESUELTO: el formato en el cable son paquetes USB-MIDI de 4 bytes.**
  Fuente: [MrHaroldA/MS3](https://github.com/MrHaroldA/MS3) (Arduino + USB Host Shield,
  controla el Boss MS-3 y el Katana), fichero `MS3.h`: el manejo de paquetes en `receive()`
  y la función `setEditorMode()`. Aunque la interfaz sea vendor-specific, los datos sobre
  `0x03` / `0x84` van empaquetados en el formato USB-MIDI Class estándar:
  `byte 0 = (cable << 4) | CIN`, `bytes 1-3` = hasta 3 bytes MIDI reales rellenados con
  `0x00`. Un SysEx se trocea de 3 en 3, con CIN `0x4` para los paquetes intermedios
  ("empieza/continúa") y `0x5` / `0x6` / `0x7` para el último según lleve 1, 2 o 3 bytes.
  Es capa de transporte: `protocol/` sigue viendo mensajes `F0…F7` limpios.
- **2026-09-02 — Handshake obligatorio antes de cualquier comando.** Misma fuente
  (`setEditorMode()`): el amplificador no responde a nada hasta recibir **dos veces
  seguidas**, con ~4 ms de delay entre medio, la trama fija
  `F0 7E 00 06 02 41 3B 03 00 00 00 00 00 00 F7`. No es el Identity Request universal
  (`F0 7E 7F 06 01 F7`), que probamos como primer contacto sin éxito; ese sigue valiendo,
  pero *después* del handshake.
  - ⚠️ **A verificar**: la trama tiene forma de Identity *Reply* y `3B` es el model id del
    MS-3; el del Katana es `33`. Si el handshake literal no funciona, probar con `33`.
- **2026-09-02 — kshoji/USB-MIDI-Driver: sigue pendiente de decidir, pero ya hay datos.**
  El dato que bloqueaba la decisión (formato en el cable) está resuelto. Recomendación:
  **no usarla** — empaquetar/desempaquetar 4 bytes son unas decenas de líneas, y el
  handshake es específico de Boss, así que ninguna librería genérica lo cubre. Falta que se
  confirme explícitamente, como pide CLAUDE.md §6.
- **Decisión pendiente**: usar o no [kshoji/USB-MIDI-Driver](https://github.com/kshoji/USB-MIDI-Driver).
  La vieja regla "sin librerías MIDI de terceros" se apoyaba en que `android.media.midi`
  cubría el caso, y ya no lo cubre, así que hay que decidirla de nuevo a propósito en vez de
  heredarla. Pros y contras en CLAUDE.md §6; mientras tanto se implementa a mano.
- **2026-09-02 — Bug corregido: el log decía "interfaces: 7" y el descriptor dice 4.**
  Confirmada la hipótesis: `UsbDevice.getInterfaceCount()` **no** devuelve `bNumInterfaces`,
  sino un `UsbInterface` por cada par (interfaz, alternate setting). Contrastado contra
  `~/katana_usb_descriptor.txt`, que tiene `bNumInterfaces 4` y 7 descriptores de interfaz:
  iface 0 (1 alt), iface 1 (2 alts), iface 2 (2 alts), iface 3 (2 alts) = 7. El conteo real
  se obtiene quedándose con los `id` distintos; el log ahora muestra ambos números
  (`interfaces: 4 (7 alt settings)`), que para diagnóstico es más útil que solo el correcto.
  - Efecto colateral útil: el descriptor confirma que **la interfaz 3 alt 1 expone los
    mismos endpoints como interrupt y con IN en `0x85`, no `0x84`**. `KatanaUsbTransport`
    ya filtraba por `id == 3 && alternateSetting == 0`, así que estaba bien; pero filtrar
    solo por `id` habría cogido a veces el alt 1 y `findEndpoint(0x84)` habría fallado.
  - También matiza lo que decíamos de las interfaces 1 y 2: son isócronas (audio, 112 bytes
    por paquete) pero **vendor-specific**, no audio class — coherente con que ninguna
    interfaz del dispositivo use clases estándar.
- **2026-09-02 — Duda abierta (baja prioridad, NO bloqueante): el handshake no responde.**
  Con model id `0x33`, los dos envíos y la lectura posterior de 1 s no devolvieron nada. No
  es fallo del transporte: en la misma sesión el Identity Request sí respondió. Hipótesis:
  el handshake **activa el modo de edición en el amp sin confirmar por SysEx**, a diferencia
  del Identity Request, que es una consulta con respuesta esperada. Sólo se puede comprobar
  de forma indirecta, viendo si las lecturas de parámetros cambian de comportamiento con y
  sin él — así que se retoma cuando haya lecturas reales (punto 1 de "Por hacer").
  - No se llegó a probar `0x3B` (MS-3), porque `0x33` no falló de forma que lo justificara:
    no hubo error, simplemente silencio, que es lo que la hipótesis predice.
  - Pista para más adelante: nuestra trama difiere del Identity Reply real del amp en **un
    solo byte**, el de versión de firmware (`00` donde el amp dice `06`). Ver CLAUDE.md §4.1.
- **2026-09-02 — ⚠️ Verificar en la próxima ejecución: inconsistencia en el log transcrito.**
  En la transcripción de la prueba, las líneas `wire` y `desempaquetado` del Identity Reply
  no son coherentes entre sí. El wire transcrito era
  `04 F0 7E 00 | 04 06 02 41 | 04 33 03 00 | 00 06 00 00 | 07 00 00 F7`: su cuarto paquete
  empieza por `00`, o sea CIN `0x0`, que no lleva payload, así que `unpackUsbMidi` habría
  producido 12 bytes (`F0 7E 00 06 02 41 33 03 00 00 00 F7`) y no los 15 que muestra la
  línea de desempaquetado. Para dar esos 15 bytes, ese paquete tendría que ser `04 00 06 00`.
  - Lo más probable con diferencia es un desliz al copiar el log a mano: la app calcula una
    línea a partir de la otra en el mismo código, así que una ejecución real no puede
    producirlas incoherentes.
  - Pero **si el amplificador realmente enviara paquetes con CIN `0x0`**, `unpackUsbMidi`
    estaría descartando bytes en silencio, y eso sí sería un bug. Confirmar mirando el log
    en pantalla la próxima vez que se envíe un Identity Request.
  - No afecta a la conclusión: la respuesta de 15 bytes coincide exactamente con el Identity
    Reply documentado en HOW.md, así que el hito se sostiene igual.
- **2026-09-02 — Un SET (`0x12`) no se confirma: es fire-and-forget.** Comprobado en dos
  fuentes independientes de `reference/`, no supuesto:
  - `TuxKatana/HOW.md` traza el write de edit mode con solo la flecha de envío (`◀`), sin la
    de respuesta (`▶`) que sí llevan todos los GET de esa misma secuencia.
  - `TuxKatana/lib/controller.py`: `set_edit_mode()` envía y sigue, mientras que `get_name()`
    y `get_presets()` llaman a `wait_msg()`.

  Por eso el Edit Mode **no** usa `awaitRolandReply` (no hay nada que correlacionar por
  dirección) sino `sendAndCollect`, que solo observa. Silencio tras el SET es el resultado
  esperado, no un fallo.
- **2026-09-02 — Edit Mode va en el flujo normal de conexión, no solo en diagnóstico.**
  Confirmado que es lo único que hace que el amp reporte cambios derivados (perillas
  físicas, parámetros que se mueven solos), así que sin activarlo la UI final nunca podría
  reflejar lo que se toca en el propio amplificador. Se activará al conectar, respetando lo
  que ya pedía CLAUDE.md §4.2: ajuste **explícito y visible**, con forma de desactivarlo,
  nunca silencioso — altera el estado del amp.
- **2026-09-02 — Dos hipótesis descartadas para el nivel de reverb, ambas por prueba con el
  amplificador, no por lectura de documentación:**
  - `60 00 06 18` (mapa de `katana_sysex.txt`, que documenta el **MK1** de 2017): **ningún**
    efecto en el Mk2, ni audible ni en estado interno; el GET devolvía un `07` fijo que
    tampoco se movía al girar la perilla. Queda como `REVERB_LEVEL_MK1`, `@Deprecated`.
  - `60 00 05 48` (`re_effect_lvl` de `reverb.yaml:17`, en la sección `SEND` de una fuente
    **de Mk2**): escribir ahí **tampoco hace nada** — tras mandar 100 repetidamente, un GET a
    `60 00 06 5B` seguía en 0, y sin cambio audible. Sí reporta por su cuenta al mover la
    perilla, pero con un valor relacionado y **retardado** respecto al real (desfase de ~15).
    Hipótesis: es el nivel de efecto ya aplicado, con inercia — algo que el amp **deriva**,
    no un punto de control. Queda como `REVERB_LEVEL_DERIVED`, `@Deprecated`.
- **2026-09-02 — No dar por bueno el patrón estructural sin probarlo.** `Adresses.txt`
  empareja cada efecto con una dirección baja y una del bloque `60 00 06 5x`, lo que sugería
  un patrón "escritura baja / reporte alto". **Para reverb resultó al revés**: la alta es la
  de control, y la baja ni siquiera acepta escritura. Los cinco efectos restantes —Presence
  `60 00 00 27`/`06 56`, Boost `60 00 00 12`/`06 57`, Mod `60 00 02 38`/`06 58`, FX
  `60 00 04 14`/`06 59`, Delay `60 00 05 06`/`06 5A`— hay que probarlos **uno a uno con
  audio**. Las direcciones bajas siguen siendo candidatas legítimas a probar primero, por
  estar citadas como `SEND`, pero nada más que eso.
- **2026-09-02 — `60 00 12 14` NO es un nivel de reverb.** El vector de checksum de
  `katana_sysex.txt` (`60 00 12 14 --> 01`, checksum `79`) viene anotado como "set Katana
  Reverb type 'red'", lo que sugería un parámetro de reverb. Al buscarlo en el documento
  resulta estar en **Color Button Management → `[Select Active Color]`**: elige qué botón de
  color está activo para el reverb, con valores `00` verde / `01` rojo / `02` amarillo. Es
  un enum de 3 valores, no un continuo, así que un slider no le pega.
  - El parámetro continuo correcto es **`60 00 06 18` = Reverb Level, `0..100`**, confirmado
    en dos fuentes: `katana_sysex.txt` (Reverb → `[Parameters]` → "Level (0..100)") y
    `simple_dsp.json` (`reverb.effectLevel`, offset 8 sobre `baseAddr [96,0,6,16]`,
    `byteRange [0,100]`). Es el que usa el slider.
  - Ambas direcciones quedan en `KatanaAddresses` y la del color lleva el aviso, para que el
    vector de test no se vuelva a confundir con un nivel.
- **2026-09-02 — Escritura optimista, y sin bucle de eco.** Un SET no se confirma, así que
  esperar confirmación solo haría que el slider fuera por detrás del dedo: la caché se
  actualiza antes de enviar. Y la regla anti-eco vive en `KatanaRepository`, la única clase
  que escribe: lo que llega del amp actualiza la caché y **nunca** se reenvía. Hay un test
  que lo fija.
- **2026-09-02 — El tamaño del dump depende del estado del amp, no es una constante.** La
  prueba real dio **8 mensajes / 1860 bytes de datos**, mientras que el ejemplo de
  `HOW.md` documenta 6 / 1344. **No es una discrepancia**: confirma que el dump devuelve
  solo los rangos de parámetros efectivamente ocupados en el estado actual del
  amplificador, y que el 1920 del GET es un **techo de rango, no una cantidad fija** — justo
  lo que ya decía katana_sysex.txt. Ningún código debe asumir un número fijo de mensajes ni
  de bytes.
- **2026-09-02 — El dump NO llega en un solo mensaje, así que no se puede correlacionar por
  dirección.** `awaitRolandReply` empareja *un* mensaje con *una* dirección; pero
  `reference/TuxKatana/HOW.md` traza la respuesta al dump como **6 mensajes** (5 de 241 bytes
  de datos y uno final de 139), cada uno con su propia dirección (`60 00 00 00`,
  `60 00 01 71`, `60 00 03 62`, …). Usar `awaitRolandReply` habría devuelto en silencio solo
  el primer trozo. Por eso el dump usa `sendAndCollect` con ventana de 3 s y reporta el
  conjunto.
  - Relacionado: los 1920 del GET son un **límite de rango, no un número de bytes**. El amp
    salta las zonas sin parámetros definidos, así que vuelven menos (katana_sysex.txt lo dice
    explícitamente). No hay que tratar "llegaron menos de 1920" como un error.
  - `SysExFramer.DEFAULT_MAX_MESSAGE_SIZE` = 4096: sobra incluso para el peor caso
    (1920 + 14 de overhead = 1934), así que no hizo falta tocarlo.
- **Un solo lector del endpoint, siempre.** `KatanaUsbTransport.incomingMessages()` es un
  flow *frío*: cada colector arrancaría su propio bucle de `bulkTransfer` y se robarían
  mensajes entre sí. El ViewModel lo colecta **una vez** y reparte por un `MutableSharedFlow`;
  el log y las peticiones que esperan respuesta se suscriben a ese. Por lo mismo,
  `sendHandshake()` ya no lee.
- **`awaitRolandReply` se suscribe con `CoroutineStart.UNDISPATCHED` antes de enviar.** Si se
  suscribiera después, una respuesta rápida se perdería. No es teórico: quitando ese flag,
  5 de los 6 tests de `RolandExchangeTest` fallan.
- **El empaquetado USB-MIDI vive en `usb/`, no en `protocol/`.** Es formato de cable, no de
  protocolo: `protocol/` seguirá viendo solo mensajes `F0…F7` limpios. Aun así `packUsbMidi`
  / `unpackUsbMidi` son Kotlin puro y tienen tests JVM, porque un bug ahí haría ilegible
  cualquier prueba con el amplificador.
- **`sendHandshake()` es `suspend`, a diferencia del resto del transporte.** Los ~4 ms entre
  los dos envíos son parte del protocolo y `delay` los respeta sin bloquear un hilo. Además,
  la lectura intermedia usa un timeout muy corto (5 ms) a propósito: leer con el timeout
  normal de 1 s entre los dos envíos convertiría el hueco de 4 ms en uno de más de un
  segundo, que es justo lo que el handshake no admite. El precio es que una respuesta lenta
  al primer envío no se ve ahí, sino en la lectura posterior — que el ViewModel hace y
  registra como "respuesta tardía".
- `protocol/` debe quedar libre de imports de `android.*` para poder testear en JVM puro.
  `SysExFramer` se mueve ahí: ya no depende de la API MIDI, solo trocea un flujo de bytes.
- **Pendiente en el manifest**: quitar `<uses-feature android:name="android.software.midi">`
  (ya no aplica) y reconsiderar si `android.hardware.usb.host` debe pasar a `required="true"`,
  ahora que USB host es el único transporte posible.
- En `device_filter.xml`, `vendor-id` y `product-id` van en **decimal**, no en hexadecimal.
  Roland = `1410` (0x0582). Con el fichero vacío ningún dispositivo hace match, así que el
  intent-filter `USB_DEVICE_ATTACHED` no dispara hasta que se rellene. Ahora es la vía
  natural para lanzar la app al conectar el amplificador.
- `UsbManager.requestPermission()` abre un diálogo del sistema y responde por `PendingIntent`:
  es un paso asíncrono que con el framework MIDI no existía. `bulkTransfer()` bloquea, así
  que va en `Dispatchers.IO`; devuelve `-1` en timeout, que en el bucle de lectura es normal
  y no significa desconexión.
- El texto de las líneas de log **no** va en `strings.xml`: son diagnósticos técnicos
  generados por la capa MIDI, no copy de UI. Lo que sí está en `strings.xml` es el chrome de
  la pantalla (título, botón, estado).
- `DebugConnectionViewModel` es un `AndroidViewModel` para que rotar la pantalla no vuelva a
  abrir los puertos MIDI; se cierran en `onCleared`.
- **Entorno**: Android Studio está instalado como Flatpak, así que corre en su propio mount
  namespace con un `/tmp` privado. Si existe un daemon de Gradle iniciado desde el host (por
  la extensión Gradle de VS Code, o por `./gradlew` en una terminal), Studio lo reutiliza a
  través de `~/.gradle/daemon/registry.bin` (el home sí es compartido) y falla el sync con
  `The specified initialization script '/tmp/ijMapper1.gradle' does not exist`, porque el
  daemon del host no ve el `/tmp` del sandbox. Solución: `./gradlew --stop` y volver a
  sincronizar, o separar los `GRADLE_USER_HOME` de host y Studio.
