package com.smartlife.sakemaru.bansuke.command

import android.content.Context
import com.smartlife.sakemaru.bansuke.config.DeviceConfig
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.network.dto.DeviceCommandDto
import com.smartlife.sakemaru.bansuke.update.AppUpdateCommandHandler

class CommandDispatcher(
    context: Context,
    private val repository: DeviceRegistrationRepository,
) {
    private val appContext = context.applicationContext

    suspend fun dispatch(config: DeviceConfig, command: DeviceCommandDto) {
        val outcome = try {
            when (command.type) {
                "app_update" -> AppUpdateCommandHandler(appContext).handle(config, command)
                else -> CommandExecutionOutcome.error(
                    errorCode = "UNSUPPORTED_COMMAND",
                    message = "Unsupported command type: ${command.type}",
                )
            }
        } catch (throwable: Throwable) {
            MdmLog.warn("Command handler crashed: id=${command.id}, type=${command.type}, error=${throwable.message}", throwable)
            CommandExecutionOutcome.error(
                errorCode = "COMMAND_FAILED",
                message = throwable.message ?: throwable::class.java.simpleName,
            )
        }

        repository.sendCommandResult(
            commandId = command.id,
            result = outcome.result,
            errorCode = outcome.errorCode,
            message = outcome.message,
        )
        MdmLog.info(
            "Command result sent: id=${command.id}, type=${command.type}, " +
                "result=${outcome.result}, errorCode=${outcome.errorCode ?: "-"}"
        )
        repository.heartbeat()
    }
}
