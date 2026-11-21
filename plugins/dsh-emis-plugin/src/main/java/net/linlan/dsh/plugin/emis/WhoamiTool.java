package net.linlan.dsh.plugin.emis;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.linlan.dsh.domain.model.entity.AbstractTool;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 校验 RS256 令牌并返回身份摘要；不接受/不记录 HS256 令牌。 */
public class WhoamiTool extends AbstractTool {

    private final EmisTokenVerifier verifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public WhoamiTool(EmisTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public String name() {
        return "whoami";
    }

    @Override
    public String description() {
        return "校验 linlan-emis 签发的 RS256 OIDC access token，返回当前用户/租户/角色/scope。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("token", stringSchema("RS256 OIDC access token（必填）"))
                .required("token")
                .build();
    }

    @Override
    public boolean isConcurrencySafe(Object args) {
        return true;
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String token = str(args, "token");
        try {
            TokenClaims claims = verifier.verify(token);
            return ok(mapper.writeValueAsString(verifier.describe(claims)));
        } catch (TokenException ex) {
            return fail(ex.getMessage(), ex.getCode());
        } catch (Exception ex) {
            return fail("身份校验失败", "EMIS_TOKEN_INVALID");
        }
    }
}
