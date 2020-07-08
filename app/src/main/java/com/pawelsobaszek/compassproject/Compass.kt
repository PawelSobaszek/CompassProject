package com.pawelsobaszek.compassproject

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager


class Compass(context: Context) : SensorEventListener {
    interface CompassListener {
        fun onNewAzimuth(azimuth: Float)
        fun onNewDazimuth(dazimuth: Float)
    }

    private var listener: CompassListener? = null
    private val sensorManager: SensorManager
    private val gsensor: Sensor
    private val msensor: Sensor
    private val mGravity = FloatArray(3)
    private val mGeomagnetic = FloatArray(3)
    private val R = FloatArray(9)
    private val I = FloatArray(9)
    private var azimuth = 0f
    private var dazimuth = 0f
    private var azimuthFix = 0f
    private val sharedPreferences = context.getSharedPreferences("COMPASS_TYPE", Context.MODE_PRIVATE)


    fun start() {
        sensorManager.registerListener(
            this, gsensor,
            SensorManager.SENSOR_DELAY_GAME
        )
        sensorManager.registerListener(
            this, msensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun setAzimuthFix(fix: Float) {
        azimuthFix = fix
    }

    fun resetAzimuthFix() {
        setAzimuthFix(0f)
    }

    fun setListener(l: CompassListener?) {
        listener = l
    }
    fun checkSharedPref(): Int {
        var returnedValue = 0
        if (sharedPreferences.getInt("CompassType", 0) == 0) {
            returnedValue = 0
        }  else if ((sharedPreferences.getInt("CompassType", 0) == 1)) {
            returnedValue = 1
        }
        return returnedValue
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.97f
        synchronized(this) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0]
                mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1]
                mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2]

                // mGravity = event.values;

                // Log.e(TAG, Float.toString(mGravity[0]));
            }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                // mGeomagnetic = event.values;
                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * event.values[0]
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * event.values[1]
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * event.values[2]
                // Log.e(TAG, Float.toString(event.values[0]));
            }

            val success = SensorManager.getRotationMatrix(
                R, I, mGravity,
                mGeomagnetic
            )

            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                // Log.d(TAG, "azimuth (rad): " + azimuth);


                // Log.d(TAG, "azimuth (deg): " + azimuth);
                if (checkSharedPref() == 0) {
                    azimuth =
                        Math.toDegrees(orientation[0].toDouble()).toFloat() // orientation
                    azimuth = (azimuth + 360) % 360
                } else if (checkSharedPref() == 1) {
                    azimuth =
                        Math.toDegrees(orientation[0].toDouble()).toFloat() // orientation
                    azimuth = (azimuth + 360) % 360
                    dazimuth = azimuth
                    dazimuth -= bearing((sharedPreferences.getFloat("currentUserLatitude", 0f)).toDouble(), (sharedPreferences.getFloat("currentUserLongitude", 0f)).toDouble(), (sharedPreferences.getFloat("latitude", 0f)).toDouble(), (sharedPreferences.getFloat("longitude", 0f)).toDouble()).toFloat()
                    //azimuth -= bearing((sharedPreferences.getFloat("currentUserLatitude", 0f)).toDouble(), (sharedPreferences.getFloat("currentUserLongitude", 0f)).toDouble(), (sharedPreferences.getFloat("latitude", 0f)).toDouble(), (sharedPreferences.getFloat("longitude", 0f)).toDouble()).toFloat()
                    if (listener != null) {
                        listener!!.onNewDazimuth(dazimuth)
                    }
                }

                if (listener != null) {
                    listener!!.onNewAzimuth(azimuth)
                }
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor,
        accuracy: Int
    ) {
    }

    protected fun bearing(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double {
        val latitude1 = Math.toRadians(startLat)
        val latitude2 = Math.toRadians(endLat)
        val longDiff = Math.toRadians(endLng - startLng)
        val y = Math.sin(longDiff) * Math.cos(latitude2)
        val x =
            Math.cos(latitude1) * Math.sin(latitude2) - Math.sin(
                latitude1
            ) * Math.cos(latitude2) * Math.cos(longDiff)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    companion object {
        private const val TAG = "Compass"
    }

    init {
        sensorManager = context
            .getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gsensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        msensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
}