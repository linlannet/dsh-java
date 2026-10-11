package net.linlan.dsh.domain.tool.service;

/**
 * ToolArgumentsParser 表示ToolArgumentsParser参数。
 */
@FunctionalInterface
public interface ToolArgumentsParser {

    Object parse(String arguments);

    static ToolArgumentsParser identity() {
        return arguments -> arguments;
    }
}
