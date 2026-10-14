package com.example.actions.impl

import android.app.Activity
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.actions.Action
import com.example.actions.ActionResult
import com.example.actions.ConfirmationSpec
import com.example.actions.ParamDefinition
import com.example.service.NotificationStore

/**
 * Reads recent message notifications (WhatsApp, SMS, Telegram...) and replies to them using the
 * notification's own reply box. Replying is a risky action: the user confirms (or has set
 * "this session"/"always" in Settings).
 */
class PhoneNotificationsAction(private val context: Context) : Action {

    override val name: String = "phone_notifications"
    override val description: String =
        "Reads the user's recent message notifications (operation 'list' or 'read') and replies to one " +
        "(operation 'reply') through its inline reply, for example answering a WhatsApp message. " +
        "Needs Notification access to be switched on."
    override val riskyLabel: String = "Reply to messages"

    override val parameters: Map<String, ParamDefinition> = mapOf(
        "operation" to ParamDefinition("string", "What to do", required = true, enumValues = listOf("list", "read", "reply")),
        "app" to ParamDefinition("string", "Filter by app, for example 'WhatsApp'", required = false),
        "sender" to ParamDefinition("string", "Filter by sender / chat name", required = false),
        "text" to ParamDefinition("string", "The reply text (reply)", required = false)
    )

    override fun confirmationFor(args: Map<String, Any?>): ConfirmationSpec? {
        if ((args["operation"] as? String)?.lowercase() != "reply") return null
        val who = (args["sender"] as? String).orEmpty()
        val app = (args["app"] as? String)?.let { " on $it" }.orEmpty()
        return ConfirmationSpec("Send reply", "Reply to $who$app: \"${args["text"]}\"?")
    }

    override suspend fun execute(args: Map<String, Any?>): ActionResult {
        if (!hasAccess()) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return ActionResult(
                "I can't see notifications yet. I've opened Notification access: switch JARVIS on, then ask again.",
                isError = true
            )
        }
        val app = args["app"] as? String
        val sender = args["sender"] as? String
        return when ((args["operation"] as? String)?.lowercase()) {
            "list", "read" -> {
                val items = NotificationStore.recent(8, app, sender)
                if (items.isEmpty()) ActionResult("No recent messages${app?.let { " from $it" }.orEmpty()}.")
                else ActionResult(
                    items.joinToString("\n") { "${it.appName} - ${it.sender}: ${it.text.take(200)}" },
                    mapOf("title" to "Messages", "snippet" to items.joinToString("\n") { "${it.sender}: ${it.text.take(80)}" })
                )
            }
            "reply" -> reply(app, sender, args["text"] as? String)
            else -> ActionResult("I can list, read or reply.", isError = true)
        }
    }

    private fun reply(app: String?, sender: String?, text: String?): ActionResult {
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return ActionResult("What should the reply say?", isError = true)
        val target = NotificationStore.recent(10, app, sender).firstOrNull { it.replyAction != null }
            ?: return ActionResult("There is no recent message I can reply to from that chat. Ask me to list your messages first.", isError = true)
        val action = target.replyAction!!
        val inputs = action.remoteInputs ?: return ActionResult("That chat can't be replied to from here.", isError = true)
        return try {
            val fill = Intent()
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, body) } }
            RemoteInput.addResultsToIntent(inputs, fill, results)
            action.actionIntent.send(context, Activity.RESULT_OK, fill)
            ActionResult("Replied to ${target.sender} on ${target.appName}.")
        } catch (e: PendingIntent.CanceledException) {
            ActionResult("That conversation was dismissed, so I can't reply to it any more.", isError = true)
        }
    }

    private fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
