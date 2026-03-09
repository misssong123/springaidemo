package com.example.springaidemo.alidemo.agents;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.example.springaidemo.alidemo.tools.ExchangeRateTool;
import com.google.gson.Gson;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BailianAgent {
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        Generation gen = new Generation();

        // 1. 初始化对话历史
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder().role(Role.USER.getValue()).content("我现在有100美金，换成人民币是多少钱？").build());

        // 2. 定义工具
        ToolBase tool = ToolFunction.builder()
                .function(ExchangeRateTool.getDefinition())
                .build();

        // 3. 第一次请求：询问模型是否需要调用工具
        GenerationParam param = GenerationParam.builder()
                .model("qwen-plus")
                .apiKey(apiKey)
                .messages(messages)
                .tools(Collections.singletonList(tool))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE) // 必须使用 MESSAGE 格式
                .build();

        GenerationResult result = gen.call(param);
        Message assistantMessage = result.getOutput().getChoices().get(0).getMessage();
        // 3. 打印模型的回复
        System.out.println(assistantMessage.getContent());
        messages.add(assistantMessage); // 将模型的回复（包含工具调用请求）放入历史

        // 4. 解析工具调用 (Tool Calls)
        if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            for (ToolCallBase toolCall : assistantMessage.getToolCalls()) {
                if (toolCall instanceof ToolCallFunction) {
                    ToolCallFunction functionCall = (ToolCallFunction) toolCall;
                    String functionName = functionCall.getFunction().getName();
                    String arguments = functionCall.getFunction().getArguments();

                    System.out.println("模型决定调用工具: " + functionName + "，参数: " + arguments);

                    // 5. 执行本地业务逻辑
                    if ("getExchangeRate".equals(functionName)) {
                        // 解析模型给出的 JSON 参数
                        Map<String, String> argMap = gson.fromJson(arguments, Map.class);
                        double rate = ExchangeRateTool.getExchangeRate(argMap.get("from"), argMap.get("to"));

                        // 6. 将工具执行结果包装成 MESSAGE 角色为 TOOL
                        Message toolResultMessage = Message.builder()
                                .role(Role.TOOL.getValue())
                                .content(String.valueOf(rate))
                                .toolCallId(functionCall.getId()) // 必须对应 tool_call 的 ID
                                .name(functionName)
                                .build();
                        messages.add(toolResultMessage);
                    }
                }
            }

            // 7. 第二次请求：将执行结果发回模型，获取最终自然语言回复
            param.setMessages(messages);
            GenerationResult finalResult = gen.call(param);
            System.out.println("最终回复: " + finalResult.getOutput().getChoices().get(0).getMessage().getContent());
        } else {
            System.out.println("回复: " + assistantMessage.getContent());
        }
    }
}
