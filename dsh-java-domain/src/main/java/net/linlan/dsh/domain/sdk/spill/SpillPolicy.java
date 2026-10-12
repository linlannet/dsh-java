package net.linlan.dsh.domain.sdk.spill;

public final class SpillPolicy {

    public record SpillApplyResult(String content, SpillStore.SpillRef spillRef) {}

    private final SpillStore store;

    public SpillPolicy(SpillStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store required");
        }
        this.store = store;
    }

    public boolean shouldSpill(String content, int threshold) {
        if (content == null) {
            return false;
        }
        return content.length() > threshold;
    }

    public SpillApplyResult apply(String content, int threshold) {
        if (content == null) {
            return new SpillApplyResult(null, null);
        }
        if (!shouldSpill(content, threshold)) {
            return new SpillApplyResult(content, null);
        }
        SpillStore.SpillRef ref = store.spill(content);
        String placeholder = "[spilled:" + ref.refId() + ":size=" + ref.originalSize() + "]";
        return new SpillApplyResult(placeholder, ref);
    }
}
