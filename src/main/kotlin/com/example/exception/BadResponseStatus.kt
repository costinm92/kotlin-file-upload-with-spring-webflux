package com.example.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class BadResponseStatus(
    message: String?
) : ResponseStatusException(
    HttpStatus.BAD_REQUEST,
    "Response has bad status, message: $message"
)
