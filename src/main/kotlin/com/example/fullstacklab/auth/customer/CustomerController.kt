package com.example.fullstacklab.auth.customer

import com.example.fullstacklab.auth.generated.api.CustomersApi
import com.example.fullstacklab.auth.generated.model.ChangePasswordRequest
import com.example.fullstacklab.auth.generated.model.CustomerAccount as CustomerAccountDto
import com.example.fullstacklab.auth.generated.model.RegisterCustomerRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/** 認証 API。openapi/auth.yaml から生成した CustomersApi を実装する。内部ネットワーク専用で、BFF を信頼する */
@RestController
class CustomerController(private val service: CustomerAccountService) : CustomersApi {

    override fun registerCustomer(registerCustomerRequest: RegisterCustomerRequest): ResponseEntity<CustomerAccountDto> {
        val account = with(registerCustomerRequest) { service.register(email, familyName, givenName, password) }
        return ResponseEntity.status(HttpStatus.CREATED).body(account.toDto())
    }

    override fun getCustomer(id: String): ResponseEntity<CustomerAccountDto> =
        ResponseEntity.ok(service.get(id).toDto())

    override fun changeCustomerPassword(id: String, changePasswordRequest: ChangePasswordRequest): ResponseEntity<Unit> {
        service.changePassword(id, changePasswordRequest.currentPassword, changePasswordRequest.newPassword)
        return ResponseEntity.noContent().build()
    }

    private fun CustomerAccount.toDto() =
        CustomerAccountDto(id = id, email = email, familyName = familyName, givenName = givenName)
}
