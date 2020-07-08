package com.pawelsobaszek.compassproject

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
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.pawelsobaszek.compassproject.Compass.CompassListener
import kotlinx.android.synthetic.main.activity_compass.*
import java.lang.Exception

class CompassActivity : AppCompatActivity() {
    private var compass: Compass? = null
    private var sotwLabel // SOTW is for "side of the world"
            : TextView? = null
    private var sotwFormatter: SOTWFormatter? = null
    private var mLatitude : Double = 0.0
    private var mLongitude : Double = 0.0
    private var mCurrentUserLatitude : Double = 0.0
    private var mCurrentUserLongitude : Double = 0.0

    private lateinit var mFusedLocationClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)
        sotwFormatter = SOTWFormatter(this)
        arrowView = findViewById(R.id.main_image_hands)
        arrowDirectionView = findViewById(R.id.main_image_hands_direction)
        directionTextView = findViewById(R.id.tv_name_of_target)
        sotwLabel = findViewById(R.id.sotw_label)
        setupCompass()

        if (directionTextView!!.text.isEmpty()) {
            val sharedPreferences = getSharedPreferences("COMPASS_TYPE", 0)
            val editor = sharedPreferences.edit()
            editor.putInt("CompassType", 0)
            editor.apply()
            arrowDirectionView!!.visibility = View.INVISIBLE
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

/*        tv_head_north.setOnClickListener {
            if (tv_name_of_target.text == "NORTH") {
                Toast.makeText(this, "Compass already heading NORTH", Toast.LENGTH_SHORT).show()
            } else {
                btnHeadNorthFunction()
                }
            }*/


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
            val sharedPreferences = getSharedPreferences("COMPASS_TYPE", 0)
            val editor = sharedPreferences.edit()
            editor.putFloat("currentUserLatitude", mCurrentUserLatitude.toFloat())
            editor.putFloat("currentUserLongitude", mCurrentUserLongitude.toFloat())
            editor.apply()
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
        compass = Compass(this)
        val cl = compassListener
        compass!!.setListener(cl)
    }

    fun adjustSotwLabel(azimuth: Float) {
        sotwLabel!!.text = sotwFormatter!!.format(azimuth)
    }

    private fun btnHeadNorthFunction() {
        val sharedPreferences = getSharedPreferences("COMPASS_TYPE", 0)
        val editor = sharedPreferences.edit()
        editor.putInt("CompassType", 0)
        editor.apply()
        recreate()
    }

    private fun btnSelectLocationFunction() {
        try {
            val fields = listOf(
                Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS
            )
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this@CompassActivity)
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
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
                tv_name_of_target.setText(place.address)
                mLatitude = place.latLng!!.latitude
                mLongitude = place.latLng!!.longitude
                val sharedPreferences = getSharedPreferences("COMPASS_TYPE", 0)
                val editor = sharedPreferences.edit()
                editor.putFloat("latitude", mLatitude.toFloat())
                editor.putFloat("longitude", mLongitude.toFloat())
                editor.putInt("CompassType", 1)
                editor.apply()
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
                    adjustArrow(azimuth)
                    adjustSotwLabel(azimuth)
                })
            }

            override fun onNewDazimuth(dazimuth: Float) {
                runOnUiThread(Runnable {
                    adjustDirectionArrow(dazimuth)
                })
            }
        }

    companion object {
        private const val TAG = "CompassActivity"
        private var arrowView: ImageView? = null
        private var arrowDirectionView: ImageView? = null
        private var directionTextView: TextView? = null
        private var currentAzimuth = 0f
        private var currentDirectionAzimuth = 0f

        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1

        fun adjustArrow(azimuth: Float) {
            Log.d(
                TAG,
                "will set rotation from " + currentAzimuth + " to "
                        + azimuth
            )
            val an: Animation = RotateAnimation(
                -currentAzimuth, -azimuth,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f
            )
            currentAzimuth = azimuth
            an.duration = 500
            an.repeatCount = 0
            an.fillAfter = true
            arrowView!!.startAnimation(an)
        }

        fun adjustDirectionArrow(dazimuth: Float) {
            Log.d(
                TAG,
                "will set rotation from " + currentDirectionAzimuth + " to "
                        + dazimuth
            )
            val an: Animation = RotateAnimation(
                -currentDirectionAzimuth, -dazimuth,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f
            )
            currentDirectionAzimuth = dazimuth
            an.duration = 500
            an.repeatCount = 0
            an.fillAfter = true
            arrowDirectionView!!.startAnimation(an)
            arrowDirectionView!!.visibility = View.VISIBLE
        }
    }
}