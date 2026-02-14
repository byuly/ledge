package io.ledge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LedgeApplication

fun main(args: Array<String>) {
    runApplication<LedgeApplication>(*args)
}
