import { ref, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { writerGenerate } from '../api/writer'

/**
 * 办公写作助手共享逻辑：表单提交 → SSE 流式接收 → 复制结果
 * @param {string} type weekly | email | notice
 */
export function useWriter(type) {
  const result = ref('')
  const loading = ref(false)
  /** 命中的生成通道：dify / deepseek / mock */
  const source = ref('')
  /** 当前流式请求的 AbortController（组件卸载时中断） */
  let controller = null

  const sourceLabels = {
    dify: 'Dify 办公写作助手',
    deepseek: 'DeepSeek 直连',
    mock: '演示模板'
  }

  /**
   * 提交生成
   * @param {object} form { points, plan?, recipient?, scene?, subject?, tone? }
   */
  async function generate(form) {
    if (loading.value) return
    loading.value = true
    result.value = ''
    source.value = ''
    controller = new AbortController()
    try {
      await writerGenerate(
        { type, ...form },
        {
          onSource: (s) => {
            source.value = s
          },
          onMessage: (chunk) => {
            result.value += chunk
          },
          onDone: () => {
            loading.value = false
          },
          onError: (e) => {
            loading.value = false
            ElMessage.error(e?.message || '生成失败，请稍后重试')
          }
        },
        controller.signal
      )
    } finally {
      loading.value = false
      controller = null
    }
  }

  // 组件卸载时中断进行中的 SSE 流，避免孤儿请求与卸载后写状态
  onBeforeUnmount(() => {
    if (controller) {
      controller.abort()
      controller = null
    }
  })

  /** 复制生成结果 */
  async function copyResult() {
    if (!result.value) {
      ElMessage.warning('暂无内容可复制')
      return
    }
    try {
      await navigator.clipboard.writeText(result.value)
      ElMessage.success('已复制到剪贴板')
    } catch (e) {
      ElMessage.error('复制失败，请手动选择文本复制')
    }
  }

  return { result, loading, source, sourceLabels, generate, copyResult }
}
