package com.jclaw.agent.config;

import com.jclaw.agent.tui.ModeAwareToolCatalog;
import com.jclaw.agent.chat.tools.WorkspaceReadTools;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ToolConfiguration {

    @Bean
    public WorkspaceReadTools workspaceReadTools(JclawPaths paths) {
        return new WorkspaceReadTools(paths.launchDirectory());
    }

    @Bean
    public GrepTool grepTool() {
        return GrepTool.builder().build();
    }

    @Bean
    public FileSystemTools fileSystemTools() {
        return FileSystemTools.builder().build();
    }

    @Bean
    public ShellTools shellTools() {
        return ShellTools.builder().build();
    }

    @Bean
    public ModeAwareToolCatalog modeAwareToolCatalog(
            WorkspaceReadTools workspaceReadTools,
            GrepTool grepTool,
            FileSystemTools fileSystemTools,
            ShellTools shellTools,
            com.jclaw.agent.chat.tools.claudecode.ClaudeCodeSubagentTools claudeCodeSubagentTools,
            ClaudeSubagentProperties claudeSubagentProperties) {

        Object[] planTools = {workspaceReadTools, grepTool};

        List<Object> buildList = new ArrayList<>(
                List.of(workspaceReadTools, grepTool, fileSystemTools, shellTools));
        if (claudeSubagentProperties.enabled()) {
            buildList.add(claudeCodeSubagentTools);
        }

        return new ModeAwareToolCatalog(planTools, buildList.toArray());
    }
}
