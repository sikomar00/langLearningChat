package com.chatlearn.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionCheckTest {
    @Test fun successMeansNoError() = runBlocking {
        assertNull(checkConnection(FakeAiService()))
    }

    @Test fun failureReturnsTheErrorKind() = runBlocking {
        val ai = FakeAiService().apply { translate = { _, _, _ -> Result.failure(AiException(AiError.BAD_KEY)) } }
        assertEquals(AiError.BAD_KEY, checkConnection(ai))
    }

    @Test fun unknownFailureBecomesServerError() = runBlocking {
        val ai = FakeAiService().apply { translate = { _, _, _ -> Result.failure(RuntimeException("x")) } }
        assertEquals(AiError.SERVER, checkConnection(ai))
    }
}
