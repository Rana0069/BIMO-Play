package com.zionhuang.innertube

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.util.logging.Logger
import java.util.regex.Pattern

/**
 * Robust YouTube signature deobfuscator using Mozilla Rhino JS engine.
 *
 * YouTube regularly changes the obfuscated function names in base.js, breaking the
 * static regex patterns in NewPipeExtractor. This class dynamically scans base.js for
 * the signature deobfuscation call site, injects a stable wrapper hook, and evaluates
 * the JS using Rhino to decode any given signature.
 *
 * The Rhino context + scope are kept open and cached per player JS hash, so the
 * expensive JS evaluation only happens once per YouTube player version.
 */
object RhinoSignatureDeobfuscator {

    private val log: Logger = Logger.getLogger("RhinoSigDeobfuscator")

    /** Patterns to locate the sig deobfuscation call site in base.js */
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

    private data class RhinoCache(
        val scope: Scriptable,
        val jsCodeHash: Int,
    )

    @Volatile private var cache: RhinoCache? = null

    /**
     * Deobfuscates a YouTube stream signature.
     * Thread-safe via @Synchronized.
     */
    @Synchronized
    fun deobfuscate(videoId: String, obfuscatedSignature: String): String {
        val jsCode = getPlayerJs(videoId)
        val rhinoCache = getOrBuildCache(jsCode)

        val escapedSig = obfuscatedSignature
            .replace("""\""", """\\""")
            .replace("'", "\\'")

        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            val result = cx.evaluateString(
                rhinoCache.scope,
                "_yt_player.__deobfuscate('$escapedSig')",
                "deobf", 1, null
            )
            return result.toString().also {
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

    private fun getOrBuildCache(jsCode: String): RhinoCache {
        val hash = jsCode.hashCode()
        cache?.let { if (it.jsCodeHash == hash) return it }

        cache = null

        log.fine("Building new Rhino cache (jsCode.length=${jsCode.length})")
        val t0 = System.currentTimeMillis()

        val cx = Context.enter()
        try {
            @Suppress("DEPRECATION")
            cx.optimizationLevel = -1

            val hookCode = buildHookCode(jsCode)
                ?: run {
                    throw IllegalStateException("Could not locate sig deobfuscation call in base.js")
                }

            val closingIife = "})(_yt_player);"
            val insertIdx = jsCode.lastIndexOf(closingIife)
            val modifiedJs = if (insertIdx >= 0) {
                jsCode.substring(0, insertIdx) + hookCode + jsCode.substring(insertIdx)
            } else {
                log.warning("Could not find IIFE closing, appending hook at end")
                jsCode + "\n" + hookCode
            }

            val scope = cx.initStandardObjects()
            cx.evaluateString(scope, BROWSER_STUBS, "stubs", 1, null)
            cx.evaluateString(scope, modifiedJs, "base.js", 1, null)
            log.fine("Rhino cache built in ${System.currentTimeMillis() - t0}ms")
            
            val newCache = RhinoCache(scope, hash)
            cache = newCache
            return newCache
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
            val varMatcher = Pattern.compile("""(\w+)\.s\s*\)""").matcher(callExpr)
            val sigVar = if (varMatcher.find()) varMatcher.group(1) else null
            val deobfCall = when {
                sigVar != null -> callExpr.replace("${sigVar}.s", "s")
                else -> callExpr.replace(Regex("""\w+\.s"""), "s")
            }
            return ";g.__deobfuscate = function(s) { return ${deobfCall}; };"
        }
        return null
    }

    /** Forces re-evaluation of base.js on next call (e.g. after YouTube player update). */
    @Synchronized
    fun clearCache() {
        cache = null
    }
}