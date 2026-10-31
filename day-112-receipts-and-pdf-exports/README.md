# Day 112: Receipts and PDF exports

> "Can I get a receipt?" The queue waits while you find a pen. And at 04:00 the organiser wants the report on her phone, not on your screen.

Watch the video: *(not recorded yet)*

## The brief

Two things leave the gate on paper (or as a file). The buyer wants a **receipt**: proof of what they paid, with the ticket's QR code on it. The organiser wants the **night's report** as a file she can email to the promoter, print at the office, or keep.

Many gates have a small **till printer**: the kind at a supermarket, printing on a roll of paper 80 mm wide. Some gates have no printer at all. So the gate does both:

- **Print** (Ctrl+P): sends the last receipt to whatever printer the laptop has.
- **Save PDF** (Ctrl+S): saves the receipt as a PDF file, which works with no printer at all.
- **`report --pdf report.pdf`**: the CLI saves the night's report as a PDF too.

```
KILELE NIGHTS
Sat 14 Nov, 18:00
--------------------------------
Ticket  VIP-0001
Type    VIP
Buyer   Wanjiku Kamau
Price   KES 5,000
Sold    Sat 14 Nov, 21:00
--------------------------------
Show this at the gate.
One entry per ticket.
```

Under the words, the PDF receipt has the ticket's QR code, so the buyer can scan in at the gate from the paper.

## What you'll learn

- How to lay out text for a narrow till roll: 32 characters a line
- How **Apache PDFBox** writes a PDF: a page, a content stream, and text commands
- How a PDF page is measured: in **points**, starting from the **bottom-left** corner
- How the QR code is drawn on the PDF as small filled squares
- How a test reads a PDF back to check what's in it
- How JavaFX prints with `PrinterJob`, and why that part has no test
- How `FileChooser` asks where to save a file

## New words

- **Receipt**: a short printed note of a sale: what, who, how much, when.
- **Till printer** (or thermal printer): a small printer that burns text onto a roll of heat-sensitive paper. No ink.
- **PDF**: a file format that looks the same on every computer, phone and printer.
- **PDFBox**: an Apache library that writes and reads PDF files from Java.
- **Point**: the unit a PDF measures in. 72 points make one inch (25.4 mm).
- **Content stream**: the list of drawing commands for one PDF page: "move here, use this font, write this text".
- **Leading** (say "ledding"): the distance from one line of text to the next.
- **Monospaced font** (mono font): a font where every letter is the same width, like Courier, so columns line up.
- **`PrinterJob`**: JavaFX's way to send something to a printer.
- **`FileChooser`**: the system's "Save as" window, opened from JavaFX.

## What's already written

From Days 101–111: everything up to power-cut-proof saving, working, with tests. New today, already done:

- The poms add **PDFBox** (`org.apache.pdfbox:pdfbox`, version 3.0.5) to `core`.
- `Receipt.java` (in `core`): `WIDTH = 32`, the dashed `RULE`, and `field(label, value)`, which lays out `Buyer   Wanjiku Kamau` and cuts a long value to fit with `…`. You write `forTicket`.
- `PdfExport.java` (in `core`): `writeReceipt` (one receipt on a till-roll-sized page, with the QR under the text), `writeReport` (the report on an A4 page) and `drawQr`. You write `writeLines`, which both of them use.
- `ReportCommand.java` (in `cli`): a new `--pdf` option. You write the two lines that use it.
- `ReceiptPrinter.java` (in `desktop`): builds the receipt as labels plus the QR image, and prints it with `PrinterJob`. Complete.
- `DashboardModel.lastSoldTicketProperty()` and `event()`: the last ticket sold, for its receipt. Complete.
- `dashboard.fxml` and `DashboardController`: **Print** and **Save PDF** buttons under the QR on the Sell tab, Ctrl+P and Ctrl+S, and a message line. Complete.

New tests: `ReceiptTest` (5), `PdfExportTest` (4), one more in `GroupSaleTest` (from Day 111's fix), one more in `BoxOfficeCliTest` and one more in `DashboardModelTest`.

## What you'll use

**Why `Receipt` returns lines, not a PDF.** `Receipt.forTicket` gives a `List<String>`. The PDF writes those lines, and the printer prints the same lines as labels. Write the words once, use them in two places, and test them with no PDF or printer at all.

**Why 32 characters.** An 80 mm till roll, with a mono font at a readable size, fits about 32 letters across. Longer lines wrap or get cut off by the printer. So every line must fit, and `field` cuts a long name: `Buyer   Wanjiku Kamau Njoroge Wa…`.

**Upper case, safely.** `"Kilele Nights".toUpperCase(Locale.ROOT)` is `"KILELE NIGHTS"`. `Locale.ROOT` again, as on Day 105.

**How a PDF page is measured.** In points, 72 to the inch. `PdfExport` has a small `mm(...)` helper, so an 80 mm roll is `mm(80)`, about 227 points. And **(0, 0) is the bottom-left corner**, not the top-left like a JavaFX window. So the first line goes near the top (a big y), and each next line has a smaller y.

**Writing text on a page.** Every piece of text goes between `beginText()` and `endText()`. Inside, you pick a font, say where to start, and write:

```java
content.beginText();
content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 11);
content.newLineAtOffset(72, 700);   // one inch from the left, near the top
content.showText("Hello, gate");
content.endText();
```

`setLeading(12)` sets how far `newLine()` moves down. Then `showText(...)` followed by `newLine()`, again and again, writes line after line. Courier is one of the 14 fonts every PDF reader already has, and it's monospaced, so the report's columns still line up.

**The QR on paper.** `drawQr` doesn't paste a picture. For each dark square in the grid from `QrCodes.encode`, it adds a small rectangle with `addRect`, then fills them all at once with `fill()`. The QR stays sharp at any size, and the file stays tiny.

**How the tests read a PDF.** `PdfExportTest` opens the file with `Loader.loadPDF`, the way a PDF app would, and pulls the words back out with `PDFTextStripper`. Then it checks they contain `KILELE NIGHTS` and `Ticket  VIP-0001`. Every PDF file also starts with the bytes `%PDF-`, and the tests check that too.

**Printing has no test.** `ReceiptPrinter.print` opens the system's print dialog. A test can't press its buttons, and the computer that runs CI has no printer. So that code stays small, and it returns a message that says what happened: `Printed VIP-0001`, `Printing cancelled`, or `No printer found. Save a PDF instead (Ctrl+S).` Test it yourself, by hand.

## Steps

1. **Open the starter and run the tests.** `cd day-112-receipts-and-pdf-exports/starter`, then `../../mvnw test`. You should see `Tests run: 103, Failures: 0, Errors: 9` in `core` and `BUILD FAILURE`. The errors say `Not written yet: Day 112, step 3` and `step 4`. `cli` and `desktop` are `SKIPPED`.

2. **Look around.** Open `starter/core/src/main/java/com/boxoffice/core/PdfExport.java`. Read `writeReceipt`: it makes a document, adds one page the size of a till roll, opens a content stream, writes the lines, then draws the QR under them. Find `drawQr`, and see how `top - (y + 1) * square` turns "row y from the top" into a PDF y that counts from the bottom. Then open `starter/desktop/src/main/java/com/boxoffice/desktop/ReceiptPrinter.java`. Nothing to write.

3. **Lay out the receipt.** Open `Receipt.java` and find `forTicket`. `typeName` is already worked out (`VIP`, or `Regular` for `REG`). Replace the `throw` with `lines.add(...)` calls, in the order the class comment shows:
   - the event's name in capitals, then `NairobiTime.format(event.startsAt().toInstant())`;
   - `RULE`;
   - `field("Ticket", ticket.code())`, then `field("Type", typeName)`, then `field("Buyer", ticket.buyer())`;
   - `field("Price", ...)`: `"Guest"` when `ticket.price().equals(Money.ZERO)`, otherwise `ticket.price().format()`;
   - `field("Sold", NairobiTime.format(ticket.soldAt()))`;
   - `RULE`, `"Show this at the gate."` and `"One entry per ticket."`;
   - `return lines`.

   Run `../../mvnw test -Dtest=ReceiptTest -Dsurefire.failIfNoSpecifiedTests=false`. All 5 pass.

4. **Write text on a PDF page.** In `PdfExport.java`, find `writeLines`. Replace the `throw`, following the TODO:
   - `beginText()`, then `setFont(...)` with Courier and the `fontSize` you were given;
   - `setLeading(LEADING)`, then `newLineAtOffset(MARGIN, top)`;
   - for each line: `showText(printable(line))`, then `newLine()` (`printable`, already written above `writeLines`, swaps letters Courier can't draw);
   - `endText()`;
   - return `top - LEADING * lines.size()`, the y just under the last line. `writeReceipt` draws the QR there.

   Run `PdfExportTest`: all 4 pass. Now `core` is done (103), so the `cli` tests run, and `savesTheReportAsAPdfToo` fails.

5. **Save the report as a PDF from the CLI.** Open `starter/cli/src/main/java/com/boxoffice/cli/ReportCommand.java`. Replace the TODO with an `if`: when `pdf` isn't `null` (the option was given), call `PdfExport.writeReport(report, pdf)`, then `out.println("Saved " + pdf)`. Keep it above `out.flush()`.

6. **Run all the tests.** `../../mvnw test`. You should see 144 tests in all and `BUILD SUCCESS`.

7. **Look at the PDFs.** Follow "Run it and test it" below: save `report.pdf` and open it. Then open the window, sell a ticket, and press **Ctrl+S** to save its receipt. Open that PDF and zoom in on the QR: it stays sharp. Point your phone's camera at it. Then press **Ctrl+P**. With a printer you get the print dialog; without one, the message under the buttons says `No printer found. Save a PDF instead (Ctrl+S).`

## Run it and test it

From the day's `starter` folder:

```
cd day-112-receipts-and-pdf-exports/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=PdfExportTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=PdfExportTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **103 tests** in `core` (including PdfExport 4, Receipt 5), **10** in `cli` and **31** in `desktop` (DashboardModel 17), **144** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Save the report as a PDF.** `../../mvnw package`, then (Windows: `cli\target\boxoffice.jar`):

```
$ java -jar cli/target/boxoffice.jar sell VIP "Wanjiku Kamau"
Sold VIP-0001 to Wanjiku Kamau for KES 5,000

$ java -jar cli/target/boxoffice.jar report --pdf report.pdf
Kilele Nights · Sat 14 Nov, 18:00
Type        Paid  Guests   Left        Takings
Regular        0       0    500          KES 0
VIP            1       0     49      KES 5,000
Total          1       0    549      KES 5,000
Saved report.pdf
```

`report.pdf` is in the folder you ran it from. Open it with any PDF app (double-click it, or `xdg-open report.pdf` on Linux, `open report.pdf` on a Mac, `start report.pdf` on Windows). Don't commit it to Git.

**The window:** `java -jar desktop/target/boxoffice-desktop.jar`. Print and Save PDF are greyed out until the first sale: there's nothing to print yet. Save PDF suggests a name like `VIP-0001.pdf`.

## When you're stuck

- **`Not written yet: Day 112, step 3`** or **`step 4`**: that method still has its `throw`. Note that `PdfExportTest` needs step 3 too: a receipt PDF writes `Receipt.forTicket`'s lines.
- **`usesTheTypesNameNotItsCode` shows `"Type    REG"`**: use `typeName`, not `ticket.typeCode()`.
- **`saysGuestInsteadOfKesZero` shows `"Price   KES 0"`**: check for `Money.ZERO` and write `"Guest"`.
- **`laysOutATicketForTheTillPrinter` fails on the first line**: the name must be in capitals, with `toUpperCase(Locale.ROOT)`.
- **`IllegalStateException: Must call setFont() before showText()`**: set the font after `beginText()` and before the first `showText`.
- **`IllegalStateException: Error: must call beginText() before newLineAtOffset()`**: `beginText()` comes first.
- **The text is at the bottom of the page, or off it**: y counts up from the bottom. Start at `top`, not at 0.
- **All the lines are on top of each other**: `setLeading(LEADING)` is missing, so `newLine()` doesn't move down.
- **`savesTheReportAsAPdfToo` says `Expecting actual: "Kilele Nights · Sat 14 Nov, 18:00 ...` to contain `Saved ...`**: step 5 isn't done, or `out.println` is missing.
- **`U+0169 ('utilde') is not available in the font Courier, encoding: WinAnsiEncoding`**, from `aNameWithKikuyuLettersStillPrints`: you called `showText(line)` instead of `showText(printable(line))`. The 14 built-in PDF fonts only know Western European letters, and Kikuyu's `ũ` and `ĩ` aren't among them. `printable` (already written) turns `ũ` into `u` so the receipt still prints. A real fix that keeps the letters is to load a full font file with `PDType0Font.load(...)`.
- **Ctrl+P says `No printer found`**: that's correct when the laptop has no printer set up. Use Ctrl+S.
- **Ctrl+P and Ctrl+S do nothing on a Mac**: use Cmd+P and Cmd+S.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/Receipt.java` (or `PdfExport.java`, `solution/cli/src/main/java/com/boxoffice/cli/ReportCommand.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why `Receipt` returns lines instead of a PDF
- [ ] Every receipt line fits in 32 characters
- [ ] Can say where (0, 0) is on a PDF page, and what a point is
- [ ] Wrote `writeLines` with `beginText`, `setFont`, `showText` and `endText`
- [ ] Saved `report.pdf` from the CLI and opened it
- [ ] Saved a receipt PDF from the window and scanned its QR with a phone
- [ ] Can say why printing has no automatic test
- [ ] All 144 tests pass
- [ ] Committed your work to Git (not `gate.db` or the PDFs)

## Next

The app sells, checks in, survives power cuts and prints. Tomorrow it becomes **version 1**: you write tests that drive the whole sell screen from the keyboard, the way gate staff use it, and they catch two bugs before anyone at a gate does.
