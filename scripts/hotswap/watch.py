#!/usr/bin/env python3
"""Watches a module's Kotlin sources; on a save, recompiles the module with Gradle, dexes the
class files that changed (d8) and publishes them to the hotswap server.

usage: watch.py --module :apps:flipcash:shared:chat --src apps/flipcash/shared/chat/src/main \
                [--server http://127.0.0.1:18492] [--once]

--once builds one patch and exits, for loupe's hotswap extension: the seq comes from HOTSWAP_SEQ, dex
files go to HOTSWAP_PATCHES, the last stdout line is the notice JSON, and nothing is published. The
baseline hashes and the cumulative class set persist in serve/once-state/ between calls.
"""
import argparse, glob, hashlib, json, os, shutil, subprocess, sys, time, urllib.request

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SDK = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk"))
DEXDUMP = sorted(glob.glob(f"{SDK}/build-tools/*/dexdump"))[-1]
D8 = sorted(glob.glob(f"{SDK}/build-tools/*/d8"))[-1]
ANDROID_JAR = sorted(glob.glob(f"{SDK}/platforms/android-*/android.jar"))[-1]
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get("HOTSWAP_PATCHES") or os.path.join(HERE, "serve", "patches")
os.makedirs(OUT, exist_ok=True)


def class_hashes(root):
    h = {}
    for p in glob.glob(root + "/**/*.class", recursive=True):
        h[os.path.relpath(p, root)] = hashlib.sha1(open(p, "rb").read()).hexdigest()
    return h


def dex_class(path):
    """The class a one-class dex defines. d8 names synthetic lambdas by file ($0) differently from
    their descriptor (Foo$$ExternalSyntheticLambda0), and only the descriptor is what the app has loaded."""
    out = subprocess.run([DEXDUMP, path], capture_output=True, text=True).stdout
    for line in out.splitlines():
        if "Class descriptor" in line:
            return line.split("'")[1][1:-1].replace("/", ".")
    raise RuntimeError("no class descriptor in " + path)


def newest_mtime(src):
    m = 0.0
    for dp, _, fs in os.walk(src):
        for f in fs:
            if f.endswith(".kt"):
                m = max(m, os.stat(os.path.join(dp, f)).st_mtime)
    return m


def post(server, path, obj):
    req = urllib.request.Request(server + path, json.dumps(obj).encode(), {"Content-Type": "application/json"})
    urllib.request.urlopen(req, timeout=10).read()


def dex_changed(changed, classes, seq):
    """Dexes each changed class file into OUT, one dex per class, and returns [{name, file}]."""
    specs = []
    for c in changed:
        dexdir = os.path.join(OUT, f"tmp-{seq}-{len(specs)}")
        os.makedirs(dexdir, exist_ok=True)
        # Desugared (lambdas become synthetic classes, as in the installed dex) and one dex per
        # class: ART's RedefineClasses takes exactly one class per dex.
        d = subprocess.run([D8, "--min-api", "29", "--lib", ANDROID_JAR, "--classpath", classes,
                            "--file-per-class", "--output", dexdir, os.path.join(classes, c)],
                           capture_output=True, text=True)
        if d.returncode != 0:
            print("d8 failed:", d.stderr[-1500:], file=sys.stderr, flush=True)
            continue
        for dp, _, fs in os.walk(dexdir):
            for f in fs:
                if not f.endswith(".dex"):
                    continue
                fn = f"{seq}-{len(specs)}.dex"
                os.replace(os.path.join(dp, f), os.path.join(OUT, fn))
                specs.append({"name": dex_class(os.path.join(OUT, fn)), "file": fn})
        shutil.rmtree(dexdir, ignore_errors=True)
    return specs


def once(args, src, classes):
    """One patch for HOTSWAP_SEQ, then exit. See the module docstring."""
    seq = int(os.environ["HOTSWAP_SEQ"])
    state_dir = os.path.join(HERE, "serve", "once-state")
    state_path = os.path.join(state_dir, args.module.strip(":").replace(":", "_") + ".json")
    os.makedirs(state_dir, exist_ok=True)
    state = None
    try:
        state = json.load(open(state_path))
    except (OSError, ValueError):
        pass
    warning = None
    if state is None:
        # No state: the baseline is the classes dir before this compile, which matches the installed
        # app only right after an assembleDebug install. Warn if the classes are newer than the APK.
        before, cumulative = class_hashes(classes), {}
        apk = glob.glob(os.path.join(REPO, "apps/flipcash/app/build/outputs/apk/debug/*.apk"))
        newest = max((os.stat(p).st_mtime for p in glob.glob(classes + "/**/*.class", recursive=True)), default=0)
        if apk and newest > max(os.stat(p).st_mtime for p in apk):
            warning = ("classes dir is newer than the debug APK: classes compiled since the install are not in the "
                       "baseline, so their changes will not be patched. Run assembleDebug and reinstall for an exact baseline.")
    else:
        before, cumulative = state["before"], state["cumulative"]
        # Dex files of earlier calls must still be served; restore any the patches dir lost.
        for n, f in cumulative.items():
            keep = os.path.join(state_dir, f)
            if os.path.exists(keep) and not os.path.exists(os.path.join(OUT, f)):
                shutil.copy(keep, os.path.join(OUT, f))
    t0 = time.time()
    r = subprocess.run(["./gradlew", f"{args.module}:compileDebugKotlin", "--offline", "-q"],
                       cwd=REPO, capture_output=True, text=True)
    t_compile = time.time()
    if r.returncode != 0:
        print("compile failed:\n" + (r.stdout + r.stderr)[-3000:], file=sys.stderr)
        sys.exit(1)
    after = class_hashes(classes)
    changed = sorted(c for c, h in after.items() if before.get(c) != h)
    specs = dex_changed(changed, classes, seq)
    if len(specs) != len(changed):
        print("some classes failed to dex; state not saved", file=sys.stderr)
        sys.exit(1)
    for sp in specs:
        cumulative[sp["name"]] = sp["file"]
        shutil.copy(os.path.join(OUT, sp["file"]), os.path.join(state_dir, sp["file"]))
    if not cumulative:
        print("no class changed since the baseline; nothing to patch", file=sys.stderr)
        json.dump({"before": after, "cumulative": cumulative}, open(state_path, "w"))
        sys.exit(1)
    json.dump({"before": after, "cumulative": cumulative}, open(state_path + ".tmp", "w"))
    os.replace(state_path + ".tmp", state_path)
    t_dex = time.time()
    notice = {"classes": [{"name": n, "file": f} for n, f in cumulative.items()], "changed": [c[:-6].replace("/", ".") for c in changed],
              "compileMs": int((t_compile - t0) * 1000), "dexMs": int((t_dex - t_compile) * 1000)}
    if warning:
        notice["warning"] = warning
    if not changed:
        notice["warning"] = (warning + " " if warning else "") + "no class changed this call; re-sending the cumulative set"
    print(json.dumps(notice), flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--module", required=True)
    ap.add_argument("--src", required=True)
    ap.add_argument("--server", default="http://127.0.0.1:18492")
    ap.add_argument("--once", action="store_true", help="build one patch for HOTSWAP_SEQ into HOTSWAP_PATCHES and print its notice")
    ap.add_argument("--classes", default=None, help="kotlin-classes dir (default: module build dir)")
    args = ap.parse_args()
    src = os.path.join(REPO, args.src)
    mdir = os.path.join(REPO, args.module.strip(":").replace(":", "/"))
    classes = args.classes or os.path.join(mdir, "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
    if args.once:
        return once(args, src, classes)
    seq = int(json.load(urllib.request.urlopen(args.server + "/seq"))["seq"])
    before = class_hashes(classes)
    seen = newest_mtime(src)
    cumulative = {}  # class name -> dex file, for every class changed since the watcher started
    print(f"watching {src} (seq {seq}, {len(before)} classes)", flush=True)
    while True:
        time.sleep(0.05)
        m = newest_mtime(src)
        if m <= seen:
            continue
        seen = m
        t_save = int(m * 1000)
        t0 = time.time()
        r = subprocess.run(["./gradlew", f"{args.module}:compileDebugKotlin", "--offline", "-q"],
                           cwd=REPO, capture_output=True, text=True)
        t_compile = time.time()
        if r.returncode != 0:
            print("compile failed:\n" + (r.stdout + r.stderr)[-2000:], flush=True)
            continue
        after = class_hashes(classes)
        changed = sorted(c for c, h in after.items() if before.get(c) != h)
        before = after
        if not changed:
            print("no class changed", flush=True)
            continue
        seq += 1
        specs = dex_changed(changed, classes, seq)
        t_dex = time.time()
        for sp in specs:
            cumulative[sp["name"]] = sp["file"]
        specs = [{"name": n, "file": f} for n, f in cumulative.items()]
        post(args.server, "/publish", {"seq": seq, "classes": specs, "savedAt": t_save,
                                       "compileMs": int((t_compile - t0) * 1000),
                                       "dexMs": int((t_dex - t_compile) * 1000)})
        print(f"published seq {seq}: {[s['name'] for s in specs]} compile {t_compile-t0:.2f}s dex {t_dex-t_compile:.2f}s", flush=True)


main()
