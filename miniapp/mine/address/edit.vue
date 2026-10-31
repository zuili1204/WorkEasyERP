<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <view class="label">收货人</view>
        <input v-model="form.receiver" class="input" placeholder="请输入收货人姓名" maxlength="30" />
      </view>
      <view class="field">
        <view class="label">手机号</view>
        <input v-model="form.phone" class="input" type="number" placeholder="请输入手机号" maxlength="11" />
      </view>
      <view class="field">
        <view class="label">省份</view>
        <input v-model="form.province" class="input" placeholder="如 广东省" maxlength="30" />
      </view>
      <view class="field">
        <view class="label">城市</view>
        <input v-model="form.city" class="input" placeholder="如 深圳市" maxlength="30" />
      </view>
      <view class="field">
        <view class="label">区/县</view>
        <input v-model="form.district" class="input" placeholder="如 南山区" maxlength="30" />
      </view>
      <view class="field">
        <view class="label">详细地址</view>
        <input v-model="form.detail" class="input" placeholder="街道、门牌号、楼层等" maxlength="100" />
      </view>
      <view class="row between switch-row">
        <view class="label">设为默认地址</view>
        <switch :checked="form.isDefault" color="#b85c2e" @change="onDefaultChange" />
      </view>
    </view>

    <button class="btn btn-primary save" :disabled="saving" @tap="save">
      {{ saving ? '保存中…' : '保存地址' }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createAddress, updateAddress, type AddressItem } from '@/api'

const id = ref('')
const saving = ref(false)
const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false,
})

onLoad((options) => {
  id.value = (options?.id as string) || ''
  const cached = uni.getStorageSync('we_mall_edit_address') as AddressItem | ''
  if (id.value && cached && typeof cached === 'object' && cached.id === id.value) {
    form.receiver = cached.receiver || ''
    form.phone = cached.phone || ''
    form.province = cached.province || ''
    form.city = cached.city || ''
    form.district = cached.district || ''
    form.detail = cached.detail || ''
    form.isDefault = cached.isDefault === true || cached.isDefault === 'true'
  }
})

function onDefaultChange(e: { detail: { value: boolean } }) {
  form.isDefault = e.detail.value
}

function validate(): string {
  if (!form.receiver.trim()) return '请输入收货人'
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) return '手机号格式不正确'
  if (!form.detail.trim()) return '请输入详细地址'
  return ''
}

async function save() {
  const err = validate()
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }
  saving.value = true
  try {
    const body = {
      receiver: form.receiver.trim(),
      phone: form.phone.trim(),
      province: form.province.trim() || null,
      city: form.city.trim() || null,
      district: form.district.trim() || null,
      detail: form.detail.trim(),
      isDefault: form.isDefault,
    }
    if (id.value) await updateAddress(id.value, body)
    else await createAddress(body)
    uni.removeStorageSync('we_mall_edit_address')
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch {
    // 提示已由 request 处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page {
  padding-bottom: 60rpx;
}
.field {
  margin-bottom: 24rpx;
}
.label {
  font-size: 25rpx;
  color: #333b39;
  margin-bottom: 10rpx;
  letter-spacing: 0.5rpx;
}
.switch-row {
  padding: 20rpx 0 4rpx;
  border-top: 1rpx solid #ebeae3;
}
.switch-row .label {
  margin-bottom: 0;
}
.save {
  margin: 20rpx;
}
</style>
