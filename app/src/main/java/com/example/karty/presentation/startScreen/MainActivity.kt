package com.example.karty.presentation.startScreen

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.karty.presentation.utils.Helpers
import com.example.karty.R
import com.example.karty.presentation.controlScreen.ControlActivity

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: DevicesAdapter
    private lateinit var viewModel: StartScreenViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //viewModel = ViewModelProvider(this).get(StartScreenViewModel::class.java)

        val devicesRecyclerView: RecyclerView = findViewById(R.id.rv_DevicesList)

        val bluetoothManger = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManger.adapter

        if (bluetoothAdapter?.isEnabled == true) {
            val devices = bluetoothAdapter.bondedDevices
            setupRecyclerView(devicesRecyclerView)
            adapter.submitList(devices?.toList())
        } else {
            handleBluetoothOff()
        }


    }


    private fun handleBluetoothOff() {
        val turnOnBluetoothActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()){}
        Toast.makeText(this, "The bluetooth is off", Toast.LENGTH_SHORT).show()
        Helpers.showIsBluetoothOff(this, true)
        val btn: Button = findViewById(R.id.btn_RetryButton)
        btn.setOnClickListener {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            turnOnBluetoothActivity.launch(intent)
        }
    }
        private fun setupRecyclerView(rv: RecyclerView) {
            adapter = DevicesAdapter(){name, macAddress ->
                val intent = Intent(this, ControlActivity::class.java)
                intent.putExtra("deviceName", name)
                intent.putExtra("macAddress", macAddress)
                startActivity(intent)
            }
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = adapter
        }
    }