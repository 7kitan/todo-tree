package com.example.todo_tree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.todo_tree.persistence.PlatformStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        PlatformStorage.init(this)
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
