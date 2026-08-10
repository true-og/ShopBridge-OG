import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.UUID
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.plugin.Plugin

internal class QuickShopAdapter private constructor(private val shopManager: Any) {
    private val getShopsMethod =
        shopManager.javaClass.methods.findMethod("getShops", Chunk::class.java)
            ?: throw IllegalStateException("QuickShop-OG does not expose ShopManager#getShops(Chunk).")

    // Chunk coordinate lookup, preferred because it never forces a chunk to load.
    private val getShopsByCoordinateMethod =
        shopManager.javaClass.methods.findMethod(
            "getShops",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        )

    private val transferShopsOwnershipMethod =
        shopManager.javaClass.methods.findMethod("transferShopsOwnership", Collection::class.java, UUID::class.java)

    private val managerDeleteShopMethod =
        shopManager.javaClass.methods.firstOrNull { method ->
            method.name == "deleteShop" && method.parameterCount == 1
        }

    private val getInventoryMethods = hashMapOf<Class<*>, Method>()
    private val clearInventoryMethods = hashMapOf<Class<*>, Method>()
    private val directDeleteShopMethods = hashMapOf<Class<*>, Method>()

    fun getShops(chunk: Chunk): Map<Location, Any> {
        val shops = getShopsMethod.call(shopManager, chunk) as? Map<*, *> ?: return emptyMap()

        return toShopMap(shops)
    }

    // Returns null when this QuickShop build has no coordinate based lookup.
    fun getShops(world: String, chunkX: Int, chunkZ: Int): Map<Location, Any>? {
        val method = getShopsByCoordinateMethod ?: return null
        val shops = method.call(shopManager, world, chunkX, chunkZ) as? Map<*, *> ?: return emptyMap()

        return toShopMap(shops)
    }

    // True when the shops were handed to QuickShop, false when unsupported.
    fun transferShopsOwnership(shops: Collection<Any>, newOwner: UUID): Boolean {
        val method = transferShopsOwnershipMethod ?: return false
        method.call(shopManager, shops, newOwner)

        return true
    }

    private fun toShopMap(shops: Map<*, *>): Map<Location, Any> {
        return shops.entries.associate { entry ->
            val location =
                entry.key as? Location
                    ?: throw IllegalStateException("QuickShop-OG returned a shop with no Bukkit location.")
            val shop = entry.value ?: throw IllegalStateException("QuickShop-OG returned a null shop at $location.")
            location to shop
        }
    }

    fun clearStock(shop: Any) {
        val getInventoryMethod =
            getInventoryMethods.getOrPut(shop.javaClass) {
                shop.javaClass.methods.findMethod("getInventory")
                    ?: throw IllegalStateException("QuickShop-OG shop does not expose getInventory().")
            }
        val inventory = getInventoryMethod.call(shop) ?: return
        val clearInventoryMethod =
            clearInventoryMethods.getOrPut(inventory.javaClass) {
                inventory.javaClass.methods.findMethod("clear")
                    ?: throw IllegalStateException("QuickShop-OG shop inventory does not expose clear().")
            }

        clearInventoryMethod.call(inventory)
    }

    fun deleteShop(shop: Any) {
        if (managerDeleteShopMethod != null) {
            managerDeleteShopMethod.call(shopManager, shop)
            return
        }

        val deleteShopMethod =
            directDeleteShopMethods.getOrPut(shop.javaClass) {
                shop.javaClass.methods.findMethod("delete")
                    ?: throw IllegalStateException("QuickShop-OG shop does not expose delete().")
            }
        deleteShopMethod.call(shop)
    }

    companion object {
        const val PLUGIN_NAME = "QuickShop-OG"

        fun connect(plugin: Plugin): QuickShopAdapter {
            require(plugin.name == PLUGIN_NAME) {
                "ShopBridge-OG only supports the $PLUGIN_NAME plugin, not ${plugin.name}."
            }

            val api = resolveApi(plugin)
            val getShopManagerMethod =
                api.javaClass.methods.findMethod("getShopManager")
                    ?: throw IllegalStateException("QuickShop-OG does not expose getShopManager().")
            val shopManager =
                getShopManagerMethod.call(api)
                    ?: throw IllegalStateException("QuickShop-OG returned a null shop manager.")

            return QuickShopAdapter(shopManager)
        }

        private fun resolveApi(plugin: Plugin): Any {
            if (plugin.javaClass.methods.findMethod("getShopManager") != null) {
                return plugin
            }

            val apiClass = plugin.javaClass.classLoader.loadClass("com.ghostchu.quickshop.api.QuickShopAPI")
            val getInstanceMethod =
                apiClass.methods.findMethod("getInstance")
                    ?: throw IllegalStateException("QuickShop-OG does not expose QuickShopAPI#getInstance().")

            return getInstanceMethod.call(null)
                ?: throw IllegalStateException("QuickShop-OG returned a null API instance.")
        }
    }
}

private fun Array<Method>.findMethod(name: String, vararg parameterTypes: Class<*>): Method? = firstOrNull { method ->
    method.name == name && method.parameterTypes.contentEquals(parameterTypes)
}

private fun Method.call(receiver: Any?, vararg arguments: Any?): Any? =
    try {
        invoke(receiver, *arguments)
    } catch (error: InvocationTargetException) {
        throw IllegalStateException("${declaringClass.name}#$name failed.", error.targetException)
    } catch (error: ReflectiveOperationException) {
        throw IllegalStateException("Could not invoke ${declaringClass.name}#$name.", error)
    }
