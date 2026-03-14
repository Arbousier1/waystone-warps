package dev.mizarc.waystonewarps.infrastructure.services

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.lang.reflect.Array

object WorldGuardCompat {
    @Volatile
    private var lookupFailed = false

    fun canInteract(player: Player, location: Location): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin("WorldGuard") ?: return true
        if (!plugin.isEnabled) return true

        return try {
            val worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin")
            if (!worldGuardPluginClass.isInstance(plugin)) return true

            val localPlayer = worldGuardPluginClass
                .getMethod("wrapPlayer", Player::class.java)
                .invoke(plugin, player)

            val worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard")
            val worldGuard = worldGuardClass.getMethod("getInstance").invoke(null)
            val platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard)
            val regionContainer = platform.javaClass.getMethod("getRegionContainer").invoke(platform)
            val query = regionContainer.javaClass.getMethod("createQuery").invoke(regionContainer)

            val adaptedLocation = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                .getMethod("adapt", Location::class.java)
                .invoke(null, location)

            val stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag")
            val interactFlag = Class.forName("com.sk89q.worldguard.protection.flags.Flags")
                .getField("INTERACT")
                .get(null)
            val flags = Array.newInstance(stateFlagClass, 1).also { Array.set(it, 0, interactFlag) }

            val testBuild = query.javaClass.methods.first {
                it.name == "testBuild" &&
                    it.parameterCount == 3 &&
                    it.parameterTypes[2].isArray &&
                    it.parameterTypes[1].isInstance(localPlayer)
            }

            testBuild.invoke(query, adaptedLocation, localPlayer, flags) as Boolean
        } catch (exception: Exception) {
            if (!lookupFailed) {
                lookupFailed = true
                Bukkit.getLogger().warning(
                    "[WaystoneWarps] Failed to query WorldGuard interact permissions; falling back to default interaction handling."
                )
            }
            true
        }
    }
}
