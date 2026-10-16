package net.linlan.dsh.domain.document.office.service;

import net.linlan.dsh.domain.document.office.adapter.port.IOfficePdfCachePort;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeToPdfResult;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * OfficePdfCacheGovernance Office 转 PDF 缓存容量治理服务。
 * 提供最大条目数限制与 LRU 驱逐策略，委托 IOfficePdfCachePort 实现持久化。
 */
public final class OfficePdfCacheGovernance {

    private final IOfficePdfCachePort cachePort;
    private final int maxEntries;
    private final LinkedHashMap<String, Boolean> keyOrder = new LinkedHashMap<>(16, 0.75f, true);

    public OfficePdfCacheGovernance(IOfficePdfCachePort cachePort, int maxEntries) {
        this.cachePort = Objects.requireNonNull(cachePort, "cachePort 不能为空");
        this.maxEntries = maxEntries > 0 ? maxEntries : 100;
    }

    public OfficePdfCacheGovernance(IOfficePdfCachePort cachePort) {
        this(cachePort, 100);
    }

    public synchronized OfficeToPdfResult get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        OfficeToPdfResult result = cachePort.load(key);
        if (result != null) {
            keyOrder.get(key);
        }
        return result;
    }

    public synchronized void put(String key, OfficeToPdfResult result) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(result, "result 不能为空");
        if (keyOrder.size() >= maxEntries && !keyOrder.containsKey(key)) {
            evictOldest();
        }
        cachePort.save(key, result);
        keyOrder.put(key, true);
    }

    public synchronized void remove(String key) {
        if (key != null) {
            cachePort.remove(key);
            keyOrder.remove(key);
        }
    }

    public synchronized void clear() {
        cachePort.clear();
        keyOrder.clear();
    }

    public synchronized int size() {
        return keyOrder.size();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public synchronized boolean isFull() {
        return keyOrder.size() >= maxEntries;
    }

    private void evictOldest() {
        if (!keyOrder.isEmpty()) {
            String oldest = keyOrder.keySet().iterator().next();
            keyOrder.remove(oldest);
            cachePort.remove(oldest);
        }
    }
}
