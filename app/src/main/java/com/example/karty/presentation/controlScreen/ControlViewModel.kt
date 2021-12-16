package com.example.karty.presentation.controlScreen

import android.bluetooth.BluetoothSocket
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karty.data.data_source.RcDao
import com.example.karty.domain.model.RC
import com.example.karty.domain.model.RcResponse
import com.example.karty.domain.use_cases.bluetooth.BluetoothUseCases
import com.example.karty.presentation.utils.Helpers.filterBluetoothMessages
import com.example.karty.presentation.utils.SharedPresManger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import java.io.IOException
import javax.inject.Inject

private const val DELAY = 900L
private lateinit var DEVICE_MAC: String
private var DEVICE_NAME = ""

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val useCases: BluetoothUseCases,
    private val dao: RcDao,
    private val sharedPresManger: SharedPresManger
) : ViewModel() {
    var bluetoothSocket: BluetoothSocket? = null

    private var _isConnected: MutableLiveData<Boolean> = MutableLiveData(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private var _isLoggingEnabled:MutableLiveData<Boolean> = MutableLiveData(true)
    val isLoggingEnabled:LiveData<Boolean> = _isLoggingEnabled

    private var _text: MutableLiveData<String> = MutableLiveData("")
    val text: LiveData<String> = _text


    init {
        val sharedPres = sharedPresManger.getSharedPref()
        if (sharedPres != null){
            _isLoggingEnabled.value = sharedPres.getBoolean("is_data_logged", false)
        }
    }

    fun moveWhileBtnPressed(motionEvent: MotionEvent, position: String): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
            val job = Job()
            CoroutineScope(Dispatchers.Main + job).launch {
                while (true) {
                    Log.d("ttt", "onCreate: sending command, to move in position ($position)")
                    sendCommand(position)
                    if (motionEvent.action == MotionEvent.ACTION_UP) {
                        break
                    }
                    delay(DELAY)
                }
            }
        }
        return true
    }

    fun connect(deviceAddress: String, deviceName: String="") {
        DEVICE_MAC = deviceAddress
        if (deviceName.isNotEmpty()){
            DEVICE_NAME = deviceName
        }
        var connectionSuccess = true
        viewModelScope.executeAsyncTask(
            onPreExecute = {
                Log.d("ttt", "connect: trying to connect....")
            },
            doInBackground = {
                try {
                    if (bluetoothSocket == null || !isConnected.value!!) {
                        bluetoothSocket = useCases.connect(deviceAddress)
                        Log.d("ttt", "connect: Connected")
                    }
                } catch (e: IOException) {
                    connectionSuccess = false
                    Log.e("ttt", "connect: ${e.message}")
                    e.printStackTrace()
                }
            },
            onPostExecute = {
                if (!connectionSuccess) {
                    Log.d("ttt", "connect: Could not connect")
                } else {
                    _isConnected.value = true
                    addDeviceToDatabase()
                }
            }
        )
    }

    private fun sendCommand(command: String) {
        if (bluetoothSocket != null) {
            try {
                useCases.sendCommand(bluetoothSocket!!, command)
                receiveData()
            } catch (e: IOException) {
                Log.e("ttt", "sendCommand: ${e.message}")
                _isConnected.value = false
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    if (isConnected.value == false) {
                        connect(DEVICE_MAC)
                        delay(1000)
                    }

                }
            }
        }
    }

    //under construction.... somewhat functional
    private fun receiveData() {
        var bytes: Int
        val buffer = ByteArray(1024)
        var message = ""
        if (isConnected.value == true) {
            try {
                while (!(message.contains("[e]"))) {
                    //read bytes received and ins to buffer
                    bytes = bluetoothSocket!!.inputStream.read(buffer)
                    //convert to string
                    message += String(buffer, 0, bytes)
                }
                val movements = message.filterBluetoothMessages()
                if (isLoggingEnabled.value == true){
                    addReadingsToDatabase(DEVICE_MAC, movements[0], movements[1])
                }
                _text.value = message
                Log.d("ttt", "receiveData: ${text.value}")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }


    private fun addDeviceToDatabase() {
        if (DEVICE_NAME.isNotEmpty()){
            val device = RC(
                deviceName = DEVICE_NAME,
                deviceAddress = DEVICE_MAC
            )
            viewModelScope.launch(Dispatchers.IO){
                dao.addDevice(device)
            }
        }

    }

    private fun addReadingsToDatabase(deviceAddress: String, leftMotor:Int, rightMotor:Int){
        viewModelScope.launch(Dispatchers.IO) {
            val reading = RcResponse(0,deviceAddress= deviceAddress, motorLeft = leftMotor, motorRight = rightMotor)
            dao.addReading(reading)
        }
    }

    fun changeIsDataLogged(isSaved: Boolean){
        _isLoggingEnabled.value = isSaved
        val sharedPrefs = sharedPresManger.getSharedPref()
        if (sharedPrefs != null){
            val editor = sharedPrefs.edit()
            editor.putBoolean("is_data_logged", isSaved)
            editor.apply()
        }
    }


    fun disconnect() {
        if (bluetoothSocket != null) {
            try {
                useCases.disconnect(bluetoothSocket!!)
                Log.d("ttt", "disconnect: Disconnected")
                _isConnected.value = false
            } catch (e: IOException) {
                Log.e("ttt", "disconnect: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}