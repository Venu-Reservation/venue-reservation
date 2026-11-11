# Story 11: Kotlin classes, data classes and functions

**Kind:** Understand (no code to merge) · **Size:** S · **Time:** 2 to 3 hours

## In plain words

Our backend is mostly **classes** (blueprints for things, like a `Venue`) and **functions**
(actions, like "find venues in a city"). Kotlin has a special kind of class, the **data class**,
for things that just hold information, like the JSON a request sends us. And it lets you pass a
small function around as a value, called a **lambda**, which you'll see whenever we sort or
filter a list.

This story practises all four in the Kotlin Playground, then finds them in our real code.

## New words

- **Class**: a blueprint. `class Venue(val name: String)` describes what every venue has.
  `Venue("The Atrium")` builds one (an *object*).
- **Constructor**: the part in brackets after the class name. It lists what you must give to
  build one.
- **Data class**: a class for holding information. Kotlin writes the boring parts for you:
  comparing two of them, printing them nicely, and copying with changes.
- **Function**: a named action: `fun hourlyTotal(rate: Double, hours: Int): Double = rate * hours`.
- **Lambda**: a small function without a name, written in `{ }`, often passed to another function:
  `venues.filter { it.capacity > 50 }`.

## Before you start (30 minutes)

- Kotlin "Classes", up to "Constructors": <https://kotlinlang.org/docs/classes.html>
- Kotlin "Data classes", the first two sections: <https://kotlinlang.org/docs/data-classes.html>
- Kotlin "Functions", up to "Default arguments": <https://kotlinlang.org/docs/functions.html>

## Steps

Do steps 1 to 5 in the Playground: <https://play.kotlinlang.org>.

1. **A class.** Write `class Room(val name: String, val capacity: Int)` above `fun main()`. In
   `main`, build `val room = Room("Gallery 41", 60)` and print `room.name`. Then print `room`
   itself. What does it show?
2. **A data class.** Change it to `data class Room(...)` and print `room` again. What changed?
   Now build two rooms with the same values and print `room1 == room2` for both the plain class
   and the data class. Why are the answers different?
3. **copy.** With the data class, write `val bigger = room.copy(capacity = 120)` and print both.
   Did `room` change?
4. **A function.** Write `fun total(rate: Double, hours: Int): Double = rate * hours` and print
   `total(320.0, 4)`. Then give `hours` a default (`hours: Int = 1`) and call `total(320.0)`.
5. **Lambdas.** Make a list:
   `val rooms = listOf(Room("Atrium", 120), Room("Loft", 80), Room("Gallery", 60))`. Print:
   - `rooms.filter { it.capacity >= 80 }`
   - `rooms.map { it.name }`
   - `rooms.sortedBy { it.capacity }`

   In each one, what is `it`?
6. **Find them in our code.** On `main`:
   - `Venue.kt`: is `Venue` a class or a data class? (We'll learn later why entities shouldn't be
     data classes.)
   - `VenueService.kt`: find a function, its parameters and its return type.
   - `VenueController.kt`: the line `class VenueController(private val venueService: VenueService)`.
     What is being passed into the constructor, and who passes it? (Hint: Spring Boot does. This
     is called *dependency injection*, and you used it in story 5.)

## How you know it works

- Every exercise runs, and you can explain the difference you saw between the class and the data
  class in step 2.

## Check your understanding

Post your answers as a comment on this issue:

1. When would you use a data class instead of a plain class?
2. In `rooms.filter { it.capacity >= 80 }`, what is the part in `{ }`, and what does `filter` do
   with it?
3. In `VenueController`, why does the controller receive `VenueService` in its constructor instead
   of creating one itself with `VenueService(...)`?

## Peek at the answer

On `feature/adding_db_flyway`, open `src/main/kotlin/com/venue/venues/VenueService.kt`. Near the
bottom you'll find several `data class`es, like `VenueResponse`: the JSON we send back for a venue.
They're data classes because they only carry information. Then open `DefaultVenueService.kt` and
find a `.map { ... }`: the same lambda you wrote in step 5.

## Stuck?

- **"Unresolved reference: Room"**: the class has to be written outside `fun main()`, above it.
