package com.guardian.client

import java.io.File
import java.util.*
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.*
import javax.mail.internet.*

/**
 * Sends the PDF report over SMTP with STARTTLS (port 587) using JavaMail.
 * Throws on failure so the WorkManager job retries - nothing is lost.
 */
object Mailer {

    fun send(cfg: Config, subject: String, bodyText: String, attachment: File) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", cfg.smtpHost)
            put("mail.smtp.port", cfg.smtpPort)
            put("mail.smtp.ssl.protocols", "TLSv1.2")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(cfg.smtpUser, cfg.smtpPass)
        })

        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(cfg.fromEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(cfg.toEmail))
            setSubject(subject)
            sentDate = Date()
        }

        val textPart = MimeBodyPart().apply { setText(bodyText) }
        val filePart = MimeBodyPart().apply {
            dataHandler = DataHandler(FileDataSource(attachment))
            fileName = attachment.name
        }
        msg.setContent(MimeMultipart().apply {
            addBodyPart(textPart); addBodyPart(filePart)
        })

        Transport.send(msg)
    }
}
