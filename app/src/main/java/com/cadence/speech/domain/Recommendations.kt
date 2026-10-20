package com.cadence.speech.domain

import kotlin.math.roundToInt
import kotlin.random.Random

enum class SuggestionSeverity { MILD, MODERATE, HIGH }

data class Suggestion(
    val id: String,
    val text: String
)

private class Catalog(
    val intros: Map<Pair<Insight, SuggestionSeverity>, List<String>>,
    val bodies: Map<Insight, List<String>>,
    val actions: Map<Pair<Insight, SessionMode>, List<String>>
)

object RecommendationEngine {

    fun suggest(
        report: SessionReport,
        language: String,
        usedIds: Set<String>
    ): Suggestion {
        val catalog = if (language == "tr") trCatalog else enCatalog
        val values = values(report)
        val (category, severity) = evaluate(report)

        val exact = candidates(catalog, category, severity, report.mode, values)
        val unusedExact = exact.filterNot { it.id in usedIds }

        val pool = if (unusedExact.isNotEmpty()) {
            unusedExact
        } else {
            SuggestionSeverity.entries
                .flatMap { sev ->
                    SessionMode.entries.flatMap { mode ->
                        candidates(catalog, category, sev, mode, values)
                    }
                }
                .filterNot { it.id in usedIds }
                .ifEmpty { exact }
        }

        return pool.random(Random(report.id.hashCode()))
    }

    private fun values(report: SessionReport): Map<Insight, String> {
        val minutes = (report.durationMs / 60_000f).coerceAtLeast(0.5f)
        return mapOf(
            Insight.FLUENCY to (report.fillerCount / minutes).roundToInt().toString(),
            Insight.PACE to report.pace.toString(),
            Insight.TONE to report.pitchStdHz.roundToInt().toString(),
            Insight.CONTINUITY to report.longPauseCount.toString(),
            Insight.GOOD to report.score.toString()
        )
    }

    private fun evaluate(report: SessionReport): Pair<Insight, SuggestionSeverity> {
        val minutes = (report.durationMs / 60_000f).coerceAtLeast(0.5f)
        val profile = report.mode.profile()
        val fillersPerMinute = report.fillerCount / minutes

        val scores = linkedMapOf(
            Insight.FLUENCY to
                (100f - fillersPerMinute * profile.fillerPenaltyPerMinute).coerceIn(0f, 100f),
            Insight.PACE to ScoreCalculator.paceScore(report.pace, profile),
            Insight.TONE to
                ((report.pitchStdHz - 8f) / 32f * 100f).coerceIn(0f, 100f),
            Insight.CONTINUITY to report.speechRatio.coerceIn(0f, 1f) * 100f
        )

        val weakest = scores.minByOrNull { it.value } ?: return Insight.GOOD to SuggestionSeverity.MILD
        val category = if (weakest.value >= 70f) Insight.GOOD else weakest.key
        val severity = when {
            category == Insight.GOOD -> SuggestionSeverity.MILD
            weakest.value < 50f -> SuggestionSeverity.HIGH
            weakest.value < 68f -> SuggestionSeverity.MODERATE
            else -> SuggestionSeverity.MILD
        }
        return category to severity
    }

    private fun candidates(
        catalog: Catalog,
        category: Insight,
        severity: SuggestionSeverity,
        mode: SessionMode,
        values: Map<Insight, String>
    ): List<Suggestion> {
        val intros = catalog.intros[category to severity]
            ?: catalog.intros[category to SuggestionSeverity.MILD]
            ?: return emptyList()
        val bodies = catalog.bodies[category] ?: return emptyList()
        val actions = catalog.actions[category to mode] ?: return emptyList()
        val value = values[category].orEmpty()

        val result = ArrayList<Suggestion>(intros.size * bodies.size * actions.size)
        intros.forEachIndexed { iIndex, intro ->
            bodies.forEachIndexed { bIndex, body ->
                actions.forEachIndexed { aIndex, action ->
                    result += Suggestion(
                        id = "$category-$severity-$mode-$iIndex-$bIndex-$aIndex",
                        text = "$intro ${body.replace("{v}", value)} $action"
                    )
                }
            }
        }
        return result
    }

    private val enCatalog = Catalog(
        intros = mapOf(
            (Insight.FLUENCY to SuggestionSeverity.MILD) to listOf(
                "One small thing to polish:",
                "Your fluency is close to solid.",
                "Nearly there on fillers:"
            ),
            (Insight.FLUENCY to SuggestionSeverity.MODERATE) to listOf(
                "Fillers are starting to stand out.",
                "This is the main thing holding the score back:",
                "Your message is good, but the fillers distract:"
            ),
            (Insight.FLUENCY to SuggestionSeverity.HIGH) to listOf(
                "Fillers are dominating your speech.",
                "This needs attention before the next session:",
                "Your ideas are getting buried under filler sounds:"
            ),
            (Insight.PACE to SuggestionSeverity.MILD) to listOf(
                "Your pace is almost right.",
                "A small pace adjustment:",
                "You are nearly in the target rhythm:"
            ),
            (Insight.PACE to SuggestionSeverity.MODERATE) to listOf(
                "Your pace drifted noticeably.",
                "One clear fix for this session:",
                "The pace is making you harder to follow:"
            ),
            (Insight.PACE to SuggestionSeverity.HIGH) to listOf(
                "Your pace is far outside the comfortable range.",
                "This is the biggest issue right now:",
                "The speed is hurting clarity:"
            ),
            (Insight.TONE to SuggestionSeverity.MILD) to listOf(
                "Your tone is decent, with a little room:",
                "A small tone tweak:",
                "Almost expressive enough:"
            ),
            (Insight.TONE to SuggestionSeverity.MODERATE) to listOf(
                "Your tone is flattening out.",
                "Worth working on next:",
                "Monotone stretches are creeping in:"
            ),
            (Insight.TONE to SuggestionSeverity.HIGH) to listOf(
                "Your tone is very flat.",
                "This is the main improvement area:",
                "The delivery sounds monotone:"
            ),
            (Insight.CONTINUITY to SuggestionSeverity.MILD) to listOf(
                "Continuity is okay.",
                "A minor continuity note:",
                "Nearly steady:"
            ),
            (Insight.CONTINUITY to SuggestionSeverity.MODERATE) to listOf(
                "Pauses are interrupting the flow.",
                "Focus on continuity next:",
                "The gaps are adding up:"
            ),
            (Insight.CONTINUITY to SuggestionSeverity.HIGH) to listOf(
                "Long pauses are breaking the flow badly.",
                "This is the most urgent fix:",
                "The silence is costing you momentum:"
            ),
            (Insight.GOOD to SuggestionSeverity.MILD) to listOf(
                "Strong session.",
                "This went well.",
                "Good work overall."
            )
        ),
        bodies = mapOf(
            Insight.FLUENCY to listOf(
                "You used about {v} filler sounds per minute.",
                "Fillers appeared roughly {v} times per minute this session.",
                "That is {v} filler sounds per minute, above your best runs.",
                "The filler rate came in around {v} per minute."
            ),
            Insight.PACE to listOf(
                "You averaged {v} words per minute.",
                "Your pace settled near {v} wpm.",
                "You spoke at about {v} wpm on average.",
                "{v} wpm sits outside the sweet spot for this mode."
            ),
            Insight.TONE to listOf(
                "Pitch variation stayed around {v} Hz.",
                "Your pitch moved only about {v} Hz around the average.",
                "A pitch range of {v} Hz reads as fairly flat.",
                "The voice stayed within roughly {v} Hz of its center."
            ),
            Insight.CONTINUITY to listOf(
                "You had {v} long pauses in this session.",
                "Long silences happened {v} times.",
                "{v} pauses went past the comfort threshold.",
                "The session included {v} noticeable long pauses."
            ),
            Insight.GOOD to listOf(
                "You scored {v} overall.",
                "Your score reached {v}.",
                "You finished with {v} points.",
                "The final score was {v}."
            )
        ),
        actions = mapOf(
            (Insight.FLUENCY to SessionMode.INTERVIEW) to listOf(
                "In answers, replace each filler with a half-second pause; it reads as confidence.",
                "Finish your thought silently before starting the next sentence."
            ),
            (Insight.FLUENCY to SessionMode.PRESENTATION) to listOf(
                "Mark your slide transitions and breathe there instead of filling.",
                "Slow the sentence before a key point rather than adding a filler."
            ),
            (Insight.FLUENCY to SessionMode.FREE) to listOf(
                "Let one silence stand every few sentences; it sounds thoughtful, not empty.",
                "When you feel a filler coming, take a breath and continue."
            ),
            (Insight.PACE to SessionMode.INTERVIEW) to listOf(
                "Aim for 110-150 wpm and pause after your main point.",
                "Count to two silently before answering the next question."
            ),
            (Insight.PACE to SessionMode.PRESENTATION) to listOf(
                "Target 120-170 wpm and slow down on the important line.",
                "Use a deliberate pause before your closing sentence."
            ),
            (Insight.PACE to SessionMode.FREE) to listOf(
                "Stay between 110-190 wpm; there is no prize for speed.",
                "Drop the pace slightly whenever you change topic."
            ),
            (Insight.TONE to SessionMode.INTERVIEW) to listOf(
                "Lift the end of key sentences so answers sound engaged.",
                "Vary emphasis on the words that matter."
            ),
            (Insight.TONE to SessionMode.PRESENTATION) to listOf(
                "Add a rise on new points and a fall on conclusions.",
                "Stress one word per sentence to break the flatness."
            ),
            (Insight.TONE to SessionMode.FREE) to listOf(
                "Let your voice rise and fall naturally with the story.",
                "Emphasize the phrase you care about most."
            ),
            (Insight.CONTINUITY to SessionMode.INTERVIEW) to listOf(
                "Prepare a short bridge phrase for when you need to think.",
                "Keep a note of your next point so gaps stay short."
            ),
            (Insight.CONTINUITY to SessionMode.PRESENTATION) to listOf(
                "Rehearse transitions so you never hunt for the next line.",
                "Take a planned pause, then move on within two seconds."
            ),
            (Insight.CONTINUITY to SessionMode.FREE) to listOf(
                "When you lose the thread, summarize the last sentence out loud.",
                "Keep talking with a simple connector instead of stopping."
            ),
            (Insight.GOOD to SessionMode.INTERVIEW) to listOf(
                "Try a harder question or a longer answer next time.",
                "Keep this rhythm and add one concrete example per answer."
            ),
            (Insight.GOOD to SessionMode.PRESENTATION) to listOf(
                "Raise the difficulty by presenting without notes.",
                "Try a longer run and keep the same energy at the end."
            ),
            (Insight.GOOD to SessionMode.FREE) to listOf(
                "Try speaking on an unfamiliar topic for five minutes.",
                "Keep the relaxed flow and aim for a slightly higher score."
            )
        )
    )

    private val trCatalog = Catalog(
        intros = mapOf(
            (Insight.FLUENCY to SuggestionSeverity.MILD) to listOf(
                "Küçük bir pürüz kaldı:",
                "Akıcılığın neredeyse yerinde.",
                "Dolgu seslerinde sona yaklaştın:"
            ),
            (Insight.FLUENCY to SuggestionSeverity.MODERATE) to listOf(
                "Dolgu sesleri öne çıkmaya başladı.",
                "Skoru asıl düşüren şey bu:",
                "Anlatım iyi ama dolgular dikkat dağıtıyor:"
            ),
            (Insight.FLUENCY to SuggestionSeverity.HIGH) to listOf(
                "Dolgu sesleri konuşmaya hâkim.",
                "Bir sonraki seanstan önce buna odaklan:",
                "Fikirlerin dolgu seslerinin altında kalıyor:"
            ),
            (Insight.PACE to SuggestionSeverity.MILD) to listOf(
                "Tempo neredeyse doğru.",
                "Küçük bir tempo ayarı:",
                "Hedef ritme çok yakınsın:"
            ),
            (Insight.PACE to SuggestionSeverity.MODERATE) to listOf(
                "Tempon belirgin şekilde kaydı.",
                "Bu seans için net bir düzeltme:",
                "Tempo takip etmeyi zorlaştırıyor:"
            ),
            (Insight.PACE to SuggestionSeverity.HIGH) to listOf(
                "Tempon rahat aralığın çok dışında.",
                "Şu anki en büyük sorun bu:",
                "Hız anlaşılırlığı düşürüyor:"
            ),
            (Insight.TONE to SuggestionSeverity.MILD) to listOf(
                "Ton iyi, ufak bir alan var:",
                "Küçük bir ton dokunuşu:",
                "Neredeyse yeterince canlı:"
            ),
            (Insight.TONE to SuggestionSeverity.MODERATE) to listOf(
                "Tonun düzleşiyor.",
                "Sırada buna çalışmak var:",
                "Tek düze bölümler artıyor:"
            ),
            (Insight.TONE to SuggestionSeverity.HIGH) to listOf(
                "Tonun oldukça düz.",
                "En çok gelişecek alan bu:",
                "Sunum tek düze duyuluyor:"
            ),
            (Insight.CONTINUITY to SuggestionSeverity.MILD) to listOf(
                "Akış fena değil.",
                "Akışla ilgili küçük not:",
                "Neredeyse istikrarlı:"
            ),
            (Insight.CONTINUITY to SuggestionSeverity.MODERATE) to listOf(
                "Duraklar akışı bölüyor.",
                "Sırada akışa odaklan:",
                "Boşluklar birikiyor:"
            ),
            (Insight.CONTINUITY to SuggestionSeverity.HIGH) to listOf(
                "Uzun duraklar akışı ciddi bölüyor.",
                "En acil düzeltme bu:",
                "Sessizlik momentum kaybettiriyor:"
            ),
            (Insight.GOOD to SuggestionSeverity.MILD) to listOf(
                "Güçlü bir seans.",
                "Bu iyi gitti.",
                "Genel olarak iyi iş."
            )
        ),
        bodies = mapOf(
            Insight.FLUENCY to listOf(
                "Dakikada yaklaşık {v} dolgu sesi kullandın.",
                "Bu seansta dakikada ortalama {v} dolgu çıktı.",
                "Dakikada {v} dolgu, iyi seanslarına göre yüksek.",
                "Dolgu oranı dakikada {v} civarında."
            ),
            Insight.PACE to listOf(
                "Ortalama {v} kelime/dk konuştun.",
                "Tempon {v} kelime/dk civarında oturdu.",
                "Ortalaman yaklaşık {v} kelime/dk.",
                "{v} kelime/dk bu mod için ideal aralığın dışında."
            ),
            Insight.TONE to listOf(
                "Ses perde değişimi {v} Hz civarında kaldı.",
                "Tonun ortalama etrafında yalnızca {v} Hz hareket etti.",
                "{v} Hz'lik bir aralık düz duyuluyor.",
                "Sesin merkezden yaklaşık {v} Hz ayrıldı."
            ),
            Insight.CONTINUITY to listOf(
                "Bu seansta {v} uzun durak oldu.",
                "Uzun sessizlikler {v} kez yaşandı.",
                "{v} durak rahat eşiği aştı.",
                "Seansta {v} belirgin uzun durak vardı."
            ),
            Insight.GOOD to listOf(
                "Genel skorun {v}.",
                "Skorun {v} oldu.",
                "{v} puanla bitirdin.",
                "Final skorun {v}."
            )
        ),
        actions = mapOf(
            (Insight.FLUENCY to SessionMode.INTERVIEW) to listOf(
                "Cevaplarda her dolgu yerine yarım saniyelik sessizlik bırak; bu özgüven gibi duyulur.",
                "Cümleye başlamadan önce düşünceni sessizce tamamla."
            ),
            (Insight.FLUENCY to SessionMode.PRESENTATION) to listOf(
                "Slayt geçişlerini işaretle ve dolgu yerine orada nefes al.",
                "Önemli noktadan önce cümleyi yavaşlat, dolgu ekleme."
            ),
            (Insight.FLUENCY to SessionMode.FREE) to listOf(
                "Birkaç cümlede bir sessizliği bırak; kulağa boş değil, düşünceli gelir.",
                "Dolgu geleceğini hissettiğinde nefes al ve devam et."
            ),
            (Insight.PACE to SessionMode.INTERVIEW) to listOf(
                "110-150 kelime/dk hedefle; ana noktadan sonra duraksa.",
                "Sonraki sorudan önce içinden ikiye kadar say."
            ),
            (Insight.PACE to SessionMode.PRESENTATION) to listOf(
                "120-170 kelime/dk hedefle ve önemli cümlede yavaşla.",
                "Kapanış cümlesinden önce bilinçli bir durak kullan."
            ),
            (Insight.PACE to SessionMode.FREE) to listOf(
                "110-190 kelime/dk aralığında kal; hızın ödülü yok.",
                "Konu değiştirdiğinde tempoyu biraz düşür."
            ),
            (Insight.TONE to SessionMode.INTERVIEW) to listOf(
                "Önemli cümlelerin sonunu yükselt; cevaplar daha ilgili duyulsun.",
                "Anlam taşıyan kelimelerde vurguyu değiştir."
            ),
            (Insight.TONE to SessionMode.PRESENTATION) to listOf(
                "Yeni noktalarda sesi yükselt, sonuçlarda düşür.",
                "Tek düzeliği kırmak için cümle başına bir kelimeyi vurgula."
            ),
            (Insight.TONE to SessionMode.FREE) to listOf(
                "Sesin hikâyeyle birlikte doğal biçimde inip çıksın.",
                "En çok önemsediğin ifadeyi öne çıkar."
            ),
            (Insight.CONTINUITY to SessionMode.INTERVIEW) to listOf(
                "Düşünmen gerektiğinde kısa bir köprü cümlesi hazırla.",
                "Sıradaki noktanı not al; boşluklar kısalsın."
            ),
            (Insight.CONTINUITY to SessionMode.PRESENTATION) to listOf(
                "Geçişleri prova et; sıradaki cümleyi arama.",
                "Planlı bir durak yap, iki saniye içinde devam et."
            ),
            (Insight.CONTINUITY to SessionMode.FREE) to listOf(
                "Konuyu kaybettiğinde son cümleyi yüksek sesle özetle.",
                "Susmak yerine basit bir bağlaçla devam et."
            ),
            (Insight.GOOD to SessionMode.INTERVIEW) to listOf(
                "Bir sonraki sefer daha zor bir soru veya daha uzun cevap dene.",
                "Bu ritmi koru, her cevaba bir somut örnek ekle."
            ),
            (Insight.GOOD to SessionMode.PRESENTATION) to listOf(
                "Not almadan sunum yaparak zorluğu artır.",
                "Daha uzun bir seans dene, sonunda aynı enerjiyi koru."
            ),
            (Insight.GOOD to SessionMode.FREE) to listOf(
                "Bildiğin olmayan bir konuda beş dakika konuş.",
                "Rahat akışı koru ve skoru biraz daha yükselt."
            )
        )
    )
}
