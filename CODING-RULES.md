# How we write code in this course

Ten habits for every day of Box Office. They're the same ones you met in Part 1; now they guard an app that has to survive a whole night at the gate.

1. **Know what "done" means before you type.** Read the day's README to the end and the failing tests. Say in one line what will be true when you finish ("all 9 tests pass, and the CLI prints the report"). If a step is unclear, ask, or check "When you're stuck", before guessing.
2. **Write only what the step asks.** No extra options, classes or "while I'm here" features. A good idea goes in a note, or in the day's stretch step.
3. **Keep methods small and clear.** A method that scrolls off the screen probably does two jobs. Before you finish, delete anything you added and don't use: old attempts, commented-out code, unused imports.
4. **Change only what the TODO needs.** Don't rename or reformat code around it. Match its style: its names, its records, its `StoreException`.
5. **A bug gets a failing test first.** Write a test that fails *because of* the bug, watch it fail, fix the code, then run every test. Days 113, 118 and 127 work this way.
6. **Check before you call it done.** From the day's `starter/` folder, then from the top of the repository:

   ```bash
   ../../mvnw test
   ```

   ```bash
   tools/check-style.sh
   ```

   `BUILD SUCCESS` from the first; `Audit done.` with no `[ERROR]` lines from the second. (On Windows, `..\..\mvnw.cmd test`, and run `check-style.sh` in Git Bash or WSL.) The compiler is the type checker: fix its warnings too.
7. **Comments say why, not what.** A public method's Javadoc says what it takes, what it returns and when it throws, so nobody needs to read its body:

   ```java
   /**
    * Sells one ticket of this type to this buyer, at the type's price.
    *
    * @param typeCode "REG" or "VIP"
    * @param buyer    the name printed on the ticket
    * @return Sold with the ticket, SoldOut when none are left, or Rejected and why
    * @throws StoreException if the sale couldn't be saved
    */
   ```
8. **Don't hide errors.** Catch the specific exception, then handle it or pass it on. Never leave a `catch` empty, never catch `Exception`, never "handle" it with `printStackTrace()`. Close what you open with try-with-resources.

   ```java
   } catch (SQLException e) {                                   // good: the caller hears about it
       throw new StoreException("Couldn't record ticket " + ticket.code(), e);
   }
   } catch (Exception e) { e.printStackTrace(); }               // bad: the gate thinks it sold
   ```

   Catching `RuntimeException` is right in a few places the course shows you (rolling back a sale then rethrowing, keeping an SMS batch going, always answering M-Pesa with 200), and none of them hides the failure: each one rethrows it, records it or logs it.
9. **Tests check, they don't print.** Every test ends in an assertion, like `assertThat(Money.ofKes(1_500).plus(Money.ofKes(5_000))).isEqualTo(Money.ofKes(6_500))`. A `System.out.println` in a test proves nothing: it passes whatever it prints.
10. **Commit small, commit clean.** One finished step per commit, with a message that says what changed and why: `feat: the gate refuses a ticket scanned twice`, not `update`. Stage files by name (`git add core/src/...`), not `git add .`, and check `git status` first: never commit `.env`, API keys, `gate.db`, `target/` or PDFs you made. `.gitignore` covers most of them; your eyes cover the rest.
