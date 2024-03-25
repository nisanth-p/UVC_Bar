package com.advantech.uvc.vcall

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.advantech.uvc.R
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.AspectRatioSurfaceView
import com.serenegiant.widget.AspectRatioTextureView
import com.serenegiant.widget.CameraViewInterface
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.IVideoFrameObserver.VideoFrame
import io.agora.rtc.RtcEngine
import io.agora.rtc.RtcEngineEx
import io.agora.rtc.video.AgoraVideoFrame
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.experimental.and


/*import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.RtcEngineEx
import io.agora.rtc2.video.AgoraVideoFrame
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration*/


class AgoraActivity : AppCompatActivity(), View.OnClickListener, SurfaceHolder.Callback {

    private val TAG = "###AgoraActivity"
    private val DEFAULT_WIDTH = 950
    private val DEFAULT_HEIGHT = 1450
    private val ACTION_USB_PERMISSION: String = "com.serenegiant.USB_PERMISSION."
    private var mUsbDevice: UsbDevice? = null
    private var mCameraHelper: ICameraHelper? = null
    private var usbManager: UsbManager? = null
    private var mNv21ToBitmap: NV21ToBitmap? = null
    private var mPermissionIntent: PendingIntent? = null
    var aspectRatioSurfaceView: AspectRatioSurfaceView? = null
    var aspectRatioSurfaceView1: AspectRatioSurfaceView? = null
    var aspectRatioTextureView: AspectRatioTextureView? = null
    var mSurfaceViewMain: SurfaceView? = null
    var mSurfaceViewMain1: SurfaceView? = null
    var cameraViewInterface: CameraViewInterface? = null
    var relativeLayoutHead: RelativeLayout? = null
    var remoteFrameViewContainer: FrameLayout? = null
    var localFrameViewContainer: FrameLayout? = null
    var i = 0
    var userType = 0  //0 = user 1= guide
    var cameraType = 0//0 = phone 1= usb
    var viewType: Int = 1  //0=surface 1= texture
    var phoneModel = ""
    var textToSpeech: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: ${android.os.Build.MODEL}")
        phoneModel = android.os.Build.MODEL
        setContentView(R.layout.activity_agora)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        initCameraHelper()
        initViews()
        initAgoraEngineAndJoinChannel()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: ")
        Toast.makeText(this, "onStart", Toast.LENGTH_SHORT).show()
        initCameraHelper()
        registerUSBReceiver()
        getUSBDeviceList()

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: ")
        if (mCameraHelper != null && usbManager != null && mUsbDevice != null) {
            usbManager?.requestPermission(mUsbDevice, mPermissionIntent)

        } else {
            Toast.makeText(this, "onResume: mUsb =$mUsbDevice", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initCameraHelper() {
        Log.d(TAG, "initCameraHelper:")
        if (mCameraHelper == null) {
            mNv21ToBitmap = NV21ToBitmap(this)
            mCameraHelper = CameraHelper(ContextCompat.RECEIVER_NOT_EXPORTED)
            mCameraHelper?.setStateCallback(iCameraHelpCallBack)
        }
    }

    private fun initViews() {
        aspectRatioSurfaceView = findViewById<AspectRatioSurfaceView>(R.id.aspect_ratio_view)
        aspectRatioTextureView = findViewById<AspectRatioTextureView>(R.id.aspect_ratio_TXview)
        remoteFrameViewContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)
        localFrameViewContainer = findViewById<FrameLayout>(R.id.local_video_view_container)
        val btnOpenCamera = findViewById<Button>(R.id.btnOpenCamera)
        val btnCloseCamera = findViewById<Button>(R.id.btnCloseCamera)
        aspectRatioSurfaceView?.setAspectRatio(
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT
        )
        aspectRatioSurfaceView?.holder?.addCallback(this)
        btnOpenCamera.setOnClickListener(this)
        btnCloseCamera.setOnClickListener(this)

    }


    private fun initAgoraEngineAndJoinChannel() {
        initializeAgoraEngine()
        setupVideoProfile()
        if (phoneModel == "22120RN86I") { //Redmi
            if (viewType == 0) setupLocalVideo()
            else setupLocalVideoTX()
            joinChannel()
        }

    }

    private var mRtcEngine: RtcEngine? = null
    private fun initializeAgoraEngine() {
        try {
            mRtcEngine = RtcEngine.create(
                baseContext,
                getString(R.string.agora_app_id), mRtcEventHandler
            )

        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw RuntimeException(
                "NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(
                    e
                )
            )
        }
    }

    private fun joinChannel() {
        var token: String? = getString(R.string.agora_access_token)
        if (token!!.isEmpty()) {
            token = null
        }
        mRtcEngine!!.joinChannel(
            token,
            "testcall",
            "Extra Optional Data",
            0
        ) // if you do not specify the uid, we will generate the uid for you
    }

    private fun setupUSBRemoteVideo(uid: Int) {

        if (remoteFrameViewContainer?.childCount!! >= 1) {
            return
        }
        // remoteFrameViewContainer?.removeAllViews()

        mSurfaceViewMain1 = RtcEngine.CreateRendererView(baseContext)
        val parentView = aspectRatioSurfaceView?.parent as ViewGroup
        parentView.removeView(aspectRatioSurfaceView)
        remoteFrameViewContainer?.addView(mSurfaceViewMain1)
        mRtcEngine!!.setupRemoteVideo(
            VideoCanvas(
                mSurfaceViewMain1,
                VideoCanvas.RENDER_MODE_FIT,
                uid
            )
        )
    }

    private fun setupRemoteVideoTX(uid: Int) {
        val container = findViewById(R.id.remote_video_view_container) as FrameLayout
        if (container.childCount >= 1) return
        val textureView = RtcEngine.CreateTextureView(baseContext)
        container.addView(textureView)
        mRtcEngine!!.setupRemoteVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_FIT, uid))
        textureView.tag = uid // for mark purpose
        val tipMsg = findViewById<TextView>(R.id.quick_tips_when_use_agora_sdk) // optional UI
        tipMsg.visibility = View.GONE
    }

    private fun setupRemoteVideo(uid: Int) {
        val container = findViewById(R.id.remote_video_view_container) as FrameLayout
        if (container.childCount >= 1) return
        val surfaceView = RtcEngine.CreateRendererView(baseContext)
        container.addView(surfaceView)
        mRtcEngine!!.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid))
        surfaceView.tag = uid // for mark purpose
        val tipMsg = findViewById<TextView>(R.id.quick_tips_when_use_agora_sdk) // optional UI
        tipMsg.visibility = View.GONE
    }

    private fun setupLocalVideo() {
        Toast.makeText(this, "setupLocalVideo: ", Toast.LENGTH_SHORT).show()
        val container = findViewById(R.id.local_video_view_container) as FrameLayout
        val surfaceView = RtcEngine.CreateRendererView(baseContext)
        surfaceView?.setZOrderMediaOverlay(true)
        container.addView(surfaceView)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun setupLocalVideoTX() {
        Toast.makeText(this, "setupLocalVideoTX: ", Toast.LENGTH_SHORT).show()
        val container = findViewById(R.id.local_video_view_container) as FrameLayout
        val textureView = RtcEngine.CreateTextureView(baseContext)
        container.addView(textureView)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun setupLocalUSBVideo() {
        Toast.makeText(this, "setupLocalVideo: ", Toast.LENGTH_SHORT).show()
        val container = findViewById(R.id.local_video_view_container) as FrameLayout
        val surfaceView = RtcEngine.CreateRendererView(baseContext)
        surfaceView?.setZOrderMediaOverlay(true)
        container.addView(surfaceView)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun setupLocalUSBVideoTX() {
        Toast.makeText(this, "setupLocalVideo: ", Toast.LENGTH_SHORT).show()
        val container = findViewById(R.id.local_video_view_container) as FrameLayout
        val textureView = RtcEngine.CreateTextureView(baseContext)
        container.addView(textureView)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private var iCameraHelpCallBack: ICameraHelper.StateCallback =
        object : ICameraHelper.StateCallback {
            override fun onAttach(device: UsbDevice?) {
                Log.d(TAG, "onAttach: ")
                selectDevice(device)
            }

            override fun onDeviceOpen(device: UsbDevice?, isFirstOpen: Boolean) {

                Toast.makeText(this@AgoraActivity, "onDeviceOpen", Toast.LENGTH_SHORT).show()
                if (!isFirstOpen) {
                    Log.d(
                        TAG,
                        "onDeviceOpen: ${mCameraHelper?.isCameraOpened} , isOPened =" + isFirstOpen
                    )
                    mCameraHelper?.openCamera()
                }

            }

            override fun onCameraOpen(device: UsbDevice?) {
                Log.d(TAG, "onCameraOpen: ")
                if (
                    phoneModel == "CPH2527"
                )
                    if (mCameraHelper != null) {
                        mCameraHelper?.startPreview()
                        setAllControl()
                        val size = mCameraHelper?.previewSize
                        if (size != null) {
                            val width = size.width
                            val height = size.height
                            Log.d(TAG, "onCameraOpen: width =$width ,height =$height")
                            //auto aspect ratio
                            //  aspectRatioSurfaceView!!.setAspectRatio(width, height)

                            if (viewType == 0)
                                setupLocalUSBVideo()
                            else
                                setupLocalUSBVideoTX()
                            joinChannel()
                            frameConfig(width, height)
                        }
                        // mCameraHelper!!.addSurface(aspectRatioSurfaceView!!.holder.surface, false)
                    }

            }

            override fun onCameraClose(device: UsbDevice?) {
                Log.d(TAG, "onCameraClose: ${mCameraHelper?.isCameraOpened}")

                if (mCameraHelper != null) {
                    mCameraHelper!!.stopPreview()
                    mCameraHelper!!.closeCamera()
                    if (i == 0) {
                        mCameraHelper!!.removeSurface(aspectRatioSurfaceView!!.holder.surface)
                    } else if (i == 1) {
                        mCameraHelper!!.removeSurface(mSurfaceViewMain!!.holder.surface)
                    }

                }
            }

            override fun onDeviceClose(device: UsbDevice?) {
                Log.d(TAG, "onDeviceClose: ")
            }

            override fun onDetach(device: UsbDevice?) {
                Log.d(TAG, "onDetach: ")
            }

            override fun onCancel(device: UsbDevice?) {
                Log.d(TAG, "onCancel: ")
            }

        }

    fun nv21ToBit(data: ByteArray, width: Int, height: Int) {
        val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
        val byteArray = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, byteArray)
        val bytes = byteArray.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun frameConfig(frameW: Int, frameH: Int) {
        if (mCameraHelper != null) {
            mRtcEngine?.setExternalVideoSource(true, true, true)
            // mRtcEngine?.setExternalVideoSource(true, false, Constants.ExternalVideoSourceType.VIDEO_FRAME)
            Log.d(TAG, "frameConfig: Format List = " + mCameraHelper?.getSupportedFormatList())
            mCameraHelper?.setFrameCallback({ frame ->
                Log.d(TAG, "onFrame: ${frame} ")

                val nv21 = ByteArray(frame!!.remaining())
                frame[nv21, 0, nv21.size]
                val bitmap = mNv21ToBitmap!!.nv21ToBitmap(nv21, frameW, frameH)
                val width: Int = mCameraHelper?.previewSize?.width!!
                val height: Int = mCameraHelper?.previewSize?.height!!

                //FORMAT_BGRA
                //BUFFER_TYPE_BUFFER
                if (width > 0 && height > 0) {
                    val videoFrame = AgoraVideoFrame()
                    videoFrame.format = AgoraVideoFrame.FORMAT_BGRA
                    videoFrame.stride = width
                    videoFrame.height = height
                    videoFrame.textureID = 2
                    videoFrame.timeStamp = System.currentTimeMillis() // Set the timestamp
                    videoFrame.buf =
                        nv21           // Check if the RTC engine is initialized before pushing the frame
                    videoFrame.syncMode = true


                    if (mRtcEngine != null) {
                        mRtcEngine!!.pushExternalVideoFrame(videoFrame)

                    } else {
                        val v = RtcEngineEx.CreateRendererView(baseContext)
                        val c = v.cameraDistance
                        Log.d(TAG, "frameConfig: mRtcEngine is null")
                    }
                } else {
                    Log.e("CameraHelper", "Invalid preview dimensions")
                }


            }, UVCCamera.FRAME_FORMAT_YUYV) //FRAME_FORMAT_YUYV



        }
    }

    fun onRenderFrame(frame: AgoraVideoFrame, cameraFrame: ByteBuffer) {

        if (cameraFrame != null) {
            try {
                val convertedData = convertYuv420ToNv21(
                    cameraFrame,
                    isYuv420P = true
                ) // Adjust based on your format
                if (convertedData != null) {
                    frame.buf = convertedData

                }
            } catch (e: Exception) {
                Log.e("MyCustomVideoSource", "Error converting frame: $e")
                // Handle the error gracefully (e.g., notify user, retry)
            }
        }
    }

    private fun convertYuv420ToNv21(yuv420Data: ByteBuffer, isYuv420P: Boolean): ByteArray? {
        val nv21Data = ByteArray(yuv420Data.remaining() * 12 / 8)
        try {
            for (i in 0 until yuv420Data.remaining() / 4) {
                nv21Data[i] = yuv420Data[i] // Copy Y component
                val uvOffset = i / 2 + yuv420Data.remaining() / 4
                if (isYuv420P) {
                    // Separate U and V from their planes (YUV 420P)
                    nv21Data[i + yuv420Data.remaining() / 4] = yuv420Data[uvOffset]
                    nv21Data[i + yuv420Data.remaining() / 2 + yuv420Data.remaining() / 8] =
                        yuv420Data[uvOffset + 1]
                } else {
                    // Interleaved U and V, extract and combine (YUV 420SP)
                    val u = yuv420Data[uvOffset] and (0xFF).toByte()
                    val v = yuv420Data[uvOffset + 1] and (0xFF).toByte()
                    nv21Data[i + yuv420Data.remaining() / 4] =
                        ((v.toInt() shl 2) or (u.toInt() shr 6)).toByte()
                }
            }
            return nv21Data
        } catch (e: Exception) {
            Log.e("MyCustomVideoSource", "Error during conversion: $e")
            return null // Indicate error
        }
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        Toast.makeText(this@AgoraActivity, "surfaceCreated", Toast.LENGTH_SHORT)
            .show()
        if (mCameraHelper != null) {
            Log.d(TAG, "surfaceCreated: holder = $holder ")
            mCameraHelper!!.addSurface(holder.surface, false)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Toast.makeText(this@AgoraActivity, "surfaceChanged", Toast.LENGTH_SHORT)
            .show()
        Log.d(TAG, "surfaceChanged: holder = $holder ,width =$width, height =$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (mCameraHelper != null) {
            mCameraHelper!!.removeSurface(holder.surface)
        }
    }

    /*******************************************************************************************************/


    private fun registerUSBReceiver() {
        mPermissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(
                ACTION_USB_PERMISSION
            ), PendingIntent.FLAG_IMMUTABLE
        )
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (Build.VERSION.SDK_INT >= 34) {
                ContextCompat.registerReceiver(
                    this,
                    usbReceiver,
                    filter,
                    ContextCompat.RECEIVER_VISIBLE_TO_INSTANT_APPS
                )
            } else {
                registerReceiver(usbReceiver, filter, RECEIVER_VISIBLE_TO_INSTANT_APPS)
            }
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun getUSBDeviceList() {

        if (usbManager != null) {
            val deviceList: HashMap<String, UsbDevice>? = usbManager?.deviceList
            for (usbDevice in deviceList?.values!!) {
                mUsbDevice = usbDevice
                usbManager?.requestPermission(usbDevice, mPermissionIntent)
            }
            if (mUsbDevice != null) {
                val hasPermision: Boolean = usbManager?.hasPermission(mUsbDevice) == true
                if (hasPermision) {
                    Log.d(TAG, "getUSBDeviceList: hasPermision = $hasPermision ")
                    selectDevice(mUsbDevice)
                }
                val connection: UsbDeviceConnection =
                    usbManager?.openDevice(mUsbDevice) ?: return

            } else {
                Toast.makeText(
                    this,
                    "getUSBDeviceList: mUsbDevice =$mUsbDevice",
                    Toast.LENGTH_SHORT
                ).show()

            }
        }
    }


    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "onReceive: --------------------$action")
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    mUsbDevice =
                        intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED,
                            false
                        )
                    ) {
                        if (mUsbDevice != null) selectDevice(mUsbDevice)
                        else Log.d(TAG, "EXTRA_PERMISSION_GRANTED: device not found-1")
                    } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                        Log.d(TAG, "ACTION_USB_DEVICE_ATTACHED: device attached")
                        if (mUsbDevice != null) selectDevice(mUsbDevice)
                        else Log.d(TAG, "ACTION_USB_DEVICE_ATTACHED : device not found-2")

                    } else {
                        Log.d(
                            TAG,
                            "UsbDevice = $mUsbDevice ,Action = ${intent.action}"
                        )
                        /* if (mUsbDevice != null) {
                             selectDevice(mUsbDevice)
                         } else {
                             Log.d(TAG, "************")
                         }*/
                    }
                }
            }
        }
    }


    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
            Log.d(
                TAG,
                "onFirstRemoteVideoDecoded: uid =$uid, width =$width, height =$height, elapsed =$elapsed"
            )

            runOnUiThread {
                if (viewType == 0)
                    setupRemoteVideo(uid)
                else
                    setupRemoteVideoTX(uid)
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "onUserOffline: ")
            runOnUiThread { onRemoteUserLeft() }
        }

        override fun onUserMuteVideo(uid: Int, muted: Boolean) {
            Log.d(TAG, "onUserMuteVideo: ")
            runOnUiThread { onRemoteUserVideoMuted(uid, muted) }
        }
    }

    private fun onRemoteUserLeft() {
        val container = findViewById(R.id.remote_video_view_container) as FrameLayout
        container.removeAllViews()

        val tipMsg = findViewById<TextView>(R.id.quick_tips_when_use_agora_sdk) // optional UI
        tipMsg.visibility = View.VISIBLE
    }

    private fun onRemoteUserVideoMuted(uid: Int, muted: Boolean) {
        val container = findViewById(R.id.remote_video_view_container) as FrameLayout

        val surfaceView = container.getChildAt(0) as SurfaceView

        val tag = surfaceView.tag
        if (tag != null && tag as Int == uid) {
            surfaceView.visibility = if (muted) View.GONE else View.VISIBLE
        }
    }


    private fun leaveChannel() {
        mRtcEngine!!.leaveChannel()
    }

    private fun selectDevice(device: UsbDevice?) {
        if (device != null && mCameraHelper != null) {
            mCameraHelper?.selectDevice(device)
            Log.v(
                TAG,
                "selectDevice:device=" + device.deviceName
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Toast.makeText(this, "onNewIntent", Toast.LENGTH_SHORT).show()

        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            Log.d(
                TAG,
                "onNewIntent: ---------------------ACTION_USB_DEVICE_ATTACHED---------------"
            )
            val mUsbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            selectDevice(mUsbDevice)
        } else {
            Log.d(TAG, "onNewIntent:${intent.action} ")
        }
    }


    override fun onStop() {
        super.onStop()
        Toast.makeText(this, "onStop", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onStop: ")
        clearCameraHelper()
        unregisterReceiver(usbReceiver)
    }


    private fun clearCameraHelper() {
        Log.d(TAG, "clearCameraHelper:")
        if (mCameraHelper != null) {
            mCameraHelper!!.release()
            mCameraHelper = null
        }
    }

    override fun onClick(v: View) {
        if (v.id == R.id.btnOpenCamera) {
            // select a uvc device
            if (mCameraHelper != null) {
                val list = mCameraHelper!!.deviceList
                if (list != null && list.size > 0) {
                    Log.d(TAG, "onClick: " + list[0])
                    mUsbDevice = list[0]
                    mCameraHelper!!.selectDevice(list[0])
                    usbManager?.requestPermission(list[0], mPermissionIntent)
                }
            }
        } else if (v.id == R.id.btnCloseCamera) {
            // close camera
            if (mCameraHelper != null) {
                mCameraHelper!!.closeCamera()
            }
        }
    }

    fun setAllControl() {
        if (mCameraHelper != null) {
            Toast.makeText(this, "autofocus true", Toast.LENGTH_SHORT).show()
            val uvcControl = mCameraHelper!!.uvcControl
            uvcControl.focusAuto = true
        }
    }

    private fun setupVideoProfile() {
        mRtcEngine!!.enableVideo()
        mRtcEngine!!.setVideoProfile(Constants.VIDEO_CODEC_H264, false) // Earlier than 2.3.0
        mRtcEngine!!.setVideoEncoderConfiguration(
            VideoEncoderConfiguration
                (
                VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            )
        )
    }

    fun onLocalVideoMuteClicked(view: View) {}
    fun onLocalAudioMuteClicked(view: View) {}
    fun onSwitchCameraClicked(view: View) {}
    fun onEncCallClicked(view: View) {}
    override fun onDestroy() {
        super.onDestroy()
        leaveChannel()
        RtcEngine.destroy()
        mRtcEngine = null
    }

    private var engine: RtcEngineEx? = null


    /*
    *
    *
    *
    *  private fun setupLocalVideo() {
         val container = findViewById(R.id.local_video_view_container) as FrameLayout
        val surfaceView = RtcEngine.CreateRendererView(baseContext)

        surfaceView?.setZOrderMediaOverlay(true)
        container.addView(surfaceView)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0))
    }
    private fun setupLocalVideo1() {
       // val container = findViewById(R.id.local_video_view_container) as FrameLayout
        mSurfaceViewMain = RtcEngine.CreateRendererView(baseContext)
        localFrameViewContainer?.removeAllViews()
        mSurfaceViewMain?.setZOrderMediaOverlay(true)
        localFrameViewContainer?.addView(mSurfaceViewMain)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(mSurfaceViewMain, VideoCanvas.RENDER_MODE_FIT, 0))
    }
    private fun setupRemoteSurface() {
        mSurfaceViewMain1 = RtcEngine.CreateRendererView(baseContext)
        mSurfaceViewMain1?.setZOrderMediaOverlay(true)
        remoteFrameViewContainer?.addView(mSurfaceViewMain1)
        mRtcEngine!!.setupRemoteVideo(
            VideoCanvas(
                mSurfaceViewMain1,
                VideoCanvas.RENDER_MODE_FIT,
                0
            )
        )
    }

    private fun setupLocalSurface1() {
        mSurfaceViewMain1 = RtcEngine.CreateRendererView(baseContext)
        mSurfaceViewMain1?.setZOrderMediaOverlay(true)
        remoteFrameViewContainer?.addView(mSurfaceViewMain1)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(mSurfaceViewMain1, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun setupUSBLocalVideo() {
        mSurfaceViewMain = RtcEngine.CreateRendererView(baseContext)
        //mSurfaceViewMain?.setZOrderMediaOverlay(true)
        if (i == 0) {
            val parentView = aspectRatioSurfaceView?.parent as ViewGroup
            parentView.removeView(aspectRatioSurfaceView)
            aspectRatioSurfaceView?.setZOrderMediaOverlay(true)
            mSurfaceViewMain = aspectRatioSurfaceView
            localFrameViewContainer?.addView(mSurfaceViewMain)
            mRtcEngine!!.setupLocalVideo(
                VideoCanvas(
                    mSurfaceViewMain,
                    VideoCanvas.RENDER_MODE_FIT,
                    0
                )
            )

            mCameraHelper!!.addSurface(mSurfaceViewMain!!.holder.surface, false)

        } else if (i == 1) {
            localFrameViewContainer?.removeAllViews()
            localFrameViewContainer?.addView(aspectRatioSurfaceView)
            mRtcEngine!!.setupLocalVideo(
                VideoCanvas(
                    aspectRatioSurfaceView,
                    VideoCanvas.RENDER_MODE_FIT,
                    0
                )
            )
            mCameraHelper!!.addSurface(aspectRatioSurfaceView!!.holder.surface, false)

        }

    }
    *
    *
    *
    *  private fun getEngine(){
        // Check if the context is valid
        val context: Context = baseContext ?: return
        try {
            val config = RtcEngineConfig()
            /*
             * The context of Android Activity
             */config.mContext = context.applicationContext
            /*
             * The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id"> How to get the App ID</a>
             */config.mAppId = getString(R.string.agora_app_id)
            /* Sets the channel profile of the Agora RtcEngine.
             CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
             Use this profile in one-on-one calls or group calls, where all users can talk freely.
             CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
             channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
             an audience can only receive streams.*/config.mChannelProfile =
                Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            /*
             * IRtcEngineEventHandler is an abstract class providing default implementation.
             * The SDK uses this class to report to the app on SDK runtime events.
             */config.mEventHandler = mRtcEventHandler
            config.mAudioScenario =
                Constants.AudioScenario.getValue(Constants.AudioScenario.DEFAULT)

            engine = RtcEngine.create(config) as RtcEngineEx


        } catch (e: java.lang.Exception) {
            e.printStackTrace()

        }
    }
    *
    *
    *
    * */
}

