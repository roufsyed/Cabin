package com.roufsyed.cabin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roufsyed.cabin.AppContainer
import com.roufsyed.cabin.nav.recentPlaces
import com.roufsyed.cabin.ui.overlay.VoiceUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Where to?": listens for a destination and hands it to the maps app. */
class VoiceViewModel(private val container: AppContainer) : ViewModel() {
    private val listening = MutableStateFlow(false)

    val ui: StateFlow<VoiceUi> = combine(listening, container.settings.settings) { isListening, settings ->
        VoiceUi(isListening, settings.recentPlaces)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceUi(false, container.settings.settings.value.recentPlaces))

    /** Starts listening. [onDone] is called once a destination has been sent to the maps app. */
    fun listen(onDone: () -> Unit) {
        listening.value = true
        container.voice.listen { heard ->
            listening.value = false
            if (heard != null) {
                go(heard)
                onDone()
            }
        }
    }

    /** Sends [destination] to the maps app and remembers it as one of the two recent places. */
    fun go(destination: String) {
        container.voice.stop()
        listening.value = false
        container.navigation.navigateTo(destination)
        viewModelScope.launch {
            container.settings.update { it.copy(recentPlaces = recentPlaces(it.recentPlaces, destination)) }
        }
    }

    fun cancel() {
        container.voice.stop()
        listening.value = false
    }

    override fun onCleared() = container.voice.stop()
}
