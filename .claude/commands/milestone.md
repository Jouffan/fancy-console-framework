# Start Milestone

Run the routine for milestone **$ARGUMENTS**.

1. Read `doc/requirements.md` §4 for the milestone scope. The ID list there (for M1, the **In M1** bullet) is the definition of done: every ID needs at least one test naming it.
2. Read the relevant part architecture in `doc/`.
3. Write tests first (NFR-2 for core), then implementation. Cite requirement IDs in test names and commit messages.
4. Get `mvn -q verify` green, then report what the human should run and what to look for (NFR-18). Do not mark the milestone done yourself.
