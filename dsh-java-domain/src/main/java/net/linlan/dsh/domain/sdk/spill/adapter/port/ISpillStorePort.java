package net.linlan.dsh.domain.sdk.spill.adapter.port;

public interface ISpillStorePort {

    void save(String refId, String content);

    String load(String refId);

    boolean exists(String refId);
}
