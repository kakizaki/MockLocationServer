package com.example.mocklocationserver.web.server

import android.content.Context
import com.example.mocklocationserver.web.data.InMemoryLocationRequestRepository
import com.example.mocklocationserver.web.dto.LocationRequest
import com.example.mocklocationserver.web.dto.LocationJsonFields
import fi.iki.elonen.NanoHTTPD
import org.json.JSONException
import org.json.JSONObject
import java.util.*


/**
 * Web サーバー
 * web api のハンドル
 * assets/html ディレクトリにあるファイルを返す
 */
class MockLocationWebServer(
    port: Int,
    private val context: Context,
    private val repository: InMemoryLocationRequestRepository
) : NanoHTTPD(port) {

//    init {
        // for https://
//        val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
//        val stream = context.assets.open("keystore.bks")
//        keystore.load(stream, "password".toCharArray())
//
//        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
//        factory.init(keystore, "password".toCharArray())
//
//        makeSecure(NanoHTTPD.makeSSLSocketFactory(keystore, factory), null)
//    }


    override fun serve(session: IHTTPSession?): Response {
        session?.let {
            // web api として処理する
            handleWebAPI(it)?.let { response -> return response }

            // web api でなければ、assets/htmlディレクトリにあるファイルを返す
            val filepath = if (it.uri == "/") "html/index.html" else "html${it.uri}"
            //println("filepath $filepath")

            try {
                val f = context.resources.assets.open(filepath)
                val i = filepath.lastIndexOf('.')

                // とりあえず拡張子があるファイルのみ
                if (0 <= i) {
                    // 拡張子から mimetype を作成
                    val mime = when (filepath.substring(i + 1).lowercase(Locale.getDefault())) {
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
            } catch (e: Exception) {
                println("$e")
            }

            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                it.uri
            )
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
                    } catch (_: Exception) {
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
            if (l?.hasNaN() == false) {
                setLocation(l)
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "text",
                    ""
                )
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


    private fun setLocation(l: LocationRequest) {
        // HACK StateFlow.emit すればいいのでは。古いものは残していないし。
        println("setLocation: $l")
        repository.update(l)
    }



    companion object {

        /**
         * キーが存在する場合に optDouble を実行する
         * キーが存在しない場合は valueWhenNotContains を返す
         */
        private fun JSONObject.optDoubleWhenContains(s: String, valueWhenNotContains: Double): Double {
            return if (this.has(s))
                this.optDouble(s)
            else
                valueWhenNotContains
        }

        private fun JSONObject.optBooleanWhenContains(s: String, valueWhenNotContains: Boolean): Boolean {
            return if (this.has(s))
                this.optBoolean(s)
            else
                valueWhenNotContains
        }

        /**
         * JSON 文字列を FakeLocation へ変換する
         */
        fun parseToFakeLocation(json: String?): LocationRequest? {
            if (json == null) {
                return null
            }

            try {
                val o = JSONObject(json)
                val lat = o.optDouble(LocationJsonFields.Lat.prop)
                val lng = o.optDouble(LocationJsonFields.Lng.prop)

                // 以下は、フィールドがない場合は0にする (緯度、経度が指定されていれば良い)
                val alt= o.optDoubleWhenContains(LocationJsonFields.Altitude.prop, 0.0)
                val hAcc= o.optDoubleWhenContains(LocationJsonFields.HAccuracy.prop, 0.0)
                val repeatedly =
                    o.optBooleanWhenContains(LocationJsonFields.RepeatedlyUpdate.prop, false)
                val velocity = o.optDoubleWhenContains(LocationJsonFields.Velocity.prop, 0.0)

                return LocationRequest(
                    lat,
                    lng,
                    alt,
                    hAcc,
                    repeatedly,
                    velocity
                )
            } catch (e: JSONException) {
                println("json parse error: $e")
            }
            return null
        }
    }
}



