import java.util.UUID
import java.util.logging.Level
import me.wiefferink.areashop.events.notify.DeletedRegionEvent
import me.wiefferink.areashop.events.notify.ResoldRegionEvent
import me.wiefferink.areashop.events.notify.SoldRegionEvent
import me.wiefferink.areashop.events.notify.TransferredRegionEvent
import me.wiefferink.areashop.events.notify.UnrentedRegionEvent
import me.wiefferink.areashop.regions.GeneralRegion
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

    // A player handed the plot to another player, so the shops follow the plot.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRegionTransferred(event: TransferredRegionEvent) {
        transferShopsIn(event.region, event.toPlayer ?: return)
    }

    private fun transferShopsIn(region: GeneralRegion, newOwner: UUID) {
        val shops = collectShopsIn(region)
        if (shops.isEmpty()) {
            return
        }

        val handled =
            try {
                quickShop.transferShopsOwnership(shops.values, newOwner)
            } catch (error: Exception) {
                logger.warning("Could not transfer shops in region ${region.name}: ${error.message}")
                return
            }

        if (!handled) {
            logger.warning(
                "${QuickShopAdapter.PLUGIN_NAME} has no ownership transfer API, " +
                    "${shops.size} shop(s) in region ${region.name} still belong to the previous owner."
            )
            return
        }

        logger.info("Transferred ${shops.size} shop(s) in region ${region.name} to $newOwner.")
    }

    private fun removeShopsIn(region: GeneralRegion, wipeStock: Boolean) {
        for ((shopLocation, shop) in collectShopsIn(region)) {
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

    // Every shop inside the region's WorldGuard boundary, keyed by its location.
    private fun collectShopsIn(region: GeneralRegion): Map<Location, Any> {
        val protectedRegion = region.region ?: return emptyMap()
        val world = region.world ?: return emptyMap()

        val minPoint = region.minimumPoint
        val maxPoint = region.maximumPoint
        val minChunkX = minPoint.blockX shr 4
        val maxChunkX = maxPoint.blockX shr 4
        val minChunkZ = minPoint.blockZ shr 4
        val maxChunkZ = maxPoint.blockZ shr 4

        val shopMap = hashMapOf<Location, Any>()
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                // Prefer the coordinate lookup, the Chunk overload would load the chunk.
                val shops =
                    quickShop.getShops(world.name, chunkX, chunkZ)
                        ?: quickShop.getShops(world.getChunkAt(chunkX, chunkZ))
                shopMap.putAll(shops)
            }
        }

        return shopMap.filterKeys { location ->
            protectedRegion.contains(location.blockX, location.blockY, location.blockZ)
        }
    }
}
