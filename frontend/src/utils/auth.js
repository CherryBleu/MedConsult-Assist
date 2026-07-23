const TOKEN_KEY = 'hospital_token'
const REFRESH_TOKEN_KEY = 'hospital_refresh_token'

const getSessionValue = (key) => {
  try {
    const sessionValue = sessionStorage.getItem(key)
    if (sessionValue) return sessionValue

    const legacyValue = localStorage.getItem(key)
    if (legacyValue) {
      sessionStorage.setItem(key, legacyValue)
      localStorage.removeItem(key)
      return legacyValue
    }
  } catch (e) {
    return ''
  }
  return ''
}

const setSessionValue = (key, value) => {
  try {
    sessionStorage.setItem(key, value)
    localStorage.removeItem(key)
  } catch (e) {
    // The Pinia store still keeps the token for the current page lifecycle.
  }
}

const removeSessionValue = (key) => {
  try {
    sessionStorage.removeItem(key)
    localStorage.removeItem(key)
  } catch (e) {
    // Ignore storage failures.
  }
}

// Store auth tokens per browser tab so patient/staff pages can be opened side by side.
export const setToken = (token) => {
  setSessionValue(TOKEN_KEY, token)
}

export const getToken = () => {
  return getSessionValue(TOKEN_KEY)
}

export const removeToken = () => {
  removeSessionValue(TOKEN_KEY)
}

export const setRefreshToken = (refreshToken) => {
  setSessionValue(REFRESH_TOKEN_KEY, refreshToken)
}

export const getRefreshToken = () => {
  return getSessionValue(REFRESH_TOKEN_KEY)
}

export const removeRefreshToken = () => {
  removeSessionValue(REFRESH_TOKEN_KEY)
}
