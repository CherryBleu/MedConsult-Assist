# Patient AI Consult Redesign 实现计划

> **面向 AI 代理的工作者：** 本计划在当前治理 worktree 内内联执行。步骤使用复选框（`- [ ]`）跟踪进度；遵守 TDD、小步提交、仅本地 commit、不 push。

**目标：** 将患者 AI 问诊页从普通聊天卡片重构为安全优先、RAG 证据可追溯、移动端和键盘可用的临床问诊工作台。

**架构：** 保持 Vue 3 + Pinia + Element Plus。`aiChat.js` 负责服务端响应归一化、失败状态和 retry 文本；`AiConsult.vue` 负责展示安全摘要、证据、时间线和 composer；Playwright 以 mock 数据验证患者真实交互。

**技术栈：** Vue 3、Pinia、Vue Router、Element Plus、@element-plus/icons-vue、Playwright、@axe-core/playwright。

---

## 文件职责

- 修改：`frontend/e2e/symptom-chat.spec.ts`
  - 增加患者 AI 问诊页的移动端、风险摘要、错误恢复、键盘和 axe 门禁。
- 修改：`frontend/src/mock/ai.js`
  - 增加高风险症状 mock、发送失败一次 mock，并保留现有低风险 RAG mock。
- 修改：`frontend/src/store/modules/aiChat.js`
  - 归一化 `failed`、`retryText`、`emergencyAdvice`、`riskLevel`、`possibleCauses`、`suggestedDepartments`。
  - 失败时保留患者消息，追加可重试的 AI 错误消息。
- 修改：`frontend/src/views/patient/ai-service/AiConsult.vue`
  - 改为临床工作台布局：主时间线、输入 composer、安全摘要、证据 disclosure、会话辅助信息。
  - 删除玻璃/渐变装饰，使用现有医疗设计 token。
- 修改：`docs/遗留问题复盘与实施状态.md`
  - 记录患者 AI 问诊页本轮 UI/RAG 证据体验推进和验证命令。
- 修改：`docs/项目实施基线.md`
  - 将患者 AI 问诊页从“RAG 引用展示”更新到“安全摘要 + 证据 + 错误恢复”阶段。

---

## 任务 1：写失败的 Playwright 门禁

**文件：**
- 修改：`frontend/e2e/symptom-chat.spec.ts`

- [ ] **步骤 1：增加辅助断言**

```ts
async function expectNoHorizontalOverflow(page) {
  const metrics = await page.evaluate(() => ({
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }))
  expect(metrics.documentScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
  expect(metrics.bodyScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
}

async function expectTouchTargetsAtLeast(page, selector: string, minSize = 44) {
  const boxes = await page.locator(selector).evaluateAll(elements =>
    elements.map(element => {
      const rect = element.getBoundingClientRect()
      return { width: rect.width, height: rect.height }
    })
  )
  expect(boxes.length).toBeGreaterThan(0)
  for (const box of boxes) {
    expect(box.width).toBeGreaterThanOrEqual(minSize)
    expect(box.height).toBeGreaterThanOrEqual(minSize)
  }
}
```

- [ ] **步骤 2：增加高风险、移动、错误恢复和键盘测试**

测试点：

- `胸痛大汗怎么办` 后出现 `role="alert"` 安全摘要，且安全摘要在检索证据前。
- 390px 宽度下无水平滚动。
- `.ai-consult-action` 控件均不小于 44px。
- 设置 `localStorage.mock_symptom_chat_fail_once=1` 后发送失败，用户原症状仍存在，错误区域 `role="alert"`，点击“重试”后同一症状再次发送成功。
- `Tab` 可聚焦证据 disclosure，`Enter` 可切换展开。
- axe 无 serious/critical 违规。

- [ ] **步骤 3：运行测试确认失败**

运行：

```powershell
npm --prefix frontend run test:e2e -- symptom-chat.spec.ts
```

预期：至少新增测试因缺少安全摘要/重试控件/44px composer 控件而失败。

---

## 任务 2：补 mock 与 store 行为

**文件：**
- 修改：`frontend/src/mock/ai.js`
- 修改：`frontend/src/store/modules/aiChat.js`

- [ ] **步骤 1：mock 支持高风险和失败一次**

`mockSendMessage()` 增加：

- `consumeFailOnce('mock_symptom_chat_fail_once')` 时 reject。
- `胸痛大汗怎么办` 返回 `riskLevel: 'HIGH'`、`emergencyAdvice: true`、心血管/急诊相关引用。

- [ ] **步骤 2：store 归一化错误消息**

失败消息结构：

```js
{
  id: Date.now(),
  role: 'ai',
  content: 'AI 问诊服务暂时不可用，请检查网络后重试。',
  failed: true,
  retryText: content,
  riskLevel: '',
  emergencyAdvice: false,
  possibleCauses: [],
  suggestedDepartments: [],
  citations: [],
  vectorMatches: []
}
```

- [ ] **步骤 3：运行 focused 测试**

运行：

```powershell
npm --prefix frontend run test:e2e -- symptom-chat.spec.ts
```

预期：错误恢复相关行为开始通过；仍可能因 UI 结构未改而失败。

---

## 任务 3：重构 AiConsult 结构与样式

**文件：**
- 修改：`frontend/src/views/patient/ai-service/AiConsult.vue`

- [ ] **步骤 1：替换页面结构**

结构：

```vue
<section class="consult-shell" aria-labelledby="ai-consult-title">
  <header class="consult-header">...</header>
  <div class="consult-layout">
    <main class="consult-main" aria-label="AI 问诊消息">
      <section class="message-timeline" ref="messagesRef">...</section>
      <form class="consult-composer" @submit.prevent="sendMessage">...</form>
    </main>
    <aside class="consult-aside" aria-label="问诊提示与会话状态">...</aside>
  </div>
</section>
```

- [ ] **步骤 2：新增安全摘要展示**

每条 AI 消息在正文前渲染：

- `section.safety-summary`
- 高风险使用 `role="alert"`。
- 低风险使用 `aria-live="polite"`。
- 显示风险等级、急症建议、可能原因、建议科室。

- [ ] **步骤 3：新增错误恢复和 retry**

错误消息渲染为：

- `section.chat-error[role="alert"]`
- “重试”按钮调用 `retryMessage(msg.retryText)`。

- [ ] **步骤 4：改 composer**

用可见 label + `el-input type="textarea"` + 独立发送按钮替代 append 按钮。

- [ ] **步骤 5：改样式**

移除 page-level glass/gradient/radial background；保证移动端：

- 无横向滚动。
- quick question 与 send/retry/evidence summary 控件均 44px。
- sticky composer 不遮挡消息内容。
- `prefers-reduced-motion` 禁用非必要过渡。

---

## 任务 4：同步文档

**文件：**
- 修改：`docs/遗留问题复盘与实施状态.md`
- 修改：`docs/项目实施基线.md`

- [ ] **步骤 1：记录本轮 UI/RAG 前端状态**

写明：

- 患者 AI 问诊页已增加安全摘要、RAG 证据 disclosure、错误恢复重试、移动端和 axe 门禁。
- 不代表前端大规模重构全部完成，医生/药房工作台等仍待推进。

- [ ] **步骤 2：记录验证命令**

记录 focused E2E、build、截图/axe 结果。

---

## 任务 5：验证和提交

- [ ] **步骤 1：运行 focused E2E**

```powershell
npm --prefix frontend run test:e2e -- symptom-chat.spec.ts
```

- [ ] **步骤 2：运行生产构建**

```powershell
npm --prefix frontend run build
```

- [ ] **步骤 3：运行 diff 检查**

```powershell
git diff --check
git diff --cached --check
```

- [ ] **步骤 4：提交**

只 stage 本轮文件，不 stage `backend/ai-service/src/main/resources/db/upgrade-ai-architecture-20260710.sql`。

```powershell
git add -- frontend/e2e/symptom-chat.spec.ts frontend/src/mock/ai.js frontend/src/store/modules/aiChat.js frontend/src/views/patient/ai-service/AiConsult.vue docs/遗留问题复盘与实施状态.md docs/项目实施基线.md
git commit -m "feat(frontend): 重构患者AI问诊安全体验"
```
