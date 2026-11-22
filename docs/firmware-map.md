# Firmware subsystem map

A pointer map into the decoded application image (`private/firmware/monostation-firmware-800.bin`, load base `0x0800C000`). It keeps what the static scan finds and points elsewhere for the rest.

`python3 tools/fwmap.py private/firmware/monostation-firmware-800.bin [xref_addr]` reproduces the scan: the vector table, word-aligned peripheral-register literals, strings, and the literal-pool cross-references to them (or to `xref_addr`). A string or peripheral literal is found through the pool word that holds its address; the function owning that pool word is the user. Function boundaries and names are in `docs/source-map.md`.

## Where the details are

- Hardware, peripherals, timers, DMA streams and interrupt handlers: `docs/inventory.md` §1.5, and the `stm32f4xx_it` table in `docs/source-map.md` *Runtime and libraries*.
- Memory map, MCU, free RAM: `docs/hardware.md`.
- Every function by address, grouped by source file: `docs/source-map.md`.
- Sequencer, note routing, recording, MIDI clock and transport: `docs/sequencer.md`.
- SysEx dispatcher `sysex_dispatch` and its commands: `docs/sysex-handlers.md`.
- Patch store `patch_store_param` and session initialiser `session_init`: `docs/sysex-spec.md`, `docs/session-format.md`.
- Every controllable piece of hardware and every firmware feature, with status: `docs/inventory.md`.

## UI input

The panel arrives as one id stream; the real dispatcher is `control_dispatch` (`0x0801E310`, `docs/inventory.md` §1.1). The pads are ADC pressure sensors; only the buttons use the GPIO matrix. Five log callbacks print each event kind:

| Function | String |
|----------|--------|
| `input_log_slider` (`0x08017910`) | `0x0802816B` `Slider %d moved to %d` |
| `input_log_encoder` (`0x08017934`) | `0x08028182` `Continuous pot %d moved with delta %d` |
| `input_log_pot` (`0x08017958`) | `0x080281A9` `Pot %d value changed to %d` |
| `input_log_button` (`0x0801797C`) | `0x080281C5` `Button %d changed state to %d` |
| `input_log_pad` (`0x080179A4`) | `0x080281E4` `Pad %d changed state to %d with velocity %d` |

Each tail-calls `printf`. The output goes nowhere: `_write` returns the length without sending anything (*Runtime libraries*). Pointing `_write` at a SysEx sender would turn these into an input-event log.

## Analogue engine

The CV targets (osc 1/2 pitch, pulse width, filter cutoff, VCA, distortion, aux CV) come from two converters: an external SPI DAC on SPI3 (`spi_dac_write` `0x0802528C` fills the buffer at `0x20000970`, `spi_dac_send` `0x08025260` sends 3 words by DMA, `DMA1_Stream2_IRQHandler` runs the chip selects) and the internal DAC through sample-and-hold multiplexers stepped by `TIM6_DAC_IRQHandler`. Details: `docs/inventory.md` §1.3.

Control signals are computed digitally at 2 kHz. `engine_tick_2khz` (`0x0801BDC4`) calls `synth_engine_update` (`0x0801D208`), which glides pitch (`osc_glide_step`), runs the envelope and sums modulation into CV values; `osc_pitch_cv_update` (`0x080252D4`) converts pitch to DAC and TIM1/TIM3 values with the unit's calibration.

**Envelope (ADSR):** digital. `synth_update_envelope` (`0x08021CA4`) turns params `0x20`–`0x23` into coefficients in a struct at `0x200006E0`: attack rate `+0xC`, decay rate `+0x10`, sustain level `+0x12`, release rate `+0x14`. `synth_engine_update` steps it: `+2` is the gate request (1 = note on → attack, 2 = note off → release, then cleared), `+1` the stage (0 attack, 1 decay, 2 sustain, 3 release), `+0x18` the 32-bit level, and `+0x1E`/`+0x20`/`+0x22` its scaled outputs. `synth_release_start` (`0x080218EC`) requests release.

Still open: the synth engine internals beyond the envelope.

## Runtime libraries

The toolchain is arm-none-eabi-gcc with newlib-nano and libstdc++. Their code fills `0x08025EAC`–`0x08026D94`, right after the startup file; newlib's assembly `memchr` sits apart at `0x0800C230` (plain `.text` links before every `.text.*`). Their constant data is at the end of `.rodata` (`0x0802AE2C`–`0x0802AEC8`), their `.data` at the end of the `.data` image (`0x0802BAA0`–`0x0802BB04`: `impure_data`, `_impure_ptr`). How the build links them from the stock toolchain: `docs/source-build.md` *Library code*. Functions per object, and the syscall stubs (`_write` drops all output, `_sbrk` stops at `0x200235B8`): `docs/source-map.md` *Runtime and libraries*.

App code calls only `operator new`, `operator new[]`, `memcpy`, `memset`, `memcmp`, `memmove` and the five input-log `printf`s, plus `__libc_init_array` from `Reset_Handler`. Everything else is pulled in by `printf`.

The 28 bytes after the `.data` image (`0x0802BB04`–`0x0802BB20`, `8 × FF` then zeros) are unexplained. `0x0802BB04` is also `_siccm`, the load address of an empty CCM `.data` section (`app_main` copies 0 bytes from it to `0x10000000`).

## Strings

- USB descriptor strings at `0x08028EA8`+ (`"AUDIO Interface"`, `"AUDIO Config"`, `"Focusrite A.E"`, `"Circuit Mono Station"`), served by the `USBD_USR_*StrDescriptor` functions (`0x08019DF0`–`0x08019E2C`). Despite the names the device is USB-MIDI only: no audio streaming.
- Default blobs: `"Classic Mono"` (`0x08028868`), `"Initial Session"` (`0x08028D70`), `"Initial Patch"` (`0x08028F6B`), `"User Session"` (`0x080293F3`); see `docs/source-map.md` *Data*.
- Byte runs that look like tables but aren't separate: the ramp at `0x08027F29` lies inside `led_breathe` (`0x08027E87`), the `@ABC…` run at `0x0802ABDC` inside `ui_colours` (`0x0802AB24`).

## Ghidra

`tools/ghidra/import_firmware.sh` imports the image, runs `MapMemory.java` (memory map + reset-handler seed) and leaves the DB in `ghidra-project/` (git-ignored). `ExportMap.java` writes the function listing to `docs/ghidra-functions.txt`; `DecompileFunc.java <addr>…` prints decompiled C. Ghidra 12.x headless has no Jython, so these are Java GhidraScripts.

Auto-analysis finds 467 functions; `MakeFunc.java` adds the vtable targets and push-prologue functions. `docs/ghidra-functions.txt` is a raw snapshot (828 `FUN_` entries); the function list of record is `src/symbols.tsv`, 826 functions, all in `docs/source-map.md`.
