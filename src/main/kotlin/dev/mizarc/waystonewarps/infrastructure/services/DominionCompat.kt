package dev.mizarc.waystonewarps.infrastructure.services

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object DominionCompat {
    private const val pluginName = "Dominion"
    private const val flagName = "waystonewarps_use"
    private const val flagDisplayName = "Waystone Warps"
    private const val flagDescription = "Whether can interact with WaystoneWarps waystones."

    @Volatile
    private var registrationAttempted = false

    @Volatile
    private var registrationLookupFailed = false

    @Volatile
    private var permissionLookupFailed = false

    fun registerFlag(plugin: JavaPlugin) {
        val dominionPlugin = Bukkit.getPluginManager().getPlugin(pluginName) ?: return
        if (!dominionPlugin.isEnabled || registrationAttempted) return

        synchronized(this) {
            if (registrationAttempted) return
            registrationAttempted = true

            try {
                val flagsClass = Class.forName("cn.lunadeer.dominion.api.dtos.flag.Flags")
                val priFlagClass = Class.forName("cn.lunadeer.dominion.api.dtos.flag.PriFlag")

                val existingFlag = flagsClass
                    .getMethod("getPreFlag", String::class.java)
                    .invoke(null, flagName)

                if (existingFlag != null) {
                    return
                }

                val flag = priFlagClass.getConstructor(
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    java.lang.Boolean::class.java,
                    java.lang.Boolean::class.java,
                    Material::class.java
                ).newInstance(
                    flagName,
                    flagDisplayName,
                    flagDescription,
                    false,
                    true,
                    Material.LODESTONE
                )

                val registered = flagsClass
                    .getMethod("registerPriFlag", JavaPlugin::class.java, priFlagClass)
                    .invoke(null, plugin, flag) as Boolean

                if (!registered) {
                    plugin.logger.warning(
                        "Dominion rejected WaystoneWarps custom flag registration; falling back to default interaction handling."
                    )
                    return
                }

                flagsClass.getMethod("applyNewCustomFlags").invoke(null)
                plugin.logger.info("Registered Dominion custom flag '$flagName' for WaystoneWarps.")
            } catch (exception: Exception) {
                if (!registrationLookupFailed) {
                    registrationLookupFailed = true
                    plugin.logger.warning(
                        "Failed to register Dominion custom flag for WaystoneWarps; falling back to default interaction handling."
                    )
                }
            }
        }
    }

    fun canInteract(player: Player, location: Location): Boolean {
        val dominionPlugin = Bukkit.getPluginManager().getPlugin(pluginName) ?: return true
        if (!dominionPlugin.isEnabled) return true

        return try {
            val flagsClass = Class.forName("cn.lunadeer.dominion.api.dtos.flag.Flags")
            val priFlagClass = Class.forName("cn.lunadeer.dominion.api.dtos.flag.PriFlag")
            val dominionApiClass = Class.forName("cn.lunadeer.dominion.api.DominionAPI")

            val flag = flagsClass.getMethod("getPreFlag", String::class.java).invoke(null, flagName) ?: return true
            val dominionApi = dominionApiClass.getMethod("getInstance").invoke(null) ?: return true

            dominionApiClass
                .getMethod("checkPrivilegeFlag", Location::class.java, priFlagClass, Player::class.java)
                .invoke(dominionApi, location, flag, player) as Boolean
        } catch (exception: Exception) {
            if (!permissionLookupFailed) {
                permissionLookupFailed = true
                Bukkit.getLogger().warning(
                    "[WaystoneWarps] Failed to query Dominion waystone permissions; falling back to default interaction handling."
                )
            }
            true
        }
    }
}
