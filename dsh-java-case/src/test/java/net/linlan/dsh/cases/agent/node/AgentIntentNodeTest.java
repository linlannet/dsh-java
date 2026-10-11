package net.linlan.dsh.cases.agent.node;

import net.linlan.dsh.cases.agent.factory.AgentMessageDynamicContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIntentNodeTest {

    private final AgentIntentNode node = new AgentIntentNode();

    @Test
    void classifiesDirectoryQuestionWithAbsolutePathAsTaskExecution() {
        var intent = node.classify("这个目录下有什么项目？/Users/mac/coding/github");

        assertEquals(AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION, intent);
    }

    @Test
    void classifiesCurrentDirectoryQuestionAsTaskExecution() {
        var intent = node.classify("这个目录下有哪些项目？");

        assertEquals(AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION, intent);
    }

    @Test
    void classifiesGreetingAsChat() {
        var intent = node.classify("你好");

        assertEquals(AgentMessageDynamicContext.MessageIntent.CHAT, intent);
    }

    @Test
    void preservesExplicitTaskVerbClassification() {
        var intent = node.classify("帮我创建一份周报");

        assertEquals(AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION, intent);
    }

    @Test
    void classifiesConceptualQuestionAsChatInsteadOfClarification() {
        var intent = node.classify("什么是幂等接口？");

        assertEquals(AgentMessageDynamicContext.MessageIntent.CODE_QUESTION, intent);
    }
}
