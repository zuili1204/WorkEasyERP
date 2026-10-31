<template>
  <view class="page">
    <view class="card">
      <view class="row between">
        <view>
          <view class="h1">选择商户</view>
          <view class="muted">{{ session.nickname || '微信会员' }} · 加入后可见专属价格</view>
        </view>
        <button class="mini" @tap="logout">退出登录</button>
      </view>
    </view>

    <view v-for="t in tenants" :key="t.tenantCode" class="card tenant" @tap="enter(t)">
      <view class="row between">
        <view class="row">
          <view class="avatar">{{ t.name.slice(0, 1) }}</view>
          <view class="t-info">
            <view class="name">{{ t.name }}</view>
            <view class="muted">{{ t.tenantCode }}</view>
          </view>
        </view>
        <view v-if="t.joined" class="tag">已加入</view>
        <view v-else-if="t.bindStatus === 'pending'" class="tag tag-warn">待审核</view>
        <view v-else class="tag tag-ghost">去加入</view>
      </view>
    </view>

    <view v-if="!tenants.length" class="empty">{{ loading ? '加载中…' : '暂无可加入的商户' }}</view>

    <view class="card">
      <view class="label">商户编码</view>
      <input v-model="manualCode" class="input" placeholder="输入商户编码加入" />
      <button class="btn btn-primary join-btn" @tap="joinManual">加入商户</button>
      <button class="btn btn-ghost" @tap="scan">扫码加入</button>
      <view class="muted tip">有邀请码时在弹窗中填写；无邀请码需商户在后台审核绑定客户后才可下单</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { joinTenant, myTenants, tenantToken, type TenantOption } from '@/api'
import { session, clearAll, setTenant, setMallToken } from '@/stores/session'

const tenants = ref<TenantOption[]>([])
const loading = ref(false)
const manualCode = ref('')
const entering = ref('')

function backToLogin() {
  uni.reLaunch({ url: '/pages/login/index' })
}

async function load() {
  if (!session.platformToken) {
    backToLogin()
    return
  }
  loading.value = true
  try {
    tenants.value = await myTenants()
  } catch {
    // 提示已由 request 处理
  } finally {
    loading.value = false
  }
}

onShow(() => {
  load()
})

/** 进入租户：签发租户域令牌，切换租户会清空上一家的购物车/地址缓存 */
async function openTenant(code: string) {
  if (entering.value) return
  entering.value = code
  try {
    const res = await tenantToken(code)
    setTenant(res.tenantCode, res.tenantName)
    setMallToken(res.token, {
      id: res.customer.id,
      code: res.customer.code,
      name: res.customer.name,
      level: res.customer.level,
    })
    uni.switchTab({ url: '/pages/index/index' })
  } catch {
    // 提示已由 request 处理（如绑定待审核）
  } finally {
    entering.value = ''
  }
}

async function enter(t: TenantOption) {
  if (t.bindStatus === 'pending' && !t.joined) {
    uni.showModal({
      title: '绑定待审核',
      content: '已提交加入申请，商户审核通过后即可进入下单',
      showCancel: false,
    })
    return
  }
  if (t.joined) {
    await openTenant(t.tenantCode)
    return
  }
  await doJoin(t.tenantCode, t.name)
}

/** 加入租户：邀请码可留空（留空则由商户后台手工绑定客户） */
function doJoin(code: string, name: string) {
  uni.showModal({
    title: `加入 ${name}`,
    editable: true,
    placeholderText: '邀请码（可留空）',
    success: async (r) => {
      if (!r.confirm) return
      const invite = (r.content || '').trim()
      try {
        const res = await joinTenant(code, invite || undefined)
        if (res.status === 'active') {
          await openTenant(code)
        } else {
          uni.showModal({
            title: '已提交申请',
            content: '绑定待商户审核，审核通过后重新进入即可查看专属价格',
            showCancel: false,
            success: () => load(),
          })
        }
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}

async function joinManual() {
  const code = manualCode.value.trim()
  if (!code) {
    uni.showToast({ title: '请输入商户编码', icon: 'none' })
    return
  }
  const found = tenants.value.find((t) => t.tenantCode === code)
  if (found) {
    await enter(found)
    return
  }
  await doJoin(code, code)
}

function scan() {
  uni.scanCode({
    success: (r) => {
      const raw = (r.result || '').trim()
      if (!raw) return
      const code = parseTenantCode(raw)
      manualCode.value = code
      const found = tenants.value.find((t) => t.tenantCode === code)
      if (found) enter(found)
      else doJoin(code, code)
    },
  })
}

/** 支持扫码内容为纯编码，或带 ?tenantCode=xxx 的链接 */
function parseTenantCode(raw: string): string {
  const m = raw.match(/tenantCode=([^&]+)/)
  if (m && m[1]) return decodeURIComponent(m[1])
  return raw.split('/').pop() || raw
}

function logout() {
  clearAll()
  backToLogin()
}
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}
.h1 {
  font-size: 34rpx;
  font-weight: 700;
}
.mini {
  font-size: 22rpx;
  padding: 6rpx 18rpx;
  line-height: 1.6;
  background: #f7ece3;
  color: #9c4a20;
  border: 1rpx solid #ead3c3;
  border-radius: 4rpx;
  margin: 0;
}
.tenant {
  margin-top: 0;
}
.avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 4rpx;
  background: #f7ece3;
  border: 1rpx solid #ead3c3;
  color: #9c4a20;
  font-size: 30rpx;
  line-height: 76rpx;
  text-align: center;
  margin-right: 20rpx;
}
.t-info {
  flex: 1;
}
.name {
  font-size: 30rpx;
  font-weight: 600;
}
.tag-warn {
  background: #f7eedd;
  border-color: #eadbb8;
  color: #8c6318;
}
.tag-ghost {
  background: #efeee8;
  border-color: #dfded6;
  color: #5a6462;
}
.label {
  font-size: 25rpx;
  color: #333b39;
  margin-bottom: 12rpx;
}
.join-btn {
  margin-top: 20rpx;
}
.btn-ghost {
  margin-top: 20rpx;
}
.tip {
  margin-top: 20rpx;
  line-height: 1.6;
}
</style>
