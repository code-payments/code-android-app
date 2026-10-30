---
name: propose-analytics-event
description: Use when someone wants to add, change, or remove an analytics or Mixpanel event or property, or asks how to track, measure, or count something users do in the app. Written for non-engineers (marketing, growth, product) who describe what they want in plain English.
user-invocable: true
---

# Propose an analytics event

The person asking is usually not an engineer. They know what they want to measure, not how
the app is built. Your job is to turn that into a catalogue change an engineer can review and
wire up, in a **draft** pull request, without making them touch git, Kotlin, or Gradle.

The catalogue is `libs/analytics-events/events.toml`. Its header explains every field: read it
first. Both apps send events from this one file (iOS through the shared KMP package), so a
proposal here covers Android and iOS.

## Talk like this

Say screens, taps, and numbers. Never make the person deal with files, builders, Gradle, the
"share sheet", SharedCore, or KMP. If you must name an event, give its Mixpanel name.

| Instead of | Say |
|---|---|
| "add an `[[event]]` to events.toml" | "add a new event to the list both apps send" |
| "wire the call site" | "an engineer connects it to the button" |
| "share sheet" | "the phone's Share menu" |
| "property" | "a detail recorded with it, like which method they picked" |
| "Publish SharedCore" | "it reaches iPhone in a later release" |

## 1. Set up quietly

Run `git fetch origin` and `git switch -c feat/analytics-<short-slug> origin/code/cash`. The base
branch is `code/cash`. There is no `main`. Don't ask the person anything about this step.

## 2. Ask before you build

Ask one question at a time, and offer concrete choices taken from the real app (read the
screen's code to find them). Start each ask with question 1, then sort it with step 3. Only
asks that end up as new or changed events need questions 2 to 4. Don't edit anything until
those asks have all four answers:

1. **The question behind it.** "What will you decide or report with this number?" Their answer
   tells you whether an event is the right tool at all.
2. **The exact moment.** What they see and tap, and whether it counts **attempts** ("tapped
   Share") or **completions** ("the card was sent"). Words like "share", "engaged", "uses" and
   "converts" have several meanings. List the ones the app supports and let them pick. Check
   for other paths to the same outcome (a second share button, a share-to-chat screen).
3. **Breakdowns.** What they'd filter by in Mixpanel: method, source, success/failure.
4. **Both apps?** The default is yes.

## 3. Check what already exists

Read `events.toml` and `libs/analytics-events/EVENTS.md`. To see whether an event is actually
sent, grep for its builder outside tests:
`git grep -n 'ProfileEvents.cardShown(' -- apps ':!*/test/*'`. Then sort each ask into one row:

| What you find | What to do |
|---|---|
| An event already covers it and is sent | Tell them its Mixpanel name and what it counts. No PR. |
| It's in the catalogue but nothing sends it | Don't add a duplicate. Put it under "Needs wiring" in the PR. |
| An existing event is missing one detail | Add a property, or a value to an existing list. |
| It can be computed from existing events: thresholds ("3+ messages in a week"), funnels, retention, totals | Explain how to build it as a Mixpanel report. No app change. |
| The app can't see it: installs from a link, ad or campaign attribution, anything after the phone's Share menu, anything outside the app | Say so plainly, list it under "Not included" in the PR, and leave the design to engineering. Never relabel an existing event to stand in for it. |
| Genuinely new | Draft it (step 4). |

Before you recommend a Mixpanel report on an existing event, read that event's properties in
`EVENTS.md`. Does it also fire on failure (then the report filters on `State` or `Error`)? Does it
say which chat, group, or currency (most events don't, so "per group" is out of reach)? Tell the
person each limit.

"Needs wiring" covers only catalogued events that would answer one of their asks. Unrelated
unsent events don't go in the PR.

## 4. Draft, then confirm

Follow the existing naming: `Domain: Past-Tense Action` (`Group: Card Shown`), Title Case
property names, and reuse an existing list wherever its values fit. Copy the shape of a
neighbour in the same domain, including whether it sets `builder`. One action reached by
several routes is one event with a list property for the route, as `Group: Invite Shared` does
with `Method`. Different actions are different events. An event may fire when the phone's
Share menu opens, as long as its `description` says the app can't see what happens next. Never record personal
data: no names, phone numbers, emails, message text, or contact details.

Before you edit, show the person a short card for each event and wait for a yes:

> **Profile: Card Exported** — fires when someone saves or shares their profile card image.
> Records: Format (PNG or SVG). Answers: how many people pass their card on.

Renaming an event or property starts a new line in Mixpanel and breaks every existing chart
that uses it. Warn them before you agree to a rename, and offer to add a new one instead.

## 5. Edit only these files

- `libs/analytics-events/events.toml`: the entry, with a `description` that names the moment.
- `libs/analytics-events/EVENTS.md`: regenerated (step 6).
- `libs/analytics-events/src/commonTest/kotlin/com/flipcash/analytics/<Domain>EventsTest.kt`:
  one test per new or changed event that asserts the event name and properties as string
  literals, like its neighbours.
- `kmp/shared-core/spm/Sources/SharedCoreKit/AnalyticsEvents.swift`: one typealias, only for a
  new list or domain.

Change no app code: screens, view models, DI, Gradle. Wiring is the reviewer's job, and the
person proposing can't review Kotlin or Swift.

A property added to an existing event is always `optional = true`. The generated builder gives
it a `null` default, so the app keeps compiling and existing sends leave the property out until an
engineer wires it. If a build error points into app code after a change like this, the property
isn't optional: fix the entry, never the app.

## 6. Check it

Run these as two commands. In one command, Gradle may check before it writes:

```
./gradlew :libs:analytics-events:writeAnalyticsEventsPage
./gradlew :libs:analytics-events:checkAnalyticsEventsPage :libs:analytics-events:testAndroidHostTest
```

Gradle needs Java 21 and an Android SDK. If the SDK exists but `local.properties` is missing,
write `sdk.dir=<path>` to it (the file is gitignored). The build stops on a bad entry with a
message naming the event and the fix, such as asking for `builder = "..."` when two event names
collide. Apply that fix yourself.

If Gradle can't run here, edit `EVENTS.md` by hand to match the neighbouring entries exactly,
and say in the PR that it wasn't run locally. CI's Unit tests job runs the same check.

## 7. Open a draft PR

Commit as `feat(analytics): <what it measures>` and use the same line as the PR title. Then check
whether the person can push to the repo:
`gh repo view code-payments/code-android-app --json viewerPermission --jq .viewerPermission`.

- `WRITE`, `MAINTAIN` or `ADMIN`: run `git push -u origin HEAD` and
  `gh pr create --draft --base code/cash --assignee @me --reviewer bmc08gt --title "<title>" --body-file <file>`.
- Anything else: push to a fork. Run
  `gh repo fork --remote --remote-name fork` from inside the checkout. That forks `origin`,
  reuses their fork if they already have one, and leaves `origin` alone. Then `git push -u fork HEAD` and
  `gh pr create --repo code-payments/code-android-app --head <their-login>:<branch> --draft --base code/cash --title "<title>" --body-file <file>`,
  with their login from `gh api user --jq .login`. Leave out `--assignee` and `--reviewer`;
  GitHub refuses both from someone without write access. End the body with
  `cc @bmc08gt` so the review request still reaches the reviewer.

Don't ask the person to choose between these, and don't mention forks unless something fails.
If `gh` isn't signed in, tell them to run `gh auth login` and stop there.

Draft status matters: the catalogue says both apps send every event in it, and that stays
untrue until an engineer wires the event up.

The PR body has these sections:

- **Why**: the question from step 2, in the person's own words.
- **Events**: a table of Mixpanel name, when it fires, and what it records.
- **Where to send it**: per event, the screen and the moment, including every path to that
  moment from step 2. This section is for engineers, so name the Android class or function
  you found. For iOS, name the screen only, because this checkout has no iOS code. Note that iOS receives it only after the next Publish
  SharedCore release.
- **Needs wiring**: events already in the catalogue that nothing sends. Leave the section out
  if there are none.
- **Not included**: asks from step 3 that need a Mixpanel report or an engineering design, each
  with one line on why.

Then run `gh pr checks --watch`. If a check fails, read the log with `gh run view --log-failed`,
fix the catalogue, and push again. Push to the same remote as before.

On a fork PR, CI may not start until a maintainer approves the run. If `gh pr checks` reports no
checks, tell the person that's expected for a first contribution and that the reviewer will start
them; don't wait in a loop. If a check fails for a reason unrelated to the catalogue, such as a
missing secret, leave it for the reviewer and say so in a PR comment.

## 8. Close out with the person

In plain words, tell them what was proposed, what wasn't proposed and why, and what happens
next: an engineer reviews it and connects it in both apps, and the numbers appear in Mixpanel
after the next app release on each platform. Give them the PR link.
