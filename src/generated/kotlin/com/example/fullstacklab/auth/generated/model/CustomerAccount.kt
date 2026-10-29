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
 * @param id Keycloak のユーザー ID（JWT の sub）
 * @param email 
 * @param familyName 
 * @param givenName 
 */
data class CustomerAccount(

    @param:JsonProperty("id", required = true)
    @get:JsonProperty("id", required = true) val id: kotlin.String,

    @param:JsonProperty("email", required = true)
    @get:JsonProperty("email", required = true) val email: kotlin.String,

    @param:JsonProperty("familyName", required = true)
    @get:JsonProperty("familyName", required = true) val familyName: kotlin.String,

    @param:JsonProperty("givenName", required = true)
    @get:JsonProperty("givenName", required = true) val givenName: kotlin.String
) {

}

