plugins {
    `kotlin-dsl`
}

// Turns a catalog plugin entry into the Maven coordinates of its jar ("plugin marker"),
// so convention plugins can apply it by id without a version.
fun pluginJar(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }

dependencies {
    implementation(pluginJar(libs.plugins.spotless))
    implementation(pluginJar(libs.plugins.spring.boot))
}
