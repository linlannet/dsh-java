package net.linlan.dsh.domain.tool.shell;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 危险命令拦截器。
 * <p>
 * 在 shell_execute 实际执行前做静态模式匹配，拦截明显具有破坏性的命令
 * （递归强删、写块设备、fork 炸弹、磁盘格式化、关机重启等）。
 * 这是审批之外的第二层防线：审批是「谁来放行」，本守卫是「永远不放行」。
 * <p>
 * 可通过 {@code harness.shell.blocked-patterns} 追加自定义正则。
 *
 *
 */
public final class DangerousCommandGuard {

    private static final List<Pattern> BUILTIN = List.of(
            // 递归/强制删除根或家目录（含变量展开与选项乱序）
            Pattern.compile("\\brm\\b[^\\n;|]*-[a-zA-Z]*[rRf][a-zA-Z]*[fF]?[^\\n;|]*\\s+(/|/\\*|~|\\$HOME)(\\s|$)"),
            Pattern.compile("\\brm\\b[^\\n;|]*\\s--no-preserve-root\\b"),
            // 写块设备 / 磁盘清零
            Pattern.compile("\\b(dd|mkfs|mkfs\\.[a-z0-9]+|fdisk|sfdisk|parted|wipefs|shred)\\b[^\\n;|]*\\b/dev/(sd|nvme|vd|hd|disk)"),
            Pattern.compile("\\b(mkfs|mkswap)\\b(\\s|$)"),
            // fork 炸弹
            Pattern.compile(":\\s*\\(\\s*\\)\\s*\\{[^}]*:\\s*\\|\\s*:"),
            // 关机/重启/注销
            Pattern.compile("\\b(shutdown|poweroff|halt|reboot|init\\s+[06])\\b(\\s|$)"),
            // 覆写系统关键目录
            Pattern.compile(">\\s*/(etc|boot|bin|sbin|usr/bin|usr/sbin|System|Windows)/"),
            // chmod/chown 递归打根
            Pattern.compile("\\b(chmod|chown)\\b[^\\n;|]*-[a-zA-Z]*R[^\\n;|]*\\s+/\\s*$"),
            // curl|sh 管道远程脚本
            Pattern.compile("\\b(curl|wget)\\b[^\\n|]*\\|\\s*(sudo\\s+)?(ba|z|fi)?sh\\b"),
            // 删除引导/影子文件
            Pattern.compile("\\brm\\b[^\\n;|]*(/etc/(passwd|shadow|sudoers)|/boot)(\\s|$)")
    );

    private final List<Pattern> extraPatterns;

    /**
     * 处理Dangerous 命令 Guard。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public DangerousCommandGuard() {
        this(List.of());
    }

    /**
     * 处理Dangerous 命令 Guard。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param extraRegexes extra Regexes参数。
     * @return 方法执行结果。
     */
    public DangerousCommandGuard(List<String> extraRegexes) {
        this.extraPatterns = extraRegexes == null ? List.of()
                : extraRegexes.stream().map(Pattern::compile).toList();
    }

    /**
     * 判定命令是否应被拦截。
     *
     * @return null 表示放行；否则返回人类可读的拦截原因
     */
    public String check(String command) {
        if (command == null || command.isBlank()) return null;
        String normalized = command.toLowerCase(Locale.ROOT);
        for (Pattern p : BUILTIN) {
            if (p.matcher(normalized).find()) {
                return "blocked dangerous command (matched " + p.pattern() + ")";
            }
        }
        for (Pattern p : extraPatterns) {
            if (p.matcher(command).find()) {
                return "blocked by custom rule (matched " + p.pattern() + ")";
            }
        }
        return null;
    }
}
