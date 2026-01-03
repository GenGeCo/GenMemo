package com.example.genmemo.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Data models for online packages from the GenMemo web platform.
 * API: https://www.gruppogea.net/genmemo/api/download.php?id=UUID
 */

@Serializable
data class OnlinePackage(
    val version: String = "",
    val id: String,
    val name: String,
    val description: String = "",
    val author: String,
    @SerialName("created_at")
    val createdAt: String = "",
    val settings: PackageSettings = PackageSettings(),
    val questions: List<OnlineQuestion>
) {
    // Compatibility properties for existing code
    val tts: TtsSettings get() = TtsSettings(
        enabled = settings.answerTypes.contains("tts") || settings.questionTypes.contains("tts")
    )
    val meta: PackageMeta get() = PackageMeta(
        uuid = id,
        name = name,
        author = author,
        downloadCount = 0
    )
}

@Serializable
data class PackageSettings(
    @SerialName("question_types")
    val questionTypes: List<String> = emptyList(),
    @SerialName("answer_types")
    val answerTypes: List<String> = emptyList(),
    @SerialName("total_questions")
    val totalQuestions: Int = 0
)

@Serializable
data class TtsSettings(
    val enabled: Boolean = false
)

@Serializable
data class PackageMeta(
    val uuid: String,
    val name: String,
    val author: String,
    @SerialName("download_count")
    val downloadCount: Int = 0,
    @SerialName("server_url")
    val serverUrl: String = ""
)

@Serializable
data class OnlineQuestion(
    val question: String,
    val mode: QuestionMode,
    @SerialName("question_tts")
    val questionTts: Boolean = false,
    @SerialName("question_image")
    val questionImage: String? = null,
    // For multiple choice
    val answers: List<MultipleAnswer>? = null,
    // For true/false, write_exact, write_word, write_partial
    @SerialName("correct_answer")
    @Serializable(with = CorrectAnswerSerializer::class)
    val correctAnswer: CorrectAnswer? = null,
    // For write_partial - alternative accepted answers
    @SerialName("accept_also")
    val acceptAlso: List<String>? = null
)

/**
 * Sealed class to handle correct_answer being either Boolean or String
 */
sealed class CorrectAnswer {
    data class BooleanAnswer(val value: Boolean) : CorrectAnswer()
    data class StringAnswer(val value: String) : CorrectAnswer()

    fun asBoolean(): Boolean? = (this as? BooleanAnswer)?.value
    fun asString(): String = when (this) {
        is BooleanAnswer -> value.toString()
        is StringAnswer -> value
    }
}

/**
 * Custom serializer for CorrectAnswer to handle both boolean and string values
 */
object CorrectAnswerSerializer : KSerializer<CorrectAnswer> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CorrectAnswer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CorrectAnswer) {
        when (value) {
            is CorrectAnswer.BooleanAnswer -> encoder.encodeBoolean(value.value)
            is CorrectAnswer.StringAnswer -> encoder.encodeString(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): CorrectAnswer {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer only works with JSON")

        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonPrimitive) {
            // Try boolean first
            element.booleanOrNull?.let {
                return CorrectAnswer.BooleanAnswer(it)
            }
            // Otherwise it's a string
            element.contentOrNull?.let {
                return CorrectAnswer.StringAnswer(it)
            }
        }
        throw IllegalStateException("Expected boolean or string for correct_answer")
    }
}

@Serializable
data class MultipleAnswer(
    val text: String,
    val tts: Boolean = false,
    val correct: Boolean = false
)

/**
 * Question modes supported by the online platform
 */
@Serializable
enum class QuestionMode {
    @SerialName("multiple")
    MULTIPLE,

    @SerialName("truefalse")
    TRUE_FALSE,

    @SerialName("write_exact")
    WRITE_EXACT,

    @SerialName("write_word")
    WRITE_WORD,

    @SerialName("write_partial")
    WRITE_PARTIAL
}
