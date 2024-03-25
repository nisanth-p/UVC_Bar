package com.advantech.uvc;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


import com.advantech.uvc.Agora.RtcTokenBuilder2;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.CameraPreviewConfig;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.VideoCapture;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCControl;
import com.serenegiant.widget.AspectRatioSurfaceView;
import com.serenegiant.widget.AspectRatioTextureView;
import com.serenegiant.widget.CameraViewInterface;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.AgoraVideoFrame;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;
import kotlin.Suppress;

public class BasicPreviewActivity extends AppCompatActivity implements View.OnClickListener, Camera.PreviewCallback, CameraViewInterface.Callback {
    private static final String TAG = "xxBasicPreviewAct";

    private static final boolean DEBUG = true;
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;


    private ICameraHelper mCameraHelper;

    public AspectRatioSurfaceView mCameraViewMain;
    public AspectRatioTextureView mSurfaceViewMain;
    public CameraViewInterface cameraViewInterface;

    private static final String ACTION_USB_PERMISSION_BASE = "com.serenegiant.USB_PERMISSION.";
    private final String ACTION_USB_PERMISSION = ACTION_USB_PERMISSION_BASE;
    private PendingIntent mPermissionIntent;
    UsbDevice device = null;
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive: --------------------" + action);
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            selectDevice(device);
                        }
                    } else {
                        Log.d(TAG, "******permission denied for device ******" + device);
                    }
                }
            }
        }
    };
    private UsbManager manager;
    private NV21ToBitmap mNv21ToBitmap;

    @Suppress(names = "UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_preview);
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        initCameraHelper();
        mNv21ToBitmap = new NV21ToBitmap(this);
        initViews();

    }
private RtcEngine mRtcEngine;
    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }

        //Initialise RtcEngine
        try {
            mRtcEngine = RtcEngine.create(getApplicationContext(),
                    appId,
                    mRtcEventHandler);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            throw new RuntimeException("Agora RtcEngine initialization failed.");
        }

        mRtcEngine.enableVideo();
        mRtcEngine.setClientRole(IRtcEngineEventHandler.ClientRole.CLIENT_ROLE_BROADCASTER);
        mRtcEngine.enableAudio();
        mRtcEngine.setEnableSpeakerphone(true);

        mRtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration
                (VideoEncoderConfiguration.VD_640x360,
                        VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                        VideoEncoderConfiguration.STANDARD_BITRATE,
                        VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT));
    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(final int uid, int elapsed) {
            // Called when a remote user joins the channel
            runOnUiThread(() -> {

                Toast.makeText(BasicPreviewActivity.this, "User Joined", Toast.LENGTH_SHORT).show();
                setupRemoteVideo(uid); // Set up remote video for the joined user
            });
        }

        @Override
        public void onUserOffline(final int uid, int reason) {
            // Called when a remote user leaves the channel or goes offline
         /*   runOnUiThread(() -> onRemoteUserLeft(uid));*/
            Toast.makeText(BasicPreviewActivity.this, "User Left", Toast.LENGTH_SHORT).show();
        }

    };
    private SurfaceView mRemoteView;
    RelativeLayout container;
    private void setupRemoteVideo(int uid) {
        mRemoteView = RtcEngine.CreateRendererView(getBaseContext());
        mRemoteView.setZOrderMediaOverlay(true);
        container.addView(mRemoteView);
        mRtcEngine.setDefaultAudioRoutetoSpeakerphone(true);
        mRtcEngine.setupRemoteVideo(new VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
        mRemoteView.setTag(uid);

    }

    private String appCertificate="121eb9412ea34af698e1c1c5e3f875e5";
    private String appId = "5bffe0a0b95345bd98ff16f92b06b32c";
    private String temp_token = "007eJxTYBDYqHGBK3BHtcKizC7NvwExKklFJ2eeOdKtVvTbf9uMQ74KDKZJaWmpBokGSZamxiamSSmWFmlphmZplkZJBmZJxkbJr7tupDYEMjK4i/KyMDJAIIjPwVCSWlySnJiTw8AAAMkhIRA=";
    private String callerChannelName="testcall";
    private int uid=0;
    private int expirationTimeInSeconds = 3600;

    private String generatToken(String ch_name) {
        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        int timestamp = Integer.parseInt(""+System.currentTimeMillis() / 1000 + expirationTimeInSeconds);
        String result = tokenBuilder.buildTokenWithUid(
                appId, appCertificate,
                ch_name, uid, RtcTokenBuilder2.Role.ROLE_PUBLISHER, timestamp, timestamp
        );
        Log.d(TAG, "generatToken: "+result.toString());
        return result;
    }

    private void joinChannel() {
        // 1. Users can only see each other after they join the same channel
        // successfully using the same app id.
        // 2. One token is only valid for the channel name that
        // you use to generate this token.
        String token = "";
        if (TextUtils.isEmpty(token) || TextUtils.equals(token, generatToken(callerChannelName))) {
            token = null; // default, no token
        }
        mRtcEngine.joinChannel(token, "channel_name", "Extra Optional Data", 0);

    }
    private void initUSBCamera() {
        cameraViewInterface = (CameraViewInterface) findViewById(R.id.svCameraViewMain);
        cameraViewInterface.setCallback(this);

    }
    private final ICameraHelper.StateCallback mStateListener =
            new ICameraHelper.StateCallback() {
                @Override
                public void onAttach(UsbDevice device) {
                    if (DEBUG) Log.v(TAG, "onAttach:");

                    selectDevice(device);
                }

                @Override
                public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
                    if (DEBUG) Log.v(TAG, "onDeviceOpen:");
                    Toast.makeText(BasicPreviewActivity.this, "onDeviceOpen", Toast.LENGTH_SHORT).show();
                    mCameraHelper.openCamera();


                }


                @Override
                public void onCameraOpen(UsbDevice device) {
                    if (DEBUG) Log.v(TAG, "onCameraOpen:");
                    Toast.makeText(BasicPreviewActivity.this, "onCameraOpen", Toast.LENGTH_SHORT).show();
                    mCameraHelper.startPreview();
                    setAllControl();
                    Size size = mCameraHelper.getPreviewSize();
                    mCameraHelper.setFrameCallback(frame -> {

                        Log.d(TAG, "onCameraOpen: " + frame.toString());
                        byte[] nv21 = new byte[frame.remaining()];
                        frame.get(nv21, 0, nv21.length);

                        Bitmap bitmap = mNv21ToBitmap.nv21ToBitmap(nv21, size.width, size.height);
                        runOnUiThread(() -> {
//                    mFrameCallbackPreview.setImageBitmap(bitmap);
                        });
                        if (size != null) {
                            int width = size.width;
                            int height = size.height;
                            //auto aspect ratio
                            mCameraViewMain.setAspectRatio(width, height);
                            if (width > 0 && height > 0) {
                                AgoraVideoFrame videoFrame = new AgoraVideoFrame();
                                videoFrame.format = AgoraVideoFrame.FORMAT_NV21;
                                videoFrame.stride = width;
                                videoFrame.height = height;
                                videoFrame.textureID = 2;
                                videoFrame.timeStamp = System.currentTimeMillis(); // Set the timestamp
                                videoFrame.buf = nv21;
                                YuvImage yuv = new YuvImage(nv21, mCameraHelper.getSupportedFormatList().get(0).type, width, height, null);
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);
                                byte[] bytes = out.toByteArray();
                              /*  AsyncAWSImageDetection runner = new AsyncAWSImageDetection();*/

                                // Check if the RTC engine is initialized before pushing the frame
                                if (mRtcEngine != null) {
                                    mRtcEngine.pushExternalVideoFrame(videoFrame);
                                }
                            } else {
                                Log.e("CameraHelper", "Invalid preview dimensions");
                            }
                        }
                    }, UVCCamera.PIXEL_FORMAT_NV21);
                    mRtcEngine.setExternalVideoSource(true, true, true);


                    mCameraHelper.addSurface(mCameraViewMain.getHolder().getSurface(), false);


                }

                @Override
                public void onCameraClose(UsbDevice device) {
                    if (DEBUG) Log.v(TAG, "onCameraClose:");

                    if (mCameraHelper != null) {
                        mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
                    }
                }

                @Override
                public void onDeviceClose(UsbDevice device) {
                    if (DEBUG) Log.v(TAG, "onDeviceClose:");
                }

                @Override
                public void onDetach(UsbDevice device) {
                    if (DEBUG) Log.v(TAG, "onDetach:");
                }

                @Override
                public void onCancel(UsbDevice device) {
                    if (DEBUG) Log.v(TAG, "onCancel:" + device);
                }

            };

    private void initViews() {
        mCameraViewMain = findViewById(R.id.svCameraViewMain);
        container = findViewById(R.id.container);
        mCameraViewMain.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        mCameraViewMain.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Toast.makeText(BasicPreviewActivity.this, "surfaceCreated", Toast.LENGTH_SHORT).show();
                if (mCameraHelper != null) {

                    mCameraHelper.addSurface(holder.getSurface(), false);

                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Toast.makeText(BasicPreviewActivity.this, "surfaceChanged", Toast.LENGTH_SHORT).show();
                //
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(holder.getSurface());
                }
            }
        });

        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        btnOpenCamera.setOnClickListener(this);
        Button btnCloseCamera = findViewById(R.id.btnCloseCamera);
        btnCloseCamera.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        initCameraHelper();
        registerUSBReceiver();
        getUSBDeviceList();
        Toast.makeText(this, "onStart", Toast.LENGTH_SHORT).show();
    }

    private void getUSBDeviceList() {
        if (manager != null) {
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            for (UsbDevice usbDevice : deviceList.values()) {
                device = usbDevice;
                manager.requestPermission(device, mPermissionIntent);
            }
            if (device != null) {
                boolean hasPermision = manager.hasPermission(device);
                Log.d(TAG, "onCreate:  " + device + "  *******************---" + hasPermision);
                if (hasPermision) {
                    selectDevice(device);
                }
                UsbDeviceConnection connection = manager.openDevice(device);

                if (connection == null) {
                    return;
                }
            }
        }
    }

    private void registerUSBReceiver() {
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (Build.VERSION.SDK_INT >= 34) {
                ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_VISIBLE_TO_INSTANT_APPS);
            } else {
                registerReceiver(usbReceiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);

            }
        } else {
            registerReceiver(usbReceiver, filter);
        }
    }

    private UsbDevice mUsbDevice;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Toast.makeText(this, "onNewIntent", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onNewIntent: ------------------------------------");
        if (Objects.equals(intent.getAction(), UsbManager.ACTION_USB_DEVICE_ATTACHED)) {

            mUsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            selectDevice(mUsbDevice);

        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        Toast.makeText(this, "onStop", Toast.LENGTH_SHORT).show();
        clearCameraHelper();
        unregisterReceiver(usbReceiver);
    }


    private void clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:");
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void selectDevice(final UsbDevice device) {

        if (device != null) {
            mCameraHelper.selectDevice(device);
            if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.getDeviceName());
        }
    }


    /*
    UsbDevice[mName=/dev/bus/usb/002/002,mVendorId=7119,mProductId=11473,mClass=239,mSubclass=2,mProtocol=1,mManufacturerName=5M Camera,mProductName=5M Camera,mVersion=56.46,mSerialNumberReader=android.hardware.usb.IUsbSerialReader$Stub$Proxy@98afe75, mHasAudioPlayback=false, mHasAudioCapture=false, mHasMidi=false, mHasVideoCapture=true, mHasVideoPlayback=true, mConfigurations=[

    * */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnOpenCamera) {
            // select a uvc device
            if (mCameraHelper != null) {
                final List<UsbDevice> list = mCameraHelper.getDeviceList();
                if (list != null && list.size() > 0) {

                    Log.d(TAG, "onClick: " + list.get(0));
                    mCameraHelper.selectDevice(list.get(0));
                    manager.requestPermission(list.get(0), mPermissionIntent);
                }
            }
        } else if (v.getId() == R.id.btnCloseCamera) {
            // close camera
            if (mCameraHelper != null) {
                mCameraHelper.closeCamera();
            }
        }
    }

    public void setAllControl() {
        if (mCameraHelper != null) {
            Toast.makeText(this, "autofocus true", Toast.LENGTH_SHORT).show();
            UVCControl uvcControl = mCameraHelper.getUVCControl();
            uvcControl.setFocusAuto(true);
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.d("xxxTAG", "onPreviewFrame: ");
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        Log.d("xxxTAG", "onSurfaceCreated: ");
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {
        Log.d("xxxTAG", "onSurfaceChanged: ");
    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        Log.d("xxxTAG", "onSurfaceDestroy: ");
    }
}