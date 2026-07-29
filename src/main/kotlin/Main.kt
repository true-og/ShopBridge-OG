import java.util.logging.Level
import me.wiefferink.areashop.events.notify.DeletedRegionEvent
import me.wiefferink.areashop.events.notify.ResoldRegionEvent
import me.wiefferink.areashop.events.notify.SoldRegionEvent
import me.wiefferink.areashop.events.notify.UnrentedRegionEvent
import me.wiefferink.areashop.regions.GeneralRegion
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin(), Listener {
    private lateinit var quickShop: QuickShopAdapter

    override fun onEnable() {
        val quickShopPlugin = server.pluginManager.getPlugin(QuickShopAdapter.PLUGIN_NAME)
        if (quickShopPlugin == null || !quickShopPlugin.isEnabled) {
            logger.severe("${QuickShopAdapter.PLUGIN_NAME} must be installed and enabled.")
            server.pluginManager.disablePlugin(this)
            return
        }

        try {
            quickShop = QuickShopAdapter.connect(quickShopPlugin)
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Could not access the ${QuickShopAdapter.PLUGIN_NAME} API.", error)
            server.pluginManager.disablePlugin(this)
            return
        }

        server.pluginManager.registerEvents(this, this)
    }

    // The plot is gone from AreaShop entirely, so its shops and their stock go with it.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRegionDeleted(event: DeletedRegionEvent) {
        removeShopsIn(event.region, wipeStock = true)
    }

    // Covers manual unrent, admin unrent and expiry, which all funnel through RentRegion.finishUnrent.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRegionUnrented(event: UnrentedRegionEvent) {
        removeShopsIn(event.region, wipeStock = true)
    }

    // A bought plot sold back to the server, which forfeits the stock the same way an unrent does.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRegionSold(event: SoldRegionEvent) {
        removeShopsIn(event.region, wipeStock = true)
    }

    // Ownership moves straight from one player to another, so the stock stays behind for the buyer.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRegionResold(event: ResoldRegionEvent) {
        removeShopsIn(event.region, wipeStock = false)
    }

    private fun removeShopsIn(region: GeneralRegion, wipeStock: Boolean) {
        val protectedRegion = region.region ?: return
        val world = region.world ?: return

        val minPoint = region.minimumPoint
        val maxPoint = region.maximumPoint
        val chuckLocations = hashSetOf<Chunk>()

        var x = minPoint.blockX
        while (x <= maxPoint.blockX + 16) {
            var z = minPoint.blockZ
            while (z <= maxPoint.blockZ + 16) {
                chuckLocations.add(world.getChunkAt(x shr 4, z shr 4))
                z += 16
            }
            x += 16
        }

        val shopMap = hashMapOf<Location, Any>()

        for (chunk in chuckLocations) {
            shopMap.putAll(quickShop.getShops(chunk))
        }

        for ((shopLocation, shop) in shopMap) {
            if (!protectedRegion.contains(shopLocation.blockX, shopLocation.blockY, shopLocation.blockZ)) {
                continue
            }

            // Empty the container first, because the shop's inventory link is no longer resolvable after deletion.
            if (wipeStock) {
                try {
                    quickShop.clearStock(shop)
                } catch (error: Exception) {
                    logger.warning(
                        "Could not clear stock of shop at $shopLocation in region ${region.name}: ${error.message}"
                    )
                }
            }

            quickShop.deleteShop(shop)
        }
    }
}
