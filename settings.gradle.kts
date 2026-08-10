plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

rootProject.name = "ShopBridge-OG"

ProcessBuilder("sh", "bootstrap.sh").directory(rootDir).inheritIO().start().let {
    if (it.waitFor() != 0) throw GradleException("bootstrap.sh failed")
}

includeBuild("libs/AreaShop-OG") // Import AreaShop-OG multi-project build (from source).
