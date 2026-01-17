package com.spamstopper.app.domain.model

import java.util.Date

data class CallHistoryItem(
    val id: String,
    val phoneNumber: String,
    val contactName: String?,
    val date: Date,
    val duration: Int,
    val callType: CallType,
    val isBlocked: Boolean,
    val transcript: String?,
    val spamScore: Float?
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED
}