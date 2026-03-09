package com.example.springaidemo.alidemo.tools;

import com.alibaba.dashscope.tools.FunctionDefinition;
import com.google.gson.JsonObject;



public class ExchangeRateTool{
    public static double getExchangeRate(String from, String to) {
        if ("USD".equals(from) && "CNY".equals(to)) return 7.25;
        return 1.0;
    }

    public static FunctionDefinition getDefinition() {
        // 构建符合 JSON Schema 标准的 JsonObject
        JsonObject props = new JsonObject();

        JsonObject fromParam = new JsonObject();
        fromParam.addProperty("type", "string");
        fromParam.addProperty("description", "源货币代码");

        JsonObject toParam = new JsonObject();
        toParam.addProperty("type", "string");
        toParam.addProperty("description", "目标货币代码");

        props.add("from", fromParam);
        props.add("to", toParam);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", props);

        return FunctionDefinition.builder()
                .name("getExchangeRate")
                .description("查询实时货币汇率")
                .parameters(parameters) // 这里的 parameters 接收 JsonObject
                .build();
    }

}
