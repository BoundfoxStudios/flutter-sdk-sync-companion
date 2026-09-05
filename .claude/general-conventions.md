# General Conventions

Stack independent rules for this project. Where the repository defines a
different convention elsewhere (CLAUDE.md, more specific skills), that one wins.

## Way of working

- Alignment before action: for anything non-trivial, summarise in a few
  sentences (1) your understanding, (2) the intended approach and (3) the
  trade-offs or risks, then wait for confirmation before implementing. For tasks
  spanning 3+ files or carrying architectural decisions, switch to plan mode.
  Exception: when the task explicitly asks for autonomous execution or the
  session runs unattended, state the assumptions explicitly and keep working
  instead of blocking.
- When the user proposes a different approach after an implementation, compare
  the old and the new one with concrete pros and cons before switching. Name
  significant downsides clearly so the choice is an informed one.
- When an approach runs into a wall (repeated failures, refuted assumptions),
  stop and re-plan instead of pushing harder.
- Delegate codebase exploration and research to subagents: the main context
  holds decisions and results, not the search process.
- Every piece of programming work goes through a subagent, never through the
  main context, even for small changes. The main context researches, decides
  and cuts the briefs; multi-step work is split into separate subagent steps
  (implementation, tests, review fixes).
- A brief for such a subagent is strict and closed: the files to change with
  their paths, the intended target behaviour, the results of the up-front
  research (API signatures, existing patterns in the repository, documentation
  excerpts), the applicable conventions, and the acceptance criteria together
  with the commands that verify them. Whatever is not in the brief is not
  touched.
- These subagents do no further research: exploration and research happened
  beforehand and are part of the brief. When something is missing or the brief
  contradicts the code, the subagent reports back instead of searching,
  deciding or guessing on its own.
- Before changing concurrent code, identify: shared mutable state, ordering
  guarantees when interleaving operations, existing synchronisation boundaries.
  For async code, add cancellation propagation, backpressure and atomicity.
- Self review before reporting done: read the changed files again (not from
  memory), check for unused variables, missing null checks, inconsistent naming
  and unhandled edge cases; build and run the affected tests.
- Simplicity check before reporting done: every new variable, wrapper,
  intermediate collection and parameter has to carry its weight. Inline values
  that are used once immediately; when something feels over-engineered,
  simplify it first.

## Code style

- The default is zero comments. A comment is written only when it states
  something the reader cannot see in the code: a non-obvious algorithm, a
  handled edge case, a workaround, or a deliberate decision against the obvious
  choice. When in doubt, leave it out. Then write only the essence, as short as
  possible, e.g. `// Safari fires pagehide twice; the guard drops the second call`.
- When code seems unclear without a comment, improve the names first or extract
  a function. A comment is only allowed when neither can carry the information.
- Forbidden comment patterns, never write these, not even as variations. The
  list is illustrative, not exhaustive:
  - Paraphrasing code or names: no `// enables the cooking mode` on
    `isCookingModeEnabled`, no `// save the user` above `repository.save(user)`.
  - Step and section narration: no `// Validate input`, `// Setup`,
    `// Main logic`, `// --- Helpers ---`; in tests no `// Arrange` /
    `// Act` / `// Assert`.
  - Change narration: no `// now uses the new API`, `// changed to async`,
    `// as requested`. The change is told by the diff and the commit message,
    not by the code.
  - Signature echo: no JSDoc/XML doc/docstring that merely rephrases name,
    parameters and return type. Doc comments only where the project or the task
    demands them, and then without parameter or return echoes, carrying only
    content beyond the signature (units, error behaviour, side effects).
- Machine directives (`// eslint-disable-next-line`, `# type: ignore`, pragmas,
  licence headers) do not count as comments under this rule.
- The rule applies to comments you write yourself. Existing comments in code you
  did not write stay untouched.
- No abbreviations in identifiers, always spell words out: `index` instead of
  `i` in loops, `template` instead of `tpl`. Spelled out names read better.
- All code is production code: clean, complete and maintainable. No stopgaps, no
  commented-out leftovers, no "TODO later" solutions. The only exception is
  prototype or throwaway code that was explicitly asked for.
- Never edit generated artefacts by hand: change the generator, the template or
  the source and regenerate. When generated files are committed, commit the
  regenerated result together with the change that caused it. Make generator
  failures visible (an error artefact or a hard abort), never swallow them
  silently.
- In time dependent logic whose behaviour tests need to control, do not read the
  system clock directly. Use a clock that is controllable from a test (the
  injected abstraction where the stack offers one).
- When the project uses structured logging, always log the same concept under
  the same property name so logs stay reliably queryable.

## Testing

- Tests verify your own application behaviour only: business logic, edge cases,
  error paths. Never test the underlying framework, e.g. no test for whether a
  variable is bound to the UI correctly, whether a getter returns the value that
  was set, or whether a framework feature works. The framework covers that
  itself.
- The guiding question before every test: which application behaviour breaks
  when this test turns red? Without a concrete answer, do not write the test.
- No test-only code in production: no members, constructors or factories whose
  only caller is a test (no `CreateForTesting`, no seed or reset methods that
  exist purely for test setups). Such helpers belong in the test code (test
  project or test directory).
- Never widen visibility for tests: a member does not become `public` or
  `internal` (or the stack's equivalent) just to be testable. Test through the
  existing public surface, or extract the logic into its own type whose
  visibility has a production reason.
- Look at the real implementation before every mock or fake: use the real type
  when it is cheap to construct (no I/O, no global state, no DI graph) or
  carries meaningful logic. A stub that behaves differently masks bugs or
  invents failures that never happen in production. Mock only when the real type
  pulls in heavy dependencies (database, network, external services); when in
  doubt, ask the user instead of inventing a stub.
- "No side effects" assertions must not be tautologically true by construction:
  when the input type structurally cannot carry the data for the side effect,
  the test verifies nothing. Drop it when a real contrast test (mixed success
  and failure case) exists.
- Organisation: first look for an existing test file or suite for the same
  member or feature and extend it there. A new file only for a genuinely new
  cut. Shared setup infrastructure (base class, fixture, shared hook) only once
  2+ places duplicate setup, never up front.
- Shared setup goes into the framework's setup mechanisms (constructor, setup
  hooks, fixtures, helpers). The arrange part of a test contains only
  scenario-specific values.
- Exactly one assertion library and exactly one mocking library per repository.
  Legacy patterns (an old assertion library, an old naming style) get no new
  usages. Use the canonical style even when adding to legacy files.
- Test names state the behaviour under test, the scenario and the expected
  result (e.g. `AddRow_EmptyTable_AddsRow`, adapted to each test framework).

## Branches and commit messages

- Branch names on creation: only the prefixes `feature/`, `fix/` and `release/`,
  always spelled out (`feature/abc`, not `feat/abc`). Other prefixes only on
  explicit instruction.
- When the branch belongs to a GitHub issue, its number goes directly after the
  prefix, before the descriptive name: `feature/123-add-retry-logic`. Never
  guess numbers, use one only when the issue was named in the task or looked up
  beforehand.
- Commit messages consist of the title (a single line) and nothing else. The
  only exception is the issue reference below.
- Commit messages follow Conventional Commits, unless the repository describes
  its own convention, in which case that one applies. The default is
  `type: description`; use a scope (`type(scope): description`) only when the
  repository defines scopes.
- Allowed types, exactly these and no others:
  - `build`: changes to the build system or to external dependencies
  - `ci`: changes to CI configuration and scripts
  - `docs`: documentation-only changes
  - `feat`: a new feature
  - `fix`: a bug fix
  - `perf`: a code change that improves performance
  - `refactor`: a code change that neither fixes a bug nor adds a feature
  - `style`: changes that do not affect the meaning of the code (whitespace,
    formatting, missing semicolons, …)
  - `test`: adding missing tests or correcting existing ones
- Never write a commit body, and no footers or trailers such as
  `Co-Authored-By` or "Generated with" lines.
- Exception: when a commit belongs to a GitHub issue, the body consists of
  exactly one line, `Refs #123`; multiple issues each get their own line. No
  closing keyword in the commit, that belongs in the pull request description
  (see below). Never guess numbers: reference an issue only when it was named in
  the task or looked up beforehand.

## GitHub issues

- Issues describe the problem or requirement in domain terms only: what, for
  whom, why, expected behaviour, acceptance criteria. No solution outline and no
  implementation sketch, unless the approach was explicitly worked out together
  beforehand, in which case exactly that agreed state goes in.
- No references to files, classes or other places in the code: issues are often
  written long before the implementation, the code moves on and the references
  go stale. Use domain terms instead of code symbols.
- Write prose without hard line breaks: one paragraph is one line, GitHub wraps
  it when rendering. Line breaks only where Markdown needs them (paragraph
  breaks, lists, code blocks).
- Sharpen the domain concept in dialogue before filing: actively raise and
  resolve ambiguities, edge cases and open decisions. An issue is filed only
  once no questions are left.
- When a parent issue has children (an epic with sub-tasks), link the children
  as GitHub sub-issues, not as a Markdown list or a task list of `#123` links in
  the body: `gh issue create --parent <parent-number>` when creating, or
  `gh issue edit <parent-number> --add-sub-issue <number>` afterwards.

## Pull requests

- Pull request titles do not follow Conventional Commits: no `feat:`/`fix:`
  prefix, just a normal descriptive title (e.g. "Add retry logic to the sync
  job" instead of "feat: add retry logic to the sync job").
- The same rule as for issues applies to pull request descriptions: prose
  without hard line breaks, GitHub wraps it when rendering.
- When a pull request belongs to a GitHub issue, the closing keyword goes into
  the description: `Fixes #123` for bugs, otherwise `Closes #123`; multiple
  issues each get their own line.
- The default is an empty pull request description. What goes in is the closing
  keyword (above) and, as far as the title, the linked issue and the diff do not
  already say it, one to a few sentences on what and why plus reviewer knowledge
  that the diff does not show: breaking changes, migration or deployment steps,
  behaviour to verify manually, deliberate decisions against the obvious choice.
  The guiding question before every sentence: do the title, the issue or the
  diff already say this? If so, leave it out, even when the description then
  consists of nothing but the closing keyword or stays empty.
- Forbidden patterns in pull request descriptions, never write these, not even
  as variations. The list is illustrative, not exhaustive: boilerplate headings
  (`## Summary`, `## Changes`, `## Test plan`), retelling the changes as bullets
  or prose, lists of changed files, repeating the title or the issue text, a log
  of your own approach or testing, checklists, emoji, "Generated with" footers.
- When the referenced issues belong to a milestone, assign the pull request to
  the same milestone (`gh pr edit <number> --milestone <title>`). GitHub allows
  only one milestone per pull request; when the issues span several, ask instead
  of guessing.
- When further remarks come in after a pull request was created and they belong
  to that pull request topically, first check whether it is already in review:
  `gh pr view <number> --json reviewRequests,reviews`. When both are empty,
  update the existing pull request (push to the same branch) instead of opening
  a new one. Once a reviewer is assigned or a review was submitted, leave that
  pull request alone and make the change as a new one.

## Dependencies and versions

- When adding or updating dependencies (npm, NuGet, pip, …), never take versions
  from training knowledge. Always determine the current latest version first
  (e.g. `npm view <package> version`, `dotnet package search`, a PyPI or
  registry query) and use that one.
- The same applies to GitHub Actions (`uses:` references), base images in
  Dockerfiles and tool versions in CI configuration: look up the latest major
  version or the latest release before writing (e.g. via
  `gh api repos/<owner>/<repo>/releases/latest`), do not guess.
- And to API surfaces: when unsure about signatures, parameters or framework
  behaviour, never guess. Look up the current documentation and read existing
  usages in the repository.

## Agent documentation

Rules for CLAUDE.md and other agent instruction files in the repository.

- The instructions are part of the deliverable: when a task changes
  architecture, conventions, data structures or behaviour described there,
  update the affected section in the same pass. Documentation and code never
  drift apart.
- The documentation describes the current state of the code, never the intended
  one. Mark things that are decided but not yet built explicitly as such,
  together with the condition under which the note goes away.
- A curated map, not a mirror of the code: a fact visible only in the code goes
  in only when several criteria hold. It is needed repeatedly, cross-cutting or
  load-bearing (a contract or invariant), non-obvious (a footgun), stable,
  expensive to derive. Local, easily discoverable mechanics stay in the code;
  never copy signatures just in case.
- Net discipline: every addition has a named destination. First check whether an
  existing entry already carries the knowledge and sharpen it there instead of
  adding next to it; remove redundant or outdated material in the same pass.
- After a user correction to a project-wide pattern, update the affected passage
  as a concrete rule, not as a vague lesson appended somewhere.
- Document decisions where a future reader would propose them again: rejected
  alternatives with the reason, deliberate deviations from the obvious choice
  marked as intended, refuted (optimisation) hypotheses with date, measurement
  and a note not to try them again.
- Record negative knowledge: document APIs that sound plausible but do not (or
  no longer) exist explicitly as "X does not exist, use Y", exactly where an
  agent would look for them.
- Project management content (dates, meeting notes, organisational questions)
  stays out. The instructions are about the code.
- As the documentation grows, separate files pay off: a glossary (one short,
  linkable definition per domain term, anchored on the code symbol, extended the
  moment an unknown term is encountered) and an invariant register
  (load-bearing contracts with statement, rationale, place of enforcement, code
  or convention only, and the symptom of a violation; after a bug fix whose root
  cause was an unenforced contract, an entry is added).

## Memory

- Machine-local project knowledge is worth nothing to the team. Never write that
  you memorised something when it is stored locally only.
- Repository related insights belong in the repository (e.g. in its CLAUDE.md)
  and get committed.
- Global insights (way of working, preferences, environment) are told to the
  user instead, so they can anchor them in their personal configuration.
