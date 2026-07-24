export const maskPhone = (phone) => {
  const value = phone == null ? '' : String(phone).trim()
  if (!value) return ''
  if (value.includes('*')) return value
  if (value.length < 7) return value
  return `${value.slice(0, 3)}****${value.slice(-4)}`
}
