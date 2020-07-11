package com.example.iotprojectfinal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

// Buffer Parameters
const val MAX_SAMPLES = 50
const val MIN_SAMPLES = 1
const val MEAS_PER_SAMPLE = 12 // 3 accelerometers, 3 gyros, 3 gravity, 1 step, 1 timestamp, 1 counter

// Sampling Parameters
const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST // Sensor capturing time (20ms)
const val MAX_SAMPLING:Long = 500 // Milliseconds
const val MIN_SAMPLING:Long = 10 // Milliseconds

// Colors
const val redColor = 0xFFFF0000
const val greenColor = 0xFF00FF00

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity(), SensorEventListener {

    val mqttClient = MqttClient(this)
    var buffCont = 0
    val data = Array(MAX_SAMPLES) {FloatArray(MEAS_PER_SAMPLE)}
    var tCounter:Long = 0 // Time counter (nanoseconds)
    var maxSamples = 1
    var sensorsEnabled = false
    var accX = 0F; var accY = 0F; var accZ = 0F // Acceleration variables
    var gyroX = 0F; var gyroY = 0F; var gyroZ = 0F // Gyroscope variables
    var gravX = 0F; var gravY = 0F; var gravZ = 0F // Gravity variables
    var steps = 0F // Steps variables
    var sampleCont = 0F

    //var timer = Timer()//("schedule", true); // Timer for sampling sensor data
    var enabledTimer = false
    lateinit var sensorM : SensorManager
    var accRead = false; var gyroRead = false; var gravRead = false; var stepRead = false
    var timerExecuted = false

    // DEBUG
    var old_time = 0F
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.mainToolbar))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Widgets initial parameters
        lblStatus.text = "Disconnected"
        lblStatus.setTextColor(redColor.toInt())
        tglConnect.text = "Connect"
        tglConnect.isChecked = false
        tglSensors.textOff = "START"
        tglSensors.textOn = "STOP"
        tglSensors.isEnabled = false

        // Sensor Manager
        sensorM = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorM.registerListener(this, sensorM.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SENSOR_DELAY)
        sensorM.registerListener(this, sensorM.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SENSOR_DELAY)
        sensorM.registerListener(this, sensorM.getDefaultSensor(Sensor.TYPE_GRAVITY), SENSOR_DELAY)
        sensorM.registerListener(this, sensorM.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SENSOR_DELAY)

        // Toggle button connect event (Connects to MQTT Broker)
        val vtglConnect: ToggleButton = findViewById(R.id.tglConnect)
        vtglConnect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Set communication parameters
                mqttClient.serverURI = Settings.serverURI
                mqttClient.serverPort = Settings.serverPort
                mqttClient.clientID = Settings.clientID
                mqttClient.userName = Settings.userName
                mqttClient.password = Settings.password
                val event = "IMU"
                mqttClient.topic = "iot-2/evt/$event/fmt/json"
                mqttClient.qos = Settings.qos

                // Toggle button disabled during connection
                tglConnect.isEnabled = false
                tglConnect.text = "Connecting"

                // Connect to MQTT Broker
                mqttClient.connect(this, {
                    // On sucessful connection
                    tglConnect.text = "Disconnect"
                    lblStatus.setTextColor(greenColor.toInt())
                    lblStatus.text = "Connected"
                    maxSamples = Settings.maxSamples // Buffer length
                    tglConnect.isEnabled = true // Enables toggle button On success
                    tglSensors.isEnabled = true
                },{
                    // On failure connection
                    tglConnect.isChecked = false
                    tglConnect.text = "Connect"
                    val text = "Not possible to connect"
                    val duration = Toast.LENGTH_LONG
                    val toast = Toast.makeText(applicationContext, text, duration)
                    toast.show()
                    tglConnect.isEnabled = true // Enables toggle button On failure
                })
            } else {
                // Disconnect from MQTT Broker
                if (mqttClient.isConnected){
                    mqttClient.disconnect() {
                        // On sucessful disconnection
                        tglConnect.text = "Connect"
                        lblStatus.setTextColor(redColor.toInt())
                        lblStatus.text = "Disconnected"
                        tglConnect.isEnabled = true // Enables toggle button On disconnection
                        tglSensors.isEnabled = false
                        }
                    }
                }
            }

        // Toggle button sensors event (enable sensors)
        val vtglSensors: ToggleButton = findViewById(R.id.tglSensors)
        vtglSensors.setOnCheckedChangeListener { _, isChecked ->
            sensorsEnabled = isChecked
            val timePeriod = Settings.period
            tCounter = System.nanoTime() // Start counter for timestamp
            if (isChecked) {
                val obj: JSONObject = JSONObject()
                obj.put("data", "START")
                obj.put("description", txtFileDesc.text.toString())
                mqttClient.publish(mqttClient.topic, obj.toString())
                if (!enabledTimer) {
                    val timer = Timer()
                    timer.scheduleAtFixedRate(0, timePeriod) { getdata() }
                    enabledTimer = true
                }
            } else { /*TODO Implement stop method for timer.*/
                val obj: JSONObject = JSONObject()
                obj.put("data", "STOP")
                mqttClient.publish(mqttClient.topic, obj.toString())
                sampleCont = 0F
                buffCont = 0
            }
        }
    }

    // Sensor functions (Must be present cause SensorEventListener)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    // On sensor change event
    override fun onSensorChanged(event: SensorEvent?) {
        val sensor:Sensor = event!!.sensor
        when(sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                accX = event.values[0]
                accY = event.values[1]
                accZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
            Sensor.TYPE_GRAVITY -> {
                gravX = event.values[0]
                gravY = event.values[1]
                gravZ = event.values[2]
            }
            Sensor.TYPE_STEP_COUNTER -> {
                steps = event.values[0]
            }
        }
    }

    // Menu creation
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // On click event (for options in menu bar)
    override fun onOptionsItemSelected(item: MenuItem):Boolean = when (item.itemId) {
        R.id.settings -> {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
            true
        }
        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    private fun getdata(){
        if (sensorsEnabled) {
            // Sensor Values
            data[buffCont][0] = accX; data[buffCont][1] = accY; data[buffCont][2] = accZ
            data[buffCont][3] = gyroX; data[buffCont][4] = gyroY; data[buffCont][5] = gyroZ
            data[buffCont][6] = gravX; data[buffCont][7] = gravY; data[buffCont][8] = gravZ
            data[buffCont][9] = steps
            // Timestamp
            val tSample = System.nanoTime()

            data[buffCont][10] = (tSample-tCounter)/1000000F // miliseconds
            if ((data[buffCont][10]-old_time)>100) {
                Log.d("Time bigger than 100", data[buffCont][10].toString())
            }
            old_time = data[buffCont][10]

            // Counter
            sampleCont++
            data[buffCont][11] = sampleCont

            // Buffer Counter
            buffCont++

            if (buffCont == maxSamples) {
                var msg = ""
                for (i in 0 until maxSamples) {
                    for (j in 0 until MEAS_PER_SAMPLE) {
                        msg += "${data[i][j]},"
                    }
                    msg += "\n"
                }
                // Conversion to JSON
                val obj: JSONObject = JSONObject()
                obj.put("data", msg)
                if (mqttClient.isConnected) {
                    //val txSample = System.nanoTime()
                    mqttClient.publish(mqttClient.topic, obj.toString())
                    //val tend = System.nanoTime()
                    //a = (tend-txSample)/1000000F
                }
                buffCont = 0
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mqttClient.isConnected)
            mqttClient.disconnect(){}
    }
}
