<template>
  <view class="page">
    <view class="hero">
      <view class="logo">订</view>
      <view class="title">订货商城</view>
      <view class="sub">微信授权登录后选择您的商户，即可查看专属价格并下单</view>
    </view>

    <view class="card">
      <view class="label">昵称（选填）</view>
      <input v-model="nickname" class="input" placeholder="用于展示，可留空" maxlength="20" />
      <button class="btn btn-primary login-btn" :loading="loading" @tap="doLogin">
        {{ session.platformToken ? '重新登录' : '微信一键登录' }}
      </button>
      <button v-if="session.platformToken" class="btn btn-ghost" @tap="goTenant">进入选择商户</button>
      <view class="tip">演示环境未接入真实微信授权时会使用模拟 code，正式环境请配置小程序 AppID</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { wxLogin } from '@/api'
import { session, setPlatformToken } from '@/stores/session'

const loading = ref(false)
const nickname = ref(session.nickname)

function fallbackCode(): string {
  return 'mock-' + Date.now()
}

/** 微信登录 code：非微信端（H5 调试）直接走模拟 code */
function getLoginCode(): Promise<string> {
  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: (r) => resolve(r.code || fallbackCode()),
      fail: () => resolve(fallbackCode()),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve(fallbackCode())
    // #endif
  })
}

async function doLogin() {
  if (loading.value) return
  loading.value = true
  try {
    const code = await getLoginCode()
    const res = await wxLogin(code, nickname.value.trim() || undefined)
    setPlatformToken(res.token, res.member?.nickname || nickname.value.trim())
    uni.showToast({ title: res.mock ? '模拟登录成功' : '登录成功', icon: 'none' })
    setTimeout(() => goTenant(), 600)
  } catch {
    // 错误提示已由 request 统一处理
  } finally {
    loading.value = false
  }
}

function goTenant() {
  uni.navigateTo({ url: '/pages/tenant/select' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding-top: 40rpx;
  box-sizing: border-box;
}
/* 品牌头：墨青图纸面板，和应用端登录页同一族视觉 */
.hero {
  margin: 20rpx;
  padding: 44rpx 44rpx 52rpx;
  background: #0d1213;
  border: 1rpx solid #1c2526;
  border-radius: 8rpx;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1rpx, transparent 1rpx),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1rpx, transparent 1rpx),
    radial-gradient(120% 90% at 0% 0%, rgba(184, 92, 46, 0.24), transparent 58%);
  background-size: 40rpx 40rpx, 40rpx 40rpx, 100% 100%;
}
.logo {
  width: 112rpx;
  height: 112rpx;
  border-radius: 4rpx;
  background: linear-gradient(150deg, #d98b5f, #b85c2e 60%, #9c4a20);
  color: #fff;
  font-size: 52rpx;
  line-height: 112rpx;
  text-align: center;
  margin-bottom: 32rpx;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.18);
}
.title {
  font-size: 42rpx;
  font-weight: 700;
  color: #f2f0e9;
  letter-spacing: 1rpx;
}
.sub {
  margin-top: 16rpx;
  font-size: 25rpx;
  color: #a9b4b1;
  line-height: 1.65;
}
.label {
  font-size: 25rpx;
  color: #333b39;
  margin-bottom: 12rpx;
}
.login-btn {
  margin-top: 32rpx;
}
.btn-ghost {
  margin-top: 20rpx;
}
.tip {
  margin-top: 24rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: #8c9693;
}
</style>
