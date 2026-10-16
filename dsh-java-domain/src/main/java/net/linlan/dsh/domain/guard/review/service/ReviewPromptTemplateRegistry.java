package net.linlan.dsh.domain.guard.review.service;

import net.linlan.dsh.domain.guard.review.model.valobj.ReviewPromptTemplate;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReviewPromptTemplateRegistry 管理可配置的审查 prompt 模板。
 */
public final class ReviewPromptTemplateRegistry {

    private final Map<String, ReviewPromptTemplate> templates = new ConcurrentHashMap<>();

    public ReviewPromptTemplateRegistry() {
        register(ReviewPromptTemplate.defaultReview());
        register(ReviewPromptTemplate.securityReview());
    }

    public void register(ReviewPromptTemplate template) {
        Objects.requireNonNull(template, "template 不能为空");
        templates.put(template.name(), template);
    }

    public ReviewPromptTemplate get(String name) {
        return templates.get(name);
    }

    public Collection<ReviewPromptTemplate> list() {
        return templates.values();
    }

    public boolean remove(String name) {
        return templates.remove(name) != null;
    }

    public boolean exists(String name) {
        return templates.containsKey(name);
    }

    public int size() {
        return templates.size();
    }
}
