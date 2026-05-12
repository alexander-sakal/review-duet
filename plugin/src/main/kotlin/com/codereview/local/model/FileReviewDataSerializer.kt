package com.codereview.local.model

import com.google.gson.*
import java.lang.reflect.Type

class FileReviewDataSerializer {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(FileCommentStatus::class.java, FileCommentStatusAdapter())
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun deserialize(json: String): FileReviewData = gson.fromJson(json, FileReviewData::class.java)

    fun serialize(data: FileReviewData): String = gson.toJson(data)

    private class FileCommentStatusAdapter : JsonSerializer<FileCommentStatus>, JsonDeserializer<FileCommentStatus> {
        override fun serialize(src: FileCommentStatus, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            JsonPrimitive(src.jsonValue)

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FileCommentStatus =
            FileCommentStatus.fromString(json.asString)
    }
}
