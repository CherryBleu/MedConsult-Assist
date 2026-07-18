import { spawn, spawnSync } from 'node:child_process'
import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

const frontendDir = fileURLToPath(new URL('..', import.meta.url))
const viteBin = fileURLToPath(new URL('../node_modules/vite/bin/vite.js', import.meta.url))
const playwrightBin = fileURLToPath(new URL('../node_modules/playwright/cli.js', import.meta.url))
const serverUrl = 'http://127.0.0.1:3100'

function isAvailable(url) {
  return new Promise((resolve) => {
    const req = http.get(url, (res) => {
      res.resume()
      resolve(res.statusCode && res.statusCode < 500)
    })
    req.on('error', () => resolve(false))
    req.setTimeout(1000, () => {
      req.destroy()
      resolve(false)
    })
  })
}

async function waitForServer(url, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (await isAvailable(url)) return
    await delay(250)
  }
  throw new Error(`Timed out waiting for ${url}`)
}

function run(command, args, options) {
  const child = spawn(command, args, options)
  return new Promise((resolve) => {
    child.on('close', (code, signal) => resolve({ code, signal }))
  })
}

function stopProcessTree(child) {
  if (!child || child.killed) return
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(child.pid), '/t', '/f'], { stdio: 'ignore' })
  } else {
    child.kill('SIGTERM')
  }
}

let viteProcess
let exitCode = 1

try {
  if (!(await isAvailable(serverUrl))) {
    viteProcess = spawn(process.execPath, [viteBin, '--host', '127.0.0.1', '--port', '3100', '--strictPort'], {
      cwd: frontendDir,
      env: {
        ...process.env,
        VITE_USE_MOCK: 'true',
        VITE_PORT: '3100'
      },
      stdio: 'inherit'
    })
    await waitForServer(serverUrl, 60_000)
  }

  const result = await run(process.execPath, [playwrightBin, 'test', '--config', 'playwright.config.ts', ...process.argv.slice(2)], {
    cwd: frontendDir,
    env: {
      ...process.env,
      PW_SKIP_WEBSERVER: 'true'
    },
    stdio: 'inherit'
  })
  exitCode = result.code ?? (result.signal ? 1 : 0)
} catch (error) {
  console.error(error)
  exitCode = 1
} finally {
  stopProcessTree(viteProcess)
}

process.exit(exitCode)
