<script setup lang="ts">
defineOptions({
  name: 'AuthenticationFormView',
});

defineProps<{
  dataSide?: 'bottom' | 'left' | 'right' | 'top';
}>();
</script>

<template>
  <div
    class="flex-col-center bg-background relative px-6 py-10 lg:flex-initial lg:px-8"
  >
    <slot></slot>
    <!-- Router View with Transition and KeepAlive -->
    <!--
      meta.noTransition 的页面（如首次登录改密）直接渲染，不放进 Transition：
      登录后立刻跳过去时，在同一个 Transition 上切换 css 会让登录页的离场动画结束不了，
      out-in 模式下新页面就一直不出现（要刷新才看得到），之前还会停在半透明的样子。
    -->
    <RouterView v-slot="{ Component, route }">
      <component
        :is="Component"
        v-if="route.meta.noTransition"
        :key="route.fullPath"
        class="mt-6 w-full sm:mx-auto md:max-w-md"
        :data-side="dataSide"
      />
      <Transition v-else appear mode="out-in" name="slide-right">
        <KeepAlive :include="['Login']">
          <component
            :is="Component"
            :key="route.fullPath"
            class="side-content mt-6 w-full sm:mx-auto md:max-w-md"
            :data-side="dataSide"
          />
        </KeepAlive>
      </Transition>
    </RouterView>

    <!-- Footer Copyright -->

    <div
      class="text-muted-foreground absolute bottom-3 flex text-center text-xs"
    >
      <slot name="copyright"> </slot>
    </div>
  </div>
</template>
