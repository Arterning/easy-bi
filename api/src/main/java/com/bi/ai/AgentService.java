package com.bi.ai;

import com.bi.service.ChatSessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

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
            - 如果数据集需要调整，使用 update_dataset 工具修改已有数据集的 SQL 或名称
            - 如果用户的描述不足以写 SQL，主动追问
            - 用中文回复
            - 回复中展示结果时，使用 Markdown 表格格式，表头用中文展示名
            """;

    private final LlmClient llm;
    private final ToolRegistry toolRegistry;
    private final ChatSessionService sessionService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxRounds;

    public AgentService(LlmClient llm, ToolRegistry toolRegistry,
                        ChatSessionService sessionService,
                        @Value("${ai.agent.max-rounds:10}") int maxRounds) {
        this.llm = llm;
        this.toolRegistry = toolRegistry;
        this.sessionService = sessionService;
        this.maxRounds = maxRounds;
    }

    /**
     * Get session messages for frontend display (replay history).
     */
    public List<Map<String, Object>> getSessionMessages(String sessionId) {
        return sessionService.loadMessages(sessionId);
    }

    /**
     * Run the agent loop. Messages are loaded from DB at start and saved after each tool call.
     * @param sessionId  existing or new session id
     * @param userMessage  user's latest message
     * @param title  session title (first message); only used when creating new session
     * @param createNew  if true, create a fresh session with system prompt only
     * @param callback  SSE event emitter
     */
    public void run(String sessionId, String userMessage, String title,
                    boolean createNew, AgentCallback callback) {

        List<Map<String, Object>> messages;

        if (createNew) {
            messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            sessionService.create(sessionId, title, messages);
        } else {
            messages = sessionService.loadMessages(sessionId);
            if (messages == null) {
                // Session not found — create new
                messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
                sessionService.create(sessionId, title, messages);
            }
        }

        // Add user message
        messages.add(Map.of("role", "user", "content", userMessage));
        sessionService.saveMessages(sessionId, title, messages);

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

                        messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", tc.id,
                            "content", result
                        ));
                    }

                    // Persist after each round
                    sessionService.saveMessages(sessionId, title, messages);

                } else {
                    // Final text response
                    if (resp.content != null) {
                        messages.add(Map.of("role", "assistant", "content", resp.content));
                    }
                    sessionService.saveMessages(sessionId, title, messages);
                    callback.onMessage(resp.content != null ? resp.content : "抱歉，我无法回答这个问题。");
                    callback.onDone();
                    return;
                }
            }
            // Exceeded max rounds
            callback.onMessage("抱歉，处理超时。请尝试简化需求。");
            sessionService.saveMessages(sessionId, title, messages);
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
