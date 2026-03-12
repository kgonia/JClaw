package com.jclaw.agent.config;

import com.jclaw.agent.tui.ModeAwareToolCatalog;
import com.jclaw.agent.chat.tools.WorkspaceReadTools;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public ModeAwareToolCatalog modeAwareToolCatalog(WorkspaceReadTools workspaceReadTools, GrepTool grepTool,
                                                     FileSystemTools fileSystemTools, ShellTools shellTools,
                                                     com.jclaw.agent.chat.tools.claudecode.ClaudeCodeSubagentTools claudeCodeSubagentTools) {
        Object[] planTools = {workspaceReadTools, grepTool};
        Object[] buildTools = {workspaceReadTools, grepTool, fileSystemTools, shellTools, claudeCodeSubagentTools};
        return new ModeAwareToolCatalog(planTools, buildTools);
    }
}
