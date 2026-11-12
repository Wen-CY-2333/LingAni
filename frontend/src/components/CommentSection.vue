<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CornerDownRight, Loader2, MessageCircle, Pencil, Send, Trash2, X } from 'lucide-vue-next'
import { createComment, deleteComment, getComments, updateComment } from '@/api/backend'
import { authState, isLoggedIn } from '@/stores/auth'
import { formatDateTime } from '@/utils/datetime'
import { showError, showSuccess } from '@/utils/toast'
import Pagination from '@/components/Pagination.vue'

const props = defineProps({
  videoId: { type: [Number, String], required: true }
})

const route = useRoute()

const comments = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 8
const loading = ref(true)
const loadError = ref('')

const content = ref('')
const posting = ref(false)

const replyTo = ref(0)
const replyContent = ref('')
const replying = ref(false)

const editingId = ref(0)
const editContent = ref('')
const savingEdit = ref(false)

const deletingId = ref(0)

const videoIdValue = computed(() => String(props.videoId ?? '').trim())
const loginRedirect = computed(() => route.fullPath)

function avatarText(item) {
  const name = item.nickname || item.username || '灵'
  return name.slice(0, 1).toUpperCase()
}

async function load(current = page.value) {
  if (!videoIdValue.value) {
    loadError.value = '动画标识无效'
    loading.value = false
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const data = await getComments({ videoId: videoIdValue.value, current, size: pageSize })
    comments.value = Array.isArray(data?.records) ? data.records : []
    total.value = Number(data?.total) || 0
    page.value = current
  } catch (error) {
    loadError.value = error.message || '评论加载失败'
  } finally {
    loading.value = false
  }
}

function requireLogin() {
  if (!isLoggedIn.value) {
    showError(new Error('请先登录后再发表评论'))
    return false
  }
  return true
}

async function submitComment() {
  if (!requireLogin()) return
  const text = content.value.trim()
  if (!text) {
    showError(new Error('评论内容不能为空'))
    return
  }
  if (text.length > 500) {
    showError(new Error('评论最多 500 个字符'))
    return
  }

  posting.value = true
  try {
    await createComment({ videoId: videoIdValue.value, content: text })
    content.value = ''
    showSuccess('评论已发表')
    await load(1)
  } catch (error) {
    showError(error, '评论发表失败')
  } finally {
    posting.value = false
  }
}

function openReply(item) {
  if (!requireLogin()) return
  replyTo.value = replyTo.value === item.id ? 0 : item.id
  replyContent.value = ''
}

async function submitReply(parent) {
  if (!requireLogin()) return
  const text = replyContent.value.trim()
  if (!text) {
    showError(new Error('回复内容不能为空'))
    return
  }

  replying.value = true
  try {
    await createComment({ videoId: videoIdValue.value, content: text, parentId: parent.id })
    replyContent.value = ''
    replyTo.value = 0
    showSuccess('回复已发表')
    await load(page.value)
  } catch (error) {
    showError(error, '回复失败')
  } finally {
    replying.value = false
  }
}

function openEdit(item) {
  editingId.value = item.id
  editContent.value = item.content || ''
}

function cancelEdit() {
  editingId.value = 0
  editContent.value = ''
}

async function saveEdit(item) {
  const text = editContent.value.trim()
  if (!text) {
    showError(new Error('评论内容不能为空'))
    return
  }
  savingEdit.value = true
  try {
    const updated = await updateComment(item.id, { content: text })
    item.content = updated.content
    item.updatedAt = updated.updatedAt
    cancelEdit()
    showSuccess('评论已更新')
  } catch (error) {
    showError(error, '评论更新失败')
  } finally {
    savingEdit.value = false
  }
}

async function removeComment(item, isReply = false) {
  if (!requireLogin()) return
  const tip = isReply ? '确定删除这条回复吗？' : '删除一级评论会同时删除其下回复，确定继续吗？'
  if (!window.confirm(tip)) return

  deletingId.value = item.id
  try {
    await deleteComment(item.id)
    showSuccess('评论已删除')
    const nextPage = comments.value.length === 1 && page.value > 1 ? page.value - 1 : page.value
    await load(nextPage)
  } catch (error) {
    showError(error, '评论删除失败')
  } finally {
    deletingId.value = 0
  }
}

function canManage(item) {
  return Boolean(isLoggedIn.value && authState.user?.id === item.userId)
}

watch(videoIdValue, () => {
  page.value = 1
  load(1)
})

onMounted(() => load(1))
</script>

<template>
  <section id="comments" class="comment-section surface">
    <div class="comment-head">
      <h2 class="section-title">
        <MessageCircle :size="18" /> 评论
        <span class="comment-count">{{ total }}</span>
      </h2>
      <p class="comment-hint">友善讨论，请勿发布广告或违规内容。</p>
    </div>

    <div v-if="isLoggedIn" class="composer">
      <div class="composer-avatar">{{ (authState.user?.nickname || authState.user?.username || '灵').slice(0, 1) }}</div>
      <div class="composer-body">
        <textarea
          v-model="content"
          rows="3"
          maxlength="500"
          placeholder="说说你对这部动画的看法…"
        ></textarea>
        <div class="composer-foot">
          <span class="char-count">{{ content.length }}/500</span>
          <button type="button" class="btn-brand" :disabled="posting || !content.trim()" @click="submitComment">
            <Loader2 v-if="posting" :size="14" class="spin" />
            <Send v-else :size="14" />
            {{ posting ? '发表中…' : '发表评论' }}
          </button>
        </div>
      </div>
    </div>

    <RouterLink v-else class="login-banner" :to="{ path: '/login', query: { redirect: loginRedirect } }">
      <MessageCircle :size="16" />
      <span>登录后即可发表评论和回复</span>
      <span class="login-banner__action">去登录</span>
    </RouterLink>

    <div v-if="loading" class="comment-state">
      <span class="spinner"></span>
      <p>正在加载评论…</p>
    </div>

    <div v-else-if="loadError" class="comment-state">
      <p class="state-box__title">评论加载失败</p>
      <p class="state-box__hint">{{ loadError }}</p>
      <button type="button" class="btn-ghost" @click="load(page)">重试</button>
    </div>

    <div v-else-if="!comments.length" class="comment-state">
      <p class="state-box__title">还没有评论</p>
      <p class="state-box__hint">来发表第一条评论吧。</p>
    </div>

    <ul v-else class="comment-list">
      <li v-for="item in comments" :key="item.id" class="comment-item">
        <div class="comment-main">
          <img
            v-if="item.avatar"
            :src="item.avatar"
            :alt="item.nickname"
            class="comment-avatar"
            @error="(event) => (event.target.style.display = 'none')"
          />
          <span v-else class="comment-avatar is-fallback">{{ avatarText(item) }}</span>

          <div class="comment-body">
            <div class="comment-meta">
              <span class="comment-name">{{ item.nickname || item.username }}</span>
              <span class="comment-time">{{ formatDateTime(item.createdAt) }}</span>
            </div>

            <template v-if="editingId === item.id">
              <textarea v-model="editContent" rows="3" maxlength="500" class="edit-area"></textarea>
              <div class="comment-actions">
                <span class="char-count">{{ editContent.length }}/500</span>
                <div class="action-buttons">
                  <button type="button" class="text-btn" @click="cancelEdit">
                    <X :size="13" /> 取消
                  </button>
                  <button
                    type="button"
                    class="text-btn is-primary"
                    :disabled="savingEdit || !editContent.trim()"
                    @click="saveEdit(item)"
                  >
                    <Loader2 v-if="savingEdit" :size="13" class="spin" />
                    <Send v-else :size="13" />
                    保存
                  </button>
                </div>
              </div>
            </template>

            <template v-else>
              <p class="comment-content">{{ item.content }}</p>
              <div class="comment-actions">
                <button type="button" class="text-btn" @click="openReply(item)">
                  <CornerDownRight :size="13" /> 回复
                </button>
                <template v-if="canManage(item)">
                  <button type="button" class="text-btn" @click="openEdit(item)">
                    <Pencil :size="13" /> 编辑
                  </button>
                  <button
                    type="button"
                    class="text-btn is-danger"
                    :disabled="deletingId === item.id"
                    @click="removeComment(item)"
                  >
                    <Loader2 v-if="deletingId === item.id" :size="13" class="spin" />
                    <Trash2 v-else :size="13" />
                    删除
                  </button>
                </template>
              </div>
            </template>

            <div v-if="replyTo === item.id" class="reply-composer">
              <textarea
                v-model="replyContent"
                rows="2"
                maxlength="500"
                :placeholder="`回复 @${item.nickname || item.username}`"
              ></textarea>
              <div class="comment-actions">
                <span class="char-count">{{ replyContent.length }}/500</span>
                <div class="action-buttons">
                  <button type="button" class="text-btn" @click="replyTo = 0">
                    <X :size="13" /> 取消
                  </button>
                  <button
                    type="button"
                    class="btn-brand reply-submit"
                    :disabled="replying || !replyContent.trim()"
                    @click="submitReply(item)"
                  >
                    <Loader2 v-if="replying" :size="13" class="spin" />
                    <Send v-else :size="13" />
                    发表回复
                  </button>
                </div>
              </div>
            </div>

            <ul v-if="item.replies?.length" class="reply-list">
              <li v-for="reply in item.replies" :key="reply.id" class="reply-item">
                <img
                  v-if="reply.avatar"
                  :src="reply.avatar"
                  :alt="reply.nickname"
                  class="comment-avatar is-small"
                  @error="(event) => (event.target.style.display = 'none')"
                />
                <span v-else class="comment-avatar is-small is-fallback">{{ avatarText(reply) }}</span>
                <div class="comment-body">
                  <div class="comment-meta">
                    <span class="comment-name">{{ reply.nickname || reply.username }}</span>
                    <span class="comment-time">{{ formatDateTime(reply.createdAt) }}</span>
                  </div>
                  <template v-if="editingId === reply.id">
                    <textarea v-model="editContent" rows="2" maxlength="500" class="edit-area"></textarea>
                    <div class="comment-actions">
                      <div class="action-buttons">
                        <button type="button" class="text-btn" @click="cancelEdit">
                          <X :size="13" /> 取消
                        </button>
                        <button
                          type="button"
                          class="text-btn is-primary"
                          :disabled="savingEdit || !editContent.trim()"
                          @click="saveEdit(reply)"
                        >
                          保存
                        </button>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    <p class="comment-content">{{ reply.content }}</p>
                    <div v-if="canManage(reply)" class="comment-actions">
                      <button type="button" class="text-btn" @click="openEdit(reply)">
                        <Pencil :size="13" /> 编辑
                      </button>
                      <button
                        type="button"
                        class="text-btn is-danger"
                        :disabled="deletingId === reply.id"
                        @click="removeComment(reply, true)"
                      >
                        <Trash2 :size="13" /> 删除
                      </button>
                    </div>
                  </template>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </li>
    </ul>

    <Pagination
      v-if="!loading && !loadError && total > 0"
      :page="page"
      :page-size="pageSize"
      :total="total"
      @change="load"
    />
  </section>
</template>

<style scoped lang="scss">
.comment-section {
  margin-top: 26px;
  padding: 24px 28px 28px;
}

.comment-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.comment-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  background-color: hsl(var(--muted));
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  font-weight: 500;
}

.comment-hint {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.composer {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 10px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--muted) / 0.45);
}

.composer-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex-shrink: 0;
  background-color: hsl(var(--brand));
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.composer-body {
  flex: 1;
  min-width: 0;
}

.composer-body textarea,
.reply-composer textarea,
.edit-area {
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--background) / 0.7);
  color: hsl(var(--foreground));
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.7;
  resize: vertical;
  outline: 0;

  &:focus {
    border-color: hsl(var(--brand) / 0.7);
  }
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.char-count {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.login-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px dashed hsl(var(--border));
  background-color: hsl(var(--muted) / 0.35);
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  transition: border-color 0.2s ease, color 0.2s ease;

  &:hover {
    border-color: hsl(var(--brand) / 0.6);
    color: hsl(var(--foreground));
  }
}

.login-banner__action {
  margin-left: auto;
  color: hsl(var(--brand));
  font-weight: 600;
}

.comment-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 36px 16px;
  color: hsl(var(--muted-foreground));
  font-size: 13.5px;
  text-align: center;
}

.comment-list {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.comment-item {
  padding-bottom: 18px;
  border-bottom: 1px solid hsl(var(--border) / 0.7);

  &:last-child {
    padding-bottom: 0;
    border-bottom: 0;
  }
}

.comment-main,
.reply-item {
  display: flex;
  gap: 12px;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background-color: hsl(var(--muted));
}

.comment-avatar.is-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: hsl(var(--muted));
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  font-weight: 700;
}

.comment-avatar.is-small {
  width: 28px;
  height: 28px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.comment-name {
  font-size: 13.5px;
  font-weight: 600;
  color: hsl(var(--foreground));
}

.comment-time {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.comment-content {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.8;
  color: hsl(var(--foreground) / 0.9);
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 6px;
}

.action-buttons {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.text-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 6px;
  border-radius: 5px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  transition: color 0.2s ease, background-color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
    background-color: hsl(var(--muted));
  }

  &.is-danger:hover {
    color: hsl(var(--brand));
  }

  &.is-primary {
    color: hsl(var(--brand));
    font-weight: 600;
  }
}

.reply-composer {
  margin-top: 10px;
  padding: 10px;
  border-radius: 8px;
  background-color: hsl(var(--muted) / 0.4);
}

.reply-submit {
  height: 30px;
  padding: 0 12px;
  font-size: 12.5px;
}

.reply-list {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background-color: hsl(var(--muted) / 0.25);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.reply-item {
  .comment-content {
    margin-top: 4px;
    font-size: 13.5px;
  }
}

.spinner {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.16);
  border-top-color: hsl(var(--brand));
  animation: spin 0.9s linear infinite;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .comment-section {
    padding: 18px;
  }

  .composer {
    flex-direction: column;
  }
}
</style>
