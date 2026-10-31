<template>
  <view class="page">
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!item" class="empty">商品不存在或已下架</view>

    <block v-else>
      <swiper v-if="images.length" class="gallery" indicator-dots circular>
        <swiper-item v-for="(url, i) in images" :key="i">
          <image class="gallery-img" :src="url" mode="aspectFill" />
        </swiper-item>
      </swiper>
      <view v-else class="gallery gallery-empty">暂无图片</view>

      <view class="card">
        <view class="row between">
          <view class="price big">¥{{ money(item.price) }}<text class="unit">/{{ item.unit }}</text></view>
          <view v-if="item.source === 'contract'" class="tag">协议价</view>
          <view v-else-if="item.source === 'level'" class="tag">等级价</view>
        </view>
        <view class="title">{{ item.onlineTitle || item.name }}</view>
        <view class="muted">{{ item.spec || '常规规格' }}<text v-if="item.brand"> · {{ item.brand }}</text></view>
        <view class="row between meta">
          <view class="muted">可售 {{ qtyText(item.availableQty) }}{{ item.unit }}</view>
          <view v-if="Number(item.minOrderQty || 1) > 1" class="muted">起订 {{ qtyText(item.minOrderQty) }}{{ item.unit }}</view>
        </view>
        <view v-if="Number(item.auxUnit || 0) === 0 && item.auxUnit" class="muted">
          {{ item.auxUnit }} 换算 {{ qtyText(item.convertRate) }}
        </view>
        <view v-if="item.onlineDesc" class="desc">{{ item.onlineDesc }}</view>
      </view>

      <view class="card">
        <view class="row between">
          <view class="label">购买数量（{{ item.unit }}）</view>
          <view class="stepper">
            <view class="step" @tap="dec">-</view>
            <input class="step-input" type="number" :value="String(qty)" @blur="onQtyBlur" />
            <view class="step" @tap="inc">+</view>
          </view>
        </view>
        <view v-if="available <= 0" class="muted warn">当前库存不足，暂不可下单</view>
      </view>

      <view class="bar">
        <view class="bar-cart" @tap="openCart">购物车</view>
        <button class="btn btn-ghost bar-btn" :disabled="!canBuy" @tap="addToCart">加入购物车</button>
        <button class="btn btn-primary bar-btn" :disabled="!canBuy" @tap="buyNow">立即购买</button>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { addCart, productDetail, type ProductDetail } from '@/api'
import { money, qtyText } from '@/api/request'

const id = ref('')
const item = ref<ProductDetail | null>(null)
const loading = ref(true)
const qty = ref(1)

const images = computed(() => {
  const list = item.value?.images?.map((i) => i.url) ?? []
  if (list.length) return list
  return item.value?.imageUrl ? [item.value.imageUrl] : []
})
const minQty = computed(() => Math.max(1, Number(item.value?.minOrderQty || 1)))
const stepQty = computed(() => Math.max(1, Number(item.value?.stepQty || 1)))
const available = computed(() => Number(item.value?.availableQty || 0))
const canBuy = computed(() => !!item.value && available.value > 0)

onLoad((options) => {
  id.value = (options?.id as string) || ''
  load()
})

async function load() {
  loading.value = true
  try {
    item.value = await productDetail(id.value)
    qty.value = minQty.value
  } catch {
    item.value = null
  } finally {
    loading.value = false
  }
}

/** 数量按起订量与步进对齐，并限制在可售范围内 */
function normalize(v: number): number {
  if (!isFinite(v) || v <= 0) return minQty.value
  const base = minQty.value
  const s = stepQty.value
  const aligned = base + Math.round((v - base) / s) * s
  const capped = Math.min(aligned, Math.max(available.value, base))
  return Math.max(base, capped)
}

function inc() {
  const next = qty.value + stepQty.value
  if (next > available.value) {
    uni.showToast({ title: '可售数量不足', icon: 'none' })
    return
  }
  qty.value = next
}

function dec() {
  const next = qty.value - stepQty.value
  if (next < minQty.value) {
    uni.showToast({ title: `最少起订 ${minQty.value}`, icon: 'none' })
    return
  }
  qty.value = next
}

function onQtyBlur(e: { detail: { value: string } }) {
  qty.value = normalize(Number(e.detail.value))
}

async function addToCart() {
  if (!canBuy.value) return
  try {
    await addCart(id.value, qty.value)
    uni.showToast({ title: '已加入购物车', icon: 'none' })
  } catch {
    // 提示已由 request 处理
  }
}

function buyNow() {
  if (!canBuy.value) return
  uni.setStorageSync('we_mall_checkout', {
    items: [{ productId: id.value, qty: qty.value }],
    fromCart: false,
  })
  uni.navigateTo({ url: '/order/confirm' })
}

function openCart() {
  uni.switchTab({ url: '/pages/cart/index' })
}
</script>

<style scoped>
.page {
  padding-bottom: 140rpx;
}
.gallery {
  height: 640rpx;
  background: #faf9f5;
  border-bottom: 1rpx solid #ebeae3;
}
.gallery-img {
  width: 100%;
  height: 640rpx;
}
.gallery-empty {
  line-height: 640rpx;
  text-align: center;
  color: #8c9693;
}
.price.big {
  font-size: 44rpx;
}
.unit {
  font-size: 24rpx;
  color: #8c9693;
  font-weight: 400;
}
.title {
  margin-top: 16rpx;
  font-size: 34rpx;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0.5rpx;
}
.meta {
  margin-top: 16rpx;
}
.desc {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #ebeae3;
  font-size: 26rpx;
  color: #333b39;
  line-height: 1.7;
  white-space: pre-wrap;
}
.label {
  font-size: 28rpx;
  color: #333b39;
}
.stepper {
  display: flex;
  align-items: center;
  border: 1rpx solid #dddcd3;
  border-radius: 4rpx;
  overflow: hidden;
}
.step {
  width: 64rpx;
  height: 60rpx;
  line-height: 60rpx;
  text-align: center;
  background: #faf9f5;
  color: #333b39;
  font-size: 30rpx;
}
.step-input {
  width: 110rpx;
  height: 60rpx;
  text-align: center;
  font-size: 28rpx;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
.warn {
  margin-top: 16rpx;
  color: #b23a2e;
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
.bar-cart {
  width: 120rpx;
  text-align: center;
  font-size: 24rpx;
  color: #9c4a20;
}
.bar-btn {
  flex: 1;
  margin-left: 16rpx;
  margin-right: 0;
}
</style>
