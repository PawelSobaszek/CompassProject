package com.pawelsobaszek.compassproject.view

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.pawelsobaszek.compassproject.Compass
import com.pawelsobaszek.compassproject.Compass.CompassListener
import com.pawelsobaszek.compassproject.R
import com.pawelsobaszek.compassproject.SOTWFormatter
import com.pawelsobaszek.compassproject.model.DirectionCoordinates
import com.pawelsobaszek.compassproject.model.UserCurrentPosition
import kotlinx.android.synthetic.main.activity_compass.*
import java.lang.Exception

class CompassActivity : AppCompatActivity() {

    private var compass: Compass? = null
    // SOTW is for "side of the world"
    private var sotwLabel: TextView? = null
    private var sotwFormatter: SOTWFormatter? = null
    //Lat and Lng of user direction
    private var mLatitude : Double = 0.0
    private var mLongitude : Double = 0.0
    //Lat and Lng of user current possition
    private var mCurrentUserLatitude : Double = 0.0
    private var mCurrentUserLongitude : Double = 0.0

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    lateinit var viewModel: Compass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)
        sotwFormatter = SOTWFormatter(this)

        viewModel = ViewModelProviders.of(this).get(Compass::class.java)
        viewModel.setDirection(false)

        arrowView = findViewById(
            R.id.main_image_hands
        )
        arrowDirectionView = findViewById(
                R.id.main_image_hands_direction
                )
        sotwLabel = findViewById(R.id.sotw_label)
        setupCompass()

        observeViewModel()

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        tv_select_location.setOnClickListener {
            if (!isLocationEnabled()) {
                Toast.makeText(this, "Your location provider is turned off. Please turn it on", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            } else {
                Dexter.withActivity(this).withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ).withListener(object: MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestNewLocationData()
                            btnSelectLocationFunction()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()
            }
        }

        if (!Places.isInitialized()) {
            Places.initialize(this@CompassActivity,
                resources.getString(R.string.google_maps_api_key))
        }
    }

    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 1000
        mLocationRequest.numUpdates = 1

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack, Looper.myLooper())
    }

    private val mLocationCallBack = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            val mLastLocation: Location = locationResult!!.lastLocation
            mCurrentUserLatitude = mLastLocation.latitude
            mCurrentUserLongitude = mLastLocation.longitude
            var userCoordinates : UserCurrentPosition = UserCurrentPosition(mCurrentUserLatitude, mCurrentUserLongitude)
            viewModel.userCoordinates(userCoordinates)
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage("It looks like you have turned off your permissions required for this feature. It can be enabled under the Application Settings")
            .setPositiveButton("GO TO SETTINGS") {
                    _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){dialog, which ->
                dialog.dismiss()
            }.show()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "start compass")
        compass!!.start()
    }

    override fun onPause() {
        super.onPause()
        compass!!.stop()
    }

    override fun onResume() {
        super.onResume()
        compass!!.start()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "stop compass")
        compass!!.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        compass!!.stop()
    }

    private fun setupCompass() {
        compass = Compass(application)
        val cl = compassListener
        compass!!.setListener(cl)
    }

    fun adjustSotwLabel(azimuth: Float) {
        sotwLabel!!.text = sotwFormatter!!.format(azimuth)
    }

    private fun btnSelectLocationFunction() {
        try {
            val fields = listOf(
                Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS
            )
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this@CompassActivity)
            startActivityForResult(intent,
                PLACE_AUTOCOMPLETE_REQUEST_CODE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
                val place: Place = Autocomplete.getPlaceFromIntent(data!!)
                directionText = (place.address!!)
                mLatitude = place.latLng!!.latitude
                mLongitude = place.latLng!!.longitude
                var directionCoordinates : DirectionCoordinates = DirectionCoordinates(mLatitude, mLongitude)
                viewModel.directionCoordinates(directionCoordinates)
                viewModel.setDirection(true)
            }
        }
    }

    // UI updates only in UI thread
    // https://stackoverflow.com/q/11140285/444966
    private val compassListener: CompassListener
        private get() = object : CompassListener {
            override fun onNewAzimuth(azimuth: Float) {
                // UI updates only in UI thread
                // https://stackoverflow.com/q/11140285/444966
                runOnUiThread(Runnable {
                    adjustArrow(
                        azimuth
                    )
                    adjustSotwLabel(azimuth)
                })
            }

            override fun onNewDazimuth(dazimuth: Float) {
                runOnUiThread(Runnable {
                    adjustDirectionArrow(
                        dazimuth
                    )
                })
            }
        }

    fun observeViewModel() {
        viewModel.designatedDirection.observe(this, Observer { designatedDirection ->
            designatedDirection?.let { main_image_hands_direction.visibility = if (it) View.VISIBLE else View.GONE
            if (it) {
                tv_name_of_target.setText(directionText)
            }}
        })
    }


    companion object {
        private const val TAG = "CompassActivity"
        private var arrowView: ImageView? = null
        private var arrowDirectionView: ImageView? = null
        private var directionText: String = ""
        private var currentAzimuth = 0f
        private var currentDirectionAzimuth = 0f

        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1

        fun adjustArrow(azimuth: Float) {
            val an: Animation = RotateAnimation(-currentAzimuth, -azimuth, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            currentAzimuth = azimuth
            an.duration = 500
            an.repeatCount = 0
            an.fillAfter = true
            arrowView!!.startAnimation(an)
        }

        fun adjustDirectionArrow(dazimuth: Float) {
            val an: Animation = RotateAnimation(-currentDirectionAzimuth, -dazimuth, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            currentDirectionAzimuth = dazimuth
            an.duration = 500
            an.repeatCount = 0
            an.fillAfter = true
            arrowDirectionView!!.startAnimation(an)
        }
    }
}