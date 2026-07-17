import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const BREAKPOINTS = {
  mobile: 640,
  tablet: 1024,
  desktop: 1280
}

export function useResponsive() {
  const width = ref(typeof window === 'undefined' ? BREAKPOINTS.desktop : window.innerWidth)

  const updateWidth = () => {
    width.value = window.innerWidth
  }

  onMounted(() => {
    updateWidth()
    window.addEventListener('resize', updateWidth, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', updateWidth)
  })

  const isMobile = computed(() => width.value < BREAKPOINTS.mobile)
  const isTablet = computed(() => width.value >= BREAKPOINTS.mobile && width.value < BREAKPOINTS.tablet)
  const isDesktop = computed(() => width.value >= BREAKPOINTS.tablet)
  const breakpoint = computed(() => {
    if (isMobile.value) return 'mobile'
    if (isTablet.value) return 'tablet'
    if (width.value < BREAKPOINTS.desktop) return 'desktop'
    return 'wide'
  })

  return {
    width,
    breakpoint,
    isMobile,
    isTablet,
    isDesktop
  }
}
