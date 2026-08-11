package com.arzikina.ne

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Redirige `Dispatchers.Main` (utilisé implicitement par `viewModelScope.launch`, voir chaque
 * ViewModel de `presentation/utilities/loans`) vers un [TestDispatcher] déterministe le temps de
 * chaque test — sans cette règle, tout ViewModel testé en JVM pur planterait dès le premier
 * `viewModelScope.launch` (`Dispatchers.Main` n'a pas d'implémentation par défaut hors Android).
 *
 * Utilisation : `@get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)`, en
 * réutilisant le MÊME [TestDispatcher] que celui passé à `runTest(testDispatcher) { ... }` dans
 * chaque test — sinon les coroutines lancées par le ViewModel (ex. dans `init {}`) tournent sur une
 * planification de temps virtuel différente de celle du corps du test et ne se terminent jamais
 * avant `advanceUntilIdle()`.
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
