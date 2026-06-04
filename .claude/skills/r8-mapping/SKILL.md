---
name: r8-mapping
description: >
  Download and use the R8 mapping file for a Flipcash release build to
  deobfuscate stack traces. Usage: /r8-mapping <versionCode>
user-invocable: true
argument-hint: "<versionCode>"
allowed-tools:
  - Bash
  - Read
  - Grep
---

# R8 Mapping: download and deobfuscate

Download the R8 `mapping.txt` for a Flipcash release build and use it to
deobfuscate stack traces.

## Background

Release builds are minified/obfuscated by R8. The mapping file is uploaded as
part of the `release-artifacts` artifact in the "Flipcash2 Build and Deploy"
GitHub Actions workflow (ID `229420296`). The mapping lives at
`mapping/release/mapping.txt` inside the artifact.

**R8 class merging**: R8 may merge multiple unrelated classes into one
obfuscated class. Use line numbers from the stack trace to disambiguate which
original class a frame belongs to.

## Step 1 — Download the mapping

Parse `$ARGUMENTS` for a versionCode (integer). If not provided, ask the user.

```bash
bash .claude/skills/r8-mapping/scripts/r8-mapping.sh <versionCode>
```

The script emits JSON:

```json
{
  "mapping_path": "/tmp/r8-mapping-3797/mapping/release/mapping.txt",
  "run_id": 26846060877,
  "version_code": 3797,
  "line_count": 1211075
}
```

## Step 2 — Deobfuscate classes

To find what an obfuscated class name maps to:

```bash
grep " -> <obfuscated_class>:" <mapping_path>
```

This returns lines like:
```
com.original.ClassName -> ag3:
com.other.MergedClass -> ag3:
```

Multiple results means R8 merged those classes. Use stack trace line numbers
to disambiguate — each class section in the mapping contains line-number
ranges for its methods.

## Step 3 — Deobfuscate methods

After finding the class section, look for method mappings within it:

```bash
# Find the class section and its method mappings
grep -A 200 "^com.original.ClassName -> <obfuscated>:" <mapping_path> | head -200
```

Method lines look like:
```
    1:5:void run():123:127 -> run
    6:10:void otherMethod():45:49 -> a
```

The format is:
```
    <obfuscated_line_start>:<obfuscated_line_end>:<return_type> <original_method>(<params>):<original_line_start>:<original_line_end> -> <obfuscated_method>
```

Match the line number from the stack trace against `obfuscated_line_start:obfuscated_line_end`
to find the original method and its original line numbers.

## Output

Present deobfuscated stack frames clearly:

```
Original: com.original.ClassName.methodName (ClassName.kt:127)
  Obfuscated: ag3.run (SourceFile:3)
```
