import { API_V1_BASE_URL } from '@/utils/apiBase'
import { getToken } from '@/utils/auth'

const parseSseChunk = (buffer, onEvent) => {
  let remaining = buffer
  let separatorIndex = remaining.search(/\r?\n\r?\n/)
  while (separatorIndex !== -1) {
    const rawEvent = remaining.slice(0, separatorIndex)
    const separator = remaining.match(/\r?\n\r?\n/)?.[0] || '\n\n'
    remaining = remaining.slice(separatorIndex + separator.length)
    emitSseEvent(rawEvent, onEvent)
    separatorIndex = remaining.search(/\r?\n\r?\n/)
  }
  return remaining
}

const emitSseEvent = (rawEvent, onEvent) => {
  const lines = rawEvent.split(/\r?\n/)
  let event = 'message'
  const dataLines = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (!dataLines.length) return
  const rawData = dataLines.join('\n')
  let data = rawData
  try {
    data = JSON.parse(rawData)
  } catch (e) {
    // Keep raw text for heartbeat or non-JSON events.
  }
  onEvent({ event, data })
}

export const subscribeNoticeStream = ({ onEvent, onError } = {}) => {
  const controller = new AbortController()
  let reconnectTimer = null

  const scheduleReconnect = () => {
    if (controller.signal.aborted) return
    reconnectTimer = window.setTimeout(connect, 2000)
  }

  const connect = async () => {
    const token = getToken()
    const decoder = new TextDecoder('utf-8')
    try {
      const response = await fetch(`${API_V1_BASE_URL}/notifications/stream`, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        signal: controller.signal
      })
      if (!response.ok || !response.body) {
        throw new Error(`notice stream failed: ${response.status}`)
      }

      const reader = response.body.getReader()
      let buffer = ''
      while (!controller.signal.aborted) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        buffer = parseSseChunk(buffer, onEvent || (() => {}))
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        onError?.(error)
      }
    } finally {
      scheduleReconnect()
    }
  }

  connect()
  return () => {
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    controller.abort()
  }
}
