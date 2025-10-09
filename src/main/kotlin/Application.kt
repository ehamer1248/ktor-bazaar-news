package bazaar.news

import bazaar.news.com.example.plugins.configureDatabases
import bazaar.news.com.example.plugins.configureRouting
import bazaar.news.com.example.plugins.configureSerialization
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureDatabases()
    configureRouting()
}
