package com.advantech.uvc.BarCode

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeDecoder(private val context:Context) {

    fun processImage(bitmap: Bitmap, imageView: ImageView)
    {
        imageView.setImageBitmap(bitmap)
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()//options is initialized with BarcodeScannerOptions,for all formate

        if (bitmap != null)
        {
            val image = InputImage.fromBitmap(bitmap!!, 0)

            val scanner = BarcodeScanning.getClient(options)

            val task: Task<List<Barcode>> =scanner.process(image)

            task.addOnSuccessListener { barcode->
                if (barcode.isNullOrEmpty())
                {
                    Toast.makeText(context, "Barcode empty", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    for (bCode in barcode){
                        Log.e("Barcode_", "processImage: "+bCode.rawValue.toString() )
                        Toast.makeText(context, bCode.rawValue.toString(), Toast.LENGTH_SHORT).show()

                    }
                }
            }.addOnFailureListener { exception->
                Toast.makeText(context, "$exception", Toast.LENGTH_SHORT).show()
            }
        }
        else
        {
            Toast.makeText(context, "Please select photo", Toast.LENGTH_SHORT).show()
        }
    }

}