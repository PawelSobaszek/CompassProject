package com.pawelsobaszek.compassproject.view

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import android.widget.ImageView
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
import com.pawelsobaszek.compassproject.viewmodel.Compass
import com.pawelsobaszek.compassproject.viewmodel.Compass.CompassListener
import com.pawelsobaszek.compassproject.R
import com.pawelsobaszek.compassproject.viewmodel.SOTWFormatter
import com.pawelsobaszek.compassproject.model.DirectionCoordinates
import com.pawelsobaszek.compassproject.model.UserCurrentPosition
import kotlinx.android.synthetic.main.activity_compass.*
import java.lang.Exception


/**
 * PL * Aplikacja wykonana przez Pawła Sobaszka
 * UWAGA!!!
 * Do poprawnego działania aplikacji należy wkleić API Key do strings.xml dla stringa "google_maps_api_key"
 *
 * EN * App made by Paweł Sobaszek
 * ATTENTION!!!
 * For proper operation of the application, paste the API Key into strings.xml for the string "google_maps_api_key"
 * */
class CompassActivity : AppCompatActivity() {

    private var compass: Compass? = null
    private var sotwFormatter: SOTWFormatter? = null

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    lateinit var viewModel: Compass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)
        sotwFormatter =
            SOTWFormatter(this)

        //ViewModel
        viewModel = ViewModelProviders.of(this).get(Compass::class.java)
        //We are sending to ViewModel the default information that the user has not yet submitted "direction"
        viewModel.setDirection(false)

        arrowView = findViewById(R.id.main_image_hands)
        arrowDirectionView = findViewById(R.id.main_image_hands_direction)

        setupCompass()

        observeViewModel()

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //region "SELECT DIRECTION" BUTTON
        /**
         * PL * Obsługa przycisku "SELECT DIRECTION". Sprawdzamy czy użytkownik ma włączoną lokalizację. Jeżeli nie, to przesyłamy go do ustawień.
         * W przeciwnym wypadku sprawdzamy Dexterem czy użytkownik nadał uprawienia naszej aplikacji na korzystanie z lokalizacji. Jeżeli tak, odpalamy
         * zlokalizowanie użytkownika oraz Places API.
         *
         * EN * Operation of the "SELECT DIRECTION" button. We check whether the user has location enabled. If not, we send it to the settings.
         * Otherwise, we check with Dexter whether the user has given permission to our application to use the location. If so, we start it
         * locate the user and Places API.
         * */
        tv_select_location.setOnClickListener {
            //check if the user have disabled location, turn on settings and show Tash
            if (!viewModel.isLocationEnabled()) {
                Toast.makeText(this, "Your location provider is turned off. Please turn it on", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            } else {
                //If user don't agree permission, setup Dexter
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
        //endregion

        if (!Places.isInitialized()) {
            Places.initialize(this@CompassActivity,
                resources.getString(R.string.google_maps_api_key))
        }
    }

    //region requestNewLocationData
    /**
     * PL * Definiujemy żądanie o pozyskanie lokalizacji użytkownika ustawiając atrybuty
     *
     * EN * We define the request for obtaining the user's location by setting attributes
     * */
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 1000
        mLocationRequest.numUpdates = 1

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack, Looper.myLooper())
    }
    //endregion

    //region mLocationCallBack
    /**
     * PL * Obsługujemy odebraną lokalizację użytkownika poprzez przesłanie danych do Compass
     *
     * EN * We service the user's received location by sending data to Compass
     * */
    private val mLocationCallBack = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            val mLastLocation: Location = locationResult!!.lastLocation
            var userCoordinates : UserCurrentPosition = UserCurrentPosition(mLastLocation.latitude, mLastLocation.longitude)
            viewModel.userCoordinates(userCoordinates)
        }
    }
    //endregion

    //region showRationalDialogForPermissions
    /**
     * PL * W tej funkcji jeżeli użytkownik wyłączył uprawienia aplikacji do korzystania z GPS pokazujemy Dialog dający mu możliwość przejścia do ustawień uprawień
     *
     * EN * In this function, if the user has disabled the application's rights to use GPS, we show Dialog giving him the opportunity to go to the permissions settings
     * */
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
    //endregion

    //region lifecycle
    /**
     * PL * Obsługa cyklów życia CompassActivity
     *
     * EN * CompassActivity life cycle support
     * */
    override fun onStart() {
        super.onStart()
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
        compass!!.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        compass!!.stop()
    }
    //endregion

    //region setupCompass
    private fun setupCompass() {
        compass = Compass(application)
        val cl = compassListener
        compass!!.setListener(cl)
    }
    //endregion

    //region adjustSotwLabel
    /**
     * Adjust TextView represent Sites of The World with formatting
     * */
    fun adjustSotwLabel(azimuth: Float) {
        sotw_label!!.text = sotwFormatter!!.format(360 - azimuth)
    }
    //endregion

    //region btnSelectLocationFunction
    /**
     * PL * Odpalamy Places API po kliknięciu przycisku "SELECT DIRECTION"
     *
     * EN * We run Places API when user click on "SELECT DIRECTION" button
     * */
    private fun btnSelectLocationFunction() {
        try {
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this@CompassActivity)
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    //endregion

    //region onActivityResult
    /**
     * PL * Obsługujemy dane zwrotne przesłane po wybraniu miejsca za pomocą Places API
     *
     * EN * We support feedback sent after choosing a place using the Places API
     * */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
                val place: Place = Autocomplete.getPlaceFromIntent(data!!)
                directionText = (place.address!!)
                var directionCoordinates : DirectionCoordinates = DirectionCoordinates(place.latLng!!.latitude, place.latLng!!.longitude)
                viewModel.directionCoordinates(directionCoordinates)
                viewModel.setDirection(true)
            }
        }
    }
    //endregion

    //region compassListener
    private val compassListener: CompassListener
        private get() = object : CompassListener {
            override fun onNewAzimuth(azimuth: Float) {
                // UI updates only in UI thread
                runOnUiThread(Runnable {adjustArrow(azimuth)
                    adjustSotwLabel(azimuth)
                })
            }

            override fun onNewDazimuth(dazimuth: Float) {
                runOnUiThread(Runnable {adjustDirectionArrow(dazimuth)
                })
            }
        }
    //endregion

    //region observeViewModel
    /**
     * PL * Nasłuchiwanie zmian w "designatedDirection", jeżeli jest true, to znaczy, że użytkownik wybrał cel podróży, jeżeli false,
     * to aplikacja ma wskazywać jedynie Północ
     *
     * EN * Listening to changes in "designatedDirection" if it is true, it means that the user has selected the destination, if false
     * this application is to indicate only North
     * */
    fun observeViewModel() {
        viewModel.designatedDirection.observe(this, Observer { designatedDirection ->
            designatedDirection?.let { main_image_hands_direction.visibility = if (it) View.VISIBLE else View.GONE
            if (it) {
                tv_name_of_target.setText(directionText)
            }}
        })
    }
    //endregion

    //region companion object
    companion object {
        private var arrowView: ImageView? = null
        private var arrowDirectionView: ImageView? = null
        //Variable in which we will save the direction
        private var directionText: String = ""
        private var currentAzimuth = 0f
        private var currentDirectionAzimuth = 0f

        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1

        //region adjustArrow
        /**
         * PL * Obsługa animacji strzałki wskazującej północ
         *
         * EN * North arrow animation support
         * */
        fun adjustArrow(azimuth: Float) {
            val an: Animation = RotateAnimation(-currentAzimuth, -azimuth, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            currentAzimuth = azimuth
            an.duration = 500
            an.repeatCount = 0
            an.fillAfter = true
            arrowView!!.startAnimation(an)
        }
        //endregion

        //region adjustDirectionArrow
        /**
         * PL * Obsługa animacji strzałki wskazującej wybrany obrany użytkownika cel/kierunek
         *
         * EN * Support for arrow animation showing selected target/direction selected by user
         * */
        fun adjustDirectionArrow(dazimuth: Float) {
            val an: Animation = RotateAnimation(-currentDirectionAzimuth, -dazimuth, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            currentDirectionAzimuth = dazimuth
            an.duration = 500
            an.repeatCount = 0
            an.fillAfter = true
            arrowDirectionView!!.startAnimation(an)
        }
        //endregion
    }
    //endregion
}