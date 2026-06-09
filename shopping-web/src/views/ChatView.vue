<template>
  <div class="chat-layout">
    <header class="chat-header">
      <div>
        <h1>电商智能导购</h1>
        <p v-if="user">你好，{{ user.displayName || user.username }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="newSession">新会话</el-button>
        <el-button type="danger" plain @click="onLogout">退出</el-button>
      </div>
    </header>

    <main class="chat-main" ref="logRef">
      <div v-if="messages.length === 0 && !streaming" class="empty-tip">
        告诉我你想买的品类、预算和使用场景，例如：想买降噪耳机，预算2000左右，用来学习。
      </div>
      <div
        v-for="(item, index) in messages"
        :key="index"
        :class="['bubble', item.role]"
      >
        <div class="role-label">{{ item.role === 'user' ? '你' : '助手' }}</div>
        <div class="content">{{ item.content }}</div>
      </div>
      <div v-if="streaming" class="bubble assistant">
        <div class="role-label">助手</div>
        <div class="content">{{ streamingText }}<span class="cursor">▍</span></div>
      </div>
    </main>

    <footer class="chat-footer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        placeholder="输入你的购买需求..."
        :disabled="sending"
        @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
    </footer>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchMe, logout } from '../api/http'

const router = useRouter()
const user = ref(null)
const input = ref('')
const messages = ref([])
const sessionId = ref(localStorage.getItem('chatSessionId') || '')
const sending = ref(false)
const streaming = ref(false)
const streamingText = ref('')
const logRef = ref(null)

onMounted(async () => {
  try {
    user.value = await fetchMe()
  } catch {
    await router.replace('/login')
  }
})

function newSession() {
  sessionId.value = crypto.randomUUID()
  localStorage.setItem('chatSessionId', sessionId.value)
  messages.value = []
}

async function onLogout() {
  try {
    await logout()
  } finally {
    localStorage.removeItem('chatSessionId')
    await router.replace('/login')
  }
}

function parseSseEvents(buffer, flush = false) {
  const events = []
  const text = buffer.replace(/\r\n/g, '\n')
  const parts = text.split('\n\n')
  const rest = flush ? '' : (parts.pop() ?? '')

  for (const part of parts) {
    for (const line of part.split('\n')) {
      if (!line.startsWith('data:')) continue
      const payload = line.slice(5).trim()
      if (!payload) continue
      try {
        events.push(JSON.parse(payload))
      } catch {
        /* ignore */
      }
    }
  }

  if (flush && rest.trim()) {
    for (const line of rest.split('\n')) {
      if (!line.startsWith('data:')) continue
      const payload = line.slice(5).trim()
      if (!payload) continue
      try {
        events.push(JSON.parse(payload))
      } catch {
        /* ignore */
      }
    }
  }

  return { events, rest }
}

function consumeSseBuffer(buffer, state) {
  const { events, rest } = parseSseEvents(buffer)
  for (const event of events) {
    applySseEvent(event, state)
  }
  return rest
}

function flushSseBuffer(buffer, state) {
  const { events } = parseSseEvents(buffer, true)
  for (const event of events) {
    applySseEvent(event, state)
  }
}

function applySseEvent(event, state) {
  if (event.type === 'session' && event.sessionId) {
    sessionId.value = event.sessionId
    localStorage.setItem('chatSessionId', event.sessionId)
  }
  if (event.type === 'delta' && event.content) {
    streamingText.value += event.content
    state.finalReply = streamingText.value
  }
  if (event.type === 'done') {
    if (event.sessionId) {
      sessionId.value = event.sessionId
      localStorage.setItem('chatSessionId', event.sessionId)
    }
    if (event.reply) {
      state.finalReply = event.reply
    }
    state.finished = true
  }
  if (event.type === 'error') {
    throw new Error(event.message || 'stream error')
  }
}

async function scrollToBottom() {
  await nextTick()
  if (logRef.value) {
    logRef.value.scrollTop = logRef.value.scrollHeight
  }
}

async function send() {
  const message = input.value.trim()
  if (!message || sending.value) return

  messages.value.push({ role: 'user', content: message })
  input.value = ''
  sending.value = true
  streaming.value = true
  streamingText.value = ''

  const state = { finalReply: '', finished: false }
  let reader = null
  const abortController = new AbortController()

  try {
    await scrollToBottom()

    const resp = await fetch('/api/v1/chat', {
      method: 'POST',
      credentials: 'include',
      signal: abortController.signal,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      },
      body: JSON.stringify({
        sessionId: sessionId.value || null,
        message
      })
    })

    if (resp.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      await router.replace('/login')
      return
    }
    if (!resp.ok) {
      throw new Error('HTTP ' + resp.status)
    }

    reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()

      if (value) {
        buffer += decoder.decode(value, { stream: true })
        buffer = consumeSseBuffer(buffer, state)
        await scrollToBottom()
      }

      if (state.finished) {
        flushSseBuffer(buffer, state)
        break
      }

      if (done) {
        buffer += decoder.decode()
        flushSseBuffer(buffer, state)
        break
      }
    }

    if (state.finalReply) {
      messages.value.push({ role: 'assistant', content: state.finalReply })
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      ElMessage.error(e.message || '发送失败')
    }
  } finally {
    if (reader) {
      try {
        await reader.cancel()
      } catch {
        /* ignore */
      }
    }
    abortController.abort()
    streaming.value = false
    streamingText.value = ''
    sending.value = false
    await scrollToBottom()
  }
}
</script>

<style scoped>
.chat-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
  box-sizing: border-box;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.chat-header h1 {
  margin: 0;
  font-size: 22px;
}
.chat-header p {
  margin: 4px 0 0;
  color: #909399;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.chat-main {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 16px;
  min-height: 420px;
}
.empty-tip {
  color: #909399;
  text-align: center;
  margin-top: 80px;
}
.bubble {
  margin-bottom: 16px;
}
.bubble.user .content {
  background: #ecf5ff;
}
.bubble.assistant .content {
  background: #f4f4f5;
}
.role-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.content {
  white-space: pre-wrap;
  line-height: 1.6;
  padding: 12px 14px;
  border-radius: 10px;
}
.cursor {
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.chat-footer {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.chat-footer .el-textarea {
  flex: 1;
}
</style>
