# Story 9: How a request travels through our code

**Kind:** Understand (no code to merge) · **Size:** XS · **Time:** 1 to 2 hours

## In plain words

When the web app or the iPhone app wants something, say "show me venues in New York", it
sends a **request** to our backend over the internet. Our backend reads it, gets the answer
(often from the database), and sends back a **response**.

Inside our code, that request is passed along a short chain, like an order in a restaurant:

- The **controller** is the waiter: it takes the order (the request) and brings back the food
  (the response). It doesn't cook.
- The **service** is the kitchen: it decides what to do and applies the rules.
- The **repository** is the pantry: it fetches and stores things in the database.
- The **database** is the storeroom where everything is kept.

You already wrote all four layers in stories 5 to 7. Today you'll follow one request through
them, so the picture is clear before we add more.

## New words

- **Request / response**: what an app sends to us, and what we send back.
- **Endpoint**: one address the backend answers, like `GET /api/v1/venues/search`.
  `GET` means "give me something"; `POST` means "here's something new"; `PUT` means "replace
  this"; `DELETE` means "remove this".
- **JSON**: the text format requests and responses use, like `{ "name": "The Atrium" }`.
- **Annotation**: the words starting with `@`, like `@RestController`. They're labels that tell
  Spring Boot what a class or function is for.
- **Spring Boot**: the framework that runs our app: it starts the web server, connects the layers
  together, and calls our functions when a request arrives.

## Before you start (20 minutes)

- Read "An overview of HTTP" on MDN, up to "HTTP flow":
  <https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview>

## Steps

1. Start the app (`./gradlew bootRun`) and send `GET http://localhost:8080/api/v1/venues/search`
   from Postman. Look at the response's status (200) and its JSON.
2. Open these files on `main` and find the code that handled your request:
   - `VenueController.kt`: which function has `@GetMapping("/search")`?
   - `VenueService.kt`: which function did the controller call?
   - `VenueRepository.kt`: did this request reach it? (It didn't. Why not?)
3. Put a breakpoint (click in the margin next to the line number in IntelliJ) inside the
   controller's `searchVenuesStub` function and the service's `getSearchStub`. Run the app in **Debug** mode
   (the bug icon), send the request again, and step through with F8. Watch the order in which the
   code runs.
4. Draw the path on paper or in any drawing tool: Postman → controller → service → (repository →
   database) → back to Postman. Label each box with the class name and what it does in one line.
5. Send `DELETE http://localhost:8080/api/v1/venues/abc` from Postman. It fails with an error,
   because `abc` isn't a valid UUID. Which layer refused it, the controller or the service?

## How you know it works

- You can point at the exact function in each file that ran for your request.
- Your drawing shows the boxes in the right order, with the repository and database marked as
  "not used yet" for this request.

## Check your understanding

Post your drawing and answers as a comment on this issue:

1. Why doesn't the controller talk to the database directly? What does a service add?
2. Who calls `VenueController.searchVenuesStub()`? (You never call it in your own code.)
3. In step 5, which layer rejected `abc`, and how do you know?

## Peek at the answer

On `feature/adding_db_flyway`, look at `src/main/kotlin/com/venue/venues/`. You'll see the same
four kinds of files: `VenuesController.kt`, `VenueService.kt` (and `DefaultVenueService.kt`),
`VenueRepository.kt`, and the entity `Venue.kt`. Same layers, just more of them. Don't read the
code in detail yet: just notice the shape is the one you drew.

## Stuck?

- **The breakpoint never stops**: make sure you started the app with the Debug (bug) button, not
  Run, and that Postman's URL matches the endpoint exactly.
