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
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque

class RabbitMq {
    private var factory = ConnectionFactory()
    private var channel: Channel? = null
    private val queue: BlockingDeque<String> = LinkedBlockingDeque()

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

    fun prepareConnection(queueName: List<String>) {
        while (true) {
            try {
                val connection: Connection = factory.newConnection()
                channel = connection.createChannel()
                queueName.forEach {
                    channel?.queueDeclare(it, true, false, false, null)
                }
                while (true) {
                    val message = queue.takeFirst()
                    try {
                        queueName.forEach {
                            publishMessage(it, message.toByteArray())
                        }
                    } catch (e: Exception) {
                        Log.d("RabbitMQ", "[f] $message")
                        throw e
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.d("RabbitMQ", "Connection broken: " + e.javaClass.name)
                try {
                    Thread.sleep(5000)
                } catch (e1: InterruptedException) {
                    break
                }
            }
        }
    }

    fun publishMessage(queueName: String, message: ByteArray) {
        try {
            channel?.basicPublish("", queueName, null, message)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}