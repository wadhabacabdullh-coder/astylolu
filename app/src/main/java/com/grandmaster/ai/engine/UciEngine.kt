package com.grandmaster.ai.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import kotlin.concurrent.thread

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Talks to a UCI chess engine running as a child process.
 *
 * All communication is serialised with a [Mutex]: only one search or command
 * sequence talks to the engine at a time, so every line read belongs to the
 * operation that currently holds the lock. All blocking I/O runs on
 * [Dispatchers.IO]; nothing here ever touches the main thread.
 */
class UciEngine(
    private val command: List<String>,
    private val workingDir: File? = null,
    private val environment: Map<String, String> = emptyMap(),
) {
    private var process: Process? = null
    private var writer: Writer? = null
    private val lines = Channel<String>(Channel.UNLIMITED)
    private val mutex = Mutex()
    private val appliedOptions = HashMap<String, String>()

    var engineName: String = "UCI engine"
        private set
    val availableOptions = LinkedHashMap<String, String>()

    val isAlive: Boolean get() = process?.isAlive == true

    /** Starts the process and performs the UCI handshake. */
    suspend fun start(timeoutMs: Long = 15_000) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pb = ProcessBuilder(command).redirectErrorStream(true)
            workingDir?.let { pb.directory(it) }
            pb.environment().putAll(environment)
            val p = try {
                pb.start()
            } catch (e: Exception) {
                throw EngineException("Could not start engine: ${e.message}", e)
            }
            process = p
            writer = OutputStreamWriter(p.outputStream, Charsets.US_ASCII)
            thread(name = "uci-reader", isDaemon = true) {
                try {
                    BufferedReader(InputStreamReader(p.inputStream, Charsets.US_ASCII)).use { r ->
                        while (true) {
                            val l = r.readLine() ?: break
                            lines.trySend(l)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    lines.close()
                }
            }
            sendCmd("uci")
            try {
                withTimeout(timeoutMs) {
                    while (true) {
                        val l = lines.receive()
                        when {
                            l.startsWith("id name ") -> engineName = l.removePrefix("id name ").trim()
                            l.startsWith("option name ") -> {
                                val rest = l.removePrefix("option name ")
                                val idx = rest.indexOf(" type ")
                                if (idx > 0) availableOptions[rest.substring(0, idx)] = rest.substring(idx + 6)
                            }
                            l == "uciok" -> break
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                throw EngineException("Engine exited during start-up", e)
            } catch (e: Exception) {
                destroyLocked()
                throw EngineException("Engine did not answer the UCI handshake", e)
            }
        }
    }

    private fun sendCmd(cmd: String) {
        val w = writer ?: throw EngineException("Engine is not running")
        try {
            w.write(cmd)
            w.write("\n")
            w.flush()
        } catch (e: Exception) {
            throw EngineException("Engine pipe closed", e)
        }
    }

    private suspend fun readyLocked(timeoutMs: Long = 30_000) {
        sendCmd("isready")
        try {
            withTimeout(timeoutMs) {
                while (lines.receive() != "readyok") { /* discard */ }
            }
        } catch (e: ClosedReceiveChannelException) {
            throw EngineException("Engine process exited", e)
        }
    }

    private suspend fun applyOptionsLocked(options: Map<String, String>) {
        var changed = false
        for ((name, value) in options) {
            if (name !in availableOptions) continue
            if (appliedOptions[name] == value) continue
            sendCmd("setoption name $name value $value")
            appliedOptions[name] = value
            changed = true
        }
        if (changed) readyLocked()
    }

    /** Sets options outside of a search (e.g. EvalFile, Threads, Hash). */
    suspend fun setOptions(options: Map<String, String>) = withContext(Dispatchers.IO) {
        mutex.withLock { applyOptionsLocked(options) }
    }

    suspend fun newGame() = withContext(Dispatchers.IO) {
        mutex.withLock {
            sendCmd("ucinewgame")
            readyLocked()
        }
    }

    /**
     * Runs a search and streams snapshots. Cancelling the collector stops the
     * search cleanly ("stop" + wait for "bestmove"), keeping the engine in sync.
     */
    fun search(
        request: SearchRequest,
        globalOptions: Map<String, String> = emptyMap(),
        throttleMs: Long = 150,
    ): Flow<SearchSnapshot> = channelFlow {
        mutex.withLock {
            val opts = LinkedHashMap(globalOptions)
            opts["MultiPV"] = request.multiPv.coerceAtLeast(1).toString()
            val s = request.strength
            if (s.limitElo != null) {
                opts["UCI_LimitStrength"] = "true"
                opts["UCI_Elo"] = s.limitElo.toString()
                opts["Skill Level"] = "20"
            } else {
                opts["UCI_LimitStrength"] = "false"
                opts["Skill Level"] = s.skillLevel.coerceIn(0, 20).toString()
            }
            applyOptionsLocked(opts)

            val pos = buildString {
                append("position fen ").append(request.fen)
                if (request.moves.isNotEmpty()) append(" moves ").append(request.moves.joinToString(" "))
            }
            sendCmd(pos)
            val go = buildString {
                append("go")
                val l = request.limits
                if (l.infinite) append(" infinite")
                l.depth?.let { append(" depth ").append(it) }
                l.moveTimeMs?.let { append(" movetime ").append(it) }
                l.nodes?.let { append(" nodes ").append(it) }
                if (request.searchMoves.isNotEmpty()) append(" searchmoves ").append(request.searchMoves.joinToString(" "))
            }
            sendCmd(go)

            val byPv = sortedMapOf<Int, EngineLine>()
            var lastEmit = 0L
            var finished = false
            try {
                while (true) {
                    val l = try {
                        lines.receive()
                    } catch (e: ClosedReceiveChannelException) {
                        throw EngineException("Engine process exited during search", e)
                    }
                    val info = UciParser.parseInfo(l)
                    if (info != null) {
                        val prev = byPv[info.multipv]
                        // Prefer exact scores; accept a bound only when nothing exact is known yet.
                        if (info.bound == Bound.EXACT || prev == null || prev.depth < info.depth) {
                            byPv[info.multipv] = info
                        }
                        // Drop lines beyond the requested MultiPV (can happen when fewer moves exist).
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= throttleMs) {
                            lastEmit = now
                            send(SearchSnapshot(request, consistentLines(byPv)))
                        }
                        continue
                    }
                    val bm = UciParser.parseBestMove(l)
                    if (bm != null) {
                        finished = true
                        val best = bm.first.takeIf { it != "(none)" }
                        send(SearchSnapshot(request, consistentLines(byPv), best, bm.second, finished = true))
                        break
                    }
                }
            } finally {
                if (!finished && process?.isAlive == true) {
                    withContext(NonCancellable) {
                        runCatching {
                            sendCmd("stop")
                            withTimeoutOrNull(10_000) {
                                while (true) {
                                    val l = lines.receive()
                                    if (l.startsWith("bestmove")) break
                                }
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Lines sorted by multipv; lines from a shallower iteration than PV 1 are kept (they are still valid). */
    private fun consistentLines(byPv: Map<Int, EngineLine>): List<EngineLine> =
        byPv.values.sortedBy { it.multipv }.toList()

    fun destroy() {
        runCatching { writer?.write("quit\n"); writer?.flush() }
        destroyLocked()
    }

    private fun destroyLocked() {
        val p = process ?: return
        runCatching { p.outputStream.close() }
        if (!p.waitForSafe(500)) p.destroyForcibly()
        process = null
        writer = null
    }

    private fun Process.waitForSafe(ms: Long): Boolean = try {
        waitFor(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        false
    }

    companion object {
        /** Creates a scope-independent engine; convenience for tests. */
        fun forBinary(path: String, workingDir: File? = null) = UciEngine(listOf(path), workingDir)
    }
}
