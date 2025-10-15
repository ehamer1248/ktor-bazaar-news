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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq




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
        SchemaUtils.create(Users, Posts, Comments)

        // Initialize existing posts with x/y if they are 0
        Posts.selectAll().forEachIndexed { index, row ->
            val currentX = row[Posts.x]
            val currentY = row[Posts.y]

            if (currentX == 0.0 && currentY == 0.0) {
                Posts.update({ Posts.id eq row[Posts.id] }) {
                    it[x] = (index % 10) * 200.0
                    it[y] = (index / 10) * 150.0
                }
            }
        }
    }

    log.info("Connected to Postgres and confirmed that tables exist")
}




