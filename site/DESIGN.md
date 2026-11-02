---
name: Box Office in 30 Days, the course site
colors:
  canvas: "#efede8"
  canvas-soft: "#e4e1d9"
  sheet: "#f9f8f5"
  ink: "#1b2030"
  ink-mute: "#4f5566"
  hairline: "#d6d2c8"
  primary: "#24467d"
  gold: "#e0a91c"
  gold-soft: "#f6e3b0"
  gold-ink: "#1b2030"
  stamp: "#ad3626"
  pass: "#22683e"
  pass-soft: "#d6ebdc"
  night: "#121a2c"
  night-ink: "#e9edf5"
  night-mute: "#9aa6c0"
  paper: "#fdfcf8"
  phase-1: "#ad3626"
  phase-1-soft: "#f4dcd4"
  phase-2: "#9c4f12"
  phase-2-soft: "#f5e1cc"
  phase-3: "#7d5d00"
  phase-3-soft: "#f1e5bd"
  phase-4: "#1b6763"
  phase-4-soft: "#d3e9e5"
  phase-5: "#24467d"
  phase-5-soft: "#dce4f1"
  phase-6: "#5b3a8c"
  phase-6-soft: "#e6ddf2"
typography:
  display-xl:
    fontFamily: "'Bricolage Grotesque', 'Arial Narrow', system-ui, sans-serif"
    fontSize: 6.6rem
    fontWeight: 400
    lineHeight: 0.95
    letterSpacing: -0.035em
  heading-lg:
    fontFamily: "'Bricolage Grotesque', 'Arial Narrow', system-ui, sans-serif"
    fontSize: 3.4rem
    fontWeight: 400
    lineHeight: 1.02
    letterSpacing: -0.025em
  heading-md:
    fontFamily: "'Bricolage Grotesque', 'Arial Narrow', system-ui, sans-serif"
    fontSize: 1.45rem
    fontWeight: 450
    lineHeight: 1.15
    letterSpacing: -0.01em
  body-md:
    fontFamily: "'Instrument Sans', system-ui, sans-serif"
    fontSize: 1.125rem
    fontWeight: 400
    lineHeight: 1.6
  button-md:
    fontFamily: "'Instrument Sans', system-ui, sans-serif"
    fontSize: 1.05rem
    fontWeight: 600
    lineHeight: 1.2
  caption:
    fontFamily: "'Instrument Sans', system-ui, sans-serif"
    fontSize: 0.9rem
    fontWeight: 400
    lineHeight: 1.45
  stub:
    fontFamily: "'Bricolage Grotesque', 'Arial Narrow', system-ui, sans-serif"
    fontSize: 0.95em
    fontWeight: 500
    lineHeight: 1.45
    fontFeature: tnum
  code:
    fontFamily: "'JetBrains Mono', ui-monospace, monospace"
    fontSize: 0.86em
    fontWeight: 400
    lineHeight: 1.65
rounded:
  none: 0px
spacing:
  unit: 8px
  frame-phone: 16px
  frame: 32px
  section: clamp(96px, 12vw, 176px)
components:
  button-primary:
    backgroundColor: "{colors.gold}"
    textColor: "{colors.gold-ink}"
    typography: "{typography.button-md}"
    rounded: "{rounded.none}"
    padding: 0 26px
  button-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.button-md}"
    rounded: "{rounded.none}"
    padding: 0 26px
  stub:
    backgroundColor: "{colors.gold}"
    textColor: "{colors.gold-ink}"
    typography: "{typography.stub}"
    rounded: "{rounded.none}"
  status-done:
    backgroundColor: "{colors.pass-soft}"
    textColor: "{colors.pass}"
    typography: "{typography.caption}"
    rounded: "{rounded.none}"
  status-next:
    backgroundColor: "{colors.gold-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.none}"
  here-mark:
    backgroundColor: "{colors.stamp}"
    textColor: "{colors.sheet}"
    typography: "{typography.caption}"
    rounded: "{rounded.none}"
  lesson:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.none}"
  sidebar:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink-mute}"
    typography: "{typography.body-md}"
    rounded: "{rounded.none}"
  search-input:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.none}"
    padding: 12px 16px
  code-block:
    backgroundColor: "{colors.night}"
    textColor: "{colors.night-ink}"
    typography: "{typography.code}"
    rounded: "{rounded.none}"
    padding: 18px 20px
  receipt:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.code}"
    rounded: "{rounded.none}"
    padding: 22px 20px 34px
  closing-band:
    backgroundColor: "{colors.night}"
    textColor: "{colors.night-mute}"
    typography: "{typography.display-xl}"
  phase-1-band:
    backgroundColor: "{colors.phase-1-soft}"
    textColor: "{colors.phase-1}"
    typography: "{typography.heading-lg}"
  phase-2-band:
    backgroundColor: "{colors.phase-2-soft}"
    textColor: "{colors.phase-2}"
    typography: "{typography.heading-lg}"
  phase-3-band:
    backgroundColor: "{colors.phase-3-soft}"
    textColor: "{colors.phase-3}"
    typography: "{typography.heading-lg}"
  phase-4-band:
    backgroundColor: "{colors.phase-4-soft}"
    textColor: "{colors.phase-4}"
    typography: "{typography.heading-lg}"
  phase-5-band:
    backgroundColor: "{colors.phase-5-soft}"
    textColor: "{colors.phase-5}"
    typography: "{typography.heading-lg}"
  phase-6-band:
    backgroundColor: "{colors.phase-6-soft}"
    textColor: "{colors.phase-6}"
    typography: "{typography.heading-lg}"
  table-row:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
  link:
    textColor: "{colors.primary}"
    typography: "{typography.body-md}"
---

# Box Office in 30 Days: the course site

## Overview

A reading site for a 30-day Java course in which you build one app, the gate laptop for one Saturday night: Kilele Nights at Ngong Racecourse, 18:00 to 04:00. The home and syllabus are **pages** (read and scrolled); the day page is a page with one working part, the checklist.

**The idea.** On this site, the gate's 80 mm till-roll receipt becomes the hero picture: run the finished app in the terminal and a real receipt prints under it, QR and all. The night itself becomes the route: six phases coloured from sunset to 04:00. (Competitor test: no other Java course has this receipt or this night.)

**The live thing.** "Sell some tickets" replays a real run of the finished CLI. Each sale reprints the receipt with that ticket's real `Receipt.forTicket` text and a QR made by the same ZXing call as `QrCodes.encode`.

### Key characteristics (the signature moves)

1. **The till roll.** The receipt is the only picture on the home page and the only place outside code where a monospace face appears, because that's how a thermal printer prints. Don't add other illustrations, icons or stock decoration.
2. **Display at weight 400.** All headings are Bricolage Grotesque at 400 (heading-md 450), tracked tight. Don't set display type bold.
3. **The night as six flat fields.** The route is six full-width bands, one per phase, each in its phase's pale tint, in the order of the night. Don't put phases in cards.
4. **Square corners.** Radius 0 everywhere. The only shaped edges are real ones: the stub's perforation and the receipt's torn foot. Don't introduce a soft radius.
5. **Tone, not lines.** Sections are separated by a change of ground, never by rules, dashed lines, borders or resting shadows. Rules exist only between table rows and around inputs.

## Colors

Dark mode keeps every role and changes the values: canvas `#0f121a`, canvas-soft `#151a25`, sheet `#1a202d`, ink `#e8ebf2`, ink-mute `#a2aabb`, primary `#93b4ec`, gold `#ebb637`, stamp `#f08a74`, pass `#74d19b`, night `#0a0d15`, paper `#ebe8e0`, and the phases red `#f08a74`, orange `#f0a46a`, yellow `#e6c35a`, teal `#6cccc0`, blue `#93b4ec`, violet `#bfa5ef` on dark tints of themselves.

A triad of the painter's primaries (red, yellow, blue) at 60-30-10, with the wheel's in-between hues for the phases.

- 60%: `canvas`, `canvas-soft`, `sheet`. Grey with the faintest warmth, so the ink and accents carry the hue.
- 30%: `primary` night blue. Allowed on: links, the focus ring, the terminal and code grounds, the dark closing band.
- 10%: `gold`. Allowed on: ticket stubs, the one primary button per view, "Up next". Never text on a light ground (2.1:1).
- A trace of `stamp` red. Allowed on: the "You are here" mark, blockquote and scene rules.
- `phase-1` to `phase-6`. Allowed on: phase bands (as pale tints), a phase's own heading mark, the day page's scene rule and phase label. A phase colour always sits next to its phase number or name, never alone.
- `pass` green means done. Nothing decorative is green.

Every text pair measures at least 4.5:1 in both modes.

## Typography

Display and heading sizes are fluid; the tokens hold the largest size, and the table gives the full range.

| Role | Size | Weight | Use |
|---|---|---|---|
| display-xl | clamp(3rem, 7vw, 6.6rem) | 400 | The home h1 only, always two lines: "Box Office / in 30 Days" |
| heading-lg | clamp(2rem, 4.2vw, 3.4rem) | 400 | Section and page titles, phase names |
| heading-md | 1.45rem | 450 | Sub-headings, day titles in lists |
| body-md | 1.125rem / 1.6 | 400 | Running text, in full ink |
| caption | 0.9rem | 400 | Helper text, in ink-mute |

No eyebrow labels, no uppercase tracked labels, no index numbers. Information that used to sit in an eyebrow goes into the heading or the first sentence.

## Layout

Container 1180px, outer frame 16px on phones and 32px from 768px, equal on both sides. Sections 96–176px apart. Neighbouring home sections never share a layout: split hero, two-column list, code beside text, full-width bands, four columns, dark close.

## Elevation and depth

Depth is tone only: canvas-soft (down), canvas, sheet (up), night (the one dark band). No shadows at rest. The receipt has a single tinted shadow because it hangs off the terminal.

## Components

- **Buttons:** one primary (gold) per view; the rest are secondary (2px ink outline) or text links.
- **Stub:** the day number on a gold ticket stub, tabular figures.
- **Status:** pale tint with dark text of the same hue, sentence case: "Done", "Up next", "40%".
- **Navigation:** wordmark (stub "30" + "Box Office") and three or four plain links, on the canvas, no blur, no bar line.
- **Footer:** the night band, the name at full width.

States: buttons and links have hover, pressed and a 3px focus ring. The checklist is the working part: checked items fade to ink-mute with a line through. The lesson has a loading line and an error state that says how to fix it.

## Motion

Tokens: `--dur-fast` 120ms (button press, checkbox), `--dur-base` 200ms (fades), `--dur-slow` 320ms (the receipt), `--ease-out` cubic-bezier(.2,.8,.2,1) for everything that enters, `--ease-in` cubic-bezier(.4,0,1,1) for anything that leaves.

Home sections enter once (heading, then text, then picture, 70ms apart, rising 24px). The receipt slides out of the terminal on each sale (duration-slow, ease-out). Everything else is still. `prefers-reduced-motion` turns all of it off, and nothing is hidden if the script fails.

## Do's and Don'ts

- Do keep every headline to two lines at every width.
- Do show real output from the course (CLI lines, receipts, folders), never made-up product screens.
- Don't add eyebrows, pill badges, icon cards, gradients, glass, blur or glows.
- Don't use monospace outside code, commands, the terminal and the receipt.
- Don't use gold for text on light grounds, or green for decoration.

## Responsive behaviour

| Width | Change |
|---|---|
| ≥ 1024px | Hero two columns; route bands two columns (phase left, days right) |
| 768–1023px | Hero stacks, receipt beside the terminal; kit two columns |
| < 768px | Everything one column; nav keeps the wordmark and Syllabus; frame 16px; touch targets 44px |

## Known gaps

- No photographs: the course has none of its own, and the receipt and terminal are real output, so they carry the pictures.
- No dark-mode receipt paper: it stays light on purpose so the QR scans from a screen.
