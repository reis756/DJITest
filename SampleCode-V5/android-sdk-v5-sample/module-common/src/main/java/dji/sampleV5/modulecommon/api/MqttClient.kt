package dji.sampleV5.modulecommon.api

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.N)
class MqttClient {

    private lateinit var client: Mqtt3Client

    fun connect() {
        client = Mqtt3Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost(BROKER)
            .serverPort(1883)
            .buildAsync()

        client.toAsync().connect()
            .whenComplete { mqtt3ConnAck, throwable ->
                if (throwable != null) {
                    Log.v(TAG, " connection failed")
                } else {
                    Log.v(TAG, " connected: code = ${mqtt3ConnAck.returnCode}, type = ${mqtt3ConnAck.type}")
                    subscribe()
                }
            }
    }

    private fun subscribe() {
        client.toAsync().subscribeWith()
            .topicFilter(TOPIC)
            .callback { mqtt3Publish ->
                Log.v(TAG, " Message: ${mqtt3Publish.payload}")
            }
            .send()
            .whenComplete { mqtt3SubAck, throwable ->
                if (throwable != null) {
                    Log.v(TAG, " failure to subscribe")
                } else {
                    Log.v(TAG, " successful subscribe:  ${mqtt3SubAck.type}")
                }
            }
    }

    fun publish(payload: ByteArray) {
        client.toAsync().publishWith()
            .topic(TOPIC)
            .payload(payload)
            .send()
            .whenComplete { mqtt3Publish, throwable ->
                if (throwable != null) {
                    Log.v(TAG, " failure to publish")
                } else {
                    Log.v(
                        TAG,
                        " successful to publish: bytearray = ${payload.toString()} , payload = ${mqtt3Publish.payload}, topic = ${mqtt3Publish.topic} "
                    )
                }
            }
    }

    fun disconnect() {
        client.toAsync().disconnect().whenComplete { mqtt3ConnAck, throwable ->
            if (throwable != null) {
                Log.v(TAG, " error to disconnect")
            } else {
                Log.v(TAG, " successful to disconnect")
            }
        }
    }

    companion object {
        const val TAG = "MqttClient"
        const val BROKER = "54.232.143.5"
        const val TOPIC = "topico/drone/m30"
    }
}