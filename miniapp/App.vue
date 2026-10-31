<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { clearTenant, restore, session } from './stores/session'

onLaunch(() => {
  restore()
})

onShow(() => {
  // 回到前台时校验登录态一致性：有租户域令牌但租户编码丢失，则清空该租户缓存
  if (session.mallToken && !session.tenantCode) {
    clearTenant()
  }
})
</script>

<style>
/* ========== 全局基线 · 墨青工业风 ==========
   纸白底 + 墨色文字 + 铜橙行动色 + 方角描边，克制、专业、高信息密度 */
page {
  background: #f5f4ef;
  color: #131a19;
  font-size: 28rpx;
  line-height: 1.6;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
}

/* 卡片：白纸 + 硬描边 + 极轻阴影（拒绝大圆角泡泡感） */
.card {
  background: #ffffff;
  border: 1rpx solid #dddcd3;
  border-radius: 8rpx;
  padding: 24rpx;
  margin: 20rpx;
  box-shadow: 0 1rpx 2rpx rgba(19, 26, 25, 0.04);
}

/* 按钮 */
.btn {
  border-radius: 6rpx;
  font-size: 28rpx;
  line-height: 1.4;
  padding: 18rpx 0;
  text-align: center;
  letter-spacing: 1rpx;
  border: 1rpx solid transparent;
}
.btn-primary {
  background: #b85c2e;
  border-color: #9c4a20;
  color: #ffffff;
  box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.16);
}
.btn-primary:active {
  background: #9c4a20;
}
.btn-ghost {
  background: #f7ece3;
  border-color: #ead3c3;
  color: #9c4a20;
}
.btn-danger {
  background: #f7e6e2;
  border-color: #ebcbc5;
  color: #b23a2e;
}

/* 输入：暖白底 + 细描边 */
.input {
  background: #fbfaf6;
  border: 1rpx solid #dddcd3;
  border-radius: 6rpx;
  padding: 18rpx 20rpx;
  font-size: 28rpx;
  color: #131a19;
  box-sizing: border-box;
}

/* 布局工具 */
.row {
  display: flex;
  align-items: center;
}
.between {
  justify-content: space-between;
}

/* 文本层级 */
.muted {
  color: #8c9693;
  font-size: 24rpx;
}
.h1 {
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
}
.kicker {
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-size: 20rpx;
  letter-spacing: 3rpx;
  color: #8c9693;
  text-transform: uppercase;
}
.mono {
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
}

/* 价格：铜橙 + 等宽数字，保持品牌一致性 */
.price {
  color: #b85c2e;
  font-weight: 700;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
}

/* 标签：方角打印标签 */
.tag {
  display: inline-block;
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  font-size: 20rpx;
  background: #f7ece3;
  color: #9c4a20;
  border: 1rpx solid #ead3c3;
  line-height: 1.6;
}

.empty {
  text-align: center;
  color: #8c9693;
  padding: 80rpx 0;
  font-size: 26rpx;
}
</style>
