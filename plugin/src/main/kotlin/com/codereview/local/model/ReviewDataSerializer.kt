package com.codereview.local.model

import com.google.gson.*
import java.lang.reflect.Type

class ReviewDataSerializer {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(CommentStatus::class.java, CommentStatusAdapter())
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun deserialize(json: String): ReviewData {
        return gson.fromJson(json, ReviewData::class.java)
    }

    fun serialize(data: ReviewData): String {
        return gson.toJson(data)
    }

    private class CommentStatusAdapter : JsonSerializer<CommentStatus>, JsonDeserializer<CommentStatus> {
        override fun serialize(src: CommentStatus, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.jsonValue)
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CommentStatus {
            return CommentStatus.fromString(json.asString)
        }
    }
}
