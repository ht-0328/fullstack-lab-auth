package com.example.fullstacklab.auth.keycloak

import com.example.fullstacklab.auth.common.RequestIdFilter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.Instant

/** Keycloak のユーザー表現のうち、この API が使う項目だけ */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KeycloakUser(
    val id: String,
    val username: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

class EmailAlreadyRegisteredException : RuntimeException("email already registered")

/** Keycloak 向けの RestClient。接続先・タイムアウト・リクエスト ID の引き継ぎだけをここで決める */
@Configuration
class KeycloakClientConfig {
    @Bean
    fun keycloakRestClient(builder: RestClient.Builder, props: KeycloakProperties): RestClient = builder
        .baseUrl(props.baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(props.connectTimeout)
                setReadTimeout(props.readTimeout)
            },
        )
        .requestInterceptor { request, body, execution ->
            MDC.get(RequestIdFilter.MDC_KEY)?.let { request.headers.set(RequestIdFilter.HEADER, it) }
            execution.execute(request, body)
        }
        .build()
}

/**
 * Keycloak の Admin REST API とトークンエンドポイントを呼ぶ。
 * 認証にはサービスアカウント（client_credentials）のトークンを使い、期限が切れる前まで使い回す。
 * パスワードは要求の本文にだけ載せ、ログや例外メッセージには出さない。
 */
@Component
class KeycloakAdminClient(private val client: RestClient, private val props: KeycloakProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val usersPath = "/admin/realms/${props.realm}/users"
    private val rolesPath = "/admin/realms/${props.realm}/roles"
    private val tokenPath = "/realms/${props.realm}/protocol/openid-connect/token"

    /** ユーザーを作り、Keycloak が採番した ID を返す。メールアドレス（= ユーザー名）が重複していれば例外 */
    fun createUser(email: String, familyName: String, givenName: String, password: String): String {
        val representation = mapOf(
            "username" to email,
            "email" to email,
            "firstName" to givenName,
            "lastName" to familyName,
            "enabled" to true,
            // 確認メールの仕組みは持たないため、登録時点で確認済みにする（設計書 0005 の仮定）
            "emailVerified" to true,
            "credentials" to listOf(passwordCredential(password)),
        )
        val response = try {
            client.post().uri(usersPath)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(representation)
                .retrieve()
                .toBodilessEntity()
        } catch (e: HttpClientErrorException.Conflict) {
            throw EmailAlreadyRegisteredException()
        }
        // 作成したユーザーの ID は Location ヘッダー（.../users/{id}）で返る
        val location = response.headers.location ?: error("Keycloak did not return the Location of the created user")
        return location.path.substringAfterLast('/')
    }

    fun assignRealmRole(userId: String, roleName: String) {
        val role = client.get().uri("$rolesPath/{name}", roleName)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(RoleRepresentation::class.java)
            ?: error("realm role $roleName not found")
        client.post().uri("$usersPath/{id}/role-mappings/realm", userId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(listOf(role))
            .retrieve()
            .toBodilessEntity()
    }

    fun deleteUser(userId: String) {
        client.delete().uri("$usersPath/{id}", userId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .toBodilessEntity()
    }

    fun getUser(userId: String): KeycloakUser? =
        try {
            client.get().uri("$usersPath/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(KeycloakUser::class.java)
        } catch (e: HttpClientErrorException.NotFound) {
            null
        }

    /** 現在のパスワードが正しいかを、Resource Owner Password Credentials でトークンを取れるかで確かめる */
    fun verifyPassword(username: String, password: String): Boolean {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "password")
            add("client_id", props.clientId)
            add("client_secret", props.clientSecret)
            add("username", username)
            add("password", password)
        }
        return try {
            client.post().uri(tokenPath)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity()
            true
        } catch (e: HttpClientErrorException) {
            // 資格情報の誤りは invalid_grant で返る（Keycloak 26 は 400、古い版は 401）。それ以外の 4xx は想定外として伝播させる
            val invalidGrant = e.statusCode.value() in setOf(400, 401) && e.responseBodyAsString.contains("\"invalid_grant\"")
            if (invalidGrant) false else throw e
        }
    }

    fun resetPassword(userId: String, newPassword: String) {
        client.put().uri("$usersPath/{id}/reset-password", userId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(passwordCredential(newPassword))
            .retrieve()
            .toBodilessEntity()
    }

    private fun passwordCredential(password: String) =
        mapOf("type" to "password", "value" to password, "temporary" to false)

    // --- サービスアカウントのトークン ---------------------------------------------------------

    private data class CachedToken(val value: String, val expiresAt: Instant)

    @Volatile
    private var cached: CachedToken? = null

    private fun bearer() = "Bearer ${accessToken()}"

    /** client_credentials で取得したトークンを、期限の 30 秒前まで使い回す */
    private fun accessToken(): String {
        val now = Instant.now()
        cached?.takeIf { it.expiresAt.isAfter(now.plusSeconds(30)) }?.let { return it.value }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", props.clientId)
            add("client_secret", props.clientSecret)
        }
        val token = client.post().uri(tokenPath)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse::class.java)
            ?: error("Keycloak returned an empty token response")
        log.debug("service account token renewed (expires in {}s)", token.expiresIn)
        cached = CachedToken(token.accessToken, now.plusSeconds(token.expiresIn))
        return token.accessToken
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TokenResponse(
        @param:JsonProperty("access_token") val accessToken: String,
        @param:JsonProperty("expires_in") val expiresIn: Long,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class RoleRepresentation(val id: String, val name: String)
}
