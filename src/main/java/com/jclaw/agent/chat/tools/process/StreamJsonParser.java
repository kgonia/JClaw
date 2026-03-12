package com.jclaw.agent.chat.tools.process;

import java.util.List;

public interface StreamJsonParser<T> {

    List<T> parseValue(String value) throws StreamParseException;
}
