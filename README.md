# Savory-Studio
Application for those restaurants which wants to promove their Micro-Enterpireses or big projects about gastronomy

# Savory Studio Project — Workflow & Contribution Guide

This document establishes the guidelines, branching strategy, and commit conventions for the project repository. All team members must follow these standards to ensure a clean Git history, seamless Jira traceability, and an organized development lifecycle.

----------------------------------------------------------------------------------------------------------

## 1. Branching Strategy

The main branch **`main`** is protected (direct pushes and unreviewed merges are strictly prohibited). All development must take place within three secondary branch types:

* **`feature/`**: Used for developing new features, modules, or core functionality.
* **`testing/`**: Used for test cases, QA automation, test scripts, or seed data generation.
* **`hotfix/`**: Used for urgent bug fixes that must be applied directly to production.

### Branch Naming Convention
Every new branch must include the branch type, the Jira ticket key, and a short description separated by hyphens (`-`):


# Format: <type>/<JIRA-KEY>-<short-description>

# Examples:
git checkout -b feature/PROJ-101-environment-variables
git checkout -b testing/PROJ-105-test-matrix
git checkout -b hotfix/PROJ-201-auth-error

----------------------------------------------------------------------------------------------------------

3. Daily Workflow (Step-by-Step)
Synchronize your local repository:

Terminal
  git checkout main
  git pull origin main
Create your working branch linked to Jira:

Terminal
  git checkout -b feature/<JIRA-KEY>-task-name
Make your changes and commit using the standard format:

Terminal
  git add .
  git commit -m "feat: <JIRA-KEY> - description of changes"
Push your branch to GitHub:

Terminal
  git push origin feature/<JIRA-KEY>-task-name
  Open a Pull Request (PR):

Go to GitHub and open a Pull Request targeting the main branch.

Include the Jira ticket key in the PR title (e.g., [APG - 5] Environment Variables Setup).

Request a review from at least 1 teammate.

Once approved, merge the PR and delete the remote working branch.

----------------------------------------------------------------------------------------------------------

4. Environment Variables (.env)
Never commit real credentials or secret passwords to the repository.

Use the .env.example file located in the project root to define required key names for the Backend-Database connection.

Copy .env.example to a local .env file to start working:

Terminal
  cp .env.example .env
----------------------------------------------------------------------------------------------------------
5. Branch Protection Rules
  5.1.Main Branch Restriction: Direct pushes to main are disabled. All changes must be merged via Pull Requests.

  5.2.Required Reviews: At least 1 approval is required before merging.

  5.3.Jira Traceability: All commits, branches, and PRs must automatically link to their respective Jira tickets.

```bash
