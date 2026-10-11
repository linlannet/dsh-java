package net.linlan.dsh.infrastructure.digitalhuman;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.service.LlmGenerationService;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.runtime.setting.service.AgentModelSettingResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlannerService {
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);
    private final ILlmRuntimePort llmRuntimePort;
    private final AgentModelSettingResolver modelSettingResolver;
    private final ObjectMapper objectMapper;

    public PlannerService(ILlmRuntimePort llmRuntimePort, AgentModelSettingResolver modelSettingResolver) {
        this.llmRuntimePort = llmRuntimePort;
        this.modelSettingResolver = modelSettingResolver;
        this.objectMapper = new ObjectMapper();
    }

    public List<PlanStep> plan(String objective, List<CapabilityCard> participants) {
        if (participants == null || participants.isEmpty()) {
            return null;
        }
        try {
            AgentOptions agent = this.modelSettingResolver.resolve(null, Integer.valueOf(2048));
            String prompt = this.buildPrompt(objective, participants);
            Message userMessage = Message.createUser(List.of(new TextBlock(prompt)), (MessageSource)new MessageSource.UserSource());
            GenerateOptions options = new GenerateOptions(agent.provider(), agent.model(), null, List.of(userMessage), "\u4f60\u662f\u591a\u667a\u80fd\u4f53\u534f\u4f5c\u7684\u89c4\u5212\u5668\uff0c\u53ea\u8f93\u51fa\u4e25\u683c JSON\uff0c\u4e0d\u8f93\u51fa\u4efb\u4f55\u89e3\u91ca\u3002", null, Double.valueOf(0.2), Integer.valueOf(2048), null, "planner-" + System.currentTimeMillis(), "collaboration-planning", agent.baseUrl(), agent.apiKey(), agent.protocol());
            Message result = (Message)new LlmGenerationService(this.llmRuntimePort).generate(options).join();
            String text = result.content().stream().map(block -> {
                String string;
                if (block instanceof TextBlock) {
                    TextBlock tb = (TextBlock)block;
                    string = tb.text();
                } else {
                    string = "";
                }
                return string;
            }).reduce("", String::concat);
            return this.parse(text, participants);
        }
        catch (Exception e) {
            log.warn("LLM planner failed, falling back to rule-based plan: {}", (Object)e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String objective, List<CapabilityCard> participants) {
        StringBuilder cards = new StringBuilder();
        for (CapabilityCard card : participants) {
            cards.append("- id=").append(card.digitalHumanId()).append(" \u540d\u79f0=").append(card.displayName()).append(" \u804c\u8d23=").append(card.purpose()).append(" \u80fd\u529b\u6807\u7b7e=").append(String.join((CharSequence)"/", card.roleTags())).append("\n");
        }
        return "\u4f60\u662f\u4e00\u4e2a\u591a\u667a\u80fd\u4f53\u534f\u4f5c\u89c4\u5212\u5668\u3002\u7ed9\u5b9a\u4e00\u4e2a\u76ee\u6807\u548c\u4e00\u7ec4\u6570\u5b57\u5458\u5de5\uff0c\u628a\u76ee\u6807\u62c6\u6210\u4e00\u4e2a\u6709\u5411\u65e0\u73af\u7684\u6267\u884c\u8ba1\u5212\u3002\n\n\u3010\u76ee\u6807\u3011\n%s\n\n\u3010\u53ef\u7528\u6570\u5b57\u5458\u5de5\u3011\n%s\n\u3010\u8981\u6c42\u3011\n1. \u6bcf\u4e00\u6b65\u660e\u786e\u6d3e\u7ed9\u4e00\u540d\u5458\u5de5\uff08assignTo \u7528\u5176 id\uff09\uff0c\u4efb\u52a1\u8981\u843d\u5728\u8be5\u5458\u5de5\u7684\u804c\u8d23\u4e0e\u80fd\u529b\u6807\u7b7e\u5185\u3002\n2. \u6709\u5148\u540e\u4f9d\u8d56\u7684\u6b65\u9aa4\u7528 dependsOn \u5f15\u7528\u524d\u5e8f\u6b65\u9aa4\u7684\u5e8f\u53f7\uff08\u4ece 0 \u5f00\u59cb\uff09\uff1b\u65e0\u4f9d\u8d56\u7684\u6b65\u9aa4\u53ef\u4ee5\u5e76\u884c\u3002\n3. \u6bcf\u540d\u6570\u5b57\u5458\u5de5\u81f3\u5c11\u5206\u5230\u4e00\u6b65\uff1b\u5982\u679c\u804c\u8d23\u53ef\u4ee5\u5e76\u884c\uff0cdependsOn \u5fc5\u987b\u4e3a\u7a7a\u6570\u7ec4\uff0c\u907f\u514d\u65e0\u610f\u4e49\u4e32\u884c\u3002\n4. \u6b65\u9aa4\u5c3d\u91cf\u5c11\u800c\u7cbe\uff0c\u901a\u5e38 %d-%d \u6b65\uff0c\u4e0d\u8981\u4e3a\u51d1\u6570\u62c6\u5206\u3002\n5. title \u7528\u4e00\u53e5\u8bdd\u6982\u62ec\u8be5\u6b65\u4ea7\u51fa\uff0cinstruction \u662f\u7ed9\u8be5\u5458\u5de5\u7684\u5b8c\u6574\u4efb\u52a1\u8bf4\u660e\u3002\n6. \u53ea\u8f93\u51fa JSON \u6570\u7ec4\uff0c\u4e0d\u8981\u8f93\u51fa\u4efb\u4f55\u5176\u4ed6\u6587\u5b57\u3001\u89e3\u91ca\u6216 markdown \u4ee3\u7801\u5757\u6807\u8bb0\u3002\n\n\u3010\u8f93\u51fa\u683c\u5f0f\u3011\n[{\"assignTo\":\"\u5458\u5de5id\",\"title\":\"\u6b65\u9aa4\u6807\u9898\",\"instruction\":\"\u4efb\u52a1\u8bf4\u660e\",\"dependsOn\":[]}]\n".formatted(objective, cards, participants.size(), Math.max(2, participants.size() + 1));
    }

    private List<PlanStep> parse(String raw, List<CapabilityCard> participants) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = raw.trim().replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        int start = json.indexOf(91);
        int end = json.lastIndexOf(93);
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode array = this.objectMapper.readTree(json.substring(start, end + 1));
            if (!array.isArray() || array.isEmpty()) {
                return null;
            }
            List<String> validIds = participants.stream().map(CapabilityCard::digitalHumanId).toList();
            ArrayList<PlanStep> steps = new ArrayList<PlanStep>();
            HashSet<String> assignedIds = new HashSet<String>();
            for (JsonNode node : array) {
                String assignTo = node.path("assignTo").asText("");
                if (!validIds.contains(assignTo)) {
                    return null;
                }
                assignedIds.add(assignTo);
                String title = node.path("title").asText("").trim();
                String instruction = node.path("instruction").asText("").trim();
                if (title.isEmpty() || instruction.isEmpty()) {
                    return null;
                }
                ArrayList<Integer> deps = new ArrayList<Integer>();
                for (JsonNode dep : node.path("dependsOn")) {
                    int index = dep.asInt(-1);
                    if (index < 0 || index >= steps.size()) {
                        return null;
                    }
                    deps.add(index);
                }
                steps.add(new PlanStep(assignTo, title, instruction, deps));
            }
            if (participants.size() > 1 && assignedIds.size() < participants.size()) {
                return null;
            }
            return steps;
        }
        catch (Exception e) {
            log.warn("Planner output parse failed: {}", (Object)e.getMessage());
            return null;
        }
    }

    public record CapabilityCard(String digitalHumanId, String displayName, String purpose, List<String> roleTags) {
    }

    public record PlanStep(String assignTo, String title, String instruction, List<Integer> dependsOn) {
    }
}

