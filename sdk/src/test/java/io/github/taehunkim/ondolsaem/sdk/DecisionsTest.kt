package io.github.taehunkim.ondolsaem.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionsTest {
    @Test fun koreanTopicGivesKoreanStatement() {
        assertEquals("이 글은 여행에 관한 내용이다.", Decisions.hypothesis("여행"))
        assertEquals("이 글은 요리(레시피, 음식)에 관한 내용이다.", Decisions.hypothesis(" 요리 ", "레시피, 음식"))
    }

    @Test fun otherTopicsGiveEnglishStatement() {
        assertEquals("This text is about travel.", Decisions.hypothesis("travel"))
        assertEquals("This text is about Work (jobs, meetings).", Decisions.hypothesis("Work", "jobs, meetings"))
        assertEquals("이 글은 receipt(영수증)에 관한 내용이다.", Decisions.hypothesis("receipt", "영수증"))
    }

    @Test fun verdictUsesLimits() {
        assertEquals(Decisions.Verdict.YES, Decisions.verdict(0.7f))
        assertEquals(Decisions.Verdict.UNSURE, Decisions.verdict(0.5f))
        assertEquals(Decisions.Verdict.NO, Decisions.verdict(0.3f))
    }
}
