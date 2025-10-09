package bazaar.news.com.example.plugins


import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Connection
import java.sql.DriverManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import bazaar.news.com.example.models.Users
import bazaar.news.com.example.models.Posts
import bazaar.news.com.example.models.Comments


fun Application.configureDatabases() {
    val url = environment.config.property("postgres.url").getString()
    val user = environment.config.property("postgres.user").getString()
    val password = environment.config.property("postgres.password").getString()

    Database.connect(
        url = url,
        driver = "org.postgresql.Driver",
        user = user,
        password = password
    )

    transaction {
       SchemaUtils.create(Users,Posts,Comments)
    }

    log.info("Connected to Postgres and confirmed that tables exist")
}




