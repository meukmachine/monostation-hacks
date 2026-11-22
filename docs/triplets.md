# Triplets (#62): research

Research for issue #62. It covers what the stock firmware already does, how other
sequencers handle triplets, what the firmware allows, and the decisions left for the
owner. Nothing here is built yet.

## Stock firmware already has triplet rates

Each pattern has a sync rate (Pattern Settings view, pads 1–8, user guide p. 48): 1/4,
1/4T, 1/8, 1/8T, 1/16 (default), 1/16T, 1/32, 1/32T. In the firmware this is pattern footer
byte 1, an index into the ticks-per-step table `[2, 3, 4, 6, 8, 12, 16, 24]` at
`0x08029F6C` (24 PPQN; `docs/session-format.md`). So a whole pattern can already run on a
triplet grid. Each track has its own pattern, so the Osc 2 track can play triplets against
a straight Osc 1 track today.

Pattern lengths in ticks (one 4/4 bar = 96 ticks):

| Rate | Ticks per step | 16 steps | Steps for one bar |
|------|---------------:|---------:|------------------:|
| 1/16 | 6 | 96 (1 bar) | 16 |
| 1/16T | 4 | 64 (2/3 bar) | 24 (more than 16: not possible) |
| 1/8T | 8 | 128 (4/3 bar) | **12** |
| 1/4T | 16 | 256 | **6** |
| 1/32T | 2 | 32 | 48 (not possible) |

A 16-step triplet pattern drifts against the bar. The guide's workaround is Instant Pattern
Switching (Shift + pattern, p. 51). A bar-aligned triplet pattern already exists at 1/8T
with 12 steps, or 1/4T with 6 steps (Pattern Settings, pads 17–32).

The rate changes only when the pattern's cycle ends (guide p. 49). The chain timing
(`seq_track_position`, `docs/session-format.md`) sums each pattern's length as
`ticks × steps`, so mixed rates in a chain are handled correctly.

**Elektron's speed multipliers map onto these rates.** Digitakt II's 3/4X "for triplets" is
the same as 1/8T, and 3/2X is the same as 1/16T. The stock rates therefore already cover what
Elektron does per track.

What stock **cannot** do: triplets inside a straight pattern. For example, one beat of
1/16T fill in a 1/16 groove, or a 3-note roll on one step.

## How other sequencers do it

The sources are manuals where I could open them. The claims marked *(unverified)* come from
reviews or search snippets.

1. **Clock division per pattern or track with triplet values.**
   - Novation Circuit Rhythm/Tracks and Launchpad Pro mk3 have the same eight rates as the
     Mono Station.
   - Roland TR-8S has SCALE with 8th(T) and 16th(T).
   - Arturia BeatStep/KeyStep Pro and Ableton Push have time divisions *(unverified)*.
   - Circuit Rhythm, like the Mono Station, applies a rate change at the end of the cycle.
2. **Speed multiplier plus independent track length (polymeter).**
   - Elektron Digitakt II offers per-track SPEED (1/8X … 2X, including 3/4X and 3/2X) and
     LENGTH.
   - A master length with CHANGE/RESET decides when cued patterns switch, which solves
     the drift above.
3. **Ratchets or retrigs per step.**
   - Elektron retrig RATE includes 1/12, 1/24 and 1/48: "to trig triplets … set the retrig
     rate to 1/12 (or 1/24)", with a retrig length that can span several steps.
   - TR-8S SUB step divides a step into 1/2, 1/3 or 1/4 (SUB + pad, VALUE).
   - Polyend Tracker Roll and OP-Z pulse/multiply repeat a step *(the triplet behaviour is
     unverified)*.
   - Note repeat in triplet values: Circuit Rhythm drum pads and MPC Note Repeat.
4. **Micro-timing per step.**
   - **Circuit Rhythm/Tracks: six micro-steps per step, each toggled on or off**
     (Shift + Gate, pads 17–22; Circuit Rhythm guide pp. 37–38). One step can fire on any
     of its 6 ticks. This is the Mono Station's own grid, and Novation users already know
     this UI.
   - Elektron micro-timing: TRIG + left/right, 24 positions per step on the Octatrack, so
     triplets land exactly.
   - Polyend Play "Micromove" *(unverified)*.
5. **Tuplet groups per step** ("these 2 steps become 3"). None of the mainstream devices
   I checked documents this. Torso T-1 2.0 has per-step note division *(unverified)*.
6. **Other approaches.** Triplet record quantise (MPC Timing Correct 1/16 triplet), and nudging a
   whole pattern (TR-8S).

Swing and triplets in other devices: the MPC swings only at 1/8 or 1/16. Circuit swing
moves the even steps. Applying swing on top of triplet hits bends them, so devices either
leave swing off triplets or accept the bend.

## What the firmware allows (from the disassembly)

- **Time base.** The clock runs at 24 PPQN, so a 1/16 step has 6 ticks. The clock engine is
  a 1 ms soft timer in the main loop (`docs/sequencer.md` *Time base*). Exact triplet
  positions on a 1/16 pattern:
  - 1/16T = every 4 ticks: steps 1–4 hit at +0 and +4, +2, +0 and +4, +2. Two hits fall in
    one step, so a single offset per step is not enough.
  - 1/8T = every 8 ticks: +0, +2, +4 on three consecutive steps.
  - 3 hits in one step = every 2 ticks (1/48, 1/32 triplets).
  - 2 hits = 3 ticks.
  - 4 hits in one step (1.5 ticks) is **not exact** at 1/16. It is exact at 1/8 and slower
    rates.
- **Note-ons.** `player_on_cell` (`0x080234AC`) plays a step's note only when the step
  cell changes, so note-ons happen only on step starts. Note-offs already run per tick
  from a delta list of 8 slots (`docs/sequencer.md` *Track player*). Hits inside a step
  need a second, delayed note-on list. An overlay can keep it in free RAM (CCM).
- **Per-step storage.** Each step has 8 bytes. Bytes 5–7 are always 0 in every session
  seen (`docs/session-format.md`). The step editors write only bytes 0–4
  (`track_patterns_on_cell` `0x0801CBDE`, field by field), and `recorder_write_step`
  (`0x08021334`) writes only bytes 0–2. So byte 5 can hold a 6-bit micro-step mask without
  changing the session format.
  - **Still to check:** whether clearing a step or pattern resets bytes 5–7, and whether
    Components keeps them when it backs up and restores a session.
- **Swing** stretches and shrinks alternate ticks (`docs/sequencer.md` *Swing*). Hits placed on
  ticks therefore move with the swing. At swing 50 (the default) they are exact.
- **Recording** (`recorder_write_step`) quantises notes to steps. Keeping recorded
  triplets would need the recorder to write the mask too. That is optional.

## Options for an in-pattern feature

| | What | Covers | Cost |
|-|------|--------|------|
| A | Nothing new: document 1/8T × 12 steps and per-track rates | whole-pattern triplets, bar-aligned | a README/flasher note |
| B | **Micro-step mask per step** (Circuit Rhythm/Tracks style): byte 5 bits 0–5 = which of the 6 ticks fire, 0 = stock (tick 0 only) | 1/16T and 1/8T inside a straight pattern (exact); ratchets of 2, 3 and 6; off-grid notes (#6) | player hook with a delayed note-on list, one new edit view, mask write on step edits |
| C | Ratchet count per step (TR-8S SUB style): 1–4 hits spread over the step | rolls; 3 = 1/48 triplet roll; 1/16T only with a span over 2 steps | smaller UI than B, fewer patterns; 4 hits not exact at 1/16 |
| D | Tuplet group editor ("these 2 steps play 3 notes") on top of B | quick 1/16T and 1/8T entry | a UI that writes B's masks; new territory, no reference device |

My recommendation is **B**. It covers both #62 and the ratchet part of #6 with one data
field. Novation users already know it from Circuit Rhythm and Tracks, and it is exact at 24
PPQN. D can come later as a shortcut that writes B's masks.

## Decisions for the owner

1. **Scope:** B (masks), C (ratchet count), or only A (stock rates, documented)?
2. **Note per hit:** do all hits of a step replay the same note with the same velocity?
   Circuit Rhythm does this. And how long is each hit? Options: the step's gate cut to the
   next hit, or a fixed short gate.
3. **UI:** where to edit the mask. One option is Shift + Gate view with pads 17–22 for the 6
   micro-steps, as on Circuit Tracks. Which buttons are free there still needs checking.
4. **Swing:** swing the hits (the stock tick behaviour) or keep them straight?
5. **Drift:** leave pattern switching as stock (end of the pattern's cycle), or add a
   bar-aligned switch for triplet-length patterns?
