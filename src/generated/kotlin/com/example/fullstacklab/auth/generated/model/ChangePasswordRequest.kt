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
 * @param currentPassword 
 * @param newPassword 
 */
data class ChangePasswordRequest(

    @get:Size(min=1,max=128)
    @param:JsonProperty("currentPassword", required = true)
    @get:JsonProperty("currentPassword", required = true) val currentPassword: kotlin.String,

    @get:Size(min=8,max=128)
    @param:JsonProperty("newPassword", required = true)
    @get:JsonProperty("newPassword", required = true) val newPassword: kotlin.String
) {

}

