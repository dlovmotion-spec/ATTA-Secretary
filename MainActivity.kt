package com.attaproductions.secretary

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var db: TaskDb
    private lateinit var list: LinearLayout
    private val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val speechTaskRequest = 200
    private val speechTimeRequest = 201
    private var pendingTitle: String? = null
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TaskDb(this)
        tts = TextToSpeech(this, this)
        requestPermissionsIfNeeded()
        showSplash()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ru = tts?.setLanguage(Locale("ru", "RU"))
            if (ru == TextToSpeech.LANG_MISSING_DATA || ru == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale.getDefault()
        }
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(17,17,17))
        }
        root.addView(TextView(this).apply {
            text = "ATTA"; textSize = 52f; setTextColor(Color.rgb(200,164,90)); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "PRODUCTIONS\nPERSONAL SECRETARY"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        setContentView(root)
        root.postDelayed({ showMain() }, 900)
    }

    private fun showMain() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,28,28,40); setBackgroundColor(Color.rgb(247,247,247)) }
        root.addView(TextView(this).apply { text="ATTA Secretary"; textSize=28f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17,17,17)) })
        root.addView(TextView(this).apply { text="Скажите — секретарь сам поставит напоминание"; textSize=15f; setTextColor(Color.DKGRAY); setPadding(0,0,0,18) })

        val mic = Button(this).apply {
            text="🎙  СКАЗАТЬ ЗАДАЧУ"
            textSize=20f
            minHeight=150
            setOnClickListener { startVoiceTask() }
        }
        root.addView(mic, LinearLayout.LayoutParams(-1,-2))

        root.addView(TextView(this).apply {
            text="Например: «Завтра в 10 позвонить Сергею»\nили «Через 30 минут проверить заказ»"
            textSize=14f; setTextColor(Color.DKGRAY); setPadding(4,10,4,14)
        })

        root.addView(TextView(this).apply { text="МОИ НАПОМИНАНИЯ"; textSize=16f; typeface=Typeface.DEFAULT_BOLD; setPadding(0,20,0,8) })
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(list)
        scroll.addView(root)
        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val tasks=db.tasks(false)
        if(tasks.isEmpty()) list.addView(TextView(this).apply { text="Пока задач нет. Нажмите микрофон и скажите задачу."; textSize=16f; setPadding(8,18,8,18) })
        tasks.forEach { task ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,16,18,16); setBackgroundColor(Color.WHITE) }
            card.addView(TextView(this).apply { text=task.title; textSize=18f; typeface=Typeface.DEFAULT_BOLD })
            card.addView(TextView(this).apply { text="⏰ ${fmt.format(Date(task.dueAt))}"; textSize=14f; setTextColor(Color.DKGRAY) })
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            row.addView(Button(this).apply { text="✓ Готово"; setOnClickListener { db.markDone(task.id); refresh() } }, LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Button(this).apply { text="Удалить"; setOnClickListener { db.delete(task.id); refresh() } }, LinearLayout.LayoutParams(0,-2,1f))
            card.addView(row)
            list.addView(card, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,0,0,12) })
        }
    }

    private fun startVoiceTask() = startVoice(speechTaskRequest, "Скажите задачу и когда напомнить")
    private fun startVoiceTime() = startVoice(speechTimeRequest, "Когда напомнить?")

    private fun startVoice(requestCode: Int, prompt: String) {
        val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT,prompt)
        }
        try { startActivityForResult(i,requestCode) } catch(_:Exception) { Toast.makeText(this,"Голосовое распознавание недоступно",Toast.LENGTH_LONG).show() }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        val spoken=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if(spoken.isBlank()) return

        when(requestCode) {
            speechTaskRequest -> {
                val parsed=SpokenCommandParser.parse(spoken)
                if(parsed.hasTemporal && parsed.dueAt!=null) createReminder(parsed.title, parsed.dueAt)
                else {
                    pendingTitle = parsed.title.ifBlank { spoken }
                    speak("Когда напомнить?")
                    window.decorView.postDelayed({ startVoiceTime() }, 700)
                }
            }
            speechTimeRequest -> {
                val at=SpokenCommandParser.parseTimeOnly(spoken)
                val title=pendingTitle
                if(at!=null && !title.isNullOrBlank()) {
                    createReminder(title, at)
                    pendingTitle=null
                } else {
                    speak("Я не понял время. Скажите, например: завтра в десять")
                    window.decorView.postDelayed({ startVoiceTime() }, 1100)
                }
            }
        }
    }

    private fun createReminder(title: String, at: Long) {
        val id=db.add(title,"",at,1,"none")
        schedule(id,title,at)
        refresh()
        val whenText=fmt.format(Date(at))
        Toast.makeText(this,"Готово: $title — $whenText",Toast.LENGTH_LONG).show()
        speak("Готово. Напомню $whenText")
    }

    private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "atta") }

    private fun schedule(id: Long, title: String, at: Long) {
        val am=getSystemService(ALARM_SERVICE) as AlarmManager
        val i=Intent(this,ReminderReceiver::class.java).putExtra("id",id).putExtra("title",title)
        val pi=PendingIntent.getBroadcast(this,id.toInt(),i,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) } catch(_:SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }
    }

    private fun requestPermissionsIfNeeded() {
        val p=mutableListOf<String>()
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) p += Manifest.permission.POST_NOTIFICATIONS
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) p += Manifest.permission.RECORD_AUDIO
        if(p.isNotEmpty()) requestPermissions(p.toTypedArray(),100)
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
