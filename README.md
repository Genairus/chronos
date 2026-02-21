# chronos

A requirements language compiler — define journeys, generate artifacts.

## Commands (v0.1)

| Command | Description |
|---------|-------------|
| `chronos prd <input> [--out <dir>] [--name <doc>]` | Generate a Markdown PRD from one file or a directory |
| `chronos build [<config>]` | Compile all sources defined in `chronos.build` |
| `chronos validate <file>` | Parse and validate a `.chronos` file, print diagnostics |
| `chronos generate <file> --target <target> [--out <dir>]` | Generate an artifact from a single file |

Run `chronos --help` for full usage, or `chronos <command> --help` for per-command options.

## Setup

# One time, globally
curl -s "https://get.sdkman.io" | bash
source ~/.zshrc

# Install GraalVM (native-image included)
sdk install java 21.0.2-graalce

# In your project
cd chronos
sdk env init        # creates .sdkmanrc
echo 'SDKMAN_AUTO_ENV=true' >> ~/.zshrc
source ~/.zshrc

# Confirm everything works
java -version
native-image --version

