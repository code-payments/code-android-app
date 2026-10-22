#!/usr/bin/env python3
"""Hotswap server. Same wire shapes as the iOS spike (~/dev/hotswap-spike/serve/server.py):

  POST /publish        watcher announces a patch: JSON with "seq" and "classes":[{name,file}]
  GET  /next?after=N   long-polls until seq > N, then answers the notice; 204 after 30s
  GET  /seq            the current seq, so a restarted app does not replay an old patch
  GET  /patches/<f>    dex bytes
  POST /report         the app's outcome; appended to reports.jsonl with a Mac receive time

usage: server.py [port=18492] [host=0.0.0.0]
"""
import http.server, json, os, sys, threading, time, urllib.parse

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "serve")
os.makedirs(os.path.join(ROOT, "patches"), exist_ok=True)
latest = {"seq": 0}
changed = threading.Condition()


class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def log_message(self, *a):
        pass

    def answer(self, code, obj=None):
        data = json.dumps(obj).encode() if obj is not None else b""
        self.send_response(code)
        if obj is not None:
            self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        if url.path == "/seq":
            return self.answer(200, {"seq": latest["seq"]})
        if url.path != "/next":
            return super().do_GET()
        after = int(urllib.parse.parse_qs(url.query).get("after", ["0"])[0])
        with changed:
            changed.wait_for(lambda: latest["seq"] > after, timeout=30)
            notice = dict(latest)
        self.answer(204) if notice["seq"] <= after else self.answer(200, notice)

    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            rec = json.loads(raw)
        except ValueError:
            rec = {"raw": raw.decode(errors="replace")}
        if self.path == "/publish":
            rec["publishedAt"] = int(time.time() * 1000)
            with changed:
                latest.clear()
                latest.update(rec)
                changed.notify_all()
            print("PUBLISH", json.dumps(rec), flush=True)
            return self.answer(204)
        rec["receivedAt"] = int(time.time() * 1000)
        with open(os.path.join(ROOT, "reports.jsonl"), "a") as f:
            f.write(json.dumps(rec) + "\n")
        print("REPORT", json.dumps(rec), flush=True)
        self.answer(204)


port = int(sys.argv[1]) if len(sys.argv) > 1 else 18492
host = sys.argv[2] if len(sys.argv) > 2 else "0.0.0.0"
http.server.ThreadingHTTPServer((host, port), H).serve_forever()
