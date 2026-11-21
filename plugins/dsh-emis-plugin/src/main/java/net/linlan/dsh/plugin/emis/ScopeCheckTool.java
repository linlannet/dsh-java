package net.linlan.dsh.plugin.emis;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.linlan.dsh.domain.model.entity.AbstractTool;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 判定令牌是否具备指定 scope；scope 以 token 内 claim 为准，不支持外部提升。 */
public class ScopeCheckTool extends AbstractTool {

    private final EmisTokenVerifier verifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public ScopeCheckTool(EmisTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public String name() {
        return "scope_check";
    }

    @Override
    public String description() {
        return "校验当前 RS256 令牌是否包含指定 scope（如 binding:write、user:read）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("token", stringSchema("RS256 OIDC access token（必填）"))
                .prop("scope", stringSchema("待检查的 scope，如 binding:write"))
                .required("token")
                .required("scope")
                .build();
    }

    @Override
    public boolean isConcurrencySafe(Object args) {
        return true;
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String token = str(args, "token");
        String required = str(args, "scope");
        if (required.isBlank()) {
            return fail("缺少 scope 参数", "EMIS_SCOPE_MISSING");
        }
        try {
            TokenClaims claims = verifier.verify(token);
            boolean allowed = claims.scopes().contains(required);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("allowed", allowed);
            result.put("scope", required);
            result.put("grantedScopes", claims.scopes());
            result.put("tenantId", claims.tenantId());
            if (!allowed) {
                result.put("reason", "token 未包含该 scope");
            }
            return ok(mapper.writeValueAsString(result));
        } catch (TokenException ex) {
            return fail(ex.getMessage(), ex.getCode());
        } catch (Exception ex) {
            return fail("scope 校验失败", "EMIS_TOKEN_INVALID");
        }
    }
}
