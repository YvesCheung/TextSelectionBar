package io.github.yvescheung.adr.widget.textselection

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import io.github.yvescheung.adr.widget.textselection.demo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectionBar.visibility = if (binding.editText.hasFocus()) VISIBLE else GONE
        binding.editText.setOnFocusChangeListener { _, hasFocus ->
            binding.selectionBar.visibility = if (hasFocus) VISIBLE else GONE
        }
        binding.selectionBar.setController(TextSelectionController(binding.editText))
    }
}