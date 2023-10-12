package dji.sampleV5.modulecommon.pages

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dji.sampleV5.modulecommon.R
import dji.sampleV5.modulecommon.models.LiveStreamVM
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.video.channel.VideoChannelState
import dji.v5.common.video.channel.VideoChannelType
import dji.v5.common.video.interfaces.VideoChannelStateChangeListener
import dji.v5.common.video.stream.PhysicalDevicePosition
import dji.v5.common.video.stream.StreamSource
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.livestream.LiveStreamType
import dji.v5.manager.datacenter.livestream.LiveVideoBitrateMode
import dji.v5.manager.datacenter.livestream.StreamQuality
import dji.v5.utils.common.JsonUtil
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.StringUtils
import dji.v5.utils.common.ToastUtils
import dji.v5.ux.cameracore.widget.autoexposurelock.AutoExposureLockWidget
import dji.v5.ux.cameracore.widget.cameracontrols.CameraControlsWidget
import dji.v5.ux.cameracore.widget.cameracontrols.exposuresettings.ExposureSettingsPanel
import dji.v5.ux.cameracore.widget.cameracontrols.lenscontrol.LensControlWidget
import dji.v5.ux.cameracore.widget.focusexposureswitch.FocusExposureSwitchWidget
import dji.v5.ux.cameracore.widget.focusmode.FocusModeWidget
import dji.v5.ux.cameracore.widget.fpvinteraction.FPVInteractionWidget
import dji.v5.ux.core.base.SchedulerProvider.io
import dji.v5.ux.core.extension.hide
import dji.v5.ux.core.extension.toggleVisibility
import dji.v5.ux.core.panel.systemstatus.SystemStatusListPanelWidget
import dji.v5.ux.core.panel.topbar.TopBarPanelWidget
import dji.v5.ux.core.util.CameraUtil
import dji.v5.ux.core.util.CommonUtils
import dji.v5.ux.core.util.DataProcessor
import dji.v5.ux.core.widget.fpv.FPVStreamSourceListener
import dji.v5.ux.core.widget.fpv.FPVWidget
import dji.v5.ux.core.widget.hsi.HorizontalSituationIndicatorWidget
import dji.v5.ux.core.widget.hsi.PrimaryFlightDisplayWidget
import dji.v5.ux.map.MapWidget
import dji.v5.ux.mapkit.core.maps.DJIMap
import dji.v5.ux.training.simulatorcontrol.SimulatorControlWidget
import dji.v5.ux.training.simulatorcontrol.SimulatorControlWidget.UIState.VisibilityUpdated
import dji.v5.ux.visualcamera.CameraNDVIPanelWidget
import dji.v5.ux.visualcamera.CameraVisiblePanelWidget
import dji.v5.ux.visualcamera.zoom.FocalZoomWidget
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import kotlinx.android.synthetic.main.frag_live_stream_page.fbStartStop
import kotlinx.android.synthetic.main.frag_live_stream_page.fbStreamingChannel
import kotlinx.android.synthetic.main.frag_live_stream_page.fbStreamingConfig
import kotlinx.android.synthetic.main.frag_live_stream_page.fbStreamingInfo
import kotlinx.android.synthetic.main.frag_live_stream_page.fbStreamingQuality
import kotlinx.android.synthetic.main.frag_live_stream_page.tv_live_stream_info
import kotlinx.android.synthetic.main.video_play_page.surfaceView
import java.util.concurrent.TimeUnit

/**
 * ClassName : LiveStreamFragment
 * Description : 直播功能
 * Author : daniel.chen
 * CreateDate : 2022/3/23 10:58 上午
 * Copyright : ©2022 DJI All Rights Reserved.
 */
class DefaultLayoutTestFragment : DJIFragment(), View.OnClickListener, SurfaceHolder.Callback {
    private val liveStreamVM: LiveStreamVM by viewModels()
    private lateinit var dialog: AlertDialog
    private lateinit var configDialog: AlertDialog
    private var checkedItem: Int = -1
    private var isConfigSelected = false
    private var showStreamInfo = true
    private var liveStreamType: LiveStreamType = LiveStreamType.UNKNOWN
    private var liveStreamBitrateMode: LiveVideoBitrateMode = LiveVideoBitrateMode.AUTO
    private var liveStreamQuality: StreamQuality = StreamQuality.UNKNOWN
    private val msg = "input is null"

    var fps: Int = -1
    var vbps: Int = -1
    var isStreaming: Boolean = false
    var resolution_w: Int = -1
    var resolution_h: Int = -1
    var packet_loss: Int = -1
    var packet_cache_len: Int = -1
    var rtt: Int = -1
    var error: IDJIError? = null

    var curWidth: Int = -1
    var curHeight: Int = -1

    private val TAG = LogUtils.getTag(this)
    private lateinit var primaryFpvWidget: FPVWidget
    private lateinit var fpvInteractionWidget: FPVInteractionWidget
    private lateinit var secondaryFPVWidget: FPVWidget
    private lateinit var systemStatusListPanelWidget: SystemStatusListPanelWidget
    private lateinit var simulatorControlWidget: SimulatorControlWidget
    private lateinit var lensControlWidget: LensControlWidget
    private lateinit var autoExposureLockWidget: AutoExposureLockWidget
    private lateinit var focusModeWidget: FocusModeWidget
    private lateinit var focusExposureSwitchWidget: FocusExposureSwitchWidget
    private lateinit var cameraControlsWidget: CameraControlsWidget
    private lateinit var horizontalSituationIndicatorWidget: HorizontalSituationIndicatorWidget
    private lateinit var exposureSettingsPanel: ExposureSettingsPanel
    private lateinit var pfvFlightDisplayWidget: PrimaryFlightDisplayWidget
    private lateinit var ndviCameraPanel: CameraNDVIPanelWidget
    private lateinit var visualCameraPanel: CameraVisiblePanelWidget
    private lateinit var focalZoomWidget: FocalZoomWidget
    private lateinit var mapWidget: MapWidget
    private lateinit var topBarPanel: TopBarPanelWidget
    private lateinit var fpvParentView: ConstraintLayout

    private var compositeDisposable: CompositeDisposable? = null
    private val cameraSourceProcessor = DataProcessor.create(
        CameraSource(
            PhysicalDevicePosition.UNKNOWN,
            CameraLensType.UNKNOWN
        )
    )
    private var primaryChannelStateListener: VideoChannelStateChangeListener? = null
    private var secondaryChannelStateListener: VideoChannelStateChangeListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.frag_default_layout_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setLayerType(View.LAYER_TYPE_NONE, null)

        fpvParentView = view.findViewById<ConstraintLayout>(R.id.fpv_holder)
        fpvParentView.setLayerType(View.LAYER_TYPE_NONE, null)
        topBarPanel = view.findViewById<TopBarPanelWidget>(R.id.panel_top_bar)
        primaryFpvWidget = view.findViewById<FPVWidget>(R.id.widget_primary_fpv)
        fpvInteractionWidget = view.findViewById<FPVInteractionWidget>(R.id.widget_fpv_interaction)
        secondaryFPVWidget = view.findViewById<FPVWidget>(R.id.widget_secondary_fpv)
        systemStatusListPanelWidget =
            view.findViewById<SystemStatusListPanelWidget>(R.id.widget_panel_system_status_list)
        simulatorControlWidget = view.findViewById<SimulatorControlWidget>(R.id.widget_simulator_control)
        lensControlWidget = view.findViewById<LensControlWidget>(R.id.widget_lens_control)
        ndviCameraPanel = view.findViewById<CameraNDVIPanelWidget>(R.id.panel_ndvi_camera)
        visualCameraPanel = view.findViewById<CameraVisiblePanelWidget>(R.id.panel_visual_camera)
        autoExposureLockWidget =
            view.findViewById<AutoExposureLockWidget>(R.id.widget_auto_exposure_lock)
        focusModeWidget = view.findViewById<FocusModeWidget>(R.id.widget_focus_mode)
        focusExposureSwitchWidget =
            view.findViewById<FocusExposureSwitchWidget>(R.id.widget_focus_exposure_switch)
        exposureSettingsPanel =
            view.findViewById<ExposureSettingsPanel>(R.id.panel_camera_controls_exposure_settings)
        pfvFlightDisplayWidget =
            view.findViewById<PrimaryFlightDisplayWidget>(R.id.widget_fpv_flight_display_widget)
        focalZoomWidget = view.findViewById<FocalZoomWidget>(R.id.widget_focal_zoom)
        cameraControlsWidget = view.findViewById<CameraControlsWidget>(R.id.widget_camera_controls)
        horizontalSituationIndicatorWidget =
            view.findViewById<HorizontalSituationIndicatorWidget>(R.id.widget_horizontal_situation_indicator)

        mapWidget = view.findViewById<MapWidget>(dji.v5.ux.R.id.widget_map)
        cameraControlsWidget.exposureSettingsIndicatorWidget
            .setStateChangeResourceId(dji.v5.ux.R.id.panel_camera_controls_exposure_settings)


        MediaDataCenter.getInstance().videoStreamManager.addStreamSourcesListener { sources: List<StreamSource>? ->
            activity?.runOnUiThread { updateFPVWidgetSource(sources) }
        }
        primaryFpvWidget.setOnFPVStreamSourceListener(object : FPVStreamSourceListener {
            override fun onStreamSourceUpdated(
                devicePosition: PhysicalDevicePosition,
                lensType: CameraLensType
            ) {
                cameraSourceProcessor.onNext(
                    CameraSource(devicePosition, lensType)
                )
            }
        })

        secondaryFPVWidget.setSurfaceViewZOrderOnTop(true)
        secondaryFPVWidget.setSurfaceViewZOrderMediaOverlay(true)

        mapWidget.initAMap { map: DJIMap ->
            // map.setOnMapClickListener(latLng -> onViewClick(mapWidget));
            val uiSetting = map.uiSettings
            uiSetting?.setZoomControlsEnabled(false)
        }
        mapWidget.onCreate(savedInstanceState)

        initListener()
        initLiveStreamInfo()
    }

    override fun onResume() {
        super.onResume()

        mapWidget.onResume()
        compositeDisposable = CompositeDisposable()
        compositeDisposable?.add(systemStatusListPanelWidget.closeButtonPressed()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { pressed: Boolean ->
                if (pressed) {
                    systemStatusListPanelWidget.hide()
                }
            })
        compositeDisposable?.add(simulatorControlWidget.getUIStateUpdates()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { simulatorControlWidgetState: SimulatorControlWidget.UIState? ->
                if (simulatorControlWidgetState is VisibilityUpdated) {
                    if ((simulatorControlWidgetState as VisibilityUpdated).isVisible) {
                        hideOtherPanels(simulatorControlWidget)
                    }
                }
            })
        compositeDisposable?.add(
            cameraSourceProcessor.toFlowable()
                .observeOn(io())
                .throttleLast(500, TimeUnit.MILLISECONDS)
                .subscribeOn(io())
                .subscribe(Consumer<CameraSource> { result: CameraSource ->
                    activity?.runOnUiThread(
                        Runnable { onCameraSourceUpdated(result.devicePosition, result.lensType) })
                })
        )
    }

    override fun onPause() {
        if (compositeDisposable != null) {
            compositeDisposable?.dispose()
            compositeDisposable = null
        }
        mapWidget.onPause()
        super.onPause()
    }

    private fun initListener() {
        fbStartStop.setOnClickListener(this)
        fbStreamingChannel.setOnClickListener(this)
        fbStreamingConfig.setOnClickListener(this)
        fbStreamingInfo.setOnClickListener(this)
        fbStreamingQuality.setOnClickListener(this)

        secondaryFPVWidget.setOnClickListener { v: View? -> swapVideoSource() }
        initChannelStateListener()

        val systemStatusWidget = topBarPanel.systemStatusWidget
        systemStatusWidget?.setOnClickListener { v: View? -> systemStatusListPanelWidget.toggleVisibility() }

        val simulatorIndicatorWidget = topBarPanel.simulatorIndicatorWidget
        simulatorIndicatorWidget?.setOnClickListener { v: View? -> simulatorControlWidget.toggleVisibility() }
    }

    private fun initLiveStreamInfo() {
        liveStreamVM.refreshLiveStreamError()
        liveStreamVM.refreshLiveStreamStatus()
        liveStreamVM.curLiveStreanmStatus.observe(viewLifecycleOwner) {
            it?.let {
                fps = it.fps
                vbps = it.vbps
                isStreaming = it.isStreaming
                resolution_w = it.resolution?.width!!
                resolution_h = it.resolution?.height!!
                packet_loss = it.packetLoss
                packet_cache_len = it.packetCacheLen
                rtt = it.rtt
                updateLiveStreamInfo()
            }
        }

        liveStreamVM.curLiveStreamError.observe(viewLifecycleOwner) {
            it?.let {
                error = it
                updateLiveStreamInfo()
            }
        }
    }

    private fun updateLiveStreamInfo() {
        val liveStreamInfo = "\nfps: ${fps}fps \n" +
                "vbps: ${vbps}Kbps \n" +
                "isStreaming: $isStreaming \n" +
                "resolution_w: $resolution_w \n" +
                "resolution_h: $resolution_h \n" +
                "packet_loss: ${packet_loss}% \n" +
                "packet_cache_len: $packet_cache_len \n" +
                "rtt: ${rtt}ms \n" +
                "error: $error"
        tv_live_stream_info.text = liveStreamInfo
    }

    private fun clearLiveStreamInfo() {
        fps = -1
        vbps = -1
        isStreaming = false
        resolution_w = -1
        resolution_h = -1
        packet_loss = -1
        packet_cache_len = -1
        rtt = -1
        tv_live_stream_info.text = getString(R.string.n_a)
    }

    //endregion
    private fun hideOtherPanels(widget: View?) {
        val panels = arrayOf<View>(
            simulatorControlWidget
        )
        for (panel in panels) {
            if (widget !== panel) {
                panel.visibility = View.GONE
            }
        }
    }

    private fun updateFPVWidgetSource(streamSources: List<StreamSource>?) {
        LogUtils.i(TAG, JsonUtil.toJson<StreamSource>(streamSources))
        if (streamSources == null) {
            return
        }

        if (streamSources.isEmpty()) {
            secondaryFPVWidget.visibility = View.GONE
            return
        }

        if (streamSources.size == 1) {
            secondaryFPVWidget.visibility = View.GONE
            return
        }
        secondaryFPVWidget.visibility = View.VISIBLE
    }

    private fun initChannelStateListener() {
        val primaryChannel =
            MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                VideoChannelType.PRIMARY_STREAM_CHANNEL
            )
        val secondaryChannel =
            MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                VideoChannelType.SECONDARY_STREAM_CHANNEL
            )
        if (primaryChannel != null) {
            primaryChannelStateListener =
                VideoChannelStateChangeListener { from: VideoChannelState?, to: VideoChannelState ->
                    val primaryStreamSource =
                        primaryChannel.streamSource
                    if (VideoChannelState.ON == to && primaryStreamSource != null) {
                        activity?.runOnUiThread({
                            primaryFpvWidget.updateVideoSource(
                                primaryStreamSource,
                                VideoChannelType.PRIMARY_STREAM_CHANNEL
                            )
                        })
                    }
                }
            primaryChannel.addVideoChannelStateChangeListener(primaryChannelStateListener)
        }
        if (secondaryChannel != null) {
            secondaryChannelStateListener =
                VideoChannelStateChangeListener { from: VideoChannelState?, to: VideoChannelState ->
                    val secondaryStreamSource =
                        secondaryChannel.streamSource
                    if (VideoChannelState.ON == to && secondaryStreamSource != null) {
                        activity?.runOnUiThread( {
                            secondaryFPVWidget.updateVideoSource(
                                secondaryStreamSource,
                                VideoChannelType.SECONDARY_STREAM_CHANNEL
                            )
                        })
                    }
                }
            secondaryChannel.addVideoChannelStateChangeListener(secondaryChannelStateListener)
        }
    }

    private fun removeChannelStateListener() {
        val primaryChannel =
            MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                VideoChannelType.PRIMARY_STREAM_CHANNEL
            )
        val secondaryChannel =
            MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                VideoChannelType.SECONDARY_STREAM_CHANNEL
            )
        primaryChannel?.removeVideoChannelStateChangeListener(primaryChannelStateListener)
        secondaryChannel?.removeVideoChannelStateChangeListener(secondaryChannelStateListener)
    }

    private fun onCameraSourceUpdated(
        devicePosition: PhysicalDevicePosition,
        lensType: CameraLensType
    ) {
        LogUtils.i(TAG, devicePosition, lensType)
        val cameraIndex = CameraUtil.getCameraIndex(devicePosition)
        updateViewVisibility(devicePosition, lensType)
        updateInteractionEnabled()
        //如果无需使能或者显示的，也就没有必要切换了。
        if (fpvInteractionWidget.isInteractionEnabled) {
            fpvInteractionWidget.updateCameraSource(cameraIndex, lensType)
            fpvInteractionWidget.updateGimbalIndex(CommonUtils.getGimbalIndex(devicePosition))
        }
        if (lensControlWidget.visibility == View.VISIBLE) {
            lensControlWidget.updateCameraSource(cameraIndex, lensType)
        }
        if (ndviCameraPanel.visibility == View.VISIBLE) {
            ndviCameraPanel.updateCameraSource(cameraIndex, lensType)
        }
        if (visualCameraPanel.visibility == View.VISIBLE) {
            visualCameraPanel.updateCameraSource(cameraIndex, lensType)
        }
        if (autoExposureLockWidget.visibility == View.VISIBLE) {
            autoExposureLockWidget.updateCameraSource(cameraIndex, lensType)
        }
        if (focusModeWidget.visibility == View.VISIBLE) {
            focusModeWidget.updateCameraSource(cameraIndex, lensType)
        }
        if (focusExposureSwitchWidget.visibility == View.VISIBLE) {
            focusExposureSwitchWidget.updateCameraSource(cameraIndex, lensType)
        }
        if (cameraControlsWidget.visibility == View.VISIBLE) {
            cameraControlsWidget.updateCameraSource(cameraIndex, lensType)
        }
        if (exposureSettingsPanel.visibility == View.VISIBLE) {
            exposureSettingsPanel.updateCameraSource(cameraIndex, lensType)
        }
        if (focalZoomWidget.visibility == View.VISIBLE) {
            focalZoomWidget.updateCameraSource(cameraIndex, lensType)
        }
        if (horizontalSituationIndicatorWidget.visibility == View.VISIBLE) {
            horizontalSituationIndicatorWidget.updateCameraSource(cameraIndex, lensType)
        }
    }

    private fun updateViewVisibility(
        devicePosition: PhysicalDevicePosition,
        lensType: CameraLensType
    ) {
        //只在fpv下显示
        pfvFlightDisplayWidget.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.VISIBLE else View.INVISIBLE

        //fpv下不显示
        lensControlWidget.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
        ndviCameraPanel.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
        visualCameraPanel.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
        autoExposureLockWidget.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
        focusModeWidget.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
        focusExposureSwitchWidget.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
        /*cameraControlsWidget.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE*/
        focalZoomWidget.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
        horizontalSituationIndicatorWidget.setSimpleModeEnable(devicePosition != PhysicalDevicePosition.NOSE)

        //有其他的显示逻辑，这里确保fpv下不显示
        if (devicePosition == PhysicalDevicePosition.NOSE) {
            exposureSettingsPanel.visibility = View.INVISIBLE
        }

        //只在部分len下显示
        ndviCameraPanel.visibility =
            if (CameraUtil.isSupportForNDVI(lensType)) View.VISIBLE else View.INVISIBLE
    }

    private fun swapVideoSource() {
        val primaryVideoChannel = primaryFpvWidget.videoChannelType
        val primaryStreamSource = primaryFpvWidget.getStreamSource()
        val secondaryVideoChannel = secondaryFPVWidget.videoChannelType
        val secondaryStreamSource = secondaryFPVWidget.getStreamSource()
        //两个source都存在的情况下才进行切换
        if (secondaryStreamSource != null && primaryStreamSource != null) {
            primaryFpvWidget.updateVideoSource(secondaryStreamSource, secondaryVideoChannel)
            secondaryFPVWidget.updateVideoSource(primaryStreamSource, primaryVideoChannel)
        }
    }

    private fun updateInteractionEnabled() {
        val newPrimaryStreamSource = primaryFpvWidget.getStreamSource()
        fpvInteractionWidget.isInteractionEnabled = false
        if (newPrimaryStreamSource != null) {
            fpvInteractionWidget.isInteractionEnabled =
                newPrimaryStreamSource.physicalDevicePosition != PhysicalDevicePosition.NOSE
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.fbStartStop -> {
                if (liveStreamVM.isStreaming()) {
                    stopStream()
                    fbStartStop.setImageResource(R.drawable.ic_play)
                } else {
                    startStream()
                    fbStartStop.setImageResource(R.drawable.ic_stop)
                }
            }

            R.id.fbStreamingChannel -> {
                if (liveStreamVM.getVideoChannel() != VideoChannelType.PRIMARY_STREAM_CHANNEL) {
                    judgeChannel(VideoChannelType.PRIMARY_STREAM_CHANNEL)
                } else {
                    judgeChannel(VideoChannelType.SECONDARY_STREAM_CHANNEL)
                }
            }

            R.id.fbStreamingConfig -> {
                //showSetLiveStreamConfigDialog()
                liveStreamVM.setRTSPConfig(
                    "123456",
                    "123",
                    "8554".toInt()
                )
                ToastUtils.showToast("RTSP config success")
            }

            R.id.fbStreamingInfo -> {
                showStreamInfo = !showStreamInfo
                tv_live_stream_info.isVisible = showStreamInfo
            }

            R.id.fbStreamingQuality -> {
                showSetLiveStreamQualityDialog()
            }

            R.id.btn_set_live_stream_config -> {
                showSetLiveStreamConfigDialog()
            }

            R.id.btn_get_live_stream_config -> {
                val streamConfig = liveStreamVM.getLiveStreamConfig()
                    ?.let { liveStreamVM.getLiveStreamConfig().toString() } ?: let { "null" }
                ToastUtils.showToast(streamConfig)
            }

            R.id.btn_start_live_stream -> {
                startStream()
            }

            R.id.btn_stop_live_stream -> {
                stopStream()
            }

            R.id.btn_set_live_stream_channel_type -> {
                showSetLiveStreamChannelTypeDialog()
            }

            R.id.btn_get_live_stream_channel_type -> {
                ToastUtils.showToast(liveStreamVM.getVideoChannel().name)
            }

            R.id.btn_set_live_stream_quality -> {
                showSetLiveStreamQualityDialog()
            }

            R.id.btn_get_live_stream_quality -> {
                ToastUtils.showToast(liveStreamVM.getLiveStreamQuality().name)
            }

            R.id.btn_set_live_stream_bit_rate_mode -> {
                showSetLiveStreamBitRateModeDialog()
            }

            R.id.btn_get_live_stream_bit_rate_mode -> {
                ToastUtils.showToast(liveStreamVM.getLiveVideoBitRateMode().name)
            }

            R.id.btn_set_live_stream_bit_rate -> {
                showSetLiveStreamBitrateDialog()
            }

            R.id.btn_get_live_stream_bit_rate -> {
                ToastUtils.showToast(liveStreamVM.getLiveVideoBitRate().toString())
            }

            R.id.btn_enable_audio -> {
                liveStreamVM.setLiveAudioEnabled(true)
            }

            R.id.btn_disable_audio -> {
                liveStreamVM.setLiveAudioEnabled(false)
            }
        }
    }

    private fun startStream() {
        liveStreamVM.startStream(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast(StringUtils.getResStr(R.string.msg_start_live_stream_success))
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast(
                    StringUtils.getResStr(
                        R.string.msg_start_live_stream_failed,
                        error.description()
                    )
                )
            }
        })
    }

    private fun stopStream() {
        liveStreamVM.stopStream(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast(StringUtils.getResStr(R.string.msg_stop_live_stream_success))
                clearLiveStreamInfo()
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast(
                    StringUtils.getResStr(
                        R.string.msg_stop_live_stream_failed,
                        error.description()
                    )
                )
            }
        })
    }

    private fun showSetLiveStreamRtmpConfigDialog() {
        val factory = LayoutInflater.from(this@DefaultLayoutTestFragment.requireContext())
        val rtmpConfigView = factory.inflate(R.layout.dialog_livestream_rtmp_config_view, null)
        val etRtmpUrl = rtmpConfigView.findViewById<EditText>(R.id.et_livestream_rtmp_config)
        etRtmpUrl.setText(
            liveStreamVM.getRtmpUrl().toCharArray(),
            0,
            liveStreamVM.getRtmpUrl().length
        )
        configDialog = this@DefaultLayoutTestFragment.requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_rtmp_config)
                .setCancelable(false)
                .setView(rtmpConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val inputValue = etRtmpUrl.text.toString()
                        if (TextUtils.isEmpty(inputValue)) {
                            ToastUtils.showToast(msg)
                        } else {
                            liveStreamVM.setRTMPConfig(inputValue)
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun showSetLiveStreamRtspConfigDialog() {
        val factory = LayoutInflater.from(this@DefaultLayoutTestFragment.requireContext())
        val rtspConfigView = factory.inflate(R.layout.dialog_livestream_rtsp_config_view, null)
        val etRtspUsername = rtspConfigView.findViewById<EditText>(R.id.et_livestream_rtsp_username)
        val etRtspPassword = rtspConfigView.findViewById<EditText>(R.id.et_livestream_rtsp_password)
        val etRtspPort = rtspConfigView.findViewById<EditText>(R.id.et_livestream_rtsp_port)
        val rtspConfig = liveStreamVM.getRtspSettings()
        if (!TextUtils.isEmpty(rtspConfig) && rtspConfig.length > 0) {
            val configs = rtspConfig.trim().split("^_^")
            etRtspUsername.setText(
                configs[0].toCharArray(),
                0,
                configs[0].length
            )
            etRtspPassword.setText(
                configs[1].toCharArray(),
                0,
                configs[1].length
            )
            etRtspPort.setText(
                configs[2].toCharArray(),
                0,
                configs[2].length
            )
        }

        configDialog = this@DefaultLayoutTestFragment.requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_rtsp_config)
                .setCancelable(false)
                .setView(rtspConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val inputUserName = etRtspUsername.text.toString()
                        val inputPassword = etRtspPassword.text.toString()
                        val inputPort = etRtspPort.text.toString()
                        if (TextUtils.isEmpty(inputUserName) || TextUtils.isEmpty(inputPassword) || TextUtils.isEmpty(
                                inputPort
                            )
                        ) {
                            ToastUtils.showToast(msg)
                        } else {
                            try {
                                liveStreamVM.setRTSPConfig(
                                    inputUserName,
                                    inputPassword,
                                    inputPort.toInt()
                                )
                            } catch (e: NumberFormatException) {
                                ToastUtils.showToast("RTSP port must be int value")
                            }
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun showSetLiveStreamGb28181ConfigDialog() {
        val factory = LayoutInflater.from(this@DefaultLayoutTestFragment.requireContext())
        val gbConfigView = factory.inflate(R.layout.dialog_livestream_gb28181_config_view, null)
        val etGbServerIp = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_server_ip)
        val etGbServerPort =
            gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_server_port)
        val etGbServerId = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_server_id)
        val etGbAgentId = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_agent_id)
        val etGbChannel = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_channel)
        val etGbLocalPort =
            gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_local_port)
        val etGbPassword = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_password)

        val gbConfig = liveStreamVM.getGb28181Settings()
        if (!TextUtils.isEmpty(gbConfig) && gbConfig.isNotEmpty()) {
            val configs = gbConfig.trim().split("^_^")
            etGbServerIp.setText(
                configs[0].toCharArray(),
                0,
                configs[0].length
            )
            etGbServerPort.setText(
                configs[1].toCharArray(),
                0,
                configs[1].length
            )
            etGbServerId.setText(
                configs[2].toCharArray(),
                0,
                configs[2].length
            )
            etGbAgentId.setText(
                configs[3].toCharArray(),
                0,
                configs[3].length
            )
            etGbChannel.setText(
                configs[4].toCharArray(),
                0,
                configs[4].length
            )
            etGbLocalPort.setText(
                configs[5].toCharArray(),
                0,
                configs[5].length
            )
            etGbPassword.setText(
                configs[6].toCharArray(),
                0,
                configs[6].length
            )
        }

        configDialog = this@DefaultLayoutTestFragment.requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_gb28181_config)
                .setCancelable(false)
                .setView(gbConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val serverIp = etGbServerIp.text.toString()
                        val serverPort = etGbServerPort.text.toString()
                        val serverId = etGbServerId.text.toString()
                        val agentId = etGbAgentId.text.toString()
                        val channel = etGbChannel.text.toString()
                        val localPort = etGbLocalPort.text.toString()
                        val password = etGbPassword.text.toString()
                        if (TextUtils.isEmpty(serverIp) || TextUtils.isEmpty(serverPort) || TextUtils.isEmpty(
                                serverId
                            ) || TextUtils.isEmpty(agentId) || TextUtils.isEmpty(channel) || TextUtils.isEmpty(
                                localPort
                            ) || TextUtils.isEmpty(password)
                        ) {
                            ToastUtils.showToast(msg)
                        } else {
                            try {
                                liveStreamVM.setGB28181(
                                    serverIp,
                                    serverPort.toInt(),
                                    serverId,
                                    agentId,
                                    channel,
                                    localPort.toInt(),
                                    password
                                )
                            } catch (e: NumberFormatException) {
                                ToastUtils.showToast("RTSP port must be int value")
                            }
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun showSetLiveStreamAgoraConfigDialog() {
        val factory = LayoutInflater.from(this@DefaultLayoutTestFragment.requireContext())
        val agoraConfigView = factory.inflate(R.layout.dialog_livestream_agora_config_view, null)

        val etAgoraChannelId =
            agoraConfigView.findViewById<EditText>(R.id.et_livestream_agora_channel_id)
        val etAgoraToken = agoraConfigView.findViewById<EditText>(R.id.et_livestream_agora_token)
        val etAgoraUid = agoraConfigView.findViewById<EditText>(R.id.et_livestream_agora_uid)

        val agoraConfig = liveStreamVM.getAgoraSettings()
        if (!TextUtils.isEmpty(agoraConfig) && agoraConfig.length > 0) {
            val configs = agoraConfig.trim().split("^_^")
            etAgoraChannelId.setText(configs[0].toCharArray(), 0, configs[0].length)
            etAgoraToken.setText(configs[1].toCharArray(), 0, configs[1].length)
            etAgoraUid.setText(configs[2].toCharArray(), 0, configs[2].length)
        }

        configDialog = this@DefaultLayoutTestFragment.requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_agora_config)
                .setCancelable(false)
                .setView(agoraConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val channelId = etAgoraChannelId.text.toString()
                        val token = etAgoraToken.text.toString()
                        val uid = etAgoraUid.text.toString()
                        if (TextUtils.isEmpty(channelId) || TextUtils.isEmpty(token) || TextUtils.isEmpty(
                                uid
                            )
                        ) {
                            ToastUtils.showToast(msg)
                        } else {
                            liveStreamVM.setAgoraConfig(channelId, token, uid)
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun showSetLiveStreamBitrateDialog() {
        val editText = EditText(this@DefaultLayoutTestFragment.requireContext())
        dialog = this@DefaultLayoutTestFragment.requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_bit_rate)
                .setCancelable(false)
                .setView(editText)
                .setPositiveButton(R.string.ad_confirm) { dialog, _ ->
                    kotlin.run {
                        var inputValue = -1
                        try {
                            editText.text?.let {
                                inputValue = it.toString().toInt()
                            } ?: let {
                                inputValue = -1
                            }
                        } catch (e: Exception) {
                            inputValue = -1
                        }

                        if (inputValue == -1) {
                            ToastUtils.showToast("input is invalid")
                        } else {
                            liveStreamVM.setLiveVideoBitRate(inputValue)
                        }
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { dialog, _ ->
                    kotlin.run {
                        dialog.dismiss()
                    }
                }
                .create()
        }
        dialog.show()
    }

    private fun showSetLiveStreamQualityDialog() {
        val liveStreamQualities = liveStreamVM.getLiveStreamQualities()
        liveStreamQualities.let {
            val items = arrayOfNulls<String>(liveStreamQualities.size)
            for (i in liveStreamQualities.indices) {
                items[i] = liveStreamQualities[i].name
            }
            if (!items.isEmpty()) {
                dialog = this@DefaultLayoutTestFragment.requireContext().let {
                    AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                        .setIcon(android.R.drawable.ic_menu_camera)
                        .setTitle(R.string.ad_select_live_stream_quality)
                        .setCancelable(false)
                        .setSingleChoiceItems(items, checkedItem) { _, i ->
                            checkedItem = i
                            ToastUtils.showToast(
                                "选择所使用的bitRateMode： " + (items[i]
                                    ?: "选择所使用的bitRateMode为null"),
                            )
                        }
                        .setPositiveButton(R.string.ad_confirm) { dialog, _ ->
                            kotlin.run {
                                liveStreamQuality = liveStreamQualities[checkedItem]
                                liveStreamVM.setLiveStreamQuality(liveStreamQuality)
                                dialog.dismiss()
                            }
                        }
                        .setNegativeButton(R.string.ad_cancel) { dialog, _ ->
                            kotlin.run {
                                dialog.dismiss()
                            }
                        }
                        .create()
                }
            }
            dialog.show()
        }
    }

    private fun showSetLiveStreamBitRateModeDialog() {
        val liveStreamBitrateModes = liveStreamVM.getLiveStreamBitRateModes()
        liveStreamBitrateModes.let {
            val items = arrayOfNulls<String>(liveStreamBitrateModes.size)
            for (i in liveStreamBitrateModes.indices) {
                items[i] = liveStreamBitrateModes[i].name
            }
            if (!items.isEmpty()) {
                dialog = this@DefaultLayoutTestFragment.requireContext().let {
                    AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                        .setIcon(android.R.drawable.ic_menu_camera)
                        .setTitle(R.string.ad_select_live_stream_bit_rate_mode)
                        .setCancelable(false)
                        .setSingleChoiceItems(items, checkedItem) { _, i ->
                            checkedItem = i
                            ToastUtils.showToast(
                                "选择所使用的bitRateMode： " + (items[i]
                                    ?: "选择所使用的bitRateMode为null"),
                            )
                        }
                        .setPositiveButton(getString(R.string.ad_confirm)) { dialog, _ ->
                            kotlin.run {
                                liveStreamBitrateMode = liveStreamBitrateModes[checkedItem]
                                liveStreamVM.setLiveVideoBitRateMode(liveStreamBitrateMode)
                                dialog.dismiss()
                            }
                        }
                        .setNegativeButton(getString(R.string.ad_cancel)) { dialog, _ ->
                            kotlin.run {
                                dialog.dismiss()
                            }
                        }
                        .create()
                }
            }
            dialog.show()
        }
    }

    private fun showSetLiveStreamChannelTypeDialog() {
        val liveStreamChannelTypes = liveStreamVM.getLiveStreamChannelTypes()
        liveStreamChannelTypes.let {
            val items = arrayOfNulls<String>(liveStreamChannelTypes.size)
            for (i in liveStreamChannelTypes.indices) {
                items[i] = liveStreamChannelTypes[i].name
            }
            if (!items.isEmpty()) {
                dialog = this@DefaultLayoutTestFragment.requireContext().let {
                    AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                        .setIcon(android.R.drawable.ic_dialog_email)
                        .setTitle(R.string.ad_select_live_stream_channel_type)
                        .setCancelable(false)
                        .setSingleChoiceItems(items, checkedItem) { _, i ->
                            checkedItem = i
                            ToastUtils.showToast(
                                "Channel： " + (items[i]
                                    ?: "channel null"),
                            )
                        }
                        .setPositiveButton(getString(R.string.ad_confirm)) { dialog, _ ->
                            kotlin.run {
                                if (liveStreamVM.getVideoChannel() != liveStreamChannelTypes[checkedItem]) {
                                    judgeChannel(liveStreamChannelTypes[checkedItem])
                                } else {
                                    ToastUtils.showToast(
                                        "Chanel is same"
                                    )
                                }

                                dialog.dismiss()
                            }
                        }
                        .setNegativeButton(getString(R.string.ad_cancel)) { dialog, _ ->
                            kotlin.run {
                                dialog.dismiss()
                            }
                        }
                        .create()
                }
            }
            dialog.show()
        }
    }

    private fun judgeChannel(videoChannel: VideoChannelType) {
        if (liveStreamVM.getChannelStatus(videoChannel) == VideoChannelState.ON) {
            setVideChannel(videoChannel)
        } else {
            ToastUtils.showToast(
                "Chanel is not open"
            )
        }
    }

    private fun setVideChannel(videoChannel: VideoChannelType) {
        liveStreamVM.setVideoChannel(videoChannel)
    }

    private fun showSetLiveStreamConfigDialog() {
        val liveStreamTypes = liveStreamVM.getLiveStreamTypes()
        liveStreamTypes.let {
            val items = arrayOfNulls<String>(liveStreamTypes.size)
            for (i in liveStreamTypes.indices) {
                items[i] = liveStreamTypes[i].name
            }
            if (!items.isEmpty()) {
                dialog = this@DefaultLayoutTestFragment.requireContext().let {
                    AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                        .setIcon(android.R.drawable.ic_input_get)
                        .setTitle(R.string.ad_select_live_stream_type)
                        .setCancelable(false)
                        .setSingleChoiceItems(items, checkedItem) { _, i ->
                            checkedItem = i
                            isConfigSelected = true
                            ToastUtils.showToast(
                                "选择的直播类型为： " + (items[i] ?: "直播类型为null"),
                            )
                        }
                        .setPositiveButton(getString(R.string.ad_confirm)) { dialog, _ ->
                            kotlin.run {
                                if (isConfigSelected) {
                                    liveStreamType = liveStreamTypes[checkedItem]
                                    setLiveStreamConfig(liveStreamType)
                                }
                                dialog.dismiss()
                                isConfigSelected = false
                            }
                        }
                        .setNegativeButton(getString(R.string.ad_cancel)) { dialog, _ ->
                            kotlin.run {
                                dialog.dismiss()
                                isConfigSelected = false
                            }
                        }
                        .create()
                }
            }
            dialog.show()
        }
    }

    private fun setLiveStreamConfig(liveStreamtype: LiveStreamType) {
        liveStreamtype.let {
            when (liveStreamtype) {
                LiveStreamType.RTMP -> {
                    showSetLiveStreamRtmpConfigDialog()
                }

                LiveStreamType.RTSP -> {
                    showSetLiveStreamRtspConfigDialog()
                }

                LiveStreamType.GB28181 -> {
                    showSetLiveStreamGb28181ConfigDialog()
                }

                LiveStreamType.AGORA -> {
                    showSetLiveStreamAgoraConfigDialog()
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        //重新创建的时候销毁openGL
//        videoDecoder?.let {
//            it.destory()
//        }
//        videoDecoder = null
//        videoDecoder = VideoDecoder(
//            this@LiveStreamFragment.context,
//            liveStreamVM.getVideoChannel(),
//            DecoderOutputMode.SURFACE_MODE,
//            surfaceView.holder,
//            surfaceView.width,
//            surfaceView.height,
//            true
//        )

        curWidth = surfaceView.width
        curHeight = surfaceView.height
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        curWidth = width
        curHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isStreaming) {
            stopStream()
        }

        mapWidget.onDestroy()
        MediaDataCenter.getInstance().videoStreamManager.clearAllStreamSourcesListeners()
        removeChannelStateListener()
    }

    private class CameraSource(
        var devicePosition: PhysicalDevicePosition,
        var lensType: CameraLensType
    )
}