package com.advantech.uvc.vcall;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


import com.advantech.uvc.R;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCControl;
import com.serenegiant.widget.AspectRatioSurfaceView;
import com.serenegiant.widget.AspectRatioTextureView;
import com.serenegiant.widget.CameraViewInterface;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.agora.rtc.video.AgoraVideoFrame;
//import io.agora.rtc2.video.AgoraVideoFrame;
import kotlin.Suppress;

public class BasicPreviewActivity extends AppCompatActivity implements View.OnClickListener, Camera.PreviewCallback, CameraViewInterface.Callback {
    private static final String TAG = "xxBasicPreviewAct";

    private static final boolean DEBUG = true;
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;


    private ICameraHelper mCameraHelper;

    public AspectRatioSurfaceView aspectRatioSurfaceView;
    public AspectRatioTextureView mSurfaceViewMain;
    public CameraViewInterface cameraViewInterface;
    public RelativeLayout relativeLayoutHead;

    private static final String ACTION_USB_PERMISSION_BASE = "com.serenegiant.USB_PERMISSION.";
    private final String ACTION_USB_PERMISSION = ACTION_USB_PERMISSION_BASE;
    private PendingIntent mPermissionIntent;
    UsbDevice device = null;
    private UsbDevice mUsbDevice;
    private NV21ToBitmap mNv21ToBitmap;

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

    @Suppress(names = "UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_preview);
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        initCameraHelper();
        initViews();
    }
    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mNv21ToBitmap = new NV21ToBitmap(this);
            mCameraHelper = new CameraHelper(ContextCompat.RECEIVER_NOT_EXPORTED);
            mCameraHelper.setStateCallback(mStateListener);
        }
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
                            aspectRatioSurfaceView.setAspectRatio(width, height);
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
                            } else {
                                Log.e("CameraHelper", "Invalid preview dimensions");
                            }
                        }
                    }, UVCCamera.PIXEL_FORMAT_NV21);

                    mCameraHelper.addSurface(aspectRatioSurfaceView.getHolder().getSurface(), false);


                }

                @Override
                public void onCameraClose(UsbDevice device) {
                    if (DEBUG) Log.v(TAG, "onCameraClose:");

                    if (mCameraHelper != null) {
                        mCameraHelper.removeSurface(aspectRatioSurfaceView.getHolder().getSurface());
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
        aspectRatioSurfaceView = findViewById(R.id.svCameraViewMain);
        relativeLayoutHead = findViewById(R.id.layout_relate_head);
        aspectRatioSurfaceView.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        aspectRatioSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
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