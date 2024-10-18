package dev.kwasi.echoservercomplete.models

import java.sql.Timestamp

/// The [ContentModel] class represents data that is transferred between devices when multiple
/// devices communicate with each other.
data class ContentModel(
    val message:String,
    var senderIp:String,
    var studentId: String,
    var timestamp: Long = System.currentTimeMillis()
)