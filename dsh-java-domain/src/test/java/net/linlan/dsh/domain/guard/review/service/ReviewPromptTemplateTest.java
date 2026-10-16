package net.linlan.dsh.domain.guard.review.service;

import net.linlan.dsh.domain.guard.review.model.valobj.AutoReviewDecision;
import net.linlan.dsh.domain.guard.review.model.valobj.ReviewPromptTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReviewPromptTemplate 审查 prompt 模板")
class ReviewPromptTemplateTest {

    @Test
    @DisplayName("render 替换变量占位符")
    void renderReplacesVariables() {
        var template = new ReviewPromptTemplate("test", "Action: ${action}, Context: ${context}", "test");
        String rendered = template.render(Map.of("action", "shell_exec", "context", "user input"));
        assertEquals("Action: shell_exec, Context: user input", rendered);
    }

    @Test
    @DisplayName("render null 变量映射返回原模板")
    void renderNullVariablesReturnsOriginal() {
        var template = new ReviewPromptTemplate("test", "Action: ${action}", "test");
        String rendered = template.render(null);
        assertEquals("Action: ${action}", rendered);
    }

    @Test
    @DisplayName("render 未提供变量保留占位符")
    void renderMissingVariableKeepsPlaceholder() {
        var template = new ReviewPromptTemplate("test", "Action: ${action}, Ctx: ${ctx}", "test");
        String rendered = template.render(Map.of("action", "shell"));
        assertEquals("Action: shell, Ctx: ${ctx}", rendered);
    }

    @Test
    @DisplayName("render null 值替换为空字符串")
    void renderNullValueReplacedWithEmpty() {
        var template = new ReviewPromptTemplate("test", "Action: ${action}", "test");
        java.util.HashMap<String, String> vars = new java.util.HashMap<>();
        vars.put("action", null);
        String rendered = template.render(vars);
        assertEquals("Action: ", rendered);
    }

    @Test
    @DisplayName("defaultReview 模板包含 action 和 context 占位符")
    void defaultReviewTemplateHasPlaceholders() {
        var template = ReviewPromptTemplate.defaultReview();
        assertTrue(template.template().contains("${action}"));
        assertTrue(template.template().contains("${context}"));
    }

    @Test
    @DisplayName("securityReview 模板包含安全检查关键词")
    void securityReviewTemplateHasSecurityKeywords() {
        var template = ReviewPromptTemplate.securityReview();
        assertTrue(template.template().contains("security"));
        assertTrue(template.template().contains("injection"));
    }
}

@DisplayName("ReviewPromptTemplateRegistry 模板注册表")
class ReviewPromptTemplateRegistryTest {

    @Test
    @DisplayName("默认注册 default-review 和 security-review 模板")
    void defaultTemplatesRegistered() {
        var registry = new ReviewPromptTemplateRegistry();
        assertEquals(2, registry.size());
        assertNotNull(registry.get("default-review"));
        assertNotNull(registry.get("security-review"));
    }

    @Test
    @DisplayName("register 添加自定义模板")
    void registerCustomTemplate() {
        var registry = new ReviewPromptTemplateRegistry();
        registry.register(new ReviewPromptTemplate("custom", "Custom: ${x}", "custom"));
        assertEquals(3, registry.size());
        assertNotNull(registry.get("custom"));
    }

    @Test
    @DisplayName("register 覆盖同名模板")
    void registerOverwritesSameName() {
        var registry = new ReviewPromptTemplateRegistry();
        registry.register(new ReviewPromptTemplate("default-review", "Updated", "updated"));
        assertEquals("Updated", registry.get("default-review").template());
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("remove 删除模板")
    void removeTemplate() {
        var registry = new ReviewPromptTemplateRegistry();
        assertTrue(registry.remove("default-review"));
        assertFalse(registry.exists("default-review"));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("remove 不存在的模板返回 false")
    void removeNonExistentReturnsFalse() {
        var registry = new ReviewPromptTemplateRegistry();
        assertFalse(registry.remove("nonexistent"));
    }

    @Test
    @DisplayName("list 返回所有模板")
    void listReturnsAll() {
        var registry = new ReviewPromptTemplateRegistry();
        registry.register(new ReviewPromptTemplate("custom", "c", "c"));
        assertEquals(3, registry.list().size());
    }

    @Test
    @DisplayName("exists 检查模板是否存在")
    void existsChecksTemplate() {
        var registry = new ReviewPromptTemplateRegistry();
        assertTrue(registry.exists("default-review"));
        assertFalse(registry.exists("nonexistent"));
    }
}

@DisplayName("HumanReviewQueue 人工复核队列")
class HumanReviewQueueTest {

    @Test
    @DisplayName("submit 创建 PENDING 状态的复核项")
    void submitCreatesPendingItem() {
        var queue = new HumanReviewQueue();
        var decision = AutoReviewDecision.deny("shell_exec", "risky");
        var item = queue.submit("shell_exec", decision);
        assertEquals("review-1", item.id());
        assertEquals("shell_exec", item.action());
        assertEquals(HumanReviewQueue.ReviewStatus.PENDING, item.status());
        assertNotNull(item.submittedAt());
    }

    @Test
    @DisplayName("pending 返回所有待复核项")
    void pendingReturnsAllPending() {
        var queue = new HumanReviewQueue();
        queue.submit("a1", AutoReviewDecision.allow("a1"));
        queue.submit("a2", AutoReviewDecision.deny("a2", "risky"));
        assertEquals(2, queue.pendingCount());
        assertEquals(2, queue.pending().size());
    }

    @Test
    @DisplayName("approve 将待复核项标记为 APPROVED")
    void approveMarksAsApproved() {
        var queue = new HumanReviewQueue();
        var item = queue.submit("a1", AutoReviewDecision.deny("a1", "risky"));
        var approved = queue.approve(item.id(), "reviewer1", "false positive");
        assertNotNull(approved);
        assertEquals(HumanReviewQueue.ReviewStatus.APPROVED, approved.status());
        assertEquals("reviewer1", approved.reviewer());
        assertEquals("false positive", approved.reviewNote());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    @DisplayName("reject 将待复核项标记为 REJECTED")
    void rejectMarksAsRejected() {
        var queue = new HumanReviewQueue();
        var item = queue.submit("a1", AutoReviewDecision.allow("a1"));
        var rejected = queue.reject(item.id(), "reviewer1", "should deny");
        assertNotNull(rejected);
        assertEquals(HumanReviewQueue.ReviewStatus.REJECTED, rejected.status());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    @DisplayName("approve 不存在的 id 返回 null")
    void approveNonExistentReturnsNull() {
        var queue = new HumanReviewQueue();
        assertNull(queue.approve("nonexistent", "r", "n"));
    }

    @Test
    @DisplayName("approve 已处理的项返回 null")
    void approveAlreadyProcessedReturnsNull() {
        var queue = new HumanReviewQueue();
        var item = queue.submit("a1", AutoReviewDecision.allow("a1"));
        queue.approve(item.id(), "r", "n");
        assertNull(queue.approve(item.id(), "r2", "n2"));
    }

    @Test
    @DisplayName("next 返回第一个待复核项")
    void nextReturnsFirstPending() {
        var queue = new HumanReviewQueue();
        queue.submit("a1", AutoReviewDecision.allow("a1"));
        queue.submit("a2", AutoReviewDecision.deny("a2", "r"));
        var next = queue.next();
        assertNotNull(next);
        assertEquals("a1", next.action());
    }

    @Test
    @DisplayName("next 无待复核项返回 null")
    void nextReturnsNullWhenEmpty() {
        var queue = new HumanReviewQueue();
        assertNull(queue.next());
    }

    @Test
    @DisplayName("all 返回所有项含已处理")
    void allReturnsAllItems() {
        var queue = new HumanReviewQueue();
        queue.submit("a1", AutoReviewDecision.allow("a1"));
        queue.submit("a2", AutoReviewDecision.deny("a2", "r"));
        queue.approve("review-1", "r", "n");
        assertEquals(2, queue.all().size());
        assertEquals(1, queue.pendingCount());
    }

    @Test
    @DisplayName("clear 清空队列")
    void clearEmptiesQueue() {
        var queue = new HumanReviewQueue();
        queue.submit("a1", AutoReviewDecision.allow("a1"));
        queue.clear();
        assertEquals(0, queue.all().size());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    @DisplayName("连续 submit 生成递增 id")
    void sequentialSubmitsGenerateIncrementingIds() {
        var queue = new HumanReviewQueue();
        var item1 = queue.submit("a1", AutoReviewDecision.allow("a1"));
        var item2 = queue.submit("a2", AutoReviewDecision.allow("a2"));
        var item3 = queue.submit("a3", AutoReviewDecision.allow("a3"));
        assertEquals("review-1", item1.id());
        assertEquals("review-2", item2.id());
        assertEquals("review-3", item3.id());
    }
}
