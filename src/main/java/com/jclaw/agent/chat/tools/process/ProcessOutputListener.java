package com.jclaw.agent.chat.tools.process;

public interface ProcessOutputListener<T> {

    default void onStarted(String processId, Process process) {
    }

    default void onStdoutLine(String line) {
    }

    default void onStderrLine(String line) {
    }

    default void onParsedEvent(T event) {
    }

    default void onParserError(String line, Exception exception) {
    }

    default void onCompleted(int exitCode) {
    }

    default void onReaderError(Exception exception) {
    }
}
