package com.advantech.uvc.tensorflow


import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcelable
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.advantech.uvc.R
import com.advantech.uvc.ml.SsdMobilenetV11Metadata1
import com.advantech.uvc.vcall.NV21ToBitmap
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.AspectRatioSurfaceView
import com.serenegiant.widget.AspectRatioTextureView
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.Locale
import java.util.Random

class CforYou :AppCompatActivity(), SurfaceHolder.Callback,
    TextureView.SurfaceTextureListener {
    private lateinit var textToSpeech: TextToSpeech
    private val TAG = "HHTensorFlowAct"
    lateinit var labels: List<String>
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1
    private var mNv21ToBitmap: NV21ToBitmap? = null

    /************************USB CAMERA********************************/
    private val DEFAULT_WIDTH = 1280
    private val DEFAULT_HEIGHT = 720
    private val ACTION_USB_PERMISSION: String = "com.serenegiant.USB_PERMISSION."
    private var mUsbDevice: UsbDevice? = null
    private var mCameraHelper: ICameraHelper? = null
    private var usbManager: UsbManager? = null
    private var mPermissionIntent: PendingIntent? = null
    var aspectRatioSurfaceView: AspectRatioSurfaceView? = null
    var aspectRatioTextureView: AspectRatioTextureView? = null
    var mSurfaceViewMain: SurfaceView? = null
    var remoteFrameViewContainer: FrameLayout? = null
    var localFrameViewContainer: FrameLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cfor_you)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor =
            ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        initCameraHelper()
        initViews()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: ")
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

    override fun onStop() {
        super.onStop()
        Toast.makeText(this, "onStop", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onStop: ")
        clearCameraHelper()
        unregisterReceiver(usbReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun initViews() {
        aspectRatioSurfaceView = findViewById<AspectRatioSurfaceView>(R.id.aspectSurfaceView)
        aspectRatioTextureView = findViewById<AspectRatioTextureView>(R.id.aspectTextureView)
        remoteFrameViewContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)
        localFrameViewContainer = findViewById<FrameLayout>(R.id.local_video_view_container)
        aspectRatioSurfaceView?.setAspectRatio(
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT
        )
        imageView = findViewById(R.id.imageView)
        aspectRatioSurfaceView?.holder?.addCallback(this)
        aspectRatioTextureView?.surfaceTextureListener = this
    }

    private fun initCameraHelper() {
        Log.d(TAG, "initCameraHelper:")
        if (mCameraHelper == null) {
            mNv21ToBitmap = NV21ToBitmap(this)
            mCameraHelper = CameraHelper(ContextCompat.RECEIVER_NOT_EXPORTED)
            mCameraHelper?.setStateCallback(iCameraHelpCallBack)
        }
    }


    private var iCameraHelpCallBack: ICameraHelper.StateCallback =
        object : ICameraHelper.StateCallback {
            override fun onAttach(device: UsbDevice?) {
                Log.d(TAG, "onAttach: ")
                selectDevice(device)
            }

            override fun onDeviceOpen(device: UsbDevice?, isFirstOpen: Boolean) {
                if (!isFirstOpen) {
                    Log.d(TAG, "onDeviceOpen: ")
                    Toast.makeText(this@CforYou, "onDeviceOpen", Toast.LENGTH_SHORT)
                        .show()
                    mCameraHelper?.openCamera()
                }
            }

            override fun onCameraOpen(device: UsbDevice?) {
                Log.d(TAG, "onCameraOpen: ")
                if (mCameraHelper != null) {
                    mCameraHelper?.startPreview()
                    val size = mCameraHelper?.previewSize
                    if (size != null) {
                        val width = size.width
                        val height = size.height
                        Log.d(TAG, "onCameraOpen: width =$width ,height =$height")
                        //auto aspect ratio
                        //  aspectRatioSurfaceView!!.setAspectRatio(width, height)
                        runOnUiThread { tensorFlowConfig() }
                    }
                    mCameraHelper!!.addSurface(aspectRatioTextureView?.surfaceTexture, false)

                }
            }

            override fun onCameraClose(device: UsbDevice?) {
                Log.d(TAG, "onCameraClose: ${mCameraHelper?.isCameraOpened}")
                if (mCameraHelper != null) {
                    mCameraHelper!!.removeSurface(aspectRatioSurfaceView!!.holder.surface)
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

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(
            cameraManager.cameraIdList[0],
            object : CameraDevice.StateCallback() {
                override fun onOpened(p0: CameraDevice) {
                    cameraDevice = p0

                    var surfaceTexture = textureView.surfaceTexture
                    var surface = Surface(surfaceTexture)

                    var captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)

                    cameraDevice.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(p0: CameraCaptureSession) {
                                p0.setRepeatingRequest(captureRequest.build(), null, null)
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                            }
                        },
                        handler
                    )
                }

                override fun onDisconnected(p0: CameraDevice) {

                }

                override fun onError(p0: CameraDevice, p1: Int) {

                }
            },
            handler
        )
    }

    private fun tensorFlowConfig() {
        Log.d(TAG, "tensorFlowConfig: ")
        var voiceStatus = false
        textToSpeech = TextToSpeech(
            applicationContext
        ) { status ->
            // if No error is found then only it will run
            if (status != TextToSpeech.ERROR) {
                // To Choose language of speech
                textToSpeech.setLanguage(Locale.ENGLISH)
            }
            if (status == TextToSpeech.SUCCESS) {
                Log.d("TTS", "Initialization success")
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "onStart: voice =$utteranceId")
                        voiceStatus = true
                        // mCameraHelper!!.stopPreview()
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "onDone: voice =$utteranceId")
                        // mCameraHelper!!.startPreview()
                        voiceStatus = false
                    }

                    override fun onError(utteranceId: String?) {
                        Log.d(TAG, "onError: voice = $utteranceId")
                    }

                })
            }
        }

        mCameraHelper?.setFrameCallback({ frame ->
            Log.d("TAG", "onFrame: ${frame} ")
            // create an object textToSpeech and adding features into it
            // create an object textToSpeech and adding features into it


            bitmap = aspectRatioTextureView?.bitmap!!
            var image = TensorImage.fromBitmap(bitmap)
            image = imageProcessor.process(image)
            val outputs = model.process(image)
            val locations = outputs.locationsAsTensorBuffer.floatArray
            val classes = outputs.classesAsTensorBuffer.floatArray
            val scores = outputs.scoresAsTensorBuffer.floatArray
            val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

            var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)

            val h = mutable.height
            val w = mutable.width
            paint.textSize = h / 15f
            paint.strokeWidth = h / 85f
            var x = 0

            scores.forEachIndexed { index, fl ->


                x = index
                x *= 4

                if (fl.toDouble() ==0.5) {
                    paint.setColor(colors.get(index))
                    paint.style = Paint.Style.STROKE
//                    canvas.drawRect(
//                        RectF(
//                            locations.get(x + 1) * w,
//                            locations.get(x) * h,
//                            locations.get(x + 3) * w,
//                            locations.get(x + 2) * h
//                        ), paint
//                    )
//                    paint.style = Paint.Style.FILL
//                    canvas.drawText(
//                        labels.get(classes.get(index).toInt()) + " " + fl.toString(),
//                        locations.get(x + 1) * w,
//                        locations.get(x) * h,
//                        paint
//                    )

//                    synchronized(this) { ->
//                        if (!voiceStatus) {
//                            var mostRecentUtteranceID =
//                                (Random().nextInt() % 9999999).toString() + ""
//                            val params = HashMap<String, String>()
//                            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] =
//                                mostRecentUtteranceID
//                            textToSpeech.speak(
//                                labels[classes.get(index).toInt()],
//                                TextToSpeech.QUEUE_FLUSH,
//                                params
//                            )
//                        }
//                    }

                }
            }
            imageView.setImageBitmap(mutable)


        }, UVCCamera.FRAME_FORMAT_YUYV)

    }

    /************************TEXTURE VIEW********************************/

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureAvailable: width = $width, height = $height")
        selectDevice(mUsbDevice)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChanged: width = $width, height = $height")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d(TAG, "onSurfaceTextureDestroyed: $surface")
        if (mCameraHelper != null)
            mCameraHelper!!.removeSurface(surface)
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (mCameraHelper != null) {
            Log.d("TAG", "onSurfaceTextureUpdated: camera open =${mCameraHelper!!.isCameraOpened}")
            return
        }

        // aspectRatioTextureView?.setSurfaceTexture(surface)
        /*runOnUiThread {
            tensorFlowConfig(surface)
        }*/
        // mCameraHelper!!.addSurface(surface, false)

    }

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

    private fun selectDevice(device: UsbDevice?) {
        if (device != null && mCameraHelper != null) {
            mCameraHelper?.selectDevice(device)
            Log.v(
                TAG,
                "selectDevice:device=" + device.deviceName
            )
        }
    }


    private fun clearCameraHelper() {
        Log.d(TAG, "clearCameraHelper:")
        if (mCameraHelper != null) {
            mCameraHelper!!.release()
            mCameraHelper = null
        }
    }


    /************************SURFACE VIEW********************************/
    override fun surfaceCreated(holder: SurfaceHolder) {
        Toast.makeText(this@CforYou, "surfaceCreated", Toast.LENGTH_SHORT)
            .show()
        if (mCameraHelper != null) {
            Log.d(TAG, "surfaceCreated: holder = $holder ")
            mCameraHelper!!.addSurface(holder.surface, false)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Toast.makeText(this@CforYou, "surfaceChanged", Toast.LENGTH_SHORT)
            .show()
        Log.d(TAG, "surfaceChanged: holder = $holder ,width =$width, height =$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (mCameraHelper != null) {
            mCameraHelper!!.removeSurface(holder.surface)
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

    fun onCameraClose(view: View) {
        mCameraHelper!!.release()
        if (mCameraHelper!!.isCameraOpened()) {
            mCameraHelper!!.stopPreview()
            mCameraHelper!!.closeCamera()
        }
        mCameraHelper = null

        Toast.makeText(this, "onCameraClose", Toast.LENGTH_SHORT).show()
    }

    fun onCameraOn(view: View) {
        initCameraHelper()
        // registerUSBReceiver()
        if (mCameraHelper != null) {
            val list = mCameraHelper!!.deviceList
            if (list != null && list.size > 0) {
                Log.d(TAG, "onClick: " + list[0])
                mUsbDevice = list[0]
                mCameraHelper!!.selectDevice(list[0])
                //usbManager?.requestPermission(list[0], mPermissionIntent)
            }
        }



        Toast.makeText(this, "onCameraOn", Toast.LENGTH_SHORT).show()
    }
}