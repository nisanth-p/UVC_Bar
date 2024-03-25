package com.advantech.uvc.TextDetection

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import com.google.android.gms.vision.Frame
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions
import java.util.Locale
import java.util.Random

class FireabaseCloudMlOcr(private val context: Context) {

    lateinit var textToSpeech:TextToSpeech
    fun detectText(bitmap: Bitmap, imageView: ImageView)
    {
        imageView.setImageBitmap(bitmap)
        textToSpeech= TextToSpeech(context){ status->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.ENGLISH)
            }

            if (status == TextToSpeech.SUCCESS) {
                Log.d("TTS", "Initialization success")
                textToSpeech.setOnUtteranceProgressListener(object :
                    UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                    }

                    override fun onDone(utteranceId: String?) {
                    }

                    override fun onError(utteranceId: String?) {
                        Log.d(TAG, "onError: voice = $utteranceId")
                    }

                })
            }

        }

        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val options= FirebaseVisionCloudTextRecognizerOptions.Builder().setLanguageHints(listOf("en")).build()
        val recognize = FirebaseVision.getInstance().getCloudTextRecognizer(options)
        recognize.processImage(image).addOnSuccessListener { firebaseVisionText->
           synchronized(this) { ->
               Log.e("textResult", "detectText: ${firebaseVisionText.toString()}", )
                       var mostRecentUtteranceID = (Random().nextInt() % 9999999).toString() + ""
                       val params = HashMap<String, String>()
                       params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = mostRecentUtteranceID
               Toast.makeText(context, "Output\t:${firebaseVisionText.toString()}", Toast.LENGTH_SHORT).show()
               Thread.sleep(1000)
                      // textToSpeech.speak(firebaseVisionText.toString(), TextToSpeech.QUEUE_FLUSH, params)

               }


        }.addOnFailureListener {e->
            synchronized(this) { ->
                    var mostRecentUtteranceID = (Random().nextInt() % 9999999).toString() + ""
                    val params = HashMap<String,String>()
                    params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = mostRecentUtteranceID
                Toast.makeText(context, "Output\t:${e.message.toString()}", Toast.LENGTH_SHORT).show()
                // textToSpeech.speak(e.message.toString(), TextToSpeech.QUEUE_FLUSH, params)
            }

        }
    }


    fun detectText2(imageBitmap: Bitmap, imageView: ImageView): StringBuilder? {
        try {


            textToSpeech= TextToSpeech(context){ status->
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.ENGLISH)
                }

                if (status == TextToSpeech.SUCCESS) {
                    Log.d("TTS", "Initialization success")
                    textToSpeech.setOnUtteranceProgressListener(object :
                        UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                        }

                        override fun onDone(utteranceId: String?) {
                        }

                        override fun onError(utteranceId: String?) {
                            Log.d(TAG, "onError: voice = $utteranceId")
                        }

                    })
                }

            }



            imageView.setImageBitmap(imageBitmap)
            val textRecognizer = com.google.android.gms.vision.text.TextRecognizer.Builder(context).build()
            if (!textRecognizer.isOperational)
            {
                Log.e(TAG, "Text recognizer is not operational.")
            }
            val frame = Frame.Builder().setBitmap(imageBitmap).build()
            val textBlocks = textRecognizer.detect(frame)
            val extractedText = StringBuilder()
            for (i in 0 until textBlocks.size())
            {
                val textBlock = textBlocks.valueAt(i)
                extractedText.append(textBlock.value)
                extractedText.append("\n")
            }
            textToSpeech.speak(extractedText.toString(),TextToSpeech.QUEUE_FLUSH,null)
            return extractedText
        }catch (e:Exception){
            Log.e(TAG, "detectText2: ${e.message.toString()}", )
        }
        return null
    }

}