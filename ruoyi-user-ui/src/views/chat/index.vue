<script setup lang='ts'>
import type { Ref } from "vue"
import { computed, onMounted, onUnmounted, ref, watch} from "vue"
import { useRoute } from "vue-router"
import { storeToRefs } from "pinia"
import {
  NAutoComplete,
  NButton,
  NInput,
  useDialog,
  useMessage
} from "naive-ui"
import html2canvas from "html2canvas"
import { Message } from "./components"
import { useScroll } from "./hooks/useScroll"
import { useChat } from "./hooks/useChat"
import { SvgIcon } from "@/components/common"
import { useBasicLayout } from "@/hooks/useBasicLayout"
import {
  gptConfigStore,
  gptsUlistStore,
  homeStore,
  useChatStore,
  usePromptStore
} from "@/store"
import {
  fetchChatAPIProcess,
  gptsType,
  mlog,
} from "@/api"
import { t } from "@/locales"
import drawListVue from "../mj/drawList.vue"
import aiGPT from "../mj/aiGpt.vue"
import AiSiderInput from "../mj/aiSiderInput.vue"
import aiGptInput from "../mj/aiGptInput.vue"

let controller = new AbortController()

const openLongReply = import.meta.env.VITE_GLOB_OPEN_LONG_REPLY === "true"

const route = useRoute()
const dialog = useDialog()
const ms = useMessage()

const chatStore = useChatStore()

const { isMobile } = useBasicLayout()
const {  updateChat, updateChatSome} = useChat()
const { scrollRef, scrollToBottom, scrollToBottomIfAtBottom } = useScroll()

const { uuid } = route.params as { uuid: string }

const dataSources = computed(() => chatStore.getChatByUuid(+uuid))

const prompt = ref<string>("")
const loading = ref<boolean>(false)
const inputRef = ref<Ref | null>(null)

// 添加PromptStore
const promptStore = usePromptStore()

// 使用storeToRefs，保证store修改后，联想部分能够重新渲染
const { promptList: promptTemplate } = storeToRefs<any>(promptStore)

// 未知原因刷新页面，loading 状态不会重置，手动重置
dataSources.value.forEach((item, index) => {
  if (item.loading) updateChatSome(+uuid, index, { loading: false })
})

function handleSubmit() {
  //onConversation() //把这个放到aiGpt
  let message = prompt.value
  if (!message || message.trim() === "") return
  if (loading.value) return
  loading.value = true
  homeStore.setMyData({
    act: "gpt.submit",
    actData: { prompt: prompt.value, uuid },
  })
  prompt.value = ""
}

async function onRegenerate(index: number) {
  if (loading.value) return

  controller = new AbortController()

  const { requestOptions } = dataSources.value[index]

  let message = requestOptions?.prompt ?? ""

  let options: Chat.ConversationRequest = {}

  if (requestOptions.options) options = { ...requestOptions.options }

  loading.value = true

  updateChat(+uuid, index, {
    dateTime: new Date().toLocaleString(),
    text: "",
    inversion: false,
    error: false,
    loading: true,
    conversationOptions: null,
    requestOptions: { prompt: message, options: { ...options } },
  })

  try {
    let lastText = ""
    const fetchChatAPIOnce = async () => {
      await fetchChatAPIProcess<Chat.ConversationResponse>({
        prompt: message,
        options,
        signal: controller.signal,
        onDownloadProgress: ({ event }) => {
          const xhr = event.target
          const { responseText } = xhr
          // Always process the final line
          const lastIndex = responseText.lastIndexOf(
            "\n",
            responseText.length - 2
          )
          let chunk = responseText
          if (lastIndex !== -1) chunk = responseText.substring(lastIndex)
          try {
            const data = JSON.parse(chunk)
            updateChat(+uuid, index, {
              dateTime: new Date().toLocaleString(),
              text: lastText + (data.text ?? ""),
              inversion: false,
              error: false,
              loading: true,
              conversationOptions: {
                conversationId: data.conversationId,
                parentMessageId: data.id,
              },
              requestOptions: { prompt: message, options: { ...options } },
            })

            if (
              openLongReply &&
              data.detail.choices[0].finish_reason === "length"
            ) {
              options.parentMessageId = data.id
              lastText = data.text
              message = ""
              return fetchChatAPIOnce()
            }
          } catch (error) {
            //
          }
        },
      })
      updateChatSome(+uuid, index, { loading: false })
    }
    await fetchChatAPIOnce()
  } catch (error: any) {
    if (error.message === "canceled") {
      updateChatSome(+uuid, index, {
        loading: false,
      })
      return
    }

    const errorMessage = error?.message ?? t("common.wrong")

    updateChat(+uuid, index, {
      dateTime: new Date().toLocaleString(),
      text: errorMessage,
      inversion: false,
      error: true,
      loading: false,
      conversationOptions: null,
      requestOptions: { prompt: message, options: { ...options } },
    })
  } finally {
    loading.value = false
  }
}

function handleExport() {
  if (loading.value) return

  const d = dialog.warning({
    title: t("chat.exportImage"),
    content: t("chat.exportImageConfirm"),
    positiveText: t("common.yes"),
    negativeText: t("common.no"),
    onPositiveClick: async () => {
      try {
        d.loading = true
        const ele = document.getElementById("image-wrapper")
        const canvas = await html2canvas(ele as HTMLDivElement, {
          useCORS: true,
        })
        const imgUrl = canvas.toDataURL("image/png")
        const tempLink = document.createElement("a")
        tempLink.style.display = "none"
        tempLink.href = imgUrl
        tempLink.setAttribute("download", "chat-shot.png")
        if (typeof tempLink.download === "undefined")
          tempLink.setAttribute("target", "_blank")

        document.body.appendChild(tempLink)
        tempLink.click()
        document.body.removeChild(tempLink)
        window.URL.revokeObjectURL(imgUrl)
        d.loading = false
        ms.success(t("chat.exportSuccess"))
        Promise.resolve()
      } catch (error: any) {
        ms.error(t("chat.exportFailed"))
      } finally {
        d.loading = false
      }
    },
  })
}

function handleDelete(index: number) {
  if (loading.value) return

  dialog.warning({
    title: t("chat.deleteMessage"),
    content: t("chat.deleteMessageConfirm"),
    positiveText: t("common.yes"),
    negativeText: t("common.no"),
    onPositiveClick: () => {
      chatStore.deleteChatByUuid(+uuid, index)
    },
  })
}

function handleClear() {
  if (loading.value) return

  dialog.warning({
    title: t("chat.clearChat"),
    content: t("chat.clearChatConfirm"),
    positiveText: t("common.yes"),
    negativeText: t("common.no"),
    onPositiveClick: () => {
      chatStore.clearChatByUuid(+uuid)
    },
  })
}

function handleEnter(event: KeyboardEvent) {
  if (!isMobile.value) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      handleSubmit()
    }
  } else {
    if (event.key === "Enter" && event.ctrlKey) {
      event.preventDefault()
      handleSubmit()
    }
  }
}

function handleStop() {
  if (loading.value) {
    homeStore.setMyData({ act: "abort" })
    controller.abort()
    loading.value = false
  }
}

// 可优化部分
// 搜索选项计算，这里使用value作为索引项，所以当出现重复value时渲染异常(多项同时出现选中效果)
// 理想状态下其实应该是key作为索引项,但官方的renderOption会出现问题，所以就需要value反renderLabel实现
const searchOptions = computed(() => {
  if (prompt.value.startsWith("/")) {
    const abc = promptTemplate.value
      .filter((item: { key: string }) =>
        item.key.toLowerCase().includes(prompt.value.substring(1).toLowerCase())
      )
      .map((obj: { value: any }) => {
        return {
          label: obj.value,
          value: obj.value,
        }
      })
    mlog("搜索选项", abc)
    return abc
  } else if (prompt.value == "@") {
    const abc = gptsUlistStore.myData.slice(0, 10).map((v: gptsType) => {
      return {
        label: v.info,
        gpts: v,
        value: v.gid,
      }
    })
    return abc
  } else {
    return []
  }
})


const placeholder = computed(() => {
  if (isMobile.value) return t("chat.placeholderMobile")
  return t("chat.placeholder")
})

const buttonDisabled = computed(() => {
  return loading.value || !prompt.value || prompt.value.trim() === ""
})

const footerClass = computed(() => {
  let classes = ["p-4"]
  if (isMobile.value)
    classes = ["sticky", "left-0", "bottom-0", "right-0", "p-2", "pr-3"] //, 'overflow-hidden'
  return classes
})

onMounted(() => {
  scrollToBottom()
  if (inputRef.value && !isMobile.value) inputRef.value?.focus()
})

onUnmounted(() => {
  if (loading.value) controller.abort()
  homeStore.setMyData({ isLoader: false })
})

const local = computed(() => homeStore.myData.local)
watch(
  () => homeStore.myData.act,
  (n) => {
    if (n == "draw") scrollToBottom()
    if (n == "scrollToBottom") scrollToBottom()
    if (n == "scrollToBottomIfAtBottom") scrollToBottomIfAtBottom()
    if (n == "gpt.submit" || n == "gpt.resubmit") {
      loading.value = true
    }
    if (n == "stopLoading") {
      loading.value = false
    }
  }
)
const st = ref({ inputme: true })

watch(
  () => loading.value,
  (n) => homeStore.setMyData({ isLoader: n })
)

const ychat = computed(() => {
  let text = prompt.value
  if (loading.value) text = ""
  else {
    scrollToBottomIfAtBottom()
  }
  return { text, dateTime: t("chat.preview") } as Chat.Chat
})

// 处理示例问题点击
defineExpose({
  handleExampleClick
})

function handleExampleClick(example: string) {
  prompt.value = example
  // 聚焦到输入框
  if (inputRef.value) {
    inputRef.value?.focus()
  }
}

</script>

<template>


  <div class="flex flex-col w-full h-full chat-content" :class="[isMobile ? '' : 'chat-content-noMobile']">

    <main class="flex-1 overflow-hidden">

      <div id="scrollRef" ref="scrollRef" class="h-full overflow-hidden overflow-y-auto">

        <div id="image-wrapper" class="w-full max-w-[1100px] m-auto dark:bg-[#101014]"
          :class="[isMobile ? 'p-2' : 'p-4']">
          <template v-if="!dataSources.length">
            <div v-if="homeStore.myData.session.notify" v-html="homeStore.myData.session.notify"
              class="text-neutral-300 mt-4">

            </div>

            <div class="welcome-container" v-else>
              <div class="welcome-card">
                <div class="welcome-header">
                  <div class="ai-icon animated-pulse">
                    <IconSvg icon="chatGPT" :width="isMobile ? '32px' : '64px'" :height="isMobile ? '32px' : '64px'"></IconSvg>
                  </div>
                  <h2 class="welcome-title">{{ t('chat.helpTitle') }}</h2>
                </div>
                
                <div class="welcome-content">
                  <div class="welcome-description">
                    👨‍⚕️ 您好！我是您的家庭用药科普助手，专业为您解答各类用药问题，提供科学、安全的用药指导。
                  </div>
                  
                  <div class="welcome-features">
                    <div class="feature-item">
                      <SvgIcon icon="ri:check-circle-line" class="feature-icon" size="16" />
                      <span>药品使用说明与注意事项</span>
                    </div>
                    <div class="feature-item">
                      <SvgIcon icon="ri:check-circle-line" class="feature-icon" size="16" />
                      <span>药物相互作用查询</span>
                    </div>
                    <div class="feature-item">
                      <SvgIcon icon="ri:check-circle-line" class="feature-icon" size="16" />
                      <span>儿童、老人特殊用药指导</span>
                    </div>
                    <div class="feature-item">
                      <SvgIcon icon="ri:check-circle-line" class="feature-icon" size="16" />
                      <span>常见疾病用药建议</span>
                    </div>
                  </div>
                  
                  <div class="welcome-examples">
                    <p class="examples-title">💡 您可以尝试这样提问：</p>
                    <div class="examples-grid">
                      <div class="example-item" v-for="(example, idx) in [
                        '退烧药和感冒药可以一起吃吗？', 
                        '儿童退烧药的正确使用方法',
                        '高血压患者用药注意事项',
                        '抗生素的使用误区有哪些？'
                      ]" :key="idx" @click="handleExampleClick(example)">
                        {{ example }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <template v-else>
            <div>
              <Message v-for="(item, index) of dataSources" :key="index" :date-time="item.dateTime" :text="item.text"
                :inversion="item.inversion" :error="item.error" :loading="item.loading"
                @regenerate="onRegenerate(index)" @delete="handleDelete(index)" :chat="item" :index="index" />
              <Message v-if="ychat.text && !homeStore.myData.session.isCloseMdPreview" :key="dataSources.length"
                :inversion="true" :date-time="$t('mj.typing')" :chat="ychat" :text="ychat.text"
                :index="dataSources.length" />
              <div class="sticky bottom-0 left-0 flex justify-center">
                <NButton v-if="loading" type="warning" @click="handleStop">
                  <template #icon>
                    <SvgIcon icon="ri:stop-circle-line" />
                  </template>
                  {{ t('common.stopResponding') }}
                </NButton>
              </div>
            </div>
          </template>
        </div>
      </div>
    </main>

    <footer :class="footerClass" class="footer-content" v-if="local !== 'draw'">
      <!-- max-w-screen-xl -->
      <div class="w-full max-w-[1100px] m-auto">
        <aiGptInput @handle-clear="handleClear" @export="handleExport"
          v-if="['gpt-4o-mini', 'gpt-3.5-turbo-16k'].indexOf(gptConfigStore.myData.model) > -1 || st.inputme"
          v-model:modelValue="prompt" :disabled="buttonDisabled" :searchOptions="searchOptions" />
        <div class="flex items-center justify-between space-x-2" v-else>
          <NAutoComplete v-model:value="prompt" :options="searchOptions">
            <template #default="{ handleInput, handleBlur, handleFocus }">
              <NInput ref="inputRef" v-model:value="prompt" type="textarea" :placeholder="placeholder"
                :autosize="{ minRows: 1, maxRows: isMobile ? 4 : 8 }" @input="handleInput" @focus="handleFocus"
                @blur="handleBlur" @keypress="handleEnter" />
            </template>
          </NAutoComplete>
          <NButton type="primary" :disabled="buttonDisabled" @click="handleSubmit">
            <template #icon>
              <span class="dark:text-black">
                <SvgIcon icon="ri:send-plane-fill" />
              </span>
            </template>
          </NButton>

        </div>
      </div>
    </footer>
  </div>

  <drawListVue />
  <aiGPT @finished="loading = false" />
  <AiSiderInput v-if="isMobile" :button-disabled="false" />

</template>

<style>
.new-chat-header {
  width: 100%;
  padding: 0 24px;
  height: 70px;
  line-height: 70px;
  max-width: 300px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 500;
}

/* 聊天内容区域样式优化 */
.chat-content {
  background-color: #f9fafb;
  background-image: 
    radial-gradient(#e5e7eb 0.5px, transparent 0.5px),
    radial-gradient(#e5e7eb 0.5px, #f9fafb 0.5px);
  background-size: 20px 20px;
  background-position: 0 0, 10px 10px;
  transition: all 0.3s ease;
}

.dark .chat-content {
  background-color: #111827;
  background-image: 
    radial-gradient(#374151 0.5px, transparent 0.5px),
    radial-gradient(#374151 0.5px, #111827 0.5px);
  background-size: 20px 20px;
  background-position: 0 0, 10px 10px;
}

/* 输入框区域样式优化 */
.footer-content {
  backdrop-filter: blur(8px);
  background-color: rgba(255, 255, 255, 0.8);
  border-top: 1px solid #e5e7eb;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.dark .footer-content {
  background-color: rgba(17, 24, 39, 0.8);
  border-top: 1px solid #374151;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.3);
}

/* 按钮样式优化 */
.n-button {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.n-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.n-button:active {
  transform: translateY(0);
}

/* 输入框样式优化 */
.n-input-wrapper {
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  transition: all 0.3s ease;
}

.n-input-wrapper:hover {
  border-color: #d1d5db !important;
}

.n-input-wrapper:focus-within {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1) !important;
}

.dark .n-input-wrapper {
  border-color: #374151 !important;
}

.dark .n-input-wrapper:hover {
  border-color: #4b5563 !important;
}

.dark .n-input-wrapper:focus-within {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2) !important;
}

/* 已读标记样式 */
.read-indicator {
  width: 8px;
  height: 8px;
  background-color: #3b82f6;
  border-radius: 50%;
  margin-left: auto;
  margin-right: 8px;
  margin-bottom: 8px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(59, 130, 246, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0);
  }
}

/* 主题切换动画 */
.theme-transition {
  transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
}

/* 全局聊天文本样式 */
#scrollRef {
  font-size: 1.2rem;
}

/* 输入框文本样式 */
.n-input {
  font-size: 1.2rem !important;
}

/* 欢迎界面样式 */
.welcome-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  padding: 2rem;
}

.welcome-card {
  background: white;
  border-radius: 16px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.05);
  width: 100%;
  max-width: 600px;
  overflow: hidden;
  transition: all 0.3s ease;
}

.dark .welcome-card {
  background: #1f2937;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
}

.welcome-header {
  text-align: center;
  padding: 2rem;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
}

.welcome-title {
  font-size: 2rem; /* 进一步增大标题字体 */
  font-weight: 600;
  margin-top: 1rem;
}

.welcome-content {
  padding: 2rem;
}

.welcome-description {
  text-align: center;
  color: #6b7280;
  margin-bottom: 2rem;
  line-height: 1.6;
  font-size: 1.25rem; /* 进一步增大描述字体 */
}

.dark .welcome-description {
  color: #d1d5db;
}

.welcome-features {
  margin-bottom: 2rem;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 0;
  color: #4b5563;
  font-size: 1.2rem; /* 进一步增大功能列表字体 */
}

.dark .feature-item {
  color: #9ca3af;
}

.feature-icon {
  color: #10b981;
  flex-shrink: 0;
}

.welcome-examples {
  background: #f9fafb;
  border-radius: 12px;
  padding: 1.5rem;
}

.dark .welcome-examples {
  background: #111827;
}

.examples-title {
  font-weight: 600;
  color: #111827;
  margin-bottom: 1rem;
  font-size: 1.25rem; /* 进一步增大示例标题字体 */
}

.dark .examples-title {
  color: #f9fafb;
}

.examples-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 0.75rem;
}

.example-item {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 1.15rem; /* 进一步增大示例问题字体 */
  color: #374151;
}

.dark .example-item {
  background: #374151;
  border-color: #4b5563;
  color: #d1d5db;
}

.example-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #6366f1;
}

.dark .example-item:hover {
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

/* 动画效果 */
.animated-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

/* 响应式调整 */
@media (max-width: 640px) {
  .welcome-container {
    padding: 1rem;
  }
  
  .welcome-card {
    border-radius: 12px;
  }
  
  .welcome-header,
  .welcome-content {
    padding: 1.5rem;
  }
  
  .examples-grid {
    grid-template-columns: 1fr;
  }
}

</style>