package be.immoj.data

import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlin.system.measureTimeMillis
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class BenchmarkTest {

    @Test
    fun benchmark() = runBlocking {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://www.google.com").build()

        println("Warming up...")
        try { client.newCall(request).execute().close() } catch(e: Exception) {}

        val timeSync = measureTimeMillis {
            val jobs = (1..50).map {
                launch(Dispatchers.IO) {
                    try {
                        val response = client.newCall(request).execute()
                        response.close()
                    } catch (e: Exception) {}
                }
            }
            jobs.joinAll()
        }
        println("Sync execution time: $timeSync ms")

        val timeAsync = measureTimeMillis {
            val jobs = (1..50).map {
                launch {
                    try {
                        val response = client.newCall(request).await()
                        response.close()
                    } catch (e: Exception) {}
                }
            }
            jobs.joinAll()
        }
        println("Async execution time: $timeAsync ms")

        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
