package com.jclaw.agent.chat.tools.process;

import java.util.List;

@FunctionalInterface
public interface ProcessOutputParser<T> {

    List<T> parseLine(String line) throws Exception;
}
