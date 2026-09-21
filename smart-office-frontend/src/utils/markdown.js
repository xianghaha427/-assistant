/**
 * Markdown 轻量渲染工具（不引第三方依赖）
 * 支持：标题 / 加粗 / 斜体 / 行内代码 / 列表 / 表格 / 分隔线 / 段落换行。
 * 输出为 HTML 字符串，供 v-html 使用；首步整体转义 HTML，防注入。
 * 共用方：BriefingHistory（全文弹窗）、Dashboard（今日简报预览卡）。
 */
export function renderMd(text) {
  const src = String(text || '')
  if (!src.trim()) return ''
  // 0. 去除 ```echarts 代码块（只用于 AI 对话图表，简报不显示）
  const clean = src.replace(/```echarts[\s\S]*?```/g, '')
  // 1. 转义 HTML，防注入
  const esc = clean.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const lines = esc.split('\n')
  const out = []
  let list = null // 'ul' | 'ol' | null
  let table = null // { head: [], aligns: [], rows: [] } | null

  const closeList = () => {
    if (list) { out.push(list === 'ul' ? '</ul>' : '</ol>'); list = null }
  }
  const closeTable = () => {
    if (!table) return
    let html = '<table><thead><tr>' + table.head.map((h) => '<th>' + inline(h) + '</th>').join('') + '</tr></thead>'
    if (table.rows.length) {
      html += '<tbody>' + table.rows.map((r) => '<tr>' + r.map((c) => '<td>' + inline(c) + '</td>').join('') + '</tr>').join('') + '</tbody>'
    }
    out.push(html + '</table>')
    table = null
  }
  const inline = (s) => s
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')

  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '')
    const t = line.trim()

    // 表格分隔行 |---|---|
    if (/^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(t)) {
      if (out.length && !list && !table) {
        const prev = out.pop()
        const cells = prev.replace(/^<p>/, '').replace(/<\/p>$/, '').split('|').map((c) => c.trim()).filter((c) => c !== '')
        if (cells.length) table = { head: cells, rows: [] }
      }
      continue
    }
    // 表格数据行
    if (table && t.includes('|')) {
      table.rows.push(t.replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim()))
      continue
    }
    closeTable()

    // 标题
    const h = t.match(/^(#{1,6})\s+(.*)$/)
    if (h) {
      closeList()
      const lv = h[1].length
      out.push('<h' + lv + '>' + inline(h[2]) + '</h' + lv + '>')
      continue
    }
    // 无序列表
    const ul = t.match(/^[-*+]\s+(.*)$/)
    if (ul) {
      if (list !== 'ul') { closeList(); out.push('<ul>'); list = 'ul' }
      out.push('<li>' + inline(ul[1]) + '</li>')
      continue
    }
    // 有序列表
    const ol = t.match(/^\d+[.、)]\s+(.*)$/)
    if (ol) {
      if (list !== 'ol') { closeList(); out.push('<ol>'); list = 'ol' }
      out.push('<li>' + inline(ol[1]) + '</li>')
      continue
    }
    closeList()
    // 空行
    if (!t) continue
    // 分隔线
    if (/^-{3,}$/.test(t)) { out.push('<hr>'); continue }
    // 普通段落（简报多为单行一段，逐行成段）
    out.push('<p>' + inline(t) + '</p>')
  }
  closeList()
  closeTable()
  return out.join('\n')
}
