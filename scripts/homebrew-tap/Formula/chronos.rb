class Chronos < Formula
  desc "Requirements modeling language and compiler for AI-assisted development"
  homepage "https://genairus.github.io/chronos/"
  license "Apache-2.0"
  version "0.1.0"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/Genairus/chronos/releases/download/v#{version}/chronos-macos-aarch64.tar.gz"
      sha256 "PLACEHOLDER_MACOS_ARM"
    else
      url "https://github.com/Genairus/chronos/releases/download/v#{version}/chronos-macos-x86_64.tar.gz"
      sha256 "PLACEHOLDER_MACOS_X86"
    end
  end

  on_linux do
    if Hardware::CPU.arm?
      url "https://github.com/Genairus/chronos/releases/download/v#{version}/chronos-linux-aarch64.tar.gz"
      sha256 "PLACEHOLDER_LINUX_ARM"
    else
      url "https://github.com/Genairus/chronos/releases/download/v#{version}/chronos-linux-x86_64.tar.gz"
      sha256 "PLACEHOLDER_LINUX_X86"
    end
  end

  def install
    bin.install "chronos"
  end

  test do
    assert_match version.to_s, shell_output("#{bin}/chronos --version")
  end
end
