package com.absenku.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Pengaturan
import com.absenku.data.repository.Repository
import com.absenku.utils.DeviceIdHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val schoolName: String = "",
    val schoolAddress: String = "",
    val schoolPhone: String = "",
    val teacherName: String = "",
    val year: String = "",
    val logo: String? = null,
    val deviceId: String = "",
    val serial: String? = null,
    val isActivated: Boolean = false,
    val isPairing: Boolean = false,
    val pairingToken: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: Repository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    fun load(deviceId: String = DeviceIdHelper.getDeviceId(appContext)) {
        viewModelScope.launch {
            _state.value = SettingsUiState(
                schoolName = repo.getSetting(Pengaturan.KEY_SCHOOL_NAME)?.value ?: "",
                schoolAddress = repo.getSetting(Pengaturan.KEY_SCHOOL_ADDRESS)?.value ?: "",
                schoolPhone = repo.getSetting(Pengaturan.KEY_SCHOOL_PHONE)?.value ?: "",
                teacherName = repo.getSetting(Pengaturan.KEY_TEACHER_NAME)?.value ?: "",
                year = repo.getSetting(Pengaturan.KEY_YEAR)?.value ?: "",
                deviceId = deviceId,
                isActivated = repo.getAktivasi()?.status == "active",
                serial = repo.getAktivasi()?.serialNumber,
            )
        }
    }

    fun save(name: String, address: String, phone: String, teacher: String, year: String) {
        viewModelScope.launch {
            repo.putSetting(Pengaturan.KEY_SCHOOL_NAME, name)
            repo.putSetting(Pengaturan.KEY_SCHOOL_ADDRESS, address)
            repo.putSetting(Pengaturan.KEY_SCHOOL_PHONE, phone)
            repo.putSetting(Pengaturan.KEY_TEACHER_NAME, teacher)
            repo.putSetting(Pengaturan.KEY_YEAR, year)
            load()
        }
    }

    fun startPairing() { _state.value = _state.value.copy(isPairing = true) }
    fun cancelPairing() { _state.value = _state.value.copy(isPairing = false) }
    fun completePairing(token: String) { _state.value = _state.value.copy(isPairing = false, pairingToken = token) }
}
