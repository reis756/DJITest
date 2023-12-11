package dji.sampleV5.modulecommon.models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dji.sampleV5.modulecommon.api.RabbitMq
import dji.sampleV5.modulecommon.data.DeviceLocation
import dji.sampleV5.modulecommon.pages.DefaultLayoutTestFragment.Companion.RABBITMQ_QUEUE_LOCATION_NAME
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.video.channel.VideoChannelState
import dji.v5.common.video.channel.VideoChannelType
import dji.v5.et.create
import dji.v5.et.get
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ILiveStreamManager
import dji.v5.manager.datacenter.livestream.*
import dji.v5.manager.datacenter.livestream.settings.AgoraSettings
import dji.v5.manager.datacenter.livestream.settings.GB28181Settings
import dji.v5.manager.datacenter.livestream.settings.RtmpSettings
import dji.v5.manager.datacenter.livestream.settings.RtspSettings
import dji.v5.manager.datacenter.video.VideoStreamManager
import dji.v5.utils.common.ContextUtil
import dji.v5.utils.common.DjiSharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ClassName : LiveStreamVM
 * Description : 直播VM
 * Author : daniel.chen
 * CreateDate : 2022/3/23 11:04 上午
 * Copyright : ©2022 DJI All Rights Reserved.
 */
class LiveStreamVM : DJIViewModel() {
    private val liveStreamStatusListener: LiveStreamStatusListener
    private val RTMP_KEY = "livestream-rtmp"
    private val RTSP_KEY = "livestream-rtsp"
    private val GB28181_KEY = "livestream-gb28181"
    private val AGORA_KEY = "livestream-agora"
    val curLiveStreanmStatus = MutableLiveData<LiveStreamStatus>()
    val curLiveStreamError = MutableLiveData<IDJIError>()
    val streamManager: ILiveStreamManager = MediaDataCenter.getInstance().liveStreamManager
    val mediaManager = MediaDataCenter.getInstance().mediaManager
    val videoStreamManager = MediaDataCenter.getInstance().videoStreamManager

    val rabbitMq = RabbitMq()
    private var startTimeFrame = 0L
    private var counter = 0
    private var kbps = 0.0

    private val _info: MutableLiveData<String> = MutableLiveData()
    val info: LiveData<String> = _info
    private val _error: MutableLiveData<String> = MutableLiveData()
    val error: LiveData<String> = _error

    private var repeatJob: Job? = null
    init {
        liveStreamStatusListener = object : LiveStreamStatusListener {
            override fun onLiveStreamStatusUpdate(status: LiveStreamStatus?) {
                status?.let {
                    curLiveStreanmStatus.postValue(it)
                }
            }

            override fun onError(error: IDJIError?) {
                error?.let {
                    curLiveStreamError.postValue(it)
                }
            }
        }
        addListener()
    }

    override fun onCleared() {
        super.onCleared()
        removeListener()
    }

    fun startStream(callback: CommonCallbacks.CompletionCallback){
        streamManager.startStream(callback)
    }

    fun stopStream(callback: CommonCallbacks.CompletionCallback){
        streamManager.stopStream(callback)
    }

    fun isStreaming():Boolean{
        return streamManager.isStreaming;
    }

    fun setLiveStreamConfig(liveStreamSettings: LiveStreamSettings) {
        streamManager.liveStreamSettings = liveStreamSettings;
    }

    fun getLiveStreamConfig(): LiveStreamSettings? {
        return streamManager.liveStreamSettings
    }

    fun setVideoChannel(videoChannel:VideoChannelType){
        streamManager.videoChannelType = videoChannel
    }

    fun getVideoChannel():VideoChannelType{
        return streamManager.videoChannelType
    }

    fun setLiveStreamQuality(liveStreamQuality:StreamQuality){
        streamManager.liveStreamQuality = liveStreamQuality
    }

    fun getLiveStreamQuality():StreamQuality{
        return streamManager.liveStreamQuality
    }

    fun setLiveVideoBitRateMode(bitRateMode: LiveVideoBitrateMode){
        streamManager.liveVideoBitrateMode = bitRateMode
    }

    fun getLiveVideoBitRateMode():LiveVideoBitrateMode{
        return streamManager.liveVideoBitrateMode
    }

    fun setLiveVideoBitRate(bitrate: Int) {
        streamManager.liveVideoBitrate = bitrate
    }

    fun getLiveVideoBitRate():Int{
        return streamManager.liveVideoBitrate
    }

    fun setLiveAudioEnabled(enabled: Boolean) {
        streamManager.isLiveAudioEnabled = enabled
    }

    fun getLiveAudioEnabled(): Boolean {
        return streamManager.isLiveAudioEnabled
    }

    fun getLiveStreamTypes(): Array<LiveStreamType> {
        return listOf(
            LiveStreamType.RTMP,
            LiveStreamType.RTSP,
            LiveStreamType.GB28181,
            LiveStreamType.AGORA
        ).toTypedArray()
    }

    fun getLiveStreamBitRateModes(): Array<LiveVideoBitrateMode> {
        return listOf(LiveVideoBitrateMode.AUTO, LiveVideoBitrateMode.MANUAL).toTypedArray()
    }

    fun getLiveStreamQualities(): Array<StreamQuality> {
        return listOf(
            StreamQuality.FULL_HD,
            StreamQuality.HD,
            StreamQuality.SD
        ).toTypedArray()
    }

    fun getLiveStreamChannelTypes(): Array<VideoChannelType> {
        return listOf(
            VideoChannelType.PRIMARY_STREAM_CHANNEL,
            VideoChannelType.SECONDARY_STREAM_CHANNEL
        ).toTypedArray()
    }

    fun setRTMPConfig(rtmpUrl: String) {
        val liveStreamConfig = LiveStreamSettings.Builder()
            .setLiveStreamType(LiveStreamType.RTMP)
            .setRtmpSettings(
                RtmpSettings.Builder()
                    .setUrl(rtmpUrl)
                    .build()
            )
            .build()
        DjiSharedPreferencesManager.putString(ContextUtil.getContext(), RTMP_KEY, rtmpUrl)
        setLiveStreamConfig(liveStreamConfig)
    }

    fun getRtmpUrl(): String {
        return DjiSharedPreferencesManager.getString(ContextUtil.getContext(), RTMP_KEY, "")
    }

    fun setRTSPConfig(userName: String, password: String, port: Int) {
        val rtspConfig = RtspSettings.Builder()
            .setUserName(userName)
            .setPassWord(password)
            .setPort(port)
            .build()
        val liveStreamConfig = LiveStreamSettings.Builder()
            .setLiveStreamType(LiveStreamType.RTSP)
            .setRtspSettings(rtspConfig)
            .build()
        val rtspSettings = userName + "^_^" + password + "^_^" + port.toString()
        DjiSharedPreferencesManager.putString(ContextUtil.getContext(), RTSP_KEY, rtspSettings)
        setLiveStreamConfig(liveStreamConfig)
    }

    fun getRtspSettings():String{
        return DjiSharedPreferencesManager.getString(ContextUtil.getContext(), RTSP_KEY, "")
    }

    fun setGB28181(
        serverIP: String,
        serverPort: Int,
        serverID: String,
        agentID: String,
        channel: String,
        localPort: Int,
        password: String
    ) {
        val gb28181Config = GB28181Settings.Builder()
            .setServerIP(serverIP)
            .setServerPort(serverPort)
            .setServerID(serverID)
            .setAgentID(agentID)
            .setChannel(channel)
            .setLocalPort(localPort)
            .setPassword(password)
            .build()
        val liveStreamConfig = LiveStreamSettings.Builder()
            .setLiveStreamType(LiveStreamType.GB28181)
            .setGB28181Settings(gb28181Config)
            .build()
        val gb28181Settings =
            serverIP + "^_^" + serverPort.toString() + "^_^" + serverID + "^_^" + agentID + "^_^" + channel + "^_^" + localPort.toString() + "^_^" + password
        DjiSharedPreferencesManager.putString(
            ContextUtil.getContext(),
            GB28181_KEY,
            gb28181Settings
        )
        setLiveStreamConfig(liveStreamConfig)
    }

    fun getGb28181Settings():String{
        return DjiSharedPreferencesManager.getString(ContextUtil.getContext(), GB28181_KEY, "")
    }

    fun setAgoraConfig(channelId: String, token: String, uid: String) {
        val agoraConfig = AgoraSettings.Builder()
            .setChannelId(channelId)
            .setToken(token)
            .setUid(uid)
            .setEnableSafety(false)
            .build()
        val liveStreamConfig = LiveStreamSettings.Builder()
            .setLiveStreamType(LiveStreamType.AGORA)
            .setAgoraSettings(agoraConfig)
            .build()
        val agoraSettings = channelId+"^_^"+token+"^_^"+uid
        DjiSharedPreferencesManager.putString(
            ContextUtil.getContext(),
            AGORA_KEY,
            agoraSettings
        )
        setLiveStreamConfig(liveStreamConfig)
    }

    fun getAgoraSettings():String{
        return DjiSharedPreferencesManager.getString(ContextUtil.getContext(), AGORA_KEY, "")
    }

    fun addListener() {
        streamManager.addLiveStreamStatusListener(liveStreamStatusListener)
    }

    fun removeListener() {
        streamManager.removeLiveStreamStatusListener(liveStreamStatusListener)
    }


    fun refreshLiveStreamStatus() {
        curLiveStreanmStatus.postValue(curLiveStreanmStatus.value)
    }

    fun refreshLiveStreamError() {
        curLiveStreamError.postValue(curLiveStreamError.value)
    }

    fun getChannelStatus(channel: VideoChannelType): VideoChannelState {
        return VideoStreamManager.getInstance().getAvailableVideoChannel(channel)
            ?.let { it.videoChannelStatus } ?: let { VideoChannelState.CLOSE }
    }

    fun setupRabbitMqConnectionFactory(
        userName: String,
        password: String,
        virtualHost: String,
        host: String,
        port: Int,
        queueName: List<String>
    ) {
        rabbitMq.setupConnectionFactory(userName, password, virtualHost, host, port)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                rabbitMq.prepareConnection(queueName)

                repeatJob = sendLocationToServer()

            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message
            }
        }
    }

    fun publishMessage(queue: String, message: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                rabbitMq.publishMessage(queue, message)
                getFps(message)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                _error.value = e.message
            }
        }
    }

    fun getFps(biteArray: ByteArray) {
        if (startTimeFrame == 0L) {
            startTimeFrame = System.currentTimeMillis()
            counter++
        } else {
            val difference: Long = System.currentTimeMillis() - startTimeFrame

            val seconds = difference / 1000.0

            if(seconds >= 1) {
                _info.postValue("$counter fps\n${String.format("%.2f", kbps)} kbps")
                counter = 0
                kbps = 0.0
                startTimeFrame = System.currentTimeMillis()
            }else{
                counter++
                kbps =+ getByteArraySize(biteArray)
            }
        }
        Log.i("FrameByFrame", "$counter fps | ${String.format("%.2f", kbps)} kbps")
    }

    private fun getByteArraySize(biteArray: ByteArray): Double {
        return biteArray.size / 1024.0
    }

    private fun sendLocationToServer(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            while (NonCancellable.isActive) {
                val location = getAircraftLocation()
                val locationJson = Gson().toJson(
                    DeviceLocation(
                        location?.latitude,
                        location?.longitude
                    )
                )
                Log.i("FrameByFrame", "latitude:  ${location?.latitude} | longitude: ${location?.longitude}")
                publishMessage(RABBITMQ_QUEUE_LOCATION_NAME, locationJson.toByteArray())

                delay(5000L)
            }
        }
    }

    private fun cancelSendLocationToServer() {
        repeatJob?.cancel()
    }

    private fun getAircraftLocation() = FlightControllerKey.KeyAircraftLocation.create().get(
        LocationCoordinate2D(0.0, 0.0)
    )
}