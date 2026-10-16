package net.linlan.dsh.domain.document.office.adapter.port;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeToPdfResult;

/**
 * IOfficePdfCachePort Office 转 PDF 缓存持久化端口。
 */
public interface IOfficePdfCachePort {
    OfficeToPdfResult load(String key);
    void save(String key, OfficeToPdfResult result);
    void remove(String key);
    void clear();
    int size();
}
