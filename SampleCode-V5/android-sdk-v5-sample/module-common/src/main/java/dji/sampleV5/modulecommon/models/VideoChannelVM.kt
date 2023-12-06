package dji.sampleV5.modulecommon.models

import android.util.Log
import android.view.WindowManager.BadTokenException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dji.sampleV5.modulecommon.api.RabbitMq
import dji.sampleV5.modulecommon.data.VideoChannelInfo
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.v5.common.video.channel.VideoChannelState
import dji.v5.common.video.channel.VideoChannelType
import dji.v5.common.video.interfaces.IVideoChannel
import dji.v5.common.video.interfaces.VideoChannelStateChangeListener
import dji.v5.et.action
import dji.v5.et.cancelListen
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager
import dji.v5.manager.datacenter.MediaDataCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoChannelVM(channelType: VideoChannelType) : DJIViewModel() {
    val videoChannelInfo = MutableLiveData<VideoChannelInfo>()
    var videoChannel: IVideoChannel? = null
    var videoChannelStateListener: VideoChannelStateChangeListener? = null
    var curChannelType: VideoChannelType? = null
    var fcHasInit = false

    val rabbitMq = RabbitMq()
    init {
        MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(channelType)
            ?.let {
                videoChannel = it
                curChannelType = channelType
                if (videoChannelInfo.value == null) {
                    videoChannelInfo.value = VideoChannelInfo(videoChannel!!.videoChannelStatus)
                }
                videoChannelInfo.value?.streamSource = videoChannel!!.streamSource
                videoChannelInfo.value?.videoChannelType = videoChannel!!.videoChannelType
                videoChannelInfo.value?.format = videoChannel!!.videoStreamFormat.name
                videoChannelStateListener = VideoChannelStateChangeListener { _, to ->
                    /**
                     * 码流通道切换事件回调方法
                     *
                     * @param from 码流通道前一个状态
                     * @param to 码流通道当前状态
                     */
                    if (videoChannelInfo.value == null) {
                        videoChannelInfo.value = VideoChannelInfo(to)
                    } else {
                        videoChannelInfo.value?.videoChannelState = to
                    }
                    if (to == VideoChannelState.ON || to == VideoChannelState.SOCKET_ON) {
                        videoChannelInfo.value?.streamSource = videoChannel!!.streamSource
                    } else {
                        videoChannelInfo.value?.streamSource = null
                    }
                    refreshVideoChannelInfo()
                }
                initListeners()
            }

        addConnectionListener()
        //防止进入图传界面的时候还处于回放状态
        CameraKey.KeyExitPlayback.create(0).action()
    }

    override fun onCleared() {
        removeListeners()
        removeConnectionListener()
    }

    private fun initListeners() {
        videoChannel?.let {
            it.addVideoChannelStateChangeListener(videoChannelStateListener)
        }
    }

    private fun removeListeners() {
        videoChannel?.let {
            it.removeVideoChannelStateChangeListener(videoChannelStateListener)
        }
    }

    private fun addConnectionListener() {
        FlightControllerKey.KeyConnection.create().listen(this) {
            it?.let {
                //飞机重启的时候更新一下所持有的channel
                if (it and fcHasInit) {
                    curChannelType?.let { it1 ->
                        MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                            it1
                        ).let {
                            videoChannel = it
                        }
                    }
                    removeListeners()
                    initListeners()
                }
                fcHasInit = true
            }
        }
    }

    private fun removeConnectionListener(){
        FlightControllerKey.KeyConnection.create().cancelListen(this)
    }

    fun refreshVideoChannelInfo() {
        videoChannelInfo.postValue(videoChannelInfo.value)
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
            rabbitMq.prepareConnection(queueName)
        }
    }

    fun publishMessage(queue: String, message: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            rabbitMq.publishMessage(queue, message)
            getFps()
        }
    }

    private var startTimeFrame = 0L
    private var counter = 0

    private val _actualFps: MutableLiveData<Int> = MutableLiveData(0)
    val actualFps: LiveData<Int> = _actualFps
    private fun getFps() {
        if (startTimeFrame == 0L) {
            startTimeFrame = System.currentTimeMillis()
            counter++
        } else {
            val difference: Long = System.currentTimeMillis() - startTimeFrame

            val seconds = difference / 1000.0

            if(seconds >= 1) {
                _actualFps.postValue(counter)
                counter = 0
                startTimeFrame = System.currentTimeMillis()
            }else{
                counter++
            }
        }
        Log.i("CameraProcessImage", "fps ${_actualFps.value}")
    }
}