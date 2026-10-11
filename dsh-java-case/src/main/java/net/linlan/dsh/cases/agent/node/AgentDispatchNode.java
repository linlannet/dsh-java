package net.linlan.dsh.cases.agent.node;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.cases.agent.factory.AgentMessageDynamicContext;
import net.linlan.dsh.domain.agent.model.valobj.InboxTarget;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 派发节点：构造用户消息并发送到 Agent Inbox。
 *
 * <p>调用流程：HTTP/API 门面 → AgentUseCase → AgentMessageFactory → 策略树 → ReactLoopAgent；会话事件写入 SessionEventLog。</p>
 * <p>示例：调用方传入 agentId、用户消息和可选模型配置，返回助手文本与工具调用结果。</p>
 *
 */
@Service("agentMessageDispatchNode")
public class AgentDispatchNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatchNode.class);

    // 提取消息中的绝对路径：遇到空白和常见中英文标点即停止，避免把后续说明文字吞进路径。
    private static final Pattern ABSOLUTE_PATH = Pattern.compile("(/[^\s，。,；;：:\"'“”‘’（）()！？!?、<>]+)");
    private static final Pattern DIRECTORY_QUESTION = Pattern.compile("(?i)(目录|项目|文件夹|仓库|里面有什么|有哪些|包含什么|列表|list|what's in|what is in)");
    private static final Pattern PLUGIN_MENTION = Pattern.compile("(?<=^|\\s)@([A-Za-z0-9][A-Za-z0-9._-]*)");

    @Autowired
    @Qualifier("agentMessageCollectNode")
    private AgentCollectNode collectNode;

    /**
     * 构造用户消息并投递到 Agent Inbox。
     * <p>执行流程：读取意图 -> prepareMessageText 注入任务指令 -> 组装文本和图片块 -> send 到 NEXT_TURN 并唤醒 Agent。
     * <p>数据示例：request.message="/tmp 目录里有什么" 会被改写为“先执行 ls -la /tmp，再列出一级项目”。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                           AgentMessageDynamicContext ctx) throws Exception {
        var intent = ctx.getIntent();
        String messageText = prepareMessageText(request.message(), intent);
        messageText = appendPluginMentionInstruction(messageText);
        List<ContentBlock> blocks = new java.util.ArrayList<>();
        blocks.add(new TextBlock(messageText));
        appendImageBlocks(request, blocks);
        Message userMessage = Message.createUser(
                blocks,
                new MessageSource.UserSource()
        );
        ctx.setUserMessage(userMessage);
        log.info("[Dispatch] Agent={} 构造用户消息并发送到 inbox（intent={} blocks={} 文本长度={}）",
                request.agentId(), intent, blocks.size(), messageText.length());
        ctx.getAgent().send(userMessage, InboxTarget.NEXT_TURN, true);
        log.info("[Dispatch] Agent={} 消息已投递 inbox 并唤醒 Agent，等待执行...", request.agentId());
        return null;
    }

    /**
     * 把请求携带的图片附件解析为 ImageBlock 追加到内容块。
     * 支持两种形式：data URL（data:image/png;base64,xxx）或裸 base64（需配 mimeType）。
     * 解析失败的附件跳过并记录（不中断消息发送）。
     */
    private static void appendImageBlocks(AgentMessageRequestDTO request, List<ContentBlock> blocks) {
        if (request.images() == null || request.images().isEmpty()) {
            return;
        }
        for (String raw : request.images()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                String mime = "image/png";
                String base64 = raw.trim();
                // data URL 形式: data:<mime>;base64,<payload>
                if (base64.startsWith("data:")) {
                    int comma = base64.indexOf(',');
                    if (comma < 0) {
                        continue;
                    }
                    String header = base64.substring(5, comma); // strip "data:"
                    int semi = header.indexOf(';');
                    mime = semi > 0 ? header.substring(0, semi) : header;
                    if (mime == null || mime.isBlank()) {
                        mime = "image/png";
                    }
                    base64 = base64.substring(comma + 1);
                }
                byte[] data = java.util.Base64.getDecoder().decode(base64);
                if (data.length == 0) {
                    continue;
                }
                blocks.add(new ImageBlock(new ImageAttachmentRef(data, mime, 0, 0)));
            } catch (IllegalArgumentException e) {
                // 非法 base64：跳过该附件，不让整条消息失败
                log.warn("[Dispatch] 图片附件解析失败，已跳过（原始长度={}）", raw.length());
            }
        }
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    /**
     * 获取Next。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param request 请求对象。
     * @param ctx ctx参数。
     * @return 获取到的Next。
     */

    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return collectNode;
    }

    /**
     * 根据意图增强用户消息。
     * <p>任务执行消息会加入“先使用工具”的约束；目录问题会进一步生成明确的 shell 命令提示；
     * 闲聊和代码问答保持原文，避免影响模型表达。
     *
     * @param messageText 用户原始文本
     * @param intent      Intent 节点识别出的意图
     * @return 实际发送给模型的消息文本
     */
    static String prepareMessageText(String messageText, AgentMessageDynamicContext.MessageIntent intent) {
        if (intent == AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION) {
            String directoryInstruction = buildDirectoryInstruction(messageText);
            if (directoryInstruction != null) {
                return directoryInstruction;
            }
            return "请先使用可用工具完成下面的任务，不要在未使用工具前直接给出结论。"
                    + "如果任务涉及本地目录、文件、代码或仓库状态，优先读取/搜索后再回答。\n\n"
                    + "用户原始请求：" + messageText;
        }
        return messageText;
    }

    /**
     * 把 @插件 提及转换为模型可见的优先级指令，便于对话中显式验证指定插件。
     */
    static String appendPluginMentionInstruction(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return messageText;
        }
        Matcher matcher = PLUGIN_MENTION.matcher(messageText);
        java.util.LinkedHashSet<String> mentions = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        if (mentions.isEmpty()) {
            return messageText;
        }
        return messageText
                + "\n\n[插件提及] 用户明确指定了以下插件："
                + String.join(", ", mentions)
                + "。优先在对应 plugin__<插件ID>__<工具名> 工具中选择；"
                + "其他插件工具只有在被指定或确实更匹配时才使用。";
    }

    /**
     * 为目录类任务生成精确的工具指令。
     * <p>只有文本同时命中目录关键词和绝对路径时才返回提示；否则返回 null，
     * 交由通用的任务执行指令处理。
     * <p>数据示例：输入“/tmp 目录里有什么”生成建议执行 `ls -la "/tmp"` 的提示。
     */
    private static String buildDirectoryInstruction(String messageText) {
        if (messageText == null || !DIRECTORY_QUESTION.matcher(messageText).find()) {
            return null;
        }
        Matcher matcher = ABSOLUTE_PATH.matcher(messageText);
        if (!matcher.find()) {
            return null;
        }
        String directory = matcher.group(1);
        // 路径必须真实存在且是目录才改写为 ls 指令——否则像 "SELECT/SHOW/DESC"
        // 这类文本片段会被误当成目录路径，把用户消息改写得面目全非。
        try {
            if (!java.nio.file.Files.isDirectory(java.nio.file.Path.of(directory))) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        return "你需要列出这个目录的一级子目录和文件。推荐用 `shell_execute` 工具执行 `ls -la \"" + directory + "\"` 获取完整列表，\n"
                + "或者用 `find \"" + directory + "\" -maxdepth 1 -type d` 只列目录。\n"
                + "拿到真实结果后，用 Markdown 列表展示所有一级项目，不要遗漏、不要模糊化。\n\n"
                + "用户原始请求：" + messageText;
    }
}
