package kr.lanthanide.wanderland

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

val CONFIG by lazy {
    val configFile = File("config.yml")
    if (configFile.exists()) {
        com.charleskorn.kaml.Yaml.default.decodeFromString(WanderlandConfigYaml.serializer(), configFile.readText())
    } else {
        WanderlandConfigYaml()
    }
}

@Serializable
data class WanderlandConfigYaml(
    val port: Int = 25565,
    val motd: String = "Wanderland Server",
    // path to favicon
    val favicon: String = "favicon.png",
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/wanderland",
    val dbUsername: String = "postgres",
    val dbPassword: String = "postgres",
    val dbMainScheme: String = "game_data",
    // uuid of the main world compositeInstance stored on DB.
    @Serializable(with = UUIDSerializer::class)
    val mainWorldUUID: UUID = UUID.randomUUID()
) {
    @OptIn(ExperimentalEncodingApi::class)
    @delegate:Transient
    val faviconBase64: String by lazy {
        Base64.Default.encode(File(favicon).readBytes())
    }
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}
