package com.jclaw.agent.chat.tools.process;

import java.util.ArrayList;
import java.util.List;

public final class ProcessTrace<T> {

    private final String processId;
    private final long startedAt = System.currentTimeMillis();
    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();
    private final List<T> events = new ArrayList<>();
    private final List<String> parserErrors = new ArrayList<>();
    private final List<String> readerErrors = new ArrayList<>();

    private long pid = -1;
    private long completedAt;
    private Integer exitCode;
    private boolean parserFailed;
    private boolean killRequested;

    ProcessTrace(String processId) {
        this.processId = processId;
    }

    synchronized void markStarted(long pid) {
        this.pid = pid;
    }

    synchronized void appendStdoutLine(String line) {
        appendLine(stdout, line);
    }

    synchronized void appendStderrLine(String line) {
        appendLine(stderr, line);
    }

    synchronized void addEvent(T event) {
        events.add(event);
    }

    synchronized void addParserError(String line, Exception exception) {
        parserFailed = true;
        parserErrors.add(formatError(line, exception));
    }

    synchronized void addReaderError(Exception exception) {
        readerErrors.add(exception == null ? "Unknown reader error" : exception.getMessage());
    }

    synchronized void markCompleted(int exitCode) {
        if (completedAt != 0L) {
            return;
        }
        this.exitCode = exitCode;
        this.completedAt = System.currentTimeMillis();
    }

    synchronized void markKillRequested() {
        this.killRequested = true;
    }

    public synchronized String processId() {
        return processId;
    }

    public synchronized long pid() {
        return pid;
    }

    public long startedAt() {
        return startedAt;
    }

    public synchronized long completedAt() {
        return completedAt;
    }

    public synchronized Integer exitCode() {
        return exitCode;
    }

    public synchronized boolean isRunning() {
        return completedAt == 0L;
    }

    public synchronized boolean parserFailed() {
        return parserFailed;
    }

    public synchronized boolean killRequested() {
        return killRequested;
    }

    public synchronized String stdout() {
        return stdout.toString();
    }

    public synchronized String stderr() {
        return stderr.toString();
    }

    public synchronized List<T> events() {
        return List.copyOf(events);
    }

    public synchronized List<String> parserErrors() {
        return List.copyOf(parserErrors);
    }

    public synchronized List<String> readerErrors() {
        return List.copyOf(readerErrors);
    }

    private static String formatError(String line, Exception exception) {
        StringBuilder message = new StringBuilder();
        if (exception != null && exception.getMessage() != null && !exception.getMessage().isBlank()) {
            message.append(exception.getMessage().trim());
        }
        else {
            message.append("Unknown parser error");
        }
        if (line != null && !line.isBlank()) {
            message.append(" | line=").append(line.trim());
        }
        return message.toString();
    }

    private static void appendLine(StringBuilder target, String line) {
        if (line == null) {
            return;
        }
        target.append(line).append('\n');
    }
}
