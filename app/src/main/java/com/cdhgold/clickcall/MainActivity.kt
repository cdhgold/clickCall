package com.cdhgold.clickcall

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cdhgold.clickcall.databinding.ActivityMainBinding
import com.cdhgold.clickcall.ui.list.ContactListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 광고 SDK 초기화 및 관련 코드는 제거/주석 처리되었습니다.

        // Show ContactListFragment initially
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, ContactListFragment())
                .commit()
        }
    }
}
