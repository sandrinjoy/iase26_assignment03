# kt-coding-agent

Assignment 3 for IASE (Advanced Software Engineering, SS 2026). A Kotlin port of
Sebastian Raschka's [`mini-coding-agent`](https://github.com/rasbt/mini-coding-agent),
implementing the six components from
[Components of a Coding Agent](https://magazine.sebastianraschka.com/p/components-of-a-coding-agent).

## What you will build

Five sub-exercises (10 points). The starter ships with stubs and 56 failing
tests; the goal is to turn them all green (the live-Ollama integration test
stays skipped unless you run with `-Dollama.test=true`).

| # | Title                                                  | Points |
|---|--------------------------------------------------------|--------|
| a | Tools, validation, and dispatcher                      | 2      |
| b | Workspace context, model client, and prompt builder    | 2      |
| c | `StubModelClient` test double                          | 0.5    |
| d | The agent loop and response parser                     | 4      |
| e | Scripted tests and reflection                          | 1.5    |

Implement in order: each sub-exercise either has no dependency on later ones
or is needed by the next. `AgentTest` (sub-exercise (d)) cannot run until
`StubModelClient` (sub-exercise (c)) is implemented.

## Build prerequisites

- JDK 25 (Oracle JDK or Temurin). Verify under `File > Project Structure > SDK` in IntelliJ.
- IntelliJ IDEA recommended; import via *File > New > Project from Existing Sources*.

### Provisioning the JDK with `mise` (optional)

The repo ships a [`mise`](https://mise.jdx.dev/) config (`mise.toml`) that pins the JDK
to Temurin 25. If you already have JDK 25 on your machine, ignore this section. If you
don't, `mise` is the lowest-friction way to install it without touching system Java.

```bash
# 1. Install mise itself (one-time, picks the right method for your shell):
curl https://mise.run | sh

# 2. From the project root, install the pinned JDK (downloads Temurin 25 if missing):
mise install

# 3. (Optional) Activate the mise shell hook so the JDK is on PATH inside this repo:
#    macOS / Linux with zsh:
echo 'eval "$(mise activate zsh)"' >> ~/.zshrc && exec zsh
#    bash:
echo 'eval "$(mise activate bash)"' >> ~/.bashrc && exec bash

# 4. Verify:
java -version    # should report 25
./gradlew --version
```

Once activated, every shell entering this directory picks up Temurin 25 automatically.
IntelliJ users can also point *Project Structure → SDK* at the path mise installed
(typically `~/.local/share/mise/installs/java/temurin-25`).

### JDK 25 native-access warning (only without `mise`)

On JDK 25, Gradle's launcher prints four `WARNING: A restricted method in
java.lang.System has been called` lines at the top of every build. To silence
them, export:

```bash
export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"
```

The `mise` shell hook already sets this via `mise.toml`'s `[env]` section.

## Running tests

```bash
./gradlew test
```

When everything is implemented, all deterministic tests pass and one integration
test (`OllamaModelClientIntegrationTest.smokeTestAgainstLiveOllama`) is skipped.
To actually run that test against a local Ollama, start the server, pull the
model, and invoke gradle with the gate flag:

```bash
# In a separate terminal:
ollama serve

# In the project root:
ollama pull qwen3.5:4b
./gradlew test -Dollama.test=true
```

## Ollama setup (only for the bonus)

The 10 points do not require Ollama. The bonus point requires a working
session trace against a real local model.

```bash
# Install (macOS, Linux):
curl -fsSL https://ollama.com/install.sh | sh

# Install (Windows):  download from https://ollama.com/download

# Verify install:
ollama --version

# Start the server in a separate terminal:
ollama serve

# Pull the model:
ollama pull qwen3.5:4b
# Smaller fallback for laptops with limited RAM (pass `--model qwen3.5:2b`):
ollama pull qwen3.5:2b
# Larger option (~6.6 GB) if you have the RAM (pass `--model qwen3.5:9b`):
ollama pull qwen3.5:9b

# Verify the model is available:
ollama list
```

Full Ollama documentation: <https://github.com/ollama/ollama/blob/main/docs/>.

## Live run

```bash
./gradlew run --console=plain --args="--cwd demo/workspace"
```

At the `>` prompt, paste the canonical prompt:

> List the files in this workspace, read README.md, and summarize this project in two sentences.

The agent does a startup check. If Ollama is not running or the
model is not pulled, you get a clear error before the REPL appears.

The first call triggers a cold start (10–30 seconds). Subsequent calls are faster.

To test against the larger model on the assignment repo itself (no `--cwd`), run
from the repo root:

```bash
./gradlew run --console=plain --args="--model qwen3.5:4b"
```

### Self-contained workspaces: `--no-repo-walk`

By default the agent walks up to the enclosing git repo root for `AGENTS.md`,
the repo-level `README.md`, etc. The demo fixture lives at `demo/workspace/`
*inside* this repo, so the default behaviour pulls in both the inner toy
`README.md` and the outer assignment `README.md`. Pass `--no-repo-walk` to
scope doc collection to `--cwd` only:

```bash
./gradlew run --console=plain --args="--cwd demo/workspace --no-repo-walk"
```

Recommended for recording the bonus session.

## Bonus (1 point)

1. Run the live demo above against the `demo/workspace/` fixture.
2. Locate the resulting session file:
   `demo/workspace/.kt-coding-agent/sessions/<id>.json`.
3. Copy it to `demo/session.json` in this repo and commit it.

The grader checks: canonical prompt as user message; at least one tool call to
`list_files` or `read_file` against a fixture file; non-empty `<final>` answer;
`modelName` is a real Ollama tag.

## Submission

**The submitted zip must contain the `.git/` directory (the full commit
history). Submissions without `.git/` are graded 0.** The provided
`prepare-submission.sh` produces a zip that satisfies this requirement.
GitHub's "Download ZIP" button strips `.git/` and is **not** an acceptable
submission method.

**The unzipped repo must build and run as-is.** On a machine with JDK 25 on
`PATH`, running `./gradlew test` in the unzipped directory must build and
execute the deterministic test suite without any further setup. Submissions
that do not meet this bar are graded 0.

1. Push your work to a **private** remote (private GitHub, private GitLab, or a
   bare git repository). Do not publish your solution publicly.
2. Build the submission zip:

   ```bash
   # Solo
   ./prepare-submission.sh Mueller Anna
   # Group of two
   ./prepare-submission.sh Mueller Anna Schmidt Berta
   ```

   On native Windows, run the script from **Git Bash** (ships with Git for
   Windows) or **WSL**. Plain `cmd.exe` / PowerShell have neither `bash`
   nor `zip` and will not work.

3. Upload the resulting `iase26_assignment03_<Lastname>_<Firstname>.zip` (solo)
   or `iase26_assignment03_<Lastname1>_<Firstname1>__<Lastname2>_<Firstname2>.zip`
   (group) to Moodle. Hyphens are kept inside compound names (e.g., `Mueller-Schmidt`);
   `_` separates last from first, `__` separates the two group members.

The zip includes `.git/` and the Gradle wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/`) and excludes build artifacts and session runtime state.

## Reference

You may study `rasbt/mini-coding-agent` while doing the assignment. The
deliverable is the Kotlin port; you are not designing the agent from scratch.

## Acknowledgments

The starter and its test suite were developed with the help of
[Claude Code](https://www.anthropic.com/claude-code) (Opus 4.7).
