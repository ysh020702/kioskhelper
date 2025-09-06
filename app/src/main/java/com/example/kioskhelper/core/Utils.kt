package com.example.kioskhelper.core

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.*
import android.view.View
import androidx.camera.view.PreviewView
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


object Utils {
    fun loadModel(ctx: Context, assetPath: String): MappedByteBuffer {
        val afd: AssetFileDescriptor = ctx.assets.openFd(assetPath)
        FileInputStream(afd.fileDescriptor).channel.use { ch ->
            return ch.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }


    fun cropBitmap(src: Bitmap, rect: RectF, padPx:Int = 8): Bitmap {
        val r = Rect((rect.left - padPx).toInt().coerceAtLeast(0),
            (rect.top - padPx).toInt().coerceAtLeast(0),
            (rect.right + padPx).toInt().coerceAtMost(src.width),
            (rect.bottom + padPx).toInt().coerceAtMost(src.height))
        return Bitmap.createBitmap(src, r.left, r.top, maxOf(1,r.width()), maxOf(1,r.height()))
    }


    fun imageToViewRect(imageRect: RectF, previewView: PreviewView): RectF {
    // Simple mapper using current PreviewView bitmap size (works for FILL_CENTER).
        val bmp = previewView.bitmap ?: return imageRect
        val vw = previewView.width.toFloat(); val vh = previewView.height.toFloat()
        val iw = bmp.width.toFloat(); val ih = bmp.height.toFloat()
        val scale = minOf(vw/iw, vh/ih)
        val dx = (vw - iw*scale)/2f; val dy = (vh - ih*scale)/2f
        return RectF(
            imageRect.left*scale+dx,
            imageRect.top*scale+dy,
            imageRect.right*scale+dx,
            imageRect.bottom*scale+dy
        )
    }
}