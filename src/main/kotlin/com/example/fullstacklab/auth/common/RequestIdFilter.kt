package com.example.fullstacklab.auth.common

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Nginx（AWS では ALB）が付与した X-Request-Id をログの MDC に載せる。
 * BFF → API と同じ ID が引き継がれるので、サービスをまたいでログを追跡できる。
 */
@Component
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        MDC.put(MDC_KEY, request.getHeader(HEADER) ?: "-")
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
    }
}
