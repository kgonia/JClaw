# Claude Code Subagent PoC

This PoC shows how to run Claude Code as a subagent process, parse `stream-json`
output, keep detailed traces for logs/debugging, and return only concise
summaries to the main agent.

## What it covers

- Reusable `BackgroundProcess<T>` wrapper with generic `ProcessTrace<T>` capture
- Simpler `BackgroundProcess<T>` API without embedded polling cursors or `OutputSnapshot`
- Generic listener contract `ProcessOutputListener<T>` for pluggable streaming
- Claude-specific strict `stream-json` parsing via `ClaudeStreamJsonParser`
- Claude-specific Spring AI tool facade: `ClaudeCodeSubagentTools`
- Summary-first tool responses to avoid polluting main context
- Explicit debug path for detailed event/stderr inspection
- Completed trace retention with TTL-based eviction

## Main classes

- `com.jclaw.agent.chat.tools.process.BackgroundProcess`
- `com.jclaw.agent.chat.tools.process.ProcessTrace`
- `com.jclaw.agent.chat.tools.process.ProcessOutputListener`
- `com.jclaw.agent.chat.tools.process.ProcessOutputParser`
- `com.jclaw.agent.chat.tools.claudecode.ClaudeStreamJsonParser`
- `com.jclaw.agent.chat.tools.claudecode.ClaudeCodeSubagentTools`

## Try it

Run the JUnit test:

```
./gradlew test --tests com.jclaw.agent.chat.tools.claudecode.ClaudeCodeSubagentToolPoCTest
```

The tool methods exposed to Spring AI are:

- `ClaudeCodeSubagent`
- `ClaudeCodeSubagentOutput`
- `ClaudeCodeSubagentDebugOutput`
- `KillClaudeCodeSubagent`

Listener setup on `ClaudeCodeSubagentTools` (single listener for now):

- `ClaudeCodeSubagentTools.builder().listener(ProcessOutputListener<ClaudeStreamEvent>)`

Relevant builder options:

- `permissionMode(...)` to override the default Claude permission mode (`bypassPermissions`)
- `completedTraceTtlMs(...)` to control how long completed runs stay pollable/debuggable

Notes:

- `BackgroundProcess<T>` always pushes raw stdout/stderr lines to the listener
- Parsed events are accumulated in `ProcessTrace<T>` for projection, polling, and debug output
