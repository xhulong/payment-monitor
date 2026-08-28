#!/usr/bin/env python3
"""Small local receiver used by the repeatable Phase F Docker E2E test."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import hmac
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class ReceiverHandler(BaseHTTPRequestHandler):
    server_version = "PaymentMonitorPhaseFReceiver/1.0"

    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/health":
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"ok":true}')

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/webhook":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        if length < 0 or length > 1_048_576:
            self.send_error(413)
            return
        body = self.rfile.read(length)
        delivery_id = self.headers.get("X-Delivery-Id", "")
        event_id = self.headers.get("X-Webhook-Event-Id", "")
        schema_version = self.headers.get("X-Webhook-Schema-Version", "")
        timestamp = self.headers.get("X-Webhook-Timestamp", "")
        signature = self.headers.get("X-Webhook-Signature", "")
        expected = "v1=" + hmac.new(
            self.server.webhook_secret.encode("utf-8"),
            timestamp.encode("ascii") + b"." + body,
            hashlib.sha256,
        ).hexdigest()
        signature_valid = hmac.compare_digest(expected, signature)
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            payload = None
        record = {
            "receivedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "deliveryId": delivery_id,
            "eventId": event_id,
            "schemaVersion": schema_version,
            "timestamp": timestamp,
            "signatureValid": signature_valid,
            "bodySha256": hashlib.sha256(body).hexdigest(),
            "payload": payload,
        }
        with self.server.output_lock:
            with self.server.output_path.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
        self.send_response(204 if signature_valid else 401)
        self.end_headers()

    def log_message(self, _format: str, *_args: object) -> None:
        return


class ReceiverServer(ThreadingHTTPServer):
    webhook_secret: str
    output_path: Path
    output_lock: threading.Lock


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=19090)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    secret = os.environ.get("WEBHOOK_SECRET")
    if not secret:
        raise SystemExit("WEBHOOK_SECRET is required")
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    server = ReceiverServer((args.host, args.port), ReceiverHandler)
    server.webhook_secret = secret
    server.output_path = output
    server.output_lock = threading.Lock()
    server.serve_forever(poll_interval=0.2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
