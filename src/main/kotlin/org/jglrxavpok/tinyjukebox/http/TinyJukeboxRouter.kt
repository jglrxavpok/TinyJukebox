package org.jglrxavpok.tinyjukebox.http

import io.github.magdkudama.krouter.*
import io.github.magdkudama.krouter.Router
import org.jglrxavpok.tinyjukebox.http.controllers.*
import java.io.OutputStream
import java.io.PrintWriter
import java.nio.file.Paths
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * Routes used by TinyJukebox
 */
val TinyJukeboxRoutes = arrayOf(
    Route("play", "/play/{name}", emptyMap(), setOf(Method.POST), PlayController::play.asRouteHandler<PlayController>()),

    // TODO: break into routes for each available music source (YT, Local, Soundcloud, etc.)
    Route("upload", "/upload", emptyMap(), setOf(Method.POST), UploadController::upload.asRouteHandler<UploadController>()),

    // TODO: simplify
    Route("action", "/action/{name}", mapOf("name" to "\\w+"), setOf(Method.POST), object: RouteHandler {
        override fun invoke(context: Any, params: Map<String, String>): RouteResponse {
            val action = params["name"]!!
            if(WebActions.isValidAction(action)) {
                return object: HttpResponse(200) {
                    override fun write(outputStream: OutputStream, writer: PrintWriter) {
                        super.write(outputStream, writer)
                        WebActions.perform(context as HttpInfo, action)
                    }
                }
            } else {
                throw RouteNotFoundException("Action '$action' does not exist")
            }
        }
    }),
    Route("playercontrol_action", "/action/playercontrol/{name}", mapOf("name" to "\\w+"), setOf(Method.POST), object: RouteHandler {
        override fun invoke(context: Any, params: Map<String, String>): RouteResponse {
            val action = "playercontrol/${params["name"]!!}"
            if(WebActions.isValidAction(action)) {
                return object: HttpResponse(200) {
                    override fun write(outputStream: OutputStream, writer: PrintWriter) {
                        super.write(outputStream, writer)
                        WebActions.perform(context as HttpInfo, action)
                    }
                }
            } else {
                throw RouteNotFoundException("Action '$action' does not exist")
            }
        }
    }),

    Route("index", "/", emptyMap(), setOf(Method.GET), IndexController::index.asRouteHandler<IndexController>()),

    Route("music_index", "/musics", emptyMap(), setOf(Method.GET), MusicController::index.asRouteHandler<MusicController>()),
    Route("music_index2", "/music/", emptyMap(), setOf(Method.GET), MusicController::index.asRouteHandler<MusicController>()),
    Route("show_music", "/music/{musicName}", emptyMap(), setOf(Method.GET), MusicController::show.asRouteHandler<MusicController>()),

    Route("show_user", "/user/{username}", emptyMap(), setOf(Method.GET), UserController::show.asRouteHandler<UserController>()),

    Route("quote", "/quote", emptyMap(), setOf(Method.GET), IndexController::quote.asRouteHandler<UserController>())
)

/**
 * Response sent if a route has been reached but the underlying method does not correspond in terms of arguments.
 * Path arguments and method arguments do not match (either in name or type)
 */
class IncompleteRouteResponse(
    val functionParameters: List<KParameter>,
    val params: Map<String, String>
) : HttpResponse(404) {
    override fun write(outputStream: OutputStream, writer: PrintWriter) {
        super.write(outputStream, writer)
        writer.println("required: "+functionParameters.map { it.name }.joinToString(", "))
        writer.println("got: "+params.keys.joinToString(", "))
    }
}

/**
 * Converts a given controller method to a RouteHandler instance that will convert the parameters passed inside the URL
 * and call the controller method with the converted parameters. Match done with parameters names
 */
private inline fun <reified T: Controller> KFunction<RouteResponse>.asRouteHandler(): RouteHandler {
    val owner = T::class
    val functionParameters = this.parameters.filter { it != this.instanceParameter }
    val function = this
    return object: RouteHandler {
        override fun invoke(context: Any, params: Map<String, String>): RouteResponse {
            val controller = owner.primaryConstructor!!.call(context as HttpInfo) // create a new controller instance for this session

            // fill in the method arguments
            val filledParameters = mutableMapOf<KParameter, Any>()
            for(param in functionParameters) {
                val value = params[param.name] ?: return IncompleteRouteResponse(functionParameters, params)
                filledParameters[param] = convertToType(param.type, value)
            }
            filledParameters[function.instanceParameter!!] = controller
            return function.callBy(filledParameters)
        }

        private fun convertToType(type: KType, value: String): Any {
            return when(type.javaType) {
                Integer::class.java, Int::class.java, java.lang.Integer.TYPE -> value.toInt()
                Float::class.java, java.lang.Float.TYPE -> value.toFloat()
                Long::class.java, java.lang.Long.TYPE -> value.toLong()
                Double::class.java, java.lang.Double.TYPE -> value.toDouble()
                String::class.java -> value
                else -> error("Unable to convert type $type (of java class ${type.javaType})")
            }
        }
    }
}

/**
 * Router responsible of routing a path to the correct Controller.
 * Attempts to read inside the classpath for static resources when none of the defined routes match.
 * @see routes for a list of all routes
 */
object TinyJukeboxRouter: Router(RouteMatcher(*TinyJukeboxRoutes)) {

    /**
     * Path representing the root of the music folder
     */
    private val rootPath = Paths.get("/")

    private object StaticController: Controller(HttpInfo.createFake())

    fun get(path: String, context: HttpInfo): HttpResponse {
        val response = this.route(path, Method.GET, context)
        if(response is InvalidRouteResponse) {
            val newPath = Paths.get(path)
            val valid = newPath.startsWith(rootPath) // the path MUST be within the server
            if(!valid) {
                println(">> Attempted to access invalid path outside of project $newPath - $rootPath")
                return TextResponse("text/html", "404")
            }
            return StaticController.serve(path)
        }
        return response as HttpResponse
    }

    fun post(path: String, context: HttpInfo): HttpResponse {
        val response = this.route(path, Method.POST, context)
        if(response is InvalidRouteResponse) {
            return TextResponse("text/text", "Not found: ${response.path} (${response.method}); ${response.message}", 404)
        }
        return response as HttpResponse
    }

}