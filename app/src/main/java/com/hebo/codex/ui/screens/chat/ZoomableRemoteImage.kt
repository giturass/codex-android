package com.hebo.codex.ui.screens.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.DynamicZoomSpec
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okio.source

@Composable
internal fun ZoomableRemoteImage(
    image: RemotePreview.Image,
    contentDescription: String,
    onZoomedChange: (Boolean) -> Unit,
) {
    val source = remember(image) {
        image.encodedBytes?.let { bytes ->
            SubSamplingImageSource.rawSource(
                source = { bytes.inputStream().source() },
                preview = image.preview,
            )
        } ?: BitmapImageSource(image.preview)
    }
    ZoomableSubSamplingImage(
        source = source,
        contentDescription = contentDescription,
        onZoomedChange = onZoomedChange,
    )
}

@Composable
internal fun ZoomableRemoteSvg(
    svg: RemotePreview.Svg,
    contentDescription: String,
    onZoomedChange: (Boolean) -> Unit,
) {
    val source = remember(svg) {
        SvgImageSource(
            picture = svg.picture,
            imageSize = svg.imageSize,
            preview = svg.preview,
        )
    }
    ZoomableSubSamplingImage(
        source = source,
        contentDescription = contentDescription,
        onZoomedChange = onZoomedChange,
    )
}

@Composable
private fun ZoomableSubSamplingImage(
    source: SubSamplingImageSource,
    contentDescription: String,
    onZoomedChange: (Boolean) -> Unit,
) {
    val zoomableState = rememberZoomableState(zoomSpec = ORIGINAL_DETAIL_ZOOM_SPEC)
    val imageState = rememberSubSamplingImageState(
        imageSource = source,
        zoomableState = zoomableState,
    )
    val currentOnZoomedChange by rememberUpdatedState(onZoomedChange)
    val isZoomed = (zoomableState.zoomFraction ?: 0f) > ZOOMED_FRACTION_THRESHOLD

    LaunchedEffect(isZoomed) {
        currentOnZoomedChange(isZoomed)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnZoomedChange(false) }
    }

    SubSamplingImage(
        state = imageState,
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxSize()
            .zoomable(
                state = zoomableState,
                onDoubleClick = DOUBLE_CLICK_ZOOM,
            ),
    )
}

private class BitmapImageSource(
    override val preview: ImageBitmap,
) : SubSamplingImageSource {
    private val bitmap = preview.asAndroidBitmap()

    override suspend fun decoder(): ImageRegionDecoder.Factory =
        ImageRegionDecoder.Factory { BitmapImageRegionDecoder(bitmap) }
}

private class BitmapImageRegionDecoder(
    private val source: Bitmap,
) : ImageRegionDecoder {
    override val imageSize = IntSize(source.width, source.height)

    override suspend fun decodeRegion(
        region: IntRect,
        sampleSize: Int,
    ): ImageRegionDecoder.DecodeResult = withContext(Dispatchers.Default) {
        val safeSampleSize = sampleSize.coerceAtLeast(1)
        val output = createBitmap(
            width = region.width.ceilDiv(safeSampleSize),
            height = region.height.ceilDiv(safeSampleSize),
        )
        Canvas(output).drawBitmap(
            source,
            Rect(region.left, region.top, region.right, region.bottom),
            Rect(0, 0, output.width, output.height),
            BITMAP_PAINT,
        )
        output.prepareToDraw()
        ImageRegionDecoder.DecodeResult(
            painter = BitmapPainter(output.asImageBitmap()),
            hasUltraHdrContent = false,
        )
    }
}

private class SvgImageSource(
    private val picture: Picture,
    private val imageSize: IntSize,
    override val preview: ImageBitmap,
) : SubSamplingImageSource {
    override suspend fun decoder(): ImageRegionDecoder.Factory =
        ImageRegionDecoder.Factory { SvgImageRegionDecoder(picture, imageSize) }
}

private class SvgImageRegionDecoder(
    private val picture: Picture,
    override val imageSize: IntSize,
) : ImageRegionDecoder {
    override suspend fun decodeRegion(
        region: IntRect,
        sampleSize: Int,
    ): ImageRegionDecoder.DecodeResult = withContext(Dispatchers.Default) {
        val safeSampleSize = sampleSize.coerceAtLeast(1)
        val output = createBitmap(
            width = region.width.ceilDiv(safeSampleSize),
            height = region.height.ceilDiv(safeSampleSize),
        )
        val scale = 1f / safeSampleSize
        val destination = RectF(
            -region.left * scale,
            -region.top * scale,
            (imageSize.width - region.left) * scale,
            (imageSize.height - region.top) * scale,
        )
        synchronized(picture) {
            Canvas(output).drawPicture(picture, destination)
        }
        output.prepareToDraw()
        ImageRegionDecoder.DecodeResult(
            painter = BitmapPainter(output.asImageBitmap()),
            hasUltraHdrContent = false,
        )
    }
}

private fun Int.ceilDiv(divisor: Int): Int = (this + divisor - 1) / divisor

private val ORIGINAL_DETAIL_ZOOM_SPEC = DynamicZoomSpec { inputs ->
    val sourceLongestEdge = maxOf(
        inputs.unscaledContentSize.width,
        inputs.unscaledContentSize.height,
    )
    val fittedLongestEdge = maxOf(
        inputs.scaledContentBounds.width,
        inputs.scaledContentBounds.height,
    )
    val fittedScale = fittedLongestEdge / sourceLongestEdge
    ZoomSpec(maxZoomFactor = maxOf(1f, fittedScale * MAX_ZOOM_FROM_FITTED_SIZE))
}

private val DOUBLE_CLICK_ZOOM = DoubleClickToZoomListener { state, centroid: Offset ->
    if ((state.zoomFraction ?: 0f) > ZOOMED_FRACTION_THRESHOLD) {
        state.resetZoom()
    } else {
        state.zoomBy(DOUBLE_CLICK_ZOOM_FACTOR, centroid)
    }
}

private val BITMAP_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
private const val MAX_ZOOM_FROM_FITTED_SIZE = 4f
private const val DOUBLE_CLICK_ZOOM_FACTOR = 2.5f
private const val ZOOMED_FRACTION_THRESHOLD = 0.001f
