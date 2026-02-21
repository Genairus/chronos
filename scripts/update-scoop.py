#!/usr/bin/env python3
"""
Update the Scoop manifest (bucket/chronos.json) with a new version and SHA256 hash.

Usage:
    update-scoop.py <manifest_path> <version> <sha_windows_x86>

Called by the GitHub Actions release workflow after building native binaries.
"""

import json
import re
import sys


def main():
    if len(sys.argv) != 4:
        print(
            "Usage: update-scoop.py <manifest_path> <version> <sha_windows_x86>",
            file=sys.stderr,
        )
        sys.exit(1)

    manifest_path, version, sha_windows = sys.argv[1:]

    if not re.fullmatch(r"[0-9a-fA-F]{64}", sha_windows):
        print(
            f"ERROR: sha_windows={sha_windows!r} does not look like a SHA256 hex string",
            file=sys.stderr,
        )
        sys.exit(1)

    with open(manifest_path, encoding="utf-8") as f:
        manifest = json.load(f)

    manifest["version"] = version
    manifest["architecture"]["64bit"]["url"] = (
        f"https://github.com/Genairus/chronos/releases/download/v{version}"
        f"/chronos-windows-x86_64.zip"
    )
    manifest["architecture"]["64bit"]["hash"] = f"sha256:{sha_windows}"

    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")

    print(f"Updated {manifest_path} to v{version}")


if __name__ == "__main__":
    main()
