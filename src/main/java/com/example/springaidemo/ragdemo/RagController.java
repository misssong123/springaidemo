package com.example.springaidemo.ragdemo;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.InputRequiredException;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    // 实际开发中请放在 application.yml 中
    private static final String API_KEY = System.getenv("DASHSCOPE_API_KEY");;

    // 模拟 MySQL 存储 (生产环境请替换为真正的 JPA/MyBatis 操作)
    private static final List<Map<String, Object>> mockMysqlTable = new ArrayList<>();

    /**
     * 1. 知识入库接口：将文本转为向量并存入“数据库”
     */
    @PostMapping("/ingest")
    public String ingest(@RequestBody String content) throws Exception {
        System.out.println(content);
        // 调用百炼 Embedding 模型
        List<Double> vector = getEmbedding(content);

        // 模拟 SQL: INSERT INTO knowledge_base (content, vector) VALUES (...)
        Map<String, Object> record = new HashMap<>();
        record.put("content", content);
        record.put("vector", vector);
        System.out.println(record);
        mockMysqlTable.add(record);

        return "成功导入一条知识，当前库内条数: " + mockMysqlTable.size();
    }

    /**
     * 2. RAG 问答接口：检索 + 生成
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String question) throws Exception {
        // Step A: 将问题向量化
        List<Double> queryVector = getEmbedding(question);

        // Step B: 检索 (在 MySQL 中计算余弦相似度并取 Top 2)
        String context = mockMysqlTable.stream()
                .sorted((a, b) -> Double.compare(
                        calculateCosineSimilarity(queryVector, (List<Double>) b.get("vector")),
                        calculateCosineSimilarity(queryVector, (List<Double>) a.get("vector"))
                ))
                .limit(2)
                .map(m -> (String) m.get("content"))
                .collect(Collectors.joining("\n"));

        // Step C: 构造增强 Prompt 并调用百炼 LLM (通义千问)
        return callQwen(question, context);
    }

    // --- 工具方法：调用百炼 Embedding 模型 ---
    private List<Double> getEmbedding(String text) throws NoApiKeyException {
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .model("text-embedding-v2")
                .texts(Collections.singletonList(text))
                .apiKey(API_KEY)
                .build();
        TextEmbeddingResult result = new TextEmbedding().call(param);
        return result.getOutput().getEmbeddings().get(0).getEmbedding();
    }

    // --- 工具方法：调用百炼 LLM ---
    private String callQwen(String question, String context) throws NoApiKeyException, InputRequiredException {
        String prompt = String.format(
                "你是一个基于私有知识库的助手。请参考以下【已知信息】回答【用户问题】。如果信息不足，请委婉告知。\n\n" +
                        "【已知信息】：\n%s\n\n" +
                        "【用户问题】：%s", context, question);

        Message msg = Message.builder().role(Role.USER.getValue()).content(prompt).build();
        GenerationParam param = GenerationParam.builder()
                .model("qwen-plus")
                .messages(Collections.singletonList(msg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .apiKey(API_KEY)
                .build();

        GenerationResult result = new Generation().call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent();
    }

    // --- 工具方法：计算余弦相似度 ---
    private double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            normA += Math.pow(vec1.get(i), 2);
            normB += Math.pow(vec2.get(i), 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
