package bazaar.news.com.example.plugins

import bazaar.news.com.example.models.Posts
import bazaar.news.com.example.models.Users
import bazaar.news.com.example.models.PostResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import io.ktor.server.plugins.cors.routing.*

import io.ktor.http.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Op


fun Application.configureCors() {
    install(CORS) {
        anyHost() // Allow all hosts for development
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
    }
}

fun Application.configureRouting() {

    routing {

        route("/api") {
            // Root route
            get("/") {
                call.respondText("Welcome to Bazaar News API!")
            }

            // Get all posts
            get("/posts") {
                val posts = transaction {
                    Posts.selectAll().map { row ->
                        PostResponse(
                            id = row[Posts.id],
                            title = row[Posts.title],
                            content = row[Posts.content],
                            authorId = row[Posts.authorId],
                            createdAt = row[Posts.createdAt].toString()
                        )
                    }
                }
                call.respond(HttpStatusCode.OK, posts)
            }

            // Get single post by ID
            get("/posts/{id}") {
                val idParam = call.parameters["id"] ?: return@get call.respondText(
                    "Missing id",
                    status = HttpStatusCode.BadRequest
                )

                val id = idParam.toIntOrNull()
                    ?: return@get call.respondText("Invalid id", status = HttpStatusCode.BadRequest)

                val post = transaction {
                    Posts.selectAll().where { Posts.id eq id }.singleOrNull()
                } ?: return@get call.respondText("Post not found", status = HttpStatusCode.NotFound)

                call.respond(
                    mapOf(
                        "id" to post[Posts.id],
                        "title" to post[Posts.title],
                        "content" to post[Posts.content],
                        "authorId" to post[Posts.authorId],
                        "createdAt" to post[Posts.createdAt].toString()
                    )
                )
            }

            // Create a new post
            post("/posts") {
                val params = call.receiveParameters()
                val title = params["title"] ?: return@post call.respondText(
                    "Missing title", status = HttpStatusCode.BadRequest
                )
                val content = params["content"] ?: return@post call.respondText(
                    "Missing content", status = HttpStatusCode.BadRequest
                )
                val authorId = params["authorId"]?.toIntOrNull() ?: return@post call.respondText(
                    "Missing or invalid authorId", status = HttpStatusCode.BadRequest
                )

                val postId: Int = transaction {
                    Posts.insert {
                        it[Posts.title] = title
                        it[Posts.content] = content
                        it[Posts.authorId] = authorId
                        it[Posts.createdAt] = LocalDateTime.now()
                    } get Posts.id
                }

                call.respondText("Post created with id $postId", status = HttpStatusCode.Created)
            }

            // Register a user
            post("/register") {
                val params = call.receiveParameters()
                val username = params["username"] ?: return@post call.respondText(
                    "Missing username", status = HttpStatusCode.BadRequest
                )
                val password = params["password"] ?: return@post call.respondText(
                    "Missing password", status = HttpStatusCode.BadRequest
                )

                // Check if username exists first
                val existingUser = transaction {
                    Users.selectAll().where { Users.username eq username }.singleOrNull()
                }

                if (existingUser != null) {
                    return@post call.respondText("Username already exists", status = HttpStatusCode.Conflict)
                }

                val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

                val userId: Int = transaction {
                    Users.insert {
                        it[Users.username] = username
                        it[Users.password] = hashedPassword
                    } get Users.id
                }

                call.respondText("User created with id $userId", status = HttpStatusCode.Created)
            }
            // Login
            post("/login") {
                val params = call.receiveParameters()
                val username = params["username"] ?: return@post call.respondText(
                    "Missing username", status = HttpStatusCode.BadRequest
                )
                val password = params["password"] ?: return@post call.respondText(
                    "Missing password", status = HttpStatusCode.BadRequest
                )

                val user = transaction {
                    Users.selectAll().where { Users.username eq username }.singleOrNull()
                } ?: return@post call.respondText("User not found", status = HttpStatusCode.NotFound)

                if (!BCrypt.checkpw(password, user[Users.password])) {
                    return@post call.respondText("Invalid password", status = HttpStatusCode.Unauthorized)
                }

                call.respondText("Login successful!")
            }
        }
    }

}
