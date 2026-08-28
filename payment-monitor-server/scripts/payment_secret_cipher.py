#!/usr/bin/env python3
"""Encrypt a payment secret using the same AES-256-GCM layout as DeviceSecretCipher."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def main() -> int:
    request = json.load(sys.stdin)
    master_key = request["masterKey"].encode("utf-8")
    plain_text = request["plainText"].encode("utf-8")
    key = hashlib.sha256(master_key).digest()
    nonce = os.urandom(12)
    encrypted_with_tag = AESGCM(key).encrypt(nonce, plain_text, None)
    sys.stdout.write(base64.b64encode(nonce + encrypted_with_tag).decode("ascii"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
