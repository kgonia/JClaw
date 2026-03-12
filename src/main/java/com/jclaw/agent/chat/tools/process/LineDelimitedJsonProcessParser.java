package com.jclaw.agent.chat.tools.process;

import java.util.List;

public class LineDelimitedJsonProcessParser<T> implements ProcessOutputParser<T> {

    private final StreamJsonParser<T> delegate;

    public LineDelimitedJsonProcessParser(StreamJsonParser<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<T> parseLine(String line) throws StreamParseException {
        return delegate.parseValue(line);
    }
}
