# Circuit Mono Station firmware mods

New features for the Novation Circuit Mono Station, built on top of Novation's own
firmware 1.2, plus the reverse-engineering tools and notes that make them possible.

- **Flash the mods from your browser:** [meukmachine.github.io/monostation-hacks](https://meukmachine.github.io/monostation-hacks/)
  (Firefox or a Chromium-based browser on the desktop; you choose Novation's stock
  firmware 1.2 file and the page builds the modded firmware from it).
- **Discussion and announcements:** the Facebook group
  [Novation Circuit Mono Station Users](https://www.facebook.com/groups/2161801177248617).

- **Plays like stock.** Each mod changes only the behaviour it adds; everything else is
  the original firmware, byte for byte.
- **Easy to undo.** The bootloader is never touched, so you can always flash stock
  firmware 1.2 back the same way.
- **No Novation code in this repo.** You supply the stock firmware yourself; the tools
  rebuild it from source generated on your machine.

> Unofficial and unsupported by Novation. Flashing modified firmware is at your own risk.

**Made with Claude.** The reverse engineering, the tools, the docs and the mods in this
repo were all done with [Claude Code](https://claude.com/claude-code). Claude can help
you too: open Claude Code in a checkout of this repo and ask it to walk you through
getting the stock firmware, building a mod and flashing it. `CLAUDE.md` gives it the
project's commands and safety rules.

## Feature: context-sensitive Mutate (#13)

Stock **Mutate** (Shift + Pattern Settings) shuffles whole steps. With this mod, Mutate
in the views below nudges only one property of the steps that hold a note: the rhythm
and everything else stay as they are.

| View | Open with | Mutate now |
|------|-----------|------------|
| Velocity | Velocity | moves velocities up or down by a pad level or a few |
| Gate | Gate | makes notes a little longer or shorter, up to the longest gate already in the pattern; one note keeps that longest gate, so the range holds press after press |
| Glide | Shift + Gate | moves glide amounts a little and now and then switches glide on or off |
| Expand | Shift + Note | moves notes to nearby notes of the current scale, within the octaves the pattern already uses: from the start of the octave of its lowest note to the end of the octave of its highest (octaves counted from the root) |
| Dual Osc | Osc 1 + Osc 2 together | moves the notes of both Osc tracks, as in Expand (#65) |
| Mod Seq level | Velocity, on the Mod Seq track | moves levels up or down by a pad, on the steps that have a level (#66) |

Every press moves each note one step (#60); press again to wander further. Keep Shift +
Pattern Settings held while the pattern plays and Mutate repeats each time the pattern
starts again, so every pass is a little different until you let go. In the dual view
each Osc track repeats on its own loop.

Tips:

- If no note is longer than one step, gates go up to one step. For longer gates, first
  make one note as long as you want the maximum to be.
- In Expand view the pattern is hidden: listen, or press Note to see the new notes.
- Works on all three tracks; the Mod Seq track's Gate, Glide and Expand views work as on
  the Osc tracks (Glide also on steps with only a level).
- In the Note view Mutate shuffles whole steps as stock, but held while playing it now
  repeats too, each time the pattern starts again. In the Pattern Settings view it does
  nothing, as stock.

## Feature: patch randomiser (#45)

Open the **Patches** view and press **Mutate** (Shift + Pattern Settings): the current
patch takes a small random step, as if a few knobs had been turned a little. The sound
glides to the new setting over one beat (four steps at the session tempo) instead of
jumping. Press again to wander further, or keep the buttons held: Mutate then repeats
once a beat, each glide running into the next, so the sound keeps morphing until you let
go.

- One press moves about half the knobs, each by up to an eighth of its range. Switches
  and waveforms change only now and then, and jump instead of gliding.
- Turn a knob during the glide and the knob wins: that parameter stops gliding. The same
  goes for parameter automation the pattern plays.
- Press Mutate while recording and the changes are recorded as automation, like knob
  moves: a quick way to put movement into a pattern.

Each step stays playable:

- Kept as they are: the octave and range of both oscillators (the patch stays in its
  register), patch level, audio-in level and the envelope-to-amp switch.
- Tuning is never changed, apart from a little Osc 2 detune. Pulse widths stay away
  from the silent extremes.
- Each knob steps within a playable range: Osc 1 stays in the mix, the cutoff stays
  open, resonance stays short of self-oscillation and mod depths stay moderate. A
  setting that was already outside that range is never pushed further out.
- When the filter shape changes, the cutoff moves to where the notes get through: the
  band sits around the middle, the high pass sits low, and both follow the keys.
- A step never makes a sustain-less patch too short to hear: the decay gets longer.

The patch shows as edited, so save it the usual way if you like it, or load another
patch to throw it away. With MIDI out of the synth params on, the new values also go out
as CCs and NRPNs (some may be dropped).

The patch randomiser is part of the Mutate mod above (`overlays/randomise`).

## Feature: live transpose (#21)

Transpose the playing sequence from the keys, the way many monosynth sequencers do.

1. Open the Note view (or Expand: Shift + Note) of Osc 1 or Osc 2, or the dual view
   (Osc 1 + Osc 2 together).
2. Hold **Shift** and tap a key. The key doesn't sound; both Osc tracks now play their
   patterns moved by that many scale degrees, counted from the first note of the
   track's pattern. In the dual view each keyboard transposes only its own track, so
   Osc 1 and Osc 2 can be moved apart; the Note and Expand views move both together.
3. Hold Shift and tap the key of the pattern's first note to go back.

While you hold Shift, the key of the pattern's first note blinks cyan, and the key
you transposed to blinks red.

The notes stay in the scale, and nothing is saved: the pattern itself doesn't change,
and loading another session starts it untransposed.
This mod is `overlays/transpose`.

## Feature: stronger drive (#61)

Overdrive and Distortion can go further than stock: turned up, they clip harder and the
three distortion types differ more. How far is set with four sliders, 0–100, in the
flasher page's **Firmware settings** tab: one for Overdrive and one per panel distortion
type. 0 is stock (the default), 50 the boost of the first drive builds, 100 twice that.
Changes are heard at once; Save keeps them across power-off (`docs/mod-settings.md`).
This mod is `overlays/drive`.

## Feature: pattern sequences (#20)

Play patterns in any order, like the Circuit's Pattern Chain Sequences.

1. Stop playback and open the Patterns view.
2. Hold **Shift** and tap patterns in the order they should play. Hold one pattern and
   tap another to add a chain; the same pattern may come back. Each track has its own
   sequence: up to 32 units, where a single pattern counts 1 and a chain 2.
3. Let go of Shift: the sequence's patterns light up. Press Play: each track plays its
   sequence and loops.

A plain tap on a pattern drops the track's sequence. **Shift + Patterns** turns Append on
(the Patterns button lights orange): Shift + tap then adds to the end of the sequence, also while
playing. Append is off after power-on, so Shift + tap while playing stays the stock
instant switch. Sequences are saved with the session. This mod is `overlays/sequences`.

## Firmware versions

Firmware 1.2 is build 800 inside the firmware. Our builds stay below 800 on purpose:
Components offers an update to any unit below 800, so it offers to put stock 1.2 back.
The flasher page offers the latest release and the current test build (it may change or
have bugs). Numbering rules: [docs/release-test.md](docs/release-test.md); what each
build does: [docs/release-notes.md](docs/release-notes.md).

## What you need

- A Circuit Mono Station and a USB cable.
- Linux or macOS with Python 3.
- `arm-none-eabi-gcc` and `binutils-arm-none-eabi` (Debian/Ubuntu:
  `sudo apt install gcc-arm-none-eabi binutils-arm-none-eabi`).
- For flashing: a virtualenv with `mido` and `python-rtmidi`:

      python3 -m venv .venv
      .venv/bin/pip install mido python-rtmidi

- The stock firmware 1.2, `monostation-firmware-800.syx`, in `private/firmware/`. It
  ships inside Novation Components; where to find it:
  [docs/firmware-versions.md](docs/firmware-versions.md). Unpack the installer with `7z`;
  don't run it.

## Quick start

The [flasher page](https://meukmachine.github.io/monostation-hacks/) is the easy way. To
build the modded firmware yourself (all mods, the build command of
[docs/release-test.md](docs/release-test.md) with version 751):

    python3 tools/syx2bin.py private/firmware/monostation-firmware-800.syx private/firmware/monostation-firmware-800.bin
    python3 tools/fwsrc.py generate private/firmware/monostation-firmware-800.bin build/mods --overlay overlays/settings --overlay overlays/randomise --overlay overlays/transpose --overlay overlays/drive --overlay overlays/sequences --version 751
    python3 tools/fwsrc.py build build/mods build/mods/firmware.bin
    python3 tools/bin2syx.py build/mods/firmware.bin private/firmware/mods-751.syx --version 751

To drop a mod, leave out its `--overlay`, but mind the dependencies: `drive` needs
`settings` (`fwsrc.py` refuses it without), and `sequences`' Shift + Patterns (Append)
needs `randomise`.

Flash it:

    .venv/bin/python tools/flash.py private/firmware/mods-751.syx

The flasher checks the file, then waits for the unit's bootloader:

1. Switch the Mono Station off.
2. Hold **Scales + Note + Velocity** and switch it on.
3. The flasher sends the firmware, waits for the unit to restart and checks that it
   reports the new version (751).

Go back to stock firmware 1.2 at any time: connect the unit to Novation Components and
accept its firmware update, or flash it the same way:

    .venv/bin/python tools/flash.py private/firmware/monostation-firmware-800.syx

## Writing your own mods

`tools/fwsrc.py` turns the stock image into Thumb-2 assembly that rebuilds byte for byte.
An **overlay** replaces stock functions by name with your own C or assembly; the original
stays callable as `stock_<name>`. A short example is `overlays/drive/drive.c` (one
replaced function, settings read with `mod_setting`); `overlays/notesink_cpp` shows a C++
overlay, and `overlays/include/firmware.h` has prototypes of stock functions. See
`docs/source-build.md` (*Overlays*) for the format and `docs/source-map.md` for what each
stock function does.

Generated source is Novation's code: it stays in the git-ignored `build/` directory, and
the firmware and other copyrighted files in the git-ignored `private/` directory. Never
commit them.

## Tools

| Tool | What it does |
|------|--------------|
| `tools/syx2bin.py`, `tools/bin2syx.py` | firmware `.syx` ⇄ raw image (`docs/firmware-container.md`) |
| `tools/fwsrc.py` | generate and rebuild firmware source, with overlays (`docs/source-build.md`) |
| `tools/fwpatch.py` | apply small assembly patches with hooks (`docs/patching.md`) |
| `tools/fwdiff.py` | make, apply and show the firmware diffs the flasher page ships (`docs/firmware-diff.md`) |
| `tools/modsettings.py` | list overlay settings, build and decode a mod settings store (`docs/mod-settings.md`) |
| `tools/fetch_toolchain.py` | install the stock compiler (GCC 5.3.1) and ST libraries into `toolchains/` (`docs/source-build.md`) |
| `tools/flash.py` | flash a `.syx` through the bootloader and verify it (`docs/update-protocol.md`) |
| `tools/ctllog.py` | turn on the stock debug reports; log or map control ids (`docs/inventory.md`) |
| `tools/p40dump.py` | read external flash with `p 0x40` op 2 (`docs/sysex-handlers.md`) |
| `tools/hwtest_slots.py` | hardware test of patch slots 64–127 (`docs/hw-test-patch-slots.md`) |
| `tools/patchbank.py` | list and decode patches in `.monostationpack` banks (`docs/sysex-spec.md`) |
| `tools/sessionsyx.py`, `tools/session.py` | split, rebuild and inspect session dumps (`docs/session-transport.md`, `docs/session-format.md`) |
| `tools/fwmap.py`, `tools/disasm.py`, `tools/ghidra/` | firmware map, quick disassembly, Ghidra headless scripts |

## Documentation

For owners:

- [Release notes](docs/release-notes.md): what each build on the flasher page does.
- [Official firmware versions](docs/firmware-versions.md): 1.0, 1.1 and 1.2, their build
  numbers, what each added and where the 1.2 file comes from.
- [Release test](docs/release-test.md): what we check on a unit before a build goes on
  the flasher page.
- [Entering the bootloader by hand](docs/update-protocol.md#entering-the-bootloader-by-hand)
  and [Recovery](docs/update-protocol.md#recovery): bootloader mode, reading the version
  lights, and retrying an update that failed.
- [Patch SysEx](docs/sysex-spec.md): the 136-byte patch format, `.monostationpack` banks,
  how to request a patch, and the 64 hidden patch slots.
- [Session format](docs/session-format.md) and [session transport](docs/session-transport.md):
  what is in a session and how session backups travel over SysEx.
- [Patch slots 64–127 on the unit](docs/hw-test-patch-slots.md): what the hidden slots
  hold and what program change can reach.
- [MIDI implementation](docs/midi.md): what the unit sends and receives, on which
  channels, and which CCs are free.
- [Triplets](docs/triplets.md): the triplet rates the stock sequencer already has, and
  research for triplets inside a pattern (#62).

For modders:

- [Firmware source build](docs/source-build.md): generating source and writing overlays.
- [Source map](docs/source-map.md): what each stock function does;
  [class diagram](docs/class-diagram.md): the C++ classes behind the vtables.
- [Inventory](docs/inventory.md): every control, circuit and firmware feature, and how far
  each is understood.
- [Mod settings](docs/mod-settings.md): the settings store overlays read, and its SysEx.
- [Firmware diffs](docs/firmware-diff.md): how the flasher page ships builds without
  Novation's bytes; [app UI](docs/app-ui.md): the flasher page's layout.
- [Sequencer](docs/sequencer.md): clock, patterns, note routing.
- [Hardware](docs/hardware.md): MCU, memory map, flash sectors, free RAM.
- [Bootloader](docs/bootloader.md), [update protocol](docs/update-protocol.md) and
  [firmware container](docs/firmware-container.md): how firmware gets onto the unit.
- [Binary patching](docs/patching.md), [SysEx handlers](docs/sysex-handlers.md),
  [firmware map](docs/firmware-map.md).
- [Bass Station II patch SysEx](docs/bass-station-ii.md): reference only, not a Mono
  Station format.
- [GitHub issues](https://github.com/meukmachine/monostation-hacks/issues): feature work (`enhancement`) and open research (`research`).

## Tests

    python3 -m unittest discover -s tests -v

Firmware tests skip when `private/firmware/monostation-firmware-800.syx` is missing; overlay
tests skip without `arm-none-eabi-gcc`.
