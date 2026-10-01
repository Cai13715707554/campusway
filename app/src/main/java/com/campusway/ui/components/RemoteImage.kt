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
 * 这里只需要"下载一张图 + 显示"这一件事，写几十行就够，
 * 不必为此引入一个依赖（也与工程"依赖尽量少、离线可构建"的取向一致）。
 *
 * **不做磁盘缓存**（需求要求退出软件重进要重新加载）：
 * 只在内存里缓存当前进程内的那一张，进程结束即失效，
 * 下次启动会重新请求，所以每次进来看到的都是新图。
 */
@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    minHeight: Int = 180,
    /** 用来触发"换一张"：值变化就重新下载。 */
    reloadKey: Int = 0,
    fallback: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, reloadKey) {
        failed = false
        bitmap = null
        val loaded = RemoteImageLoader.loadFresh(context, url)
        if (loaded != null) {
            bitmap = loaded
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
                text = "正在加载图片…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 远程图片的加载。
 *
 * 只在内存缓存当前进程的一张图；**不写磁盘**，
 * 这样退出软件重进时一定会重新请求（需求要求"重进要刷新"）。
 */
object RemoteImageLoader {

    private const val TIMEOUT_MS = 10_000

    /** 进程内缓存：同一次启动里切来切去不重复下载。 */
    private val memory = mutableMapOf<String, Bitmap>()

    /** 老版本写过磁盘缓存，清掉，避免"重进还是旧图"。 */
    private fun clearLegacyDiskCache(context: Context) {
        runCatching { File(context.cacheDir, "remote_images").deleteRecursively() }
    }

    /**
     * 强制重新下载（用于"换一张"以及每次进入页面）。
     */
    suspend fun loadFresh(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        clearLegacyDiskCache(context)
        memory.remove(url)
        val bytes = runCatching { httpGet(url) }.getOrNull() ?: return@withContext null
        val bitmap = decode(bytes) ?: return@withContext null
        memory[url] = bitmap
        bitmap
    }

    /** 只读内存缓存，不发起请求。 */
    fun fromMemory(url: String): Bitmap? = memory[url]

    private fun decode(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private fun httpGet(url: String): ByteArray =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // 带上标识便于服务端区分；加时间戳参数避免中间层缓存同一张图
            setRequestProperty("User-Agent", "CampusWay/0.3 (Android)")
            setRequestProperty("Cache-Control", "no-cache")
            try {
                val code = responseCode
                if (code !in 200..299) error("HTTP $code")
                inputStream.use { it.readBytes() }
            } finally {
                disconnect()
            }
        }
}
