package com.example.fullstacklab.auth.generated.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.validation.Valid

/**
 * 
 * @param email ログイン ID を兼ねる
 * @param familyName 
 * @param givenName 
 * @param password 
 */
data class RegisterCustomerRequest(

    @get:Pattern(regexp="^[^@ ]+@[^@ ]+[.][^@ ]+$")
    @get:Size(max=254)
    @param:JsonProperty("email", required = true)
    @get:JsonProperty("email", required = true) val email: kotlin.String,

    @get:Size(min=1,max=50)
    @param:JsonProperty("familyName", required = true)
    @get:JsonProperty("familyName", required = true) val familyName: kotlin.String,

    @get:Size(min=1,max=50)
    @param:JsonProperty("givenName", required = true)
    @get:JsonProperty("givenName", required = true) val givenName: kotlin.String,

    @get:Size(min=8,max=128)
    @param:JsonProperty("password", required = true)
    @get:JsonProperty("password", required = true) val password: kotlin.String
) {

}

