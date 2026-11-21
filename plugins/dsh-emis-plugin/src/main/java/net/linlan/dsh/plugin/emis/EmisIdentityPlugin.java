package net.linlan.dsh.plugin.emis;

import net.linlan.dsh.domain.spi.AbstractHarnessPlugin;
import net.linlan.dsh.domain.spi.PluginContext;

/**
 * linlan-emis 企业身份插件（P0：只做鉴权工具）。
 * 身份事实来源为 emis；插件只验 RS256 OIDC 令牌，不接触 HS256 平台令牌、不直连数据库。
 */
public class EmisIdentityPlugin extends AbstractHarnessPlugin {

    public EmisIdentityPlugin() {
        super("emis-identity");
    }

    @Override
    public void configure(PluginContext context) {
        EmisConfig config = EmisConfig.from(context);
        EmisTokenVerifier verifier = new EmisTokenVerifier(config);

        context.registerTool(new WhoamiTool(verifier));
        context.registerTool(new ScopeCheckTool(verifier));

        context.registerSystemPrompt("identity", 30,
                "## Enterprise identity (linlan-emis)\n"
                + "- Identity, tenant, role and scope are issued by linlan-emis; never infer or elevate them.\n"
                + "- Use the whoami tool to read the current identity from an RS256 OIDC token.\n"
                + "- Use scope_check before sensitive actions (e.g. binding:write, user:read).\n"
                + "- HS256 platform tokens are NOT accepted by this plugin; dsh-java must use OIDC RS256 tokens.\n");

        log.info("[emis-identity] configured, baseUrl={}, jwksPath={}", config.baseUrl(), config.jwksPath());
    }
}
