package com.naniger.arzikina.presentation.categories

import com.naniger.arzikina.domain.model.CategoryIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconVisibilityTest {

    private val all = CategoryIcon.entries

    @Test
    fun collapsedShowsTwoRowsOfCommonIcons() {
        val shown = CategoryIconVisibility.visible(all, CategoryIcon.FOOD, expanded = false, columns = 6)
        assertEquals(12, shown.size)
        assertEquals(all.take(12), shown)
    }

    @Test
    fun expandedShowsEverything() {
        assertEquals(all, CategoryIconVisibility.visible(all, CategoryIcon.FOOD, expanded = true, columns = 6))
    }

    @Test
    fun selectedIconIsNeverHiddenWhenCollapsed() {
        val shown = CategoryIconVisibility.visible(all, CategoryIcon.MOSQUE, expanded = false, columns = 6)
        assertEquals(12, shown.size)
        assertTrue(CategoryIcon.MOSQUE in shown)
        assertEquals(all.take(11), shown.take(11))
    }

    @Test
    fun toggleOnlyWhenSomeIconsAreHidden() {
        assertTrue(CategoryIconVisibility.needsToggle(all.size, 6))
        assertFalse(CategoryIconVisibility.needsToggle(12, 6))
        assertEquals(all.take(10), CategoryIconVisibility.visible(all.take(10), CategoryIcon.FOOD, expanded = false, columns = 6))
    }

    @Test
    fun editingACategoryWithAHiddenIconStartsExpanded() {
        assertTrue(CategoryIconVisibility.startsExpanded(all, CategoryIcon.CHURCH, columns = 6))
        assertFalse(CategoryIconVisibility.startsExpanded(all, CategoryIcon.OTHER, columns = 6))
    }

    @Test
    fun degenerateColumnCountStillWorks() {
        assertEquals(2, CategoryIconVisibility.visible(all, CategoryIcon.FOOD, expanded = false, columns = 0).size)
    }
}
