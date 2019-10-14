# Kotlin file upload with Spring WebFlux

## Overview
Spring WebFlux is the reactive-stack web framework that was added in version 5.0 of Spring framework. 
It is fully non-blocking, supports Reactive Streams back pressure, and runs on such servers as Netty, Undertow, and Servlet 3.1+ containers.

It was created because of the need for a non-blocking web stack to handle concurrency with a small number of threads 
and scale with fewer hardware resources.

### What is a DataBuffer?
DataBuffer is the representation for a byte buffer in WebFlux. 
The key point to understand is that on some servers like Netty, byte buffers are pooled and reference counted, 
and must be released when consumed to avoid memory leaks.

WebFlux applications generally do not need to be concerned with such issues, unless they consume or produce data buffers directly, 
as opposed to relying on codecs to convert to and from higher level objects. Or unless they choose to create custom codecs. 

When running on Netty, applications must use DataBufferUtils.retain(dataBuffer) if they wish to hold on input data buffers 
in order to ensure they are not released, and subsequently use DataBufferUtils.release(dataBuffer) when the buffers are consumed.

An alternative to that is to use the cache function from the Flux to retain the last emitted signal and use it on other subscribers.

## Example
In this example, we have created a server that exposes endpoints to upload, download, list and delete files.

We have a project started with Gradle Kotlin DSL and the following spring dependencies:
```
implementation("org.springframework.boot:spring-boot-starter-webflux")
```

The most important part in this example is the file upload functionality, so let's dive in.

In our [FileController](src/main/kotlin/com/example/controller/FileController.kt) we have exposed:
  - a `POST` endpoint that is used to upload a file for an user
  - a `GET` endpoint that is used to download a file for an user
  - a `GET` endpoint that is used to list all the files that an user has uploaded
  - a `DELETE` endpoint that is used to delete a file for an user

### POST
First, let's have a look at the `uploadFile` POST method from the [FileController](src/main/kotlin/com/example/controller/FileController.kt)

#### Controller
```
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
```
Here we can notice that we extract the multi-parts from the request into variables `file` and `userId`.

For `userId` we are parsing the DataBuffer into a String with this method:
```
private fun dataBufferToString(
    dataBuffer: Flux<DataBuffer>
): Mono<String> = DataBufferUtils.join(dataBuffer).map { buffer ->
    val inputStream = buffer.asInputStream()
    val s = Scanner(inputStream).useDelimiter("\\A")
    if (s.hasNext()) s.next() else ""
}
```
We have to get all the parts of the DataBuffer as an InputStream and then transform it to String.

#### Service
In the service, we write the DataBuffer to a ByteChannel (the one that we build in `createFileAndGetChannel`).
```
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
```

### GET
Here we load the file to an InputStream and write it to a Flux<DataBuffer>.
```
fun getFileContent(userId: String, fileId: String): Flux<DataBuffer> {
    val filePath = File("test_files/$userId/$fileId").toPath()
    return DataBufferUtils.readInputStream(
        { Files.newInputStream(filePath) },
        DefaultDataBufferFactory(),
        size
    )
}
```

### Result
The result is an application with 5 endpoints, that allow us to upload/download/delete/list files for an user.

## References
* [Web on Reactive Stack](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html)
* [projectreactor.io Flux cache() documentation](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#cache--)
