import http.server
import os
import re
import subprocess
import urllib.parse


ROLE = os.environ.get("ROLE", "base")
DB_ENV = dict(os.environ, PGPASSWORD=os.environ["DB_PASSWORD"])


def sql(statement):
    result = subprocess.run(
        [
            "psql",
            "-h",
            os.environ.get("DB_HOST", "postgres"),
            "-U",
            os.environ.get("DB_USER", "backline"),
            "-d",
            os.environ.get("DB_NAME", "backline"),
            "-At",
            "-v",
            "ON_ERROR_STOP=1",
            "-c",
            statement,
        ],
        env=DB_ENV,
        capture_output=True,
        text=True,
        timeout=5,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            try:
                sql("SELECT 1")
                self.reply(200, "ok")
            except Exception as error:
                self.reply(503, str(error))
            return
        match = re.fullmatch(r"/items/([a-z0-9-]+)", self.path)
        if not match:
            self.reply(404, "not found")
            return
        try:
            value = sql("SELECT status FROM demo_items WHERE id = '%s'" % match.group(1))
            if not value:
                self.reply(404, "missing")
            elif ROLE == "base" and value == "ARCHIVED":
                self.reply(500, "base cannot decode ARCHIVED")
            else:
                self.reply(200, value)
        except Exception as error:
            self.reply(500, str(error))

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        match = re.fullmatch(r"/items/([a-z0-9-]+)", parsed.path)
        status = urllib.parse.parse_qs(parsed.query).get("status", [""])[0]
        if not match or status not in ("ACTIVE", "ARCHIVED"):
            self.reply(400, "invalid item")
            return
        try:
            sql(
                "INSERT INTO demo_items(id, status) VALUES ('%s', '%s') "
                "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status"
                % (match.group(1), status)
            )
            self.reply(204, "")
        except Exception as error:
            self.reply(500, str(error))

    def reply(self, status, body):
        encoded = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, format, *args):
        return


sql("CREATE TABLE IF NOT EXISTS demo_items (id text PRIMARY KEY, status text NOT NULL)")
http.server.ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
