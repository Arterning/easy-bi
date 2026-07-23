package com.bi.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String SYSTEM_PROMPT = """
            你是 easy-bi 的 AI 数据分析助手。你可以帮助用户：
            1. 探索数据：查看有哪些表、各自有哪些列
            2. 编写 SQL 查询数据
            3. 生成 BI 报表（汇聚、排序、过滤、跨表 JOIN）
            4. 将查询保存为数据集，方便后续复用

            规则：
            - 所有数据表在 DuckDB 的 main schema 下，引用时使用 main."表名" 格式
            - 表名是物理名（如 t_a1b2c3d4），list_tables 返回的括号里就是物理名
            - 写 SQL 前先查看表结构，避免列名写错
            - 结果正确后主动将 SQL 保存为数据集（调用 create_dataset 工具）
            - 如果用户的描述不足以写 SQL，主动追问
            - 用中文回复
            - 回复中展示结果时，使用 Markdown 表格格式，表头用中文展示名
            """;

    private final LlmClient llm;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxRounds;

    /** Map<sessionId, List<Message>> — conversation history */
    private final Map<String, List<Map<String, Object>>> sessions = new ConcurrentHashMap<>();

    public AgentService(LlmClient llm, ToolRegistry toolRegistry,
                        @Value("${ai.agent.max-rounds:10}") int maxRounds) {
        this.llm = llm;
        this.toolRegistry = toolRegistry;
        this.maxRounds = maxRounds;
    }

    /**
     * Run the agent loop and push progress events to the callback.
     */
    public void run(String sessionId, String userMessage, AgentCallback callback) {
        List<Map<String, Object>> messages = sessions.computeIfAbsent(sessionId, k -> {
            List<Map<String, Object>> list = new ArrayList<>();
            list.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            return list;
        });

        // Add user message
        messages.add(Map.of("role", "user", "content", userMessage));

        var tools = toolRegistry.getFunctionDefinitions();

        try {
            for (int round = 0; round < maxRounds; round++) {
                callback.onThinking("思考中...");

                LlmClient.LlmResponse resp = llm.chat(messages, tools);

                if (resp.hasToolCalls()) {
                    // Store assistant message with tool calls
                    List<Map<String, Object>> toolCallsForMsg = new ArrayList<>();
                    for (LlmClient.LlmResponse.ToolCall tc : resp.toolCalls) {
                        toolCallsForMsg.add(Map.of(
                            "id", tc.id,
                            "type", "function",
                            "function", Map.of("name", tc.name, "arguments", tc.arguments)
                        ));
                    }
                    messages.add(Map.of(
                        "role", "assistant",
                        "content", resp.content != null ? resp.content : "",
                        "tool_calls", toolCallsForMsg
                    ));

                    // Execute each tool call
                    for (LlmClient.LlmResponse.ToolCall tc : resp.toolCalls) {
                        callback.onToolCall(tc.name, tc.arguments);
                        String result = executeTool(tc);
                        callback.onToolResult(tc.name, result);

                        // Add tool result message
                        messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", tc.id,
                            "content", result
                        ));
                    }

                } else {
                    // Final text response
                    if (resp.content != null) {
                        messages.add(Map.of("role", "assistant", "content", resp.content));
                    }
                    callback.onMessage(resp.content != null ? resp.content : "抱歉，我无法回答这个问题。");
                    callback.onDone();
                    return;
                }
            }
            // Exceeded max rounds
            callback.onMessage("抱歉，处理超时。请尝试简化需求。");
            callback.onDone();
        } catch (Exception e) {
            log.error("Agent error", e);
            callback.onError(e.getMessage());
        }
    }

    private String executeTool(LlmClient.LlmResponse.ToolCall tc) {
        AiTool tool = toolRegistry.get(tc.name).orElse(null);
        if (tool == null) {
            return "未知工具: " + tc.name;
        }
        try {
            Map<String, Object> args = mapper.readValue(tc.arguments,
                    new TypeReference<Map<String, Object>>() {});
            return tool.execute(args);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", tc.name, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /** Clear session history. */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    // ---- Callback interface ----

    public interface AgentCallback {
        void onThinking(String text);
        void onToolCall(String toolName, String arguments);
        void onToolResult(String toolName, String result);
        void onMessage(String text);
        void onError(String error);
        void onDone();
    }
}
