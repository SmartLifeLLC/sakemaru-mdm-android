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
    private val executedIds = appContext.getSharedPreferences("mdm_executed_commands", Context.MODE_PRIVATE)

    suspend fun dispatch(config: DeviceConfig, command: DeviceCommandDto) {
        if (executedIds.contains(command.id.toString())) {
            MdmLog.info("Command already executed, skipping: id=${command.id}, type=${command.type}")
            return
        }

        val outcome = try {
            when (command.type) {
                "app_update" -> AppUpdateCommandHandler(appContext).handle(config, command)
                "device_lock" -> DeviceLockCommandHandler(appContext).lock(command)
                "device_unlock" -> DeviceLockCommandHandler(appContext).unlock()
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
        executedIds.edit().putBoolean(command.id.toString(), true).apply()
        MdmLog.info(
            "Command result sent: id=${command.id}, type=${command.type}, " +
                "result=${outcome.result}, errorCode=${outcome.errorCode ?: "-"}"
        )
        repository.heartbeat()
    }
}
