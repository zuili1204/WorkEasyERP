<template>
  <view class="page">
    <view class="card addr" @tap="pickAddress">
      <view v-if="selectedAddress">
        <view class="row between">
          <view class="bold">{{ selectedAddress.receiver }}</view>
          <view class="muted">{{ selectedAddress.phone }}</view>
        </view>
        <view class="muted addr-text">{{ fullAddress(selectedAddress) }}</view>
      </view>
      <view v-else class="muted">请选择收货地址</view>
      <view class="arrow">›</view>
    </view>

    <view class="card" v-if="preview">
      <view v-for="l in lines" :key="l.productId" class="line">
        <image v-if="l.imageUrl" class="thumb" :src="l.imageUrl" mode="aspectFill" />
        <view class="l-info">
          <view class="l-name">{{ l.name || l.productName }}</view>
          <view class="muted">{{ l.spec || '常规规格' }} · {{ qtyText(l.qty) }} {{ l.unit }}</view>
          <view class="row between">
            <view class="muted">¥{{ money(l.price) }}</view>
            <view class="price">¥{{ money(l.amount) }}</view>
          </view>
        </view>
      </view>
      <view class="row between total">
        <view class="muted">合计（含税 {{ qtyText(preview.totalQty) }} 件）</view>
        <view class="price">¥{{ money(preview.totalAmount) }}</view>
      </view>
      <view v-if="minOrderAmount > 0" class="muted">起送金额 ¥{{ money(minOrderAmount) }}</view>
    </view>

    <view class="card" v-if="preview">
      <view class="label">支付方式</view>
      <view
        v-for="o in payOptions"
        :key="o.type"
        class="pay"
        :class="{ active: payType === o.type, off: !o.enabled }"
        @tap="choosePay(o)"
      >
        <view>{{ o.label }}</view>
        <view v-if="!o.enabled" class="muted">未开通</view>
        <view v-else-if="payType === o.type" class="tag">已选</view>
      </view>
      <view v-if="payType === 'credit' && creditMsg" class="muted warn">{{ creditMsg }}</view>
    </view>

    <view class="card">
      <view class="label">订单备注</view>
      <input v-model="remark" class="input" placeholder="选填，如送货时间要求" maxlength="100" />
    </view>

    <view class="bar">
      <view class="sum">
        <view class="muted">应付金额</view>
        <view class="price">¥{{ money(preview?.totalAmount) }}</view>
      </view>
      <button class="btn btn-primary bar-btn" :disabled="!canSubmit" @tap="submit">
        {{ submitting ? '提交中…' : '提交订单' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  addresses,
  createOrder,
  mockPay,
  orderPreview,
  payJsapi,
  type AddressItem,
  type OrderPreview,
  type PayOption,
} from '@/api'
import { money, qtyText } from '@/api/request'

const checkout = ref<{ items: { productId: string; qty: number }[]; fromCart: boolean } | null>(null)
const preview = ref<OrderPreview | null>(null)
const addrList = ref<AddressItem[]>([])
const addressId = ref('')
const payType = ref('wechat')
const remark = ref('')
const submitting = ref(false)

const lines = computed(() => preview.value?.lines || [])
const payOptions = computed<PayOption[]>(() => preview.value?.payOptions || [])
const selectedAddress = computed(() => addrList.value.find((a) => a.id === addressId.value) || null)
const minOrderAmount = computed(() => Number(preview.value?.minOrderAmount || 0))
const canSubmit = computed(() => !!preview.value && !!addressId.value && !submitting.value)

const creditMsg = computed(() => {
  const c = preview.value?.credit as Record<string, unknown> | undefined
  if (!c) return ''
  const action = String(c.action || '')
  const msg = String(c.message || '')
  if (action === 'block') return msg || '超出授信额度，暂不可使用账期支付'
  if (action === 'approval') return msg || '超出授信额度，提交后需商户经理确认放行'
  return ''
})

onLoad(() => {
  const raw = uni.getStorageSync('we_mall_checkout')
  if (!raw || !raw.items || !raw.items.length) {
    uni.showToast({ title: '结算信息已失效', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
    return
  }
  checkout.value = raw
  init()
})

onShow(() => {
  // 地址列表页选择后回填
  const picked = uni.getStorageSync('we_mall_selected_address')
  if (picked && checkout.value) {
    uni.removeStorageSync('we_mall_selected_address')
    addressId.value = picked
    loadPreview()
  }
})

async function init() {
  try {
    addrList.value = await addresses()
    const def = addrList.value.find((a) => a.isDefault === true || a.isDefault === 'true')
    addressId.value = addressId.value || (def?.id || addrList.value[0]?.id || '')
  } catch {
    // 提示已由 request 处理
  }
  loadPreview()
}

async function loadPreview() {
  if (!checkout.value) return
  try {
    const res = await orderPreview(checkout.value.items, addressId.value || undefined, payType.value)
    preview.value = res
    const enabled = (res.payOptions || []).filter((o) => o.enabled).map((o) => o.type)
    if (enabled.length && !enabled.includes(payType.value)) {
      payType.value = enabled.includes('wechat') ? 'wechat' : enabled[0]
    }
  } catch {
    preview.value = null
  }
}

function choosePay(o: PayOption) {
  if (!o.enabled) {
    uni.showToast({ title: '该支付方式未开通', icon: 'none' })
    return
  }
  if (payType.value === o.type) return
  payType.value = o.type
  loadPreview()
}

function pickAddress() {
  uni.navigateTo({ url: '/mine/address/list?mode=select' })
}

function fullAddress(a: AddressItem) {
  return (a.province || '') + (a.city || '') + (a.district || '') + (a.detail || '')
}

async function submit() {
  if (!checkout.value || !canSubmit.value) return
  submitting.value = true
  try {
    const order = await createOrder(checkout.value.items, addressId.value, payType.value, remark.value.trim() || undefined)
    uni.removeStorageSync('we_mall_checkout')
    if (payType.value === 'wechat') {
      await payWechat(order.id)
    }
    uni.redirectTo({ url: `/order/result?id=${order.id}` })
  } catch {
    // 提示已由 request 处理
  } finally {
    submitting.value = false
  }
}

/** mock 支付优先；未开启 mock 时走真实 JSAPI 参数唤起微信支付 */
async function payWechat(orderId: string) {
  try {
    await mockPay(orderId)
    return
  } catch {
    // 继续尝试真实支付
  }
  try {
    const p = await payJsapi(orderId)
    await new Promise<void>((resolve, reject) => {
      uni.requestPayment({
        provider: 'wxpay',
        timeStamp: p.timeStamp,
        nonceStr: p.nonceStr,
        package: p.package,
        signType: (p.signType as 'MD5' | 'HMAC-SHA256' | 'RSA') || 'RSA',
        paySign: p.paySign,
        success: () => resolve(),
        fail: () => reject(new Error('支付未完成')),
      })
    })
  } catch {
    uni.showToast({ title: '支付未完成，可在订单列表继续支付', icon: 'none' })
  }
}
</script>

<style scoped>
.page {
  padding-bottom: 160rpx;
}
.addr {
  position: relative;
  padding-right: 56rpx;
}
.addr-text {
  margin-top: 8rpx;
}
.arrow {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  color: #8c9693;
  font-size: 36rpx;
}
.bold {
  font-size: 30rpx;
  font-weight: 600;
}
.line {
  display: flex;
  padding-bottom: 20rpx;
  margin-bottom: 20rpx;
  border-bottom: 1rpx solid #ebeae3;
}
.thumb {
  width: 128rpx;
  height: 128rpx;
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
  margin-bottom: 8rpx;
}
.total {
  margin-top: 8rpx;
}
.label {
  font-size: 25rpx;
  color: #333b39;
  margin-bottom: 12rpx;
}
.pay {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 20rpx;
  border: 1rpx solid #dddcd3;
  border-radius: 4rpx;
  margin-bottom: 12rpx;
  background: #fff;
}
.pay.active {
  border-color: #b85c2e;
  background: #f7ece3;
}
.pay.off {
  opacity: 0.55;
}
.warn {
  color: #8c6318;
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
  align-items: center;
  box-shadow: 0 -4rpx 16rpx rgba(19, 26, 25, 0.05);
}
.sum {
  flex: 1;
  padding-right: 20rpx;
}
.sum .price {
  font-size: 36rpx;
}
.bar-btn {
  width: 260rpx;
  margin: 0;
}
</style>
