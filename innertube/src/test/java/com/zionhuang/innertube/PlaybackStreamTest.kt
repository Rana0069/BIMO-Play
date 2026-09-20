package com.zionhuang.innertube

import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.response.PlayerResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.util.regex.Pattern

class PlaybackStreamTest {
    private val videoId = "0KSOMA3QBU0" // Maroon 5 - Animals
    private val client = OkHttpClient()
    private val youTube = YouTube

    @Test
    fun testWebRemixStream() = kotlinx.coroutines.runBlocking {
        NewPipeUtils.getSignatureTimestamp(videoId)
        val cls = org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager::class.java
        val method = cls.getDeclaredMethod("extractJavaScriptCodeIfNeeded", String::class.java)
        method.isAccessible = true
        method.invoke(null, videoId)

        val codeField = cls.getDeclaredField("cachedJavaScriptPlayerCode")
        codeField.isAccessible = true
        val jsCode = codeField.get(null) as String
        println("jsCode length: ${jsCode.length}")

        val t0 = System.currentTimeMillis()
        val cx = org.mozilla.javascript.Context.enter()
        cx.optimizationLevel = -1
        try {
            val scope = cx.initStandardObjects()
            val initScript = """
                var window = this;
                var self = this;
                var document = {
                    createElement: function() { return { getContext: function() { return null; } }; },
                    getElementsByTagName: function() { return []; },
                    documentElement: {}
                };
                var navigator = { userAgent: 'Mozilla/5.0', cookieEnabled: true };
                var location = { hostname: 'music.youtube.com', href: 'https://music.youtube.com' };
                function XMLHttpRequest() {
                    this.open = function() {};
                    this.send = function() {};
                    this.setRequestHeader = function() {};
                }
                function Image() {}
            """.trimIndent()
            // Find the call site in jsCode:
            val callPattern = java.util.regex.Pattern.compile("""([a-zA-Z0-9_${'$'}]+)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*([a-zA-Z0-9_${'$'}]+)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\w+)\.s\s*\)\s*\)""")
            val matcher = callPattern.matcher(jsCode)
            if (!matcher.find()) {
                throw RuntimeException("Could not find signature call pattern in base.js!")
            }
            val callExpr = matcher.group(0)
            val varName = matcher.group(7) // e.g. "b"
            println("Found callExpr: $callExpr with var: $varName")

            // Check if there is an outer encoder like YJ(2, 7845, Y)
            val encPattern = java.util.regex.Pattern.compile("""([a-zA-Z0-9_${'$'}]+)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\w+)\)""")
            val afterCall = jsCode.substring(matcher.end(), Math.min(jsCode.length, matcher.end() + 100))
            val encMatcher = encPattern.matcher(afterCall)
            val encExpr = if (encMatcher.find()) encMatcher.group(0) else null
            println("Found encExpr: $encExpr in afterCall: $afterCall")

            // Replace b.s with s in callExpr
            val deobfCall = callExpr.replace("$varName.s", "s")
            val hook = ";g.__deobfuscate = function(s) { return $deobfCall; };"
            println("Injecting hook: $hook")

            val endIdx = jsCode.lastIndexOf("})(_yt_player);")
            val modifiedJs = jsCode.substring(0, endIdx) + hook + jsCode.substring(endIdx)

            cx.evaluateString(scope, initScript, "init", 1, null)
            cx.evaluateString(scope, modifiedJs, "base.js", 1, null)
            println("Base.js successfully evaluated with hook in ${System.currentTimeMillis() - t0} ms!")

            // Now fetch real WEB_REMIX format
            val sigTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
            val playerResp = YouTube.player(videoId, null, client = YouTubeClient.WEB_REMIX, signatureTimestamp = sigTimestamp).getOrThrow()
            val format = playerResp.streamingData!!.adaptiveFormats.first { it.isAudio }
            println("Format signatureCipher: ${format.signatureCipher}")

            val params = io.ktor.http.parseQueryString(format.signatureCipher!!)
            val s = params["s"]!!
            val sp = params["sp"]!!
            val rawUrl = params["url"]!!

            // Call _yt_player.__deobfuscate(s)
            val testCall = "_yt_player.__deobfuscate('$s')"
            val deobfSig = cx.evaluateString(scope, testCall, "deobf", 1, null).toString()
            println("Original s: $s")
            println("Deobfuscated sig: $deobfSig")

            // Test downloading first chunk (0-1000)
            val finalUrl1 = "$rawUrl&$sp=$deobfSig"
            val req1 = okhttp3.Request.Builder()
                .url(finalUrl1)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                .header("Range", "bytes=0-1000")
                .build()
            val resp1 = client.newCall(req1).execute()
            println("Test 1 (raw deobfSig) bytes=0-1000: code=${resp1.code}, len=${resp1.body?.contentLength()}")
            resp1.close()

            // Also test if encoded sig needed
            if (resp1.code != 206) {
                val encSig = cx.evaluateString(scope, "encodeURIComponent('$deobfSig')", "enc", 1, null).toString()
                val finalUrl2 = "$rawUrl&$sp=$encSig"
                val req2 = okhttp3.Request.Builder()
                    .url(finalUrl2)
                    .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                    .header("Range", "bytes=0-1000")
                    .build()
                val resp2 = client.newCall(req2).execute()
                println("Test 2 (encoded sig) bytes=0-1000: code=${resp2.code}, len=${resp2.body?.contentLength()}")
                resp2.close()
            }

            // Test bytes past 30 seconds (2,000,000 - 2,001,000)
            val reqPast30s = okhttp3.Request.Builder()
                .url(finalUrl1)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                .header("Range", "bytes=2000000-2001000")
                .build()
            val respPast30s = client.newCall(reqPast30s).execute()
            println("Test past 30s (2MB-2MB+1KB): code=${respPast30s.code}, len=${respPast30s.body?.contentLength()}")
            respPast30s.close()
        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }
}
