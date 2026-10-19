package com.ayeshiai.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(
    val id: Long,
    val role: String, // "user" or "model"
    val text: String,
    val isError: Boolean = false
)

data class Conversation(
    val id: Long,
    val title: String,
    val updated: Long,
    val messages: List<ChatMessage>
)

/** Conversation history stored as a private JSON file inside the app's internal storage. */
class ChatRepository(context: Context) {
    private val file = File(context.filesDir, "conversations.json")
    private var cache: MutableList<Conversation>? = null

    @Synchronized
    private fun ensure(): MutableList<Conversation> {
        cache?.let { return it }
        val list = mutableListOf<Conversation>()
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ms = o.getJSONArray("messages")
                    val msgs = (0 until ms.length()).map { j ->
                        val m = ms.getJSONObject(j)
                        ChatMessage(
                            m.getLong("id"),
                            m.getString("role"),
                            m.getString("text"),
                            m.optBoolean("error", false)
                        )
                    }
                    list.add(Conversation(o.getLong("id"), o.getString("title"), o.getLong("updated"), msgs))
                }
            }
        } catch (e: Exception) {
            list.clear()
        }
        cache = list
        return list
    }

    @Synchronized
    fun all(): List<Conversation> = ensure().sortedByDescending { it.updated }

    @Synchronized
    fun upsert(c: Conversation) {
        val list = ensure()
        list.removeAll { it.id == c.id }
        list.add(c)
        write(list)
    }

    @Synchronized
    fun delete(id: Long) {
        val list = ensure()
        list.removeAll { it.id == id }
        write(list)
    }

    @Synchronized
    fun clear() {
        ensure().clear()
        write(emptyList())
    }

    @Synchronized
    fun exportJson(memories: List<String>): String {
        val root = JSONObject()
        root.put("app", "AYESHI AI")
        root.put("conversations", toJson(ensure()))
        root.put("memories", JSONArray(memories))
        return root.toString(2)
    }

    private fun toJson(list: List<Conversation>): JSONArray {
        val arr = JSONArray()
        list.forEach { c ->
            val ms = JSONArray()
            c.messages.forEach { m ->
                ms.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("role", m.role)
                        .put("text", m.text)
                        .put("error", m.isError)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("title", c.title)
                    .put("updated", c.updated)
                    .put("messages", ms)
            )
        }
        return arr
    }

    private fun write(list: List<Conversation>) {
        try {
            val tmp = File(file.parentFile, "conversations.json.tmp")
            tmp.writeText(toJson(list).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(toJson(list).toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // Storage failure: keep the app working; history just won't persist.
        }
    }
}
