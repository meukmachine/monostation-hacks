# Release test

Run this list on the unit before a build goes on the flasher page. Note the build, the date
and anything that fails in the release's issue comment.

Versions stay between 750 and 800, so Components still offers stock 1.2. There is one
test number at a time (odd), and every test build is rebuilt under it. When it passes this
list, the same source is built as test + 1 (even) and released; the next test number is
test + 2. The flasher page offers the latest release and the current test build.
Now: test 751, no release yet (the first will be 752).

Build:

```
python3 tools/fwsrc.py generate private/firmware/monostation-firmware-800.bin build/rel \
    --overlay overlays/settings --overlay overlays/randomise --overlay overlays/transpose --overlay overlays/drive --overlay overlays/sequences --version N
python3 tools/fwsrc.py build build/rel build/rel/fw.bin
python3 tools/bin2syx.py build/rel/fw.bin private/firmware/mods-N.syx --version N
```

## Flash

- [ ] The flasher page flashes it (Firefox): reboot into the bootloader, send, the unit reports N.
- [ ] Components shows firmware N and offers 1.2 as an update.

## Mild Mutate on steps (#13, #60)

Shift + Pattern Settings is Mutate. Use an Osc 1 pattern with notes on about half the steps.

- [ ] Velocity view: one press changes every note's velocity a little; the rhythm stays.
- [ ] Gate view: gates move a little; one note keeps the longest gate; nothing goes past it.
- [ ] Glide view: glide amounts move; some steps switch glide on or off.
- [ ] Expand view: notes move to nearby notes of the scale, never off the scale, and stay between the start of the lowest note's octave and the end of the highest note's octave.
- [ ] The same on the Osc 2 track.
- [ ] Mod Seq track (#66), with some steps that have only a level: Velocity (level) view moves levels, never to none; Gate and Expand change only steps with a note; Glide changes steps with a note or a level.
- [ ] Dual view (#65, Osc 1 + Osc 2 together): notes of both Osc tracks move to nearby scale notes.
- [ ] Note view: Mutate is stock (shuffles whole steps), on every track.
- [ ] Empty steps stay empty in every view.
- [ ] Playing, hold Shift + Pattern Settings: Mutate repeats once per pattern loop, as the pattern starts again; letting go of either button stops it.
- [ ] Stopped, hold it: one Mutate only.
- [ ] Dual view, Osc 1 and Osc 2 patterns of different lengths, held: each track changes when its own pattern starts again.
- [ ] Mod Seq Level view, held while playing: repeats once per Mod Seq loop.
- [ ] Note view, every track, held while playing: the stock whole-step shuffle repeats once per loop; stopped, once only.
- [ ] Pattern Settings view: Mutate does nothing (stock).

## Patch randomiser (#45, #60)

- [ ] Patches view + Mutate: the sound changes a little and glides over one beat (500 ms at 120 BPM, 1 s at 60 BPM); the patch shows as edited.
- [ ] Hold Shift + Pattern Settings: the sound keeps morphing, one glide a beat, smoothly into the next; it stops on release.
- [ ] Each press changes it about as much as the last.
- [ ] Turn a knob (cutoff) during a glide: the knob wins, no jump back.
- [ ] Hold a note while pressing: no clicks or stuck notes; MIDI out (if monitored) shows the param changes.
- [ ] Select another patch right after a press: the new patch loads as saved, no leftover glide.
- [ ] Save the mutated patch, reload it: it sounds the same.
- [ ] Record on, playing: Mutate's changes are recorded as automation and play back.

## Live transpose (#21)

- [ ] Note view, Osc 1, playing: Shift + key transposes both Osc tracks by scale degrees; the key doesn't sound.
- [ ] While Shift is held the reset key blinks cyan, the current key red.
- [ ] Tapping the first note's key resets.
- [ ] Dual view (Osc 1 + Osc 2): Shift + a key on one keyboard transposes only that track, the other keeps its transpose; each keyboard blinks its own reset and current key. Back in the Note view, Shift + key sets both again.
- [ ] Loading a session resets the transpose.
- [ ] No hanging notes when transposing while notes play.

## Mod settings (#68)

- [ ] With no store (fresh unit): the unit boots, `7C 03` answers store 0, every setting is at its default. After Reset to stock: store 1 (an empty store), every setting at its default.
- [ ] Firmware settings tab, Read from unit: both drive sliders listed; moving one is heard at once.
- [ ] Stock firmware: Read from unit says it has no mod settings.
- [ ] Save, power off and on: the values come back (`7C 03` answers store 1).
- [ ] Patch and session saves and loads still work after a Save.
- [ ] Back up: the pack holds `mod-settings.bin` and Components still opens it; Reset to stock, then Send the pack with its settings: the values come back.
- [ ] CLI: `--settings --set "Overdrive amount=60" --save`, then `--settings` lists 60.

## Drive (#61)

- [ ] Every slider at 0 (the default): Overdrive and Distortion sound stock at every knob setting.
- [ ] Sliders at 50: as the earlier drive builds (×3); all three distortion types sound distinct. Each Distortion slider changes only its own panel type.
- [ ] Sliders at 100: no clicks or dropouts when moving them while a note plays (distortion is already at its most around 50, by ear 2026-11-21).

## Stock behaviour

- [ ] Sessions: save, load, switch while playing.
- [ ] Patches: save, load, Initialise (Shift + Patches).
- [ ] Power off and on: settings and the last session come back.
- [ ] MIDI clock in and out, DIN and USB; notes in from a keyboard.
- [ ] CV/gate out and sync out.
- [ ] Components: read and write a patch, back up sessions.
- [ ] One hour of playing with patterns, Mutate and transpose: no freeze or reset.
- [ ] Restore stock 1.2 from the flasher page, then the build again.
