<template>
  <view class="page">
    <view class="tabs">
      <view
        v-for="t in ORDER_TABS"
        :key="t.key"
        class="tab"
        :class="{ active: status === t.key }"
        @tap="pick(t.key)"
      >
        {{ t.label }}
      </view>
    </view>

    <view v-if="!list.length && !loading" class="empty">暂无订单</view>

    <view v-for="o in list" :key="o.id" class="card order" @tap="open(o.id)">
      <view class="row between">
        <view class="muted">{{ o.orderNo }}</view>
        <view class="st" :style="{ color: statusColor(o.status) }">{{ statusText(o.status) }}</view>
      </view>
      <view class="summary">{{ o.summary || '-' }}</view>
      <view class="row between">
        <view class="muted">{{ o.itemCount || 0 }} 项 · {{ payTypeText(o.payType) }}</view>
        <view class="price">¥{{ money(o.totalAmount) }}</view>
      </view>
      <view class="muted time">{{ o.createdAt }}</view>
      <view v-if="o.status === 'pending_pay' || o.status === 'shipped'" class="row actions">
        <button v-if="o.status === 'pending_pay'" class="btn btn-ghost act" @tap.stop="cancel(o)">取消订单</button>
        <button v-if="o.status === 'pending_pay'" class="btn btn-primary act" @tap.stop="pay(o)">去支付</button>
        <button v-if="o.status === 'shipped'" class="btn btn-primary act" @tap.stop="confirm(o)">确认收货</button>
      </view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="list.length && !hasMore" class="empty">没有更多了</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { cancelOrder, confirmReceipt, mockPay, orders, type OrderRow } from '@/api'
import { money } from '@/api/request'
import { ORDER_TABS, payTypeText, statusColor, statusText } from '@/utils/status'

const status = ref('')
const list = ref<OrderRow[]>([])
const page = ref(1)
const size = 20
const loading = ref(false)
const hasMore = ref(true)

onLoad((options) => {
  status.value = (options?.status as string) || ''
})

onShow(() => {
  load(true)
})

onPullDownRefresh(async () => {
  try {
    await load(true)
  } finally {
    uni.stopPullDownRefresh()
  }
})

onReachBottom(() => {
  if (!hasMore.value || loading.value) return
  page.value += 1
  load(false)
})

function pick(s: string) {
  if (status.value === s) return
  status.value = s
  load(true)
}

async function load(reset: boolean) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      page.value = 1
      hasMore.value = true
    }
    const res = await orders(status.value || undefined, page.value, size)
    list.value = reset ? res.list : list.value.concat(res.list)
    hasMore.value = list.value.length < res.total
  } catch {
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

function open(id: string) {
  uni.navigateTo({ url: `/order/detail?id=${id}` })
}

async function pay(o: OrderRow) {
  try {
    await mockPay(o.id)
    uni.showToast({ title: '支付成功', icon: 'none' })
    load(true)
  } catch {
    // 提示已由 request 处理（真实环境请在详情页唤起微信支付）
  }
}

function cancel(o: OrderRow) {
  uni.showModal({
    title: '取消订单',
    content: '取消后已锁定的库存将释放，确定取消？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await cancelOrder(o.id)
        uni.showToast({ title: '已取消', icon: 'none' })
        load(true)
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}

function confirm(o: OrderRow) {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到货物？确认后订单完成',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await confirmReceipt(o.id)
        uni.showToast({ title: '已确认收货', icon: 'none' })
        load(true)
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}
.tabs {
  display: flex;
  background: #fff;
  padding: 0 10rpx;
  border-bottom: 1rpx solid #dddcd3;
  position: sticky;
  top: 0;
  z-index: 2;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 22rpx 0;
  font-size: 26rpx;
  color: #616b68;
  border-bottom: 3rpx solid transparent;
}
.tab.active {
  color: #b85c2e;
  font-weight: 600;
  border-bottom-color: #b85c2e;
}
.order {
  margin: 12rpx 20rpx;
}
.st {
  font-size: 26rpx;
  font-weight: 600;
}
.summary {
  margin: 16rpx 0 12rpx;
  font-size: 28rpx;
  line-height: 1.5;
}
.time {
  margin-top: 10rpx;
}
.actions {
  justify-content: flex-end;
  margin-top: 16rpx;
}
.act {
  width: 180rpx;
  margin: 0 0 0 16rpx;
  padding: 12rpx 0;
  font-size: 26rpx;
}
</style>
