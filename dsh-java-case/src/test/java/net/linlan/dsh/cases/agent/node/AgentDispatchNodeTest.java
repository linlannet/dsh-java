package net.linlan.dsh.cases.agent.node;

import net.linlan.dsh.cases.agent.factory.AgentMessageDynamicContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDispatchNodeTest {

    @Test
    void buildsFsSearchInstructionForDirectoryQuestion(@TempDir Path tempDir) {
        String message = AgentDispatchNode.prepareMessageText(
                "这个目录下有什么项目？" + tempDir,
                AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION
        );

        assertTrue(message.contains("shell_execute"));
        assertTrue(message.contains("ls -la \"" + tempDir + "\""));
        assertTrue(message.contains("maxdepth 1"));
    }

    @Test
    void fallsBackToGeneralToolInstructionForOtherTaskExecution() {
        String message = AgentDispatchNode.prepareMessageText(
                "帮我检查这个仓库",
                AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION
        );

        assertTrue(message.contains("请先使用可用工具完成下面的任务"));
    }

    @Test
    void appendsPluginMentionInstruction() {
        String message = AgentDispatchNode.appendPluginMentionInstruction(
                "@sample-tools 查询插件状态"
        );

        assertTrue(message.contains("[插件提及]"));
        assertTrue(message.contains("sample-tools"));
        assertTrue(message.contains("plugin__<插件ID>__<工具名>"));
    }

    @Test
    void ignoresEmailLikeTextAsPluginMention() {
        String message = AgentDispatchNode.appendPluginMentionInstruction(
                "联系 someone@example.com"
        );

        assertTrue(!message.contains("[插件提及]"));
    }
}
