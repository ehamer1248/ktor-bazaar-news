package bazaar.news.com.example.plugins

import bazaar.news.com.example.models.PostResponse
import bazaar.news.com.example.models.CommentResponse

import bazaar.news.com.example.models.Posts
import bazaar.news.com.example.models.Users
import bazaar.news.com.example.models.Comments
import bazaar.news.com.example.models.Upvotes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime

@kotlinx.serialization.Serializable
data class UpvoteResult(val count: Int, val youVoted: Boolean)


// ───────────────────────────────────────────────────────────────────────────────
// CORS (dev; tighten for prod)
// ───────────────────────────────────────────────────────────────────────────────
fun Application.configureCors() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        // If you later add DELETE (unvote), also:
        // allowMethod(HttpMethod.Delete)
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// Routing (Exposed-safe for older versions)
// ───────────────────────────────────────────────────────────────────────────────
fun Application.configureRouting() {

    routing {
        route("/api") {

            get("/") {
                call.respondText("Welcome to Bazaar News API!")
            }

            // All posts
            get("/posts") {
                val posts: List<PostResponse> = transaction {
                    Posts
                        .selectAll()
                        .orderBy(Posts.id, SortOrder.ASC)
                        .map { row ->
                            PostResponse(
                                id = row[Posts.id],
                                title = row[Posts.title],
                                content = row[Posts.content],
                                authorId = row[Posts.authorId],
                                createdAt = row[Posts.createdAt].toString(),
                                x = row[Posts.x],
                                y = row[Posts.y],
                                traction = row[Posts.traction],
                                color = row[Posts.color],
                                radius = row[Posts.radius]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, posts)
            }

            // Single post by ID
            get("/posts/{id}") {
                val idParam = call.parameters["id"]
                    ?: return@get call.respondText("Missing id", status = HttpStatusCode.BadRequest)

                val id = idParam.toIntOrNull()
                    ?: return@get call.respondText("Invalid id", status = HttpStatusCode.BadRequest)

                try {
                    val row = transaction {
                        // Older-Exposed-friendly: selectAll().where { ... }.singleOrNull()
                        Posts
                            .selectAll()
                            .where { Posts.id eq id }
                            .singleOrNull()
                    } ?: return@get call.respondText("Post not found", status = HttpStatusCode.NotFound)

                    val dto = PostResponse(
                        id = row[Posts.id],
                        title = row[Posts.title],
                        content = row[Posts.content],
                        authorId = row[Posts.authorId],
                        createdAt = row[Posts.createdAt].toString(),
                        x = row[Posts.x],
                        y = row[Posts.y],
                        traction = row[Posts.traction],
                        color = row[Posts.color],
                        radius = row[Posts.radius]
                    )
                    call.respond(HttpStatusCode.OK, dto)
                } catch (t: Throwable) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Error fetching post $id: ${t.message ?: t.toString()}"
                    )
                }
            }

            // Create post (x-www-form-urlencoded)
            post("/posts") {
                val params = call.receiveParameters()
                val title = params["title"]
                    ?: return@post call.respondText("Missing title", status = HttpStatusCode.BadRequest)
                val content = params["content"]
                    ?: return@post call.respondText("Missing content", status = HttpStatusCode.BadRequest)
                val username = params["username"]
                    ?: return@post call.respondText("Missing username", status = HttpStatusCode.BadRequest)

                val postId: Int? = transaction {
                    val user = Users.selectAll().where { Users.username eq username }.singleOrNull()
                    if (user == null) return@transaction null
                    val authorId = user[Users.id]
                    Posts.insert {
                        it[Posts.title] = title
                        it[Posts.content] = content
                        it[Posts.authorId] = authorId
                        it[Posts.createdAt] = LocalDateTime.now()
                    } get Posts.id
                }

                if (postId == null) {
                    call.respondText("User not found", status = HttpStatusCode.BadRequest)
                } else {
                    call.respondText("Post created with id $postId", status = HttpStatusCode.Created)
                }
            }

            // Register (x-www-form-urlencoded)
            post("/register") {
                val params = call.receiveParameters()
                val username = params["username"]
                    ?: return@post call.respondText("Missing username", status = HttpStatusCode.BadRequest)
                val password = params["password"]
                    ?: return@post call.respondText("Missing password", status = HttpStatusCode.BadRequest)

                val existingUser = transaction {
                    Users
                        .selectAll()
                        .where { Users.username eq username }
                        .singleOrNull()
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

            // Login (x-www-form-urlencoded)
            post("/login") {
                val params = call.receiveParameters()
                val username = params["username"]
                    ?: return@post call.respondText("Missing username", status = HttpStatusCode.BadRequest)
                val password = params["password"]
                    ?: return@post call.respondText("Missing password", status = HttpStatusCode.BadRequest)

                val user = transaction {
                    Users
                        .selectAll()
                        .where { Users.username eq username }
                        .singleOrNull()
                } ?: return@post call.respondText("User not found", status = HttpStatusCode.NotFound)

                if (!BCrypt.checkpw(password, user[Users.password])) {
                    return@post call.respondText("Invalid password", status = HttpStatusCode.Unauthorized)
                }

                call.respondText("Login successful!")
            }

            // ───────────────────────────────── Comments ───────────────────────────────────────
            get("/posts/{postId}/comments") {
                val postIdParam = call.parameters["postId"]
                    ?: return@get call.respondText("Missing postId", status = HttpStatusCode.BadRequest)

                val postId = postIdParam.toIntOrNull()
                    ?: return@get call.respondText("Invalid postId", status = HttpStatusCode.BadRequest)

                val comments: List<CommentResponse> = transaction {
                    // Join with Users table to get username
                    (Comments innerJoin Users)
                        .selectAll()
                        .where { Comments.postId eq postId }
                        .orderBy(Comments.createdAt, SortOrder.ASC)
                        .map { row ->
                            CommentResponse(
                                id = row[Comments.id],
                                postId = row[Comments.postId],
                                authorId = row[Comments.authorId],
                                content = row[Comments.content],
                                createdAt = row[Comments.createdAt].toString(),
                                authorUsername = row[Users.username]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, comments)
            }

            // create a comment
            post("/posts/{postId}/comments") {
                val postIdParam = call.parameters["postId"]
                    ?: return@post call.respondText("Missing postId", status = HttpStatusCode.BadRequest)

                val postId = postIdParam.toIntOrNull()
                    ?: return@post call.respondText("Invalid postId", status = HttpStatusCode.BadRequest)

                val params = call.receiveParameters()
                val content = params["content"]
                    ?: return@post call.respondText("Missing content", status = HttpStatusCode.BadRequest)
                val username = params["username"]
                    ?: return@post call.respondText("Missing username", status = HttpStatusCode.BadRequest)

                val commentId: Int? = transaction {
                    // Verify post exists
                    val postExists = Posts.selectAll().where { Posts.id eq postId }.count() > 0
                    if (!postExists) return@transaction null

                    // Look up user by username
                    val user = Users.selectAll().where { Users.username eq username }.singleOrNull()
                    if (user == null) return@transaction null
                    val authorId = user[Users.id]

                    Comments.insert {
                        it[Comments.postId] = postId
                        it[Comments.authorId] = authorId
                        it[Comments.content] = content
                        it[Comments.createdAt] = LocalDateTime.now()
                    } get Comments.id
                }

                if (commentId == null) {
                    call.respondText("Post or user not found", status = HttpStatusCode.BadRequest)
                } else {
                    call.respondText("Comment created with id $commentId", status = HttpStatusCode.Created)
                }
            }

            // ───────────────────────────────── Upvotes ────────────────────────────────────────

            // Count upvotes for a post (older-Exposed style)
            get("/posts/{id}/upvotes") {
                val postId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respondText("Invalid id", status = HttpStatusCode.BadRequest)

                val count = transaction {
                    Upvotes
                        .selectAll()
                        .where { Upvotes.postId eq postId }
                        .count()
                }.toInt()

                call.respond(HttpStatusCode.OK, mapOf("count" to count))
            }

            // Has this username upvoted this post? (older-Exposed style)
            get("/posts/{id}/upvotes/me") {
                val postId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respondText("Invalid id", status = HttpStatusCode.BadRequest)
                val username = call.request.queryParameters["username"]
                    ?: return@get call.respondText("Missing username", status = HttpStatusCode.BadRequest)

                val youVoted = transaction {
                    val user = Users.selectAll().where { Users.username eq username }.singleOrNull()
                        ?: return@transaction false
                    val uid = user[Users.id]

                    Upvotes
                        .selectAll()
                        .where { (Upvotes.postId eq postId) and (Upvotes.userId eq uid) }
                        .limit(1)
                        .any()
                }

                call.respond(HttpStatusCode.OK, mapOf("youVoted" to youVoted))
            }

            // Perform upvote (idempotent; older-Exposed compatible; JSON DTO response)
            post("/posts/{id}/upvote") {
                val postId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respondText("Invalid id", status = HttpStatusCode.BadRequest)
                val params = call.receiveParameters()
                val username = params["username"]
                    ?: return@post call.respondText("Missing username", status = HttpStatusCode.BadRequest)

                val countOrNull = transaction {
                    // Ensure post exists
                    val postExists = Posts.selectAll().where { Posts.id eq postId }.limit(1).any()
                    if (!postExists) return@transaction null

                    // Resolve user
                    val user = Users.selectAll().where { Users.username eq username }.singleOrNull()
                        ?: return@transaction null
                    val uid = user[Users.id]

                    // Try insert; if already exists, ignore the error (composite PK prevents duplicates)
                    // Works across Exposed versions without relying on insertIgnore
                    try {
                        Upvotes.insert {
                            it[Upvotes.postId] = postId
                            it[Upvotes.userId] = uid
                            it[Upvotes.createdAt] = LocalDateTime.now()
                        }
                    } catch (_: Exception) {
                        // Most likely a duplicate key / already upvoted → ignore
                    }

                    // Authoritative count after (potential) insert
                    Upvotes.selectAll()
                        .where { Upvotes.postId eq postId }
                        .count()
                        .toInt()
                }

                if (countOrNull == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Post or user not found")
                }

                // Always return JSON; client expects {count, youVoted:true}
                call.respond(HttpStatusCode.OK, UpvoteResult(count = countOrNull, youVoted = true))
            }
        }
    }
}
