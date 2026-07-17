<template>
  <section class="responsive-table" :aria-label="ariaLabel">
    <div class="responsive-table__desktop">
      <slot name="table" />
    </div>
    <div class="responsive-table__mobile">
      <slot name="card" />
    </div>
  </section>
</template>

<script setup>
defineProps({
  ariaLabel: {
    type: String,
    default: '响应式列表'
  }
})
</script>

<style scoped>
.responsive-table {
  min-width: 0;
}

.responsive-table__desktop {
  min-width: 0;
}

.responsive-table__mobile {
  display: none;
  min-width: 0;
}

@media (max-width: 640px) {
  .responsive-table__desktop {
    display: none;
  }

  .responsive-table__mobile {
    display: grid;
    gap: 12px;
  }
}
</style>
