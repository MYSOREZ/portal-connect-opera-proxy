package com.example.operaproxy

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.security.MessageDigest

/**
 * Помощник для работы с PIN-защитой.
 */
object PinHelper {
    private const val PREFS = "OperaProxyPrefs"
    private const val KEY_PIN_ENABLED = "PIN_ENABLED"
    private const val KEY_PIN_HASH = "PIN_HASH"

    fun isPinEnabled(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PIN_ENABLED, false)
    }

    private fun getPinHash(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN_HASH, null)
    }

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(ctx: Context, pin: String): Boolean {
        val hash = getPinHash(ctx) ?: return false
        return hashPin(pin) == hash
    }

    fun setPin(ctx: Context, pin: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PIN_HASH, hashPin(pin))
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
    }

    fun disablePin(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_ENABLED, false)
            .apply()
    }

    fun showVerifyDialog(context: Context, onSuccess: () -> Unit, onCancel: (() -> Unit)? = null) {
        if (context is Activity && context.isDestroyed) return

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pin_verify, null)
        val editPin = view.findViewById<TextInputEditText>(R.id.editPin)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pin_dialog_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { d, _ -> 
                d.dismiss()
                onCancel?.invoke()
            }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = editPin.text?.toString()?.trim() ?: ""
                if (pin.length != 4) {
                    Toast.makeText(context, "Введите 4 цифры", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (verifyPin(context, pin)) {
                    dialog.dismiss()
                    onSuccess()
                } else {
                    Toast.makeText(context, R.string.pin_error_wrong, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    fun showSetupDialog(context: Context, onSuccess: () -> Unit, onCancel: (() -> Unit)? = null) {
        if (context is Activity && context.isDestroyed) return

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pin_setup, null)
        val editPin1 = view.findViewById<TextInputEditText>(R.id.editPin1)
        val editPin2 = view.findViewById<TextInputEditText>(R.id.editPin2)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pin_setup_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
                onCancel?.invoke()
            }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = editPin1.text?.toString()?.trim() ?: ""
                val p2 = editPin2.text?.toString()?.trim() ?: ""

                if (p1.length != 4) {
                    Toast.makeText(context, "Введите 4 цифры", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (p1 != p2) {
                    Toast.makeText(context, "PIN не совпадают", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                setPin(context, p1)
                Toast.makeText(context, "PIN установлен", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onSuccess()
            }
        }
        dialog.show()
    }
}
