package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.model.dto.ChartSpec;
import com.bi.model.dto.QueryResult;
import com.bi.service.QueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RenderChartTool implements AiTool {
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "bar", "line", "area", "pie", "scatter", "number", "table");
    private static final int MAX_CHART_ROWS = 200;

    private final QueryService queryService;
    private final ObjectMapper mapper;

    public RenderChartTool(ToolRegistry registry, QueryService queryService, ObjectMapper mapper) {
        this.queryService = queryService;
        this.mapper = mapper;
        registry.register(this);
    }

    @Override public String name() { return "render_chart"; }

    @Override
    public String description() {
        return "将只读 SQL 查询结果渲染为对话中的图表。查询成功且结果适合可视化时调用。"
                + "单值用 number，时间趋势用 line，分类比较用 bar，占比且分类不多用 pie，两个数值维度用 scatter，不适合时用 table。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("sql", Map.of("type", "string", "description", "生成图表数据的只读 SQL，列名应使用便于展示的别名"));
        properties.put("chartType", Map.of("type", "string", "enum", List.of("bar", "line", "area", "pie", "scatter", "number", "table"), "description", "图表类型"));
        properties.put("title", Map.of("type", "string", "description", "简短的中文图表标题"));
        properties.put("xField", Map.of("type", "string", "description", "横轴或分类字段；number 和 table 可省略"));
        properties.put("yFields", Map.of("type", "array", "items", Map.of("type", "string"), "description", "数值字段列表；table 可省略"));
        properties.put("seriesField", Map.of("type", "string", "description", "可选的系列分组字段"));
        return Map.of("type", "object", "properties", properties, "required", List.of("sql", "chartType", "title"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String sql = requiredString(args, "sql");
        String chartType = requiredString(args, "chartType").toLowerCase(Locale.ROOT);
        String title = requiredString(args, "title");
        String xField = optionalString(args, "xField");
        String seriesField = optionalString(args, "seriesField");
        List<String> yFields = stringList(args.get("yFields"));

        if (!ALLOWED_TYPES.contains(chartType)) throw new IllegalArgumentException("不支持的图表类型: " + chartType);

        QueryResult result = queryService.execute(sql, 0, MAX_CHART_ROWS);
        Set<String> columns = new LinkedHashSet<>(result.getColumns());
        validateField(columns, xField, "xField");
        validateField(columns, seriesField, "seriesField");
        for (String field : yFields) validateField(columns, field, "yFields");

        if (!Set.of("table", "number").contains(chartType)) {
            if (xField == null) throw new IllegalArgumentException(chartType + " 图表必须指定 xField");
            if (yFields.isEmpty()) throw new IllegalArgumentException(chartType + " 图表必须至少指定一个 yFields");
        }
        if ("number".equals(chartType) && yFields.isEmpty()) {
            throw new IllegalArgumentException("number 图表必须指定一个 yFields");
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (List<Object> row : result.getRows()) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (int i = 0; i < result.getColumns().size(); i++) item.put(result.getColumns().get(i), row.get(i));
            data.add(item);
        }

        ChartSpec spec = new ChartSpec(chartType, title, xField, yFields, seriesField, data,
                result.getTotalRows(), result.getTotalRows() > data.size());
        try {
            return mapper.writeValueAsString(spec);
        } catch (Exception e) {
            throw new IllegalStateException("图表数据序列化失败", e);
        }
    }

    private static String requiredString(Map<String, Object> args, String key) {
        String value = optionalString(args, key);
        if (value == null) throw new IllegalArgumentException(key + " 不能为空");
        return value;
    }

    private static String optionalString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static void validateField(Set<String> columns, String field, String argument) {
        if (field != null && !columns.contains(field)) {
            throw new IllegalArgumentException(argument + " 字段不存在: " + field + "，可用字段: " + columns);
        }
    }
}
