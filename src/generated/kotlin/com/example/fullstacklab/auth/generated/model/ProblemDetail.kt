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
 * @param type 
 * @param title 
 * @param status 
 * @param detail 
 * @param instance 
 */
data class ProblemDetail(

    @param:JsonProperty("type")
    @get:JsonProperty("type") val type: kotlin.String? = null,

    @param:JsonProperty("title")
    @get:JsonProperty("title") val title: kotlin.String? = null,

    @param:JsonProperty("status")
    @get:JsonProperty("status") val status: kotlin.Int? = null,

    @param:JsonProperty("detail")
    @get:JsonProperty("detail") val detail: kotlin.String? = null,

    @param:JsonProperty("instance")
    @get:JsonProperty("instance") val instance: kotlin.String? = null
) {

}

