---
name: release-notes
description: Generate GitHub release notes from git tags. Usage - /release-notes <from_tag> <to_tag>. Runs the changelog script, then rewrites the output into polished GitHub release notes.
disable-model-invocation: true
argument-hint: <from_tag> <to_tag>
user-invocable: true
allowed-tools: Bash(git log *), Bash(git tag *), Bash(git describe *), Bash(bash scripts/changelog.sh *), Bash(gh release *), Read, Agent
---

# Release Notes Generator

Generate polished GitHub release notes for the code-android-app repository.

## Pre-flight context

- Latest tag: !`git describe --tags --match 'fcash/*' --abbrev=0 HEAD 2>/dev/null || echo "no tags found"`
- All recent tags: !`git tag --sort=-creatordate | head -10`

## Input

Parse the two tags from `$ARGUMENTS`, e.g.:
- `/release-notes fcash/2026.4.10 fcash/2026.4.11`
- `/release-notes fcash/2026.4.9 HEAD`

If only one tag is provided, use it as `<from>` and default `<to>` to `HEAD`.
If no tags are provided, use the pre-flight latest tag as `<from>` and `HEAD` as `<to>`.

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
> - Group under: **Features**, **Bug Fixes**, **Improvements** (omit empty sections)
> - Write for end users — no jargon, file names, or internals
> - One short sentence per item
> - Group related commits into a single bullet when they address the same area
> - Use scope as context but write in plain language; keep scope in **bold** prefix when it adds clarity
> - Drop internal-only changes (pure refactors, CI tweaks, build config) unless user-facing
> - Feature bullets start with a lowercase verb (e.g., "add", "support", "enable")
> - Bug fix bullets start with "Fixed" (capitalized)
> - Use 2-space indent before each bullet (`  - `)
> - Do NOT include commit hashes
> - If no user-facing changes, output: Bug fixes and performance improvements.
> - Output ONLY the section markdown, no title or fences

### 3. Assemble final notes

Combine the agent's output with the changelog compare link:

```markdown
{agent output}

**Full Changelog**: https://github.com/code-payments/code-android-app/compare/<from_tag>...<to_tag>
```

The release title/name is the version number without the `fcash/` prefix (e.g., `2026.4.11`).

### 4. Review gate

Show the assembled release notes to the user for approval. Do NOT create the release until the user explicitly confirms.

### 5. Publish

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
