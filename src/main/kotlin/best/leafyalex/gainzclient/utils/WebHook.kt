package best.leafyalex.gainzclient.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object WebHook {

    fun sendEmbed(url: String, embed: JsonObject) {
        val body = JsonObject()
        body.addProperty("content", "")
        body.addProperty("username", "Gainz Watcher")
        body.addProperty("avatar_url", "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")

        val arr = JsonArray()
        arr.add(embed)
        body.add("embeds", arr)

        println("Sending webhook with body...")
        println(body.toString())
        val url = URL(url)
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.addRequestProperty("Content-Type", "application/json")
            conn.addRequestProperty("User-Agent", "Gainz-Client-Webhook")
            conn.doOutput = true
            conn.requestMethod = "POST"

            DataOutputStream(conn.outputStream).use { it.writeBytes(body.toString()) }
            BufferedReader(InputStreamReader(conn.inputStream)).use { bf ->
                var line: String?
                while (bf.readLine().also { line = it } != null) {
                    println(line)
                }
            }
        } catch (e: Exception) {
            println("bruh")
            println(conn.responseMessage)
            println(conn.errorStream)
            e.printStackTrace()
            // just print all the messages
        }
    }

    fun buildEmbed(title: String, description: String, fields: JsonArray, footer: JsonObject, author: JsonObject, thumbnail: JsonObject, color: Int): JsonObject {
        val obj = JsonObject()
        obj.addProperty("title", title)
        if (description != "")
            obj.addProperty("description", description)
        obj.addProperty("color", color)
        obj.add("footer", footer)
        obj.add("author", author)
        obj.add("thumbnail", thumbnail)
        obj.add("fields", fields)
        return obj
    }

    fun buildFields(fields: ArrayList<Map<String, String>>): JsonArray {
        val arr = JsonArray()
        for (field in fields) {
            val obj = JsonObject()
            obj.addProperty("name", field["name"])
            obj.addProperty("value", field["value"])
            obj.addProperty("inline", field["inline"] == "true")
            arr.add(obj)
        }
        return arr
    }

    fun buildAuthor(name: String, icon: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("name", name)
        obj.addProperty("icon_url", icon)
        return obj
    }

    fun buildThumbnail(url: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("url", url)
        return obj
    }

    fun buildFooter(text: String, icon: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("text", text)
        obj.addProperty("icon_url", icon)
        return obj
    }

}