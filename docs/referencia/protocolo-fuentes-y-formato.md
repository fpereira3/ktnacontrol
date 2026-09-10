> Archivado desde CLAUDE.md — §5 "Dónde está documentado el protocolo SysEx" (cabecera, tabla de fuentes y resumen operativo)
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

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

