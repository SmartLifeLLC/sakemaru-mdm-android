package com.smartlife.sakemaru.bansuke.command

data class CommandExecutionOutcome(
    val result: String,
    val errorCode: String? = null,
    val message: String? = null,
) {
    companion object {
        fun done(message: String? = null) = CommandExecutionOutcome(
            result = "done",
            message = message,
        )

        fun error(errorCode: String, message: String) = CommandExecutionOutcome(
            result = "error",
            errorCode = errorCode,
            message = message,
        )
    }
}

