/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.advantech.uvc.TextDetection;

// [START vision_text_detection]

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class NewVersionGCVTextApi {

    Context context;

    public NewVersionGCVTextApi(Context context) {
        this.context = context;
    }

    public void detectText(TextDetectionActivity activity, ImageView imageView, Bitmap bitmap) {
        try {

            List<AnnotateImageRequest> requests = new ArrayList<>();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            ByteString imgBytes = ByteString.copyFrom(stream.toByteArray());
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
            Log.d("*********TAG", "detectText1: " + request.getImage());
            requests.add(request);
            ImageAnnotatorClient client = ImageAnnotatorClient.create();
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();
            Log.d("*********TAG", "detectText5: ");
            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    System.out.format("Error: %s%n", res.getError().getMessage());
                    Log.d("*********TAG", "detectText2: " + "Error: %s%n" + res.getError().getMessage());
                }
                // For full list of available annotations, see http://g.co/cloud/vision/docs
                for (EntityAnnotation annotation : res.getTextAnnotationsList()) {
                    // Toast.makeText(context,"Text: %s%n"+annotation.getDescription(),Toast.LENGTH_LONG).show();
                    Log.d("*********TAG", "detectText3: " + annotation.getDescription());
                    Log.d("*********TAG", "detectText4: Position =" + annotation.getBoundingPoly());
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("*********TAG", "detectText: Exception =>>  " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

