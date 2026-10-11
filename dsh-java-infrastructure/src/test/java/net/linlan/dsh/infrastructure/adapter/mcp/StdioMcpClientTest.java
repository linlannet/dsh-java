package net.linlan.dsh.infrastructure.adapter.mcp;

import net.linlan.dsh.domain.tool.mcp.IMcpClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class StdioMcpClientTest {

    @Test
    void connectsAndListsToolsOverChildProcessStdio() throws Exception {
        String serverScript = """
                import json,sys
                for line in sys.stdin:
                    request=json.loads(line)
                    result={} if request.get('method')=='initialize' else {
                        'tools':[{'name':'search','description':'Search','inputSchema':{}}]
                    }
                    print(json.dumps({
                        'jsonrpc':'2.0','id':request.get('id'),'result':result
                    }),flush=True)
                """;
        StdioMcpClient client = new StdioMcpClient(
                "python3",
                List.of("-u", "-c", serverScript),
                Map.of(),
                null,
                5000
        );

        try {
            client.connect().get(5, TimeUnit.SECONDS);
            List<IMcpClient.McpToolDescriptor> tools = client.listTools().get(5, TimeUnit.SECONDS);

            assertTrue(client.isConnected());
            assertEquals(1, tools.size());
            assertEquals("search", tools.get(0).name());
        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
        }
    }
}
