package com.jvmd.notesbridge.main

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.jvmd.notesbridge.NotesBridgeApplication
import com.jvmd.notesbridge.R
import com.jvmd.notesbridge.share.ShareReceiverActivity

class MainActivity : AppCompatActivity() {
    private val pickDocuments =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) convert(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<android.view.View>(R.id.main_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val app = application as NotesBridgeApplication
        findViewById<TextView>(R.id.main_formats).text =
            app.registry.supportedFormats.joinToString(separator = "\n") { "• ${it.displayName}" }
        findViewById<MaterialButton>(R.id.main_pick).setOnClickListener { pickDocuments.launch(arrayOf("*/*")) }
    }

    private fun convert(uris: List<Uri>) {
        val intent =
            Intent(this, ShareReceiverActivity::class.java).apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                clipData =
                    ClipData.newRawUri(null, uris.first()).also { clip ->
                        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(intent)
    }
}
