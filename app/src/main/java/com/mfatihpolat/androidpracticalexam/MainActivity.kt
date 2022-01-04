package com.mfatihpolat.androidpracticalexam

import android.Manifest
import android.R.attr
import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.util.Util
import com.mfatihpolat.androidpracticalexam.databinding.ActivityMainBinding
import android.R.attr.y

import android.R.attr.x
import android.content.Context
import android.location.Location
import android.media.AudioManager
import android.os.Looper

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.*
import java.lang.Exception
import java.util.zip.ZipEntry
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {


    private var player: ExoPlayer? = null
    private var playWhenReady = false
    private var currentWindow = 0
    private var playbackPosition = 0L
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lasLocation: Location? = null
    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val audioManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private lateinit var gyroscopeSensor: Sensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        startTimer()
        initializeGyroscope()
        initializeLocation()
        initLocationCallback()
    }

    private fun initLocationCallback() {


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    val diff = location.distanceTo(lasLocation)
                    if (diff >= 10) {
                        lasLocation = location
                        playVideoFromStart()
                    }
                }
            }
        }
    }

    private fun playVideoFromStart() {
        playbackPosition = 0
        player?.seekTo(playbackPosition)

    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)

    }

    private fun initializeLocation() {

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    startTrackLocation(fusedLocationClient)
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    startTrackLocation(fusedLocationClient)
                }
                else -> {
                    // No location access granted.
                }
            }
        }
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    }

    @SuppressLint("MissingPermission")

    private fun startTrackLocation(fusedLocationClient: FusedLocationProviderClient) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location ->
            lasLocation = location
        }
        fusedLocationClient.requestLocationUpdates(
            createLocationRequest(),
            locationCallback,
            Looper.getMainLooper()
        )


    }

    fun createLocationRequest(): LocationRequest {
        return LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }


    private fun initializeGyroscope() {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val gyroSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                handleSensorEvent(event)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

            }

        }
        sensorManager.registerListener(gyroSensorListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL)

        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val accelerometerSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let { safeEvent ->
                    if (isShaking(safeEvent)) {
                        if (player?.isPlaying == true) {
                            player?.pause()
                        } else {
                            player?.play()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

            }

        }
        sensorManager.registerListener(accelerometerSensorListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)

    }

    private fun handleSensorEvent(event: SensorEvent?) {


        event?.let { safeEvent ->
            if (safeEvent.values[0] > 0.5f) {
                volumeUp()
            } else if (safeEvent.values[0] < -0.5f) {
                volumeDown()
            }

            if (safeEvent.values[2] > 0.5f) {
                try {
                    viewBinding.next.startAnimation(
                        AnimationUtils.loadAnimation(this, R.anim.blink)
                    )
                    player?.currentPosition?.plus(2000)?.let { player?.seekTo(it) }
                } catch (e: Exception) {

                }

            }
            if (safeEvent.values[2] < -0.5f) {
                try {
                    player?.currentPosition?.minus(2000)?.let { player?.seekTo(it) }
                    viewBinding.previus.startAnimation(
                        AnimationUtils.loadAnimation(this, R.anim.blink)
                    )
                } catch (e: Exception) {

                }

            }
        }
    }

    private fun volumeDown() {
        Log.e("XXXX", "volumeDown")
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)

    }

    private fun volumeUp() {
        Log.e("XXXX", "volumeUp")
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND)

    }

    val SHAKE_SLOP_TIME_MS = 500
    var mShakeTimestamp = 0L
    var mShakeCount = 0
    private fun isShaking(safeEvent: SensorEvent): Boolean {

        var isShaking = false
        val x: Float = safeEvent.values[0]
        val y: Float = safeEvent.values[1]
        val z: Float = safeEvent.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH


        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce > 2.0f) {


            val now = System.currentTimeMillis();

            if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                isShaking = false;
            }

            if (mShakeTimestamp + 1000 < now) {
                mShakeCount = 0;
            }

            mShakeTimestamp = now;
            mShakeCount++;

            if (2 == mShakeCount) {
                Log.e("XXX", "Shaking")
                isShaking = true
            }
        }
        return isShaking
    }

    private fun startTimer() {

        val timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                viewBinding.countDownTimerTV.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                viewBinding.countDownTimerTV.visibility = View.GONE
                player?.playWhenReady = true
            }
        }
        timer.start()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .build()
            .also { exoplayer ->
                val mediaItem = MediaItem.fromUri(Uri.parse(VIDEO_URL))
                exoplayer.setMediaItem(mediaItem)
                exoplayer.playWhenReady = playWhenReady
                exoplayer.seekTo(currentWindow, playbackPosition)
                exoplayer.prepare()
                viewBinding.videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                viewBinding.videoView.player = exoplayer

            }


    }

    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    public override fun onResume() {
        super.onResume()
        hideSystemUi()
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer()
        }
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        ViewCompat.setOnApplyWindowInsetsListener(
            viewBinding.videoView
        ) { v, windowInsets ->
            val insets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())

            v?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets?.left!!
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT < 24) {
            releasePlayer()
        }
    }


    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        player?.run {
            playbackPosition = this.currentPosition
            currentWindow = this.currentWindowIndex
            playWhenReady = this.playWhenReady
            release()
        }
        player = null
    }
}