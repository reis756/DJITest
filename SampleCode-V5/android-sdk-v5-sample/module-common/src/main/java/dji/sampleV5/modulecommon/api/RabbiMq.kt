package dji.sampleV5.modulecommon.api

import android.util.Log
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URISyntaxException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException

class RabbitMq {
    var factory = ConnectionFactory()
    var channel: Channel? = null

    fun setupConnectionFactory(
        userName: String,
        password: String,
        virtualHost: String,
        host: String,
        port: Int
    ) {
        try {
            factory.username = userName
            factory.password = password
            factory.virtualHost = virtualHost
            factory.host = host
            factory.port = port
            factory.isAutomaticRecoveryEnabled = false
        } catch (e1: KeyManagementException) {
            e1.printStackTrace()
        } catch (e1: NoSuchAlgorithmException) {
            e1.printStackTrace()
        } catch (e1: URISyntaxException) {
            e1.printStackTrace()
        }
    }

    suspend fun prepareConnection(queueName: String) {
        withContext(Dispatchers.Default) {
            while (true) {
                try {
                    val connection: Connection = factory.newConnection()
                    channel = connection.createChannel()
                    channel?.queueDeclare(queueName, false, false, false, null)
                } catch (e: InterruptedException) {
                    Log.d("RabbitMQ", "Interrupted: " + e.javaClass.name)
                    break
                } catch (e: Exception) {
                    Log.d("RabbitMQ", "Connection broken: " + e.javaClass.name)
                    try {
                        Thread.sleep(5000) //sleep and then try again
                    } catch (e1: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    suspend fun publishMessage(queueName: String, message: String) {
        withContext(Dispatchers.Default) {
            try {
                Log.d("RabbitMQ", "[q] $message")
                channel?.basicPublish("", queueName, null, message.toByteArray())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }
}