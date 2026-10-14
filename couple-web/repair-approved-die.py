#!/usr/bin/env python3
"""One-time integrity recovery of the approved WebP after binary API transport.

The source artwork's Git blob SHA-1 is verified before and after recovery.
Fail closed if the binary differs in any way other than exactly one deleted byte.
"""
from __future__ import annotations

import hashlib
from pathlib import Path

ASSET = Path(__file__).parent / "assets" / "loveroll-die-approved.webp"
EXPECTED_SHA = "66c794b25ba1b5ff11ea73dc0a99b951554f08de"
EXPECTED_LENGTH = 3504

def digest(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()

def restore() -> bool:
    raw = ASSET.read_bytes()
    if len(raw) == EXPECTED_LENGTH and digest(raw) == EXPECTED_SHA:
        print("Approved artwork already verified")
        return False
    if len(raw) != EXPECTED_LENGTH - 1:
        raise RuntimeError(f"Wrong approved image length: {len(raw)}, expected {EXPECTED_LENGTH}")
    header = b"blob 3504\0"
    for index in range(len(raw) + 1):
        before, after = raw[:index], raw[index:]
        for value in range(256):
            candidate = before + bytes([value]) + after
            if hashlib.sha1(header + candidate).hexdigest() == EXPECTED_SHA:
                ASSET.write_bytes(candidate)
                print(f"Restored exact original approved artwork at offset {index}; SHA {EXPECTED_SHA}")
                return True
    raise RuntimeError("Artwork differs by more than one byte. Refusing a guessed replacement.")

if __name__ == "__main__":
    restore()
