package com.example.fullstacklab.auth.keycloak

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Keycloak の接続先と、認証 API が使うクライアント（サービスアカウント）の設定 */
@ConfigurationProperties("auth.keycloak")
data class KeycloakProperties(
    /** コンテナ間の URL（例: http://keycloak:8080） */
    val baseUrl: String,
    val realm: String,
    val clientId: String,
    val clientSecret: String,
    /** 登録したユーザーに付与する realm ロール */
    val customerRole: String = "customer",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(5),
)
