package cn.walioffice.plugin.sample;

import net.linlan.dsh.domain.model.entity.AbstractTool;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 天气查询工具（模拟数据）。
 * <p>
 * 继承 {@link AbstractTool}，只需实现 name/description/parameters/run。
 * 演示一个无副作用、可并发调用的只读工具。
 *
 * <p>调用流程：接收输入 → 执行本类职责 → 返回结果。</p>
 * <p>示例：根据调用方传入参数完成一次独立操作。</p>
 * 
 */
public class WeatherQueryTool extends AbstractTool {

    @Override
    public String name() {
        return "weather_query";
    }

    @Override
    public String description() {
        return "查询指定城市的天气信息（温度、天气状况、湿度）。";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("city", stringSchema("城市名称，如 北京、上海、深圳"))
                .required("city")
                .build();
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return objectSchema()
                .prop("city", stringSchema(null))
                .prop("temperature", stringSchema(null))
                .prop("condition", stringSchema(null))
                .prop("humidity", stringSchema(null))
                .build();
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) {
        return true;
    }
    /**
     * 运行目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param Map<String Map String参数。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */


    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String city = str(args, "city", "未知");
        return ok(mockWeather(city));
    }
    /**
     * mock Weather。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param city city参数。
     * @return 方法执行结果。
     */


    private String mockWeather(String city) {
        int temp = switch (city) {
            case "北京", "Beijing" -> 28;
            case "上海", "Shanghai" -> 31;
            case "深圳", "Shenzhen" -> 33;
            case "广州", "Guangzhou" -> 34;
            default -> 25;
        };
        String condition = temp >= 32 ? "晴" : temp >= 28 ? "多云" : "阴";
        int humidity = temp >= 32 ? 70 : 55;

        return String.format("【%s 天气】温度 %d°C，天气 %s，湿度 %d%%", city, temp, condition, humidity);
    }
}
