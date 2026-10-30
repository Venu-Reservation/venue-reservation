# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the backend for the Reserved venue platform. Product rules, the API contract, shared data, decisions, backend rules and open items are in the shared project file, which the web and iOS repos use too:

@docs/project.md

Everything below is specific to this repo.

## Commands

- Needs JDK 25 (the Gradle toolchain in `build.gradle` requires it) and Docker. README.md has the full setup.
- `docker compose up -d`: starts the local services from `docker-compose.yaml`. `application.properties` points at them.
- `./gradlew build`: compiles and runs the tests. The current context-load test starts the app, so the Docker services must be running.
- `./gradlew bootRun`: runs the API on http://localhost:8080. The web app's `npm run dev` sends its `/api` requests here.

## Workflow

- Make small changes, build after each one (`./gradlew build`), and run the tests before saying a task is done.
- Commit after each completed step. Don't add dependencies that aren't needed.
- Before large work, summarize the plan and wait for approval.
- `docs/project.md` is shared with the web and iOS repos. Contract or product changes go there first, then get synced to the other repos.
