package com.jclaw.agent.chat.tools.process;

import java.util.List;

public class CompositeProcessOutputListener<T> implements ProcessOutputListener<T> {

    private final List<ProcessOutputListener<T>> delegates;

    public CompositeProcessOutputListener(List<ProcessOutputListener<T>> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void onStarted(String processId, Process process) {
        delegates.forEach(delegate -> delegate.onStarted(processId, process));
    }

    @Override
    public void onStdoutLine(String line) {
        delegates.forEach(delegate -> delegate.onStdoutLine(line));
    }

    @Override
    public void onStderrLine(String line) {
        delegates.forEach(delegate -> delegate.onStderrLine(line));
    }

    @Override
    public void onParsedEvent(T event) {
        delegates.forEach(delegate -> delegate.onParsedEvent(event));
    }

    @Override
    public void onParserError(String line, Exception exception) {
        delegates.forEach(delegate -> delegate.onParserError(line, exception));
    }

    @Override
    public void onCompleted(int exitCode) {
        delegates.forEach(delegate -> delegate.onCompleted(exitCode));
    }

    @Override
    public void onReaderError(Exception exception) {
        delegates.forEach(delegate -> delegate.onReaderError(exception));
    }
}
