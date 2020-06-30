package com.example.mocklocationserver.web

import android.content.Context
import com.example.mocklocationserver.web.dto.FakeLocation
import com.example.mocklocationserver.web.dto.LocationJsonFields
import com.example.mocklocationserver.web.dto.RequestFakeLocation
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.channels.Channel
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Web サーバー
 * web api のハンドル
 * assets/html ディレクトリにあるファイルを返す
 */
class MockLocationWebServer(private val context: Context, val port: Int) : NanoHTTPD(port) {

    private val lockObject = Object()

    private val notifyUpdate = Object()

    // HACK コルーチンのキャンセルができてこちらの方が良いが、受信者が多になる場合はだめ
    private val notifyChannel = Channel<Unit>(0)

    private var requestFakeLocation: RequestFakeLocation? =  null


    override fun stop() {
        super.stop()

        try {
            synchronized(notifyUpdate) {
                notifyUpdate.notifyAll()
            }
        }
        catch (e: Exception) {
        }

        notifyChannel.cancel()
    }

    override fun serve(session: IHTTPSession?): Response {
        session?.let {
            // web api として処理する
            handleWebAPI(it)?.let { return it }

            // web api でなければ、assets/htmlディレクトリにあるファイルを返す
            val filepath = if (it.uri == "/") "html/index.html" else "html${it.uri}"
            //println("filepath $filepath")

            try {
                val f = context.resources.assets.open(filepath)
                val i = filepath.lastIndexOf('.')

                // とりあえず拡張子があるファイルのみ
                if (0 <= i) {
                    // 拡張子から mimetype を作成
                    val mime = when (filepath.substring(i + 1).toLowerCase(Locale.getDefault())) {
                        "html", "htm" -> "text/html"
                        "js" -> "text/javascript"
                        "css" -> "text/css"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> ""
                    }
                    if (mime.isNotEmpty()) {
                        return newChunkedResponse(Response.Status.OK, mime, f)
                    }
                }
            }
            catch (e: Exception) {
                println("${e}")
            }

            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                it.uri
            );
        }

        return super.serve(session)
    }


    /**
     * リクエストを web api として処理する
     * 処理できれば Response を返す
     * 処理できなければ null を返す
     */
    private fun handleWebAPI(h: IHTTPSession): Response? {
        when (h.uri) {
            "/api/location" -> {
                if (h.method == Method.POST) {
                    val body = mutableMapOf<String, String>()
                    try {
                        h.parseBody(body)
                        return onLocationRequest(body)
                    }
                    catch (e: Exception) {
                    }

                    // TODO http status code
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "text",
                        ""
                    )
                }
            }
        }
        return null
    }


    /**
     * 位置更新リクエスト
     */
    private fun onLocationRequest(body: MutableMap<String, String>): Response {
        body["postData"]?.let {
            val l = parseToFakeLocation(it)
            if (l != null) {
                if (l.hasNaN() == false) {
                    setLocation(l)
                    return newFixedLengthResponse(
                        Response.Status.OK,
                        "text",
                        ""
                    )
                }
            }
        }

        // とりあえず、以下の場合すべて、BAD_REQUEST、とする
        // postData が null
        // postData が JSON でない
        // 数値への変換失敗 (Double.isNaN)
        return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "text",
            ""
        )
    }


    private fun setLocation(l: FakeLocation) {
        println("setLocation: $l")
        var d = Date()
        synchronized(lockObject)  {
            requestFakeLocation =
                RequestFakeLocation(d, l)
        }

        synchronized(notifyUpdate) {
            notifyUpdate.notifyAll()
        }

        notifyChannel.offer(Unit)
    }


    fun getLocation(): RequestFakeLocation? {
        synchronized(lockObject) {
            return requestFakeLocation?.copy()
        }
    }


    /**
     * 新しい位置情報を受信するまで待機する
     */
    fun waitNewLocation(date: Date?, timeout: Long): Boolean {
        synchronized(lockObject) {
            if (date != requestFakeLocation?.date) {
                return true
            }
        }

        var notified = false
        synchronized(notifyUpdate) {
            try {
                notifyUpdate.wait(timeout)
                notified = false
            }
            catch (e: InterruptedException) {
                notified = true
            }
        }
        return notified
    }


    /**
     * 新しい位置情報を受信するまで待機する
     * HACK withTimeoutOrNull などが使用でき便利だが、呼び出し側が複数になる場合、offer等によりブロックが解除されるのは1つのため、だめ
     */
    suspend fun waitChannel(date: Date?) {
        synchronized(lockObject) {
            if (date != requestFakeLocation?.date) {
                return
            }
        }
        notifyChannel.receive()
    }


    companion object {

        /**
         * キーが存在する場合に optDouble を実行する
         * キーが存在しない場合は valueWhenNotContains を返す
         */
        fun JSONObject.optDoubleWhenContains(s: String, valueWhenNotContains: Double): Double {
            if (this.has(s))
                return this.optDouble(s)
            else
                return valueWhenNotContains
        }

        /**
         * JSON 文字列を FakeLocation へ変換する
         */
        fun parseToFakeLocation(json: String?): FakeLocation? {
            if (json == null) {
                return null
            }

            try {
                val o = JSONObject(json)
                val lat = o.optDouble(LocationJsonFields.lat.prop)
                val lng = o.optDouble(LocationJsonFields.lng.prop)

                // 以下は、フィールドがない場合は0にする (緯度、経度が指定されていれば良い)
                val alt = o.optDoubleWhenContains(LocationJsonFields.alt.prop, 0.0)
                val hacc = o.optDoubleWhenContains(LocationJsonFields.hacc.prop, 0.0)

                val l = FakeLocation(
                    lat,
                    lng,
                    alt,
                    hacc
                )

                return l
            }
            catch (e: JSONException) {
                println("json parse error: $e")
            }
            return null
        }

    }
}



