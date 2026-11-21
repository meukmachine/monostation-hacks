# Release notes

What each custom firmware build on the [flasher page](https://meukmachine.github.io/monostation-hacks/)
does, newest first. Every build is stock firmware 1.2 plus the features below; anything not
listed works as in 1.2. Build numbers: `docs/release-test.md`.

## Test build 751

Being tested for the first release (752): it may change or have bugs.

### Mutate (Shift + Pattern Settings)

- **Mild Mutate on steps** (#13): in the Velocity, Gate, Glide and Expand views, Mutate nudges
  that one field of every note a little instead of shuffling whole steps. The rhythm stays;
  empty steps stay empty.
  - Velocity: every note's velocity moves a little.
  - Gate: gates move a little; nothing grows past the longest gate.
  - Glide: glide amounts move; some steps switch glide on or off.
  - Expand: notes move to nearby notes of the scale, within the octaves the pattern
    already uses.
- **Every track**: Osc 1, Osc 2 and the Mod Seq track (#66; there Velocity moves the levels).
- **Dual view** (Osc 1 + Osc 2, #65): the notes of both Osc tracks move.
- **Patch randomiser** (#45): Mutate in the Patches view changes the sound a little and glides
  to it over one beat. The patch shows as edited; save it to keep it. While recording, the
  changes are recorded as automation.
- **Hold to repeat** (#60): hold Shift + Pattern Settings while playing and Mutate repeats:
  steps once per pattern loop (each track on its own loop), the patch once a beat, gliding
  on. Also the stock whole-step shuffle in the Note view. Stopped, it mutates once.
- The Note view keeps the stock Mutate (shuffles whole steps).

### Live transpose (#21)

- In the Note or Expand view, Shift + a key transposes both Osc tracks by scale degrees
  while they play; the key doesn't sound.
- While Shift is held, the reset key blinks cyan and the current key red; tap the first
  note's key to reset.
- Dual view: Shift + a key on one keyboard transposes only that track.
- Loading a session resets the transpose.

### Pattern sequences

- While stopped, hold Shift in the Patterns view and tap patterns or chains to play them in
  that order.
- Shift + Patterns is Append (lit orange): taps add to the sequence, also while playing.
- The sequence is saved in the session.

### Drive amounts (#61)

- In the Firmware settings tab of the flasher page: an Overdrive amount and one Distortion
  amount per panel distortion type, 0–100.
- 0 is stock (the default); changes are heard at once; Save keeps them across power-off.

## Stock firmware 1.2

Novation's firmware (build 800), unchanged: flash it to restore the unit.
