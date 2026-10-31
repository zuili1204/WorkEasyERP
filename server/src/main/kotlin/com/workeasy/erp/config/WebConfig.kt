package com.workeasy.erp.config

import com.workeasy.erp.mall.MallAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val authInterceptor: AuthInterceptor,
    private val tenantResolverInterceptor: TenantResolverInterceptor,
    private val mallAuthInterceptor: MallAuthInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        // 先解析租户（含未登录接口），再校验身份
        registry.addInterceptor(tenantResolverInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns("/health", "/error")
            .order(0)
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/auth/login", "/health", "/error",
                // 平台侧租户开通接口走平台令牌，不使用员工 JWT
                "/platform/**",
                // 商城走会员双 realm 鉴权（MallAuthInterceptor）
                "/mall/**",
            )
            .order(1)

        // 商城会员：平台会员令牌（选租户）与租户域令牌（下单/查询）
        registry.addInterceptor(mallAuthInterceptor)
            .addPathPatterns("/mall/**")
            .excludePathPatterns(
                "/mall/auth/wx-login",      // 微信登录换取平台会员令牌
                "/mall/pay/notify/**",      // 支付回调：免登录 + 验签 + 幂等
                "/mall/public/**",          // 公告/轮播等公开信息
            )
            .order(2)
    }
}
