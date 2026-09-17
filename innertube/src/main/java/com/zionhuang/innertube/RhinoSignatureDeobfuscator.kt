package com.zionhuang.innertube

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.util.logging.Logger
import java.util.regex.Pattern

/**
 * Robust YouTube signature deobfuscator using Mozilla Rhino JS engine.
 * Bypasses brittle regex patterns in NewPipeExtractor by dynamically finding
 * the deobfuscation call site in base.js and injecting a named wrapper function.
 * The evaluated Rhino scope is cached per player JS hash (once per player version).
 */
object RhinoSignatureDeobfuscator {

    private val log: Logger = Logger.getLogger("RhinoSigDeobfuscator")

    // Patterns to locate the signature deobfuscation call site in base.js
    private val CALL_PATTERNS = listOf(
        // Current (2025+): outerFn(num, num, innerFn(num, num, b.s))
        Pattern.compile("""([a-zA-Z0-9_$]+)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*([a-zA-Z0-9_$]+)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\w+)\.s\s*\)\s*\)"""),
        // Older: m=fn(decodeURIComponent(h.s))
        Pattern.compile("""\bm=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        // Another: b&&(b=fn(decodeURIComponent(b)))
        Pattern.compile("""\b(?:[a-zA-Z0-9_$]+)&&\((?:[a-zA-Z0-9_$]+)=([a-zA-Z0-9_$]{2,})\(decodeURIComponent\((?:[a-zA-Z0-9_$]+)\)\)\)"""),
    )

    private val BROWSER_STUBS = """
        var window = this;
        var self = this;
        var document = {
            createElement: function(tag) { return { getContext: function() { return null; }, style: {} }; },
            getElementsByTagName: function() { return []; },
            documentElement: { style: {} },
            head: { appendChild: function() {} }
        };
        var navigator = { userAgent: 'Mozilla/5.0', cookieEnabled: true, platform: 'Win32' };
        var location = { hostname: 'music.youtube.com', href: 'https://music.youtube.com/', protocol: 'https:' };
        function XMLHttpRequest() {
            this.open = function() {}; this.send = function() {}; this.setRequestHeader = function() {};
        }
        function Image() {}
        var performance = { now: function() { return Date.now(); } };
        var console = { log: function() {}, warn: function() {}, error: function() {}, debug: function() {} };
    """.trimIndent()

    private data class CachedScope(val scope: Scriptable, val jsCodeHash: Int)

    @Volatile private var cache: CachedScope? = null

    /**
     * Deobfuscates a YouTube stream signature using Rhino JavaScript engine.
     * Falls back by rethrowing if deobfuscation fails.
     */
    @Synchronized
    fun deobfuscate(videoId: String, obfuscatedSignature: String): String {
        val jsCode = getPlayerJs(videoId)
        val scope = getOrCreateScope(jsCode)
        val escapedSig = obfuscatedSignature
            .replace("""\""", """\\""")
            .replace("'", "\\'")
        val cx = Context.enter()
        cx.optimizationLevel = -1
        return try {
            val result = cx.evaluateString(
                scope, "_yt_player.__deobfuscate('$escapedSig')", "deobf", 1, null
            )
            result.toString().also {
                log.fine("[$videoId] Deobfuscated sig (len=${it.length})")
            }
        } finally {
            Context.exit()
        }
    }

    private fun getPlayerJs(videoId: String): String {
        val cls = YoutubeJavaScriptPlayerManager::class.java
        val extractMethod = cls.getDeclaredMethod("extractJavaScriptCodeIfNeeded", String::class.java)
        extractMethod.isAccessible = true
        extractMethod.invoke(null, videoId)
        val codeField = cls.getDeclaredField("cachedJavaScriptPlayerCode")
        codeField.isAccessible = true
        return (codeField.get(null) as? String)
            ?: throw IllegalStateException("NewPipeExtractor did not cache the player JS")
    }

    private fun getOrCreateScope(jsCode: String): Scriptable {
        val hash = jsCode.hashCode()
        cache?.let { if (it.jsCodeHash == hash) return it.scope }

        log.fine("Building new Rhino scope (jsCode.length=${jsCode.length})")
        val t0 = System.currentTimeMillis()
        val cx = Context.enter()
        cx.optimizationLevel = -1
        return try {
            val scope = cx.initStandardObjects()
            val hookCode = buildHookCode(jsCode)
                ?: throw IllegalStateException("Could not locate sig deobfuscation call in base.js")

            // Inject hook inside the _yt_player IIFE before its closing bracket
            val closingIife = "})(_yt_player);"
            val insertIdx = jsCode.lastIndexOf(closingIife)
            val modifiedJs = if (insertIdx >= 0) {
                jsCode.substring(0, insertIdx) + hookCode + jsCode.substring(insertIdx)
            } else {
                log.warning("Could not find IIFE closing bracket, appending hook at end")
                jsCode + "\n" + hookCode
            }

            cx.evaluateString(scope, BROWSER_STUBS, "stubs", 1, null)
            cx.evaluateString(scope, modifiedJs, "base.js", 1, null)
            log.fine("Rhino scope ready in ${System.currentTimeMillis() - t0}ms")
            scope.also { cache = CachedScope(it, hash) }
        } finally {
            Context.exit()
        }
    }

    private fun buildHookCode(jsCode: String): String? {
        for (pattern in CALL_PATTERNS) {
            val matcher = pattern.matcher(jsCode)
            if (!matcher.find()) continue
            val callExpr = matcher.group(0) ?: continue
            log.fine("Found sig call: $callExpr")
            // Find the variable name with .s (the obfuscated sig parameter)
            val varMatcher = Pattern.compile("""(\w+)\.s\s*\)""").matcher(callExpr)
            val sigVar = if (varMatcher.find()) varMatcher.group(1) else null
            val deobfCall = when {
                sigVar != null -> callExpr.replace("$sigVar.s", "s")
                else -> callExpr.replace(Regex("""\w+\.s"""), "s")
            }
            return ";g.__deobfuscate = function(s) { return $deobfCall; };"
        }
        return null
    }

    /** Clears the cached scope, forcing re-evaluation on the next deobfuscate call. */
    fun clearCache() { cache = null }
}