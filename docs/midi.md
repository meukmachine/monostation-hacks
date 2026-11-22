# MIDI implementation (#53)

What the stock firmware (build 800) sends and receives over MIDI, checked against Novation's
MIDI parameter guide (`private/docs/circuit-mono-station-midi-parameters-v1.pdf`). All
findings come from the disassembly. The internals (parsers, transport, clock, router) are in
`docs/sequencer.md` *MIDI clock and transport*. SysEx is in `docs/sysex-handlers.md` and
`docs/sysex-spec.md`.

USB and DIN are merged on input (`midi_in_parse` ignores the port) and both get every
output message (`midi_tx_short`, port mask 3).

## Channels

| Use | In | Out | Default | Set by |
|-----|----|-----|---------|--------|
| Osc 1 notes (track 0) | MIDI-in ctx `+0x08` | out channel table `[0]` | ch 1 | System Settings; record `0x0C` |
| Osc 2 notes (track 1) | ctx `+0x09` | out table `[1]` | ch 2 | System Settings; record `0x10` |
| Patch CC/NRPN, patch program change | ctx `+0x0B` (= Osc 1 channel) | out table `[3]` (= Osc 1 channel) | ch 1 | follows Osc 1 |
| Session program change | ctx `+0x0C` | out table `[4]` | **ch 16, fixed** | `app_construct` only |
| out table `[2]` | — | — | ch 1 | never changed; not seen used |

`midi_apply_settings` (`0x0801CA18`) copies the Osc 1/Osc 2 channels into both tables
whenever the settings change. `app_construct` builds the MIDI-in ctx at `0x0800F9F0`
(`+8 = 0`, `+9 = 1`, `+0xB = 0`, `+0xC = 0x0F`) and the 5-byte MIDI-out channel table
at `0x0800E370` (`00 01 00 00 0F`).

The Osc 2 channel byte at record `0x10` is confirmed in code: `midi_apply_settings` copies
it to the MIDI-in ctx `+0x09` (the Osc 2 note channel in `midi_in_parse`) and to out
table `[1]`.

## Received

| Message | Where | Effect | Filter |
|---------|-------|--------|--------|
| `9n`/`8n` | Osc 1 or Osc 2 channel | note on/off into the router (track 0/1); velocity 0 = off | Note Rx |
| `Bn` CC 0–119 | Osc 1 channel | patch parameter through `cc_param_map` (`0x08028D90`); CC pairs use both n and n + 32 | CC Rx |
| `Bn` CC 99/98 | Osc 1 channel | NRPN MSB/LSB, marks NRPN pending | CC Rx |
| `Bn` CC 6 | Osc 1 channel | NRPN data entry: applied only while an NRPN is pending and MSB ≤ 2, through `nrpn_param_map` (`0x08028A68`) | CC Rx |
| `Bn` CC 101/100 | Osc 1 channel | stored. RPN null (127/127) resets the parser and clears NRPN pending. No RPN is acted on | CC Rx |
| `Bn` CC 121 | Osc 1 channel | resets the NRPN/RPN parser state (it is **not** a controller reset for the synth) | CC Rx |
| `Bn` CC 123 value 0 | Osc 1 or Osc 2 channel | all notes off on that track | Note Rx |
| `Cn` | Osc 1 channel | load patch 0–63 (`midi_ctl_program_change`, param id `0x50`) | Note Rx section |
| `Cn` | ch 16 | 0–31: switch session now; 64–95: queue the switch for the end of the pattern (`session_ctl_on_cell`) | Note Rx section |
| `F8` | any | clock (`clock_state_on_midi_clock`) | Clock Rx |
| `FA`/`FB`/`FC` | — | Start / Continue / Stop | Clock Rx |
| `F2` | — | Song Position Pointer (only while stopped) | Clock Rx |
| `F3` | — | Song Select: same cell as the session program change, then forwarded to MIDI out | Clock Rx |
| `F0` | — | SysEx (`sysex_dispatch`, `docs/sysex-handlers.md`) | none |

**Ignored:**
- CCs on any channel other than the Osc 1 channel, apart from CC 123 on the Osc 2 channel.
  This includes CCs on ch 16.
- CC 120 and up, except 121 and 123.
- NRPN data-entry LSB (CC 38), data increment/decrement (CC 96/97) and NRPN MSB > 2.
- Polyphonic and channel aftertouch, pitch bend, sustain (CC 64), mod wheel (CC 1) and
  expression (CC 11). The PDF's *Global supported* table also lists these as unsupported.
- `F4`–`F7`, `F9`, `FE` (active sensing), `FF` (reset).

**Filter bits** (MIDI-in ctx `+0x0D`, from record byte `0x08`): bit 0 = notes and program
change, bit 1 = CCs, bit 2 = realtime (always set; clock Rx is checked later in the clock
and transport handlers).

## Sent

| Message | Channel | When | Gate (Settings) |
|---------|---------|------|-----------------|
| `9n`/`8n` | Osc 1 / Osc 2 channel per track | keys, sequencer playback | Note Tx |
| CC, CC pair (n = value >> 1, n + 32 = low bit << 6), NRPN (99, 98, 6) | Osc 1 channel | a patch parameter changes on the panel (`midiout_param` `0x08018B90`, record bytes 6–7 of `param_table`) | CC Tx |
| `FA`/`FB`/`FC`/`F2`/`F3`/`F8` | — | transport and clock; in MIDI-slave mode `F8` is a thru of the input | Clock Tx |
| SysEx | — | replies only (`docs/sysex-handlers.md`) | none |

No program change is sent when a patch or session changes: the code has no `0xC0` status constant (a scan found only literal-pool data).

## CC/NRPN coverage (#2 candidates)

`param_table` (`0x0802A62A`, 76 records × 8 bytes), `cc_param_map` and `nrpn_param_map`
agree for every parameter. A scratch script compared all three and found no mismatch.
`tests/test_patchbank.py` `FirmwareTableTest` already checks the PDF's numbers against the
table.

- **Patch bytes no CC/NRPN reaches:** only the name (bytes 9–24). Bytes 28–40 are unused
  in every patch. So the only patch data that is SysEx-only is the name.
- **Category and genre** are NRPN MSB 2, LSB 64/65 in the firmware. The PDF prints them as
  64:2/65:2, with MSB and LSB swapped.
- **Not reachable by MIDI at all:** the session-level and global state: tempo, swing, swing
  rate, pattern settings, scales/root, paraphonic mode, CV/Gate source, Audio In gain and
  param `0x4C` (glide-related global). These are candidates for #2.
- **Free CCs** on the Osc 1 channel (0–119, not in `cc_param_map` and not taken by the
  NRPN/RPN parser, which uses 6 and 98–101): 0, 1, 2, 4, 8, 10, 11, 32, 64–68, 72, 74, 77,
  82, 85, 96, 97, 106. Free CCs reach `cc_param_map` and are dropped there.

## System Settings record: open bytes settled

Updates to `docs/session-transport.md` *Settings record*:

| Offset | Was | Now |
|--------|-----|-----|
| `0x00` | unknown, `08` | **Audio In gain**. `0x08018EE8` (settings unit) copies it, clamped to 31, to `0x2000086C`. That byte drives switch-word bits 0–1/13–15 and is also written from the mixer view's Shift + Audio In pot (pot / 8 = 0–15). `08` is mid-scale. `app_construct`'s `> 0x1F` check is the same clamp. |
| `0x08` bit 1 | set in every reading, unknown | **Fixed Velocity** (`docs/sequencer.md` *MIDI settings*). It was on for the test unit. |
| `0x10` | inferred Osc 2 channel | confirmed in code (above). |
| `0x14` | `FF`, unknown | Still unknown. `settings_save` (`0x08018F74`) compares it, so it is part of the saved state. No reader was found in the settings view, the MIDI code or `app_construct`. |
| `0x04` | `00` | Also compared by `settings_save`; no reader found. |

`settings_save` compares the bytes at `0x00`, `0x04`, `0x08`, `0x09` (bits 0–4), `0x0C`,
`0x10` and `0x14` with the copy loaded at boot. It writes the 24 bytes to `0x1000` only
when one of them differs.

## Open

- Record `0x14` and `0x04`: change each by hand (`p 0x40` op 3) and watch what changes on the
  unit.
- Audio In gain = record `0x00`: confirm on hardware (change the gain, power off, read
  `0x1000`).
- Out table `[2]`: find out whether track 2 (Mod Seq) notes ever reach MIDI out.
