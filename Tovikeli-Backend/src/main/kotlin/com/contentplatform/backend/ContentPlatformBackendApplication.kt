package com.contentplatform.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ContentPlatformBackendApplication

fun main(args: Array<String>) {
	runApplication<ContentPlatformBackendApplication>(*args)
}
