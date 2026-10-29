package com.example.fullstacklab.auth.customer

import com.example.fullstacklab.auth.keycloak.EmailAlreadyRegisteredException
import com.example.fullstacklab.auth.keycloak.KeycloakAdminClient
import com.example.fullstacklab.auth.keycloak.KeycloakProperties
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.never
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 認証 API → Keycloak（トークンエンドポイントと Admin API）の通信と、エラーの変換を確認する。
 * HTTP の層を MockRestServiceServer で差し替え、Keycloak の応答を再現する。
 */
class CustomerAccountServiceTest {
    private lateinit var keycloak: MockRestServiceServer
    private lateinit var service: CustomerAccountService

    private val base = "http://keycloak.test"
    private val token = "$base/realms/customer/protocol/openid-connect/token"
    private val users = "$base/admin/realms/customer/users"
    private val userId = "7a0c1d2e-0000-4000-8000-000000000001"

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(base)
        keycloak = MockRestServiceServer.bindTo(builder).build()
        val props = KeycloakProperties(baseUrl = base, realm = "customer", clientId = "auth-api", clientSecret = "secret")
        service = CustomerAccountService(KeycloakAdminClient(builder.build(), props), props)
    }

    private fun expectServiceAccountToken() {
        keycloak.expect(once(), requestTo(token))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("grant_type=client_credentials")))
            .andExpect(content().string(containsString("client_secret=secret")))
            .andRespond(withSuccess("""{"access_token":"sa-token","expires_in":300}""", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `register creates the user, assigns the customer role and reuses the token`() {
        expectServiceAccountToken()
        keycloak.expect(requestTo(users))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sa-token"))
            .andExpect(jsonPath("$.username").value("taro@example.com"))
            .andExpect(jsonPath("$.email").value("taro@example.com"))
            .andExpect(jsonPath("$.firstName").value("太郎"))
            .andExpect(jsonPath("$.lastName").value("山田"))
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.credentials[0].value").value("password-1"))
            .andExpect(jsonPath("$.credentials[0].temporary").value(false))
            .andRespond(withCreatedEntity(URI.create("$users/$userId")))
        keycloak.expect(requestTo("$base/admin/realms/customer/roles/customer"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sa-token"))
            .andRespond(withSuccess("""{"id":"role-id","name":"customer","composite":false}""", MediaType.APPLICATION_JSON))
        keycloak.expect(requestTo("$users/$userId/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$[0].id").value("role-id"))
            .andExpect(jsonPath("$[0].name").value("customer"))
            .andRespond(withNoContent())

        val account = service.register("taro@example.com", "山田", "太郎", "password-1")

        assertEquals(CustomerAccount(userId, "taro@example.com", "山田", "太郎"), account)
        keycloak.verify()
    }

    @Test
    fun `duplicate email is reported as a conflict`() {
        expectServiceAccountToken()
        keycloak.expect(requestTo(users))
            .andRespond(withStatus(HttpStatus.CONFLICT).body("""{"errorMessage":"User exists with same username"}"""))

        assertFailsWith<EmailAlreadyRegisteredException> { service.register("taro@example.com", "山田", "太郎", "password-1") }
    }

    @Test
    fun `user is deleted when the role cannot be assigned`() {
        expectServiceAccountToken()
        keycloak.expect(requestTo(users)).andRespond(withCreatedEntity(URI.create("$users/$userId")))
        keycloak.expect(requestTo("$base/admin/realms/customer/roles/customer"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
        keycloak.expect(requestTo("$users/$userId")).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent())

        assertFailsWith<RestClientException> { service.register("taro@example.com", "山田", "太郎", "password-1") }
        keycloak.verify()
    }

    @Test
    fun `unknown user is not found`() {
        expectServiceAccountToken()
        keycloak.expect(requestTo("$users/$userId")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<CustomerNotFoundException> { service.get(userId) }
    }

    @Test
    fun `password change is rejected when the current password does not match`() {
        expectServiceAccountToken()
        keycloak.expect(requestTo("$users/$userId"))
            .andRespond(withSuccess(userJson(), MediaType.APPLICATION_JSON))
        keycloak.expect(requestTo(token))
            .andExpect(content().string(containsString("grant_type=password")))
            .andExpect(content().string(containsString("username=taro%40example.com")))
            .andExpect(content().string(containsString("password=wrong")))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":"invalid_grant","error_description":"Invalid user credentials"}"""),
            )
        keycloak.expect(never(), requestTo("$users/$userId/reset-password"))

        assertFailsWith<InvalidCurrentPasswordException> { service.changePassword(userId, "wrong", "password-2") }
        keycloak.verify()
    }

    @Test
    fun `password change verifies the current password and resets to the new one`() {
        expectServiceAccountToken()
        keycloak.expect(requestTo("$users/$userId"))
            .andRespond(withSuccess(userJson(), MediaType.APPLICATION_JSON))
        keycloak.expect(requestTo(token))
            .andExpect(content().string(containsString("grant_type=password")))
            .andExpect(content().string(not(containsString("password-2"))))
            .andRespond(withSuccess("""{"access_token":"user-token","expires_in":300}""", MediaType.APPLICATION_JSON))
        keycloak.expect(requestTo("$users/$userId/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sa-token"))
            .andExpect(jsonPath("$.type").value("password"))
            .andExpect(jsonPath("$.value").value("password-2"))
            .andExpect(jsonPath("$.temporary").value(false))
            .andRespond(withNoContent())

        service.changePassword(userId, "password-1", "password-2")
        keycloak.verify()
    }

    private fun userJson() =
        """{"id":"$userId","username":"taro@example.com","email":"taro@example.com","firstName":"太郎","lastName":"山田","enabled":true}"""
}
