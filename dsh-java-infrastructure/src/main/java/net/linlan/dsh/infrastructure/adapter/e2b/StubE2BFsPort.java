package net.linlan.dsh.infrastructure.adapter.e2b;

import net.linlan.dsh.domain.e2b.adapter.port.IE2BFsPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** Stub E2B filesystem port for local mode (no real E2B sandbox). */
@Service
public class StubE2BFsPort implements IE2BFsPort {
    @Override public String readFile(String sandboxId, String path) { return ""; }
    @Override public void writeFile(String sandboxId, String path, String content) {}
    @Override public List<String> listDir(String sandboxId, String dir) { return List.of(); }
    @Override public void delete(String sandboxId, String path) {}
}
