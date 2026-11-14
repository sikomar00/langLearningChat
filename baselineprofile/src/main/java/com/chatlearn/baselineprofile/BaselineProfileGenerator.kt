package com.chatlearn.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 앱을 켜고 네 탭을 한 번씩 연다. 여기서 실행된 코드가 설치 때 미리 컴파일된다. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startupAndTabs() = rule.collect(packageName = "com.chatlearn", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // 새로 설치하면 첫 실행 안내가 탭을 가린다. "나중에"로 닫는다.
        device.wait(Until.findObject(By.text("나중에")), 3_000)?.click()
        for (tab in listOf("번역", "라이브러리", "성장", "오늘")) {
            val node = device.wait(Until.findObject(By.text(tab)), 3_000) ?: break
            node.click()
            device.waitForIdle()
        }
    }
}
