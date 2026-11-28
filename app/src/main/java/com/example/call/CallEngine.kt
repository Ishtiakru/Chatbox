package com.example.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * One real-time audio/video call (WebRTC peer connection).
 *
 * Signaling is non-trickle: each side waits for ICE gathering to finish (or a
 * short timeout) and sends ONE complete SDP (offer / answer) through the
 * existing hidden Firestore call-signal messages, so a call costs only a few
 * Firestore writes. STUN finds direct routes; the public Open Relay TURN
 * servers relay media when both phones sit behind strict mobile-carrier NATs.
 */
class CallEngine(
    context: Context,
    val callId: String,
    val isVideo: Boolean,
    /** Called (on a background thread) with ("offer"|"answer", sdp) to send to the peer. */
    private val onLocalSdp: (kind: String, sdp: String) -> Unit
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadScheduledExecutor()

    val eglBase: EglBase = EglBase.create()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    /** "connecting" | "connected" | "reconnecting" | "failed" */
    private val _mediaState = MutableStateFlow("connecting")
    val mediaState: StateFlow<String> = _mediaState.asStateFlow()

    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()
    /** Audio calls start on the earpiece (hold the phone to the ear); video calls on the loudspeaker. */
    private val _speakerOn = MutableStateFlow(isVideo)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()
    private val _cameraOff = MutableStateFlow(false)
    val cameraOff: StateFlow<Boolean> = _cameraOff.asStateFlow()

    private var adm: JavaAudioDeviceModule? = null
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    @Volatile private var closed = false
    private var remoteDescriptionSet = false

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false

    private fun post(block: () -> Unit) {
        if (executor.isShutdown) return
        runCatching { executor.execute(block) }
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(appContext, p) == PackageManager.PERMISSION_GRANTED

    private fun setUp() {
        ensureInitialized(appContext)
        val audioModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        adm = audioModule
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f

        val config = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = f.createPeerConnection(config, observer)
            ?: throw IllegalStateException("PeerConnection could not be created")
        pc = peer

        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            val src = f.createAudioSource(MediaConstraints())
            audioSource = src
            val track = f.createAudioTrack("chatbox_a0", src)
            audioTrack = track
            peer.addTrack(track, listOf(STREAM_ID))
        }
        if (isVideo && hasPermission(Manifest.permission.CAMERA)) {
            runCatching { startCamera(f, peer) }.onFailure { Log.w(TAG, "camera start failed", it) }
        }
        configureAudio()
    }

    private fun startCamera(f: PeerConnectionFactory, peer: PeerConnection) {
        val enumerator: CameraEnumerator =
            if (Camera2Enumerator.isSupported(appContext)) Camera2Enumerator(appContext)
            else Camera1Enumerator(true)
        val names = enumerator.deviceNames
        val name = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull() ?: return
        val cap = enumerator.createCapturer(name, null) ?: return
        val helper = SurfaceTextureHelper.create("ChatboxCapture", eglBase.eglBaseContext)
        val src = f.createVideoSource(false)
        cap.initialize(helper, appContext, src.capturerObserver)
        cap.startCapture(640, 480, 24)
        val track = f.createVideoTrack("chatbox_v0", src)
        peer.addTrack(track, listOf(STREAM_ID))
        capturer = cap
        textureHelper = helper
        videoSource = src
        videoTrack = track
        _localVideo.value = track
    }

    @Suppress("DEPRECATION")
    private fun configureAudio() {
        val am = audioManager ?: return
        runCatching {
            previousAudioMode = am.mode
            previousSpeaker = am.isSpeakerphoneOn
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        applyRoute(_speakerOn.value)
    }

    /**
     * Loudspeaker or earpiece (a connected headset / Bluetooth wins over the earpiece).
     * Android 12+ ignores the old isSpeakerphoneOn on many phones — that's why calls were always loud —
     * so pick the communication device explicitly there.
     */
    @Suppress("DEPRECATION")
    private fun applyRoute(speaker: Boolean) {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val target = if (speaker) {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    devices.firstOrNull { it.type in HEADSET_TYPES } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                }
                if (target != null) am.setCommunicationDevice(target) else am.clearCommunicationDevice()
                if (!speaker && am.isSpeakerphoneOn) am.isSpeakerphoneOn = false
            } else {
                am.isSpeakerphoneOn = speaker
            }
        }.onFailure { Log.w(TAG, "audio route failed", it) }
    }

    // ---------------- signaling ----------------

    /** Caller: build the offer once the callee has accepted. */
    fun makeOffer() = post {
        val peer = pc ?: return@post
        peer.createOffer(object : SimpleSdpObserver("createOffer") {
            override fun onCreateSuccess(desc: SessionDescription) {
                peer.setLocalDescription(object : SimpleSdpObserver("setLocal(offer)") {
                    override fun onSetSuccess() = sendWhenGathered("offer")
                }, desc)
            }
        }, offerConstraints())
    }

    /** Callee: apply the caller's offer and answer it. */
    fun acceptOffer(sdp: String) = post {
        val peer = pc ?: return@post
        if (remoteDescriptionSet) return@post
        remoteDescriptionSet = true
        peer.setRemoteDescription(object : SimpleSdpObserver("setRemote(offer)") {
            override fun onSetSuccess() {
                post {
                    peer.createAnswer(object : SimpleSdpObserver("createAnswer") {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            peer.setLocalDescription(object : SimpleSdpObserver("setLocal(answer)") {
                                override fun onSetSuccess() = sendWhenGathered("answer")
                            }, desc)
                        }
                    }, offerConstraints())
                }
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    /** Caller: apply the callee's answer → media starts flowing. */
    fun acceptAnswer(sdp: String) = post {
        val peer = pc ?: return@post
        if (remoteDescriptionSet) return@post
        remoteDescriptionSet = true
        peer.setRemoteDescription(
            SimpleSdpObserver("setRemote(answer)"),
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private fun sendWhenGathered(kind: String) {
        post {
            val peer = pc ?: return@post
            val deadline = System.currentTimeMillis() + GATHER_TIMEOUT_MS
            while (!closed && peer.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(100)
            }
            if (closed) return@post
            val sdp = peer.localDescription?.description ?: return@post
            runCatching { onLocalSdp(kind, sdp) }.onFailure { Log.w(TAG, "send sdp failed", it) }
        }
    }

    private fun offerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
    }

    // ---------------- controls ----------------

    fun toggleMic() = post {
        val muted = !_micMuted.value
        audioTrack?.setEnabled(!muted)
        _micMuted.value = muted
    }

    fun toggleSpeaker() = post {
        val on = !_speakerOn.value
        _speakerOn.value = on
        applyRoute(on)
    }

    fun toggleCamera() = post {
        val off = !_cameraOff.value
        videoTrack?.setEnabled(!off)
        _cameraOff.value = off
    }

    fun switchCamera() = post {
        runCatching { capturer?.switchCamera(null) }
    }

    /** Releases camera, mic and the connection. Safe to call more than once. */
    @Suppress("DEPRECATION")
    fun close() {
        if (closed) return
        closed = true
        _remoteVideo.value = null
        _localVideo.value = null
        // Small delay so on-screen renderers detach before the GL context goes away.
        if (executor.isShutdown) return
        executor.schedule(Runnable {
            runCatching { capturer?.stopCapture() }
            runCatching { capturer?.dispose() }
            runCatching { videoSource?.dispose() }
            runCatching { textureHelper?.dispose() }
            runCatching { pc?.dispose() }
            runCatching { audioSource?.dispose() }
            runCatching { factory?.dispose() }
            runCatching { adm?.release() }
            runCatching { eglBase.release() }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager?.clearCommunicationDevice()
                audioManager?.mode = previousAudioMode
                audioManager?.isSpeakerphoneOn = previousSpeaker
            }
            capturer = null; videoSource = null; textureHelper = null; pc = null
            audioSource = null; audioTrack = null; videoTrack = null; factory = null; adm = null
            executor.shutdown()
        }, 400, TimeUnit.MILLISECONDS)
    }

    private fun fail(where: String, t: Throwable) {
        Log.e(TAG, "call engine $where failed", t)
        _mediaState.value = "failed"
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            _mediaState.value = when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> "connected"
                PeerConnection.IceConnectionState.DISCONNECTED -> "reconnecting"
                PeerConnection.IceConnectionState.FAILED -> "failed"
                else -> _mediaState.value
            }
            // Some phones reset the route when call audio starts — apply earpiece/speaker again.
            if (state == PeerConnection.IceConnectionState.CONNECTED) post { if (!closed) applyRoute(_speakerOn.value) }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(candidate: IceCandidate) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {
            stream.videoTracks.firstOrNull()?.let { if (!closed) _remoteVideo.value = it }
        }
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack && !closed) _remoteVideo.value = track
        }
    }

    init {
        post { runCatching { setUp() }.onFailure { fail("setup", it) } }
    }

    private open class SimpleSdpObserver(private val label: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.w(TAG, "$label create failed: $error") }
        override fun onSetFailure(error: String?) { Log.w(TAG, "$label set failed: $error") }
    }

    companion object {
        /** Wired / USB / Bluetooth headsets — used instead of the earpiece when connected. */
        private val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26 /* TYPE_BLE_HEADSET */
        )
        private const val TAG = "ChatboxCall"
        private const val STREAM_ID = "chatbox_s0"
        private const val GATHER_TIMEOUT_MS = 5_000L
        @Volatile private var initialized = false

        private fun ensureInitialized(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions()
                )
                initialized = true
            }
        }

        private fun iceServers(): List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder(
                listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")
            ).createIceServer(),
            PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder(
                listOf(
                    "turn:openrelay.metered.ca:80",
                    "turn:openrelay.metered.ca:443",
                    "turn:openrelay.metered.ca:443?transport=tcp"
                )
            ).setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        )
    }
}
