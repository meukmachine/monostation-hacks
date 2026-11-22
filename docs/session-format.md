# Session image format

Layout of one 90,112-byte `.monostationsession` image. The SysEx framing that carries these images is described in `docs/session-transport.md`. Parser: `tools/session.py` (`python3 tools/session.py info <image>`).

Sources:
- the firmware's session initialiser `session_init`, which builds the struct in RAM;
- Components 1.72.5, whose session model maps only the header;
- `session_32.monostationsession` (the `INIT` image);
- the 48 distinct `USER` sessions in the two YBC soundpack dumps.

All 370 automation lanes that hold data in the YBC sessions fit the lane format below, and every session's patch matches the 136-byte patch format.

## Map

| offset | size | content |
|--------|------|---------|
| `0x00000` | 4 | magic, u32 LE: `INIT` (`0x54494E49`), `USER` or `DEMO` (Components `lQ`) |
| `0x00004` | 4 | struct size, u32 LE: always `0x15FFC` |
| `0x00008` | 4 | flags, u32 LE. Components reads bits 0–2 as the minimum firmware ("V1.2" each). The firmware writes `7`; the Components `INIT` file has `0`. |
| `0x0000C` | 1 | pad colour index (Components `binColor`). The YBC sessions use 16, 5 and 26 as group colours. |
| `0x00020` | 32 | name, space-padded |
| `0x00040` | 1 | tempo, BPM (init 120) |
| `0x00041` | 1 | swing (init 50) |
| `0x00042` | 1 | `3` everywhere seen. **swing sync rate**: third field of the tempo/swing observable (tempo, swing, this), which the session controller loads and saves with them (`session_ctl_load_globals` / `session_ctl_save_globals`). The clock copies it to its `+0x50`; it indexes the tick table `[2, 3, 4, 6, 8, 12, 16, 24]`, so the default 3 = 6 ticks = 1/16. Set in Swing Sync View (Shift + Tap). |
| `0x00043` | 9 | 0 everywhere seen |
| `0x0004C` | 3 × 4 | per-track pattern chain: byte 0 = first pattern, byte 1 = last pattern, then 2 bytes that are always 0. One group each for tracks 0, 1 and 2, at `0x4C`, `0x50` and `0x54`. |
| `0x00058` | 16 × 140 | track 0 (synth / osc 1) patterns |
| `0x00918` | 16 × 42 × 108 | track 0 automation (42 lanes per pattern) |
| `0x12498` | 8 × 140 | track 1 (osc 2) patterns |
| `0x128F8` | 8 × 12 × 108 | track 1 automation (12 lanes per pattern) |
| `0x15178` | 8 × 140 | track 2 (mod sequence) patterns, no automation |
| `0x155D8` | 2 | **scale**: `0x155D8` = scale type (field 0, written by `scale_type_view_pad_down` `0x08014734` through the table at `0x08014764`), `0x155D9` = scale root note 0–11 (field 1, `scale_root_view_pad_down` `0x08014028`). The observable is UI constructor argument 7. YBC: `0x155D8` always 0, `0x155D9` 0 except one `05` (root F). |
| `0x155DA` | 1 | **current patch slot** (0–63 from the panel; the firmware accepts < `0x80`, `docs/sysex-spec.md` *Patch store*): the controller's current-patch observable (`+0x64`), the one step byte 4 is compared against. Every YBC session whose patch is in its pack has this slot. |
| `0x155DB` | 2 | two observables, UI constructor arguments 8 and 9 (controller `+0x68` / `+0x6C`). On change the controller (`session_ctl_save_globals`) mirrors them through the `.data` pointer block at `0x20000864` (`+0xC`, `+0x10`) into two RAM flags. **`0x155DB` = paraphonic mode** → `0x20003204`: 0 = Mode 1 (only Osc 1 opens the VCA), 1 = Mode 2 (an Osc 2 note also plays Osc 1 while Osc 1 is idle, so Osc 2 notes are heard). Shift + Scales toggles it (`ui_root` button handler, button `0x10`); the Shift overlay lights Scales bright for 1, dim for 0. **`0x155DC` = Note CV/Gate source** → `0x20003205`: 0 = Osc 1 (in Mode 2 the gate also follows Osc 2), 1 = Osc 2, 2 = track 2 (Mod Seq) note value and gate. `synth_engine_update` writes that voice's pitch to the CV DAC (`spi_dac_write`) and its gate to the GATE pin; note on/off latch the source into `0x20002FA2`. The select3 view (`ui_root +0x44`, vtable `0x08027400`, pads 0–2) edits it. **Shift + Sessions** opens it: `ui_button_event` pushes view code 3 on button `0x18` with Shift held (code `0x16` → `0x0802A168[22]` = slot 3 of the overlay table at `ui_root +0xD4`, which is `+0x44`), while Sessions alone pushes code 2, the sessions view `+0x40`. The user guide documents neither the view nor any CV source but Osc 1. Loading does not clamp the value. YBC: `0x155DB` is 0 or 1, `0x155DC` 0 except one `0x22`, which is out of range (it would read past the 3-entry source table at `0x08029D10`). |
| `0x155DD` | 2 | slots 6 and 0 of the third parameter group, ids `0x4C` and `0x4D` (no patch byte), the Mod Seq track's counterparts of the oscillators' glide and octave. Init `00 40`. **`0x155DD` = id `0x4C`, track 2 glide**: `ui_construct` builds a glide view for it next to those for ids `0x09`/`0x10` (Osc 1/2 glide), and it is the track 2 step parameter (step byte 3). `osc_glide_step` slews the third voice (`0x20002608`), whose pitch is NOTE OUT source 2 (see `0x155DC`). **`0x155DE` = id `0x4D`, track 2 octave**, `0x40` = centre: an `octave_view` (Oct up/down LEDs) like those for ids `0x03`/`0x0A` (Osc 1/2 octave). Not checked on the unit. |
| `0x155DF` | 1 | zero |
| `0x155E0` | 196 | zero in stock. `overlays/sequences` (#20) keeps the pattern chain sequences here: 3 entry counts (0 = none, up to 32), a pad byte, then per track 32 entries of {first, last} pattern. Stock zeroes the region when it builds a new session (`memset` `0x97D` at `0x08018E40`) and saves and dumps it with the rest. |
| `0x156A4` | 0x8B8 | zero |
| `0x15F5C` | 126 | the session's patch: bytes 9–134 of the 136-byte bank format (name, category, genre, params; `docs/sysex-spec.md`) |
| `0x15FDA` | 0x22 | zero in files. The firmware fills this region with `0xFF` in RAM. |
| `0x15FFC` | 4 | `FF FF FF FF`, past the struct |

The firmware's initialiser reads its defaults from these templates:
- magic and size: literal pool `0x08018E80`;
- name `Initial Session`: `0x08028D70`;
- globals `78 32 03 00 …` and pattern counts `10 08 08`: `0x08026D98`;
- default step range `0F` (steps 0–15): `0x08029E9C`;
- `Classic Mono` patch body: `0x08028868`.

**Each session holds one patch, not 64.** The patch store is a separate flash area of 128 × 512-byte slots from `0x2000` (126 bytes used per slot), of which the panel and Components use the first 64; Components calls those `MS_ALL_PATCHES` (start `0x2000`, length `0x8000`, `docs/sysex-spec.md` *Patch store*).

## Pattern (140 bytes)

The 16 steps are 8 bytes each, followed by a 12-byte footer.

The tracks, from the parameters their automation lanes drive (below): **track 0** is the synth with oscillator 1, **track 1** is oscillator 2, and **track 2** is the mod sequence. The playback routine `player_on_cell`, which Ghidra had not defined as a function, reads the steps like this:

| byte | init | meaning |
|------|------|---------|
| 0 | `00` | note number; 0 = empty step. Sent with byte 1 as a note-on. |
| 1 | `60` (tracks 0, 1), `00` (track 2) | velocity; 0 plays as 96. On track 2 this is the mod-sequence value (centred on `0x40`). |
| 2 | `00` | gate in micro-steps (1/6 step), clamped to 1–96; 6 = one step, above 6 ties into later steps. Played as `gate × ticks_per_step / 6`. |
| 3 | `FF` | value for the track's step parameter: id `0x09` (osc 1 glide, patch byte 49) on track 0, id `0x10` (osc 2 glide, byte 58) on track 1, id `0x4C` on track 2. So on the note tracks it is per-step glide. `FF` = none. All YBC sessions leave it `FF`. |
| 4 | `FF` | **patch change**: patch slot to load when the step plays (0–63 from the panel, < `0x80` accepted), sent as id `0x50`. `FF` = none. In the YBC sessions it is set only on track 0 note steps, values 0–60. |
| 5–7 | `00` | always 0 |

Byte offsets here are in the 136-byte patch message (see `tools/patchbank.py`). Bytes 49 and 58 are not in the community PDF; see `docs/sysex-spec.md` *Patch bytes the PDF does not name*.

Footer:
- Byte 0 is the played step range: start step in the high nibble, end step in the low nibble (init `0F` = steps 0–15). The length is `end + 1 − start`, plus 16 when end < start, so the range can wrap past step 15 (`pattern_length`). The YBC sessions only use `0F` and `07`.
- Byte 1 is the sync rate: an index 0–7 into the ticks-per-step table `[2, 3, 4, 6, 8, 12, 16, 24]` (24 PPQN) at `0x08029F6C`, i.e. 1/32T, 1/32, 1/16T, 1/16, 1/8T, 1/8, 1/4T, 1/4. Init `03` = 1/16. The YBC sessions also use `04` and `06`.
- Byte 2 is the play direction: 0 forward, 1 reverse, 2 ping-pong, 3 random. Always 0 in the YBC sessions.

The song-position routine `seq_track_position` reads bytes 0–2. It sums each chained pattern's length as `ticks[byte 1] × steps(byte 0)`, doubled for ping-pong. It then takes the clock modulo that total and walks the chain to the current step: up from start for forward, down from end for reverse, both ways for ping-pong, and through a random generator for random. Playback `player_on_cell` uses the same table for gate timing.
- Byte 3 is the mod-sequence smooth flag (0 off, 1 on); the UI toggles it on button event `0x13` (`ui_button_event`). The mod-sequence output `modseq_output` reads it. When it is off, the mod value jumps to each step's byte 1 at the step start. When it is on, the value crossfades linearly from the current step's byte 1 to the next step's on every clock tick: `(wA × current + wB × next) / 48 | 0x80`. The weights come from a table at `0x0802A286` with one 24-pair row per sync rate (`48/0, 24/24` at 2 ticks per step; `48/0, 32/16, 16/32` at 3; …). The YBC sessions set it once, on a track 2 pattern.
- The rest is 0.

Step byte 4 in detail: the setter `param_set` treats id `0x50` apart from the patch parameters. It compares the value with the current patch number (an observable at controller `+0x64`) and, if it differs, posts it to a port of the session controller (vtable `0x08027068`; handler `session_ctl_patch_load`, missed by Ghidra). For a value below `0x80` the handler reads 126 bytes from the patch store at `0x2000 + value × 0x200` (`ext_flash_read(sector = (value >> 3) + 2, offset = (value & 7) << 9, …)`, the `MS_ALL_PATCHES` area) into the session's patch at `0x15F5C`, loads it into the synth engine, and stores the new patch number. The limit is 128, not 64: the panel only reaches slots 0–63 (`docs/sysex-spec.md`, *Patch store*).

The Components `INIT` file stores step byte 4 as `00`, where the firmware and all real sessions use `FF`.

## Automation lane (108 bytes)

A lane has a 12-byte mask followed by 96 values, one per micro-step: 16 steps × 6 micro-steps. Mask bit *i* (LSB first, little-endian) marks micro-step *i* as recorded. Values outside the mask are always 0. The firmware clears each lane through `lane_clear(lane, step, micro)` and records through `recorder_on_cell`, which interpolates between micro-steps.

### Lane → parameter

The sequencer track constructor `player_new` gets one table of firmware parameter ids per track. Track 0 uses 42 u16 ids at `0x08027588`, track 1 uses 12 at `0x0802775C`, and track 2 has no table. During playback, lane *n* sends its value to `set(table[n], value)`. `tools/session.py` holds these tables as `LANE_IDS`, and `lane_param(track, lane)` names them.

The ids index the firmware parameter table at `0x0802A62A`, which has 76 records of 8 bytes:

| byte | content |
|------|---------|
| 0 | patch body offset (+9 = 136-byte message offset) |
| 1 | 0 |
| 2 | kind: 0 enum, 1 unsigned, 2 signed, 5 bool |
| 3 | centre |
| 4 | min |
| 5 | max |
| 6 | MIDI: `FF` = plain CC, `FC` = CC plus a +32 LSB CC, other values = NRPN MSB |
| 7 | CC number or NRPN LSB |

The MIDI-out encoder `midiout_param` reads bytes 6–7. Ids 0 and 1 are category (max 14) and genre (max 9), which confirms the offset column. In order, the ids cover the 136-byte fields at offsets 25–134, and `tools/session.py` keeps them as `PARAM_OFFSETS`. How these CCs compare with Components' Bass Station II `kD` array: `docs/bass-station-ii.md` *Compared with the Mono Station firmware*.

Track 0 lanes 0–41: osc1 coarse, fine, pw; mix osc1, sub, noise, audio in, ring mod; filter overdrive, reso, cutoff; dist amount; lfo rate, lfo sync rate; env attack, decay, sustain, release; then every mod-matrix routing (env, lfo, seq and vel sources) that does not target osc 2.

Track 1 lanes 0–11: osc2 coarse, fine, pw; mix osc2; then the osc 2 pitch and pw routings for env, lfo, seq and vel.

This fits the YBC data. The most-automated parameters there are filter cutoff (79 lanes), reso (78), env release (40) and sustain (38), mix sub, mix osc1 and osc 2 level.

## Open

- Header byte `0x42` and session bytes `0x155D8`–`0x155DE` are resolved from code (#55); a hardware diff would confirm them.
