const stripTrailingSlash = (value) => value.replace(/\/+$/, '')

export const API_BASE_URL = stripTrailingSlash(import.meta.env.VITE_API_BASE_URL || '/api')
export const API_V1_BASE_URL = `${API_BASE_URL}/v1`
