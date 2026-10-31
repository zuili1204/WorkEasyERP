<template>
  <view class="page">
    <view v-if="orderId" class="card">
      <view class="label">申请售后</view>
      <view class="muted">仅已发货 / 已完成订单可申请，同一订单同时只能有一笔处理中的售后</view>
      <input v-model="amount" class="input mt" type="digit" placeholder="退款金额（留空为订单全额）" />
      <textarea
        v-model="reason"
        class="textarea"
        placeholder="请填写售后原因，例如少发、破损、质量问题"
        maxlength="200"
      />
      <button class="btn btn-primary mt" :disabled="saving" @tap="submit">
        {{ saving ? '提交中…' : '提交申请' }}
      </button>
    </view>

    <view v-if="!list.length && !loading" class="empty">暂无售后记录</view>

    <view v-for="r in list" :key="r.id" class="card">
      <view class="row between">
        <view class="refund-no">{{ r.refundNo }}</view>
        <view class="st" :class="r.status">{{ refundStatusText(r.status) }}</view>
      </view>
      <view class="muted mt8">订单 {{ r.orderNo }}</view>
      <view class="reason">{{ r.reason }}</view>
      <view class="row between mt8">
        <view class="muted">{{ r.createdAt }}</view>
        <view class="price">¥{{ money(r.amount) }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { afterSales, applyAfterSales, type RefundRow } from '@/api'
import { money } from '@/api/request'
import { refundStatusText } from '@/utils/status'

const orderId = ref('')
const reason = ref('')
const amount = ref('')
const list = ref<RefundRow[]>([])
const loading = ref(false)
const saving = ref(false)

onLoad((options) => {
  orderId.value = (options?.orderId as string) || ''
})

onShow(() => load())

async function load() {
  loading.value = true
  try {
    list.value = await afterSales()
  } catch {
    // 提示已由 request 处理
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!orderId.value) return
  const text = reason.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写售后原因', icon: 'none' })
    return
  }
  const amt = amount.value.trim()
  if (amt && !isFinite(Number(amt))) {
    uni.showToast({ title: '退款金额格式不正确', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await applyAfterSales(orderId.value, text, amt || undefined)
    reason.value = ''
    amount.value = ''
    uni.showToast({ title: '已提交，等待商户审核', icon: 'none' })
    orderId.value = ''
    await load()
  } catch {
    // 提示已由 request 处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}
.label {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}
.mt {
  margin-top: 20rpx;
}
.mt8 {
  margin-top: 8rpx;
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 160rpx;
  background: #fbfaf6;
  border: 1rpx solid #dddcd3;
  border-radius: 6rpx;
  padding: 18rpx 20rpx;
  font-size: 28rpx;
  margin-top: 20rpx;
}
.refund-no {
  font-size: 27rpx;
  font-weight: 600;
  font-family: 'Cascadia Mono', Consolas, monospace;
}
.st {
  font-size: 24rpx;
}
.st.applied {
  color: #b8842a;
}
.st.approved {
  color: #3f7a58;
}
.st.rejected {
  color: #b23a2e;
}
.st.refunded {
  color: #3a6772;
}
.reason {
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #616b68;
  line-height: 1.5;
}
</style>
