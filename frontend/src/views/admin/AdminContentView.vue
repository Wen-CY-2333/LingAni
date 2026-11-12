<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deleteAdminComment,
  deleteAdminDanmaku,
  getAdminComments,
  getAdminDanmaku,
  updateAdminCommentStatus,
  updateAdminDanmakuStatus
} from '@/api/backend'
import { formatDateTime } from '@/utils/datetime'
import { showError, showSuccess } from '@/utils/toast'
import Pagination from '@/components/Pagination.vue'

const activeTab = ref('comments')

const commentState = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
  keyword: '',
  loading: false,
  records: [],
  busyId: null
})

const danmakuState = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
  keyword: '',
  loading: false,
  records: [],
  busyId: null
})

async function loadComments() {
  commentState.loading = true
  try {
    const data = await getAdminComments({
      current: commentState.page,
      size: commentState.pageSize,
      keyword: commentState.keyword.trim() || undefined
    })
    commentState.records = data?.records || []
    commentState.total = data?.total || 0
  } catch (error) {
    showError(error.message)
    commentState.records = []
    commentState.total = 0
  } finally {
    commentState.loading = false
  }
}

async function loadDanmaku() {
  danmakuState.loading = true
  try {
    const data = await getAdminDanmaku({
      current: danmakuState.page,
      size: danmakuState.pageSize,
      keyword: danmakuState.keyword.trim() || undefined
    })
    danmakuState.records = data?.records || []
    danmakuState.total = data?.total || 0
  } catch (error) {
    showError(error.message)
    danmakuState.records = []
    danmakuState.total = 0
  } finally {
    danmakuState.loading = false
  }
}

function searchComments() {
  commentState.page = 1
  loadComments()
}

function searchDanmaku() {
  danmakuState.page = 1
  loadDanmaku()
}

async function setCommentStatus(row, status) {
  commentState.busyId = row.id
  try {
    await updateAdminCommentStatus(row.id, status)
    row.status = status
    showSuccess(status === 1 ? '已屏蔽该评论' : '已恢复该评论')
  } catch (error) {
    showError(error.message)
  } finally {
    commentState.busyId = null
  }
}

async function removeComment(row) {
  if (!window.confirm('确定删除这条评论吗？删除一级评论会同时删除其子回复。')) return
  commentState.busyId = row.id
  try {
    await deleteAdminComment(row.id)
    showSuccess('评论已删除')
    if (commentState.records.length === 1 && commentState.page > 1) {
      commentState.page -= 1
    }
    await loadComments()
  } catch (error) {
    showError(error.message)
  } finally {
    commentState.busyId = null
  }
}

async function setDanmakuStatus(row, status) {
  danmakuState.busyId = row.id
  try {
    await updateAdminDanmakuStatus(row.id, status)
    row.status = status
    showSuccess(status === 1 ? '已屏蔽该弹幕' : '已恢复该弹幕')
  } catch (error) {
    showError(error.message)
  } finally {
    danmakuState.busyId = null
  }
}

async function removeDanmaku(row) {
  if (!window.confirm('确定删除这条弹幕吗？')) return
  danmakuState.busyId = row.id
  try {
    await deleteAdminDanmaku(row.id)
    showSuccess('弹幕已删除')
    if (danmakuState.records.length === 1 && danmakuState.page > 1) {
      danmakuState.page -= 1
    }
    await loadDanmaku()
  } catch (error) {
    showError(error.message)
  } finally {
    danmakuState.busyId = null
  }
}

function colorHex(value) {
  return `#${(Number(value) || 0).toString(16).padStart(6, '0')}`
}

function displayUser(row) {
  return row.nickname || row.username || `用户 ${row.userId}`
}

const totalHint = computed(() => (activeTab.value === 'comments' ? commentState.total : danmakuState.total))

onMounted(() => {
  loadComments()
  loadDanmaku()
})
</script>

<template>
  <header class="admin-head">
    <div>
      <h1 class="admin-title">内容审核</h1>
      <p class="admin-sub">共 {{ totalHint }} 条记录，屏蔽后前台不再展示，删除不可恢复。</p>
    </div>
  </header>

  <div class="tabs">
    <button
      type="button"
      class="tab"
      :class="{ 'is-active': activeTab === 'comments' }"
      @click="activeTab = 'comments'"
    >
      评论
    </button>
    <button
      type="button"
      class="tab"
      :class="{ 'is-active': activeTab === 'danmaku' }"
      @click="activeTab = 'danmaku'"
    >
      弹幕
    </button>
  </div>

  <template v-if="activeTab === 'comments'">
    <div class="toolbar">
      <input
        v-model="commentState.keyword"
        class="search-input"
        type="search"
        placeholder="搜索评论内容"
        @keyup.enter="searchComments"
      />
      <button type="button" class="admin-btn" @click="searchComments">搜索</button>
      <button type="button" class="admin-btn" :disabled="commentState.loading" @click="loadComments">
        {{ commentState.loading ? '加载中…' : '刷新' }}
      </button>
    </div>

    <div class="admin-card">
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>动画 ID</th>
              <th>用户</th>
              <th>内容</th>
              <th>类型</th>
              <th>状态</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in commentState.records" :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ row.videoId }}</td>
              <td>{{ displayUser(row) }}</td>
              <td class="td-ellipsis" :title="row.content">{{ row.content }}</td>
              <td>
                <span class="badge badge--muted">{{ row.parentId ? '回复' : '一级评论' }}</span>
              </td>
              <td>
                <span class="badge" :class="row.status === 0 ? 'badge--ok' : 'badge--off'">
                  {{ row.status === 0 ? '正常' : '已屏蔽' }}
                </span>
              </td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    class="act-btn"
                    :disabled="commentState.busyId === row.id"
                    @click="setCommentStatus(row, row.status === 0 ? 1 : 0)"
                  >
                    {{ row.status === 0 ? '屏蔽' : '恢复' }}
                  </button>
                  <button
                    type="button"
                    class="act-btn is-danger"
                    :disabled="commentState.busyId === row.id"
                    @click="removeComment(row)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!commentState.records.length">
              <td colspan="8">
                <p class="empty-state">{{ commentState.loading ? '加载中…' : '暂无评论数据' }}</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        :page="commentState.page"
        :page-size="commentState.pageSize"
        :total="commentState.total"
        @change="(next) => { commentState.page = next; loadComments() }"
      />
    </div>
  </template>

  <template v-else>
    <div class="toolbar">
      <input
        v-model="danmakuState.keyword"
        class="search-input"
        type="search"
        placeholder="搜索弹幕内容"
        @keyup.enter="searchDanmaku"
      />
      <button type="button" class="admin-btn" @click="searchDanmaku">搜索</button>
      <button type="button" class="admin-btn" :disabled="danmakuState.loading" @click="loadDanmaku">
        {{ danmakuState.loading ? '加载中…' : '刷新' }}
      </button>
    </div>

    <div class="admin-card">
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>章节 ID</th>
              <th>用户</th>
              <th>内容</th>
              <th>时间点</th>
              <th>颜色</th>
              <th>状态</th>
              <th>发送时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in danmakuState.records" :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ row.sectionId }}</td>
              <td>{{ displayUser(row) }}</td>
              <td class="td-ellipsis" :title="row.text">{{ row.text }}</td>
              <td>{{ Number(row.time).toFixed(1) }}s</td>
              <td>
                <span class="color-dot" :style="{ backgroundColor: colorHex(row.color) }"></span>
              </td>
              <td>
                <span class="badge" :class="row.status === 0 ? 'badge--ok' : 'badge--off'">
                  {{ row.status === 0 ? '正常' : '已屏蔽' }}
                </span>
              </td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    class="act-btn"
                    :disabled="danmakuState.busyId === row.id"
                    @click="setDanmakuStatus(row, row.status === 0 ? 1 : 0)"
                  >
                    {{ row.status === 0 ? '屏蔽' : '恢复' }}
                  </button>
                  <button
                    type="button"
                    class="act-btn is-danger"
                    :disabled="danmakuState.busyId === row.id"
                    @click="removeDanmaku(row)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!danmakuState.records.length">
              <td colspan="9">
                <p class="empty-state">{{ danmakuState.loading ? '加载中…' : '暂无弹幕数据' }}</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        :page="danmakuState.page"
        :page-size="danmakuState.pageSize"
        :total="danmakuState.total"
        @change="(next) => { danmakuState.page = next; loadDanmaku() }"
      />
    </div>
  </template>
</template>

<style scoped lang="scss">
.color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 1px solid hsl(var(--border));
  vertical-align: middle;
}
</style>
