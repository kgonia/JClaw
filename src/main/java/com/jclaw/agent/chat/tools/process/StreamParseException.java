package com.jclaw.agent.chat.tools.process;

public class StreamParseException extends Exception {

    public StreamParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public StreamParseException(String message) {
        super(message);
    }
}
