<template>
  <el-descriptions :column="2" border>
    <el-descriptions-item label="商户展示名称">
      {{ application.merchantDisplayName }}
    </el-descriptions-item>
    <el-descriptions-item label="申请人">
      {{ application.applicantName }}
    </el-descriptions-item>
    <el-descriptions-item label="联系手机">
      {{ application.phoneNumber || '-' }}
    </el-descriptions-item>
    <el-descriptions-item label="国家/地区">
      {{ application.countryRegion }}
    </el-descriptions-item>
    <el-descriptions-item label="省市">
      {{ regionLabel }}
    </el-descriptions-item>
    <el-descriptions-item label="计划平台">
      {{ platformLabel }}
    </el-descriptions-item>
    <el-descriptions-item label="预计月订单">
      {{ application.monthlyOrderRange }}
    </el-descriptions-item>
    <el-descriptions-item label="预计月收款">
      {{ application.monthlyAmountRange }} 元
    </el-descriptions-item>
    <el-descriptions-item label="收款使用场景" :span="2">
      {{ application.paymentUseCase }}
    </el-descriptions-item>
    <el-descriptions-item label="提交时间">
      {{ formatApiTime(application.submittedAt) }}
    </el-descriptions-item>
    <el-descriptions-item label="更新时间">
      {{ formatApiTime(application.updatedAt) }}
    </el-descriptions-item>
  </el-descriptions>
</template>

<script setup lang="ts">
import { formatApiTime } from '@/api/payment/time';
import type { MerchantApplicationVO } from '@/api/payment/types';

const props = defineProps<{
  application: MerchantApplicationVO;
}>();

const regionLabel = computed(
  () =>
    [props.application.province, props.application.city]
      .filter(Boolean)
      .join(' ') || '-'
);
const platformLabel = computed(
  () =>
    props.application.plannedPlatforms
      .split(',')
      .filter(Boolean)
      .map(item => ({ WECHAT: '微信', ALIPAY: '支付宝' })[item] || item)
      .join('、') || '-'
);
</script>
