<script setup>
import { computed, reactive } from 'vue'
import { Crosshair, RotateCcw, Save } from 'lucide-vue-next'
import {
  CUSTOM_MODEL_ID,
  MASCOT_LIMITS,
  MASCOT_MODELS,
  ensureMascotPosition,
  mascotPosition,
  mascotSettings,
  resetMascotSettings,
  saveMascotSettings
} from '@/stores/mascot'
import { showError, showSuccess } from '@/utils/toast'

ensureMascotPosition()

const draft = reactive({
  modelId: mascotSettings.modelId,
  customModelUrl: mascotSettings.customModelUrl,
  modelScale: mascotSettings.modelScale,
  panelOffsetX: mascotSettings.panelOffsetX,
  panelOffsetY: mascotSettings.panelOffsetY
})

const draftPosition = reactive({
  left: Math.round(mascotPosition.left),
  top: Math.round(mascotPosition.top)
})

const displaySize = computed(() => ({
  width: Math.round(mascotSettings.canvasWidth * draft.modelScale),
  height: Math.round(mascotSettings.canvasHeight * draft.modelScale)
}))

function syncDraft() {
  draft.modelId = mascotSettings.modelId
  draft.customModelUrl = mascotSettings.customModelUrl
  draft.modelScale = mascotSettings.modelScale
  draft.panelOffsetX = mascotSettings.panelOffsetX
  draft.panelOffsetY = mascotSettings.panelOffsetY
  draftPosition.left = Math.round(mascotPosition.left)
  draftPosition.top = Math.round(mascotPosition.top)
}

function pickNumber(value, range, fallback) {
  const num = Number(value)
  if (!Number.isFinite(num) || value === '') return fallback
  return Math.min(range.max, Math.max(range.min, num))
}

/** 以下 apply* 在表单变化时立即写入全局状态，看板娘实时预览。 */
function applyModel() {
  if (!draft.modelId) return
  mascotSettings.modelId = draft.modelId
  mascotSettings.customModelUrl = draft.customModelUrl.trim()
}

function applyModelScale() {
  const scale = pickNumber(draft.modelScale, MASCOT_LIMITS.modelScale, mascotSettings.modelScale)
  draft.modelScale = Math.round(scale * 100) / 100
  mascotSettings.modelScale = draft.modelScale
}

function applyPanelPosition() {
  draft.panelOffsetX = Math.round(
    pickNumber(draft.panelOffsetX, MASCOT_LIMITS.panelOffsetX, mascotSettings.panelOffsetX)
  )
  draft.panelOffsetY = Math.round(
    pickNumber(draft.panelOffsetY, MASCOT_LIMITS.panelOffsetY, mascotSettings.panelOffsetY)
  )
  mascotSettings.panelOffsetX = draft.panelOffsetX
  mascotSettings.panelOffsetY = draft.panelOffsetY
}

function applyPosition() {
  draftPosition.left = Math.max(0, Math.round(pickNumber(draftPosition.left, { min: 0, max: window.innerWidth }, mascotPosition.left)))
  draftPosition.top = Math.max(0, Math.round(pickNumber(draftPosition.top, { min: 0, max: window.innerHeight - 80 }, mascotPosition.top)))
  mascotPosition.left = draftPosition.left
  mascotPosition.top = draftPosition.top
}

/** 用看板娘当前所在位置作为默认位置。 */
function useCurrentPosition() {
  draftPosition.left = Math.round(mascotPosition.left)
  draftPosition.top = Math.round(mascotPosition.top)
}

/** 恢复为贴右侧、垂直居中。 */
function useAutoPosition() {
  draftPosition.left = Math.max(0, window.innerWidth - displaySize.value.width)
  draftPosition.top = Math.max(0, window.innerHeight / 2 - 50)
  applyPosition()
}

function save() {
  if (draft.modelId === CUSTOM_MODEL_ID && !draft.customModelUrl.trim()) {
    showError(new Error('请填写自定义模型地址'))
    return
  }
  saveMascotSettings({
    modelId: draft.modelId,
    customModelUrl: draft.customModelUrl,
    modelScale: draft.modelScale,
    panelOffsetX: draft.panelOffsetX,
    panelOffsetY: draft.panelOffsetY,
    defaultPosition: {
      left: Number(draftPosition.left) || 0,
      top: Number(draftPosition.top) || 0
    }
  })
  syncDraft()
  showSuccess('看板娘设置已保存')
}

function reset() {
  resetMascotSettings()
  syncDraft()
  showSuccess('已恢复默认设置')
}
</script>

<template>
  <div class="mascot-page container">
    <section class="page-head">
      <p class="eyebrow">看板娘</p>
      <h1 class="page-title">小灵外观设置</h1>
      <p class="page-desc">调整时看板娘会实时预览，点击保存后长期生效，配置只保存在当前浏览器。</p>
    </section>

    <div class="settings-grid">
      <section class="panel surface">
        <h2 class="section-title">模型</h2>
        <div class="panel-form">
          <label class="field">
            <span class="field-label">选择模型</span>
            <select v-model="draft.modelId" @change="applyModel">
              <option v-for="item in MASCOT_MODELS" :key="item.id" :value="item.id">{{ item.name }}</option>
              <option :value="CUSTOM_MODEL_ID">自定义模型地址</option>
            </select>
          </label>
          <label v-if="draft.modelId === CUSTOM_MODEL_ID" class="field">
            <span class="field-label">模型地址（model.json）</span>
            <input
              v-model="draft.customModelUrl"
              type="text"
              placeholder="/live2d/model/xxx/model.json"
              @change="applyModel"
            />
          </label>
        </div>
      </section>

      <section class="panel surface">
        <h2 class="section-title">模型大小</h2>
        <div class="panel-form">
          <label class="field">
            <span class="field-label">模型大小 · {{ Number(draft.modelScale).toFixed(2) }}×</span>
            <input
              v-model.number="draft.modelScale"
              type="range"
              :min="MASCOT_LIMITS.modelScale.min"
              :max="MASCOT_LIMITS.modelScale.max"
              :step="MASCOT_LIMITS.modelScale.step"
              @input="applyModelScale"
            />
          </label>
          <p class="hint">实际显示尺寸：{{ displaySize.width }} × {{ displaySize.height }} px</p>
        </div>
      </section>

      <section class="panel surface">
        <h2 class="section-title">聊天框位置</h2>
        <div class="panel-form">
          <label class="field">
            <span class="field-label">水平偏移 · {{ draft.panelOffsetX }} px</span>
            <input
              v-model.number="draft.panelOffsetX"
              type="range"
              :min="MASCOT_LIMITS.panelOffsetX.min"
              :max="MASCOT_LIMITS.panelOffsetX.max"
              :step="MASCOT_LIMITS.panelOffsetX.step"
              @input="applyPanelPosition"
            />
          </label>
          <label class="field">
            <span class="field-label">垂直偏移 · {{ draft.panelOffsetY }} px</span>
            <input
              v-model.number="draft.panelOffsetY"
              type="range"
              :min="MASCOT_LIMITS.panelOffsetY.min"
              :max="MASCOT_LIMITS.panelOffsetY.max"
              :step="MASCOT_LIMITS.panelOffsetY.step"
              @input="applyPanelPosition"
            />
          </label>
          <p class="hint">悬停看板娘即可查看聊天框；聊天框会自动出现在看板娘空间更充足的一侧，偏移以看板娘为基准，负值表示靠近看板娘。</p>
        </div>
      </section>

      <section class="panel surface">
        <h2 class="section-title">默认位置</h2>
        <div class="panel-form">
          <div class="field-row">
            <label class="field">
              <span class="field-label">距左侧（px）</span>
              <input v-model.number="draftPosition.left" type="number" min="0" @input="applyPosition" />
            </label>
            <label class="field">
              <span class="field-label">距顶部（px）</span>
              <input v-model.number="draftPosition.top" type="number" min="0" @input="applyPosition" />
            </label>
          </div>
          <div class="actions-row">
            <button type="button" class="btn-ghost" @click="useCurrentPosition">
              <Crosshair :size="14" /> 使用当前位置
            </button>
            <button type="button" class="btn-ghost" @click="useAutoPosition">恢复右下角居中</button>
          </div>
          <p class="hint">也可以直接拖动右下角的看板娘，再点「使用当前位置」。</p>
        </div>
      </section>
    </div>

    <div class="page-actions">
      <button type="button" class="btn-brand" @click="save">
        <Save :size="15" /> 保存并应用
      </button>
      <button type="button" class="btn-ghost" @click="reset">
        <RotateCcw :size="15" /> 恢复默认
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mascot-page {
  padding-top: 32px;
  padding-bottom: 24px;
}

/* 宽屏时为右侧看板娘预览留出空间，避免表单遮挡 */
@media (min-width: 1101px) {
  .mascot-page {
    padding-right: 380px;
  }
}

.page-head {
  max-width: 720px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: hsl(var(--brand));
}

.page-title {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  color: hsl(var(--foreground));
}

.page-desc {
  margin-top: 8px;
  font-size: 13.5px;
  line-height: 1.7;
  color: hsl(var(--muted-foreground));
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 20px;
  margin-top: 26px;
}

.panel {
  padding: 22px 24px;
}

.panel-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.field select,
.field input[type='text'],
.field input[type='number'] {
  height: 40px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--muted) / 0.7);
  color: hsl(var(--foreground));
  outline: 0;
  font-size: 14px;

  &:focus {
    border-color: hsl(var(--brand) / 0.7);
  }
}

.field input[type='range'] {
  width: 100%;
  accent-color: hsl(var(--brand));
  cursor: pointer;
}

.field-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hint {
  font-size: 12.5px;
  line-height: 1.7;
  color: hsl(var(--muted-foreground));
}

.page-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}
</style>
