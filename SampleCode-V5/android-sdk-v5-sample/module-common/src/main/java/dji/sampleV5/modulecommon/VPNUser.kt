package dji.sampleV5.modulecommon

data class VPNUser(
    var alias: String? = null,
    var ip: String? = null,
    var ovpn: String? = null,
    var ovpnUserName: String? = null,
    var ovpnUserPassword: String? = null,
    var connected: Boolean? = false
)