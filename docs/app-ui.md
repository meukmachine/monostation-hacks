# App UI layout

A design for the web app at `flasher/` once it does more than flash firmware.

## What the app will do

| Area | Feature | Priority | Device path |
|------|---------|----------|-------------|
| Firmware | Flash a build (release, test build, stock 1.2) | done (#63) | bootloader, `docs/update-protocol.md` |
| Packs | Upload a `.monostationpack`: its patches, its sessions, or both | patches: done, CLI and page (Firefox) tried on the unit; sessions: done, CLI and page (Firefox) tried on the unit (session 1 and INIT, 11 s, read-back matches) | bootloader `0x77`/`0x79` upload of slots 1–64 (`docs/session-transport.md` *Upload*), for patches and sessions |
| Packs | Single-patch `.syx` and single-session files | next | as above |
| Packs | Back up the unit (64 patches, 32 sessions) to a `.monostationpack` | done: CLI and page (Firefox) tried on the unit (#1) | bootloader `0x78` dump: patches at `0x2000`, sessions at `0x12000` |
| Packs | Read one session from the unit | later (#56) | bootloader `0x78` takes any start and length |
| Packs | Slots 64–127, once the panel can reach them | later (#5, #27) | type-1 slot write (works on slot 64) |
| Packs | Patch editor | low | CC/NRPN per parameter (`tools/patchbank.py` `PARAMS`), type-0 current patch, type-1 slot write |
| Packs | Sequence editor (steps, chains, automation, #20 sequences) | low | session image (`docs/session-format.md`), offline; sent with the session upload |
| Settings | System settings record (MIDI channels, clock, …) | maybe (#53) | settings record, `docs/session-transport.md` |
| Firmware settings | Mod settings, tweaks and feature flags: drive amounts (#61) first, later scales #28, microtuning #40, CV #47, MIDI routing #49 | built (#68): page Firmware settings tab and CLI `--settings`, not yet tried on the unit | mod settings store, `docs/mod-settings.md` |

Out of scope: sample or audio work.

## Packs

A `.monostationpack` is a ZIP holding `patches.syx` (64 × 136-byte patches) and/or
`sessions.syx` (a session dump, `docs/session-transport.md`); Components accepts either
one alone (`frontend/utils/monostation/is-v1-pack`). Components sends a whole pack through
the bootloader, and so do we (see *Decisions (2026-11-21)*).

## Principles

- One page, no account, nothing uploaded anywhere: files stay in the browser.
- One device connection shared by every area, shown at the top.
- One transfer at a time: while anything talks to the unit, every other action button is
  disabled.
- Nothing on the unit is overwritten without a backup first (ticked by default).
- The log is shared and collapsed by default; errors also show next to the action.

## Layout

```
┌────────────────────────────────────────────────────────────────────┐
│ [logo] Custom firmware for Circuit Mono Station  ● Connected       │
│ Unofficial. Nothing leaves your browser.      app · firmware 751   │
│                                               [Connect]            │
├───────┬──────────┬───────────────────┬───────┬──────┐              │
│ Start │ Firmware │ Firmware settings │ Packs │ Help │              │
├───────┴──────────┴───────────────────┴───────┴──────┴──────────────┤
│                                                                    │
│   tab content                                                      │
│                                                                    │
├────────────────────────────────────────────────────────────────────┤
│ ▸ Log (3)                                                          │
└────────────────────────────────────────────────────────────────────┘
```

**Header / device bar.** Connect asks for MIDI with SysEx once. The status shows: not
connected / app running (firmware version from `0x70`) / in bootloader (bootloader
version) / busy (what, with a progress bar). When an action needs the bootloader, the app
reboots into it and back by itself; the button steps appear only if no unit answers.

**Tabs.** Start holds the use-at-your-own-risk warning (back up first, stock 1.2 is the way back) and what each tab does; the first visit opens it, afterwards the last tab is remembered. Help, the last tab, covers browsers, a unit that doesn't answer, a failed flash, and where to talk (the Facebook group) or report problems (GitHub issues). Settings
has the mod settings. The page is titled "Custom firmware for Circuit
Mono Station, by Meuk Machine", with the Meuk Machine logo.

### Firmware tab

The current page, unchanged in content:

```
1. Stock firmware     [Choose monostation-firmware-800.syx]  ✓ stock 1.2
2. Build              ○ Mods 752 (release)   ○ Test 753   ○ Stock 1.2
3.                    [Flash]   ████████░░░░ 2900 / 4250
```

### Packs tab

Three views: **[Library | Patch edit | Sequence edit]**. The editors appear when they are
built; until then the tab is the library alone. A link can open a view directly
(`#packs/patch-edit`).

#### Library

```
[Choose .monostationpack / .syx]  Roboo.monostationpack: 64 patches, no sessions
[Back up unit…]  → monostation-backup-2026-11-19.monostationpack

 Patches                                        Sessions
┌ File ─────────────────┐  ┌ Unit ───────────┐  ┌ File ──────┐  ┌ Unit ──────┐
│  1 bass1   Bass  ☑    │─▶│ 8 × 8 slots      │  │  1 … ☑     │─▶│ 32 slots    │
│  2 bass2   Bass  ☑    │  │ changed slots    │  │  …         │  │             │
│ 33 Initial Patch ☐    │  │ highlighted      │  │            │  │             │
│ [All][None][Skip init]│  └──────────────────┘  └────────────┘  └─────────────┘
└───────────────────────┘
 Place: (•) same slots  ( ) from slot [ 1 ]        ☑ Back up the unit first
 [Send 32 patches and 16 sessions]   ████░░░░ sessions 3 / 16
```

- The file columns list the pack's patches (name, category) and sessions (name), each
  with a tick box. `Initial Patch` and default sessions are recognised and unticked by
  "Skip init", so a half-empty pack doesn't wipe slots.
- The unit columns fill from the backup dump; without one, slot numbers only. Slots that
  will change are highlighted. Drag-and-drop onto slots comes later.
- **Send** order: backup (bootloader dump of patches and sessions, downloaded as a
  `.monostationpack`), then in the bootloader: read slots 1–64, put the patches in, upload
  them, read them back and compare; then the session upload; back to the app.
- A pack with no sessions (like Roboo) shows only the Patches half.

#### Patch edit (low priority)

```
Edit: Initial Patch (live)     [Get from unit] [Open…]  [Save to slot ▾] [Save file]
┌ Osc 1 ──────┐┌ Osc 2 ──────┐┌ Mixer ─────┐┌ Filter ────┐┌ Env ───────┐
│ wave ▾ range│                                                       │
│ coarse fine │      knobs/sliders grouped as on the panel             │
│ pw  glide   │                                                       │
└─────────────┘└─────────────┘└────────────┘└────────────┘└────────────┘
┌ LFO ────────┐┌ Distortion ─┐┌ Mod matrix: source × destination grid ───────┐
```

- Groups follow the panel; ranges, enums and centres come from `PARAMS` (the firmware
  table at `0x0802A62A`).
- Moving a control sends its CC/NRPN, so the unit plays the change at once; the unit's
  own knob moves come back the same way and move the control.
- Parameters that only SysEx reaches (#2) go as a type-0 current patch.
- Also opens a patch from the library without a unit connected; edits there go back to
  the file.

#### Sequence edit (low priority)

Edits a session offline, from a pack, a backup or (after #56) the unit, and saves it to
the pack or sends it with the session upload. There is no live path: the app has no
SysEx for steps.

```
Session 3   Track [Osc 1 | Osc 2 | Mod Seq]   Pattern [1 … 16]   Chain 1–4   Sync 1/16
step   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16
note  C3  –   D#3 –   G3 …                                             (grid)
vel   ▇   ▅   ▇ …
gate  ▬▬  ▬ …
glide ·   ▃ …
lane [cutoff ▾]  automation curve over 16 steps
Sequences (#20): Osc 1: 1–2, 3, 1–2, 4   Osc 2: …
```

- Fields from `docs/session-format.md` (140-byte patterns, automation lanes, chains, the
  #20 sequences at `0x155E0`); `tools/session.py` is the reference decoder.

### Firmware settings tab

Mod settings (`docs/mod-settings.md` *In the app*): [Read from unit] asks the running build
to describe its settings and draws one control per setting, grouped by mod; changes go to
the unit at once; [Save to unit] and [Reset to stock] write the store through the
bootloader. The tab is always shown, since the page can't know the build before MIDI
access; for firmware without settings it says so. Later: the System settings record
(#53).

## Decisions (2026-11-19)

- **Patch upload:** type-1 slot writes to the running app, one per slot. No reboot;
  partial packs and single patches are easy.
- **Session upload:** the bootloader `0x77`/`0x79` upload, as Components does.
- **Backup:** before each upload the app dumps the unit's patches and sessions through
  the bootloader and offers them as a `.monostationpack` download. Ticked by default.
- **Tabs:** Start / Firmware / Firmware settings / Packs / Help under one device bar and a shared log. The patch
  and sequence editors are views inside Packs, not tabs: they work on the pack's
  contents, and three tabs fit a phone screen.
- **Name:** "flasher" until packs ship.
- **Pack contents:** patches and sessions in one Packs tab, since Components packs hold
  both.

## Decisions (2026-11-20)

- **Build order:** backup first (bootloader `0x78` dump of patches and sessions to a
  `.monostationpack`), then uploading.
- **ZIP:** our own code. The directory parsing lives in common Kotlin; inflate/deflate
  is per platform: `DecompressionStream`/`CompressionStream` (`deflate-raw`) in the
  browser, `java.util.zip` on the JVM.
- **Placement:** "same slots" or "from slot N" only; drag-and-drop later.
- **Check:** after the slot writes, request each written slot with type `0x40` in the
  app and compare. No reboot.

## Decisions (2026-11-21)

- **Patch upload through the bootloader**, not slot writes. Slot writes to the running app
  were tried on the unit (Roboo over slots 1–64, 500 ms apart, 35 s): every slot was
  stored, but the unit's cursor moved through each slot as if saving it, every save
  made all pads flicker, it was slow, and touching a pad meanwhile could easily disturb it. Packs now go through the
  bootloader for patches and sessions alike.
- **Check:** the bootloader read-back after the upload (no `0x40` check: a `0x40` request
  returns only the current patch).
- **UI toolkit:** the web page stays plain HTML (`index.html` + Kotlin/JS `Main.kt`);
  Compose is for the desktop apps (#69) only. Eventually everything moves to Compose
  Multiplatform, the page included (Wasm), so web and desktop share one UI. Open then: Web
  MIDI on Wasm (our `MidiAccess` is Kotlin/JS), download size, canvas instead of HTML.

## Notes for building it

- The upload (`flasher/.../Upload.kt` `uploadRange`) follows Components: header and first
  block at once, then one message per new flow-control counter, the trailer last, and the
  unit's `0x7A` CRC must match. `uploadToUnit` does patches and sessions in one bootloader
  visit. Patches: it reads `0x2000`–`0xA000` (slots
  1–64, eight whole 4 KB subsectors), merges, uploads, reads back; 7 s for 64 slots
  (2026-11-21, firmware 751). Bytes 126–511 of a slot hold leftovers on the unit (random
  data in some slots); we zero them, as Components does. Hidden slots 65–128 stay as they
  are. Sessions: each ticked image goes alone to `0x12000 + n × 0x16000` (22 whole
  subsectors) and is read back. Placement as for patches: same numbers, or one after
  another from session N (1–32); INIT (index 32, the template for new sessions) is its
  own row and only ever goes to INIT. Unused sessions carry the `INIT` magic too.
- With no unit on USB at all, the CLI's `--backup` and `--upload` give up after 10 s
  (`FlashOptions.noUnitWaitMs`); a firmware flash still falls back to the button entry,
  which recovering a broken app needs.
- A full session dump is 2.97 MB (11,616 blocks), so the backup needs its own progress
  bar.
- The backup (`flasher/.../Backup.kt`) reads the patches and the 33 session images in
  `0x78` dumps of 4 KB (`--chunk` on the CLI), each retried up to 3 times: the blocks
  carry no index, so a lost block shows up only as a short dump or a bad CRC. Through the
  ALSA sequencer (ktmidi's `alsa` backend) even 4 KB dumps lose 1–2 of 16 blocks, so on
  Linux the CLI backs up through ALSA rawmidi (`RawMidiAccess.kt`, `--backend raw`):
  3 MB in about 15 s, no retries (2026-11-21, firmware 751). It writes the pack as
  Components' v1 format: `patches.syx` (64 type-1 messages, the first 126 bytes of each
  512-byte slot, as Components' `chunkMonostationPatches`) and `sessions.syx` (one `0x77`
  transfer of all 33 images, as in the YBC packs). It then leaves the bootloader with
  `0x61`; the unit comes back in the app by itself.
