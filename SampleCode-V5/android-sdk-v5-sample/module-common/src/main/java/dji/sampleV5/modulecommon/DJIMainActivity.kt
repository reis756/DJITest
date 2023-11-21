package dji.sampleV5.modulecommon

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.VpnStatus
import dji.sampleV5.modulecommon.models.BaseMainActivityVm
import dji.sampleV5.modulecommon.models.MSDKInfoVm
import dji.sampleV5.modulecommon.models.MSDKManagerVM
import dji.sampleV5.modulecommon.models.globalViewModels
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.PermissionUtil
import dji.v5.utils.common.StringUtils
import dji.v5.utils.common.ToastUtils
import dji.v5.utils.common.ToastUtils.showToast
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


/**
 * Class Description
 *
 * @author Hoker
 * @date 2022/2/10
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
abstract class DJIMainActivity : AppCompatActivity() {

    val tag: String = LogUtils.getTag(this)
    private val permissionArray = arrayListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.KILL_BACKGROUND_PROCESSES,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    init {
        permissionArray.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

        }
    }

    private val baseMainActivityVm: BaseMainActivityVm by viewModels()
    private val msdkInfoVm: MSDKInfoVm by viewModels()
    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val disposable = CompositeDisposable()

    private var connection: CheckInternetConnection? = null

    private var vpnStart = false

    private var vpnUser: VPNUser? = null
    private var vpnUserList: MutableList<VPNUser> = getVpnUserList()

    private lateinit var vpnUserListAdapter: VpnUserListAdapter

    private var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                setStatus(intent.getStringExtra("state"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                var duration = intent.getStringExtra("duration")
                var lastPacketReceive = intent.getStringExtra("lastPacketReceive")
                var byteIn = intent.getStringExtra("byteIn")
                var byteOut = intent.getStringExtra("byteOut")
                if (duration == null) duration = "00:00:00"
                if (lastPacketReceive == null) lastPacketReceive = "0"
                if (byteIn == null) byteIn = " "
                if (byteOut == null) byteOut = " "
                //updateConnectionStatus(duration, lastPacketReceive, byteIn, byteOut)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    abstract fun prepareUxActivity()

    abstract fun prepareTestingToolsActivity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        initMSDKInfoView()
        observeSDKManagerStatus()
        checkPermissionAndRequest()

        connection = CheckInternetConnection()
        setupAdapter()

        vpnBtn.setOnClickListener {
            if (vpnStart) {
                confirmDisconnect()
            } else {
                prepareVpn()
            }
        }

        isServiceRunning()
        VpnStatus.initLogCache(cacheDir)
    }

    private fun isServiceRunning() {
        setStatus(OpenVPNService.getStatus())
    }

    fun setStatus(connectionState: String?) {
        if (connectionState != null) when (connectionState) {
            "DISCONNECTED" -> {
                status("connect")
                vpnStart = false
                OpenVPNService.setDefaultStatus()
                logTv.text =
                    String.format(getString(R.string.vpn_status), getString(R.string.disconnected))
            }

            "CONNECTED" -> {
                vpnStart = true // it will use after restart this activity
                status("connected")
                logTv.text = String.format(
                    getString(R.string.vpn_status),
                    getString(R.string.connected)
                )
            }

            "WAIT" -> logTv.text = "VPN waiting for server connection!!"
            "AUTH" -> logTv.text = "VPN server authenticating!!"
            "RECONNECTING" -> {
                status("connecting")
                logTv.text = "VPN Reconnecting..."
            }

            "NONETWORK" -> logTv.text = "VPN No network connection"
        }
    }

    private fun status(status: String) {
        when (status) {
            "connect" -> {
                vpnBtn.text = getString(R.string.connect)
            }

            "connecting" -> {
                vpnBtn.text = getString(R.string.connecting)
            }

            "connected" -> {
                vpnBtn.text = getString(R.string.disconnect)
            }

            "tryDifferentServer" -> {
                vpnBtn.text = "Try Different\nServer"
            }

            "loading" -> {
                vpnBtn.text = "Loading Server.."
            }

            "invalidDevice" -> {
                vpnBtn.text = "Invalid Device"
            }

            "authenticationCheck" -> {
                vpnBtn.text = "Authentication \n Checking..."
            }
        }
    }

    private fun confirmDisconnect() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(getString(R.string.connection_close_confirm))
        builder.setPositiveButton(getString(R.string.yes)) { dialog, id ->
            stopVpn()
        }
        builder.setNegativeButton(getString(R.string.no)) { dialog, id ->
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun prepareVpn() {
        if (!vpnStart) {
            if (getInternetStatus()) {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else {
                    startVpn()
                }

                status("connecting")
            } else {
                showToast("you have no internet connection !!")
            }
        } else if (stopVpn()) {
            showToast("Disconnect Successfully")
        }
    }

    private fun startVpn() {
        try {
            val conf = vpnUser?.ovpn?.let { assets.open(it) }
            val isr = InputStreamReader(conf)
            val br = BufferedReader(isr)
            var config = ""
            var line: String?
            while (true) {
                line = br.readLine()
                if (line == null) break
                config += "$line\n"
            }

            br.readLine()
            OpenVpnApi.startVpn(
                this,
                config,
                vpnUser?.alias,
                vpnUser?.ovpnUserName,
                vpnUser?.ovpnUserPassword
            )

            logTv.text = "VPN Connecting..."
            vpnStart = true
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun stopVpn(): Boolean {
        try {
            vpnUserListAdapter.setItems(getVpnUserList())
            vpnUser?.connected = false
            OpenVPNThread.stop()
            status("connect")
            vpnStart = false
            return true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            startVpn()
        } else {
            showToast("Permission Deny !! ")
        }
    }

    private fun getInternetStatus(): Boolean {
        return connection!!.netCheck(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter("connectionState"))
    }

    private fun setupAdapter() {
        vpnUserListAdapter =
            VpnUserListAdapter(object : VpnUserListAdapter.Listener {
                override fun onUserClick(vpnUser: VPNUser) {
                    reorderVpnList(vpnUser)

                    newServer(vpnUser)
                }
            })

        vpnUserListAdapter.setItems(vpnUserList)

        with(rvVpnUsers) {
            adapter = vpnUserListAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    private fun reorderVpnList(vpnUser: VPNUser) {
        val userToRemove = vpnUserList.findLast { it.alias == vpnUser.alias }
        vpnUserList.remove(userToRemove)
        vpnUserList.add(vpnUser)
        vpnUserListAdapter.setItems(vpnUserList.sortedBy { it.alias })
    }

    fun updateConnectionStatus(
        duration: String,
        lastPacketReceive: String,
        byteIn: String,
        byteOut: String
    ) {
        /*binding.durationTv.setText("Duration: $duration")
        binding.lastPacketReceiveTv.setText("Packet Received: $lastPacketReceive second ago")
        binding.byteInTv.setText("Bytes In: $byteIn")
        binding.byteOutTv.setText("Bytes Out: $byteOut")*/
    }

    private fun getVpnUserList(): MutableList<VPNUser> {
        val vpnUsers = mutableListOf<VPNUser>()
        vpnUsers.add(
            VPNUser(
                "SSTDrone",
                "192.168.8.10",
                "sstdrone.ovpn",
                "SSTDrone",
                "sstdrone"
            )
        )
        vpnUsers.add(
            VPNUser(
                "SSTDrone1",
                "192.168.8.11",
                "sstdrone1.ovpn",
                "SSTDrone1",
                "sstdrone"
            )
        )
        vpnUsers.add(
            VPNUser(
                "SSTDrone2",
                "192.168.8.12",
                "sstdrone2.ovpn",
                "SSTDrone2",
                "sstdrone"
            )
        )
        vpnUsers.add(
            VPNUser(
                "SSTDrone3",
                "192.168.8.13",
                "sstdrone3.ovpn",
                "SSTDrone3",
                "sstdrone"
            )
        )

        vpnUsers.add(
            VPNUser(
                "SSTDrone4",
                "192.168.8.14",
                "sstdrone4.ovpn",
                "SSTDrone4",
                "sstdrone"
            )
        )
        return vpnUsers
    }

    private fun newServer(vpnUser: VPNUser?) {
        this.vpnUser = vpnUser
        if (vpnStart) {
            stopVpn()
        }

        prepareVpn()
    }

    private fun handleAfterPermissionPermitted() {
        prepareTestingToolsActivity()
    }

    @SuppressLint("SetTextI18n")
    private fun initMSDKInfoView() {
        ToastUtils.init(this)
        msdkInfoVm.msdkInfo.observe(this) {
            val pInfo: PackageInfo =
                packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            text_view_version.text = StringUtils.getResStr(R.string.sdk_version, version)
            text_view_product_name.text =
                StringUtils.getResStr(R.string.product_name, it.productType.name)
            text_view_package_product_category.text =
                StringUtils.getResStr(R.string.package_product_category, it.packageProductCategory)
            text_core_info.text = it.coreInfo.toString()
        }
        view_base_info.setOnClickListener {
            baseMainActivityVm.doPairing {
                ToastUtils.showToast(it)
            }
        }
    }

    private fun observeSDKManagerStatus() {
        msdkManagerVM.lvRegisterState.observe(this) { resultPair ->
            val statusText: String?
            if (resultPair.first) {
                ToastUtils.showToast("Register Success")
                statusText = StringUtils.getResStr(this, R.string.registered)
                msdkInfoVm.initListener()
                handler.postDelayed({
                    prepareUxActivity()
                }, 5000)
            } else {
                ToastUtils.showToast("Register Failure: ${resultPair.second}")
                statusText = StringUtils.getResStr(this, R.string.unregistered)
            }
            text_view_registered.text =
                StringUtils.getResStr(R.string.registration_status, statusText)
        }

        msdkManagerVM.lvProductConnectionState.observe(this) { resultPair ->
            //ToastUtils.showToast("Product: ${resultPair.second} ,ConnectionState:  ${resultPair.first}")
        }

        msdkManagerVM.lvProductChanges.observe(this) { productId ->
            //ToastUtils.showToast("Product: $productId Changed")
        }

        msdkManagerVM.lvInitProcess.observe(this) { processPair ->
            ToastUtils.showToast("Init Process event: ${processPair.first.name}")
        }

        msdkManagerVM.lvDBDownloadProgress.observe(this) { resultPair ->
            ToastUtils.showToast("Database Download Progress current: ${resultPair.first}, total: ${resultPair.second}")
        }
    }

    fun <T> enableDefaultLayout(cl: Class<T>) {
        enableShowCaseButton(default_layout_button, cl)
    }

    fun <T> enableWidgetList(cl: Class<T>) {
        enableShowCaseButton(widget_list_button, cl)
    }

    fun <T> enableTestingTools(cl: Class<T>) {
        enableShowCaseButton(testing_tool_button, cl)
    }

    private fun <T> enableShowCaseButton(view: View, cl: Class<T>) {
        view.isEnabled = true
        view.setOnClickListener {
            Intent(this, cl).also {
                startActivity(it)
            }
        }
    }

    private fun checkPermissionAndRequest() {
        if (!checkPermission()) {
            requestPermission()
        }
    }

    private fun checkPermission(): Boolean {
        for (i in permissionArray.indices) {
            if (!PermissionUtil.isPermissionGranted(this, permissionArray[i])) {
                return false
            }
        }
        return true
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result?.entries?.forEach {
            if (it.value == false) {
                requestPermission()
                return@forEach
            }
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(permissionArray.toArray(arrayOf()))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
        ToastUtils.destroy()
    }
}