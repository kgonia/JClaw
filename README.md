# Jclaw

**Status:** `0.0.1-SNAPSHOT` (early development build)

Jclaw is a terminal-first coding assistant built with Spring AI + TamboUI.
It runs as a full-screen split-pane TUI: chat on the left, workflow projection on the right.

## Configure

Required:

- `OPENAI_API_KEY` environment variable

You can variable using command line:

```bash
export OPENAI_API_KEY=your_api_key_here
```

on windows powershell:

```powershell
$env:OPENAI_API_KEY="your_api_key_here"
```

Optional (`application.yml` defaults already exist):

- `jclaw.subagent.enabled` (default: `false`) — set to `true` to activate the Claude Code subagent tool in BUILD mode
- `jclaw.subagent.claude-command` (default: `claude`)
- `jclaw.subagent.permission-mode` (default: `bypassPermissions`)
- `jclaw.ai.max-messages` (default: `100`)
- `jclaw.session.*` paths for local H2 databases

To enable the subagent, add to `application-local.properties`:

```properties
jclaw.subagent.enabled=true
```

Or pass as a JVM flag: `-Djclaw.subagent.enabled=true`

Notes:

- Session/workflow data is persisted under `.jclaw/` in the launch directory.
- Global config database is under `~/.jclaw/`.
- `src/main/resources/application-local.properties` is intentionally **not tracked** by Git.

## Run

Project can't be run from an IDE neither using ./gradle bootJar due to TamboUI's terminal handling. 
To run the project, use the command line:

```bash
./gradlew build
java -jar build/libs/jclaw-0.0.1-SNAPSHOT.jar
```

with claude subagent enabled:

```bash
./gradlew build
java -Djclaw.subagent.enabled=true -jar build/libs/jclaw-0.0.1-SNAPSHOT.jar
```


## What It Can Do

- Stream assistant output live in the TUI.
- Maintain two execution modes:
  - `PLAN` (default): read-only toolset
  - `BUILD`: expanded toolset after confirmation
- Handle slash commands:
  - `/plan`, `/build`, `/clear`, `/help`
- Project tool/subagent progress into a workflow pane (steps, status, risk, estimated cost).
- Run Claude Code as a subagent (opt-in via `jclaw.subagent.enabled=true`, requires `claude` on PATH).
