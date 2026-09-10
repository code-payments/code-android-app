#!/usr/bin/env python3
"""Summarise one bucket-matrix run.

Usage: scripts/spike/parse-bucket-log.py <run.log[.gz]> <run.sends>

Joins the sends file against the onMessageReceived traces on the spike_seq id,
so a push that never arrived and a push that arrived late are different
outcomes rather than the same silence. Written against a trace line observed on
device, not a guessed format:

  09-09 15:13:37.101 D/LoggingKt | trace (15895): onMessageReceived | \
seq=active-silent-001, has_body=false, actions=0, silent=true, bucket=active, \
latency_ms=-329, priority=1, original_priority=1
"""
import datetime
import gzip
import re
import statistics
import sys

TRACE = re.compile(r"onMessageReceived \| (.+)$")
FIELD = re.compile(r"(\w+)=([^,]+)")
# `adb logcat -v time` stamps lines "MM-DD HH:MM:SS.mmm" with no year.
STAMP = re.compile(r"^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})")


def read_log(path, year):
    """Return (first trace per seq, epoch-ms of the last stamped line).

    One pass: the captures run to tens of MB, and a `<(gunzip -c ...)` argument
    is a FIFO that cannot be read twice.
    """
    opener = gzip.open if path.endswith(".gz") else open
    seen = {}
    last = None
    with opener(path, "rt", errors="replace") as fh:
        for line in fh:
            stamp = STAMP.match(line)
            if stamp:
                last = stamp
            m = TRACE.search(line)
            if not m:
                continue
            fields = dict(FIELD.findall(m.group(1)))
            seq = fields.get("seq", "")
            if seq:
                # First arrival wins; FCM can redeliver.
                seen.setdefault(seq, fields)
    return seen, stamp_to_epoch_ms(last, year)


def stamp_to_epoch_ms(match, year):
    """Epoch-ms for one `adb logcat -v time` stamp, which carries no year.

    A run whose capture stops early makes the sends after that point look like
    delivery failures. They are not measurements at all, so they have to be
    separable from a push that was inside the window and never arrived.
    """
    if not match:
        return None
    mo, d, h, mi, sec, ms = (int(g) for g in match.groups())
    try:
        when = datetime.datetime(year, mo, d, h, mi, sec, ms * 1000)
    except ValueError:  # 02-29 against a non-leap year, etc.
        return None
    return int(when.timestamp() * 1000)


def pct(values, p):
    if not values:
        return None
    ordered = sorted(values)
    idx = min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1))))
    return ordered[idx]


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    log_path, sends_path = sys.argv[1], sys.argv[2]

    sent = []
    with open(sends_path) as fh:
        for line in fh:
            parts = line.split()
            if len(parts) == 2:
                sent.append((parts[1], int(parts[0])))

    year = (datetime.datetime.fromtimestamp(sent[0][1] / 1000).year if sent
            else datetime.datetime.now().year)
    seen, end = read_log(log_path, year)
    delivered = [q for q, _ in sent if q in seen]
    # Only a send the capture was still running for can be called missing.
    missing = [q for q, at in sent if q not in seen and (end is None or at <= end)]
    unobserved = [q for q, at in sent if q not in seen and end is not None and at > end]
    unsolicited = [q for q in seen if q not in dict(sent)]

    latencies = []
    downgraded = 0
    buckets = set()
    for seq in delivered:
        f = seen[seq]
        try:
            latencies.append(int(f["latency_ms"]))
        except (KeyError, ValueError):
            pass
        if f.get("priority") != f.get("original_priority"):
            downgraded += 1
        buckets.add(f.get("bucket", "?"))

    measured = len(delivered) + len(missing)
    print(f"sent:       {len(sent)}")
    print(f"delivered:  {len(delivered)}"
          + (f" ({100 * len(delivered) / measured:.0f}% of {measured} measured)" if measured else ""))
    print(f"missing:    {len(missing)}"
          + (f"  {', '.join(missing)}" if missing else ""))
    if unobserved:
        print(f"unobserved: {len(unobserved)}  {', '.join(unobserved)}"
              "  (capture ended before these were due — not delivery failures)")
    if unsolicited:
        print(f"unexpected: {len(unsolicited)}  {', '.join(sorted(unsolicited))}")
    print(f"bucket(s) reported by app: {', '.join(sorted(buckets)) or 'n/a'}")
    print(f"priority downgraded: {downgraded}/{len(delivered)}")

    # Group by the bucket the app reported, not the one the run asked for.
    # `am set-standby-bucket` is a request the OS re-evaluates mid-run, so the
    # two diverge and only the reported one describes the data point.
    by_bucket = {}
    for seq in delivered:
        f = seen[seq]
        try:
            by_bucket.setdefault(f.get("bucket", "?"), []).append(int(f["latency_ms"]))
        except (KeyError, ValueError):
            pass
    if len(by_bucket) > 1:
        floor = min(min(v) for v in by_bucket.values())
        print("per reported bucket, skew-corrected:")
        for b in sorted(by_bucket):
            v = sorted(x - floor for x in by_bucket[b])
            print(f"  {b:12s} n={len(v):3d}  median={v[len(v) // 2]}ms  worst={v[-1]}ms")

    if latencies:
        # latency_ms is device clock minus FCM sentTime, so it carries the
        # device/server clock skew. A small negative floor is skew, not a push
        # arriving before it was sent.
        print(f"latency_ms  min={min(latencies)}  median={int(statistics.median(latencies))}"
              f"  p95={pct(latencies, 95)}  max={max(latencies)}")
        print(f"  (skew floor {min(latencies)} ms; subtract it for a delivery-only figure)")
    else:
        print("latency_ms: none recorded")


if __name__ == "__main__":
    main()
