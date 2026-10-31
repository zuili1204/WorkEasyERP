<template>
  <view class="page">
    <view class="card summary">
      <view class="muted">应收余额（未结清）</view>
      <view class="amount">¥{{ money(ar?.totalRemain) }}</view>
      <view v-if="overdue > 0" class="overdue">其中逾期 ¥{{ money(overdue) }}</view>
    </view>

    <view class="card" v-if="credit">
      <view class="row between">
        <view class="label">授信额度</view>
        <view>{{ credit.unlimited ? '不限额' : '¥' + money(credit.creditLimit) }}</view>
      </view>
      <view class="row between row-gap">
        <view class="label">已用 / 在途</view>
        <view>¥{{ money(credit.creditUsed) }} / ¥{{ money(credit.pendingAmount) }}</view>
      </view>
      <view class="row between row-gap">
        <view class="label">可用额度</view>
        <view class="price">{{ credit.unlimited ? '—' : '¥' + money(credit.available) }}</view>
      </view>
      <view v-if="credit.message" class="muted warn">{{ credit.message }}</view>
    </view>

    <view class="card" v-if="aging.length">
      <view class="label">逾期账龄</view>
      <view class="aging">
        <view v-for="b in aging" :key="b.bucket" class="bucket">
          <view class="bucket-amount">¥{{ money(b.amount) }}</view>
          <view class="muted">{{ b.bucket }} 天</view>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="label">应收明细</view>
      <view v-if="!items.length" class="empty">暂无未结清应收</view>
      <view v-for="it in items" :key="it.id" class="bill">
        <view class="row between">
          <view class="bill-no">{{ it.bizNo || it.bizType || '-' }}</view>
          <view class="st" :class="{ over: Number(it.overdueDays || 0) > 0 }">
            {{ arStatusText(it.status) }}
          </view>
        </view>
        <view class="row between">
          <view class="muted">应收 ¥{{ money(it.amount) }} · 已结 ¥{{ money(it.settledAmount) }}</view>
          <view class="price">未结 ¥{{ money(it.remainAmount) }}</view>
        </view>
        <view class="muted">
          到期日 {{ it.dueDate || '未设置' }}
          <text v-if="Number(it.overdueDays || 0) > 0"> · 逾期 {{ it.overdueDays }} 天</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { arSummary, type ArItem, type ArSummary } from '@/api'
import { money } from '@/api/request'
import { arStatusText } from '@/utils/status'

const ar = ref<ArSummary | null>(null)
const items = computed<ArItem[]>(() => (ar.value?.items as ArItem[] | undefined) || [])
const aging = computed(() => ar.value?.aging || [])
const overdue = computed(() => Number(ar.value?.overdueRemain || 0))
const credit = computed(() => ar.value?.credit as Record<string, unknown> | undefined)

async function load() {
  try {
    ar.value = await arSummary()
  } catch {
    // 提示已由 request 处理
  }
}

onShow(() => load())
onPullDownRefresh(async () => {
  try {
    await load()
  } finally {
    uni.stopPullDownRefresh()
  }
})
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}
.summary {
  text-align: center;
  padding: 40rpx 24rpx;
  background: linear-gradient(160deg, #fcfbf7, #ffffff 62%);
}
.amount {
  font-size: 52rpx;
  font-weight: 700;
  color: #b85c2e;
  margin-top: 12rpx;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
.overdue {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #b23a2e;
}
.label {
  font-size: 25rpx;
  color: #333b39;
  letter-spacing: 0.5rpx;
}
.row-gap {
  margin-top: 18rpx;
}
.warn {
  margin-top: 16rpx;
  color: #8c6318;
  line-height: 1.5;
}
.aging {
  display: flex;
  margin-top: 16rpx;
}
.bucket {
  flex: 1;
  text-align: center;
  padding: 16rpx 8rpx;
  background: #fcfbf7;
  border: 1rpx solid #ebeae3;
  border-radius: 4rpx;
  margin-right: 12rpx;
}
.bucket:last-child {
  margin-right: 0;
}
.bucket-amount {
  font-size: 24rpx;
  color: #333b39;
  margin-bottom: 6rpx;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
.bill {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #ebeae3;
}
.bill:last-child {
  border-bottom: none;
}
.bill-no {
  font-size: 26rpx;
  font-weight: 600;
  font-family: 'Cascadia Mono', Consolas, monospace;
}
.st {
  font-size: 24rpx;
  color: #8c9693;
}
.st.over {
  color: #b23a2e;
}
</style>
