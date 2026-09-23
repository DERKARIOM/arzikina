package com.naniger.arzikina.presentation.components

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AppLanguage
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.DefaultAccountKey
import com.naniger.arzikina.domain.model.SystemCategoryKey
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.repository.AppLanguageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traduction des noms par défaut pour les ViewModels, qui n'ont pas de `Context` de vue (voir
 * [displayName] pour tout le reste). Deux usages seulement :
 *
 * 1. **Formulaires de modification** : afficher « Salary » dans le champ, puis ENREGISTRER le nom
 *    canonique « Salaire » si l'utilisateur n'a pas changé le nom (voir
 *    [SystemCategoryKey.canonicalNameFor]). Sans cela, un simple « Enregistrer » en anglais
 *    remplacerait « Salaire » par « Salary » en base, et casserait la recherche par nom des
 *    catégories système (prêts, frais).
 * 2. **Recherche** : une recherche « salary » doit trouver la catégorie affichée « Salary ».
 *
 * Aucun résultat n'est mis en cache par ce service côté ViewModel : chaque appel lit la langue
 * effective du moment ([AppLanguageRepository.getEffectiveLanguage]). Seules les `Resources` par
 * langue sont mises en cache (objets immuables, coûteux à créer).
 */
@Singleton
class DefaultNameLocalizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLanguageRepository: AppLanguageRepository
) {

    private val resourcesByLanguage = ConcurrentHashMap<AppLanguage, Resources>()

    fun displayName(category: Category): String =
        category.systemKey?.let { currentResources().getString(it.labelRes) } ?: category.name

    fun displayName(account: Account): String =
        account.defaultKey?.let { currentResources().getString(it.labelRes) } ?: account.name

    fun canonicalCategoryName(input: String, type: TransactionType): String =
        SystemCategoryKey.canonicalNameFor(input, type) { key -> labelsInAllLanguages(key.labelRes) }

    fun canonicalAccountName(input: String): String =
        DefaultAccountKey.canonicalNameFor(input) { key -> labelsInAllLanguages(key.labelRes) }

    private fun labelsInAllLanguages(labelRes: Int): List<String> =
        AppLanguage.supported.map { resourcesFor(it).getString(labelRes) }

    private fun currentResources(): Resources = resourcesFor(appLanguageRepository.getEffectiveLanguage())

    private fun resourcesFor(language: AppLanguage): Resources = resourcesByLanguage.getOrPut(language) {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(requireNotNull(language.languageTag)))
        }
        context.createConfigurationContext(configuration).resources
    }
}
