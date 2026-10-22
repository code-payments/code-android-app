#!/usr/bin/env python3
"""Watches a module's Kotlin sources; on a save, recompiles the module with Gradle, dexes the
class files that changed (d8) and publishes them to the hotswap server.

usage: watch.py --module :apps:flipcash:shared:chat --src apps/flipcash/shared/chat/src/main \
                [--server http://127.0.0.1:18492] [--once]
"""
import argparse, glob, hashlib, json, os, subprocess, sys, time, urllib.request

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SDK = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk"))
D8 = sorted(glob.glob(f"{SDK}/build-tools/*/d8"))[-1]
ANDROID_JAR = sorted(glob.glob(f"{SDK}/platforms/android-*/android.jar"))[-1]
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "serve", "patches")
os.makedirs(OUT, exist_ok=True)


def class_hashes(root):
    h = {}
    for p in glob.glob(root + "/**/*.class", recursive=True):
        h[os.path.relpath(p, root)] = hashlib.sha1(open(p, "rb").read()).hexdigest()
    return h


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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--module", required=True)
    ap.add_argument("--src", required=True)
    ap.add_argument("--server", default="http://127.0.0.1:18492")
    ap.add_argument("--classes", default=None, help="kotlin-classes dir (default: module build dir)")
    args = ap.parse_args()
    src = os.path.join(REPO, args.src)
    mdir = os.path.join(REPO, args.module.strip(":").replace(":", "/"))
    classes = args.classes or os.path.join(mdir, "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
    seq = int(json.load(urllib.request.urlopen(args.server + "/seq"))["seq"])
    before = class_hashes(classes)
    seen = newest_mtime(src)
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
        specs = []
        for c in changed:
            name = c[:-6].replace("/", ".")
            dexdir = os.path.join(OUT, f"tmp-{seq}-{len(specs)}")
            os.makedirs(dexdir, exist_ok=True)
            # Desugared (lambdas become synthetic classes, as in the installed dex) and one dex per
            # class: ART's RedefineClasses takes exactly one class per dex.
            d = subprocess.run([D8, "--min-api", "29", "--lib", ANDROID_JAR, "--classpath", classes,
                                "--file-per-class", "--output", dexdir, os.path.join(classes, c)],
                               capture_output=True, text=True)
            if d.returncode != 0:
                print("d8 failed:", d.stderr[-1500:], flush=True)
                continue
            for dp, _, fs in os.walk(dexdir):
                for f in fs:
                    if not f.endswith(".dex"):
                        continue
                    rel = os.path.relpath(os.path.join(dp, f), dexdir)[:-4]
                    fn = f"{seq}-{len(specs)}.dex"
                    os.replace(os.path.join(dp, f), os.path.join(OUT, fn))
                    specs.append({"name": rel.replace("/", "."), "file": fn})
        t_dex = time.time()
        post(args.server, "/publish", {"seq": seq, "classes": specs, "savedAt": t_save,
                                       "compileMs": int((t_compile - t0) * 1000),
                                       "dexMs": int((t_dex - t_compile) * 1000)})
        print(f"published seq {seq}: {[s['name'] for s in specs]} compile {t_compile-t0:.2f}s dex {t_dex-t_compile:.2f}s", flush=True)


main()
