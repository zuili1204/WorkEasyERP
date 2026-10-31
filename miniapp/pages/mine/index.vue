<template>
  <view class="page">
    <view class="card head">
      <view class="row">
        <view class="avatar">{{ initial }}</view>
        <view class="u-info">
          <view class="name">{{ session.nickname || '微信会员' }}</view>
          <view class="muted">
            {{ session.customer?.name || '未加入商户' }}
            <text v-if="session.tenantName"> · {{ session.tenantName }}</text>
          </view>
          <view class="muted" @tap="editPhone">{{ member?.phone || '绑定手机号' }}</view>
        </view>
        <view v-if="session.customer?.level" class="tag">{{ session.customer?.level }} 级</view>
      </view>
    </view>

    <view v-if="!joined" class="card">
      <view class="muted">尚未加入商户，无法查看订单与对账单</view>
      <button class="btn btn-primary mt" @tap="goTenant">选择商户</button>
    </view>

    <block v-else>
      <view class="card">
        <view class="row between" @tap="goOrders('')">
          <view class="section-title">我的订单</view>
          <view class="muted">全部订单 ›</view>
        </view>
        <view class="quick">
          <view v-for="q in quick" :key="q.key" class="q" @tap="goOrders(q.key)">
            <view class="q-label">{{ q.label }}</view>
          </view>
        </view>
      </view>

      <view class="card menu">
        <view v-for="m in menus" :key="m.url" class="m-row" @tap="go(m.url)">
          <view>
            <view class="m-label">{{ m.label }}</view>
            <view class="muted">{{ m.desc }}</view>
          </view>
          <view class="muted">›</view>
        </view>
      </view>

      <view class="card" v-if="config.servicePhone">
        <view class="row between">
          <view class="muted">客服电话</view>
          <view @tap="callService">{{ config.servicePhone }}</view>
        </view>
      </view>
    </block>

    <button class="btn btn-danger logout" @tap="logout">退出登录</button>
    <view class="muted footer">WorkEasy 订货商城</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { bindPhone, me as fetchMe, publicConfig, type MemberInfo, type PublicConfig } from '@/api'
import { clearAll, isJoined, session } from '@/stores/session'

const member = ref<MemberInfo | null>(null)
const config = ref<PublicConfig>({})
const joined = computed(() => isJoined())
const initial = computed(() => (session.nickname || session.customer?.name || '会').slice(0, 1))

const quick = [
  { key: 'pending_pay', label: '待支付' },
  { key: 'pending_ship', label: '待发货' },
  { key: 'shipped', label: '已发货' },
  { key: 'completed', label: '已完成' },
]

const menus = [
  { label: '我的订单', desc: '全部订单与物流状态', url: '/order/list' },
  { label: '对账单', desc: '应收余额与账龄', url: '/mine/statement' },
  { label: '售后服务', desc: '退款申请与审核进度', url: '/mine/after-sales' },
  { label: '收货地址', desc: '管理默认收货地址', url: '/mine/address/list' },
  { label: '切换商户', desc: '切换后购物车与订单相互隔离', url: '/pages/tenant/select' },
]

onShow(() => {
  if (!session.platformToken) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  fetchMe()
    .then((m) => {
      member.value = m
    })
    .catch(() => {})
  if (joined.value) {
    publicConfig()
      .then((c) => {
        config.value = c || {}
      })
      .catch(() => {})
  }
})

function editPhone() {
  uni.showModal({
    title: '绑定手机号',
    editable: true,
    placeholderText: '请输入手机号',
    success: async (r) => {
      if (!r.confirm) return
      const phone = (r.content || '').trim()
      if (!phone) return
      try {
        member.value = await bindPhone(phone)
        uni.showToast({ title: '已绑定', icon: 'none' })
      } catch {
        // 提示已由 request 处理
      }
    },
  })
}

function goOrders(status: string) {
  uni.navigateTo({ url: `/order/list${status ? '?status=' + status : ''}` })
}

function go(url: string) {
  uni.navigateTo({ url })
}

function goTenant() {
  uni.navigateTo({ url: '/pages/tenant/select' })
}

function callService() {
  if (config.value.servicePhone) uni.makePhoneCall({ phoneNumber: config.value.servicePhone })
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后需重新微信登录并选择商户',
    success: (r) => {
      if (!r.confirm) return
      clearAll()
      uni.reLaunch({ url: '/pages/login/index' })
    },
  })
}
</script>

<style scoped>
.page {
  padding-bottom: 60rpx;
}
/* 会员卡：墨青底 + 网格 + 铜色头像，跟 ERP 侧栏同一套语言 */
.card.head {
  background: #0d1213;
  border-color: #1c2526;
  color: #f2f0e9;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1rpx, transparent 1rpx),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1rpx, transparent 1rpx);
  background-size: 40rpx 40rpx;
}
.head .avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 4rpx;
  background: linear-gradient(150deg, #d98b5f, #b85c2e 60%, #9c4a20);
  color: #fff;
  font-size: 40rpx;
  line-height: 96rpx;
  text-align: center;
  margin-right: 20rpx;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.18);
}
.head .name { color: #fff; }
.head .muted { color: #a9b4b1; }
.head .tag {
  background: rgba(184, 92, 46, 0.18);
  border-color: rgba(184, 92, 46, 0.42);
  color: #e6b394;
}
.u-info {
  flex: 1;
}
.name {
  font-size: 34rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}
.mt {
  margin-top: 20rpx;
}
.section-title {
  font-size: 30rpx;
  font-weight: 600;
}
.quick {
  display: flex;
  margin-top: 24rpx;
}
.q {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  background: #fbfaf6;
  border: 1rpx solid #ebeae3;
  border-radius: 4rpx;
  margin-right: 12rpx;
}
.q:last-child {
  margin-right: 0;
}
.q-label {
  font-size: 25rpx;
  color: #333b39;
}
.menu {
  padding-top: 8rpx;
  padding-bottom: 8rpx;
}
.m-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #ebeae3;
}
.m-row:last-child {
  border-bottom: none;
}
.m-label {
  font-size: 28rpx;
  margin-bottom: 4rpx;
}
.logout {
  margin: 24rpx 20rpx 0;
}
.footer {
  text-align: center;
  margin-top: 32rpx;
}
</style>
