package com.example.springaidemo.mcpdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class AliYunCompatibleMcpServer {
    private static final ObjectMapper mapper = new ObjectMapper();

    // JDK 17 Record: 定义 MCP 标准响应格式
    public record McpResponse(String jsonrpc, long id, Map<String, Object> result) {}
    public record ToolResult(List<Map<String, String>> content, boolean isError) {}

    public static void main(String[] args) {
        // 使用 System.err 打印日志，避免污染标准输出 (MCP 协议使用 System.out 传输数据)
        System.err.println("MCP Server 启动，等待 Client 指令...");

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank()){
                continue;
            }
            try {
                // 1. 解析 Client 发来的 JSON-RPC 请求
                var request = mapper.readTree(line);
                String method = request.get("method").asText();
                long id = request.get("id").asLong();

                // 2. 根据 MCP 协议处理方法
                if ("tools/list".equals(method)) {
                    sendResponse(id, Map.of("tools", List.of(getWeatherTool())));
                } else if ("tools/call".equals(method)) {
                    String toolName = request.get("params").get("name").asText();
                    if ("get_weather".equals(toolName)) {
                        String city = request.get("params").get("arguments").get("city").asText();
                        sendResponse(id, Map.of("content", List.of(Map.of("type", "text", "text", city + "天气晴，25度"))));
                    }
                }
            } catch (Exception e) {
                System.err.println("解析失败: " + e.getMessage());
            }
        }
    }

    private static Map<String, Object> getWeatherTool() {
        return Map.of(
                "name", "get_weather",
                "description", "查询天气",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("city", Map.of("type", "string"))
                )
        );
    }

    private static void sendResponse(long id, Map<String, Object> result) throws Exception {
        var resp = new McpResponse("2.0", id, result);
        // 必须输出到 System.out，这是 MCP 通信管道
        System.out.println(mapper.writeValueAsString(resp));
    }
}
