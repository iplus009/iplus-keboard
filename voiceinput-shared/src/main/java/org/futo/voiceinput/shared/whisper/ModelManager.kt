package org.futo.voiceinput.shared.whisper

import android.content.Context
import org.futo.voiceinput.shared.ggml.WhisperGGML
import org.futo.voiceinput.shared.types.ModelLoader


class ModelManager(
    val context: Context
) {
    private val loadedModels: HashMap<ModelLoader, WhisperGGML> = hashMapOf()

    fun obtainModel(model: ModelLoader): WhisperGGML {
        synchronized(loadedModels) {
            loadedModels[model]?.let { return it }
            val loaded = model.loadGGML(context)
            loadedModels[model] = loaded
            return loaded
        }
    }

    fun cancelAll() {
        val snapshot = synchronized(loadedModels) { loadedModels.values.toList() }
        snapshot.forEach {
            it.cancel()
        }
    }

    suspend fun cleanUp() {
        val snapshot = synchronized(loadedModels) {
            loadedModels.values.toList().also { loadedModels.clear() }
        }
        for (model in snapshot) {
            model.close()
        }
    }
}
