package tech.muso.demo.rekoil

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


fun subscribe(symbol: String): Flow<String> = flow {
    // connect to the server (localhost:8080; maps to 10.0.2.2:8080 inside the android emulator)
    val client = HttpClient(CIO).config { install(WebSockets) }
    client.ws(method = HttpMethod.Get, host = "10.0.2.2", port = 8080, path = "/test/v1/bars") {
        send(Frame.Text(symbol)) // todo: process some JSON for real

        // launch asynchronously 1000 WebSocket calls every 1000 ms
        async {
            for (i in 0 until 1000) {
                delay(500)
                send(Frame.Text("Fetching... [$i]"))

                // early exit; todo: design and document actual mock api
                if (i == 300) {
                    send(Frame.Text("CLOSE"))
                }

            }
        }

        var j = 0
        for (message in incoming) {
            // ignore future responses that will not print.
            if (message !is Frame.Text) {

            } else {
                val string = message.readText()
                if (!string.startsWith("CONNECTED")) {
                    emit(string)
                }
                println("Server said [${++j}]: $string")
            }
        }
    }
}