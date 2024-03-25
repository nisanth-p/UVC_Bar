/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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


import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.advantech.uvc.R;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


public class GoogleTextVision extends Application {
    private static final String CLOUD_VISION_API_KEY = "AIzaSyDu6y_VWbtdKt_ALvYxZ2tQcQP5-pVH1ds";
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final int MAX_LABEL_RESULTS = 10;
    private static final int MAX_TEXT_RESULTS=10;
    private static final String TAG = GoogleTextVision.class.getSimpleName();

    private Context context;

    static TextToSpeech textToSpeech;
    public GoogleTextVision(Context context){
        this.context=context;
    }

    void initTTS() {
        textToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.ENGLISH);
                }

                if (status == TextToSpeech.SUCCESS) {
                    Log.d("TTS", "Initialization success");
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                        }

                        @Override
                        public void onDone(String utteranceId) {
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.d(TAG, "onError: voice = " + utteranceId);
                        }
                    });
                }
            }
        });
    }


    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        try {
            HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            VisionRequestInitializer requestInitializer = new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                /**
                 * We override this so we can inject important identifying fields into the HTTP
                 * headers. This enables use of a restricted cloud platform API key.
                 */
                @Override
                protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                        throws IOException {
                    super.initializeVisionRequest(visionRequest);

                    String packageName = "com.advantech.uvc";
                    visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                    String sig = PackageManagerUtils.getSignature(context.getPackageManager(), packageName);

                    visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                }
            };

            Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
            builder.setVisionRequestInitializer(requestInitializer);

            Vision vision = builder.build();

            BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
            batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>()
            {
                {
                AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                // Add the image
                Image base64EncodedImage = new Image();
                // Convert the bitmap to a JPEG
                // Just in case it's a format that Android understands but Cloud Vision
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                byte[] imageBytes = byteArrayOutputStream.toByteArray();

                // Base64 encode the JPEG
                base64EncodedImage.encodeContent(imageBytes);
                annotateImageRequest.setImage(base64EncodedImage);

                // add the features we want
                annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                    Feature labelDetection = new Feature();
                    labelDetection.setType("LABEL_DETECTION");
                    labelDetection.setMaxResults(MAX_LABEL_RESULTS);
                    add(labelDetection);
                }});

                // Add the list of one thing to the request
                add(annotateImageRequest);
            }});

            Vision.Images.Annotate annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);
            // Due to a bug: requests to Vision API containing large images fail when GZipped.
            annotateRequest.setDisableGZipContent(true);
            Log.d(TAG, "created Cloud Vision request object, sending request"+annotateRequest);
            return annotateRequest;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }


    private Vision.Images.Annotate prepareAnnotationRequest1(Bitmap bitmap) throws IOException {
        try {
            HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            VisionRequestInitializer requestInitializer = new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                /**
                 * We override this so we can inject important identifying fields into the HTTP
                 * headers. This enables use of a restricted cloud platform API key.
                 */
                @Override
                protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                        throws IOException {
                    super.initializeVisionRequest(visionRequest);

                    String packageName = "com.advantech.uvc";
                    visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                    String sig = PackageManagerUtils.getSignature(context.getPackageManager(), packageName);

                    visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                }
            };

            Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
            builder.setVisionRequestInitializer(requestInitializer);

            Vision vision = builder.build();

            BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
            batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {
                {
                    AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                    // Add the image
                    Image base64EncodedImage = new Image();
                    // Convert the bitmap to a JPEG
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                    byte[] imageBytes = byteArrayOutputStream.toByteArray();

                    // Base64 encode the JPEG
                    base64EncodedImage.encodeContent(imageBytes);
                    annotateImageRequest.setImage(base64EncodedImage);

                    // Set feature for text detection
                    Feature textDetection = new Feature();
                    textDetection.setType("TEXT_DETECTION");
                    annotateImageRequest.setFeatures(Collections.singletonList(textDetection));

                    // Add the request to the batch
                    add(annotateImageRequest);
                }
            });

            Vision.Images.Annotate annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);
            // Due to a bug: requests to Vision API containing large images fail when GZipped.
            annotateRequest.setDisableGZipContent(true);
            Log.d(TAG, "created Cloud Vision request object, sending request"+annotateRequest);
            return annotateRequest;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }



//    private static class LableDetectionTask extends AsyncTask<Object, Void, String> {
//        private final WeakReference<GoogleTextVision> mActivityWeakReference;
//        private Vision.Images.Annotate mRequest;
//
//        LableDetectionTask(GoogleTextVision activity, Vision.Images.Annotate annotate) {
//            mActivityWeakReference = new WeakReference<>(activity);
//            if (annotate!=null)
//            {
//                mRequest = annotate;
//            }else
//                Toast.makeText(activity, "Annotation is null", Toast.LENGTH_SHORT).show();
//
//        }
//
//        @Override
//        protected String doInBackground(Object... params) {
//            try {
//                Log.d(TAG, "created Cloud Vision request object, sending request");
//                BatchAnnotateImagesResponse response = mRequest.execute();
//                return convertResponseToString(response);
//
//            } catch (GoogleJsonResponseException e) {
//                Log.d(TAG, "failed to make API request because " + e.getContent());
//            } catch (IOException e) {
//                Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
//            }
//            return "Cloud Vision API request failed. Check logs for details.";
//        }
//
//        protected void onPostExecute(String result) {
//            GoogleTextVision activity = mActivityWeakReference.get();
//            if (this!=null && !this.isCancelled()){
//                Log.e(TAG, "onPostExecute: "+result.toString() );
//                Toast.makeText(activity.context, result.toString(), Toast.LENGTH_SHORT).show();
//            }
//        }
//    }




    String getRequest(Vision.Images.Annotate annotate){
        try {
            initTTS();
            Vision.Images.Annotate mRequest=annotate;
            Log.d(TAG, "created Cloud Vision request object, sending request");
            BatchAnnotateImagesResponse response = mRequest.execute();
            return convertResponseToString(response);
        } catch (GoogleJsonResponseException e) {
            Log.d(TAG, "failed to make API request because " + e.getContent());
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
        }

        return null;
    }


    public void callCloudVision(ImageView imageView, final Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
        try {
              getRequest(prepareAnnotationRequest(bitmap));
//            getRequest(prepareTextExtractionRequest(bitmap));
//            AsyncTask<Object, Void, String> labelDetectionTask = new LableDetectionTask(this, prepareAnnotationRequest(bitmap));
//            labelDetectionTask.execute();
        } catch (IOException e) {
            Log.d("_exception1", "failed to make API request because of other IOException " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    private static String convertResponseToString(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder("I found these things:\n\n");
        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        if (labels != null) {
            for (EntityAnnotation label : labels) {
                message.append(String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription()));
                message.append("\n");
            }
        } else {
            message.append("nothing");
        }
        Log.e("_result", "convertResponseToString: "+message.toString() );
        textToSpeech.speak(message.toString(),TextToSpeech.QUEUE_FLUSH,null);
        return message.toString();
    }

}
