<template>
   <div>
      <i-frame v-model:src="url"></i-frame>
   </div>
</template>

<script setup>
import { API_BASE_URL } from '@/utils/request';
import iFrame from '@/components/iFrame/index.vue'

import { ref } from 'vue';

const url = ref(API_BASE_URL + '/druid/index.html');
</script>
