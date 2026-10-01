package com.campusway.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远程图片，用于"时间表全部不显示"时替换时间表的位置。
 *
 * 为什么不引第三方图片库（Coil 等）：
 * 这里只需要"下载一张图 + 显示 + 缓存"这一件事，写一百来行就够，
 * 不必为此引入一个依赖（也与工程"依赖尽量少、离线可构建"的取向一致）。
 *
 * 缓存策略：内存 → 磁盘（cacheDir）→ 网络。
 * 下载成功后会写入磁盘，所以**联网看过一次之后，之后离线也能显示**。
 */
@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    minHeight: Int = 180,
    fallback: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(RemoteImageLoader.fromMemory(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = RemoteImageLoader.load(context, url)
        if (loaded != null) {
            bitmap = loaded
            failed = false
        } else {
            failed = true
        }
    }

    val current = bitmap
    when {
        current != null -> Image(
            bitmap = current.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
                .fillMaxWidth()
                .height(minHeight.dp),
            contentScale = ContentScale.Fit
        )

        failed -> Column(
            modifier = modifier
                .fillMaxWidth()
                .height(minHeight.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            fallback()
        }

        else -> Column(
            modifier = modifier
                .fillMaxWidth()
                .height(minHeight.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.height(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "正在加载占位图…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 远程图片的加载与缓存。
 */
object RemoteImageLoader {

    private const val TIMEOUT_MS = 10_000

    /** 内存缓存，避免同一张图反复解码。 */
    private val memory = mutableMapOf<String, Bitmap>()

    fun fromMemory(url: String): Bitmap? = memory[url]

    /** 依次尝试：内存 → 磁盘 → 网络。失败返回 null。 */
    suspend fun load(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        memory[url]?.let { return@withContext it }

        val file = cacheFile(context, url)

        // 磁盘缓存（上次联网看过就有）
        if (file.exists()) {
            decode(file.readBytes())?.let {
                memory[url] = it
                return@withContext it
            }
            runCatching { file.delete() }
        }

        // 网络
        val bytes = runCatching { httpGet(url) }.getOrNull() ?: return@withContext null
        val bitmap = decode(bytes) ?: return@withContext null
        memory[url] = bitmap
        runCatching { file.writeBytes(bytes) }
        bitmap
    }

    /** 清掉缓存（设置里的"清除离线地图缓存"会顺带调用）。 */
    fun clear(context: Context) {
        memory.clear()
        runCatching { cacheDir(context).deleteRecursively() }
    }

    private fun cacheDir(context: Context) = File(context.cacheDir, "remote_images")

    private fun cacheFile(context: Context, url: String): File {
        val dir = cacheDir(context).apply { mkdirs() }
        return File(dir, "img_${url.hashCode().toUInt().toString(16)}.bin")
    }

    private fun decode(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private fun httpGet(url: String): ByteArray =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // 这个接口按 UA/参数返回图片，带上标识便于服务端区分
            setRequestProperty("User-Agent", "CampusWay/0.2 (Android)")
            try {
                val code = responseCode
                if (code !in 200..299) error("HTTP $code")
                inputStream.use { it.readBytes() }
            } finally {
                disconnect()
            }
        }
}
