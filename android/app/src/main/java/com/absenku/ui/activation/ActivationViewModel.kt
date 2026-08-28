package com.absenku.ui.activation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Aktivasi
import com.absenku.data.repository.Repository
import com.absenku.utils.DeviceIdHelper
import com.absenku.utils.SerialValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ui state for the Activation / splash flow. */
data class ActivationUiState(
    val deviceId: String = "",
    val serial: String = "",
    val isActivated: Boolean = false,
    val isChecking: Boolean = false,
    val errorMsg: String? = null,
)

/** ViewModel — reads/writes activation state + serial validation. */
@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val repo: Repository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState(isChecking = true))
    val uiState: StateFlow<ActivationUiState> = _uiState

    /** Load device id + check existing activation. */
    fun loadDeviceId() {
        viewModelScope.launch {
            val deviceId = DeviceIdHelper.getDeviceId(appContext)
            val aktivasi = repo.getAktivasi()
            val activated = aktivasi?.status == "active"
            _uiState.value = _uiState.value.copy(
                deviceId = deviceId,
                isActivated = activated,
                isChecking = false,
            )
            if (aktivasi == null) {
                repo.addAktivasi(Aktivasi(deviceId = deviceId, serialNumber = null))
            }
        }
    }

    /** Submit serial — validate locally then persist. */
    fun submitSerial(deviceId: String, serial: String) {
        val valid = SerialValidator.isValid(deviceId, serial)
        _uiState.value = _uiState.value.copy(
            serial = serial,
            isActivated = valid,
            errorMsg = if (valid) null else "Serial tidak valid untuk device ini.",
        )
        if (valid) {
            viewModelScope.launch {
                repo.getAktivasi()?.let {
                    repo.updateAktivasi(it.copy(status = "active", serialNumber = serial, activatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    fun refresh() { loadDeviceId() }
}
