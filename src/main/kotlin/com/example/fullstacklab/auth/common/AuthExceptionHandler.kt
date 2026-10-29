package com.example.fullstacklab.auth.common

import com.example.fullstacklab.auth.customer.CustomerNotFoundException
import com.example.fullstacklab.auth.customer.InvalidCurrentPasswordException
import com.example.fullstacklab.auth.keycloak.EmailAlreadyRegisteredException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException

/** Keycloak の応答をこの API の契約（Problem Details）に変換する。Keycloak の応答本文は呼び出し側に出さない */
@RestControllerAdvice
class AuthExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun handleDuplicate(e: EmailAlreadyRegisteredException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "このメールアドレスは登録済みです")

    @ExceptionHandler(CustomerNotFoundException::class)
    fun handleNotFound(e: CustomerNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "not found")

    @ExceptionHandler(InvalidCurrentPasswordException::class)
    fun handleInvalidCurrentPassword(e: InvalidCurrentPasswordException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "現在のパスワードが違います")

    // Keycloak が入力を拒否した（ユーザープロファイルやパスワードポリシーなど）
    @ExceptionHandler(HttpClientErrorException.BadRequest::class)
    fun handleRejectedInput(e: HttpClientErrorException.BadRequest): ProblemDetail {
        log.info("identity provider rejected the input: {}", e.statusCode)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "入力が受け付けられませんでした")
    }

    // 接続できない・タイムアウト
    @ExceptionHandler(ResourceAccessException::class)
    fun handleUnreachable(e: ResourceAccessException): ProblemDetail {
        log.error("identity provider unreachable: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "identity provider unavailable")
    }

    // 想定外の応答（5xx、サービスアカウントの権限不足など）。ステータスだけを記録する
    @ExceptionHandler(RestClientResponseException::class)
    fun handleUnexpectedResponse(e: RestClientResponseException): ProblemDetail {
        log.error("unexpected response from identity provider: {}", e.statusCode)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "unexpected response from identity provider")
    }
}
