#!/usr/bin/env python3
"""Import your own COSMO credentials without printing or committing tokens."""
import argparse
import base64
import getpass
import json
import os
from pathlib import Path
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-file", type=Path, help="Your cosmo-tui auth.json or COSMO credential JSON")
    parser.add_argument("--output", type=Path, default=Path("data/cosmo/credentials.json"))
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists. Back it up and stop the app before replacing credentials.")
    if args.from_file:
        source = json.loads(args.from_file.read_text())
        tokens = source.get("credentials", source)
    else:
        tokens = {"accessToken": getpass.getpass("COSMO access token (hidden): ").strip(),
                  "refreshToken": getpass.getpass("COSMO refresh token (hidden): ").strip()}
    for field in ("accessToken", "refreshToken"):
        try:
            token = tokens[field]
            parts = token.split(".")
            assert len(parts) == 3
            claims = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
            assert claims["exp"] > time.time()
        except (KeyError, ValueError, TypeError, AssertionError):
            parser.error(field + " is missing, malformed or expired; log in again.")
    args.output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(prefix=".credentials-", dir=args.output.parent)
    try:
        with os.fdopen(fd, "w") as file:
            json.dump({field: tokens[field] for field in ("accessToken", "refreshToken")}, file)
        os.replace(temporary, args.output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print("COSMO credentials saved with owner-only permissions. Start the app. Use /cosmo/gravity/connect for future email login.")


if __name__ == "__main__":
    main()
