import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

test('患者重复申请退款只生成一条退款单', async ({ page }) => {
  await loginViaUI(page, 'patient', 'patient')
  await page.goto('/patient/appointment')
  await expect(page.getByRole('heading', { name: '我的预约' })).toBeVisible({ timeout: 15_000 })

  const appointment = page.locator('.appointment-item').filter({ hasText: 'APT20260710001' })
  await expect(appointment).toBeVisible()

  await appointment.getByRole('button', { name: '申请退款' }).click()
  await page.getByRole('button', { name: '确认申请' }).dblclick()

  await expect(appointment.getByText('已退款')).toBeVisible()
  await expect(appointment.getByRole('button', { name: '申请退款' })).toHaveCount(0)

  const refundOrderCount = await page.evaluate(() => {
    const orders = JSON.parse(window.localStorage.getItem('mock_refund_orders') || '[]')
    return orders.filter((order: { appointmentNo: string }) => order.appointmentNo === 'APT20260710001').length
  })
  expect(refundOrderCount).toBe(1)
})
