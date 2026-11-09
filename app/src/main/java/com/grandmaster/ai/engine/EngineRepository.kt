package com.grandmaster.ai.engine

import android.content.Context
import android.util.Log
import com.grandmaster.ai.BuildConfig
import com.grandmaster.ai.data.EngineSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed class EngineStatus {
    data object Idle : EngineStatus()
    data class Installing(val progress: Float) : EngineStatus()
    data object Starting : EngineStatus()
    data class Ready(val info: EngineInfo) : EngineStatus()
    data class Failed(val message: String) : EngineStatus()
}

data class EngineInfo(
    val name: String,
    val binary: String,
    val cpuFeature: String,
    val network: String,
    val threads: Int,
    val hashMb: Int,
)

/**
 * Owns the single Stockfish process used by the whole app. The NNUE network is
 * shipped as an asset and copied (and SHA-256 verified) to internal storage once.
 */
class EngineRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val startMutex = Mutex()
    private var engine: UciEngine? = null
    @Volatile private var settings: EngineSettings = EngineSettings()

    fun updateSettings(s: EngineSettings) {
        settings = s
        val st = _status.value
        if (st is EngineStatus.Ready) {
            _status.value = st.copy(info = st.info.copy(threads = s.threads, hashMb = s.hashMb))
        }
    }

    private fun globalOptions(): Map<String, String> = mapOf(
        "Threads" to settings.threads.toString(),
        "Hash" to settings.hashMb.toString(),
    )

    fun warmUp() {
        scope.launch {
            runCatching { ensureStarted() }
        }
    }

    suspend fun ensureStarted(): UciEngine = startMutex.withLock {
        engine?.takeIf { it.isAlive }?.let { return it }
        engine?.destroy()
        engine = null
        try {
            val net = installNetwork()
            _status.value = EngineStatus.Starting
            val (binary, feature) = selectBinary()
            val e = UciEngine(listOf(binary.absolutePath), workingDir = context.filesDir)
            e.start()
            e.setOptions(mapOf("EvalFile" to net.absolutePath) + globalOptions())
            engine = e
            _status.value = EngineStatus.Ready(
                EngineInfo(
                    name = e.engineName,
                    binary = binary.name,
                    cpuFeature = feature,
                    network = net.name,
                    threads = settings.threads,
                    hashMb = settings.hashMb,
                )
            )
            e
        } catch (t: Throwable) {
            Log.e(TAG, "Engine start failed", t)
            _status.value = EngineStatus.Failed(t.message ?: t.javaClass.simpleName)
            throw if (t is EngineException) t else EngineException(t.message ?: "Engine failed", t)
        }
    }

    /** Streams a search; the engine is started on demand. */
    fun search(request: SearchRequest): Flow<SearchSnapshot> = flow {
        val e = ensureStarted()
        try {
            emitAll(e.search(request, globalOptions()))
        } catch (ex: EngineException) {
            _status.value = EngineStatus.Failed(ex.message ?: "Engine error")
            throw ex
        }
    }

    suspend fun newGame() {
        runCatching { ensureStarted().newGame() }
    }

    fun restart() {
        scope.launch {
            startMutex.withLock {
                engine?.destroy()
                engine = null
                _status.value = EngineStatus.Idle
            }
            runCatching { ensureStarted() }
        }
    }

    fun shutdown() {
        engine?.destroy()
        engine = null
    }

    // ---------------------------------------------------------------- install

    private fun selectBinary(): Pair<File, String> {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val dotprod = File(libDir, "libstockfish_dotprod.so")
        val plain = File(libDir, "libstockfish.so")
        if (dotprod.exists() && cpuSupportsDotProd()) return dotprod to "ARMv8.2 dot-product NEON"
        if (plain.exists()) {
            val feature = when {
                libDir.path.contains("arm64") -> "ARMv8 NEON"
                libDir.path.contains("arm") -> "ARMv7 NEON"
                libDir.path.contains("x86_64") -> "x86-64 SSE4.1/POPCNT"
                else -> "native"
            }
            return plain to feature
        }
        throw EngineException("Stockfish binary not found for this device's CPU (${android.os.Build.SUPPORTED_ABIS.joinToString()})")
    }

    private fun cpuSupportsDotProd(): Boolean = runCatching {
        File("/proc/cpuinfo").readLines().any { line ->
            line.startsWith("Features") && line.split(Regex("\\s+")).contains("asimddp")
        }
    }.getOrDefault(false)

    private suspend fun installNetwork(): File = withContext(Dispatchers.IO) {
        val name = BuildConfig.NNUE_FILE
        val target = File(context.filesDir, name)
        val marker = File(context.filesDir, "$name.sha256")
        if (target.exists() && marker.exists() && marker.readText().trim() == BuildConfig.NNUE_SHA256) return@withContext target

        _status.value = EngineStatus.Installing(0f)
        val tmp = File(context.filesDir, "$name.tmp")
        val md = MessageDigest.getInstance("SHA-256")
        // Total size is only an estimate for progress display (the asset is compressed in the APK).
        val expected = 98_511_183L
        context.assets.open(name).use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var total = 0L
                var lastReport = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    total += n
                    if (total - lastReport > 2_000_000) {
                        lastReport = total
                        _status.value = EngineStatus.Installing((total.toFloat() / expected).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        if (sha != BuildConfig.NNUE_SHA256) {
            tmp.delete()
            throw EngineException("NNUE network checksum mismatch")
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true); tmp.delete()
        }
        marker.writeText(sha)
        target
    }

    private companion object {
        const val TAG = "EngineRepository"
    }
}
