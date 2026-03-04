package com.jclaw.agent.chat.tools.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackgroundProcess<T> {

    private enum StreamType {
        STDOUT,
        STDERR
    }

    private final String processId;
    private final Process process;
    private final ProcessOutputParser<T> parser;
    private final ProcessOutputListener<T> listener;
    private final boolean destroyOnParserError;
    private final ProcessTrace<T> trace;

    private final Thread stdoutReader;
    private final Thread stderrReader;
    private final CompletableFuture<Integer> completionFuture;
    private final AtomicBoolean destroyRequested = new AtomicBoolean();

    private BackgroundProcess(String processId, Process process, ProcessOutputParser<T> parser,
                              ProcessOutputListener<T> listener, boolean destroyOnParserError) {
        this.processId = processId;
        this.process = process;
        this.parser = parser;
        this.listener = listener;
        this.destroyOnParserError = destroyOnParserError;
        this.trace = new ProcessTrace<>(processId);

        this.trace.markStarted(process.pid());
        if (this.listener != null) {
            this.listener.onStarted(processId, process);
        }

        this.stdoutReader = startReader(process.getInputStream(), StreamType.STDOUT);
        this.stderrReader = startReader(process.getErrorStream(), StreamType.STDERR);
        this.completionFuture = startCompletionFuture();
    }

    public static <T> BackgroundProcess<T> start(String processId, Process process, ProcessOutputParser<T> parser,
                                                 ProcessOutputListener<T> listener) {
        return new BackgroundProcess<>(processId, process, parser, listener, false);
    }

    public static <T> BackgroundProcess<T> start(String processId, Process process, ProcessOutputParser<T> parser,
                                                 ProcessOutputListener<T> listener, boolean destroyOnParserError) {
        return new BackgroundProcess<>(processId, process, parser, listener, destroyOnParserError);
    }

    private Thread startReader(InputStream stream, StreamType streamType) {
        Thread thread = Thread.ofVirtual()
                .name(processId + "-" + streamType.name().toLowerCase())
                .unstarted(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            onLine(streamType, line);
                        }
                    }
                    catch (IOException e) {
                        if (process.isAlive()) {
                            trace.addReaderError(e);
                            if (listener != null) {
                                listener.onReaderError(e);
                            }
                        }
                    }
                });
        thread.start();
        return thread;
    }

    private CompletableFuture<Integer> startCompletionFuture() {
        return process.onExit().thenApply(exitedProcess -> {
            awaitReaderThreadsAfterExit();
            int exitCode = exitedProcess.exitValue();
            trace.markCompleted(exitCode);
            if (listener != null) {
                listener.onCompleted(exitCode);
            }
            return exitCode;
        });
    }

    private void awaitReaderThreadsAfterExit() {
        try {
            stdoutReader.join(1_000L);
            stderrReader.join(1_000L);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void onLine(StreamType streamType, String line) {
        if (streamType == StreamType.STDOUT) {
            trace.appendStdoutLine(line);
            if (listener != null) {
                listener.onStdoutLine(line);
            }
            if (parser != null) {
                parseStdoutLine(line);
            }
            return;
        }

        trace.appendStderrLine(line);
        if (listener != null) {
            listener.onStderrLine(line);
        }
    }

    private void parseStdoutLine(String line) {
        try {
            List<T> parsedEvents = parser.parseLine(line);
            if (parsedEvents == null || parsedEvents.isEmpty()) {
                return;
            }
            for (T parsedEvent : parsedEvents) {
                trace.addEvent(parsedEvent);
                if (listener != null) {
                    listener.onParsedEvent(parsedEvent);
                }
            }
        }
        catch (Exception e) {
            trace.addParserError(line, e);
            if (listener != null) {
                listener.onParserError(line, e);
            }
            if (destroyOnParserError) {
                destroy();
            }
        }
    }

    public boolean awaitExit(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            completionFuture.get(timeout, unit);
            return true;
        }
        catch (TimeoutException e) {
            return false;
        }
        catch (ExecutionException e) {
            throw new IllegalStateException("Background process completion failed", e.getCause());
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public int exitCode() {
        return process.exitValue();
    }

    public void destroy() {
        if (!destroyRequested.compareAndSet(false, true)) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    public String processId() {
        return processId;
    }

    public ProcessTrace<T> trace() {
        return trace;
    }

    public void markKillRequested() {
        trace.markKillRequested();
    }
}
