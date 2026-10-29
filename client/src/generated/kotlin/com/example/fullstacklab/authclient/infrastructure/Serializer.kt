package com.example.fullstacklab.authclient.infrastructure

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.cfg.DateTimeFeature
import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

object Serializer {
    @JvmStatic
    val jacksonObjectMapper: JsonMapper = jsonMapper {
        addModule(kotlinModule())
        changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_ABSENT) }
        disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}
