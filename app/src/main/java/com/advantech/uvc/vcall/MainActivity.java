package com.advantech.uvc.vcall;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;


import com.advantech.uvc.R;

import com.advantech.uvc.tensorflow.CforYou;
import com.advantech.uvc.tensorflow.TensorFlowActivity;
import com.hjq.permissions.XXPermissions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initListeners();

    }

    private void initListeners() {
        Button btnBasicPreview = findViewById(R.id.btnBasicPreview);
        Button btnObject = findViewById(R.id.btnObject);
        Button btnC4U=findViewById(R.id.btnC4UDemo);
        Button textdetection=findViewById(R.id.textdetection);
        btnC4U.setOnClickListener(this);
        btnBasicPreview.setOnClickListener(this);
        btnObject.setOnClickListener(this);
        textdetection.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        List<String> needPermissions = new ArrayList<>();
        needPermissions.add(Manifest.permission.CAMERA);

        XXPermissions.with(this)
                .permission(needPermissions)
                .request((permissions, all) -> {
                    if (v.getId() == R.id.btnBasicPreview) {
                        startActivity(new Intent(this, AgoraActivity.class));
                        finish();
                    } else if (v.getId() == R.id.btnObject) {
                        startActivity(new Intent(this, TensorFlowActivity.class));
                        finish();
                    } else if (v.getId() == R.id.btnC4UDemo) {
                        Log.d("MainActivity", "btnC4U clicked");
                        startActivity(new Intent(this, CforYou.class));
                        finish();
                    } else if (v.getId()==R.id.textdetection) {
                        startActivity(new Intent(this, com.advantech.uvc.TextDetection.TextDetectionActivity.class));
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
