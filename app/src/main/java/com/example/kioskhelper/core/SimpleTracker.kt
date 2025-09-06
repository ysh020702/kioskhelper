package com.example.kioskhelper.core

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min


data class TrkBox(var trackId: Int, var rect: RectF, var score: Float, var age:Int = 0)


class SimpleTracker(private val iouThresh: Float = 0.3f) {
    private var nextId = 1
    private var tracks = mutableListOf<TrkBox>()


    fun update(dets: List<TrkBox>): List<TrkBox> {
        val updated = mutableListOf<TrkBox>()
        val used = BooleanArray(dets.size)
// match by IoU
        for (t in tracks) {
            var bestIou = 0f; var best = -1
            for ((i, d) in dets.withIndex()) if (!used[i]) {
                val iou = iou(t.rect, d.rect)
                if (iou > bestIou) { bestIou = iou; best = i }
            }
            if (bestIou >= iouThresh && best >= 0) {
                val d = dets[best]; used[best] = true
                t.rect = d.rect; t.score = d.score; t.age = 0
                updated += t
            } else { t.age++ }
        }
// new tracks
        dets.forEachIndexed { i, d -> if (!used[i]) updated += TrkBox(nextId++, d.rect, d.score) }
// keep alive
        tracks = updated.filter { it.age < 30 }.toMutableList()
        return tracks
    }


    fun predict(): List<TrkBox> = tracks
    fun reset() { tracks.clear(); nextId = 1 }


    private fun iou(a: RectF, b: RectF): Float {
        val x1 = max(a.left, b.left); val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right); val y2 = min(a.bottom, b.bottom)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val ua = a.width()*a.height() + b.width()*b.height() - inter
        return if (ua>0) inter/ua else 0f
    }
}