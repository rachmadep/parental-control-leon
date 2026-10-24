package com.parentalcontrol.child.manager

/**
 * Keputusan kapan penegakan batas waktu boleh bertindak terhadap layar (logika murni, bisa diuji).
 *
 * Layar mati: anak tidak bisa memakai tablet, jadi tidak ada yang perlu dihentikan, dan overlay tidak
 * boleh dimunculkan karena akan membangunkan layar lalu menguras baterai. Pemeriksaan dilanjutkan
 * begitu layar menyala lagi.
 */
object LockOverlayPolicy {

    fun shouldEvaluate(screenInteractive: Boolean): Boolean = screenInteractive

    /**
     * @param overlayVisible overlay sedang tampil di depan (bukan sekadar masih ada di latar belakang).
     * Anak yang menekan Home meninggalkan overlay hidup tetapi tidak terlihat, jadi harus dimunculkan lagi.
     */
    fun shouldLaunchOverlay(violation: Boolean, screenInteractive: Boolean, overlayVisible: Boolean): Boolean =
        violation && screenInteractive && !overlayVisible
}
