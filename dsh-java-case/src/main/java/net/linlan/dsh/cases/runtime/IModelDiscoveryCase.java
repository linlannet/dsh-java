package net.linlan.dsh.cases.runtime;

import java.util.List;

/**
 * 上游模型同步用例。
 * <p>按渠道协议从上游拉取可用模型列表；失败抛错由上层提示，不覆盖已有配置。</p>
 *
 *
 */
public interface IModelDiscoveryCase {

    /**
     * 同步上游模型列表。
     *
     * @param baseUrl  渠道基础地址（必填）
     * @param apiKey   API Key（可为空）
     * @param protocol 渠道协议编码（openai / anthropic / ollama），空值按 openai 处理
     * @return 已排序的模型编码列表
     */
    List<String> syncModels(String baseUrl, String apiKey, String protocol);
}
