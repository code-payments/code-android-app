---
name: release-notes
description: Generate GitHub release notes from git refs (tags, commits, or HEAD). Usage - /release-notes <from> <to>. Runs the changelog script, then rewrites the output into polished GitHub release notes.
argument-hint: <from_ref> [to_ref]
user-invocable: true
allowed-tools: Bash(git log *), Bash(git tag *), Bash(git describe *), Bash(bash scripts/changelog.sh *), Bash(gh release *), Read, Agent
---

# Release Notes Generator

Generate polished GitHub release notes for the code-android-app repository.

## Pre-flight context

- Latest tag: !`git describe --tags --match 'fcash/*' --abbrev=0 HEAD 2>/dev/null || echo "no tags found"`
- All recent tags: !`git tag --sort=-creatordate | head -10`

## Input

Parse the two refs from `$ARGUMENTS`. Each ref can be a tag, a commit SHA, a commit number (Nth commit in repo history), or `HEAD`:
- `/release-notes fcash/2026.4.10 fcash/2026.4.11`
- `/release-notes fcash/2026.4.9 HEAD`
- `/release-notes a3f2417 HEAD`
- `/release-notes 3599 HEAD` (from the 3599th commit to HEAD)

If only one ref is provided, use it as `<from>` and default `<to>` to `HEAD`.
If no refs are provided, use the pre-flight latest tag as `<from>` and `HEAD` as `<to>`.

## Steps

### 1. Run the changelog script

```bash
bash scripts/changelog.sh <from_tag> <to_tag>
```

Display the raw output for the user to see.

### 2. Rewrite into release notes

Use the Agent tool with `model: "haiku"`. Pass the raw changelog output with this prompt:

> Given these git commits (conventional commit format), write user-facing release notes.
>
> Rules:
> - Group under: **Features**, **Bug Fixes**, **Improvements**, **Dependencies** (omit empty sections)
> - Write for end users — no jargon, file names, or internals
> - One short sentence per item
> - Group related commits into a single bullet when they address the same area
> - Use scope as context but write in plain language; keep scope in **bold** prefix when it adds clarity
> - ALWAYS include bug fixes — only drop pure refactors, CI pipeline changes, and release/manifest bookkeeping
> - Feature bullets start with a lowercase verb (e.g., "add", "support", "enable")
> - Bug fix bullets start with "Fixed" (capitalized)
> - **Dependencies** section: list each dependency bump as `Name X.Y.Z → A.B.C` (use the human-readable library name, not the Maven coordinate). Include targetSdkVersion and Gradle wrapper bumps in this section too.
> - Use 2-space indent before each bullet (`  - `)
> - Do NOT include commit hashes
> - If no user-facing changes, output: Bug fixes and performance improvements.
> - Output ONLY the section markdown, no title or fences

### 3. Generate App Store "What's New"

Using the same changelog, write a short "What's New" blurb suitable for the Google Play Store listing. Rules:
- 3–5 bullet lines max, plain dashes (`- `)
- Lead with the most impactful user-facing features
- End with "Bug fixes and performance improvements" if there are fixes/chores
- No markdown bold, no sections headers — just a flat list
- Keep each line under ~60 characters
- If there are no notable user-facing changes, output only: `- Bug fixes and performance improvements`

You can derive this yourself from the changelog without a subagent call.

### 4. Assemble final notes

Combine the agent's output with the changelog compare link:

```markdown
{agent output}

**Full Changelog**: https://github.com/code-payments/code-android-app/compare/<from_tag>...<to_tag>
```

The release title/name is the version number without the `fcash/` prefix (e.g., `2026.4.11`).

### 5. Review gate

Show the assembled GitHub release notes **and** the App Store "What's New" to the user for approval. Do NOT create the release until the user explicitly confirms.

### 6. Publish

After user confirms, create the release:
```bash
gh release create <to_tag> --repo code-payments/code-android-app --title "<version>" --notes "$(cat <<'EOF'
<release_notes>
EOF
)"
```

## Never
- Publish without user approval
- Include commit hashes in the final notes
- Include internal-only changes unless they have user-facing impact
