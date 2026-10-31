<template>
  <view class="page">
    <view v-if="!joined" class="card">
      <view class="h1">尚未选择商户</view>
      <view class="muted">登录并加入商户后即可浏览商品、查看专属价格并下单</view>
      <button class="btn btn-primary mt" @tap="goTenant">选择商户</button>
    </view>

    <block v-else>
      <view class="card">
        <view class="row between">
          <view>
            <view class="kicker">STORE / 商户</view>
            <view class="h1">{{ session.tenantName || session.tenantCode }}</view>
            <view class="muted">
              {{ session.customer?.name || session.nickname }}
              <text v-if="session.customer?.level"> · {{ session.customer?.level }} 级客户</text>
            </view>
          </view>
          <button class="mini" @tap="goTenant">切换商户</button>
        </view>
        <view v-if="config.notice" class="notice">{{ config.notice }}</view>
      </view>

      <swiper v-if="bannerList.length" class="banner" indicator-dots autoplay circular interval="4000">
        <swiper-item v-for="b in bannerList" :key="b.id">
          <image class="banner-img" :src="b.imageUrl" mode="aspectFill" @tap="tapBanner(b)" />
        </swiper-item>
      </swiper>

      <view class="search-wrap">
        <input
          v-model="keyword"
          class="input"
          placeholder="搜索商品名称 / 编码"
          confirm-type="search"
          @confirm="reload"
        />
      </view>

      <scroll-view class="cats" scroll-x>
        <view class="cat" :class="{ active: !activeCat }" @tap="pickCat('')">全部</view>
        <view
          v-for="c in cats"
          :key="c.id"
          class="cat"
          :class="{ active: activeCat === c.id }"
          @tap="pickCat(c.id)"
        >
          {{ c.name }}
        </view>
      </scroll-view>

      <view class="list-head">
        <text class="kicker">PRODUCTS</text>
        <text class="muted">{{ list.length }} 件在售</text>
      </view>

      <view v-for="p in list" :key="p.productId" class="card product" @tap="openProduct(p.productId)">
        <view class="row">
          <image v-if="p.imageUrl" class="thumb" :src="p.imageUrl" mode="aspectFill" />
          <view v-else class="thumb thumb-empty">无图</view>
          <view class="p-info">
            <view class="p-title">{{ p.onlineTitle || p.name }}</view>
            <view class="muted">{{ p.spec || '常规规格' }} · {{ p.unit }}</view>
            <view class="row between p-bottom">
              <view class="price">¥{{ money(p.price) }}<text class="unit">/{{ p.unit }}</text></view>
              <view class="muted">可售 {{ qtyText(p.availableQty) }}</view>
            </view>
            <view v-if="Number(p.minOrderQty || 1) > 1" class="muted">
              起订 {{ qtyText(p.minOrderQty) }}{{ p.unit }}
            </view>
          </view>
        </view>
      </view>

      <view v-if="!list.length && !loading" class="empty">暂无商品</view>
      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="list.length && !hasMore" class="empty">没有更多了</view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  banners,
  categories,
  products,
  publicConfig,
  type BannerItem,
  type ProductItem,
  type PublicConfig,
} from '@/api'
import { money, qtyText } from '@/api/request'
import { isJoined, session } from '@/stores/session'

const joined = computed(() => isJoined())
const config = ref<PublicConfig>({})
const bannerList = ref<BannerItem[]>([])
const cats = ref<{ id: string; name: string; productCount: string }[]>([])
const list = ref<ProductItem[]>([])
const keyword = ref('')
const activeCat = ref('')
const page = ref(1)
const size = 20
const loading = ref(false)
const hasMore = ref(true)

async function loadHead() {
  if (!joined.value) return
  try {
    const [cfg, bns, cs] = await Promise.all([publicConfig(), banners(), categories()])
    config.value = cfg || {}
    bannerList.value = bns || []
    cats.value = cs || []
  } catch {
    // 公开配置失败不阻断购物
  }
}

async function loadProducts(reset: boolean) {
  if (!joined.value || loading.value) return
  loading.value = true
  try {
    if (reset) {
      page.value = 1
      hasMore.value = true
    }
    const res = await products(keyword.value.trim() || undefined, activeCat.value || undefined, page.value, size)
    list.value = reset ? res.list : list.value.concat(res.list)
    hasMore.value = list.value.length < res.total
  } catch {
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

async function reload() {
  await loadProducts(true)
}

onShow(() => {
  if (!joined.value) {
    list.value = []
    return
  }
  loadHead()
  loadProducts(true)
})

onPullDownRefresh(async () => {
  try {
    await loadHead()
    await loadProducts(true)
  } finally {
    uni.stopPullDownRefresh()
  }
})

onReachBottom(() => {
  if (!hasMore.value || loading.value) return
  page.value += 1
  loadProducts(false)
})

function pickCat(id: string) {
  if (activeCat.value === id) return
  activeCat.value = id
  loadProducts(true)
}

function openProduct(id: string) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

function tapBanner(b: BannerItem) {
  if (!b.linkUrl) return
  if (b.linkUrl.startsWith('/pages/')) uni.navigateTo({ url: b.linkUrl })
}

function goTenant() {
  uni.navigateTo({ url: '/pages/tenant/select' })
}
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}
.mt {
  margin-top: 24rpx;
}
/* 切换商户：方角描边小标签，不做胶囊按钮 */
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
.notice {
  margin-top: 18rpx;
  padding: 14rpx 20rpx;
  background: #f7eedd;
  color: #8c6318;
  font-size: 24rpx;
  border-left: 4rpx solid #b8842a;
  border-radius: 4rpx;
}
.banner {
  margin: 20rpx;
  height: 300rpx;
  border-radius: 8rpx;
  overflow: hidden;
  border: 1rpx solid #dddcd3;
}
.banner-img {
  width: 100%;
  height: 300rpx;
}
.search-wrap {
  padding: 4rpx 20rpx 12rpx;
}
.cats {
  white-space: nowrap;
  padding: 0 20rpx 6rpx;
  box-sizing: border-box;
}
.cat {
  display: inline-block;
  padding: 10rpx 24rpx;
  margin-right: 12rpx;
  border-radius: 4rpx;
  background: #ffffff;
  border: 1rpx solid #dddcd3;
  color: #333b39;
  font-size: 24rpx;
}
.cat.active {
  background: #b85c2e;
  border-color: #9c4a20;
  color: #fff;
  font-weight: 600;
}
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 24rpx 0;
}
.product {
  margin: 12rpx 20rpx;
}
.thumb {
  width: 168rpx;
  height: 168rpx;
  border-radius: 4rpx;
  background: #faf9f5;
  border: 1rpx solid #ebeae3;
  flex-shrink: 0;
}
.thumb-empty {
  font-size: 22rpx;
  color: #8c9693;
  text-align: center;
  line-height: 168rpx;
}
.p-info {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}
.p-title {
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 8rpx;
}
.p-bottom {
  margin-top: 12rpx;
}
.unit {
  font-size: 22rpx;
  color: #8c9693;
  font-weight: 400;
}
</style>
