<template>
  <view class="page">
    <view class="card result">
      <view class="icon" :class="ok ? 'ok' : 'warn'">{{ ok ? '✓' : '!' }}</view>
      <view class="title">{{ title }}</view>
      <view class="muted">{{ sub }}</view>
      <view v-if="detail" class="amount">¥{{ money(detail.totalAmount) }}</view>
    </view>

    <view class="card" v-if="detail">
      <view class="row between">
        <view class="muted">订单号</view>
        <view>{{ detail.orderNo }}</view>
      </view>
      <view class="row between row-gap">
        <view class="muted">支付方式</view>
        <view>{{ payTypeText(detail.payType) }}</view>
      </view>
      <view class="row between row-gap">
        <view class="muted">收货信息</view>
        <view class="addr-right">{{ detail.receiver }} {{ detail.receiverPhone }}</view>
      </view>
      <view class="muted addr">{{ detail.address }}</view>
    </view>

    <button class="btn btn-primary mt20" @tap="goDetail">查看订单详情</button>
    <button class="btn btn-ghost" @tap="goList">我的订单</button>
    <button class="btn btn-ghost" @tap="goHome">返回首页</button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { orderDetail, type OrderDetail } from '@/api'
import { money } from '@/api/request'
import { payTypeText, statusText } from '@/utils/status'

const id = ref('')
const detail = ref<OrderDetail | null>(null)

const ok = computed(() => {
  const s = detail.value?.status
  return !!s && s !== 'pending_pay'
})

const title = computed(() => {
  const s = detail.value?.status
  if (!s) return '订单已提交'
  if (s === 'pending_pay') return '支付未完成'
  if (s === 'pending_confirm') return '订单待确认'
  return statusText(s)
})

const sub = computed(() => {
  const s = detail.value?.status
  if (s === 'pending_pay') return '可稍后在订单列表继续支付'
  if (s === 'pending_confirm') return '超出授信额度，商户经理确认后自动进入待发货'
  if (s === 'pending_ship') return '支付成功，商户将尽快为您发货'
  if (s === 'shipped') return '商户已发货，请注意查收'
  if (s === 'completed') return '订单已完成，感谢您的订购'
  return ''
})

onLoad((options) => {
  id.value = (options?.id as string) || ''
  if (!id.value) {
    uni.showToast({ title: '订单不存在', icon: 'none' })
    return
  }
  load()
})

async function load() {
  try {
    detail.value = await orderDetail(id.value)
  } catch {
    // 提示已由 request 处理
  }
}

function goDetail() {
  uni.redirectTo({ url: `/order/detail?id=${id.value}` })
}

function goList() {
  uni.redirectTo({ url: '/order/list' })
}

function goHome() {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style scoped>
.page {
  padding-top: 20rpx;
}
.result {
  text-align: center;
  padding: 48rpx 24rpx;
}
.icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 4rpx;
  margin: 0 auto 24rpx;
  color: #fff;
  font-size: 52rpx;
  line-height: 112rpx;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.2);
}
.icon.ok {
  background: #3f7a58;
}
.icon.warn {
  background: #b8842a;
}
.title {
  font-size: 36rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}
.amount {
  margin-top: 20rpx;
  font-size: 46rpx;
  font-weight: 700;
  color: #b85c2e;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
.row-gap {
  margin-top: 18rpx;
}
.addr-right {
  max-width: 440rpx;
  text-align: right;
}
.addr {
  margin-top: 8rpx;
}
.mt20 {
  margin: 28rpx 20rpx 0;
}
.btn-ghost {
  margin: 20rpx 20rpx 0;
}
</style>
