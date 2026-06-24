package com.getcode.util.resources

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

class FakeResourceHelper(vararg rClasses: Class<*>) : ResourceHelper {

    private val nameMap: Map<Int, String> = if (rClasses.isNotEmpty()) {
        buildMapFrom(rClasses)
    } else {
        discoverFromClassLoader()
    }

    private val overrides = mutableMapOf<Int, String>()

    fun stub(resourceId: Int, value: String): FakeResourceHelper = apply {
        overrides[resourceId] = value
    }

    override fun getString(resourceId: Int): String =
        overrides[resourceId] ?: nameMap[resourceId] ?: resourceId.toString()

    override fun getString(resourceId: Int, vararg formatArgs: Any): String {
        val template = overrides[resourceId] ?: nameMap[resourceId] ?: return resourceId.toString()
        return runCatching { template.format(*formatArgs) }.getOrDefault(template)
    }

    override fun getRawResource(resourceId: Int): String = ""
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any, default: String): String = default
    override fun getDimension(dimenId: Int, default: Float): Float = default
    override fun getDimensionPixelSize(dimenId: Int, default: Int): Int = default
    override fun getDir(name: String, mode: Int): File? = null
    override val displayMetrics: DisplayMetrics get() = DisplayMetrics()
    override fun getDrawable(drawableResId: Int): Drawable? = null
    override fun getIdentifier(name: String, type: ResourceType): Int? = null
    override fun getFont(fontResId: Int): Typeface? = null

    companion object {
        private fun buildMapFrom(rClasses: Array<out Class<*>>): Map<Int, String> = buildMap {
            for (rClass in rClasses) {
                for (nested in rClass.declaredClasses) {
                    for (field in nested.declaredFields) {
                        runCatching { put(field.getInt(null), field.name) }
                    }
                }
            }
        }

        private fun discoverFromClassLoader(): Map<Int, String> = buildMap {
            val urls = collectUrls()
            for (url in urls) {
                runCatching {
                    val file = File(url.toURI())
                    if (file.isDirectory) {
                        scanDirectory(file, file, this)
                    } else if (file.extension == "jar") {
                        scanJar(file, this)
                    }
                }
            }
        }

        private fun collectUrls(): List<URL> {
            val urls = mutableListOf<URL>()
            var cl: ClassLoader? = FakeResourceHelper::class.java.classLoader
            while (cl != null) {
                if (cl is URLClassLoader) {
                    urls.addAll(cl.urLs)
                }
                cl = cl.parent
            }
            if (urls.isEmpty()) {
                val classpath = System.getProperty("java.class.path") ?: return emptyList()
                for (entry in classpath.split(File.pathSeparator)) {
                    runCatching { urls.add(File(entry).toURI().toURL()) }
                }
            }
            return urls
        }

        private fun scanDirectory(root: File, dir: File, map: MutableMap<Int, String>) {
            for (file in dir.listFiles() ?: return) {
                if (file.isDirectory) {
                    scanDirectory(root, file, map)
                } else if (file.name.startsWith("R\$") && file.name.endsWith(".class")) {
                    val className = file.relativeTo(root).path
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
                    loadRClassFields(className, map)
                }
            }
        }

        private fun scanJar(jar: File, map: MutableMap<Int, String>) {
            runCatching {
                JarFile(jar).use { jf ->
                    val entries = jf.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.contains("/R\$") && name.endsWith(".class")) {
                            val className = name.removeSuffix(".class").replace('/', '.')
                            loadRClassFields(className, map)
                        }
                    }
                }
            }
        }

        private fun loadRClassFields(className: String, map: MutableMap<Int, String>) {
            runCatching {
                val clazz = Class.forName(className, false, FakeResourceHelper::class.java.classLoader)
                for (field in clazz.declaredFields) {
                    runCatching { map[field.getInt(null)] = field.name }
                }
            }
        }
    }
}
