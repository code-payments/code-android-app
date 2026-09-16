# Triage Brief: {{title}}

| Field | Value |
|-------|-------|
| **Bugsnag** | [{{error_id}}]({{error_url}}) |
| **Severity** | {{severity}} |
| **Events / Users** | {{events}} / {{users}} |
| **First seen** | {{first_seen}} |
| **Release** | {{release}} |
| **Production versionName** | {{production_version}} |
| **Experts** | {{experts}} |

## Root Cause

{{1-3 sentences explaining the root cause, referencing specific `.kt:NN` locations}}

## Evidence

- **Exception**: `{{errorClass}}`: {{message}}
- **Key stack frame**: `{{file.kt:NN}}` — `{{method}}`
- **Log excerpt**: `{{relevant log line(s)}}`
- **Breadcrumb trail**: {{last N breadcrumbs summarized}}
- **Device**: {{manufacturer}} {{model}}, Android {{os_version}}

## Fix Direction

{{Concrete description of what to change and where (`.kt:NN`), why it fixes the
issue, and any risks. NOT a full implementation — just the direction.}}

## Appendix: Stack Trace (in-project frames)

```
{{mapped in-project frames with file:line and method}}
```
