package com.github.daanbouwman.flightplanner.aircraftart

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders an outline the way the app will — even-odd fill, nose up — so a
 * shape can be looked at before anything is wired to it.
 */
object Preview {

    /**
     * Several outlines on one sheet, each fitted to its own cell and labelled,
     * so a batch can be looked at in one image.
     */
    fun montage(items: List<Pair<String, Outline>>, file: File, columns: Int = 6, cell: Int = 220) {
        val rows = (items.size + columns - 1) / columns
        val image = BufferedImage(columns * cell, rows * (cell + 18), BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        items.forEachIndexed { index, (label, outline) ->
            val ox0 = (index % columns) * cell
            val oy0 = (index / columns) * (cell + 18)
            val margin = cell * 0.06
            val scale = (cell - 2 * margin) / maxOf(outline.spanCm, outline.lengthCm)
            val ox = ox0 + (cell - outline.spanCm * scale) / 2
            val oy = oy0 + (cell - outline.lengthCm * scale) / 2
            val path = Path2D.Double(Path2D.WIND_EVEN_ODD)
            for (ring in outline.rings) {
                path.moveTo(ox + ring[0] * scale, oy + ring[1] * scale)
                for (i in 1 until ring.size / 2) path.lineTo(ox + ring[i * 2] * scale, oy + ring[i * 2 + 1] * scale)
                path.closePath()
            }
            g.color = Color(0x1F, 0x2A, 0x36)
            g.fill(path)
            g.color = Color(0xB0, 0x26, 0x1D)
            g.drawString(label, ox0 + 6, oy0 + cell + 13)
        }
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    /**
     * One region of an outline at high resolution, with a one-metre grid, for
     * looking at a detail: [region] is x0, y0, x1, y1 in metres from the
     * outline's top-left (left wing tip, nose).
     */
    fun zoom(outline: Outline, region: DoubleArray, file: File, size: Int = 900) {
        val (x0, y0, x1, y1) = region.toList()
        val scale = size / maxOf(x1 - x0, y1 - y0) / 100.0
        val w = ((x1 - x0) * 100 * scale).toInt()
        val h = ((y1 - y0) * 100 * scale).toInt()
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.color = Color(0xDD, 0xE3, 0xE8)
        var m = kotlin.math.ceil(x0)
        while (m <= x1) { val px = ((m - x0) * 100 * scale).toInt(); g.drawLine(px, 0, px, h); m += 1.0 }
        m = kotlin.math.ceil(y0)
        while (m <= y1) { val py = ((m - y0) * 100 * scale).toInt(); g.drawLine(0, py, w, py); m += 1.0 }
        val path = Path2D.Double(Path2D.WIND_EVEN_ODD)
        for (ring in outline.rings) {
            path.moveTo((ring[0] - x0 * 100) * scale, (ring[1] - y0 * 100) * scale)
            for (i in 1 until ring.size / 2) path.lineTo((ring[i * 2] - x0 * 100) * scale, (ring[i * 2 + 1] - y0 * 100) * scale)
            path.closePath()
        }
        g.color = Color(0x1F, 0x2A, 0x36, 0xD0)
        g.fill(path)
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    fun png(outline: Outline, file: File, size: Int = 640) {
        val margin = size * 0.04
        val scale = (size - 2 * margin) / maxOf(outline.spanCm, outline.lengthCm)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0xF4, 0xF1, 0xEA)
        g.fillRect(0, 0, size, size)
        val ox = (size - outline.spanCm * scale) / 2
        val oy = (size - outline.lengthCm * scale) / 2
        val path = Path2D.Double(Path2D.WIND_EVEN_ODD)
        for (ring in outline.rings) {
            path.moveTo(ox + ring[0] * scale, oy + ring[1] * scale)
            for (i in 1 until ring.size / 2) path.lineTo(ox + ring[i * 2] * scale, oy + ring[i * 2 + 1] * scale)
            path.closePath()
        }
        g.color = Color(0x1F, 0x2A, 0x36)
        g.fill(path)
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }
}
