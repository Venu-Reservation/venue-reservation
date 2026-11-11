# Story 10: Kotlin basics: values, types and "maybe empty"

**Kind:** Understand (no code to merge) · **Size:** S · **Time:** 2 to 3 hours

## In plain words

Kotlin is the language our backend is written in. You've already used it without seeing all
its rules. This story teaches the handful of basics that appear on almost every line of our
code, using the **Kotlin Playground**, a website where you can run Kotlin without installing
anything or touching the project.

The most important idea is the last one: in Kotlin, a value that might be **missing** has to say
so, with a `?`. That one rule prevents a whole family of crashes.

## New words

- **`val`**: a value that can't change after it's set (like a label you write in pen).
- **`var`**: a value that can change (written in pencil). Prefer `val`.
- **Type**: what kind of thing a value is: `String` (text), `Int` (whole number), `Boolean`
  (true or false), `Double` (number with decimals).
- **`null`**: "nothing here". A missing phone number, for example.
- **Nullable type** (`String?`): a type that's allowed to be `null`. A plain `String` never is.

## Before you start (30 minutes)

- Kotlin "Basic syntax", the sections on **Variables**, **String templates** and
  **Conditional expressions**: <https://kotlinlang.org/docs/basic-syntax.html>
- Kotlin "Null safety", up to and including "Elvis operator":
  <https://kotlinlang.org/docs/null-safety.html>

## Steps

Do these in the Playground: <https://play.kotlinlang.org>. Each exercise starts inside `fun main() { }`.

1. **val and var.** Create `val city = "New York"` and `var guests = 10`. Change `guests` to 12
   and print it with `println("Guests: $guests")`. Now try to change `city`. What does the error
   say?
2. **Types.** Write `val capacity: Int = 120` and `val hourlyRate: Double = 320.5`. Try
   `val capacity2: Int = "120"`. Read the error: Kotlin checks types before the code runs.
3. **if as a value.** Write `val size = if (capacity > 100) "large" else "small"` and print it.
4. **Maybe empty.** Write `val phone: String? = null`. Try `println(phone.length)`. Read the
   error, then try these three fixes and print each:
   - `phone?.length` (the "safe call": gives `null` instead of crashing)
   - `phone?.length ?: 0` (the "Elvis operator": gives 0 when it's `null`)
   - `if (phone != null) phone.length else 0`
5. **Why it matters.** Write `val name: String = null`. Kotlin refuses. Explain in one sentence
   why that's a good thing.
6. **Find them in our code.**
   - On `main`, open `Reservation.kt` and `Venue.kt`. Find a `var` and its type. Notice there's
     no `val` in either: every field was written as `var`, so any of them can be changed. Which
     ones should never change once a venue exists? (Later stories make those `val`.)
   - Open `VenueRepositoryTest.kt` from your PR #13. Find the `val`s, and the `!!` in
     `savedVenue.id!!` and the `?.` in `foundVenue?.name`. What do they mean, now that you know?
     Which one would crash if the value were `null`?

## How you know it works

- Every exercise runs in the Playground, and you can explain each error you saw.

## Check your understanding

Post your answers as a comment on this issue:

1. When would you use `var` instead of `val`?
2. What's the difference between `phone?.length` and `phone!!.length`? Which one can crash?
3. In your own words: why does Kotlin make you write `String?` for something that might be missing?

## Peek at the answer

On `feature/adding_db_flyway`, open `src/main/kotlin/com/venue/users/User.kt`. Find `phone`: it's
`String?` because a user may not have given one. Compare it with `email`, which is a plain
`String`. Every user must have an email.

## Stuck?

- **The Playground shows nothing**: make sure your code is inside `fun main() { ... }` and that
  you pressed the Run button (the triangle).
