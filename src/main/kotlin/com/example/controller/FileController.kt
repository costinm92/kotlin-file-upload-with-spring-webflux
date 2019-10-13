package com.example.controller

import com.example.exception.BadResponseStatus
import com.example.model.FileData
import com.example.service.FileService
import mu.KotlinLogging
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

private val logger = KotlinLogging.logger { }

@RestController
class FileController(
    private val fileService: FileService
) {
    @PostMapping
    fun uploadFile(
        exchange: ServerWebExchange
    ): Mono<FileData> {
        logger.info { "Started file upload" }
        return exchange.multipartData.flatMap { parts ->
            val map = parts.toSingleValueMap()
            val file = map["file"]!!
            val userId = dataBufferToString(map["userid"]!!.content())
            userId.flatMap { fileService.uploadAndProcessFile(it, file.headers(), file.content().cache()) }
        }.onErrorMap { BadResponseStatus(it.message) }
    }

    @GetMapping
    fun list(
        @RequestParam(required = true) userId: String
    ): Flux<FileData> = fileService.listFiles(userId)

    @GetMapping("/{fileId}")
    fun getContent(
        @RequestParam(required = true) userId: String,
        @PathVariable fileId: String
    ): Flux<DataBuffer> = fileService.getFileContent(userId, fileId)

    @DeleteMapping("/{fileId}")
    fun deleteFile(
        @RequestParam(required = true) userId: String,
        @PathVariable fileId: String,
        response: ServerHttpResponse
    ): Mono<Void> {
        logger.info { "Started removing file $fileId" }
        return fileService.deleteFile(userId, fileId).flatMap {
            if (it) Mono.empty<Void>()
            else Mono.error(BadResponseStatus("Could not remove file $fileId"))
        }
    }
}

private fun dataBufferToString(
    dataBuffer: Flux<DataBuffer>
): Mono<String> = DataBufferUtils.join(dataBuffer).map { buffer ->
    val inputStream = buffer.asInputStream()
    val s = Scanner(inputStream).useDelimiter("\\A")
    if (s.hasNext()) s.next() else ""
}
