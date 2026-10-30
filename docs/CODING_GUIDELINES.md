# Coding guidelines

Rules for all code in this repository. In a conflict, the order is: security, data consistency, clarity, typing, test
coverage, performance, documentation. Security wins over convenience, and clarity wins over short code.

## Formatting and flow

- Leave a blank line before `return`, unless it is the first statement of the block.
- Never write `if`, `else`, `for` or `while` inline. Always use a block, with braces in languages that have them.
- Prefer `total += 1` to `total++`.
- Prefer early returns to deep nesting.
- Never nest ternaries. With three or more branches, extract a function that decides with `if` and early returns.
- Avoid immediately invoked functions. Extract a named function that can be tested.
- Never use the em dash character in code or documentation. Use a hyphen.

## Naming

- Never abbreviate. Avoid `x`, `y`, `k`, `s`, `i`, `obj` and `item` whenever a better name is possible, including in loops, callbacks, comprehensions, mocks and tests.
- Use semantic loop names: `for episode in episodes`, not `for e in episodes`.
- Do not invent acronyms in functions, variables or fixtures.
- Prefix unused variables and parameters with `_`.
- Use realistic, readable names in tests. Never `User1`, `Item1` or `TenantA`.
- Name magic values. Do not write loose numbers or arrays: create a constant that says what the value represents.

## Typing

- Annotate every Python parameter and return, including test methods (`-> None`).
- Use modern built-in types: `list[str]`, `dict[str, int]`, `str | None`.
- Do not use `from __future__ import annotations`.
- Avoid `Any`. It is acceptable only for free-form JSON and for values of an external API with no useful type.
- Type important local variables when that removes ambiguity.
- `mypy --strict` must pass.

## Comments

A comment is the exception, for what the code cannot express.

- Remove useless comments, decorative separators and docstrings that only repeat a name.
- Before commenting, ask if a better name, an extracted function or an early return makes the code obvious. If so, do that instead.
- Keep only comments that explain non-obvious behavior, a temporary workaround, a regression, or a security or compatibility restriction.
- A good name replaces the comment. Do not write both. Read the line without the comment: if it is still clear, the comment does not go in.
- A comment describes the final code, never the path to it. Do not justify the absence of something that only existed in a draft, narrate a refactor, or compare with a previous state.
- If a comment only makes sense to someone who followed the discussion, it does not go in.
- Never cite a task or ticket. Code is read in its current state.
- When two similar things coexist, document the difference between them, not how they came to exist.
- Reasons that outlive the edit, such as architecture decisions and trade-offs, go in the commit message, the pull request or the project documentation.
- Do not open a test file with a block that summarizes the suite. A short comment inside a test is enough for a real subtlety.

## Organization

- Keep modules cohesive. About 200 to 400 lines is comfortable, about 500 is a good moment to think about splitting, and over 1000 almost always means too many responsibilities. A cohesive long file is still better than a short mixed one.
- Prefer direct imports. Do not import something only to re-export it, unless it is a real public API.
- Fix circular imports with a refactor. Use a local import only if there is no alternative.
- Keep handlers thin: reading the request, deciding and writing the response should not be mixed with parsing or indexing.
- Keep decisions in pure functions, next to the code that uses them, so they can be tested without a device or a server.
- Before using a library feature, check the version in the project. It may not exist yet, or may no longer work.
- Pin dependency versions, and pin GitHub Actions to a full commit hash. A published package can be compromised at any time.

## Security

- Never trust the client. The server decides what can be read, and clients never name a disk path.
- Never add a write route without a design review: the server is read-only by design.
- `GET` has no side effects: it does not write to the database or change state.
- Keep the network restrictions in the code and in the service unit. They protect different layers.
- Prevent SQL injection with bound parameters, path traversal with validated paths, and command injection by never building shell strings from input.
- Never use `eval`, `pickle` on external data, unsafe YAML loading or dynamic imports from input.
- Treat every parsed file as hostile, including `.nfo` XML, images and subtitles. A broken file must not stop indexing.
- Do not expose stack traces, disk paths or sensitive data in responses. Keep logs free of them too.
- Never hardcode secrets, personal addresses or machine paths.
- Never fetch a URL taken from user content without validating it.

### Regular expressions

- Avoid nested or overlapping quantifiers such as `(a+)+`, `(a*)*` and `(.*)*`.
- Anchor patterns, limit repetition with `{0,N}` when there is a natural ceiling, and use specific character classes.
- Never build a pattern from user input without escaping it.
- Apply patterns to input of limited size.
- Compile fixed patterns once at module level when they run on a hot path.
- When in doubt, prefer explicit parsing to a complex expression.

## API

- A route, its documentation and the app change together. The API table in the README is part of the contract.
- Collections that can grow should be paginated. This project is a deliberate exception: it serves a personal library and returns whole lists, and that stays true only while the lists stay small.
- Keep health checks short. They must not reveal paths or configuration.
- Return correct status codes and a stable error shape.

## Performance and stability

- Heavy work does not run in a request path. Indexing runs in the background and requests only read the result.
- Do not recreate heavy objects per call. Keep them in a module-level cache or a singleton.
- Read each piece of data once. In a list screen, load what you need up front instead of querying per item.
- Write in batches, never with a loop of single writes.
- Every column used in a filter, sort or join needs an adequate index, decided together with the query. Avoid redundant indexes.
- Do not mask a deterministic bug with a retry. Fix the data or the code at the origin.
- Anything that reschedules itself needs a limit or a backoff.
- Keep the TV app light: fixed image sizes, reused views, nothing left running after the app closes.
- Measure smoothness on a real device. The emulator does not show it.

## Tests

- A relevant bug comes with a regression test.
- Coverage is not the goal. The question is: if a bug is injected here, does some test break?
- Assert the exact value when the value is the behavior. `assertEqual(position, 30)`, not `assertTrue(position > 0)`.
- Think of the mutant. If inverting an `if` or changing `>` to `>=` breaks nothing, the test is decoration. Test the boundary.
- Do not repeat magic numbers in tests. Import the real constant, so the test follows the change.
- Cover the error path and the denied side effect, not only the happy path.
- Test behavior, not implementation. A test should only break when observable behavior breaks.
- One scenario per test, with a name that describes it. Avoid `if` and `for` in the test body: use table-driven cases.
- Do not test framework code or thin wrappers. Extract the decision and test it.
- A test that cannot fail should not exist.
- Prefer readable scenarios to clever tests.
- Freeze time in tests that compare dates with a relative window, or pass `now` as a parameter. Never depend on the real clock or on real randomness for an expected result.

### Which tests to run

Choose by what the change can break, not by reflex.

| Change | Run |
|---|---|
| Comment, log text, formatting, constant rename | Lint |
| Logic of a function | Lint and the unit tests of that scope |
| Screen flow, API contract, navigation, playback | Lint, unit tests and the E2E suite |
| Core code used in many places, database schema | Everything, once, when closing the change |

While editing, run only the test of what you touched. If a test failed, rerun that test to read the error instead of the whole suite. Run the wider suite before opening a pull request.

## Commits and pull requests

- Keep commits small and focused, with a one-line message in English.
- Do not push directly to the main branch, and do not force push.
- Do not rewrite shared history. Ask for help with conflicts.
- Heavy files stay out of git: no videos, databases, APKs or build output.
- Before committing, run `./run-tests.sh`, `ruff check .` and `mypy`.
