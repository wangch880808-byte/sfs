<template>
  <div class="mb-7 sm:mx-auto sm:w-full sm:max-w-md">
    <h2 class="text-foreground mb-2 text-2xl font-semibold leading-8">
      <slot></slot>
    </h2>

    <p class="text-muted-foreground text-sm">
      <slot name="desc"></slot>
    </p>
  </div>
</template>
