package org.ruoyi.service.impl;

import cn.hutool.json.JSONObject;
import com.google.protobuf.ServiceException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.batch.api.ObjectsBatchDeleter;
import io.weaviate.client.v1.batch.model.BatchDeleteResponse;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.Schema;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.service.ConfigService;
import org.ruoyi.domain.bo.QueryVectorBo;
import org.ruoyi.domain.bo.StoreEmbeddingBo;
import org.ruoyi.service.VectorStoreService;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量库管理
 *
 * @author ageer
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final ConfigService configService;

//    private EmbeddingStore<TextSegment> embeddingStore;
    private WeaviateClient client;


    @Override
    public void createSchema(String kid, String modelName) {
        String protocol = configService.getConfigValue("weaviate", "protocol");
        String host = configService.getConfigValue("weaviate", "host");
        String className = configService.getConfigValue("weaviate", "classname")+kid;
        // 创建 Weaviate 客户端
        client= new WeaviateClient(new Config(protocol, host));
        // 检查类是否存在，如果不存在就创建 schema
        Result<Schema> schemaResult = client.schema().getter().run();
        Schema schema = schemaResult.getResult();
        boolean classExists = false;
        for (WeaviateClass weaviateClass : schema.getClasses()) {
            if (weaviateClass.getClassName().equals(className)) {
                classExists = true;
                break;
            }
        }
        if (!classExists) {
            // 类不存在，创建 schema
            WeaviateClass build = WeaviateClass.builder()
                    .className(className)
                    .vectorizer("none")
                    .properties(
                            List.of(Property.builder().name("text").dataType(Collections.singletonList("text")).build(),
                                    Property.builder().name("fid").dataType(Collections.singletonList("text")).build(),
                                    Property.builder().name("kid").dataType(Collections.singletonList("text")).build(),
                                    Property.builder().name("docId").dataType(Collections.singletonList("text")).build())
                    )
                    .build();
            Result<Boolean> createResult = client.schema().classCreator().withClass(build).run();
            if (createResult.hasErrors()) {
                log.error("Schema 创建失败: {}", createResult.getError());
            } else {
                log.info("Schema 创建成功: {}", className);
            }
        }
//        embeddingStore = WeaviateEmbeddingStore.builder()
//                .scheme(protocol)
//                .host(host)
//                .objectClass(className)
//                .scheme(protocol)
//                .avoidDups(true)
//                .consistencyLevel("ALL")
//                .build();
    }

    @Override
    public void storeEmbeddings(StoreEmbeddingBo storeEmbeddingBo) {
        createSchema(storeEmbeddingBo.getKid(), storeEmbeddingBo.getVectorModelName());
        EmbeddingModel embeddingModel = getEmbeddingModel(storeEmbeddingBo.getEmbeddingModelName(),
                storeEmbeddingBo.getApiKey(), storeEmbeddingBo.getBaseUrl());
        List<String> chunkList = storeEmbeddingBo.getChunkList();
        List<String> fidList = storeEmbeddingBo.getFids();
        String kid = storeEmbeddingBo.getKid();
        String docId = storeEmbeddingBo.getDocId();
        log.info("向量存储条数记录: " + chunkList.size());
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < chunkList.size(); i++) {
            String text = chunkList.get(i);
            String fid = fidList.get(i);
            Embedding embedding = embeddingModel.embed(text).content();
            Map<String, Object> properties = Map.of(
                    "text", text,
                    "fid",fid,
                    "kid", kid,
                    "docId", docId
            );
            Float[] vector = toObjectArray(embedding.vector());
            client.data().creator()
                    .withClassName("LocalKnowledge" + kid) // 注意替换成实际类名
                    .withProperties(properties)
                    .withVector(vector)
                    .run();
        }
        long endTime = System.currentTimeMillis();
        log.info("向量存储完成消耗时间："+ (endTime-startTime)/1000+"秒");
    }

    private static Float[] toObjectArray(float[] primitive) {
        Float[] result = new Float[primitive.length];
        for (int i = 0; i < primitive.length; i++) {
            result[i] = primitive[i]; // 自动装箱
        }
        return result;
    }
    @Override
    public List<String> getQueryVector(QueryVectorBo queryVectorBo) {
        log.info("🚀 开始向量检索 - 知识库ID: {}, 查询内容: {}", queryVectorBo.getKid(), queryVectorBo.getQuery());
        createSchema(queryVectorBo.getKid(), queryVectorBo.getVectorModelName());
        EmbeddingModel embeddingModel = getEmbeddingModel(queryVectorBo.getEmbeddingModelName(),
                queryVectorBo.getApiKey(), queryVectorBo.getBaseUrl());
        log.info("📊 开始向量化查询内容，使用模型: {}", queryVectorBo.getEmbeddingModelName());
        Embedding queryEmbedding = embeddingModel.embed(queryVectorBo.getQuery()).content();
        float[] vector = queryEmbedding.vector();
        List<String> vectorStrings = new ArrayList<>();
        for (float v : vector) {
            vectorStrings.add(String.valueOf(v));
        }
        String vectorStr = String.join(",", vectorStrings);
        String className = configService.getConfigValue("weaviate", "classname") ;
        // 构建 GraphQL 查询（包含相似度分数）
        String graphQLQuery = String.format(
                "{\n" +
                        "  Get {\n" +
                        "    %s(nearVector: {vector: [%s]} limit: %d) {\n" +
                        "      text\n" +
                        "      fid\n" +
                        "      kid\n" +
                        "      docId\n" +
                        "      _additional {\n" +
                        "        distance\n" +
                        "        certainty\n" +
                        "        id\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                className+ queryVectorBo.getKid(),
                vectorStr,
                queryVectorBo.getMaxResults()
        );

        log.info("🔍 执行Weaviate向量搜索查询");
        Result<GraphQLResponse> result = client.graphQL().raw().withQuery(graphQLQuery).run();
        List<String> resultList = new ArrayList<>();
        if (result != null && !result.hasErrors()) {
            Object data = result.getResult().getData();
            JSONObject entries = new JSONObject(data);
            Map<String, cn.hutool.json.JSONArray> entriesMap = entries.get("Get", Map.class);
            cn.hutool.json.JSONArray objects = entriesMap.get(className + queryVectorBo.getKid());
            log.info("📊 Weaviate查询完成，找到 {} 条向量记录", objects.size());
            
            if(objects.isEmpty()){
                log.warn("⚠️ 没有找到相关的向量数据");
                return resultList;
            }
            
            // 记录每条结果的详细信息
            for (int i = 0; i < objects.size(); i++) {
                Object object = objects.get(i);
                Map<String, Object> map = (Map<String, Object>) object;
                String content = (String) map.get("text");
                String fid = (String) map.get("fid");
                String docId = (String) map.get("docId");
                
                // 获取相似度信息
                Map<String, Object> additional = (Map<String, Object>) map.get("_additional");
                Double distance = additional != null ? (Double) additional.get("distance") : null;
                Double certainty = additional != null ? (Double) additional.get("certainty") : null;
                
                // 计算相似度百分比（certainty * 100）
                String similarityPercent = certainty != null ? String.format("%.2f%%", certainty * 100) : "未知";
                
                log.info("📄 向量结果[{}] - 相似度: {}, 距离: {}, 文档ID: {}, 片段ID: {}", 
                        i + 1, similarityPercent, distance, docId, fid);
                
                // 记录内容预览（前150字符）
                String preview = content.length() > 150 ? content.substring(0, 150) + "..." : content;
                log.info("📝 内容预览[{}]: {}", i + 1, preview);
                
                resultList.add(content);
            }
            
            log.info("✅ 向量检索成功，返回 {} 条相关文档", resultList.size());
            return resultList;
        } else {
            log.error("GraphQL 查询失败: {}", result.getError());
            return resultList;
        }
    }

    @Override
    @SneakyThrows
    public void removeById(String id, String modelName) {
        String protocol = configService.getConfigValue("weaviate", "protocol");
        String host = configService.getConfigValue("weaviate", "host");
        String className = configService.getConfigValue("weaviate", "classname");
        String finalClassName = className + id;
        WeaviateClient client = new WeaviateClient(new Config(protocol, host));
        Result<Boolean> result = client.schema().classDeleter().withClassName(finalClassName).run();
        if (result.hasErrors()) {
            log.error("失败删除向量: " + result.getError());
            throw new ServiceException("失败删除向量数据!");
        } else {
            log.info("成功删除向量数据: " + result.getResult());
        }
    }

    @Override
    public void removeByDocId(String docId, String kid) {
        String className = configService.getConfigValue("weaviate", "classname") + kid;
        // 构建 Where 条件
        WhereFilter whereFilter = WhereFilter.builder()
                .path("docId")
                .operator(Operator.Equal)
                .valueText(docId)
                .build();
        ObjectsBatchDeleter deleter = client.batch().objectsBatchDeleter();
        Result<BatchDeleteResponse> result = deleter.withClassName(className)
                .withWhere(whereFilter)
                .run();
        if (result != null && !result.hasErrors()) {
            log.info("成功删除 docId={} 的所有向量数据", docId);
        } else {
            log.error("删除失败: {}", result.getError());
        }
    }

    @Override
    public void removeByFid(String fid, String kid) {
        String className = configService.getConfigValue("weaviate", "classname") + kid;
        // 构建 Where 条件
        WhereFilter whereFilter = WhereFilter.builder()
                .path("fid")
                .operator(Operator.Equal)
                .valueText(fid)
                .build();
        ObjectsBatchDeleter deleter = client.batch().objectsBatchDeleter();
        Result<BatchDeleteResponse> result = deleter.withClassName(className)
                .withWhere(whereFilter)
                .run();
        if (result != null && !result.hasErrors()) {
            log.info("成功删除 fid={} 的所有向量数据", fid);
        } else {
            log.error("删除失败: {}", result.getError());
        }
    }

    /**
     * 获取向量模型
     */
    @SneakyThrows
    public EmbeddingModel getEmbeddingModel(String modelName, String apiKey, String baseUrl) {
        EmbeddingModel embeddingModel;
        if ("quentinz/bge-large-zh-v1.5".equals(modelName)) {
            embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
        } else if ("baai/bge-m3".equals(modelName)) {
            embeddingModel = OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
        } else {
            throw new ServiceException("未找到对应向量化模型!");
        }
        return embeddingModel;
    }

}
