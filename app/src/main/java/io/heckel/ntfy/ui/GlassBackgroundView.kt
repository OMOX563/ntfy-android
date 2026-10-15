package io.heckel.ntfy.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.util.Log
import io.heckel.ntfy.util.isDarkThemeOn
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Page background for the liquid-glass design, see docs/custom-background.md.
 *
 * Responsibilities (kept in one place so all three pages behave identically):
 *  · read the background preference (docs §1: none / atmosphere / custom / solid)
 *  · decode the user image downsampled to max [MAX_DIMENSION_PX] (docs §2)
 *  · cache the decoded background, so switching pages does not re-decode (docs §3)
 *  · blur with [RenderEffect] where available, degrade gracefully everywhere else (docs §2/§3)
 *  · draw the adaptive veil that keeps text on the glass cards readable (docs §2.1)
 *
 * This view is inserted as the first child of the activity layouts, i.e. below the
 * app bar and below the glass cards, so everything on top of it stays readable.
 *
 * Robustness (docs §7): every failure path falls back to the atmosphere background.
 * It must never throw, because it is inflated from XML.
 */
class GlassBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var mode: String? = null // null until the first (asynchronous) load finished
    private var bitmap: Bitmap? = null
    private var solidColor = 0
    private var atmosphere: Drawable? = null
    private var blurRadiusPx = 0f

    // Bumped on every refresh(), used to drop results of loads that are no longer needed
    private var generation = 0

    /**
     * Re-reads the preferences and redraws. Called from the activities' onResume(),
     * see docs/custom-background.md §5.
     */
    fun refresh() {
        val currentGeneration = ++generation
        val appContext = context.applicationContext
        executor.execute {
            val background = loadBackground(appContext)
            post {
                if (currentGeneration == generation) {
                    apply(background)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        generation++
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            Repository.BACKGROUND_MODE_NONE -> canvas.drawColor(ContextCompat.getColor(context, R.color.bg_base))
            Repository.BACKGROUND_MODE_SOLID -> canvas.drawColor(solidColor)
            Repository.BACKGROUND_MODE_CUSTOM -> drawImage(canvas)
            // Atmosphere is the fallback for every error case (docs §7), so this is the default
            else -> drawAtmosphere(canvas)
        }
    }

    private fun drawAtmosphere(canvas: Canvas) {
        if (atmosphere == null) {
            atmosphere = ContextCompat.getDrawable(context, R.drawable.bg_atmosphere)
        }
        val drawable = atmosphere ?: return
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
    }

    private fun drawImage(canvas: Canvas) {
        val image = bitmap ?: return
        if (width <= 0 || height <= 0) {
            return
        }
        // centerCrop, scaled up slightly to hide the transparent edges the blur
        // pulls in at the borders (docs §2)
        val scale = max(width.toFloat() / image.width, height.toFloat() / image.height) * CROP_SCALE
        val scaledWidth = image.width * scale
        val scaledHeight = image.height * scale
        canvas.save()
        canvas.translate((width - scaledWidth) / 2f, (height - scaledHeight) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(image, 0f, 0f, paint)
        canvas.restore()
    }

    private fun apply(background: Background) {
        mode = background.mode
        bitmap = background.bitmap
        solidColor = background.solidColor
        blurRadiusPx = if (background.mode == Repository.BACKGROUND_MODE_CUSTOM) blurRadius() else 0f
        applyBlur()
        invalidate()
    }

    /**
     * Blur is applied with [RenderEffect] (Android 12+), see docs/custom-background.md §2.
     * Older versions and low-RAM devices simply get an unblurred background, see docs §3.
     */
    private fun applyBlur() {
        if (blurRadiusPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isLowRamDevice(context)) {
            setRenderEffect(blurEffect(blurRadiusPx))
        } else {
            setRenderEffect(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun blurEffect(radiusPx: Float): RenderEffect {
        return RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
    }

    private fun blurRadius(): Float {
        val repository = Repository.getInstance(context)
        return (repository.getBackgroundBlur() * resources.displayMetrics.density).roundToInt().toFloat()
    }

    /**
     * Reads the preference and produces everything needed to draw, on a background thread.
     * Never throws; every failure results in the atmosphere background (docs §7).
     */
    private fun loadBackground(context: Context): Background {
        val repository = Repository.getInstance(context)
        return try {
            when (repository.getBackgroundMode()) {
                Repository.BACKGROUND_MODE_NONE -> Background(Repository.BACKGROUND_MODE_NONE)
                Repository.BACKGROUND_MODE_SOLID -> {
                    val color = repository.getBackgroundSolidColor()?.let { parseColor(it) }
                        ?: ContextCompat.getColor(context, R.color.bg_base)
                    Background(Repository.BACKGROUND_MODE_SOLID, solidColor = color)
                }
                Repository.BACKGROUND_MODE_CUSTOM -> loadCustomBackground(context, repository)
                else -> Background(Repository.BACKGROUND_MODE_ATMOSPHERE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load background, falling back to atmosphere", e)
            Background(Repository.BACKGROUND_MODE_ATMOSPHERE)
        }
    }

    private fun loadCustomBackground(context: Context, repository: Repository): Background {
        val uri = repository.getBackgroundImageUri()
        if (uri.isNullOrEmpty()) {
            Log.w(TAG, "Custom background selected, but no image URI is stored, falling back to atmosphere")
            return Background(Repository.BACKGROUND_MODE_ATMOSPHERE)
        }
        val darkTheme = isDarkThemeOn(context)
        val cacheKey = "$uri|${repository.getBackgroundBlur()}|$darkTheme"
        bitmapCache.get(cacheKey)?.let { return Background(Repository.BACKGROUND_MODE_CUSTOM, bitmap = it) }

        val decoded = decodeBackground(context, Uri.parse(uri))
            ?: return Background(Repository.BACKGROUND_MODE_ATMOSPHERE)
        val prepared = applyVeils(context, decoded, darkTheme)
        bitmapCache.put(cacheKey, prepared)
        return Background(Repository.BACKGROUND_MODE_CUSTOM, bitmap = prepared)
    }

    /**
     * Draws the veils onto the decoded image (docs §2 and §2.1):
     *  · dark theme: bg_veil (black 30%) keeps a bright user image from glaring
     *  · always: the adaptive veil, which lifts or dims the image until the text on
     *    the glass cards reaches [TARGET_CONTRAST] against the card surface
     */
    private fun applyVeils(context: Context, image: Bitmap, darkTheme: Boolean): Bitmap {
        val canvas = Canvas(image)
        if (darkTheme) {
            canvas.drawColor(ContextCompat.getColor(context, R.color.bg_veil))
        }
        val veil = adaptiveVeil(
            image,
            ContextCompat.getColor(context, R.color.glass_fill),
            ContextCompat.getColor(context, R.color.ink),
            ContextCompat.getColor(context, R.color.ink_muted)
        )
        if (veil != Color.TRANSPARENT) {
            Log.d(TAG, "Adaptive veil: #${Integer.toHexString(veil)}")
            canvas.drawColor(veil)
        }
        return image
    }

    /**
     * True when the card surface clears **both** contrast targets: [TARGET_CONTRAST]
     * against body text and [TARGET_SECONDARY_CONTRAST] against secondary text.
     * Both must hold — solving on the stricter one alone is not enough, because the
     * two foreground colors sit at different luminances and either can be the binding
     * constraint depending on the background image.
     */
    private fun meetsTargets(cardColor: Int, ink: Int, inkMuted: Int): Boolean {
        return contrastRatio(cardColor, ink) >= TARGET_CONTRAST &&
            contrastRatio(cardColor, inkMuted) >= TARGET_SECONDARY_CONTRAST
    }

    /**
     * Solves for the veil strength that puts the glass card at [TARGET_CONTRAST] against the
     * ink color (docs §2.1). Darkening and lightening are both tried, because depending on the
     * theme only one of them can lift the contrast: in the light theme the ink is dark, so a
     * black image has to be lifted by a white veil, and vice versa in the dark theme.
     *
     * The smallest sufficient strength wins, so the background stays as visible as possible.
     *
     * The veil is solved against **both** [TARGET_CONTRAST] (body text) and
     * [TARGET_SECONDARY_CONTRAST] (secondary text); see [meetsTargets].
     * Solving on `ink` alone left `ink_muted` at ~3.0:1 on a pure-black
     * background — technically short of the 4.5:1 that design-system.md §8
     * requires for secondary text, and unreadable in practice. The extra cost
     * is negligible for ordinary photos (0–2% veil); only extreme images
     * need a strong veil, where a dimmed image is the right trade.
     */
    private fun adaptiveVeil(image: Bitmap, glassFill: Int, ink: Int, inkMuted: Int): Int {
        val backgroundColor = averageColor(image)
        val cardColor = composite(glassFill, backgroundColor)
        if (meetsTargets(cardColor, ink, inkMuted)) {
            return Color.TRANSPARENT // Nothing to do, the image is already readable
        }
        // Black first: a veil over a bright image is the case that actually happens, and
        // in the light theme only a white veil can lift the text at all (docs §2.1)
        for (veilColor in intArrayOf(Color.BLACK, Color.WHITE)) {
            val veil = solveVeil(veilColor, backgroundColor, glassFill, ink, inkMuted)
            if (veil != Color.TRANSPARENT) {
                return veil
            }
        }
        // Neither direction reaches the target, e.g. a mid-gray image in the light theme.
        // Stay transparent rather than washing the image out without meeting the goal.
        Log.d(TAG, "Adaptive veil: no direction reaches ${TARGET_CONTRAST}:1, leaving the image untouched")
        return Color.TRANSPARENT
    }

    /**
     * Binary searches the smallest veil strength that reaches [TARGET_CONTRAST].
     *
     * Contrast moves monotonically with the strength in whichever direction helps, so this is a
     * plain binary search: [low] is known to be too weak, [high] is the weakest strength not yet
     * ruled out. Returns [Color.TRANSPARENT] if even [MAX_VEIL_ALPHA] does not get there.
     */
    private fun solveVeil(veilColor: Int, backgroundColor: Int, glassFill: Int, ink: Int, inkMuted: Int): Int {
        var low = 0f
        var high = MAX_VEIL_ALPHA
        repeat(BINARY_SEARCH_STEPS) {
            val strength = (low + high) / 2f
            val veil = withAlpha(veilColor, (strength * 255f).roundToInt())
            if (meetsTargets(composite(glassFill, composite(veil, backgroundColor)), ink, inkMuted)) {
                high = strength
            } else {
                low = strength
            }
        }
        val veil = withAlpha(veilColor, (high * 255f).roundToInt())
        return if (meetsTargets(composite(glassFill, composite(veil, backgroundColor)), ink, inkMuted)) {
            veil
        } else {
            Color.TRANSPARENT
        }
    }

    /**
     * Average color of the image, sampled from at most [SAMPLE_GRID_SIZE]² pixels (docs §2.1).
     */
    private fun averageColor(image: Bitmap): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        val stepX = max((image.width - 1) / (SAMPLE_GRID_SIZE - 1), 1)
        val stepY = max((image.height - 1) / (SAMPLE_GRID_SIZE - 1), 1)
        var samples = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val pixel = image.getPixel(x, y)
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                samples++
                x += stepX
            }
            y += stepY
        }
        if (samples == 0) {
            return Color.GRAY
        }
        return Color.rgb((red / samples).toInt(), (green / samples).toInt(), (blue / samples).toInt())
    }

    private fun decodeBackground(context: Context, uri: Uri): Bitmap? {
        val cacheFile = File(context.cacheDir, "glass_background_" + uri.toString().hashCode().toString() + DISK_CACHE_SUFFIX)
        try {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                val fromDisk = decodeSampled(cacheFile)
                if (fromDisk != null) {
                    return fromDisk
                }
                Log.w(TAG, "Cached background image is unreadable, re-decoding")
            }
            val decoded = decodeSampled(context, uri)
            if (decoded != null) {
                // No EXIF: BitmapFactory does not decode any, and compressing a bitmap writes none (docs §6)
                FileOutputStream(cacheFile).use { out ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, DISK_CACHE_QUALITY, out)
                }
            }
            return decoded
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode background image, falling back to atmosphere", e)
            return null
        }
    }

    private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            // RGB_565 halves the memory for the formats that have no transparency, see docs §2
            inPreferredConfig = if (hasAlpha(uri)) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            inMutable = true // The veils are drawn into the bitmap, see docs/custom-background.md §2.1
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun hasAlpha(uri: Uri): Boolean {
        return try {
            resolverMimeType(uri) != MIME_JPEG
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine the MIME type of the background image", e)
            true // Assume the worst case and keep the alpha channel
        }
    }

    private fun resolverMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }

    private fun decodeSampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = true // The veils are drawn into the bitmap, see docs/custom-background.md §2.1
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * Largest power of two that still keeps the long edge within [MAX_DIMENSION_PX],
     * so a 4K photo is never decoded at full size (docs §2, docs §7).
     */
    private fun sampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var longEdge = max(width, height)
        while (longEdge / 2 >= MAX_DIMENSION_PX) {
            longEdge /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun parseColor(color: String): Int? {
        return try {
            Color.parseColor(color)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid stored background color: $color")
            null
        }
    }

    private fun isLowRamDevice(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            activityManager?.isLowRamDevice ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine low-RAM state, assuming not low-RAM", e)
            false
        }
    }

    /**
     * Draws [src] (including its alpha) on top of the opaque color [dst].
     */
    private fun composite(src: Int, dst: Int): Int {
        val alpha = Color.alpha(src) / 255f
        val red = (Color.red(src) * alpha + Color.red(dst) * (1f - alpha)).roundToInt()
        val green = (Color.green(src) * alpha + Color.green(dst) * (1f - alpha)).roundToInt()
        val blue = (Color.blue(src) * alpha + Color.blue(dst) * (1f - alpha)).roundToInt()
        return Color.rgb(red, green, blue)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun contrastRatio(color1: Int, color2: Int): Float {
        val luminance1 = relativeLuminance(color1)
        val luminance2 = relativeLuminance(color2)
        val lighter = max(luminance1, luminance2)
        val darker = min(luminance1, luminance2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(color: Int): Float {
        val red = linearize(Color.red(color) / 255f)
        val green = linearize(Color.green(color) / 255f)
        val blue = linearize(Color.blue(color) / 255f)
        return 0.2126f * red + 0.7152f * green + 0.0722f * blue
    }

    private fun linearize(component: Float): Float {
        return if (component <= 0.03928f) {
            component / 12.92f
        } else {
            Math.pow(((component + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
    }

    private class Background(
        val mode: String,
        val bitmap: Bitmap? = null,
        val solidColor: Int = 0
    )

    companion object {
        private const val TAG = "GlassBackgroundView"

        // Long edge of the decoded background image, see docs/custom-background.md §2
        private const val MAX_DIMENSION_PX = 1440

        // centerCrop overshoot that hides the blur edges, see docs/custom-background.md §2
        private const val CROP_SCALE = 1.02f

        // Adaptive veil, see docs/custom-background.md §2.1. 7:1 instead of the AA minimum
        // of 4.5:1, so there is headroom for ink-muted and for imperfect sampling.
        private const val TARGET_CONTRAST = 7f
        private const val MAX_VEIL_ALPHA = 0.85f
        private const val BINARY_SEARCH_STEPS = 16

        // 64 pixels is plenty for an average color, see docs/custom-background.md §2.1
        private const val SAMPLE_GRID_SIZE = 8

        private const val MIME_JPEG = "image/jpeg"
        private const val DISK_CACHE_SUFFIX = ".jpg"
        private const val DISK_CACHE_QUALITY = 85

        // Single thread: the backgrounds are cheap to prepare, and one at a time is enough
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "glass-background")
        }

        // Decoded backgrounds, capped at 1/8 of the available memory (docs/custom-background.md §3)
        private val bitmapCache = object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 1024).toInt() / 8
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }
}
