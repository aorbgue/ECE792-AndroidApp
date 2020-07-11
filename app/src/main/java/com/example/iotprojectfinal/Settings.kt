package com.example.iotprojectfinal

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_settings.*

class Settings : AppCompatActivity() {
    companion object {
        var userName = "use-token-auth"
        var password = "OY1K@7VKUH-3nTzp0Z"
        var serverURI = "2yxw00.messaging.internetofthings.ibmcloud.com"
        var serverPort = "1883"
        var clientID = "d:2yxw00:Android:S9_Alhiet"
        var maxSamples = 10
        var period:Long = 25 // miliseconds
        var qos = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.mainToolbar))
    }

    private fun getParameters(){
        serverURI = txtId.text.toString()+".messaging.internetofthings.ibmcloud.com"
        serverPort = txtServerPort.text.toString()
        clientID = "d:2yxw00:Android:"+txtDeviceId.text.toString()
        userName = "use-token-auth"
        password = txtToken.text.toString()
        maxSamples = txtSamples.text.toString().toInt() // Buffer length
        qos = txtQos.textAlignment.toString().toInt()
        if (maxSamples > MAX_SAMPLES) {
            maxSamples = MAX_SAMPLES
        }
        if (maxSamples == 0) {
            maxSamples = MIN_SAMPLES
        }
        period = txtPeriod.text.toString().toLong() // Sampling Time
        if (period >= MAX_SAMPLING) {
            period = MAX_SAMPLING
        }
        if (period <= MIN_SAMPLING) {
            period = MIN_SAMPLING
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        getParameters()
    }

}
