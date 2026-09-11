# Spike results: do App Standby Buckets delay or drop silent pushes?

Plan: `docs/superpowers/plans/` in the orchestrator repo.
Spec: `docs/superpowers/specs/2026-09-08-push-preload-research.md`.
Raw traces and send manifests: `docs/spikes/raw/` — gitignored and local to the machine
that ran the matrix, because the captures are ~41 MB of logcat carrying unrelated device
chatter. Everything the conclusions rest on is quoted here.

**Status: all six charging cells collected, plus the deep-Doze cell they were missing.**
Device `SM02G4061915766`, model `Seeker`, Android 16 (API 36), throughout.

**Correction, 2026-09-10: every cell below measured delivery and nothing else.** The runner sent
`{spike_seq: …}` with no `flipcash_payload`, and the app derives all push-triggered work from that
key, so no cell exercised the push handling path — the trace reads `actions=0` on all 139 sends.
The delivery and latency figures are unaffected. Every claim about work is restated or withdrawn
below; see [The matrix never exercised the push handling
path](#the-matrix-never-exercised-the-push-handling-path).

Each cell: 20 high-priority pushes, 180 s apart, then a 300 s straggler hold. Latency comes
from `onMessageReceived`'s trace line, joined to the send manifest on `spike_seq`, so a push
that never arrived and a push that arrived late are different outcomes rather than the same
silence.

---

## Delivery

**139 observable sends, 139 delivered, 0 dropped, 0 priority downgrades.**

| Cell | Delivered | Priority downgraded | Bucket the app actually saw |
|---|---|---|---|
| `active` / silent | 20 / 20 | 0 | `active` for 4 sends, then `working_set` |
| `working_set` / silent | 19 / 19 observable | 0 | `working_set` throughout |
| `frequent` / silent | 20 / 20 | 0 | `frequent` throughout |
| `rare` / silent | 20 / 20 | 0 | `rare` throughout |
| `restricted` / silent | 20 / 20 | 0 | `restricted` throughout |
| `active` / visible | 20 / 20 | 0 | `active` throughout |
| `restricted` / silent, deep Doze | 20 / 20 | 0 | `restricted` throughout |

The `working_set` run's 20th send is not a miss. Logcat capture stopped at 18:44:02 and that
push was not due until 18:44:10 — the window was never observed, so it is excluded rather
than counted as a drop.

A seventh capture exists, `active-silent-20260909T151759.log.gz`, holding three sends from an
aborted first attempt. It is excluded from every figure here.

## Latency

The device clock runs behind the host that timestamps the sends, which puts a floor of about
−330 ms under every reading. Each run is corrected against its own floor (−322 to −397 ms
across the seven), which is what `parse-bucket-log.py` does for a single run; earlier revisions
of this document used one global floor and so quoted figures a few ms off these.

The floor is each run's own fastest sample, which is a noisy estimator of the true skew. Two
runs whose raw distributions match can end up tens of milliseconds apart after correction
purely because one happened to catch a faster outlier. Differences of that size between rows
should not be read as real.

| Bucket the app reported | n | median | p95 | worst |
|---|---|---|---|---|
| `restricted` | 20 | 61 ms | 233 ms | 444 ms |
| `active` | 24 | 96 ms | 203 ms | 116 s (see below) |
| `working_set` | 35 | 104 ms | 322 ms | 543 ms |
| `frequent` | 20 | 122 ms | 545 ms | 749 ms |
| `rare` | 20 | 138 ms | 529 ms | 836 ms |
| `restricted`, deep Doze | 20 | 131 ms | 316 ms | 317 ms |

The first five rows are charging cells, grouped by the bucket the app reported rather than the
one the run requested — see below. The `active` row mixes 4 silent observations with 20 visible
ones; every other row is silent only. The last row is the deep-Doze cell, kept separate because
its power state differs.

**Bucket depth did not gate delivery.** The most throttled bucket Android has, `restricted`,
delivered all 20 and posted the lowest median in the matrix. A 61-to-138 ms spread across five
buckets, with per-bucket p95s that overlap each other, is not a bucket effect — it is the
noise floor of the measurement. High priority is documented to bypass Doze and bucket
deferral, and on this device it does.

## A visible notification holds `active`; a silent push does not

`am set-standby-bucket active` is a request the OS re-evaluates, not a latch. In the silent
cell the runner re-asserted it every 45 seconds and the app still reported `working_set` from
the fifth push onward — roughly twelve minutes in.

The visible cell ran the same pinner against the same app and held `active` for all 20 sends,
just over an hour. The difference between the two runs is the notification: a push the user
can see is itself a signal that keeps the app in `active`, and a silent one is not.

Two consequences:

- **The silent `active` cell has n=4, not n=20.** Sixteen of its sends are `working_set`
  observations wearing the wrong label, which is why they are counted under `working_set`
  above.
- **Any measurement that assumes a pinned bucket is measuring something else.** The per-send
  bucket label in the trace is what makes this visible; without it the run would have produced
  twenty clean-looking `active` rows that were mostly not active.

Analysis has to key on the reported bucket rather than the requested one. This run's parser
does.

## One 116-second delivery, unexplained

`active-visible-001` was sent at 21:53:26 and reached `onMessageReceived` at 21:55:23.568,
which the app's own trace records as `latency_ms=115608`. Every other send in the matrix
landed inside 850 ms.

Two candidate explanations were checked against the capture and both fail:

- **Not the app freezer.** The delivery is preceded by
  `sync unfroze 6266 com.flipcash.app.android for 3`, but that line precedes *every* delivery
  in the cell — the process is frozen between pushes and unfrozen to receive each one. The
  other 19 unfreezes cost ~100 ms.
- **Not an FCM retry.** GMS arms a `FcmRetry` alarm alongside this delivery, but it does so on
  every delivery in every cell (29 to 45 per capture), so its presence says nothing about this
  message.

What is distinctive is position: this was the first push of a cell, sent six seconds after the
cell started and one second after logcat began recording. But the first pushes of the
`frequent` and `restricted` cells cost 384 ms and −122 ms, so "first of a cell" is not
sufficient on its own.

It stays on the record as one unexplained outlier in 119, not averaged away. A 116-second
delay is past the point where preloaded content would still be waiting when the user opens the
app, so if it recurs on battery it stops being a curiosity.

## The first six cells were measured on a charging device

`dumpsys battery` reported USB powered, 100%, and `dumpsys deviceidle get deep` reported
`ACTIVE` — deep Doze never engaged for any of the six.

That was built into the harness rather than overlooked: the runner drove the device over USB
adb, and a device on USB is charging and therefore Doze-exempt. So those six describe delivery
to a plugged-in phone, which is close to the best case and not the case the preload design has
to survive.

Two things closed the gap. `adb tcpip` took the runner off the cable — the connection needs an
`adb kill-server` after it, because the server holds stale state from the restart of `adbd` and
reports "No route to host" against a device that is plainly listening. And the runner now
samples power and idle state before every send into a `.power` sidecar, so the state a cell ran
in is a property of the capture rather than of a spot check taken beside it.

## `restricted` under deep Doze, on battery

**20 / 20 delivered, no priority downgrades, and the chat feed fetched successfully every
time.** `deep=IDLE` on all 20 pre-send samples; the only `IDLE_MAINTENANCE` reading is the
final one, taken after the straggler hold. The app reported `restricted` for all 20.

Delivery timing is indistinguishable from the same cell on mains:

| push → `onMessageReceived` | median | p95 | worst |
|---|---|---|---|
| deep Doze, battery | 131 ms | 316 ms | 317 ms |
| charging | 61 ms | 233 ms | 444 ms |

Do not read the median gap as a Doze penalty. The two runs' *uncorrected* medians are −266 and
−267 ms — the same number — and the whole difference comes from their skew floors, −397 ms
against −328 ms. The tail moves the other way, with Doze's worst case 317 ms against 444 ms.
Both cells sit inside the noise of the matrix.

**The push woke the process, and the process reached the network.** Waking a frozen process
proves nothing on its own if the network is still shut. On each of the 20 pushes the app brought
its gRPC channel from `CONNECTING` to `READY`, issued `GetDmChatFeedRequest`, and logged
`The request was processed successfully`:

| push → first successful RPC | n | median | p95 | worst |
|---|---|---|---|---|
| deep Doze, battery | 20 | 373 ms | 455 ms | 521 ms |
| charging | 21 | 524 ms | 1208 ms | 1891 ms |

So on this device, a `restricted`-bucket app in deep Doze on battery went from a silent push to
a completed chat-feed fetch in under 530 ms, worst case.

**That is not the preload path, and an earlier revision of this document said it was.** The trace
reads `actions=0` on all 20 of these pushes, so no `PushAction` ran and no push handling took
place. The fetch is the event stream reconnecting once the push woke the process. What the cell
establishes is the precondition for a preload — a push in the most throttled bucket, under forced
deep idle, wakes the process, and the process can then reach the network — rather than the preload
itself.

One error per push appears in both cells — `An error occurred while processing the request`,
about 20 s after delivery, following `EventStreamingController: Stream error: Event stream
timed out`. It occurs 20 times in the Doze cell and 21 times in the charging cell, once per
push in each, so it is the event stream's own reconnect behaviour and not something Doze did.

### What this cell does not establish

- **Forced idle is not naturally-entered Doze.** `deviceidle force-idle` applies the same
  restriction set but skips the motion and screen gating that normally precedes it. A phone
  that reached deep idle on its own, in a pocket, over hours, is a longer test than this one.
  This has since been run — see the natural-idle cell below. Delivery held; the app's network
  activity did not, though not for the reason first recorded here, since neither cell exercised
  push handling at all.
- **The battery was unplugged at the framework level, not physically.** Every power decision
  above the driver was taken as if on battery, which is where Doze decisions are made, but this
  says nothing about behaviour at a low charge level or under thermal pressure.
- **Wi-Fi adb held a live socket to the device throughout**, so the radio was up for the whole
  hour. A device with no debugger attached may let the radio idle harder.
- **One device, one OEM, one Android version.** Samsung's power management is its own; this
  result should not be read as an Android-wide guarantee.

## `restricted` under Doze the device entered on its own

The forced cell's first caveat was that `force-idle` skips the gating real Doze applies. That
cell has now been re-run with nothing overridden: phone physically unplugged, screen off, left
untouched until the framework reached deep idle by itself, over Wi-Fi adb. All 22 power samples
read `plugged=[ac=false,usb=false,wireless=false]`, and `dumpsys deviceidle` reported
`mForceIdle=false` throughout.

Raw: `raw/restricted-silent-natdoze-20260910T102101.{log,sends,power}`.

**Delivery survives the stronger condition.** 20 sent, 20 delivered, none missing, none
downgraded, every one reporting `bucket=restricted`.

**Latency does not.** Three consecutive pushes were held and then released together:

| seq | sent at | held | delivered at |
|---|---|---|---|
| 011 | +1816s | 182 ms | +1815.8s |
| 012 | +1999s | 397.9s | +2396.5s |
| 013 | +2182s | 216.3s | +2397.9s |
| 014 | +2363s | 35.0s | +2397.6s |
| 015 | +2545s | 0 ms | +2544.6s |

Three pushes sent three minutes apart arriving within 1.4 seconds of each other is a maintenance
window flushing a queue, not three independent delays. The `.power` sidecar agrees from the other
side: 21 of 22 samples read `deep=IDLE`, and the one that does not is the pre-send sample for 015,
which reads `deep=IDLE_MAINTENANCE`.

Split on that boundary, against this run's own skew floor of −405 ms:

| push → `onMessageReceived` | n | median | p95 | worst |
|---|---|---|---|---|
| between windows | 17 | 182 ms | 506 ms | 616 ms |
| held for a window | 3 | — | — | 397.9s |
| forced idle, for comparison | 20 | 131 ms | 316 ms | 317 ms |

Between windows, natural Doze looks exactly like forced Doze. The difference is entirely the tail,
and forcing is what hid it: `DOZE=1` re-asserts `force-idle` before every send, which suppresses
the maintenance windows this cell exists to observe. The forced cell did not measure a device that
had no deferral; it measured a device that was not allowed to flush.

### The app received all 20 and did no network work on any of them

The forced cell's stronger claim was that the app completed a chat-feed fetch on every push. That
does not reproduce. Counting log lines from the app's own pid, the same process (6266) in both
cells:

| tag | forced idle | natural idle |
|---|---|---|
| `LoggingKt \| trace` (deliveries) | 61 | 20 |
| `[RpcLogging]` | 314 | 0 |
| `[gRPC]` | 151 | 0 |
| `[event-streaming]` | 178 | 0 |
| `[BIDI]` | 60 | 0 |
| total app-pid lines | 4,957 | 391 |

`onMessageReceived` ran 20 times. No gRPC channel was opened, no RPC was issued, and no RPC
completed — including for 015, which arrived *during* the maintenance window.

**Neither cell asked the app to do anything.** Both read `actions=0` on every send, so the
difference between them is not one of push handling. What differs is that the forced capture's
event stream was alive and cycling — 41 `Stream error`, 15 `Event stream timed out` — and the
natural capture contains none of that machinery at all.

**The silence is not network denial.** Denied network leaves failed attempts behind. Pid 6266
logged no network activity of any kind across the hour: no attempt, no failure. The seven
`UNAVAILABLE` and four `NetworkException` lines in the capture belong to other processes —
location reporting, the sync service, Play services — and none to the app. It did not try.

**The control does not settle it either.** That run was itself `actions=0`, so it tested nothing
about push handling; what it shows is that a push to an awake device wakes the process into doing
work, which was never the point in doubt. An earlier revision of this document offered it as proof
that the app was healthy and the absence was therefore Doze. It does not support that. Whether the
forced/natural difference is Doze or app state — an event stream alive at 00:30 and gone by 10:20 —
is not resolved by anything in these two captures.

Incidentally the control prices the wake fan-out two parallel investigations have flagged: one
silent push cost eight successful RPCs, none of them asked for by the push.

### The aborted first attempt

`raw/restricted-silent-natdoze-20260910T095802.*` is a 5-send capture from the same condition,
kept because it is valid as far as it goes: 5/5 delivered, all five pre-send samples on battery at
`deep=IDLE`. It ended at send 5 when Wi-Fi adb dropped the session and `set -e` took the runner
down — the phone was still in unforced deep idle when it died. The harness now survives that; see
the defects section.

## The matrix never exercised the push handling path

Every trace line the matrix produced carries an `actions=` field, and across all seven cells it
reads `actions=0`. 139 sends, no exceptions, plus the awake control run afterwards.

`NotificationService.onMessageReceived` derives every unit of work from the `flipcash_payload`
data key. `planPushHandling` hands that to `syncActionsFor`, which returns an empty list when the
payload is null (`PushHandlingPlanner.kt:36`), and `onMessageReceived` returns before dispatching
anything. The runner sent `{spike_seq: …}` and nothing else, so the payload was null on every push
in every cell.

The matrix was built to tell a missing push from a late one, and it does that correctly — the
`spike_seq` correlation, the delivery counts and the latency figures are all unaffected. What it
cannot speak to is what the app does with a push, because it never sent one the app was built to
act on. The field naming that miss was in every line being read.

Three consequences, two for the cells above and one for the design.

**The forced cell's RPCs were real, and were not the preload.** They are locked to the sends: 40
successful RPCs, exactly two per push, each pair 450–650 ms after its own push trace, at the 180 s
send cadence. That is not background noise, and calling it ambient would be wrong. But with
`actions=0` no `PushAction` ran, so the fetch came from the event stream reconnecting once the
push woke the process — a wake effect, not push handling.

**The natural cell's silence was an app that was never asked.** Not a network it was denied: see
the section above for why the two look different in the capture.

**Silent preload was gated on a flag while these cells ran.** `planPushHandling` consulted
`PushSilentSync` when the title was null, and the flag defaulted to `false`, so a data-only push
did nothing on a default build however well formed its payload. The flag was on for the device
under test — established by sending one silent push carrying a payload and reading `actions=2`
back, against `actions=0` for all 139 committed sends. The flag has since been removed and a
data-only push plans its sync unconditionally, so a re-run needs no flag setup; every number below
was still measured with it on.

This also constrained how the remaining cells could be run. A visible push cannot measure Doze:
posting the notification lights the screen, and screen-on ends deep idle. One visible smoke push
took the device from `IDLE` to `ACTIVE`. So a Doze cell has to be silent.

## Re-run with a payload the app acts on — four samples, then the phone went away

`raw/restricted-silent-natdoze-20260910T113729.*`. Twelve silent pushes were planned, 180 s apart,
`restricted`, natural deep idle on battery, carrying `flipcash_payload=IAU=` — a two-byte
`flipcash.push.v1.Payload` holding `category=CONTACT_JOIN` and nothing else, which plans
`RefreshFeed` + `SyncContacts` without naming a chat or a contact.

**The cell aborted at send seven.** Wireless adb went unreachable at sends five, six and seven, and
the runner's three-consecutive-failure guard stopped it. Sends five and six went out but were never
observed, so the traced sample is four.

**All four handled pushes behaved identically, and the handler completed.**

| Reading | Value |
|---|---|
| Traced pushes | 4, all `actions=2`, all pid 6266 |
| Power state at every sample | `deep=IDLE`, 5 of 5 |
| `GetDmChatFeedRequest` | 8 — two per push |
| `The request was processed successfully` | 8 — two per push |
| Contact RPCs | 8 — two per push, all `UNAVAILABLE: contact list service disabled` |
| Trace line to both feed RPCs complete | 0.51–0.65 s |

The contact failures are not network failures. `contact list service disabled` is an application
answer from the backend, which means the RPC left the device, reached the server and came back. So
both halves of the planned action list ran: `RefreshFeed` fetched, and `SyncContacts` was refused
for a reason that has nothing to do with Doze.

**What four samples support:** the push handling path is not blocked under Doze the device entered
on its own, on a `restricted`-bucket app that is not on the Doze allowlist. That is the thing seven
earlier cells and 139 sends never tested.

**What they do not support:** any rate. Four consecutive successes cannot distinguish "always works"
from "works until the maintenance window moves", and the cell was designed for twelve partly to see
whether behaviour degrades across a longer idle. The re-run below supplies the missing eight. The adb
drops that ended this cell look like Wi-Fi power management during idle, which is the same failure the
`9b21c6b76` retry loop was added for and did not survive.

## The same cell, finished — twelve samples, and the app got killed halfway through

`raw/restricted-silent-natdoze-20260910T131820.*`. Identical settings to the aborted run:
`restricted`, silent, 180 s apart, natural deep idle on battery, `flipcash_payload=IAU=`. Twelve
sends, twelve traced.

| Reading | Value |
|---|---|
| Delivered | 12 of 12, 0 missing |
| Priority downgraded | 0 of 12 |
| `actions=2` | 12 of 12 |
| Power state | `deep=IDLE`, `light=OVERRIDE`, `bucket=45` at all fourteen samples — `start`, twelve `pre-`, `end` |
| Latency | min -335, median -81, p95 695, max 939 ms (skew floor -335 ms) |
| Feed RPCs per push | 4 `GetDmChatFeedRequest`, 2 completions, uniform across all twelve |
| Contact RPCs per push | 2, all `UNAVAILABLE: contact list service disabled` |

Four request lines per push against two completions is twice what the four-sample cell logged. The
shape is two requests within 5 ms of the trace line, then two more that each complete: pushes one
through eight had 5.4 s between the pairs, pushes nine through twelve had 0.35 s. That is two
callers rather than a retry: the push's `RefreshFeed` and the event stream's reconnect handler both
call `FeedSyncDelegate.syncFeed()`, which cancels any sync already in flight before launching its
own, so the loser is cancelled after it has issued its RPCs. See below.

Combined with the aborted cell, that is sixteen payload-carrying pushes at `deep=IDLE` in
`restricted`, all delivered, all planning two actions, all reaching the network.

### The app was SIGKILLed mid-cell, and the next push brought it back

Between send five and send six the platform killed the app:

```
13:31:22.390 I/ActivityManager: Killing 1307:com.flipcash.app.android/u0a309 (adj 905):
             excessive cpu 13300 during 300059 dur=854340751 limit=2
```

`adj 905` is the cached bucket, so this is the background CPU killer: 13.3 s of CPU inside a 300 s
window, about 4.4%, against a 2% limit. It happened three times across the two payload cells —
pid 6266 at 11:50:15 (7,240 ms), pid 1307 at 13:31:22 (13,300 ms), pid 3633 at 13:56:15 (6,730 ms).
The last of those landed during the straggler hold after send twelve, so it cost no samples; the
first ended the four-sample cell.

What happened next is the finding. Send six arrived 129 s after the kill, into no process at all:

```
13:33:30.790 START_DEBUG: ProcessRecord{8f1da92 3633:com.flipcash.app.android/u0a309}
13:33:31.468 onMessageReceived | seq=restricted-silent-006, actions=2, silent=true, bucket=restricted
13:33:34.848 authState change Unknown => Authenticating
13:33:34.864 database init start ZaFTTj4o
13:33:34.878 database init end
13:33:35.014 authState change Authenticating => Ready
13:33:38.074 Request: [GetDmChatFeedRequest ...
```

A data-only push, to a `restricted`-bucket app, under deep idle the device entered on its own, cold-
started the process, opened the Room database, completed soft login and fetched. Sends seven through
twelve then ran in pid 3633 exactly as one through five had run in 1307.

The cost is startup latency, not delivery: 6.6 s from the push to the first RPC on the cold process,
against 14 ms warm. For a preload that is the difference between content being ready and content
arriving while the user is already looking at the screen.

### What burned the CPU: the wake, not the stream

The bill is push-driven almost in full. Binned into 30 s buckets, the process logs a ~415-line burst
at each push and nothing at all in between — the quiet stretches are two minutes wide and completely
empty. The stream traffic that looked like a free-running reconnect loop is per-push and one-to-one:
12 `onMessageReceived`, 24 `flipcash-stream => CONNECTING` (two per push), 12 `=> READY`, 11 `Event
stream down, syncing feed and reconnecting`, 10 `Event stream timed out`. Every one of them falls
inside a burst.

Regressing each kill's CPU against the app-owned log lines inside its own 300 s window:

| pid | window | CPU | app lines | ms/line |
|---|---|---|---|---|
| 1307 | 13:26:22–13:31:22 | 13,300 ms | 861 (two bursts) | 15.45 |
| 3633 | 13:51:15–13:56:15 | 6,730 ms | 431 (one burst) | 15.61 |
| 6266 | 11:45:15–11:50:15 (aborted cell) | 7,240 ms | 808 | 8.96 |

The two windows from the same run agree to within 1%. Solving them for a per-burst cost and an idle
rate gives **~6.7 s of CPU per push**, plus a residual of ~0.5 ms per idle second. Only the first
term survived direct measurement — a frozen process reads zero ticks, and the residual turned out to
be burst edges attributed to the gap beside them. See *An untouched cached process costs nothing,
because it is frozen* below.

That reverses the cadence caveat. 6.7 s is 2.24% of a five-minute window on its own, so **one push
per five minutes already exceeds the 2% limit**, and break-even is a push every ~335 s. The 180 s
cadence decides how fast the kill arrives, not whether it arrives.

Within a burst the work is front-loaded. Taking pid 3633's 13:36:31 push, 435 lines over ~88 s:

| Phase | Wall | Lines |
|---|---|---|
| Wake → stream `READY` (reconnect, 162 SQLite statements) | 6.8 s | 205 |
| Feed sync + contact sync | 0.9 s | 207 |
| Stream alive, 5 s pings | 39.4 s | 7 |
| Stream times out, second reconnect cycle | 41.1 s | 16 |

95% of the logged work is in the first 7.7 s. Holding the stream open is nearly free;
re-establishing it is not.

### Why a cached process holds a stream open at all

`RealChatCoordinator.onStop` does tear the stream down — `stopHeartbeat()` then `close()`. It cannot
run here. `onStop` requires the process lifecycle to have reached STARTED, and a push-woken process
never does: the capture has zero `Lifecycle resumed` and zero `teardown complete` lines across 40
minutes. pid 3633 was created for an FCM broadcast and never had a UI.

The stream gets opened anyway. Soft login during push handling fires `Events.OnLoggedIn`, which
reaches `onUserLoggedIn`, which calls `open()` and `startHeartbeat { syncFeed() }` with no lifecycle
check; the connectivity observer in `wireDelegateRouting()` calls `open()` on the same terms. A
headless process therefore acquires a stream and a supervisor that nothing in that process can stop.

The freezer turns that into per-wake cost. A cached process is SIGSTOPped, so the 30 s heartbeat tick
and the stream's own ping timeout both come due while it is suspended and both fire on the next thaw.
Every burst opens the same way:

```
13:36:31.815 flipcash-stream => CONNECTING
13:36:31.815 Event stream down, syncing feed and reconnecting
13:36:31.816 event-stream: Timed out, signaling error for reconnect
```

The backoff policies are not at fault: `OpenStream` and `EventStreamDelegate.reopenBackoff` both cap
at 30 s and neither spins. The cost is that each wake pays a fresh gRPC connect plus a full
`syncFeed()` from `onReconnect`, on top of the `RefreshFeed` the push asked for.

Four changes follow, in cost order:

1. Gate `open()` and `startHeartbeat()` on the process lifecycle having reached STARTED, in
   `onUserLoggedIn` and in the connectivity observer. A push-woken process then does its push work
   and never touches the stream, which removes the reconnect, the pings and the second reconnect
   cycle.
2. Make the heartbeat freeze-aware — compare wall clock across the `withTimeoutOrNull` wait, so a
   180 s gap on a 30 s tick reads as *we were frozen* rather than *the server dropped us*.
3. Coalesce `syncFeed()` within a short window instead of cancelling and relaunching, so two callers
   cost one round trip.
4. Skip the contact `FullUpload` when the service is disabled. It ships the entire phone book on
   every push and is refused every time.

**One caveat on the 6.7 s.** The capture build had `TraceManager.includeRpcBodies = true`, so
`LoggingClientCallListener` builds a full proto `toString()` of every request and response — about
55 KB per burst, including the whole contact list and the whole feed with blurhashes and CDN URLs —
and `SQLiteTime` logs every statement at VERBOSE, ~285 per burst. A release build constructs none of
those strings. Treat 6.7 s as an upper bound and re-measure with bodies off before quoting it as a
production figure. The structure it describes — a headless process reconnecting a stream and
full-resyncing on every wake — does not depend on the logging.

### What this says about option B's Android half

- **Reliability, as far as twelve samples can say it.** No degradation across 33 minutes of
  continuous idle, and no drop when the process underneath changed.
- **Process death is not a delivery failure here.** FCM re-created the process and the handler ran.
  The preload design does not need the app to stay resident.
- **The CPU killer is a real constraint, and it is the push path that trips it.** Three kills in
  two cells, at 2.2–4.4% average CPU while cached. Each push costs ~6.7 s of CPU, which is 2.24% of
  a five-minute window on its own, against an idle cost of zero, since a cached process is frozen
  rather than merely quiet. The cadence accelerates the kill rather than causing it, and the ten
  `Event stream timed out` errors and 24 `flipcash-stream => CONNECTING` transitions are not work
  running alongside the pushes — they are two reconnects per push, triggered by the wake. Preload
  work would land inside a window that is already over budget.
- **`actions=2` is the plan, not the outcome.** It is what `planPushHandling` returned. The feed
  RPCs prove `RefreshFeed` ran; `SyncContacts` was refused by the backend. Neither is instrumented
  to report completion.

One device, one OEM, one Android version — unchanged.

## Where this leaves the kill criterion

The criterion was: *rare-bucket delivery materially better than documented weakens the argument
against option A; delivery as poor as documented settles it.*

Measured, delivery is better than documented — not just in `rare` but in `restricted`, and with
no drops anywhere. On the evidence collected, bucket assignment is not a reason to reject
option A.

**The measurement the argument turns on has now been taken twice, and delivery agrees both
times.** The most throttled bucket Android has, in deep idle, on battery, delivered all 20
pushes — with idle forced, and again with idle reached naturally. Bucket assignment is not a
reason to reject option A on this device.

**What neither cell supports is any claim about the preload running.** Both read `actions=0`
throughout, so the forced cell's chat-feed fetch was the event stream reconnecting after the wake
and the natural cell's silence was an app that was never asked to do anything. Sizing option A
against either would be sizing it against the wrong measurement.

**The re-run is the first cell to put the question properly, and across both attempts it says the
path is not blocked.** Sixteen pushes handled at `deep=IDLE`, sixteen times `actions=2`, feed RPCs
that completed every time. Twelve of those ran consecutively across 33 minutes of unbroken idle with
no degradation, which is as close to a rate as this rig produces.

**What the finished cell added was a constraint the matrix was not looking for.** The platform
SIGKILLed the app mid-cell for excessive background CPU, three times across the two payload cells,
and delivery survived it — the next push cold-started the process, which opened the database and
authenticated before fetching. Delivery is not what the CPU killer threatens. Latency is: 6.6 s to
the first RPC cold against 14 ms warm.

Generality is unchanged — one device, one OEM, one Android version. The other open question has
shrunk: the natural cell's silence was an app with nothing to do, and the re-run shows that same
process reaching the network under natural idle once the payload asks it to. The forced cell's
unprompted reconnect traffic has closed too, and it was never about Doze: a push-woken process opens
the event stream during soft login and never reaches the lifecycle state that would close it, so
every thaw fires the heartbeat and the stream's ping timeout together and pays a reconnect plus a
full feed sync. Note that the
spec's objection has narrowed rather than closed: high-priority FCM was never the missing
ingredient, since every push in every cell was sent `priority: "high"` and none was downgraded.

## Harness defects found and fixed

- **`run-bucket-matrix.sh` exited 0 when the device was gone**, producing three empty cells
  that looked like completed work. It now fails fast on a missing device, and checks again
  after the straggler hold so a mid-run disconnect is reported rather than read as a delivery
  failure.
- **`adb logcat` hung for ~16 minutes** after the device vanished, which is why the first
  `working_set` cell reports finishing at 19:00:39 when its last trace is 18:44.
- **The device model and Android version were unrecorded.** The runner now writes them into
  each log's header before logcat starts appending.
- **`parse-bucket-log.py` read the log twice**, which silently returned zero deliveries for a
  `<(gunzip -c …)` argument, since a process-substitution FIFO can only be read once. It now
  makes a single pass and opens `.gz` natively.

- **Nothing recorded the power state a cell ran in**, so the charging caveat on the first six
  cells rests on spot checks taken beside the captures rather than on the captures themselves.
  The runner now writes AC/USB/wireless power, deep and light idle state, and the standby bucket
  to a `.power` sidecar before every send, and `DOZE=1` aborts the run if the device does not
  reach `IDLE` instead of quietly measuring the charging case again.

**A dropped Wi-Fi adb session killed the cell rather than a sample.** The first natural-idle
attempt died at send 5 of 20 on `adb: device offline`, with the phone still in unforced deep idle
on battery — TCP 5555 was open when probed and the device re-authorized within ten seconds on its
own, so adbd had reset the session and nothing about the device had changed. Under `set -e` a
failed `dumpsys` inside `power_sample` took the run down, and logcat, which is the capture, died
with it. `adb_try` now retries one-shot calls for `ADB_GRACE` seconds and `power_sample` degrades
to `unreachable`; three consecutive unreachable sends still fail the run. logcat runs under a
supervisor that reattaches from the last stamped line and marks the gap. Fixed in `9b21c6b76`; the
re-run recorded zero gaps.

**The runner sent a payload the app is built to ignore**, which is why seven cells measured
delivery and nothing else. `PAYLOAD` now injects a base64 `flipcash.push.v1.Payload` as the
`flipcash_payload` data key. Checked against the device before use: visible plus payload reads
`actions=3`, silent plus payload reads `actions=2`, against `actions=0` for all 139 committed
sends. This was the costliest defect in the harness — the `actions=` field that exposes it was
present in every trace line the earlier cells were read from.

**A visible push cannot be used to measure Doze.** Posting the notification lights the screen and
screen-on ends deep idle; one visible smoke push took the device from `IDLE` to `ACTIVE`. Doze
cells have to be silent, which on the build these cells ran against meant `PushSilentSync` on.

**`DOZE=natural` woke the device it was waiting on.** The setup pressed `KEYCODE_HOME` to
background the app. HOME is a wake key, so on a screen-off phone it lit the display and restarted
the idle countdown. Skipped under `natural`, where the device is already asleep and the app already
backgrounded.

## Sizing the preload against the 2% ceiling

The killer's rule is a budget: 2% of a 300 s window is **6000 ms of CPU**, and a cached process that
spends more than that is SIGKILLed. How many pushes fit is that budget minus what the process costs
while nothing is happening, divided by what one push costs. The regression above produced both terms
from log volume. One of them has now been read directly.

### An untouched cached process costs nothing, because it is frozen

Sampling `/proc/<pid>/stat` utime+stime once a minute for **906 s**, with the app cached, the screen
off and the phone physically unplugged, the counters do not move at all:

| | |
|---|---|
| Samples | 16, at 60 s |
| Span | 906 s |
| `oom_score_adj` | 910 throughout (kills have been seen at 700, 900 and 905) |
| `cgroup.freeze` | `/sys/fs/cgroup/apps/uid_10309/pid_5303/cgroup.freeze` = 1 |
| utime + stime | 2221 + 199 ticks at the first sample, and at all fifteen after it |
| Delta | **0 ticks** |

`CLK_TCK` is 100, so a tick is 10 ms and zero ticks over 906 s bounds idle at under 11 µs per second
— under 10 ms of the 6000 ms allowance, whichever 300 s window you take. The mechanism is in the
third row: the process is frozen, so it is not sleeping cheaply, it is not running at all.

**That is not the quantity the regression called idle.** Its ~0.5 ms per idle second was fitted
across windows in which pushes arrived every 180 s, so it is the residue either side of a burst —
thaw, timer catch-up, the stream's ping timer — attributed to the gap it sat in. Measured with
nothing arriving, the floor is zero. The practical consequence is that the budget has one term: the
whole 6000 ms is available to push work, and what remains is the cost of one push and how many of
them a window holds.

### The answer, at the measured cost

The instrument has now been run. `scripts/spike/measure-push-cpu.sh` brackets `/proc/<pid>/stat`
utime+stime around each send, takes a push-free window first for the idle term, and gates each window
on the process having actually reached cached before the push lands.

Twelve endpoint brackets across three cells, plus one burst sampled at 2 s:

| Cell | Spacing | n | Per-push (ms) | Mean |
|---|---|---|---|---|
| 240 s, first attempt | 240 s | 4 | 1810, 2200, 1830, 2180 | 2005 |
| 240 s, repeat | 240 s | 3 | 1930, 1990, 2290 | 2070 |
| 420 s | 420 s | 5 | 2680, 2210, 2430, 1970, 2360 | 2330 |
| 2 s profile | single push | 1 | 2030 total | — |

**A push costs about 2.2 s of CPU, not the 6.7 s the regression inferred.** That number came from log
volume; read off the counters it is a third of the size. Bodies are still on for every figure here —
this is a `debug` install, so `includeRpcBodies` is true and each RPC still builds a full proto
`toString()` — so 2.2 s is also an upper bound on production, just a much tighter one.

The profile says where it goes: cumulative 1760 ms at t=2 s of a 2030 ms total, then roughly 10 ms
per 2 s out to t≈120 s. **87% of a burst is in its first two seconds.** The tail is the adj-700
previous-app decay winding down, not work worth trimming.

### The 300 s window is uptime, not wall clock

Dividing the budget by the cost gives 6000 / 2200 = 2.7 pushes per five minutes. The device
disagrees, and not in the direction a margin would explain:

| Cell | Process between pushes | Pushes | Outcome |
|---|---|---|---|
| 120 s spacing | never frozen, adj 0 then 700 | 10 | survived |
| 240 s spacing | cached and frozen | 5 | killed on #5 |
| 240 s spacing, repeat | cached and frozen | 4 | killed on #4 |
| 420 s spacing | cached and frozen | 5 | survived |

The 120 s row is the voided first attempt, whose readings are unusable because it never backgrounded
the app. Whether the process survived is not a reading, so that row still counts, and it is the row
that makes the pattern impossible to read as a rate: ten pushes at the tightest spacing survived
while four at an intermediate spacing did not. No rate expressed in wall-clock seconds produces that
ordering. The kill record from the repeat cell
resolves it:

```
am_kill: [0,12639,com.flipcash.app.android,900,excessive cpu 6230 during 300043 dur=1750357 limit=2,249060]
```

6230 ms charged. The three windows measured for that process sum to 1930 + 1990 + 2290 = **6210 ms**.
The instrument and AMS agree to 0.3%, which retires the discrepancy this document previously could
not account for. But those three bursts are 240 s apart, so first to last is at least 480 s of wall
clock, and AMS calls the interval between them `300043`. **The window is 300 s of `uptimeMillis()`,
which does not advance across suspend.**

How much wall clock a window covers is then a question about how much the phone suspends. Two
readings of `dumpsys batterystats`, 421 s apart with the device asleep and no pushes arriving, differ
by 142 s of uptime: **33.7%**, or a 300 s window stretched across 890 s. Over the whole 14 h on
battery the figure is 38.4%. Neither is the number that applies during a push cadence, because each
push wakes the device and buys back uptime.

The cells bracket that number better than either aggregate does. Three sends 240 s apart were charged
to one window, so the window covers more than 480 s of wall clock. The 420 s cell survived, so fewer
than three of its sends fit, putting the window at 840 s or less. A 300 s uptime window during a
cadence like this therefore spans between roughly 480 s and 840 s, and every cell follows:

| Cell | Wall clock per window | Pushes inside it | CPU | vs 6000 ms |
|---|---|---|---|---|
| 120 s, never frozen — device stays awake, so uptime ≈ realtime | 300 s | 2.5 | ~5500 ms | under, survived |
| 240 s, frozen | 480-840 s | 2.0-3.5 | 4400-7700 ms | over at the observed 3, killed twice |
| 420 s, frozen | 480-840 s | 1.1-2.0 | 2500-4400 ms | under, survived |

Keeping the process out of the freezer is also what keeps the device out of suspend, so the tightest
cadence is safe for the same reason the widest one is: neither manages to compress three bursts into
a single window.

**One correction to the idle table further up.** It recorded the kills as being at `adj 905`, which
reads as though the killer only reaches fully cached processes. It does not. The two kills in these
cells name `adj 700` and `adj 900`, and AMS checks any process at `setProcState >= PROCESS_STATE_HOME`,
which
includes the previous-app slot. Handling a push promotes the process from 900 to 700 for about 135 s;
that promotion does not buy it immunity.

### What this means for the preload

The budget is 6000 ms per 300 s of uptime and a push costs ~2.2 s, so the app can absorb **two pushes
per uptime window**. The third is what killed it in both 240 s cells.

Turning that into a cadence needs the suspend ratio, which is a property of the user's device and
day rather than of the app. Two pushes per 840 s is **one push every seven minutes**, which is what
420 s delivered: five consecutive pushes, no kill, and by the same bracket 2500-4400 ms against a
6000 ms budget. 240 s killed in both attempts. Treat 420 s as the fastest cadence observed to be
safe, and note the margin is thinner than the ratio 6000/2200 suggests — at the unfavourable end of
the bracket, 420 s is already using 73% of the budget.

A chat preload driven by message arrival will exceed that on any active conversation, which leaves
two options, and the burst shape chooses between them. Coalescing server-side costs one burst per
window whatever the message volume. Trimming the handler has 87% of its target inside the first two
seconds, in the sync RPC fan-out, and almost nothing in the tail — so it is a fan-out problem, not a
timer problem.

### What this does not establish

Bodies were on for all of it. Production sets `includeRpcBodies` false, so the real per-push cost is
below 2.2 s by a margin nothing here measures. The `benchmark` variant would attribute it: it is
`initWith(debug)` with `isDebuggable = false` and shares the `contributors` signing key, so it
installs over the existing build and keeps the login. That run replaces the app on the test device
and was not made.

The four fixes listed above are unmeasured against the new baseline. Each targets work inside the
first seconds of a burst, which is still where the cost sits, but 2.2 s leaves less to reclaim than
6.7 s did.

The first 240 s cell's kill charged 7190 ms where its four preceding windows sum to 8020. A window
boundary falling inside a burst rather than between bursts would explain it, but that was not
confirmed; only the repeat cell's charge lines up closely enough to stand as evidence by itself.

The suspend ratio during a cadence was bracketed, not measured. The 480-840 s span comes from which
cells lived and died, so it is only as tight as the two spacings tried; a cell between them would
narrow it. The direct readings either side — 33.7% over a quiet window, 38.4% over the battery's
lifetime — are both from periods that are not a push cadence.

Generality is unchanged from the rest of this document: one device, one OEM, one Android version. The
uptime ratio travels worst of all the numbers here, since it is set by how much a given phone
suspends, which varies by device, by user, and by whatever else is installed.
