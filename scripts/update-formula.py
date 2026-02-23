#!/usr/bin/env python3
"""
Update the Homebrew formula (Formula/chronos.rb) with a new version and SHA256 hashes.

Usage:
    update-formula.py <formula_path> <version> <sha_macos_arm> <sha_linux_x86> <sha_linux_arm>

Note: No separate Intel macOS binary is produced. Intel Mac users run the ARM binary
via Rosetta 2, which is transparent on macOS 11+.

Called by the GitHub Actions release workflow after building native binaries.
"""

import sys
import re

TEMPLATE = '''\
class Chronos < Formula
  desc "Requirements modeling language and compiler for AI-assisted development"
  homepage "https://genairus.github.io/chronos/"
  license "Apache-2.0"
  version "{version}"

  on_macos do
    # Single ARM binary for all macOS — Intel Macs run it via Rosetta 2 (macOS 11+).
    url "https://github.com/Genairus/chronos/releases/download/v{version}/chronos-macos-aarch64.tar.gz"
    sha256 "{sha_macos_arm}"
  end

  on_linux do
    if Hardware::CPU.arm?
      url "https://github.com/Genairus/chronos/releases/download/v{version}/chronos-linux-aarch64.tar.gz"
      sha256 "{sha_linux_arm}"
    else
      url "https://github.com/Genairus/chronos/releases/download/v{version}/chronos-linux-x86_64.tar.gz"
      sha256 "{sha_linux_x86}"
    end
  end

  def install
    bin.install "chronos"
  end

  test do
    assert_match version.to_s, shell_output("#{{bin}}/chronos --version")
  end
end
'''


def main():
    if len(sys.argv) != 6:
        print(
            "Usage: update-formula.py <formula_path> <version> "
            "<sha_macos_arm> <sha_linux_x86> <sha_linux_arm>",
            file=sys.stderr,
        )
        sys.exit(1)

    formula_path, version, sha_macos_arm, sha_linux_x86, sha_linux_arm = sys.argv[1:]

    for label, val in [
        ("sha_macos_arm", sha_macos_arm),
        ("sha_linux_x86", sha_linux_x86),
        ("sha_linux_arm", sha_linux_arm),
    ]:
        if not re.fullmatch(r"[0-9a-fA-F]{64}", val):
            print(f"ERROR: {label}={val!r} does not look like a SHA256 hex string", file=sys.stderr)
            sys.exit(1)

    content = TEMPLATE.format(
        version=version,
        sha_macos_arm=sha_macos_arm,
        sha_linux_x86=sha_linux_x86,
        sha_linux_arm=sha_linux_arm,
    )

    with open(formula_path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Updated {formula_path} to v{version}")


if __name__ == "__main__":
    main()
