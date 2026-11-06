# Working on Savory Studio

This guide applies to every contributor agent working in this repository. Start with the Jira ticket, the current remote branch, and the open PRs. Keep the ticket key, commit IDs, and verification results together so the next reviewer can reproduce the work.

## 1. Establish the baseline before editing

1. Read the whole Jira issue, including its owner, description, acceptance criteria, dependencies, comments, and linked contracts. Identify the deliverable and the neighboring tasks owned by others. A contract or review ticket may require work in Jira rather than Java. Record any decision that needs Product, Backend, Data, or QA input.
2. Check the checkout, remotes, upstream, and local changes. Preserve the user's edits and untracked files. If switching or pulling would disturb their work, use a separate worktree. Use a fast-forward-only pull on the integration branch. If the local branch has diverged, inspect both histories and work from the fetched remote tip in an isolated worktree.
3. Query GitHub before starting implementation. List the open PRs, their targets, and their exact head commits. Inspect recently merged PRs that touched this task. A teammate's local branch name, Jira status, or earlier chat summary can be out of date.

```powershell
git status --short --branch
git remote -v
git fetch --prune origin
# In a clean checkout used for the task
git switch develop
git pull --ff-only origin develop
git rev-parse HEAD
gh pr list --state open --limit 100 --json number,title,baseRefName,headRefName,headRefOid,mergeable
gh pr list --state merged --base develop --limit 20 --json number,title,mergedAt,mergeCommit
```

If the open list reaches the limit, retrieve the remaining pages. For each open PR targeting the integration branch, inspect its changed files and identify shared behavior. Also inspect PRs targeting other branches when the ticket depends on them. Open the actual diff for every overlap or dependency. If GitHub is unavailable, local analysis can proceed, but live conflict verification and publication must wait.

4. Write a short working plan: ticket boundaries, target SHA, affected entry points, relevant PR numbers and SHAs, expected interactions, and tests. Keep this in the task or PR. Create repository documents only when they are part of the deliverable.

## 2. Branches, commits, and ownership

- **Integration targets.** Start routine feature, testing, documentation, and corrective work from current `origin/develop`. Open PRs to `develop`. Only an approved release goes from `develop` to `main`. The README's older daily-workflow examples point feature PRs at `main`. Follow the integration target recorded in [APG-18](https://epenuelachica.atlassian.net/browse/APG-18) and [APG-27](https://epenuelachica.atlassian.net/browse/APG-27).
- **Protection.** Both branches receive changes through reviewed PRs. The effective GitHub rules currently require **two approving reviews**. Request the reviewers needed to meet the live requirement. Check the rules again before delivery, including when the README's review count differs. An administrator working as an agent follows the same review process.
- **Names.** Use `<type>/<JIRA-KEY>-<short-kebab-case-description>`. Follow the README's `feature/`, `testing/`, and `hotfix/` conventions. Use the real Jira key. On a pre-existing PR, update its existing head branch. Check remote refs before choosing a new name. For example, a remote branch literally named `hotfix` blocks all `hotfix/...` names. Preserve it and coordinate an available team prefix.
- **Commits.** Use `<type>: <JIRA-KEY> - <description>`, e.g. `fix: APG-42 - restore customer coupon query`. Choose a type matching the change, such as `feat`, `fix`, `test`, or `docs`. Stage the specific paths and review the staged diff. Keep unrelated tickets, local plans, agent caches, IDE files, build output, real credentials, and local `.env` files out of commits.
- **Ownership.** Work on your ticket branch. For an integration assignment covering other people's PRs, preserve each author's commits and publish each fix to its owning PR within the authorized scope. Use ordinary fast-forward pushes. If a push is rejected, fetch and inspect the intervening changes. Never force-push over a teammate, delete someone else's branch to free a name, or push a local combined rehearsal into an individual PR.

Create your ticket branch **before editing**. For example, after checking the name is available, run `git switch --no-track -c feature/APG-42-active-promotion-queries origin/develop` with your own ticket and description. For an existing PR, check out its head instead. Confirm `git branch --show-current` before committing.

The [README](README.md) supplies the naming examples and environment setup. Check effective GitHub protection, including repository rulesets, with these commands.

```powershell
gh api repos/Savory-Studio/Savory-Studio/rules/branches/develop
gh api repos/Savory-Studio/Savory-Studio/rules/branches/main
```

## 3. Implement within the existing backend

The application is a Java 21 Spring Boot backend built with the Maven wrapper. Check [pom.xml](pom.xml) for current versions. Production code lives under `src/main/java/com/apg/promociones/apg_api`, with tests under the corresponding `src/test/java` package. Follow the nearest implemented feature from HTTP request through persistence before adding code.

| Area | How we work |
| --- | --- |
| `controller` and `dto` | Controllers bind paths, headers, and request DTOs, apply Jakarta validation, resolve caller context where appropriate, and delegate to services. Use response DTOs to control what clients receive. Keep existing route aliases, JSON field names, statuses, messages, and Springdoc annotations in sync with the contract. Write new Java identifiers in English and preserve existing public API vocabulary. |
| `service` | Put business rules, entity-to-DTO mapping, and transaction boundaries here. Reuse existing services and helpers. Follow the existing transactional methods for multi-step mutations and read-only queries. If an operation must be atomic or unique under concurrency, enforce the invariant in the database and verify the client-visible outcome. |
| `repository` and `model` | Reuse Spring Data JPA interfaces and mapped entities. Check callers, derived property paths, native SQL, PostgreSQL enum casts, column types, and relationships together. Search production **and test** callers before changing or removing a method. |
| `security` and `exception` | Follow `SecurityConfig`, the active filter, caller context, and `GlobalExceptionHandler`. Exercise requests through the real filter when public access or caller identity changes. Use existing exception types and error DTOs for the relevant status. |
| `db` and resources | PostgreSQL is the database used by the app and Supabase. Reconcile entity mappings with [the schema snapshot](db/schema.sql), [versioned migrations](db/migrations), [test seeds](db/seed/test_data.sql), and [the repository test schema](src/test/resources/sql/apg42-schema.sql). The [application configuration](src/main/resources/application.yml) uses Hibernate schema validation. Apply SQL explicitly to disposable databases during QA. |

Use the committed REST contracts for [registration](contrato-registro.md), [publishing](contrato-promociones.md), [claiming](contrato-cupones.md), [coupon hashes](contrato-hash-cupon.md), and [redemption](contrato-canje.md). Compare them with the live controller, tests, and current Jira decisions. When they disagree, identify the precise discrepancy and which behavior existing clients receive before choosing a change.

**Contracts that have crossed PR boundaries**

- Registration is public. With simulated authentication enabled, protected API calls use the locally configured `TEST_AUTH_TOKEN` and a positive `X-User-Id`. Verify the filter and controller together. In particular, a body or path ID belonging to a different customer must not override the authenticated caller on coupon operations. Keep secret values outside source, logs, and PR evidence.
- Claiming a promotion creates an `UNUSED` coupon with a persisted UUID. The catalog uses promotion status, validity dates, and remaining stock. Only `USED` coupons consume redemption limits. Customer coupon queries retain redemption history. Preserve both customer-list aliases and the existing coupon-hash lookup when changing neighboring routes or repositories.
- Registration relies on unique email and BCrypt persistence. Publishing relies on discount-type-specific database constraints. A successful registration or promotion insert after loading the seed data exercises behavior that mocked repositories can miss. These are starting points for regression checks whenever a ticket changes one of those contracts.

Use direct source or semantic navigation for known code. For an unfamiliar cross-cutting change, use the repository's Codebase Memory graph if available to identify boundaries, then verify the exact files at the PR revisions you are comparing. Check index freshness and coverage. Read live source for stale or missing ranges, especially in new branches and SQL. These instructions must also be usable by contributors with only Git, GitHub, and the project toolchain.

## 4. Compare the candidate, the base, and other PRs

**Run this check twice:** before choosing the implementation and after the final candidate commit, immediately before publishing or merging. Use the candidate working tree and its staged diff during development. Use committed, fetched SHAs for final verification.

1. Read the candidate diff from its merge base with `origin/develop`: `git diff --name-status origin/develop...HEAD` followed by `git diff origin/develop...HEAD`. Inspect what landed on `develop` since the branch split with `git log --oneline HEAD..origin/develop`. Compare behavior on both sides.
2. For each open PR, record **PR number, base, head SHA, shared files or contracts, and how you checked the interaction**. Consider dependencies across different files: filter versus controller, DTO versus client payload, entity versus SQL, seed IDs versus generated IDs, repository method versus caller, and tests versus live response. A mergeable badge only answers the Git question.
3. Fetch each relevant PR head by number. This also works for PRs from forks. For example, to inspect PR 16, use `gh pr view 16 --json baseRefName,headRefOid,files`, `git fetch origin refs/pull/16/head`, then `git rev-parse FETCH_HEAD`. Save the result and compare it to GitHub's head OID immediately. Another fetch replaces `FETCH_HEAD`. If the OIDs differ, refresh the inventory.
4. Use `git merge-tree --write-tree <base-sha> <candidate-sha>` and `git merge-tree --write-tree <candidate-sha> <peer-sha>` on a Git version supporting that form. Exit 0 indicates a clean textual merge, 1 reports conflicts, and other failures need investigation. For a repository-wide audit, inventory closed and merged PRs too, verify whether their merge commits are ancestors of `develop`, and compare every requested pair. Leave closed, unmerged work out of the release candidate unless explicitly selected.
5. Rehearse interacting changes together in a **separate worktree** created with `git worktree add --detach <scratch-path> <base-sha>`. Inside it, merge the exact candidate and peer commits in the intended order with `git merge --no-edit <sha>`. Run the acceptance checks on the resulting tree. For parallel PRs expected to merge in any order, start each alternative order in a fresh worktree at the **same base SHA**. For a small set, test every order and compare `git rev-parse 'HEAD^{tree}'`. Identical tree IDs let one full build verify that same content in every order. For a true prerequisite, document and test the required order.
6. If conflicts arise, compare the merge base and both implementations. Preserve the behaviors each ticket added, including imports, query methods, aliases, security configuration, and tests. Carry the correction back to the owning branch, verify it there, and rebuild the combined tree from the corrected heads. Avoid whole-file “ours” or “theirs” resolutions that drop neighboring features.
7. Immediately before pushing, refresh the remote base and open PR heads. If any relevant revision changed, inspect the delta, repeat the affected comparisons, and rerun verification on the new combined content. Remove only your own clean scratch worktrees when finished.

## 5. Verify through the normal entry points

For backend changes, establish whether the base builds, run focused tests during development, and run a full clean verification on the final candidate. Repeat on the combined tree when it differs materially from the candidate. Capture pre-existing failures separately with the exact base and reproduction steps. A failing or skipped check stays visible in the handoff.

1. Use JDK 21, the checked-in wrapper, and Docker for the PostgreSQL Testcontainers tests. Check the current build file before changing toolchains. Point `DB_URL`, `DB_USER`, and `DB_PASSWORD` at an **isolated local database** for the application-context test and runtime checks. Some integration tests start their own containers. Confirm the effective target before running seeds or write requests.
2. Initialize the empty database with V1, V2, and any later migrations in order, then the test seed. For schema changes also test a clean install from the snapshot, upgrade from a representative previous schema, and retry migrations/seeds where repeatability is promised. Keep an already-deployed migration reproducible by putting subsequent changes in a new version. Check that existing UUIDs, history, relationships, constraints, and generated IDs survive. Use `psql -v ON_ERROR_STOP=1` so a failed statement fails the run.
3. Use the commands below from the repository root. On Unix, use `sh ./mvnw` in place of the Windows wrapper and check the process exit code. Supply the focused test class relevant to the change.

```powershell
.\mvnw.cmd -version
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=PromotionRepositoryTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
git diff --check origin/develop...HEAD
```

4. For changes to endpoints, identity, persistence, or runtime configuration, start the packaged JAR against the migrated database. Set `TEST_AUTH_TOKEN` locally when exercising protected routes. Check the affected paths in `/v3/api-docs`, call the actual endpoints, and inspect the resulting database rows. Exercise the successful path and the relevant invalid, unauthorized, forbidden, missing, or conflicting request. For rejected writes, verify the original data survived. For a cross-feature coupon change, run registration → publish → active catalog → claim → own coupons → redeem by UUID → updated history and stock.
5. Record the build command, Java and PostgreSQL versions, pass/fail/skip counts, runtime outcomes, and the base/head/tree IDs tested. Distinguish tests compiled, tests executed, app started, and workflow exercised. For a documentation-only diff, verify paths, commands, rules, and change scope, and state exactly which checks ran.

## 6. Publish and hand off

- Inspect `git status --short`, `git diff --cached`, and the final `origin/develop...HEAD` diff at the appropriate stages. Push the ticket branch and open or update its PR with the Jira key in the title, e.g. `[APG-42] Restore customer coupon query`. Set the base explicitly. Link Jira and any prerequisite or overlapping PRs.
- Give reviewers four things: **scope and acceptance criteria, integration evidence, verification results, and remaining decisions**. Include the exact revisions, required merge order if any, local environment prerequisites, and any verification gaps. Keep machine-specific paths, credentials, and raw environment dumps out of the PR.
- Verify the published head SHA, target branch, GitHub mergeability, required checks, and review decision. A PR can be conflict-free and still blocked on reviews. Wait for the effective approvals. Merge only with the team's authorization and normal GitHub rules. Agents must not self-approve, bypass protection, or claim another role's sign-off.
- After an authorized merge, pull `develop` with `--ff-only`, confirm the merged PRs and resulting tree, and validate the actual integration. Recheck outstanding PRs against the new tip. Report the commit tested and any remaining dependency before marking the Jira work complete.
