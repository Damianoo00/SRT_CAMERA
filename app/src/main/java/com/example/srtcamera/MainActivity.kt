package com.example.srtcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.srtcamera.databinding.ActivityMainBinding
import io.github.thibaultbee.streampack.core.elements.encoders.AudioCodecConfig
import io.github.thibaultbee.streampack.core.elements.encoders.VideoCodecConfig
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.interfaces.startPreview
import io.github.thibaultbee.streampack.core.interfaces.stopPreview
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.ext.srt.configuration.mediadescriptor.SrtMediaDescriptor
import io.github.thibaultbee.streampack.ext.srt.elements.endpoints.SrtEndpointFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var streamer: SingleStreamer
    private var isStreaming   = false
    private var isFrontCamera = false
    private var surfaceReady  = false

    companion object {
        private val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
        private const val PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        streamer = SingleStreamer(
            context = this,
            endpointFactory = SrtEndpointFactory(coroutineDispatcher = Dispatchers.IO)
        )

        binding.preview.holder.addCallback(this)

        binding.btnStream.setOnClickListener {
            hideKeyboard()
            if (isStreaming) stopStream() else startStream()
        }

        binding.btnFlip.setOnClickListener {
            flipCamera()
        }

        checkPermissions()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (hasPermissions()) setupPreview(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        lifecycleScope.launch {
            try { streamer.stopPreview() } catch (_: Exception) {}
        }
    }

    private fun hasPermissions() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            if (surfaceReady) setupPreview(binding.preview.holder)
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            if (surfaceReady) setupPreview(binding.preview.holder)
        } else {
            Toast.makeText(this, "Wymagane uprawnienia!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupPreview(holder: SurfaceHolder) {
        lifecycleScope.launch {
            try {
                streamer.videoInput?.setSource(CameraSourceFactory(cameraId = "0"))
                streamer.setAudioSource(MicrophoneSourceFactory())
                streamer.setVideoConfig(VideoCodecConfig(gopDurationInS = 0.5f))
                streamer.setAudioConfig(AudioCodecConfig())
                streamer.startPreview(holder)
                setStatus("⏸ Gotowy — wpisz IP i naciśnij START")
            } catch (e: Exception) {
                setStatus("❌ Błąd podglądu: ${e.message}")
            }
        }
    }

    private fun startStream() {
        val ip   = binding.etIp.text.toString().trim()
        val port = binding.etPort.text.toString().trim().toIntOrNull() ?: 9999

        if (ip.isEmpty()) {
            Toast.makeText(this, "Wpisz adres IP!", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "srt://$ip:$port?latency=120&rcvlatency=120&peerlatency=120"

        lifecycleScope.launch {
            try {
                setStatus("🔄 Łączę z $url...")
                streamer.open(SrtMediaDescriptor(url))
                streamer.startStream()
                isStreaming = true
                setStatus("🔴 LIVE → $url")
                binding.btnStream.text = "⏹  STOP"
                binding.btnStream.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFF333333.toInt())
            } catch (e: Exception) {
                setStatus("❌ Błąd: ${e.message}")
                isStreaming = false
            }
        }
    }

    private fun stopStream() {
        lifecycleScope.launch {
            try {
                streamer.stopStream()
                streamer.close()
                isStreaming = false
                streamer.startPreview(binding.preview.holder)
                setStatus("⏸ Zatrzymano")
                binding.btnStream.text = "▶  START"
                binding.btnStream.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xCCFF0000.toInt())
            } catch (e: Exception) {
                setStatus("❌ Błąd: ${e.message}")
            }
        }
    }

    private fun flipCamera() {
        lifecycleScope.launch {
            try {
                isFrontCamera = !isFrontCamera
                val cameraId = if (isFrontCamera) "1" else "0"
                streamer.videoInput?.setSource(CameraSourceFactory(cameraId = cameraId))
                if (!isStreaming) {
                    streamer.startPreview()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Błąd kamery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { binding.tvStatus.text = msg }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            try {
                if (isStreaming) {
                    streamer.stopStream()
                    streamer.close()
                }
                streamer.release()
            } catch (_: Exception) {}
        }
    }
}
