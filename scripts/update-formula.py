#!/usr/bin/env python3
"""
Update the Homebrew formula (Formula/chronos.rb) with a new version and SHA256 hashes.

Usage:
    update-formula.py <formula_path> <version> <sha_macos_arm> <sha_macos_x86> <sha_linux_x86> <sha_linux_arm>

Called by the GitHub Actions release workflow after building native binaries.
"""

import sys
import re

PLACEHOLDERS = {
    "PLACEHOLDER_MACOS_ARM": None,
    "PLACEHOLDER_MACOS_X86": None,
    "PLACEHOLDER_LINUX_X86": None,
    "PLACEHOLDER_LINUX_ARM": None,
}

TEMPLATE = '''\
class Chronos < Formula
  desc "Requirements modeling language and compiler for AI-assisted development"
  homepage "https://genairus.github.io/chronos/"
  license "Apache-2.0"
  version "{version}"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/Genairus/chronos/releases/download/v{version}/chronos-macos-aarch64.tar.gz"
      sha256 "{sha_macos_arm}"
    else
      url "https://github.com/Genairus/chronos/releases/download/v{version}/chronos-macos-x86_64.tar.gz"
      sha256 "{sha_macos_x86}"
    end
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
    assert_match version.to_s, shell_output("\#{bin}/chronos --version")
  end
end
'''


def main():
    if len(sys.argv) != 7:
        print(
            "Usage: update-formula.py <formula_path> <version> "
            "<sha_macos_arm> <sha_macos_x86> <sha_linux_x86> <sha_linux_arm>",
            file=sys.stderr,
        )
        sys.exit(1)

    formula_path, version, sha_macos_arm, sha_macos_x86, sha_linux_x86, sha_linux_arm = (
        sys.argv[1:]
    )

    # Validate inputs look like hex SHA256
    for label, val in [
        ("sha_macos_arm", sha_macos_arm),
        ("sha_macos_x86", sha_macos_x86),
        ("sha_linux_x86", sha_linux_x86),
        ("sha_linux_arm", sha_linux_arm),
    ]:
        if not re.fullmatch(r"[0-9a-fA-F]{64}", val):
            print(f"ERROR: {label}={val!r} does not look like a SHA256 hex string", file=sys.stderr)
            sys.exit(1)

    content = TEMPLATE.format(
        version=version,
        sha_macos_arm=sha_macos_arm,
        sha_macos_x86=sha_macos_x86,
        sha_linux_x86=sha_linux_x86,
        sha_linux_arm=sha_linux_arm,
    )

    with open(formula_path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Updated {formula_path} to v{version}")


if __name__ == "__main__":
    main()
