# Installation

Chronos ships as a self-contained native binary — no JRE or runtime required.

## macOS

=== "Homebrew (recommended)"

    ```sh
    brew tap Genairus/tap
    brew install chronos
    ```

    To upgrade later:

    ```sh
    brew upgrade chronos
    ```

=== "Direct download"

    Download the archive from the [latest release](https://github.com/Genairus/chronos/releases/latest):

    | Mac | File |
    |-----|------|
    | Apple Silicon (M1/M2/M3/M4) | `chronos-macos-aarch64.tar.gz` |
    | Intel | Use the ARM binary — it runs transparently via Rosetta 2 |

    Then extract and install:

    ```sh
    tar -xzf chronos-macos-*.tar.gz
    chmod +x chronos
    sudo mv chronos /usr/local/bin/
    ```

    If you see a Gatekeeper warning the first time, run:

    ```sh
    xattr -d com.apple.quarantine /usr/local/bin/chronos
    ```

---

## Linux

=== "Homebrew (recommended)"

    Homebrew works on Linux the same way it does on macOS — same tap, same command:

    ```sh
    # Install Homebrew if you don't have it
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

    brew tap Genairus/tap
    brew install chronos
    ```

=== "Direct download"

    Download the appropriate archive from the [latest release](https://github.com/Genairus/chronos/releases/latest):

    | Architecture | File |
    |-------------|------|
    | x86_64 (most servers/desktops) | `chronos-linux-x86_64.tar.gz` |
    | ARM64 (AWS Graviton, Raspberry Pi) | `chronos-linux-aarch64.tar.gz` |

    Then extract and install:

    ```sh
    tar -xzf chronos-linux-*.tar.gz
    chmod +x chronos
    sudo mv chronos /usr/local/bin/
    ```

---

## Windows

=== "Scoop (recommended)"

    [Scoop](https://scoop.sh) is the easiest way to install developer tools on Windows — no admin rights required.

    ```powershell
    # Install Scoop if you don't have it
    Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
    Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression

    # Add the Chronos bucket and install
    scoop bucket add chronos https://github.com/Genairus/scoop-chronos
    scoop install chronos
    ```

    To upgrade later:

    ```powershell
    scoop update chronos
    ```

=== "Direct download"

    Download `chronos-windows-x86_64.zip` from the [latest release](https://github.com/Genairus/chronos/releases/latest), extract it, and add the folder to your `PATH`.

---

## Verify

After installation, confirm the binary works:

```sh
chronos --version
# chronos 0.1.0

chronos --help
```

---

## Building from source

Requires GraalVM JDK 21 with `native-image` installed.

```sh
git clone https://github.com/Genairus/chronos.git
cd chronos
./gradlew :chronos-cli:nativeCompile
# Binary: chronos-cli/build/native/nativeCompile/chronos
```

To run without building a native binary (requires any JDK 21):

```sh
./gradlew :chronos-cli:run --args="--help"
```
