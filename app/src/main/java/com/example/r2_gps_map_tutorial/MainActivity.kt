package com.example.r2_gps_map_tutorial

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.r2_gps_map_tutorial.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnSuccessListener

private const val DEFAULT_UPDATE_INTERVAL = 5L // Default update in seconds
private const val FAST_UPDATE_INTERVAL = 2L // Fast update in seconds
private const val PERMISSIONS_FINE_LOCATION = 99 // Permission id number for fine location

class MainActivity : AppCompatActivity() {
    // We use view binding to access our view elements
    private lateinit var binding: ActivityMainBinding

    // Google's API interface for location services
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // Location request is a "config file" for all settings related to FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    // Location callback is called when location is retrieved (update interval (DEFAULT_UPDATE_INTERVAL) is met)
    private lateinit var locationCallback: LocationCallback

    // Location value for sending to the map view
    private var locationForMap: Location? = null

    // Function for initializing our main activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create binding to main activity
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize location objects
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Location request has to be built using builder. Here we set default update interval, minimum update interval
        // and a balanced power accuracy plan for best battery performance. These values are stored as a constants above.
        val builder = LocationRequest.Builder(1000 * DEFAULT_UPDATE_INTERVAL)
        builder.setMinUpdateIntervalMillis(1000 * FAST_UPDATE_INTERVAL)
        builder.setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)

        // Explanation for different priorities:
        // Priority.PRIORITY_HIGH_ACCURACY = provides the most accurate location possible, which is computed using as many inputs as necessary (it enables GPS, Wi-Fi, and cell, and uses a variety of Sensors), and may cause significant battery drain.
        // Priority.PRIORITY_BALANCED_POWER_ACCURACY = provides accurate location while optimizing for power. Very rarely uses GPS. Typically uses a combination of Wi-Fi and cell information to compute device location.
        // Priority.PRIORITY_LOW_POWER = largely relies on cell towers and avoids GPS and Wi-Fi inputs, providing coarse (city-level) accuracy with minimal battery drain.
        // Priority.PRIORITY_PASSIVE = receives locations passively from other apps for which location has already been computed.


        // Build location request using build() method
        locationRequest = builder.build()

        // Create a callback function which updates ui values
        locationCallback = object: LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                p0 ?: return // If p0 is null then return (Elvis operator, see the elvis hair -> ?:)
                for (location in p0.locations){
                    updateUIValues(location)
                }
            }
        }

        // Set switch click listener for GPS / Save Power switch
        binding.swGpsSavePower.setOnClickListener {

            // In order to change parameters in location request, we have
            // to use builder to copy old location request and then change the parameters.
            // After that, we build a new location request with the new parameters.
            val builder = LocationRequest.Builder(locationRequest)

            if(binding.swGpsSavePower.isChecked) {
                builder.setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                locationRequest = builder.build()
                binding.twGpsSavePower.text = "Using GPS sensors"
            }
            else {
                builder.setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                locationRequest = builder.build()
                binding.twGpsSavePower.text = "Using Towers + WiFi"
            }
        }

        // Set switch click listener for location updating on / off
        binding.swLocUpdates.setOnClickListener{
            if (binding.swLocUpdates.isChecked) {
                // turn on location updates and disable swGpsSavePower -switch
                startLocationUpdates()
                binding.swGpsSavePower.isClickable = false
            } else {
                // turn off location updates and enable swGpsSavePower -switch
                stopLocationUpdates()
                binding.swGpsSavePower.isClickable = true
            }
        }

        // Add onclick-listener to open map -button for opening map view
        binding.btnShowMap.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java).apply {
                putExtra("Latitude", locationForMap?.latitude)
                putExtra("Longitude", locationForMap?.longitude)
            }
            startActivity(intent)
        }

        // Make initial location update just once to get latest known position to screen
        updateLocation()
    } // end of onCreate()

    // Function to turn off continuous location tracking
    private fun stopLocationUpdates() {
        binding.tvLocUpdates.text = "Location updates off"
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    // Function to turn on continuous location tracking
    @SuppressLint("MissingPermission") // Supressed permission warnings for the sake of simplicity
    private fun startLocationUpdates() {
        binding.tvLocUpdates.text = "Location updates on"
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
        updateLocation()
    }

    // Function that is called after permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            PERMISSIONS_FINE_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateLocation()
                } else {
                    Toast.makeText(this, "This app requires permission to be granted in order to work properly", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    // Function that verifies permission if not asked and then either asks permission and updates location
    @SuppressLint("MissingPermission")// Supressed permission warnings for the sake of simplicity
    private fun updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            // Permission OK
            fusedLocationProviderClient.lastLocation.addOnSuccessListener(this, OnSuccessListener {
                updateUIValues(it)
            })
        } else {
            // Permission not OK. Ask for them
            requestPermissions( arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_FINE_LOCATION
            )
        }
    }

    // Function to update UI values
    private fun updateUIValues(location: Location?){
        location ?: return
        locationForMap = location
        binding.tvLatValue.text = location.latitude.toString()
        binding.tvLonValue.text = location.longitude.toString()
        binding.tvAccuracyValue.text = location.accuracy.toString()

        // Not all devices have altitude, so we check first is it available
        if (location.hasAltitude()){
            binding.tvAltValue.text = location.altitude.toString()
        } else {
            binding.tvAltValue.text = "Not available"
        }

        // Not all devices have speed, so we check first is it available
        if (location.hasSpeed()){
            binding.tvSpeedValue.text = location.speed.toString()
        } else {
            binding.tvSpeedValue.text = "Not available"
        }

        // Get location address with geocode. Log error if it fails.
        val geocoder = Geocoder(this)
        try {
            val addresses = arrayOf(geocoder.getFromLocation(location.latitude, location.longitude, 1))
            binding.tvAddressValue.text = addresses[0][0].getAddressLine(0)
        } catch (ex: Exception) {
            binding.tvAddressValue.text = "Not available"
            Log.d(ex.message, "Error happened while geocoding")
        }

    }
}