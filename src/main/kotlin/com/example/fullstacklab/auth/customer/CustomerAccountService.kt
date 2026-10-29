package com.example.fullstacklab.auth.customer

import com.example.fullstacklab.auth.keycloak.KeycloakAdminClient
import com.example.fullstacklab.auth.keycloak.KeycloakProperties
import com.example.fullstacklab.auth.keycloak.KeycloakUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

class CustomerNotFoundException(id: String) : RuntimeException("customer $id not found")
class InvalidCurrentPasswordException : RuntimeException("current password does not match")

/** 一般利用者のアカウント（Keycloak のユーザーのうち、この API が公開する項目） */
data class CustomerAccount(
    val id: String,
    val email: String,
    val familyName: String,
    val givenName: String,
)

/**
 * 一般利用者のアカウント管理。Keycloak の都合（ユーザー表現、ロール付与の手順、エラーの形）をここで吸収し、
 * 呼び出し側（BFF）には契約（openapi/auth.yaml）の形だけを見せる。
 */
@Service
class CustomerAccountService(
    private val keycloak: KeycloakAdminClient,
    private val props: KeycloakProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun register(email: String, familyName: String, givenName: String, password: String): CustomerAccount {
        val id = keycloak.createUser(email, familyName, givenName, password)
        try {
            keycloak.assignRealmRole(id, props.customerRole)
        } catch (e: RestClientException) {
            // ロールのないユーザーを残さない。削除して、登録をやり直せる状態にする
            log.warn("role assignment failed for user {}; deleting the user so that registration can be retried", id)
            runCatching { keycloak.deleteUser(id) }
                .onFailure { log.error("failed to delete user {} after role assignment failure: {}", id, it.message) }
            throw e
        }
        log.info("registered customer {}", id)
        return CustomerAccount(id, email, familyName, givenName)
    }

    fun get(id: String): CustomerAccount =
        keycloak.getUser(id)?.toAccount() ?: throw CustomerNotFoundException(id)

    fun changePassword(id: String, currentPassword: String, newPassword: String) {
        val user = keycloak.getUser(id) ?: throw CustomerNotFoundException(id)
        if (!keycloak.verifyPassword(user.username, currentPassword)) {
            log.info("password change rejected for customer {}: current password does not match", id)
            throw InvalidCurrentPasswordException()
        }
        keycloak.resetPassword(id, newPassword)
        log.info("password changed for customer {}", id)
    }

    private fun KeycloakUser.toAccount() = CustomerAccount(
        id = id,
        email = email ?: username,
        familyName = lastName.orEmpty(),
        givenName = firstName.orEmpty(),
    )
}
