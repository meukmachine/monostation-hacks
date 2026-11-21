# Mod settings

A design (2026-11-21) for one store of settings, tweaks and feature flags shared by every
mod, set from the app (`flasher/`, Firmware settings tab in `docs/app-ui.md`). The first user is the
configurable drive amount (#61). Status: firmware side built (`overlays/settings`,
`tools/modsettings.py`), tested on the host; app side built (`flasher/` `ModSettings.kt`,
Firmware settings tab, CLI `--settings`). On the unit (751, 2026-11-21): reading,
live sliders (each Distortion slider changes only its own panel type), the CLI, and
Save from the page with the values back after a power cycle.

## Decisions (2026-11-21)

- **One store for all mods**, not one per feature. A setting that isn't in the store means
  stock behaviour (a flag means off). Every mod is inert at its defaults.
- **The app writes it, through the bootloader**, with the upload the pack sender uses
  (`docs/session-transport.md` *Upload*). The firmware only reads it, once at boot. It never
  writes external flash for this. Writing from the firmware comes later, if a setting has to
  change from the panel.
- **Live changes through SysEx, in RAM.** Sound tweaks such as the drive sliders are heard
  while they move; Save in the app then writes the store.
- **The firmware describes its own settings.** The page asks the build which settings it
  has and draws the form, so it needs no per-build schema and test builds just work.
- **Settings take a few predefined kinds** (below), so a description is small and the page
  has one control per kind.

## Kinds

| kind | value | described by | control on the page |
|------|-------|--------------|---------------------|
| `flag` | 0 or 1 | default | checkbox (on/off) |
| `number` | signed 16-bit | min, max, default | number field |
| `slider` | signed 16-bit | min, max, default | slider with its value |
| `enum` | index 0…n-1 | labels, default | radio buttons or drop-down |
| `text` | ASCII, up to n bytes | max length, default | text field |

Each setting also has: an **id** (stable, never reused), a **name** (shown on the page,
ASCII, up to 24 characters), a **group** (the mod's name, for headings) and **when** it takes
effect: `live` (at once, from the SysEx set) or `boot` (at the next power-on; the page says
so).

## Declaring a setting

An overlay declares its settings in its `overlay.toml`:

```toml
[[setting]]
id = 0x0101
name = "Overdrive amount"
kind = "slider"
min = 0          # 0 = stock
max = 100
default = 0
when = "live"    # the default
```

Keys per kind: `flag` default (0/1); `number`, `slider` min, max, default (signed 16-bit);
`enum` labels (2 to 16, each up to 24 characters), default (an index); `text` max (1 to 32
characters), default (a string). The group is the overlay's directory name.

`fwsrc.py generate` collects the settings of every overlay in the build, checks that the
ids and header names are unique, and fails when an overlay declares settings but
`overlays/settings` isn't in the build. It writes the description table
(`zz_overlay__settings_table.c`, const data after the image) and `mod_settings.h` (one
`SETTING_<NAME>` id per setting, `MOD_SETTING_COUNT`, …) into the generated tree. An
overlay includes `<mod_settings.h>` and reads a value with `mod_setting(id)` (text:
`mod_setting_text(id)`), which returns the stored value clamped to the setting's range, or
its default. `python3 tools/modsettings.py list overlays/drive` prints an overlay's
settings.

## Firmware: `overlays/settings`

- Replaces `ext_flash_read` and `sysex_dispatch`. Only `app_construct` reads sector 1
  offset 0 (the System Settings record, `0x0800E41E`), once at boot with the NOR driver
  idle; right after that read the overlay reads the store (8 header bytes, then the
  entries and CRC) with the stock blocking read. Every other read passes through.
- RAM in SRAM3 (`docs/hardware.md` *Free RAM*): the store is read into `0x20024000`
  (4 KB), replies are built at `0x20025000` (512 bytes), the values live at `0x20025200`
  with a magic word tied to the build's table address. Until the store is read (or after a
  reset into another build) `mod_setting` returns defaults.
- `tests/test_modsettings.py` compiles `settings.c` for the host (`tests/settings_host/`,
  the SRAM3 addresses mapped with `mmap`) and checks the store reading and every SysEx op. Ids are listed in *Ids* below, so two mods never take the same one; the high byte
is the mod, the low byte the setting.

## Store in external flash

One 4 KB subsector at **`0x7FF000`**, the last one of the 8 MiB chip. Everything from
`0x2E8000` up is unused (all `FF`, `docs/session-transport.md` *Storage map*); the far end
keeps clear of the sessions.

| offset | content |
|--------|---------|
| `0x00` | magic `MODS` |
| `0x04` | format version, 1 |
| `0x05` | 0 |
| `0x06` | length of the entries, u16 LE |
| `0x08` | entries: `id` u16 LE, `length` u8, value (`length` bytes; numbers u16/s16 LE) |
| after | zlib CRC-32 (LE) over bytes `0x00` up to the CRC |

The app always writes the whole subsector, `FF` after the CRC. A bad magic, version or CRC
(an erased chip, an interrupted write) means: no settings, everything stock. A build skips
ids it doesn't know and clamps values to its own ranges, so one store works across releases,
test builds and stock firmware (which never reads it).

## SysEx `0x7C`

`F0 00 20 29 01 61 7C <op> … F7`, in the Mono Station family (`docs/sysex-handlers.md`).
The stock dispatcher doesn't use byte 6 `0x7C`: the earlier drive tuning message took it,
and `overlays/drive` dropped that message. `0x7D` was the removed `overlays/ledtest`. The reply goes to
the port the request came from.

Encoding: a number of n nibbles is 4-bit values, most significant first (16-bit values as
4 nibbles, two's complement for signed ones); a single nibble is one byte; a string is
its length (2 nibbles) and one byte per character. Replies are at most 512 bytes.

| op | request | reply |
|----|---------|-------|
| `0x00` describe | index (2) | `7C 00` index (2), count (2), id (4), kind (1), when (1), group (2), the kind's fields, name (string); past the end `7C 00 7F` |
| `0x01` get | id (4) | `7C 01` id (4), status (1: 0 ok, 1 unknown id), value (none for an unknown id) |
| `0x02` set | id (4), value | `7C 02` id (4), status (1): 0 ok, 1 unknown id, 2 out of range (nothing changes), 3 stored, takes effect at the next boot |
| `0x03` build | none | `7C 03` format version (2, now 1), count (2), store (1: 0 none, 1 read, 2 damaged), group count (2), group names (strings) |

Kinds: 0 flag, 1 number, 2 slider, 3 enum, 4 text; when: 0 live, 1 boot. The kind's fields:
flag default (1); number and slider min (4), max (4), default (4); enum label count (2),
default (2), labels (strings); text max length (2), default (string). Values: 4 nibbles,
text a string. Ops 0 and 3 work before the store is read; a set before then answers 2.

## Ids

| id | mod | setting | issue |
|----|-----|---------|-------|
| `0x01xx` | drive | `0x0101` Overdrive amount, `0x0102`/`0x0103`/`0x0104` Distortion 1/2/3 amount (one per panel type; `0x0102` was one Distortion amount for all types in earlier 751 test builds): sliders 0–100, 0 = stock (default), 50 = the fixed boost of builds up to 751, 100 = twice that push. By ear on the unit (2026-11-21) every distortion type is at its most at about 50; the range up to 100 stays, so nobody is short of drive | #61 |

## In the app

`flasher/src/commonMain/kotlin/flasher/ModSettings.kt` ports the wire and store codecs
(`ModSettingsTest` checks them against `tools/modsettings.py` and the replies of the
host-compiled `settings.c`).

- The Firmware settings tab has *Read from unit*: it asks the running build `7C 03`; stock and older
  mod builds don't answer within 1 s, and the tab says the firmware has no settings.
  Otherwise it reads every description (`7C 00`) and value (`7C 01`), groups the controls
  by mod (one control per kind) and sends `7C 02` as a control changes; while a slider
  drags, only the latest value waits for the previous reply.
- **Save** reboots into the bootloader, dumps the store, writes the new one, reads it back
  and leaves the bootloader (a few seconds), then reads the settings again. The new store
  keeps the entries of ids this build doesn't know (another build's settings) and leaves
  out settings at their default, so those follow the default of whatever build runs.
  Unchanged stores aren't written. **Reset to stock** writes an empty store.
- Flash, Back up and Send close the settings connection and clear the form first.
- Read and Save ask for MIDI access each time (Firefox keeps dead ports after a restart of
  the unit). When something else restarts the unit (the CLI), Firefox on Linux may lose its
  ALSA connection to the port for good (`aconnect -l`: the WebMIDI clients connect to
  nothing): the page reports that the unit doesn't answer, and only restarting Firefox
  helps (2026-11-21).
- **Backup:** the backup reads the store and, when it holds one, adds it to the
  `.monostationpack` as `mod-settings.bin` (the 4 KB subsector). Components reads only
  `patches.syx` and `sessions.syx` by name (`loadV1Pack`; `is-v1-pack` wants either), so
  it still opens the pack. Send pack offers the backup's settings with a tick box; the CLI
  sends them unless `--only patches|sessions` (`--only settings` sends just them).
- CLI: `flasher --settings` lists settings and values, `--set ID=VALUE` (repeatable; ID
  `0x0101` or the name) sets one live, `--save` stores them, `--reset-settings` writes an
  empty store.

## Open

- **Size limits:** how long a description can be in one SysEx (enum labels, text
  defaults).
- **Firmware writes:** only once a setting has to change from the panel; then through the
  async NOR driver, with care around patch and session saves.
