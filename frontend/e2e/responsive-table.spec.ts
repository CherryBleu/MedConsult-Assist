import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function expectNoHorizontalOverflow(page) {
  const metrics = await page.evaluate(() => ({
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }))

  expect(metrics.documentScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
  expect(metrics.bodyScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
}

test.describe('responsive table', () => {
  test('admin user list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/user')

    await expect(page.locator('[data-testid="responsive-user-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('admin patient list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/patient')

    await expect(page.locator('[data-testid="responsive-patient-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('doctor reception list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'doctor')

    await page.goto('/doctor/reception')

    await expect(page.locator('[data-testid="responsive-reception-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('admin stock warning list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/stock-warning')

    await expect(page.locator('[data-testid="responsive-stock-warning-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('pharmacy stock warning list keeps filters usable on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'pharmacy')

    await page.goto('/pharmacy/stock-warning')

    await expect(page.locator('[data-testid="responsive-stock-warning-card"]').first()).toBeVisible()
    const lowStockFilter = page.getByTestId('stock-warning-filter-low')
    await lowStockFilter.locator('.el-radio-button__inner').click()
    await expect(lowStockFilter.getByRole('radio')).toBeChecked()
    await expect(page.locator('[data-testid="responsive-stock-warning-card"]')).toHaveCount(2)
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('audit log switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route('**/api/v1/audit-logs**', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'success',
          data: {
            total: 1,
            items: [{
              id: 1,
              auditNo: 'AUD202607170001',
              resourceType: 'appointment',
              resourceId: 'APT202607170001',
              action: 'VIEW',
              operatorName: '系统管理员',
              operatorRole: 'HOSPITAL_ADMIN',
              result: 'SUCCESS',
              createdAt: '2026-07-17 09:30:00'
            }]
          }
        })
      })
    })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/audit-log')

    await expect(page.locator('[data-testid="responsive-audit-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('admin AI call log switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/ai-call-log')

    await expect(page.locator('[data-testid="responsive-ai-call-log-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('admin AI feedback keeps processing dialog usable on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/ai-feedback')

    const firstFeedbackCard = page.locator('[data-testid="responsive-ai-feedback-card"]').first()
    await expect(firstFeedbackCard).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await firstFeedbackCard.getByTestId('ai-feedback-process-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expectNoHorizontalOverflow(page)
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })

  test('prescription review switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route('**/api/v1/prescriptions**', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue()
        return
      }
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'success',
          data: {
            total: 1,
            items: [{
              id: 1,
              prescriptionId: 'RX202607170001',
              status: 'PENDING_REVIEW',
              totalFee: 88.5,
              paymentStatus: 'UNPAID',
              createdAt: '2026-07-17 10:15:00'
            }]
          }
        })
      })
    })
    await loginViaUI(page, 'staff', 'pharmacy')

    await page.goto('/pharmacy/prescription-review')

    await expect(page.locator('[data-testid="responsive-prescription-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('prescription review ignores stale list responses after filter changes', async ({ page }) => {
    let pendingRoute: any = null
    let resolvePendingStarted: () => void = () => {}
    const pendingStarted = new Promise<void>(resolve => {
      resolvePendingStarted = resolve
    })

    const buildBody = (status: string | null) => JSON.stringify({
      code: 0,
      message: 'success',
      data: {
        total: 1,
        items: status === 'APPROVED'
          ? [{
              id: 2,
              prescriptionId: 'RX202607170099',
              status: 'APPROVED',
              totalFee: 126,
              paymentStatus: 'UNPAID',
              createdAt: '2026-07-17 10:20:00'
            }]
          : [{
              id: 1,
              prescriptionId: 'RX202607170000',
              status: 'PENDING_REVIEW',
              totalFee: 88.5,
              paymentStatus: 'UNPAID',
              createdAt: '2026-07-17 10:15:00'
            }]
      }
    })

    await page.route('**/api/v1/prescriptions**', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue()
        return
      }

      const url = new URL(route.request().url())
      const status = url.searchParams.get('status')
      if (status === 'PENDING_REVIEW' && !pendingRoute) {
        pendingRoute = route
        resolvePendingStarted()
        return
      }

      await route.fulfill({
        contentType: 'application/json',
        body: buildBody(status)
      })

      if (status === 'APPROVED' && pendingRoute) {
        setTimeout(() => {
          pendingRoute.fulfill({
            contentType: 'application/json',
            body: buildBody('PENDING_REVIEW')
          })
          pendingRoute = null
        }, 100)
      }
    })
    await loginViaUI(page, 'staff', 'pharmacy')

    await page.goto('/pharmacy/prescription-review')
    await pendingStarted
    await page.locator('.header-actions .el-select').click()
    await page.getByRole('option', { name: '已通过' }).click()

    const desktopTable = page.locator('.responsive-table__desktop')
    await expect(desktopTable.getByText('RX202607170099')).toBeVisible()
    await page.waitForTimeout(400)
    await expect(desktopTable.getByText('RX202607170099')).toBeVisible()
    await expect(desktopTable.getByText('RX202607170000')).toHaveCount(0)
  })
})
