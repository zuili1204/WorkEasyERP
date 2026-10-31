<template>
  <view class="page">
    <view v-if="!joined" class="card">
      <view class="h1">尚未选择商户</view>
      <view class="muted">加入商户后购物车才会生效</view>
      <button class="btn btn-primary mt" @tap="goTenant">选择商户</button>
    </view>

    <block v-else>
      <view class="cart-head">
        <text class="kicker">CART / 购物车</text>
        <text class="muted">{{ list.length }} 件商品</text>
      </view>

      <view v-if="!list.length && !loading" class="empty">购物车还是空的，去首页选购吧</view>

      <view v-for="it in list" :key="it.id" class="card item" :class="{ dim: !selected.includes(it.id) }">
        <view class="row">
          <view class="check" :class="{ on: selected.includes(it.id) }" @tap.stop="toggle(it.id)">
            {{ selected.includes(it.id) ? '✓' : '' }}
          </view>
          <image v-if="it.imageUrl" class="thumb" :src="it.imageUrl" mode="aspectFill" @tap="openProduct(it.productId)" />
          <view v-else class="thumb thumb-empty">无图</view>

          <view class="info">
            <view class="name" @tap="openProduct(it.productId)">{{ it.name }}</view>
            <view class="muted">{{ it.spec || '常规规格' }} · {{ it.unit }}</view>
            <view v-if="!it.online" class="muted warn">商品已下架</view>
            <view v-else-if="!it.stockEnough" class="muted warn">库存不足</view>
            <view class="row between bottom">
              <view class="price">¥{{ money(it.price) }}<text class="unit">/{{ it.unit }}</text></view>
              <view class="stepper">
                <view class="step" @tap.stop="changeQty(it, -1)">-</view>
                <view class="step-val">{{ qtyText(it.qty) }}</view>
                <view class="step" @tap.stop="changeQty(it, 1)">+</view>
              </view>
            </view>
            <view class="row between">
              <view class="muted">小计 ¥{{ money(it.amount) }}</view>
              <view class="del" @tap.stop="remove(it)">删除</view>
            </view>
          </view>
        </view>
      </view>

      <view v-if="list.length" class="bar">
        <view class="all" @tap="toggleAll">
          <view class="check" :class="{ on: allSelected }">{{ allSelected ? '✓' : '' }}</view>
          <text>全选</text>
        </view>
        <view class="sum">
          <view class="muted">已选 {{ selected.length }} 项</view>
          <view class="price">¥{{ money(totalAmount) }}</view>
        </view>
        <button class="btn btn-primary bar-btn" :disabled="!canCheckout" @tap="checkout">去结算</button>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { cart as fetchCart, removeCart, updateCartQty, type CartItem } from '@/api'
import { money, qtyText } from '@/api/request'
import { isJoined } from '@/stores/session'

const joined = computed(() => isJoined())
const list = ref<CartItem[]>([])
const selected = ref<string[]>([])
const loading = ref(false)

const selectedRows = computed(() => list.value.filter((i) => selected.value.includes(i.id)))
const totalAmount = computed(() => selectedRows.value.reduce((a, i) => a + Number(i.amount || 0), 0))
const allSelected = computed(() => list.value.length > 0 && selected.value.length === list.value.length)
const canCheckout = computed(
 () => selectedRows.value.length > 0 && selectedRows.value.every((i) => i.online && i.stockEnough),
)

async function load() {
  if (!joined.value) {
    list.value = []
    selected.value = []
    return
  }
  loading.value = true
  try {
    list.value = await fetchCart()
    const ids = list.value.map((i) => i.id)
    // 保留已勾选；首次进入默认勾选可下单的商品
    if (selected.value.length) {
      selected.value = selected.value.filter((id) => ids.includes(id))
    } else {
      selected.value = list.value.filter((i) => i.online && i.stockEnough).map((i) => i.id)
    }
  } catch {
    // 提示已由 request 处理
  } finally {
    loading.value = false
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

function toggle(id: string) {
  const i = selected.value.indexOf(id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(id)
}

function toggleAll() {
  selected.value = allSelected.value ? [] : list.value.map((i) => i.id)
}

async function changeQty(it: CartItem, dir: number) {
  const step = Math.max(1, Number(it.stepQty || 1))
  const min = Math.max(1, Number(it.minOrderQty || 1))
  const next = Number(it.qty) + dir * step
  if (next < min) {
    uni.showToast({ title: `最少起订 ${min}${it.unit || ''}`, icon: 'none' })
    return
  }
  if (next > Number(it.availableQty || 0)) {
    uni.showToast({ title: '可售数量不足', icon: 'none' })
    return
  }
  try {
    await updateCartQty(it.id, next)
    await load()
  } catch {
    // 提示已由 request 处理
  }
}

function remove(it: CartItem) {
  uni.showModal({
    title: '移除商品',
    content: `确定从购物车移除「${it.name}」？`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        await removeCart(it.id)
        await load()
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}

function checkout() {
  if (!canCheckout.value) return
  uni.setStorageSync('we_mall_checkout', {
    items: selectedRows.value.map((i) => ({ productId: i.productId, qty: i.qty })),
    fromCart: true,
  })
  uni.navigateTo({ url: '/order/confirm' })
}

function openProduct(id: string) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

function goTenant() {
  uni.navigateTo({ url: '/pages/tenant/select' })
}
</script>

<style scoped>
.page {
  /* H5 的 tabBar 在页面内渲染，底部留出结算条 + tabBar 高度 */
  padding-bottom: calc(200rpx + var(--window-bottom, 0px));
}
.mt {
  margin-top: 24rpx;
}
.cart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx 0;
}
.item.dim {
  opacity: 0.62;
}
/* 勾选框：方角，像工程清单上的打钩格 */
.check {
  width: 38rpx;
  height: 38rpx;
  border-radius: 4rpx;
  border: 1rpx solid #c9c7bc;
  background: #fff;
  color: #fff;
  font-size: 22rpx;
  line-height: 36rpx;
  text-align: center;
  flex-shrink: 0;
  margin-right: 16rpx;
}
.check.on {
  background: #b85c2e;
  border-color: #9c4a20;
}
.thumb {
  width: 148rpx;
  height: 148rpx;
  border-radius: 4rpx;
  background: #faf9f5;
  border: 1rpx solid #ebeae3;
  flex-shrink: 0;
}
.thumb-empty {
  font-size: 22rpx;
  color: #8c9693;
  text-align: center;
  line-height: 148rpx;
}
.info {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}
.name {
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.4;
}
.bottom {
  margin: 12rpx 0;
}
.unit {
  font-size: 22rpx;
  color: #8c9693;
  font-weight: 400;
}
.warn {
  color: #b23a2e;
}
.stepper {
  display: flex;
  align-items: center;
  border: 1rpx solid #dddcd3;
  border-radius: 4rpx;
  overflow: hidden;
}
.step {
  width: 56rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  background: #faf9f5;
  color: #333b39;
  font-size: 28rpx;
}
.step-val {
  min-width: 70rpx;
  text-align: center;
  font-size: 26rpx;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
.del {
  font-size: 24rpx;
  color: #8c9693;
}
.bar {
  position: fixed;
  left: 0;
  right: 0;
  /* 微信小程序为 0，H5 需抬到 tabBar 之上 */
  bottom: var(--window-bottom, 0px);
  background: #fff;
  border-top: 1rpx solid #dddcd3;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  box-shadow: 0 -4rpx 16rpx rgba(19, 26, 25, 0.05);
}
.all {
  display: flex;
  align-items: center;
  font-size: 26rpx;
  color: #333b39;
}
.sum {
  flex: 1;
  text-align: right;
  margin-right: 20rpx;
}
.sum .price {
  font-size: 34rpx;
}
.bar-btn {
  width: 220rpx;
  margin: 0;
}
</style>
