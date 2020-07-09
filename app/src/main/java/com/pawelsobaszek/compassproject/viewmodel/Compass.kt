package com.pawelsobaszek.compassproject.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.MutableLiveData
import com.pawelsobaszek.compassproject.model.DirectionCoordinates
import com.pawelsobaszek.compassproject.model.UserCurrentPosition


/**
 * PL * W tej klasie obsługujemy sensony, sprawdzamy czyużytkownik ma włączoną lokalizację,
 * zarządzamy współrzędnymi użytkownika oraz wskazanego celu.
 *
 * EN * In this class, we support sensons, check whether the user has location enabled,
 * we manage the coordinates of the user and the indicated destination.
 * */
class Compass(application: Application) : SensorEventListener, AndroidViewModel(application), LifecycleObserver {
    interface CompassListener {
        fun onNewAzimuth(azimuth: Float)
        fun onNewDazimuth(dazimuth: Float)
    }

    private var listener: CompassListener? = null
    private val sensorManager: SensorManager
    private val locationManager: LocationManager
    private val gsensor: Sensor
    private val msensor: Sensor
    private val mGravity = FloatArray(3)
    private val mGeomagnetic = FloatArray(3)
    private val R = FloatArray(9)
    private val I = FloatArray(9)
    private var azimuth = 0f
    private var dazimuth = 0f

    val designatedDirection = MutableLiveData<Boolean>()

    //region start
    /**
     * PL * Uruchamiamy listenery na sensorach
     *
     * EN * We run the listeners on sensors
     * */
    fun start() {
        sensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_GAME
        )
    }
    //endregion


    //region stop
    /**
     * PL * Zatrzymujemy listener nasłuchujący sensory
     *
     * EN * We stop the listener for sensors
     * */
    fun stop() {
        sensorManager.unregisterListener(this)
    }
    //endregion

    //region isLocationEnabled
    /**
     * PL * Sprawdzamy czy użytkownik ma włączoną lokalizację lub połączenie z internetem
     *
     * EN * We check whether the user has location enabled or internet connection
     * */
    fun isLocationEnabled(): Boolean {
        val locationManager : LocationManager = locationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    //endregion

    fun setListener(l: CompassListener?) {
        listener = l
    }

    //region onSensorChanged
    /**
     * PL * Obsługujemy każdą zmianę w sensorze
     *
     * EN * We support every change in the sensor
     * */
    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.97f
        synchronized(this) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0]
                mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1]
                mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2]

            }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                // mGeomagnetic = event.values;
                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * event.values[0]
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * event.values[1]
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * event.values[2]
            }

            val success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)

            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)

                //We check did user set direction.
                // If NO - handle only the north indicator
                // If YES - handle north and direction indicator
                if (!didDirectionIsSet) {
                    azimuth =
                        Math.toDegrees(orientation[0].toDouble()).toFloat() // orientation
                    azimuth = (azimuth + 360) % 360
                } else  {
                    azimuth =
                        Math.toDegrees(orientation[0].toDouble()).toFloat() // orientation
                    azimuth = (azimuth + 360) % 360
                    dazimuth = azimuth
                    dazimuth -= bearing(startLat, startLng, endLat, endLng).toFloat()
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
    //endregion

    //region userCoordinates
    /**
     * PL * Obsługujemy otrzymane współrzędne użytkownika
     *
     * EN * We support received user coordinates
     * */
    fun userCoordinates(userCoordinates: UserCurrentPosition) {
        startLat = userCoordinates.userLatitude
        startLng = userCoordinates.userLongitude
    }
    //endregion

    //region directionCoordinates
    /**
     * PL * Obsługujemy otrzymane współrzędne lokalizacji którą na cel obrał użytkownik
     *
     * EN * We service the received coordinates of the location which the user choosed
     * */
    fun directionCoordinates(directionCoordinates: DirectionCoordinates) {
        endLat = directionCoordinates.directionLatitude
        endLng = directionCoordinates.directionLongitude
    }
    //endregion

    //region setDirection
    fun setDirection(boolean: Boolean){
        designatedDirection.value = boolean
        didDirectionIsSet = boolean
    }
    //endregion


    //region bearing
    /**
     * PL * Kalkulujemy return wartości Double do wskazania kierunku obranego przez użytkownika.
     * Do obliczeń wykorzystujemy aktualną pozycję użytkownika oraz współrzędne lokalizacji wybranej
     * przez użytkownika w Places API
     *
     * EN * We calculate the return value of Double to indicate the direction chosen by the user.
     * For calculations, we use the user's current position and coordinates of the selected location
     * by the user in Places API
     * */
    protected fun bearing(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double {
        val latitude1 = Math.toRadians(startLat)
        val latitude2 = Math.toRadians(endLat)
        val longDiff = Math.toRadians(endLng - startLng)
        val y = Math.sin(longDiff) * Math.cos(latitude2)
        val x = Math.cos(latitude1) * Math.sin(latitude2) - Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(longDiff)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }
    //endregion

    //region empty
    //Empty
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }
    //endregion

    //region companion object
    companion object {
        private var didDirectionIsSet : Boolean = false
        private var startLat: Double = 0.0
        private var startLng: Double = 0.0
        private var endLat: Double = 0.0
        private var endLng: Double = 0.0

    }
    //endregion

    //region init
    init {
        sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gsensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        msensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
    //endregion
}