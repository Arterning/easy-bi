package com.bi.model.dto;

import java.util.List;
import java.util.Map;

/** A small, safe chart contract shared by the agent and the web client. */
public record ChartSpec(
        String chartType,
        String title,
        String xField,
        List<String> yFields,
        String seriesField,
        List<Map<String, Object>> data,
        long totalRows,
        boolean truncated
) {}
