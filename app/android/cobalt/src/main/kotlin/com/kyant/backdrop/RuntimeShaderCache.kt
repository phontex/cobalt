package com.kyant.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        runtimeShaders[key]?.let { return it }
        if (android.os.Build.VERSION.SDK_INT < 33) throw UnsupportedOperationException("Runtime shaders require Android 13")
        val shader = RuntimeShader(string)
        runtimeShaders[key] = shader
        return shader
    }

    fun clear() {
        runtimeShaders.clear()
    }
}
