package be.immoj.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import be.immoj.MainActivity
import be.immoj.R
import be.immoj.data.Annonce
import be.immoj.util.Format

/** Notifications Android locales (mode sans compte). */
object Notifications {
    const val CANAL = "immoj_annonces"

    fun creerCanal(contexte: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Nouvelles annonces", NotificationManager.IMPORTANCE_DEFAULT)
            canal.description = "Nouvelles annonces et baisses de prix correspondant à tes recherches"
            contexte.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    fun autorisees(contexte: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexte, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun nouvelleAnnonce(contexte: Context, a: Annonce, nomRecherche: String?) {
        val details = listOfNotNull(
            Format.type(a.type), Format.prix(a.prix, a.transaction),
            a.chambres?.let { "$it ch." }, a.surface?.let { "$it m²" },
        ).joinToString(" · ")
        envoyer(contexte, a.id.hashCode(),
            if (nomRecherche != null) "$nomRecherche : ${a.lieu}" else "Nouveau : ${a.lieu}",
            "$details\n${a.titre ?: ""}", a.id)
    }

    fun baissePrix(contexte: Context, a: Annonce, ancien: Int) {
        envoyer(contexte, a.id.hashCode() + 1, "Prix baissé : ${a.lieu}",
            "${Format.prix(ancien, a.transaction)} → ${Format.prix(a.prix, a.transaction)}\n${a.titre ?: ""}",
            a.id)
    }

    fun resume(contexte: Context, nb: Int) {
        envoyer(contexte, 1, "ImmoJ", "$nb nouvelles annonces correspondent à tes recherches", null)
    }

    private fun envoyer(contexte: Context, id: Int, titre: String, texte: String, annonceId: String?) {
        if (!autorisees(contexte)) return
        val intent = Intent(contexte, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            annonceId?.let { putExtra("annonce_id", it) }
        }
        val pending = PendingIntent.getActivity(contexte, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(contexte, CANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titre)
            .setContentText(texte.lines().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(contexte).notify(id, notif)
        } catch (_: SecurityException) {
            // Permission retirée entre-temps : on ignore.
        }
    }
}
