package com.example.service

import com.example.model.FileData
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono
import java.io.File
import java.nio.channels.ByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*

@Service
class FileService {
    fun uploadAndProcessFile(
        userId: String,
        fileHeaders: HttpHeaders,
        file: Flux<DataBuffer>
    ): Mono<FileData> {
        val fileName = UUID.randomUUID().toString()
        return DataBufferUtils.write(file, createFileAndGetChannel("test_files/$userId/$fileName"))
            .map { DataBufferUtils.release(it) }
            .then(fileName.toMono())
            .map { FileData(it) }

    }

    fun listFiles(userId: String): Flux<FileData> {
        val parentFile = File("test_files/$userId")
        return parentFile.list()?.toFlux()?.map { FileData(it) } ?: Flux.empty()
    }

    fun getFileContent(userId: String, fileId: String): Flux<DataBuffer> {
        val filePath = File("test_files/$userId/$fileId").toPath()
        return DataBufferUtils.readInputStream(
            { Files.newInputStream(filePath) },
            DefaultDataBufferFactory(),
            3
        )
    }

    fun deleteFile(userId: String, fileId: String): Mono<Boolean> {
        val deleted = File("test_files/$userId/$fileId").delete()
        return deleted.toMono()
    }
}

private fun createFileAndGetChannel(path: String): ByteChannel {
    val tempFile = File(path)
    if (!Files.exists(tempFile.parentFile.toPath())) {
        Files.createDirectories(tempFile.parentFile.toPath())
    }
    tempFile.createNewFile()
    val filePath = tempFile.toPath()
    return Files.newByteChannel(filePath, StandardOpenOption.WRITE)
}

