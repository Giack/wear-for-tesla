package dev.giack.teslawear

/** Tesla's global endpoints; everything user-specific (domain, credentials, region) comes from setup. */
object TeslaConfig {
    /** Asked in turn for the user's region (`users/region`); the user's own server is used afterwards. */
    val FLEET_REGIONAL_SERVERS = listOf(
        "https://fleet-api.prd.na.vn.cloud.tesla.com/",
        "https://fleet-api.prd.eu.vn.cloud.tesla.com/",
    )
    const val AUTH_URL = "https://auth.tesla.com/oauth2/v3/authorize"
    const val TOKEN_URL = "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token"
    const val SCOPES = "openid offline_access vehicle_device_data vehicle_cmds vehicle_charging_cmds"
    fun pairingUrl(domain: String) = "https://tesla.com/_ak/$domain"
    const val LOG_TAG = "TeslaWear"
}
