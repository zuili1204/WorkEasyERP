<template>
  <view class="page">
    <view v-if="!detail" class="empty">{{ loading ? '加载中…' : '订单不存在' }}</view>

    <block v-else>
      <view class="card">
        <view class="row between">
          <view class="st" :style="{ color: statusColor(detail.status) }">{{ statusText(detail.status) }}</view>
          <view class="muted">{{ detail.orderNo }}</view>
        </view>
        <view v-if="detail.status === 'pending_confirm'" class="hint">
          超出授信额度，等待商户经理确认后自动进入待发货
        </view>
        <view v-else-if="detail.status === 'pending_pay'" class="hint">请完成支付，长时间未支付订单可能被关闭</view>
      </view>

      <view class="card">
        <view class="label">收货信息</view>
        <view class="row between">
          <view class="bold">{{ detail.receiver }}</view>
          <view class="muted">{{ detail.receiverPhone }}</view>
        </view>
        <view class="muted addr">{{ detail.address }}</view>
        <view v-if="detail.remark" class="muted">备注：{{ detail.remark }}</view>
      </view>

      <view class="card">
        <view v-for="l in detailItems" :key="l.id || l.productId" class="line">
          <image v-if="l.imageUrl" class="thumb" :src="l.imageUrl" mode="aspectFill" />
          <view class="l-info">
            <view class="l-name">{{ l.productName }}</view>
            <view class="muted">
              {{ l.spec || '常规规格' }} · {{ qtyText(l.qty) }} {{ l.unit }} × ¥{{ money(l.price) }}
            </view>
            <view class="price">¥{{ money(l.amount) }}</view>
          </view>
        </view>
        <view class="row between total">
          <view class="muted">共 {{ qtyText(detail.totalQty) }} 件 · {{ payTypeText(detail.payType) }}</view>
          <view class="price">¥{{ money(detail.totalAmount) }}</view>
        </view>
      </view>

      <view v-if="payments.length" class="card">
        <view class="label">支付记录</view>
        <view v-for="p in payments" :key="p.paymentNo || p.outTradeNo" class="row between pay-row">
          <view class="muted">{{ p.provider === 'offline' ? '线下打款' : '微信支付' }}</view>
          <view>¥{{ money(p.amount) }}</view>
        </view>
      </view>

      <view class="card muted">下单时间 {{ detail.createdAt }}</view>
    </block>

    <view v-if="detail" class="bar">
      <button v-if="detail.status === 'pending_pay'" class="btn btn-ghost bar-btn" @tap="cancel">取消订单</button>
      <button v-if="detail.status === 'pending_pay'" class="btn btn-primary bar-btn" @tap="pay">去支付</button>
      <button v-if="detail.status === 'shipped'" class="btn btn-primary bar-btn" @tap="confirm">确认收货</button>
      <button
        v-if="detail.status === 'shipped' || detail.status === 'completed'"
        class="btn btn-ghost bar-btn"
        @tap="afterSales"
      >
        申请售后
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { cancelOrder, confirmReceipt, mockPay, orderDetail, type OrderDetail, type OrderLine } from '@/api'
import { money, qtyText } from '@/api/request'
import { payTypeText, statusColor, statusText } from '@/utils/status'

const id = ref('')
const detail = ref<OrderDetail | null>(null)
const loading = ref(true)

const detailItems = computed<OrderLine[]>(() => (detail.value?.items as OrderLine[] | undefined) || [])
const payments = computed(() => detail.value?.payments || [])

onLoad((options) => {
  id.value = (options?.id as string) || ''
})

onShow(() => {
  load()
})

async function load() {
  if (!id.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    detail.value = await orderDetail(id.value)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function pay() {
  try {
    await mockPay(id.value)
    uni.showToast({ title: '支付成功', icon: 'none' })
    load()
  } catch {
    // 提示已由 request 处理（真实环境需唤起 JSAPI 支付）
  }
}

function cancel() {
  uni.showModal({
    title: '取消订单',
    content: '取消后已锁定的库存将释放，确定取消？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await cancelOrder(id.value)
        uni.showToast({ title: '已取消', icon: 'none' })
        load()
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}

function confirm() {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到货物？确认后订单完成',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await confirmReceipt(id.value)
        uni.showToast({ title: '已确认收货', icon: 'none' })
        load()
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}

function afterSales() {
  uni.navigateTo({ url: `/mine/after-sales?orderId=${id.value}` })
}
</script>

<style scoped>
.page {
  padding-bottom: 170rpx;
}
.st {
  font-size: 32rpx;
  font-weight: 700;
}
.hint {
  margin-top: 16rpx;
  padding: 14rpx 20rpx;
  background: #f7eedd;
  color: #8c6318;
  font-size: 24rpx;
  border-left: 4rpx solid #b8842a;
  border-radius: 4rpx;
}
.label {
  font-size: 25rpx;
  color: #333b39;
  margin-bottom: 12rpx;
}
.bold {
  font-size: 30rpx;
  font-weight: 600;
}
.addr {
  margin-top: 8rpx;
  line-height: 1.5;
}
.line {
  display: flex;
  padding-bottom: 20rpx;
  margin-bottom: 20rpx;
  border-bottom: 1rpx solid #ebeae3;
}
.thumb {
  width: 120rpx;
  height: 120rpx;
  border-radius: 4rpx;
  background: #faf9f5;
  border: 1rpx solid #ebeae3;
  flex-shrink: 0;
}
.l-info {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}
.l-name {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 6rpx;
}
.total {
  margin-top: 8rpx;
}
.pay-row {
  padding: 10rpx 0;
}
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  border-top: 1rpx solid #dddcd3;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  display: flex;
  justify-content: flex-end;
  box-shadow: 0 -4rpx 16rpx rgba(19, 26, 25, 0.05);
}
.bar-btn {
  width: 200rpx;
  margin: 0 0 0 16rpx;
  padding: 16rpx 0;
  font-size: 27rpx;
}
</style>
