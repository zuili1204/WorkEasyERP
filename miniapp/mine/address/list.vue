<template>
  <view class="page">
    <view v-if="!list.length && !loading" class="empty">还没有收货地址</view>

    <view v-for="a in list" :key="a.id" class="card addr" @tap="choose(a)">
      <view class="row between">
        <view class="row">
          <view class="name">{{ a.receiver }}</view>
          <view class="muted phone">{{ a.phone }}</view>
          <view v-if="isDefault(a)" class="tag">默认</view>
        </view>
        <view v-if="selecting" class="select">选择</view>
      </view>
      <view class="muted detail">{{ fullAddress(a) }}</view>
      <view class="row actions">
        <view v-if="!isDefault(a)" class="act" @tap.stop="setDefault(a)">设为默认</view>
        <view class="act" @tap.stop="edit(a)">编辑</view>
        <view class="act del" @tap.stop="remove(a)">删除</view>
      </view>
    </view>

    <view class="bar">
      <button class="btn btn-primary bar-btn" @tap="create">新增收货地址</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { addresses, deleteAddress, updateAddress, type AddressItem } from '@/api'

const list = ref<AddressItem[]>([])
const loading = ref(false)
const mode = ref('')

const selecting = computed(() => mode.value === 'select')

onLoad((options) => {
  mode.value = (options?.mode as string) || ''
})

onShow(() => {
  load()
})

async function load() {
  loading.value = true
  try {
    list.value = await addresses()
  } catch {
    // 提示已由 request 处理
  } finally {
    loading.value = false
  }
}

function isDefault(a: AddressItem) {
  return a.isDefault === true || a.isDefault === 'true'
}

function fullAddress(a: AddressItem) {
  return (a.province || '') + (a.city || '') + (a.district || '') + (a.detail || '')
}

function choose(a: AddressItem) {
  if (!selecting.value) return
  uni.setStorageSync('we_mall_selected_address', a.id)
  uni.navigateBack()
}

/** 地址接口为全量更新，设为默认需带上完整字段 */
async function setDefault(a: AddressItem) {
  try {
    await updateAddress(a.id, {
      receiver: a.receiver,
      phone: a.phone,
      province: a.province || null,
      city: a.city || null,
      district: a.district || null,
      detail: a.detail,
      isDefault: true,
    })
    uni.showToast({ title: '已设为默认', icon: 'none' })
    load()
  } catch {
    // 提示已由 request 处理
  }
}

function edit(a: AddressItem) {
  uni.setStorageSync('we_mall_edit_address', a)
  uni.navigateTo({ url: `/mine/address/edit?id=${a.id}` })
}

function create() {
  uni.removeStorageSync('we_mall_edit_address')
  uni.navigateTo({ url: '/mine/address/edit' })
}

function remove(a: AddressItem) {
  uni.showModal({
    title: '删除地址',
    content: `确定删除「${a.receiver}」的收货地址？`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        await deleteAddress(a.id)
        load()
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}
</script>

<style scoped>
.page {
  padding-bottom: 160rpx;
}
.name {
  font-size: 30rpx;
  font-weight: 600;
}
.phone {
  margin-left: 16rpx;
}
.select {
  font-size: 24rpx;
  color: #b85c2e;
  font-weight: 600;
}
.detail {
  margin-top: 10rpx;
  line-height: 1.5;
}
.actions {
  justify-content: flex-end;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #ebeae3;
}
.act {
  font-size: 24rpx;
  color: #616b68;
  margin-left: 32rpx;
}
.act.del {
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
  box-shadow: 0 -4rpx 16rpx rgba(19, 26, 25, 0.05);
}
.bar-btn {
  width: 100%;
  margin: 0;
}
</style>
