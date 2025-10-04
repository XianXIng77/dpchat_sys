package org.ruoyi.chat.service.chat.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollectionUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.ruoyi.chat.enums.promptTemplateEnum;
import org.ruoyi.chat.factory.ChatServiceFactory;
import org.ruoyi.chat.service.chat.IChatCostService;
import org.ruoyi.chat.service.chat.IChatService;
import org.ruoyi.chat.service.chat.ISseService;
import org.ruoyi.chat.support.ChatRetryHelper;
import org.ruoyi.chat.support.RetryNotifier;
import org.ruoyi.chat.util.SSEUtil;
import org.ruoyi.common.chat.entity.Tts.TextToSpeech;
import org.ruoyi.common.chat.entity.chat.Message;
import org.ruoyi.common.chat.entity.files.UploadFileResponse;
import org.ruoyi.common.chat.entity.whisper.WhisperResponse;
import org.ruoyi.common.chat.openai.OpenAiStreamClient;
import org.ruoyi.common.chat.request.ChatRequest;
import org.ruoyi.common.core.utils.DateUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.core.utils.file.FileUtils;
import org.ruoyi.common.core.utils.file.MimeTypeUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.ChatSessionBo;
import org.ruoyi.domain.bo.QueryVectorBo;
import org.ruoyi.domain.vo.ChatModelVo;
import org.ruoyi.domain.vo.KnowledgeInfoVo;
import org.ruoyi.domain.vo.PromptTemplateVo;
import org.ruoyi.service.IChatModelService;
import org.ruoyi.service.IChatSessionService;
import org.ruoyi.service.IKnowledgeInfoService;
import org.ruoyi.service.IPromptTemplateService;
import org.ruoyi.service.VectorStoreService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * @author ageer
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SseServiceImpl implements ISseService {

    private final OpenAiStreamClient openAiStreamClient;

    private final VectorStoreService vectorStoreService;

    private final IChatCostService chatCostService;

    private final IChatModelService chatModelService;

    private final ChatServiceFactory chatServiceFactory;

    private final IChatSessionService chatSessionService;

    private final IKnowledgeInfoService knowledgeInfoService;

    private ChatModelVo chatModelVo;

    // 提示词模板服务
    private final IPromptTemplateService promptTemplateService;


    @Override
    public SseEmitter sseChat(ChatRequest chatRequest, HttpServletRequest request) {
        SseEmitter sseEmitter = new SseEmitter(0L);
        try {
            // 记录当前会话令牌，供异步线程使用
            try {
                chatRequest.setToken(StpUtil.getTokenValue());
            } catch (Exception ignore) {
                // 保底：无token场景下忽略
            }
            // 构建消息列表
            buildChatMessageList(chatRequest);
            // 设置对话角色
            chatRequest.setRole(Message.Role.USER.getName());

            if (LoginHelper.isLogin()) {

                // 设置用户id
                chatRequest.setUserId(LoginHelper.getUserId());


                //待优化的地方 （这里请前端提交send的时候传递uuid进来或者sessionId）
                //待优化的地方 （这里请前端提交send的时候传递uuid进来或者sessionId）
                //待优化的地方 （这里请前端提交send的时候传递uuid进来或者sessionId）
                {
                    // 设置会话id
                    if (chatRequest.getUuid() == null) {
                        //暂时随机生成会话id
                        chatRequest.setSessionId(System.currentTimeMillis());
                    } else {
                        //这里或许需要修改一下，这里应该用uuid 或者 前端传递 sessionId
                        chatRequest.setSessionId(chatRequest.getUuid());
                    }
                }



                chatRequest.setUserId(chatCostService.getUserId());
                if (chatRequest.getSessionId() == null) {
                    ChatSessionBo chatSessionBo = new ChatSessionBo();
                    chatSessionBo.setUserId(chatCostService.getUserId());
                    chatSessionBo.setSessionTitle(getFirst10Characters(chatRequest.getPrompt()));
                    chatSessionBo.setSessionContent(chatRequest.getPrompt());
                    chatSessionService.insertByBo(chatSessionBo);
                    chatRequest.setSessionId(chatSessionBo.getId());
                }
                
                // 保存用户消息
                chatCostService.saveMessage(chatRequest);
            }
            // 自动选择模型并获取对应的聊天服务
            IChatService chatService = autoSelectModelAndGetService(chatRequest);

            // 用户消息只保存不计费，AI回复由BillingChatServiceProxy自动处理计费
            // chatCostService.publishBillingEvent(chatRequest); // 用户输入不计费
            if (Boolean.TRUE.equals(chatRequest.getAutoSelectModel())) {
                ChatModelVo currentModel = this.chatModelVo;
                String currentCategory = currentModel.getCategory();
                ChatRetryHelper.executeWithRetry(
                        currentModel,
                        currentCategory,
                        chatModelService,
                        sseEmitter,
                        (modelForTry, onFailure) -> {
                            // 替换请求中的模型名称
                            chatRequest.setModel(modelForTry.getModelName());
                            // 以 emitter 实例为唯一键注册失败回调
                            RetryNotifier.setFailureCallback(sseEmitter, onFailure);
                            try {
                                autoSelectServiceByCategoryAndInvoke(chatRequest, sseEmitter,
                                        modelForTry.getCategory());
                            } finally {
                                // 不在此处清理，待下游结束/失败时清理
                            }
                        }
                );
            } else {
                // 不重试不降级，直接调用
                chatService.chat(chatRequest, sseEmitter);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            SSEUtil.sendErrorEvent(sseEmitter, e.getMessage());
        }
        return sseEmitter;
    }

    /**
     * 自动选择模型并获取对应的聊天服务
     */
    private IChatService autoSelectModelAndGetService(ChatRequest chatRequest) {
        try {
            if (Boolean.TRUE.equals(chatRequest.getHasAttachment())) {
                chatModelVo = selectModelByCategory("image");
            } else if (Boolean.TRUE.equals(chatRequest.getAutoSelectModel())) {
                chatModelVo = selectModelByCategory("chat");
            } else {
                chatModelVo = chatModelService.selectModelByName(chatRequest.getModel());
            }

            if (chatModelVo == null) {
                throw new IllegalStateException("未找到模型名称：" + chatRequest.getModel());
            }
            // 自动设置请求参数中的模型名称
            chatRequest.setModel(chatModelVo.getModelName());
            // 直接返回对应的聊天服务
            return chatServiceFactory.getChatService(chatModelVo.getCategory());
        } catch (Exception e) {
            log.error("模型选择和服务获取失败: {}", e.getMessage(), e);
            throw new IllegalStateException("模型选择和服务获取失败: " + e.getMessage());
        }
    }

    /**
     * 根据给定分类获取服务并发起调用（避免在降级时重复选择模型）
     */
    private void autoSelectServiceByCategoryAndInvoke(ChatRequest chatRequest, SseEmitter sseEmitter, String category) {
        IChatService service = chatServiceFactory.getChatService(category);
        service.chat(chatRequest, sseEmitter);
    }

    /**
     * 根据分类选择优先级最高的模型
     */
    private ChatModelVo selectModelByCategory(String category) {
        ChatModelVo model = chatModelService.selectModelByCategoryWithHighestPriority(category);
        if (model == null) {
            throw new IllegalStateException("未找到" + category + "分类的模型配置");
        }
        return model;
    }

    /**
     * 获取对话标题
     *
     * @param str 原字符
     * @return 截取后的字符
     */
    public static String getFirst10Characters(String str) {
        // 判断字符串长度
        if (str.length() > 10) {
            // 如果长度大于10，截取前10个字符
            return str.substring(0, 10);
        } else {
            // 如果长度不足10，返回整个字符串
            return str;
        }
    }

    /**
     * 构建消息列表
     */
    private void buildChatMessageList(ChatRequest chatRequest) {
        List<Message> messages = chatRequest.getMessages();

        // 处理知识库相关逻辑
        String sysPrompt = processKnowledgeBase(chatRequest, messages);

        // 设置系统提示词
        Message sysMessage = Message.builder()
                .content(sysPrompt)
                .role(Message.Role.SYSTEM)
                .build();
        messages.add(0, sysMessage);

        chatRequest.setSysPrompt(sysPrompt);

        // 用户对话内容
        String chatString = null;
        // 获取用户对话信息
        Object content = messages.get(messages.size() - 1).getContent();
        if (content instanceof List<?> listContent) {
            if (CollectionUtil.isNotEmpty(listContent)) {
                chatString = listContent.get(0).toString();
            }
        } else {
            chatString = content.toString();
        }
        chatRequest.setPrompt(chatString);
    }

    /**
     * 处理知识库相关逻辑
     */
    private String processKnowledgeBase(ChatRequest chatRequest, List<Message> messages) {
        // 记录用户消息
        String userMessage = chatRequest.getMessages().get(chatRequest.getMessages().size() - 1).getContent().toString();
        log.info("💬 用户消息: {}", userMessage);
        
        if (StringUtils.isEmpty(chatRequest.getKid())) {
            log.warn("⚠️ 知识库ID为空，跳过RAG检索");
            return getPromptTemplatePrompt(promptTemplateEnum.VECTOR.getDesc());
        }
        
        log.info("🔍 开始RAG检索，知识库ID: {}", chatRequest.getKid());

        try {
            // 查询知识库信息
            KnowledgeInfoVo knowledgeInfoVo = knowledgeInfoService.queryById(Long.valueOf(chatRequest.getKid()));
            if (knowledgeInfoVo == null) {
                log.warn("知识库信息不存在，kid: {}", chatRequest.getKid());
                return getPromptTemplatePrompt(promptTemplateEnum.VECTOR.getDesc());
            }

            // 查询向量模型配置信息
            ChatModelVo chatModel = chatModelService.selectModelByName(knowledgeInfoVo.getEmbeddingModelName());
            if (chatModel == null) {
                log.warn("向量模型配置不存在，模型名称: {}", knowledgeInfoVo.getEmbeddingModelName());
                return getPromptTemplatePrompt(promptTemplateEnum.VECTOR.getDesc());
            }

            // 构建向量查询参数
            QueryVectorBo queryVectorBo = buildQueryVectorBo(chatRequest, knowledgeInfoVo, chatModel);
            log.info("🔍 向量搜索参数 - 知识库: {}, 模型: {}, 检索数量: {}", 
                    queryVectorBo.getKid(), queryVectorBo.getEmbeddingModelName(), queryVectorBo.getMaxResults());

            // 获取向量查询结果
            List<String> nearestList = vectorStoreService.getQueryVector(queryVectorBo);
            log.info("📊 RAG检索结果 - 找到 {} 条相关文档", nearestList.size());
            
            // 记录检索到的文档内容（前100字符）
            for (int i = 0; i < nearestList.size(); i++) {
                String content = nearestList.get(i);
                String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                log.info("📄 检索文档[{}]: {}", i + 1, preview);
            }

            // 添加知识库消息到上下文
            addKnowledgeMessages(messages, nearestList);

            // 返回知识库系统提示词
            return getKnowledgeSystemPrompt(knowledgeInfoVo);

        } catch (Exception e) {
            log.error("❌ RAG处理失败: {}", e.getMessage(), e);
            return getPromptTemplatePrompt(promptTemplateEnum.VECTOR.getDesc());
        }
    }

    /**
     * 构建向量查询参数
     */
    private QueryVectorBo buildQueryVectorBo(ChatRequest chatRequest, KnowledgeInfoVo knowledgeInfoVo,
                                             ChatModelVo chatModel) {
        String content = chatRequest.getMessages().get(chatRequest.getMessages().size() - 1).getContent().toString();

        QueryVectorBo queryVectorBo = new QueryVectorBo();
        queryVectorBo.setQuery(content);
        queryVectorBo.setKid(chatRequest.getKid());
        queryVectorBo.setApiKey(chatModel.getApiKey());
        queryVectorBo.setBaseUrl(chatModel.getApiHost());
        queryVectorBo.setVectorModelName(knowledgeInfoVo.getVectorModelName());
        queryVectorBo.setEmbeddingModelName(knowledgeInfoVo.getEmbeddingModelName());
        queryVectorBo.setMaxResults(knowledgeInfoVo.getRetrieveLimit());

        return queryVectorBo;
    }

    /**
     * 添加知识库消息到上下文
     */
    private void addKnowledgeMessages(List<Message> messages, List<String> nearestList) {
        for (String prompt : nearestList) {
            Message userMessage = Message.builder()
                    .content(prompt)
                    .role(Message.Role.USER)
                    .build();
            messages.add(userMessage);
        }
    }

    /**
     * 获取知识库系统提示词
     */
    private String getKnowledgeSystemPrompt(KnowledgeInfoVo knowledgeInfoVo) {
        String sysPrompt = knowledgeInfoVo.getSystemPrompt();
        if (StringUtils.isEmpty(sysPrompt)) {
            sysPrompt = "###角色设定\n" +
                    "你是一个智能知识助手，专注于利用上下文中的信息来提供准确和相关的回答。\n" +
                    "###指令\n" +
                    "当用户的问题与上下文知识匹配时，利用上下文信息进行回答。如果问题与上下文不匹配，运用自身的推理能力生成合适的回答。\n" +
                    "###限制\n" +
                    "确保回答清晰简洁，避免提供不必要的细节。始终保持语气友好\n" +
                    "当前时间：" + DateUtils.getDate();
        }
        return sysPrompt;
    }


    /**
     * 获取提示词模板提示词
     */
    private String getPromptTemplatePrompt(String category) {
        PromptTemplateVo promptTemplateVo = promptTemplateService.queryByCategory(category);
        if (Objects.isNull(promptTemplateVo) || StringUtils.isEmpty(promptTemplateVo.getTemplateContent())) {
            return getDefaultSystemPrompt();
        }
        return promptTemplateVo.getTemplateContent();
    }

    /**
     * 获取默认系统提示词
     */
    private String getDefaultSystemPrompt() {
        String sysPrompt = chatModelVo != null ? chatModelVo.getSystemPrompt() : null;
        if (StringUtils.isEmpty(sysPrompt)) {
            sysPrompt = "你是一个由RuoYI-AI开发的人工智能助手，名字叫RuoYI人工智能助手。"
                    + "你擅长中英文对话，能够理解并处理各种问题，提供安全、有帮助、准确的回答。"
                    + "当前时间：" + DateUtils.getDate()
                    + "#注意：回复之前注意结合上下文和工具返回内容进行回复。";
        }
        return sysPrompt;
    }


    /**
     * 文字转语音
     */
    @Override
    public ResponseEntity<Resource> textToSpeed(TextToSpeech textToSpeech) {
        ResponseBody body = openAiStreamClient.textToSpeech(textToSpeech);
        if (body != null) {
            // 将ResponseBody转换为InputStreamResource
            InputStreamResource resource = new InputStreamResource(body.byteStream());
            // 创建并返回ResponseEntity
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(resource);
        } else {
            // 如果ResponseBody为空，返回404状态码
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 语音转文字
     */
    @Override
    public WhisperResponse speechToTextTranscriptionsV2(MultipartFile file) {
        // 确保文件不为空
        if (file.isEmpty()) {
            throw new IllegalStateException("Cannot convert an empty MultipartFile");
        }
        if (!FileUtils.isValidFileExtention(file, MimeTypeUtils.AUDIO__EXTENSION)) {
            throw new IllegalStateException("File Extention not supported");
        }
        // 创建一个文件对象
        File fileA = new File(System.getProperty("java.io.tmpdir") + File.separator + file.getOriginalFilename());
        try {
            // 将 MultipartFile 的内容写入文件
            file.transferTo(fileA);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert MultipartFile to File", e);
        }
        return openAiStreamClient.speechToTextTranscriptions(fileA);
    }


    @Override
    public UploadFileResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalStateException("Cannot upload an empty MultipartFile");
        }
        if (!FileUtils.isValidFileExtention(file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION)) {
            throw new IllegalStateException("File Extention not supported");
        }
        return openAiStreamClient.uploadFile("fine-tune", convertMultiPartToFile(file));
    }

    private File convertMultiPartToFile(MultipartFile multipartFile) {
        File file = null;
        try {
            // 获取原始文件名
            String originalFileName = multipartFile.getOriginalFilename();
            // 默认扩展名
            String extension = ".tmp";
            // 尝试从原始文件名中获取扩展名
            if (originalFileName != null && originalFileName.contains(".")) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }

            // 使用原始文件的扩展名创建临时文件
            Path tempFile = Files.createTempFile(null, extension);
            file = tempFile.toFile();

            // 将MultipartFile的内容写入文件
            try (InputStream inputStream = multipartFile.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(file)) {
                int read;
                byte[] bytes = new byte[1024];
                while ((read = inputStream.read(bytes)) != -1) {
                    outputStream.write(bytes, 0, read);
                }
            } catch (IOException e) {
                // 处理文件写入异常
                e.printStackTrace();
            }
        } catch (IOException e) {
            // 处理临时文件创建异常
            e.printStackTrace();
        }
        return file;
    }

}
