import { test, expect } from '@playwright/test'
import { loginViaUI } from './helpers'

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

/**
 * 流程 5：医生写病历/处方（保存草稿）。
 * 填主诉（必填，否则前端拦截提示"请填写主诉"），点"保存草稿"，
 * 断言成功提示出现。验证"填写 → 校验 → 提交 → 成功反馈"的病历录入闭环。
 * mock（src/mock/record.js）创建病历固定返回成功。
 *
 * 注：RecordWrite 通过 route.query 接收 appointmentId/patientId，直接 goto 不带参数时
 * 走默认值（patientName='患者'），mock 下不影响草稿保存。
 */
test('医生填写主诉后保存病历草稿成功', async ({ page }) => {
  await loginViaUI(page, 'staff', 'doctor')

  // 进入病历书写页（路由 name=RecordWrite, path=/doctor/record/write）
  await page.goto('/doctor/record/write')
  await expect(page.getByPlaceholder('请输入主诉')).toBeVisible({ timeout: 15_000 })

  // 填主诉（前端校验：主诉为空时拦截）
  await page.getByPlaceholder('请输入主诉').fill('咳嗽伴胸闷3天')
  await page.getByPlaceholder('请输入初步诊断').fill('急性支气管炎')

  // 点"保存草稿"
  await page.getByRole('button', { name: '保存草稿' }).click()

  // 行为断言：成功提示出现（mock 固定返回，页面 ElMessage.success('草稿已保存')）
  await expect(page.getByText('草稿已保存').first()).toBeVisible({ timeout: 15_000 })
})

test('AI整理病历会填入诊断和医嘱草稿', async ({ page }) => {
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/record/write?patientId=1001&doctorId=1&patientName=测试患者')
  await expect(page.getByRole('heading', { name: '书写电子病历' })).toBeVisible({ timeout: 15_000 })
  await page.getByPlaceholder('请输入主诉').fill('咳嗽伴发热3天')

  await page.getByRole('button', { name: 'AI整理病历' }).click()

  await expect(page.getByPlaceholder('请输入初步诊断')).toHaveValue(/急性支气管炎/, { timeout: 15_000 })
  await expect(page.getByPlaceholder('请输入医嘱')).toHaveValue(/多饮水|复诊|随诊/)
})

test('医生可结构化开方并通知药房', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/record/write?patientId=1001&doctorId=1&patientName=测试患者')
  await expect(page.getByRole('heading', { name: '书写电子病历' })).toBeVisible({ timeout: 15_000 })

  await page.getByPlaceholder('请输入主诉').fill('咳嗽伴发热3天')
  await page.getByPlaceholder('请输入初步诊断').fill('急性支气管炎')

  const firstItem = page.getByTestId('structured-prescription-item').first()
  await expect(firstItem).toBeVisible()
  await firstItem.locator('.drug-name-select').click()
  await page.getByRole('option', { name: /阿莫西林胶囊/ }).click()
  await expect(firstItem.locator('[input-id="rx-spec-0"]')).toHaveValue('0.5g*24粒')
  await firstItem.getByPlaceholder('单次剂量').fill('0.5g')
  await firstItem.getByPlaceholder('频次').fill('每日三次')
  await firstItem.getByPlaceholder('给药途径').fill('口服')
  await firstItem.getByLabel('用药天数').fill('5')
  await firstItem.getByLabel('数量').fill('2')

  await expect(page.getByTestId('structured-prescription-total')).toContainText('¥51.00')
  await expectTouchTargetsAtLeast(page, '.record-action:visible')
  await page.getByRole('button', { name: '开方并通知药房' }).click()

  await expect(page.getByText('处方已推送药房，患者可缴费').first()).toBeVisible({ timeout: 15_000 })
  await expectNoHorizontalOverflow(page)
})

test('医生开方提交失败时显示可恢复错误', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')
  await page.evaluate(() => localStorage.setItem('mock_prescription_create_fail_once', '1'))

  await page.goto('/doctor/record/write?patientId=1001&doctorId=1&patientName=测试患者')
  await page.getByPlaceholder('请输入主诉').fill('咳嗽伴发热3天')
  await page.getByPlaceholder('请输入初步诊断').fill('急性支气管炎')

  const firstItem = page.getByTestId('structured-prescription-item').first()
  await firstItem.locator('.drug-name-select').click()
  await page.getByRole('option', { name: /阿莫西林胶囊/ }).click()
  await firstItem.getByLabel('用药天数').fill('5')
  await firstItem.getByLabel('数量').fill('2')

  await page.getByRole('button', { name: '开方并通知药房' }).click()

  await expect(page.getByRole('alert')).toContainText(/处方推送失败|处方提交失败|请重试/)
  await expect(firstItem.locator('.drug-name-select')).toContainText('阿莫西林胶囊')
  await expectNoHorizontalOverflow(page)
})
